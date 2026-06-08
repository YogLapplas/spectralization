package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.OpticalPort;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalPathTracer;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.OpticalTraceStep;
import io.github.yoglappland.spectralization.optics.OpticalTraceTermination;
import io.github.yoglappland.spectralization.optics.OpticalTraceTerminationReason;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.CompiledReadoutLayer;
import io.github.yoglappland.spectralization.optics.compiler.OpticalCompilerDebugLogger;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphCompiler;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortWaveKind;
import io.github.yoglappland.spectralization.optics.compiler.OpticalReadoutLayerCompiler;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolver;
import io.github.yoglappland.spectralization.optics.validation.OpticalTraceValidator;
import io.github.yoglappland.spectralization.optics.world.OpticalWorldIndex;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalTraceCache {
    private static final Map<Level, LevelTraceCache> CACHES = new WeakHashMap<>();
    private static final int MAX_DISCOVERED_SYSTEM_SOURCES = 32;
    private static final int MIN_DIRECT_GEOMETRY_CACHE_ENTRIES = 512;
    private static final int MAX_DIRECT_GEOMETRY_CACHE_ENTRIES = 4096;
    private static final int GEOMETRY_POSITIONS_PER_EXTRA_CACHE_ENTRY = 3;
    private static final int MAX_GEOMETRY_SIGNATURE_POSITIONS = 8192;
    private static final long GEOMETRY_SIGNATURE_SEED = 0x9E3779B97F4A7C15L;
    private static final int MIN_SYSTEM_COMPILATION_CACHE_ENTRIES = 32;

    public static void requestOrApply(Level level, BlockPos sourcePos, OutputBeam sourceOutput) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || sourceOutput.beam().isEmpty()) {
            return;
        }

        LevelTraceCache cache = cacheFor(serverLevel);
        SourceTraceKey key = new SourceTraceKey(sourcePos, sourceOutput.outgoingDirection());
        int networkId = cache.networkIdFor(key);
        CachedOpticalTrace cachedTrace = cache.cachedTracesByNetwork.get(networkId);

        if (!OpticalWorldIndex.canRunDerived(serverLevel)) {
            if (cachedTrace != null) {
                cachedTrace.applyOutputs(level, false, cache.nextReadoutStep());
            }

            return;
        }

        if (cachedTrace != null
                && cachedTrace.matches(sourceOutput)
                && !cache.dependencyIndex.isDirty(networkId)) {
            if (applyCachedOutputs(level, cache, networkId, cachedTrace)) {
                OpticalWorldIndex.markDerivedCommitted(serverLevel);
            }

            return;
        }

        cache.enqueue(new TraceRequest(networkId, sourcePos, sourceOutput), serverLevel.getGameTime());
    }

    public static boolean markChanged(LevelAccessor level, BlockPos pos) {
        return markChanged(level, pos, OpticalDirtyKind.STRUCTURE);
    }

    public static boolean markChanged(LevelAccessor level, BlockPos pos, OpticalDirtyKind dirtyKind) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        LevelTraceCache cache = cacheFor(serverLevel);
        if (dirtyKind != OpticalDirtyKind.STRUCTURE) {
            OpticalWorldIndex.markChanged(serverLevel, pos, dirtyKind);
        }

        if (dirtyKind == OpticalDirtyKind.PARAMETER) {
            return cache.markParameterChanged(serverLevel, pos);
        }

        return cache.markChanged(pos, dirtyKind, serverLevel.getGameTime());
    }

    public static void processQueues(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            processQueue(level);
        }
    }

    private static void processQueue(ServerLevel level) {
        LevelTraceCache cache = cacheFor(level);

        if (!OpticalWorldIndex.canRunDerived(level)) {
            cache.applyPendingCachedOutputs(level, false);
            return;
        }

        long deadlineNanos = System.nanoTime() + SpectralizationConfig.opticalSolverBudgetMicros() * 1_000L;
        int maxRequests = SpectralizationConfig.opticalSolverMaxRequestsPerTick();
        IntSet processedNetworkIds = new IntOpenHashSet();
        int initialRequestCount = cache.pendingRequests.size();
        boolean deferredDirectWork = false;
        int processed = 0;

        while (!cache.pendingRequests.isEmpty() && processed < maxRequests && initialRequestCount-- > 0) {
            if (processed > 0 && System.nanoTime() >= deadlineNanos) {
                break;
            }

            TraceRequest request = cache.pendingRequests.removeFirst();
            cache.queuedNetworkIds.remove(request.networkId());

            if (!level.isLoaded(request.sourcePos())) {
                continue;
            }

            SourceTraceKey sourceKey = new SourceTraceKey(request.sourcePos(), request.sourceOutput().outgoingDirection());
            CachedOpticalTrace existingTrace = cache.cachedTracesByNetwork.get(request.networkId());

            if (shouldDeferDirectRecompile(level, cache, request, existingTrace)) {
                existingTrace.applyOutputs(level, false, cache.nextReadoutStep());
                cache.requeueDeferred(request);
                deferredDirectWork = true;
                continue;
            }

            GeometrySignature geometrySignature = cache.geometrySignatureFor(level, request.networkId());
            DirectCompilationKey directCompilationKey = geometrySignature == null
                    ? null
                    : new DirectCompilationKey(sourceKey, request.sourceOutput(), geometrySignature);
            DirectCompilation directCompilation = directCompilationKey == null
                    ? null
                    : cache.directCompilationCache.get(directCompilationKey);
            boolean directGeometryCacheHit = directCompilation != null;

            boolean shouldLogCompilerDebug = SpectralizationConfig.opticalCompilerDebugLog()
                    && cache.shouldRunCompilerDebug(sourceKey, level.getGameTime());
            CompiledPortGraph directGraph;
            CompiledPortGraph passiveDirectGraph;
            CompiledPortGraph coherentDirectGraph;
            ScalarPowerSolution scalarPowerSolution;
            CompiledReadoutLayer directReadoutLayer;

            if (directCompilation != null) {
                directGraph = directCompilation.graph();
                passiveDirectGraph = directCompilation.passiveGraph();
                coherentDirectGraph = directCompilation.coherentGraph();
                scalarPowerSolution = directCompilation.solution();
                directReadoutLayer = directCompilation.readoutLayer();
            } else {
                passiveDirectGraph = PortGraphCompiler.compileDirect(
                        level,
                        request.sourcePos(),
                        withCoherence(request.sourceOutput(), CoherenceKind.INCOHERENT)
                );
                coherentDirectGraph = PortGraphCompiler.compileDirect(
                        level,
                        request.sourcePos(),
                        withCoherence(request.sourceOutput(), CoherenceKind.COHERENT)
                );
                directGraph = unionGraphs(List.of(passiveDirectGraph, coherentDirectGraph));
                scalarPowerSolution = solvePowerChannels(
                        level,
                        passiveDirectGraph,
                        coherentDirectGraph,
                        sourcePowerMap(passiveDirectGraph.sourceNode(), incoherentPower(request.sourceOutput())),
                        sourcePowerMap(coherentDirectGraph.sourceNode(), coherentPower(request.sourceOutput()))
                );
                directReadoutLayer = OpticalReadoutLayerCompiler.compile(level, directGraph);
            }
            List<ReceiverOutput> directReceiverOutputs = directReadoutLayer.sample(scalarPowerSolution);
            boolean directReadoutDemand = hasReadoutDemand(directReadoutLayer, directReceiverOutputs);
            CompiledOpticalTrace trace = traceLegacyIfNeeded(
                    level,
                    request,
                    directGraph,
                    shouldLogCompilerDebug
            );
            CompiledPortGraph observedGraph = shouldLogCompilerDebug && trace != null
                    ? PortGraphCompiler.compileObservedTrace(trace)
                    : null;
            CachedOpticalTrace cachedTrace = buildCachedTrace(
                    level,
                    request,
                    trace,
                    directGraph,
                    directReadoutLayer,
                    scalarPowerSolution,
                    directReceiverOutputs
            );
            GeometrySignature updatedGeometrySignature = cache.rememberGeometrySignaturePositions(
                    level,
                    request.networkId(),
                    cachedTrace.dependencies()
            );
            DirectCompilationKey updatedDirectCompilationKey = updatedGeometrySignature == null
                    ? directCompilationKey
                    : new DirectCompilationKey(sourceKey, request.sourceOutput(), updatedGeometrySignature);
            if (updatedGeometrySignature != null) {
                cache.directCompilationCache.put(
                        updatedDirectCompilationKey,
                        new DirectCompilation(
                                directGraph,
                                passiveDirectGraph,
                                coherentDirectGraph,
                                directReadoutLayer,
                                scalarPowerSolution
                        )
                );
            }
            CachedOpticalSystem cachedSystem = null;
            ScalarPowerSolution networkSolution = null;
            List<ReceiverOutput> networkReceiverOutputs = List.of();
            SystemLegacyReadout systemLegacyReadout = new SystemLegacyReadout(
                    cachedTrace.receiverOutputs(),
                    true,
                    !cachedTrace.unstable()
            );
            int networkSourceCount = 0;
            int networkSystemId = 0;
            boolean networkStructurallyFresh = true;
            boolean networkParametricallyFresh = true;
            boolean networkUsableForGameplay = false;
            int networkReadoutBindingCount = 0;

            if (directReadoutDemand
                    && directGraph.nodes().size() <= SpectralizationConfig.opticalCompilerFullNetworkMaxNodes()) {
                cache.directSourcesByKey.put(
                        sourceKey,
                        new DirectSourceRecord(
                                request.networkId(),
                                directGraph,
                                passiveDirectGraph,
                                coherentDirectGraph,
                                request.sourceOutput(),
                                updatedDirectCompilationKey,
                                cache.epochs
                        )
                );
                cache.enqueueSystemRebuild(request.networkId());
                cachedSystem = cache.systemForNetwork(request.networkId());

                if (cachedSystem != null) {
                    networkSolution = cachedSystem.solution();
                    networkReceiverOutputs = cachedSystem.receiverOutputs();
                    systemLegacyReadout = collectSystemLegacyReadout(
                            cache,
                            cachedSystem.systemId(),
                            request.networkId(),
                            cachedTrace
                    );
                    networkSourceCount = cachedSystem.sourceCount();
                    networkSystemId = cachedSystem.systemId();
                    networkStructurallyFresh = cachedSystem.structurallyFresh();
                    networkParametricallyFresh = cachedSystem.parametricallyFresh();
                    networkUsableForGameplay = cachedSystem.usableForGameplay();
                    networkReadoutBindingCount = cachedSystem.readoutLayer().size();
                }
            } else if (!directReadoutDemand) {
                cache.unregisterDirectSourceForNetwork(request.networkId());
            }

            if (shouldLogCompilerDebug && trace != null && observedGraph != null) {
                OpticalCompilerDebugLogger.logObservedDirectAndNetwork(
                        level,
                        trace,
                        observedGraph,
                        directGraph,
                        scalarPowerSolution,
                        directGeometryCacheHit,
                        updatedGeometrySignature == null ? 0 : updatedGeometrySignature.positionCount(),
                        cache.directCompilationCache.size(),
                        cache.directCompilationCacheEntryLimit,
                        cache.lastReliableReceiverOutputsByReadout.size(),
                        cachedSystem == null ? null : cachedSystem.graph(),
                        networkSolution,
                        networkSourceCount,
                        networkSystemId,
                        networkStructurallyFresh,
                        networkParametricallyFresh,
                        networkUsableForGameplay,
                        cachedTrace.receiverOutputs(),
                        directReceiverOutputs,
                        networkReceiverOutputs,
                        !cachedTrace.unstable(),
                        directReadoutLayer.size(),
                        networkReadoutBindingCount,
                        systemLegacyReadout.receiverOutputs(),
                        systemLegacyReadout.complete(),
                        systemLegacyReadout.comparable()
                );
            } else if (shouldLogCompilerDebug) {
                OpticalCompilerDebugLogger.logDirectOnly(
                        level,
                        request.sourcePos(),
                        request.sourceOutput(),
                        directGraph,
                        scalarPowerSolution,
                        directGeometryCacheHit,
                        updatedGeometrySignature == null ? 0 : updatedGeometrySignature.positionCount(),
                        cache.directCompilationCache.size(),
                        cache.directCompilationCacheEntryLimit,
                        cache.lastReliableReceiverOutputsByReadout.size(),
                        cachedSystem == null ? null : cachedSystem.graph(),
                        networkSolution,
                        networkSourceCount,
                        networkSystemId,
                        networkStructurallyFresh,
                        networkParametricallyFresh,
                        networkUsableForGameplay,
                        directReceiverOutputs,
                        networkReceiverOutputs,
                        directReadoutLayer.size(),
                        networkReadoutBindingCount
                );
            }
            cache.cachedTracesByNetwork.put(request.networkId(), cachedTrace);
            cache.dependencyIndex.replaceDependencies(request.networkId(), cachedTrace.dependencies());
            cache.dependencyIndex.clearDirty(request.networkId());
            if (cachedSystem != null && trace != null) {
                OpticalTraceValidator.validate(level, request.sourcePos(), request.sourceOutput(), trace);
            }
            processedNetworkIds.add(request.networkId());
            processed++;
        }

        if (processed > 0 || deferredDirectWork) {
            cache.lastDirectWorkTick = level.getGameTime();
        }

        if (processed == 0 && !deferredDirectWork) {
            processedNetworkIds.addAll(processPendingSystemRebuilds(level, cache, deadlineNanos));
        }

        for (int processedNetworkId : processedNetworkIds) {
            CachedOpticalTrace cachedTrace = cache.cachedTracesByNetwork.get(processedNetworkId);

            if (cachedTrace != null && !cache.dependencyIndex.isDirty(processedNetworkId)) {
                applyCachedOutputs(level, cache, processedNetworkId, cachedTrace);
            }
        }

        if (processed > 0 || !processedNetworkIds.isEmpty()) {
            OpticalWorldIndex.markDerivedCommitted(level);
        }
    }

    private static IntSet processPendingSystemRebuilds(
            ServerLevel level,
            LevelTraceCache cache,
            long deadlineNanos
    ) {
        IntSet rebuiltNetworkIds = new IntOpenHashSet();

        if (!cache.hasPendingSystemRebuilds() || System.nanoTime() >= deadlineNanos) {
            return rebuiltNetworkIds;
        }

        if (!cache.quietEnoughForSystemRebuild(level.getGameTime())) {
            return rebuiltNetworkIds;
        }

        pruneInactiveSources(level, cache);

        Integer networkId = cache.pollPendingSystemRebuildNetworkId();
        if (networkId == null) {
            return rebuiltNetworkIds;
        }

        rebuiltNetworkIds.addAll(rebuildDirectSystemForNetwork(level, cache, networkId));
        CachedOpticalSystem rebuiltSystem = cache.systemForNetwork(networkId);
        OpticalCompilerDebugLogger.logSystemRebuild(
                level,
                networkId,
                rebuiltNetworkIds.size(),
                cache.pendingSystemRebuildCount(),
                SpectralizationConfig.opticalCompilerSystemRebuildQuietTicks(),
                cache.lastSystemRebuildCacheHit,
                cache.systemCompilationCache.size(),
                SpectralizationConfig.opticalCompilerSystemCacheMaxEntries(),
                rebuiltSystem == null ? 0 : rebuiltSystem.systemId(),
                rebuiltSystem == null ? 0 : rebuiltSystem.sourceCount(),
                rebuiltSystem == null ? 0 : rebuiltSystem.readoutLayer().size(),
                rebuiltSystem == null ? 0 : rebuiltSystem.receiverOutputs().size(),
                rebuiltSystem != null && rebuiltSystem.usableForGameplay(),
                rebuiltSystem == null ? "none" : rebuiltSystem.solution().solverKind().name()
        );
        return rebuiltNetworkIds;
    }

    private static boolean shouldDeferDirectRecompile(
            ServerLevel level,
            LevelTraceCache cache,
            TraceRequest request,
            CachedOpticalTrace existingTrace
    ) {
        if (existingTrace == null) {
            return false;
        }

        int quietTicks = SpectralizationConfig.opticalCompilerDirectRecompileQuietTicks();

        if (quietTicks <= 0
                || existingTrace.portGraph().nodes().size() < SpectralizationConfig.opticalCompilerLargeDirectGraphNodes()) {
            return false;
        }

        long activityTick = cache.lastActivityTickForNetwork(request.networkId());

        if (activityTick == Long.MIN_VALUE) {
            return false;
        }

        return level.getGameTime() - activityTick < quietTicks;
    }

    private static CompiledOpticalTrace traceLegacyIfNeeded(
            ServerLevel level,
            TraceRequest request,
            CompiledPortGraph directGraph,
            boolean shouldLogCompilerDebug
    ) {
        int maxStates = SpectralizationConfig.opticalEffectTraceMaxStates();

        if (maxStates <= 0) {
            return null;
        }

        if (shouldRunLegacyEffects(directGraph)) {
            return OpticalPathTracer.traceEffects(
                    level,
                    request.sourcePos(),
                    request.sourceOutput(),
                    maxStates
            );
        }

        if (shouldLogCompilerDebug
                && SpectralizationConfig.opticalCompilerLegacyDebugOracle()
                && directGraph.feedbackSccCount() == 0) {
            return OpticalPathTracer.traceForDebug(
                    level,
                    request.sourcePos(),
                    request.sourceOutput(),
                    maxStates
            );
        }

        return null;
    }

    private static boolean shouldRunLegacyEffects(CompiledPortGraph directGraph) {
        if (!SpectralizationConfig.lightPathsVisible()
                && !SpectralizationConfig.surfaceSpotsVisible()
                && !SpectralizationConfig.laserDamage()
                && !SpectralizationConfig.laserBlindness()) {
            return false;
        }

        int maxGraphNodes = SpectralizationConfig.opticalCompilerLegacyEffectMaxGraphNodes();

        return directGraph.feedbackSccCount() == 0
                && maxGraphNodes > 0
                && directGraph.nodes().size() <= maxGraphNodes;
    }

    private static boolean applyCachedOutputs(
            Level level,
            LevelTraceCache cache,
            int networkId,
            CachedOpticalTrace cachedTrace
    ) {
        CachedOpticalSystem cachedSystem = cache.systemForNetwork(networkId);
        long readoutStep = cache.nextReadoutStep();

        if (cache.isSystemRebuildPending(networkId)) {
            CachedOpticalSystem heldSystem = cache.lastUsableSystemForNetwork(networkId);

            if (heldSystem != null && cache.markSystemApplied(heldSystem.systemId(), level.getGameTime())) {
                heldSystem.applyOutputs(level, false, readoutStep);
                logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, heldSystem,
                        "pending_rebuild_held_system", false, readoutStep);
                return false;
            }

            boolean reliable = reliablePendingDirectTrace(level, cachedTrace);
            cachedTrace.applyOutputs(level, reliable, readoutStep);
            logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, null,
                    "pending_rebuild_direct_trace", reliable, readoutStep);
            return reliable;
        }

        if (cachedSystem != null) {
            if (cachedSystem.usableForGameplay()) {
                if (cache.markSystemApplied(cachedSystem.systemId(), level.getGameTime())) {
                    cachedSystem.applyOutputs(level, true, readoutStep);
                    logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, cachedSystem,
                            "cached_system_reliable", true, readoutStep);
                }
                return true;
            }

            CachedOpticalSystem heldSystem = cache.lastUsableSystemForNetwork(networkId);
            if (heldSystem != null && cache.markSystemApplied(heldSystem.systemId(), level.getGameTime())) {
                heldSystem.applyOutputs(level, false, readoutStep);
                logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, heldSystem,
                        "unusable_system_held_system", false, readoutStep);
                return false;
            }

            List<ReceiverOutput> heldReceiverOutputs = cache.lastReliableReceiverOutputsFor(cachedSystem);
            if (!heldReceiverOutputs.isEmpty() && cache.markHeldReadoutApplied(cachedSystem, level.getGameTime())) {
                applyReceiverOutputs(level, heldReceiverOutputs, false, readoutStep);
                logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, cachedSystem,
                        "unusable_system_held_readout", false, readoutStep);
                return false;
            }

            if (cache.markSystemApplied(cachedSystem.systemId(), level.getGameTime())) {
                cachedSystem.applyOutputs(level, false, readoutStep);
                logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, cachedSystem,
                        "unusable_system_current_outputs", false, readoutStep);
                return false;
            }
        }

        boolean reliable = cachedTrace.scalarPowerSolution().reliableForReadout();
        cachedTrace.applyOutputs(level, reliable, readoutStep);
        logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, null,
                "direct_trace", reliable, readoutStep);
        return reliable;
    }

    private static void logReadoutApplyIfNeeded(
            Level level,
            LevelTraceCache cache,
            int networkId,
            CachedOpticalTrace cachedTrace,
            CachedOpticalSystem appliedSystem,
            String mode,
            boolean reliable,
            long readoutStep
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        int directSourceOutputCount = directGraphSourceOutputCount(level, cachedTrace.portGraph());
        ReadoutApplyDebugState debugState = new ReadoutApplyDebugState(
                mode,
                reliable,
                cache.isSystemRebuildPending(networkId),
                directSourceOutputCount,
                appliedSystem != null,
                appliedSystem == null ? 0 : appliedSystem.systemId(),
                appliedSystem == null ? 0 : appliedSystem.sourceCount(),
                appliedSystem != null && appliedSystem.usableForGameplay()
        );

        if (!cache.shouldRunReadoutApplyDebug(networkId, debugState, level.getGameTime())) {
            return;
        }

        OpticalCompilerDebugLogger.logReadoutApply(
                level,
                networkId,
                cachedTrace.portGraph().sourcePos(),
                cachedTrace.portGraph().sourceDirection(),
                mode,
                reliable,
                readoutStep,
                cache.isSystemRebuildPending(networkId),
                cachedTrace.readoutLayer().size(),
                cachedTrace.receiverOutputs().size(),
                cachedTrace.scalarPowerSolution().reliableForReadout(),
                directSourceOutputCount,
                appliedSystem != null,
                appliedSystem == null ? 0 : appliedSystem.systemId(),
                appliedSystem == null ? 0 : appliedSystem.sourceCount(),
                appliedSystem == null ? 0 : appliedSystem.readoutLayer().size(),
                appliedSystem == null ? 0 : appliedSystem.receiverOutputs().size(),
                appliedSystem != null && appliedSystem.usableForGameplay(),
                appliedSystem != null && appliedSystem.solution().reliableForReadout(),
                cache.lastReliableReceiverOutputsByReadout.size()
        );
    }

    private static boolean reliablePendingDirectTrace(Level level, CachedOpticalTrace cachedTrace) {
        return cachedTrace.scalarPowerSolution().reliableForReadout()
                && directGraphSourceOutputCount(level, cachedTrace.portGraph()) == 1;
    }

    private static boolean hasReadoutDemand(
            CompiledReadoutLayer readoutLayer,
            List<ReceiverOutput> receiverOutputs
    ) {
        return readoutLayer.size() > 0 || !receiverOutputs.isEmpty();
    }

    private static int directGraphSourceOutputCount(Level level, CompiledPortGraph graph) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }

        return discoverGraphSourceOutputs(serverLevel, graph).size();
    }

    private static void applyReceiverOutputs(
            Level level,
            List<ReceiverOutput> receiverOutputs,
            boolean reliable,
            long step
    ) {
        for (ReceiverOutput receiverOutput : receiverOutputs) {
            receiverOutput.apply(level, reliable, step);
        }
    }

    private static CachedOpticalTrace buildCachedTrace(
            ServerLevel level,
            TraceRequest request,
            CompiledOpticalTrace trace,
            CompiledPortGraph portGraph,
            CompiledReadoutLayer readoutLayer,
            ScalarPowerSolution scalarPowerSolution,
            List<ReceiverOutput> directReceiverOutputs
    ) {
        LongSet dependencies = new LongOpenHashSet();
        List<ReceiverOutput> receiverOutputs = trace == null
                ? new ArrayList<>(directReceiverOutputs)
                : new ArrayList<>();
        dependencies.add(request.sourcePos().asLong());
        OpticalFieldSources.addPotentialFieldSourceDependencies(level, request.sourcePos(), dependencies);

        if (trace != null) {
            for (OpticalTraceStep step : trace.steps()) {
                addDependency(level, step.pos(), dependencies);
                collectReceiverOutput(level, step, receiverOutputs);
            }

            for (OpticalTraceTermination termination : trace.terminations()) {
                addDependency(level, termination.pos(), dependencies);
            }
        }

        addGraphDependencies(level, portGraph, dependencies);

        return new CachedOpticalTrace(
                request.networkId(),
                request.sourceOutput(),
                receiverOutputs,
                dependencies,
                portGraph,
                readoutLayer,
                scalarPowerSolution,
                trace == null ? !scalarPowerSolution.reliableForReadout() : isUnstable(trace)
        );
    }

    private static void addDependency(ServerLevel level, BlockPos pos, LongSet dependencies) {
        dependencies.add(pos.asLong());
        OpticalFieldSources.addPotentialFieldSourceDependencies(level, pos, dependencies);
    }

    private static void addGraphDependencies(ServerLevel level, CompiledPortGraph portGraph, LongSet dependencies) {
        for (PortGraphNode node : portGraph.nodes()) {
            addDependency(level, node.pos(), dependencies);
        }

        for (var edge : portGraph.edges()) {
            addEdgePathDependencies(level, edge.from().pos(), edge.to().pos(), dependencies);
        }
    }

    private static void addEdgePathDependencies(
            ServerLevel level,
            BlockPos from,
            BlockPos to,
            LongSet dependencies
    ) {
        int dx = Integer.compare(to.getX(), from.getX());
        int dy = Integer.compare(to.getY(), from.getY());
        int dz = Integer.compare(to.getZ(), from.getZ());
        int changedAxes = (dx == 0 ? 0 : 1) + (dy == 0 ? 0 : 1) + (dz == 0 ? 0 : 1);

        if (changedAxes != 1) {
            addDependency(level, from, dependencies);
            addDependency(level, to, dependencies);
            return;
        }

        BlockPos.MutableBlockPos cursor = from.mutable();

        while (!cursor.equals(to)) {
            cursor.move(dx, dy, dz);
            addDependency(level, cursor, dependencies);
        }
    }

    private static void collectReceiverOutput(
            ServerLevel level,
            OpticalTraceStep step,
            List<ReceiverOutput> receiverOutputs
    ) {
        if (!level.isLoaded(step.pos())) {
            return;
        }

        BlockState state = level.getBlockState(step.pos());

        if (state.getBlock() instanceof CmosSensorBlock) {
            Direction receivingSide = state.getValue(CmosSensorBlock.FACING).getOpposite();

            if (step.incomingDirection() == receivingSide) {
                receiverOutputs.add(ReceiverOutput.cmos(step.pos(), step.interactingBeam().totalPower()));
            }

            return;
        }

        if (state.getBlock() instanceof PassThroughSensorBlock && !step.result().outputs().isEmpty()) {
            Direction positiveZDirection = state.getValue(PassThroughSensorBlock.FACING);
            Direction negativeZDirection = positiveZDirection.getOpposite();
            Direction outgoingDirection = step.incomingDirection().getOpposite();

            if (outgoingDirection == positiveZDirection || outgoingDirection == negativeZDirection) {
                receiverOutputs.add(ReceiverOutput.passThroughSensor(
                        step.pos(),
                        outgoingDirection == positiveZDirection,
                        coherentPower(step.interactingBeam())
                ));
            }
        }
    }

    private static boolean isUnstable(CompiledOpticalTrace trace) {
        for (OpticalTraceTermination termination : trace.terminations()) {
            if (termination.reason() == OpticalTraceTerminationReason.MAX_SEGMENTS
                    || termination.reason() == OpticalTraceTerminationReason.MAX_STATES) {
                return true;
            }
        }

        return false;
    }

    private static boolean canUseForGameplay(CompiledPortGraph graph, ScalarPowerSolution solution) {
        return !graph.nodes().isEmpty() && solution.reliableForReadout();
    }

    private static SystemLegacyReadout collectSystemLegacyReadout(
            LevelTraceCache cache,
            int systemId,
            int currentNetworkId,
            CachedOpticalTrace currentTrace
    ) {
        IntSet networkIds = cache.networkIdsBySystem.get(systemId);

        if (networkIds == null || networkIds.isEmpty()) {
            return new SystemLegacyReadout(currentTrace.receiverOutputs(), true, !currentTrace.unstable());
        }

        List<ReceiverOutput> receiverOutputs = new ArrayList<>();
        boolean complete = true;
        boolean comparable = true;

        for (int networkId : networkIds) {
            CachedOpticalTrace trace = networkId == currentNetworkId
                    ? currentTrace
                    : cache.cachedTracesByNetwork.get(networkId);

            if (trace == null) {
                complete = false;
                comparable = false;
                continue;
            }

            comparable &= !trace.unstable();
            receiverOutputs.addAll(trace.receiverOutputs());
        }

        return new SystemLegacyReadout(receiverOutputs, complete, complete && comparable);
    }

    private static IntSet rebuildDirectSystemForNetwork(
            ServerLevel level,
            LevelTraceCache cache,
            int networkId
    ) {
        IntSet rebuiltNetworkIds = new IntOpenHashSet();
        SourceTraceKey seedKey = cache.sourceKeyForNetwork(networkId);

        if (seedKey == null) {
            cache.clearSystemMappingForNetwork(networkId);
            return rebuiltNetworkIds;
        }

        IntSet oldSystemNetworkIds = cache.systemNetworkIdsForNetwork(networkId);
        Set<SourceTraceKey> remainingKeys = new HashSet<>(cache.directSourcesByKey.keySet());

        if (!remainingKeys.remove(seedKey)) {
            return rebuiltNetworkIds;
        }

        Set<SourceTraceKey> componentKeys = collectConnectedSourceKeys(cache, seedKey, remainingKeys);

        if (componentKeys.isEmpty()) {
            return rebuiltNetworkIds;
        }

        BuiltOpticalSystem builtSystem = buildCachedSystem(level, cache, componentKeys);
        IntSet componentNetworkIds = networkIdsFor(cache, builtSystem.sourceKeys());
        cache.installSystem(builtSystem.system(), componentNetworkIds);
        cache.dropQueuedSystemRebuilds(componentNetworkIds);
        rebuiltNetworkIds.addAll(componentNetworkIds);

        for (int oldNetworkId : oldSystemNetworkIds) {
            if (!componentNetworkIds.contains(oldNetworkId)) {
                cache.clearSystemMappingForNetwork(oldNetworkId);
                cache.enqueueSystemRebuild(oldNetworkId);
            }
        }

        return rebuiltNetworkIds;
    }

    private static Set<SourceTraceKey> collectConnectedSourceKeys(
            LevelTraceCache cache,
            SourceTraceKey seedKey,
            Set<SourceTraceKey> remainingKeys
    ) {
        Set<SourceTraceKey> componentKeys = new HashSet<>();
        ArrayDeque<SourceTraceKey> pendingKeys = new ArrayDeque<>();
        pendingKeys.add(seedKey);

        while (!pendingKeys.isEmpty()) {
            SourceTraceKey currentKey = pendingKeys.removeFirst();

            if (!componentKeys.add(currentKey)) {
                continue;
            }

            DirectSourceRecord currentRecord = cache.directSourcesByKey.get(currentKey);

            if (currentRecord == null) {
                continue;
            }

            for (SourceTraceKey candidateKey : List.copyOf(remainingKeys)) {
                DirectSourceRecord candidateRecord = cache.directSourcesByKey.get(candidateKey);

                if (candidateRecord != null && sharesAnyNode(currentRecord.graph(), candidateRecord.graph())) {
                    remainingKeys.remove(candidateKey);
                    pendingKeys.addLast(candidateKey);
                }
            }
        }

        return componentKeys;
    }

    private static BuiltOpticalSystem buildCachedSystem(
            ServerLevel level,
            LevelTraceCache cache,
            Set<SourceTraceKey> sourceKeys
    ) {
        cache.lastSystemRebuildCacheHit = false;
        Set<SourceTraceKey> expandedSourceKeys = new HashSet<>(sourceKeys);
        int systemId = Integer.MAX_VALUE;
        boolean structurallyFresh = true;
        boolean parametricallyFresh = true;
        OpticalEpochs currentEpochs = cache.epochs;
        List<CompiledPortGraph> graphs = new ArrayList<>();
        List<CompiledPortGraph> passiveGraphs = new ArrayList<>();
        List<CompiledPortGraph> coherentGraphs = new ArrayList<>();

        for (SourceTraceKey sourceKey : sourceKeys) {
            DirectSourceRecord record = cache.directSourcesByKey.get(sourceKey);

            if (record == null) {
                continue;
            }

            systemId = Math.min(systemId, record.networkId());
            structurallyFresh &= record.epochs().structurallyMatches(currentEpochs);
            parametricallyFresh &= record.epochs().parametersMatch(currentEpochs);
            graphs.add(record.graph());
            passiveGraphs.add(record.passiveGraph());
            coherentGraphs.add(record.coherentGraph());
        }

        if (graphs.isEmpty()) {
            throw new IllegalStateException("Cannot build an optical system without direct source graphs");
        }

        SystemCompilationKey systemCompilationKey = SystemCompilationKey.of(cache, sourceKeys);

        if (systemCompilationKey != null) {
            CachedOpticalSystem cachedSystem = SpectralizationConfig.opticalCompilerSystemCacheMaxEntries() <= 0
                    ? null
                    : cache.systemCompilationCache.get(systemCompilationKey);

            if (cachedSystem != null) {
                for (GraphSourceOutput sourceOutput : discoverGraphSourceOutputs(level, cachedSystem.graph())) {
                    expandedSourceKeys.add(sourceOutput.key());
                }
                boolean usableForGameplay = structurallyFresh
                        && parametricallyFresh
                        && canUseForGameplay(cachedSystem.graph(), cachedSystem.solution());
                CachedOpticalSystem refreshedSystem = cachedSystem.withRuntimeState(
                        systemId,
                        currentEpochs,
                        structurallyFresh,
                        parametricallyFresh,
                        usableForGameplay
                );
                cache.lastSystemRebuildCacheHit = true;

                if (refreshedSystem.usableForGameplay()) {
                    cache.rememberUsableSystem(refreshedSystem);
                }

                return new BuiltOpticalSystem(refreshedSystem, expandedSourceKeys);
            }
        }

        GraphExpansion graphExpansion = expandGraphsWithDiscoveredSources(level, graphs, null);
        graphs = graphExpansion.graphs();
        expandedSourceKeys.addAll(graphExpansion.sourceKeys());
        passiveGraphs = expandGraphsWithDiscoveredSources(level, passiveGraphs, CoherenceKind.INCOHERENT).graphs();
        coherentGraphs = expandGraphsWithDiscoveredSources(level, coherentGraphs, CoherenceKind.COHERENT).graphs();
        CompiledPortGraph graph = unionGraphs(graphs);
        CompiledPortGraph passiveGraph = unionGraphs(passiveGraphs);
        CompiledPortGraph coherentGraph = unionGraphs(coherentGraphs);
        Map<PortGraphNode, Double> incoherentSourcePowersByNode = collectGraphIncoherentSourcePowers(level, passiveGraph);
        Map<PortGraphNode, Double> directCoherentSourcePowersByNode = collectGraphCoherentSourcePowers(level, coherentGraph);
        ScalarPowerSolution solution = solvePowerChannels(
                level,
                passiveGraph,
                coherentGraph,
                incoherentSourcePowersByNode,
                directCoherentSourcePowersByNode
        );
        Map<PortGraphNode, Double> sourcePowersByNode = mergedPowerMap(
                incoherentSourcePowersByNode,
                directCoherentSourcePowersByNode
        );
        CompiledReadoutLayer readoutLayer = OpticalReadoutLayerCompiler.compile(level, graph);
        List<ReceiverOutput> receiverOutputs = readoutLayer.sample(solution);
        CachedOpticalSystem system = new CachedOpticalSystem(
                systemId,
                graph,
                sourcePowersByNode,
                readoutLayer,
                solution,
                receiverOutputs,
                sourcePowersByNode.size(),
                currentEpochs,
                structurallyFresh,
                parametricallyFresh,
                structurallyFresh && parametricallyFresh && canUseForGameplay(graph, solution)
        );

        if (system.usableForGameplay()) {
            cache.rememberUsableSystem(system);
        }

        if (systemCompilationKey != null && SpectralizationConfig.opticalCompilerSystemCacheMaxEntries() > 0) {
            cache.systemCompilationCache.put(systemCompilationKey, system);
        }

        return new BuiltOpticalSystem(system, expandedSourceKeys);
    }

    private static GraphExpansion expandGraphsWithDiscoveredSources(
            ServerLevel level,
            List<CompiledPortGraph> initialGraphs,
            CoherenceKind sampleCoherence
    ) {
        List<CompiledPortGraph> graphs = new ArrayList<>(initialGraphs);
        Set<SourceTraceKey> compiledSourceKeys = new HashSet<>();

        for (CompiledPortGraph graph : graphs) {
            compiledSourceKeys.add(new SourceTraceKey(graph.sourcePos(), graph.sourceDirection()));
        }

        Set<SourceTraceKey> sourceKeys = new HashSet<>(compiledSourceKeys);

        boolean added;

        do {
            added = false;
            CompiledPortGraph graph = graphs.size() == 1
                    ? graphs.getFirst()
                    : PortGraphCompiler.unionDirectGraphs(graphs);

            for (GraphSourceOutput sourceOutput : discoverGraphSourceOutputs(level, graph)) {
                if (compiledSourceKeys.size() >= MAX_DISCOVERED_SYSTEM_SOURCES) {
                    return new GraphExpansion(graphs, sourceKeys);
                }

                if (!compiledSourceKeys.add(sourceOutput.key())) {
                    continue;
                }

                sourceKeys.add(sourceOutput.key());
                graphs.add(compileDirectForChannel(level, sourceOutput, sampleCoherence));
                added = true;
            }
        } while (added);

        return new GraphExpansion(graphs, sourceKeys);
    }

    private static CompiledPortGraph compileDirectForChannel(
            ServerLevel level,
            GraphSourceOutput sourceOutput,
            CoherenceKind sampleCoherence
    ) {
        if (sampleCoherence != null) {
            return PortGraphCompiler.compileDirect(
                    level,
                    sourceOutput.key().sourcePos(),
                    withCoherence(sourceOutput.outputBeam(), sampleCoherence)
            );
        }

        return unionGraphs(List.of(
                PortGraphCompiler.compileDirect(
                        level,
                        sourceOutput.key().sourcePos(),
                        withCoherence(sourceOutput.outputBeam(), CoherenceKind.INCOHERENT)
                ),
                PortGraphCompiler.compileDirect(
                        level,
                        sourceOutput.key().sourcePos(),
                        withCoherence(sourceOutput.outputBeam(), CoherenceKind.COHERENT)
                )
        ));
    }

    private static Map<PortGraphNode, Double> collectGraphIncoherentSourcePowers(ServerLevel level, CompiledPortGraph graph) {
        Map<PortGraphNode, Double> sourcePowersByNode = new HashMap<>();
        Set<PortGraphNode> graphNodes = new HashSet<>(graph.nodes());

        for (GraphSourceOutput sourceOutput : discoverGraphSourceOutputs(level, graph)) {
            PortGraphNode sourceNode = sourceOutput.sourceNode();

            if (!graphNodes.contains(sourceNode)) {
                continue;
            }

            sourcePowersByNode.merge(
                    sourceNode,
                    incoherentPower(sourceOutput.outputBeam()),
                    Double::sum
            );
        }

        return sourcePowersByNode;
    }

    private static Map<PortGraphNode, Double> collectGraphCoherentSourcePowers(ServerLevel level, CompiledPortGraph graph) {
        Map<PortGraphNode, Double> sourcePowersByNode = new HashMap<>();
        Set<PortGraphNode> graphNodes = new HashSet<>(graph.nodes());

        for (GraphSourceOutput sourceOutput : discoverGraphSourceOutputs(level, graph)) {
            PortGraphNode sourceNode = sourceOutput.sourceNode();

            if (!graphNodes.contains(sourceNode)) {
                continue;
            }

            sourcePowersByNode.merge(
                    sourceNode,
                    coherentPower(sourceOutput.outputBeam()),
                    Double::sum
            );
        }

        return sourcePowersByNode;
    }

    private static ScalarPowerSolution solvePowerChannels(
            ServerLevel level,
            CompiledPortGraph passiveGraph,
            CompiledPortGraph coherentGraph,
            Map<PortGraphNode, Double> incoherentSourcePowersByNode,
            Map<PortGraphNode, Double> directCoherentSourcePowersByNode
    ) {
        ScalarPowerSolution incoherentSolution = ScalarPowerSolver.solve(passiveGraph, incoherentSourcePowersByNode);
        Map<PortGraphNode, Double> coherentSourcePowersByNode = new HashMap<>(directCoherentSourcePowersByNode);
        addRubyCoherentSeedSources(level, coherentGraph, incoherentSolution, coherentSourcePowersByNode);
        ScalarPowerSolution coherentSolution = ScalarPowerSolver.solve(coherentGraph, coherentSourcePowersByNode);

        if (failedWithoutUsablePower(coherentSolution)) {
            coherentSolution = ScalarPowerSolution.empty();
        }

        return combineChannelSolutions(incoherentSolution, coherentSolution);
    }

    private static boolean failedWithoutUsablePower(ScalarPowerSolution solution) {
        return !solution.reliableForReadout()
                && solution.powerByNode().isEmpty()
                && solution.totalNodePower() == 0.0
                && solution.maxNodePower() == 0.0;
    }

    private static void addRubyCoherentSeedSources(
            ServerLevel level,
            CompiledPortGraph coherentGraph,
            ScalarPowerSolution incoherentSolution,
            Map<PortGraphNode, Double> coherentSourcePowersByNode
    ) {
        Set<PortGraphNode> coherentNodes = new HashSet<>(coherentGraph.nodes());
        Map<PortGraphNode, Double> seedPowerByNode = new HashMap<>();
        Map<BlockPos, Double> seedPowerByRuby = new HashMap<>();

        for (Map.Entry<PortGraphNode, Double> entry : incoherentSolution.powerByNode().entrySet()) {
            PortGraphNode node = entry.getKey();

            if (node.waveKind() != PortWaveKind.OUTGOING || !coherentNodes.contains(node) || !level.isLoaded(node.pos())) {
                continue;
            }

            BlockState state = level.getBlockState(node.pos());
            double seedPower = entry.getValue();

            if (OpticalMaterialProfiles.saturatedCoherentEmissionFor(level, node.pos(), state, seedPower) <= 0.0) {
                continue;
            }

            seedPowerByNode.put(node, seedPower);
            seedPowerByRuby.merge(node.pos(), seedPower, Double::sum);
        }

        for (Map.Entry<PortGraphNode, Double> entry : seedPowerByNode.entrySet()) {
            PortGraphNode node = entry.getKey();
            double totalSeedPower = seedPowerByRuby.getOrDefault(node.pos(), 0.0);

            if (totalSeedPower <= 0.0) {
                continue;
            }

            BlockState state = level.getBlockState(node.pos());
            double totalConvertedPower = OpticalMaterialProfiles.saturatedCoherentEmissionFor(
                    level,
                    node.pos(),
                    state,
                    totalSeedPower
            );
            double convertedPower = totalConvertedPower * entry.getValue() / totalSeedPower;

            if (convertedPower > 0.0) {
                coherentSourcePowersByNode.merge(node, convertedPower, Double::sum);
            }
        }
    }

    private static ScalarPowerSolution combineChannelSolutions(
            ScalarPowerSolution incoherentSolution,
            ScalarPowerSolution coherentSolution
    ) {
        Map<PortGraphNode, Double> totalPowerByNode = mergedPowerMap(
                incoherentSolution.powerByNode(),
                coherentSolution.powerByNode()
        );
        double maxPower = 0.0;
        double totalPower = 0.0;

        for (double power : totalPowerByNode.values()) {
            maxPower = Math.max(maxPower, power);
            totalPower += power;
        }

        List<io.github.yoglappland.spectralization.optics.compiler.ScalarSolverRegionResult> regionResults =
                new ArrayList<>(incoherentSolution.regionResults());
        regionResults.addAll(coherentSolution.regionResults());

        return new ScalarPowerSolution(
                coherentSolution.solverKind() != io.github.yoglappland.spectralization.optics.compiler.ScalarSolverKind.NONE
                        ? coherentSolution.solverKind()
                        : incoherentSolution.solverKind(),
                coherentSolution.solverKind() != io.github.yoglappland.spectralization.optics.compiler.ScalarSolverKind.NONE
                        ? coherentSolution.solverPlan()
                        : incoherentSolution.solverPlan(),
                incoherentSolution.converged() && coherentSolution.converged(),
                incoherentSolution.unstable() || coherentSolution.unstable(),
                incoherentSolution.iterations() + coherentSolution.iterations(),
                Math.max(incoherentSolution.residual(), coherentSolution.residual()),
                maxPower,
                totalPower,
                totalPowerByNode,
                coherentSolution.powerByNode(),
                regionResults
        );
    }

    private static Map<PortGraphNode, Double> mergedPowerMap(
            Map<PortGraphNode, Double> first,
            Map<PortGraphNode, Double> second
    ) {
        Map<PortGraphNode, Double> merged = new HashMap<>(first);

        for (Map.Entry<PortGraphNode, Double> entry : second.entrySet()) {
            if (entry.getValue() > 0.0) {
                merged.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        return merged;
    }

    private static Map<PortGraphNode, Double> sourcePowerMap(PortGraphNode sourceNode, double power) {
        if (power <= 0.0) {
            return Map.of();
        }

        return Map.of(sourceNode, power);
    }

    private static CompiledPortGraph unionGraphs(List<CompiledPortGraph> graphs) {
        if (graphs.size() == 1) {
            return graphs.getFirst();
        }

        return PortGraphCompiler.unionDirectGraphs(graphs);
    }

    private static OutputBeam withCoherence(OutputBeam outputBeam, CoherenceKind coherence) {
        return new OutputBeam(outputBeam.outgoingDirection(), outputBeam.beam().withCoherence(coherence));
    }

    private static double coherentPower(OutputBeam outputBeam) {
        return coherentPower(outputBeam.beam());
    }

    private static double coherentPower(BeamPacket beam) {
        double power = 0.0;

        for (PlaneWaveComponent component : beam.components()) {
            if (component.coherence() == CoherenceKind.COHERENT) {
                power += component.power();
            }
        }

        return power;
    }

    private static double incoherentPower(OutputBeam outputBeam) {
        double power = 0.0;

        for (PlaneWaveComponent component : outputBeam.beam().components()) {
            if (component.coherence() == CoherenceKind.INCOHERENT) {
                power += component.power();
            }
        }

        return power;
    }

    private static List<GraphSourceOutput> discoverGraphSourceOutputs(ServerLevel level, CompiledPortGraph graph) {
        Set<BlockPos> positions = new HashSet<>();
        Set<PortGraphNode> graphNodes = new HashSet<>(graph.nodes());
        List<GraphSourceOutput> sourceOutputs = new ArrayList<>();
        positions.add(graph.sourcePos());

        for (PortGraphNode node : graph.nodes()) {
            positions.add(node.pos());
        }

        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);

            if (!(state.getBlock() instanceof OpticalSource source)) {
                continue;
            }

            for (OutputBeam outputBeam : source.getOutputBeams(state, level, pos)) {
                if (outputBeam.beam().isEmpty()) {
                    continue;
                }

                SourceTraceKey key = new SourceTraceKey(pos, outputBeam.outgoingDirection());
                PortGraphNode sourceNode = PortGraphNode.outgoing(new OpticalPort(pos, outputBeam.outgoingDirection()));

                if (!graphNodes.contains(sourceNode)) {
                    continue;
                }

                sourceOutputs.add(new GraphSourceOutput(key, sourceNode, outputBeam));
            }
        }

        return sourceOutputs;
    }

    private static IntSet networkIdsFor(LevelTraceCache cache, Set<SourceTraceKey> sourceKeys) {
        IntSet networkIds = new IntOpenHashSet();

        for (SourceTraceKey sourceKey : sourceKeys) {
            DirectSourceRecord record = cache.directSourcesByKey.get(sourceKey);

            if (record != null) {
                networkIds.add(record.networkId());
            }
        }

        return networkIds;
    }

    private static int directGeometryCacheLimitFor(int signaturePositions) {
        int extraEntries = Math.max(0, signaturePositions + GEOMETRY_POSITIONS_PER_EXTRA_CACHE_ENTRY - 1)
                / GEOMETRY_POSITIONS_PER_EXTRA_CACHE_ENTRY;
        int proposedLimit = MIN_DIRECT_GEOMETRY_CACHE_ENTRIES + extraEntries;

        return Math.min(MAX_DIRECT_GEOMETRY_CACHE_ENTRIES, Math.max(MIN_DIRECT_GEOMETRY_CACHE_ENTRIES, proposedLimit));
    }

    private static boolean sharesAnyNode(CompiledPortGraph left, CompiledPortGraph right) {
        Set<PortGraphNode> leftNodes = new HashSet<>(left.nodes());

        for (PortGraphNode node : right.nodes()) {
            if (leftNodes.contains(node)) {
                return true;
            }
        }

        return false;
    }

    private static void pruneInactiveSources(ServerLevel level, LevelTraceCache cache) {
        List<SourceTraceKey> inactiveKeys = new ArrayList<>();

        for (SourceTraceKey sourceKey : cache.directSourcesByKey.keySet()) {
            if (!level.isLoaded(sourceKey.sourcePos())
                    || !(level.getBlockState(sourceKey.sourcePos()).getBlock() instanceof OpticalSource)) {
                inactiveKeys.add(sourceKey);
            }
        }

        for (SourceTraceKey inactiveKey : inactiveKeys) {
            DirectSourceRecord record = cache.directSourcesByKey.remove(inactiveKey);

            if (record != null) {
                cache.systemIdsByNetwork.remove(record.networkId());
            }
        }
    }

    private static LevelTraceCache cacheFor(ServerLevel level) {
        synchronized (CACHES) {
            return CACHES.computeIfAbsent(level, ignored -> new LevelTraceCache());
        }
    }

    private record SourceTraceKey(BlockPos sourcePos, Direction direction) {
        private SourceTraceKey {
            sourcePos = sourcePos.immutable();
            Objects.requireNonNull(direction, "direction");
        }
    }

    private record TraceRequest(int networkId, BlockPos sourcePos, OutputBeam sourceOutput) {
        private TraceRequest {
            sourcePos = sourcePos.immutable();
            Objects.requireNonNull(sourceOutput, "sourceOutput");
        }
    }

    private record DirectSourceRecord(
            int networkId,
            CompiledPortGraph graph,
            CompiledPortGraph passiveGraph,
            CompiledPortGraph coherentGraph,
            OutputBeam sourceOutput,
            DirectCompilationKey compilationKey,
            OpticalEpochs epochs
    ) {
        private DirectSourceRecord {
            if (networkId <= 0) {
                throw new IllegalArgumentException("Direct source network id must be positive");
            }

            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(passiveGraph, "passiveGraph");
            Objects.requireNonNull(coherentGraph, "coherentGraph");
            Objects.requireNonNull(sourceOutput, "sourceOutput");
            Objects.requireNonNull(epochs, "epochs");
        }
    }

    private record GraphSourceOutput(SourceTraceKey key, PortGraphNode sourceNode, OutputBeam outputBeam) {
        private GraphSourceOutput {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(sourceNode, "sourceNode");
            Objects.requireNonNull(outputBeam, "outputBeam");
        }
    }

    private record BuiltOpticalSystem(CachedOpticalSystem system, Set<SourceTraceKey> sourceKeys) {
        private BuiltOpticalSystem {
            Objects.requireNonNull(system, "system");
            Objects.requireNonNull(sourceKeys, "sourceKeys");
            sourceKeys = Set.copyOf(sourceKeys);
        }
    }

    private record GraphExpansion(List<CompiledPortGraph> graphs, Set<SourceTraceKey> sourceKeys) {
        private GraphExpansion {
            Objects.requireNonNull(graphs, "graphs");
            Objects.requireNonNull(sourceKeys, "sourceKeys");
            graphs = List.copyOf(graphs);
            sourceKeys = Set.copyOf(sourceKeys);
        }
    }

    private record GeometrySignature(long hash, int positionCount) {
        private static GeometrySignature capture(ServerLevel level, LongSet positions) {
            if (positions == null || positions.isEmpty()) {
                return null;
            }

            long[] sortedPositions = new long[positions.size()];
            int index = 0;

            for (long position : positions) {
                sortedPositions[index++] = position;
            }

            Arrays.sort(sortedPositions);

            long hash = GEOMETRY_SIGNATURE_SEED;

            for (long positionLong : sortedPositions) {
                BlockPos pos = BlockPos.of(positionLong);
                int stateId = 0;

                if (level.isLoaded(pos)) {
                    BlockState state = level.getBlockState(pos);
                    stateId = normalizedOpticalStateId(level, pos, state);
                }

                hash = mix(hash, positionLong);
                hash = mix(hash, stateId);
            }

            return new GeometrySignature(hash, sortedPositions.length);
        }

        private static int normalizedOpticalStateId(ServerLevel level, BlockPos pos, BlockState state) {
            if (state.getBlock() instanceof CmosSensorBlock) {
                state = state
                        .setValue(CmosSensorBlock.POWER, 0)
                        .setValue(CmosSensorBlock.LOGARITHMIC, false);
            }

            int stateId = Block.getId(state);

            if (state.is(Blocks.GLOWSTONE) && level.hasNeighborSignal(pos)) {
                stateId ^= 0x40000000;
            }

            return stateId;
        }

        private static long mix(long hash, long value) {
            long mixedValue = value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
            return hash ^ mixedValue;
        }
    }

    private record DirectCompilationKey(
            SourceTraceKey sourceKey,
            OutputBeam sourceOutput,
            GeometrySignature geometrySignature
    ) {
        private DirectCompilationKey {
            Objects.requireNonNull(sourceKey, "sourceKey");
            Objects.requireNonNull(sourceOutput, "sourceOutput");
            Objects.requireNonNull(geometrySignature, "geometrySignature");
        }
    }

    private record DirectCompilation(
            CompiledPortGraph graph,
            CompiledPortGraph passiveGraph,
            CompiledPortGraph coherentGraph,
            CompiledReadoutLayer readoutLayer,
            ScalarPowerSolution solution
    ) {
        private DirectCompilation {
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(passiveGraph, "passiveGraph");
            Objects.requireNonNull(coherentGraph, "coherentGraph");
            Objects.requireNonNull(readoutLayer, "readoutLayer");
            Objects.requireNonNull(solution, "solution");
        }
    }

    private record SystemCompilationKey(List<DirectCompilationKey> sourceCompilationKeys) {
        private SystemCompilationKey {
            Objects.requireNonNull(sourceCompilationKeys, "sourceCompilationKeys");
            sourceCompilationKeys = List.copyOf(sourceCompilationKeys);

            if (sourceCompilationKeys.isEmpty()) {
                throw new IllegalArgumentException("System compilation key must contain at least one source");
            }
        }

        private static SystemCompilationKey of(LevelTraceCache cache, Set<SourceTraceKey> sourceKeys) {
            List<DirectCompilationKey> keys = new ArrayList<>();

            for (SourceTraceKey sourceKey : sourceKeys) {
                DirectSourceRecord record = cache.directSourcesByKey.get(sourceKey);

                if (record == null || record.compilationKey() == null) {
                    return null;
                }

                keys.add(record.compilationKey());
            }

            if (keys.isEmpty()) {
                return null;
            }

            keys.sort(Comparator
                    .comparingInt((DirectCompilationKey key) -> key.sourceKey().sourcePos().getX())
                    .thenComparingInt(key -> key.sourceKey().sourcePos().getY())
                    .thenComparingInt(key -> key.sourceKey().sourcePos().getZ())
                    .thenComparingInt(key -> key.sourceKey().direction().ordinal())
                    .thenComparingLong(key -> key.geometrySignature().hash())
                    .thenComparingInt(key -> key.geometrySignature().positionCount()));

            return new SystemCompilationKey(keys);
        }
    }

    private record ReadoutSignature(String key) {
        private static ReadoutSignature of(List<ReceiverOutput> receiverOutputs) {
            if (receiverOutputs.isEmpty()) {
                return new ReadoutSignature("");
            }

            TreeSet<String> keys = new TreeSet<>();

            for (ReceiverOutput receiverOutput : receiverOutputs) {
                ReceiverOutput.ReceiverOutputKey outputKey = receiverOutput.key();
                keys.add(outputKey.kind()
                        + "@"
                        + outputKey.pos().getX()
                        + ","
                        + outputKey.pos().getY()
                        + ","
                        + outputKey.pos().getZ()
                        + ":"
                        + outputKey.positiveZ());
            }

            return new ReadoutSignature(String.join("|", keys));
        }

        private boolean empty() {
            return key.isEmpty();
        }
    }

    private record SystemLegacyReadout(List<ReceiverOutput> receiverOutputs, boolean complete, boolean comparable) {
        private SystemLegacyReadout {
            Objects.requireNonNull(receiverOutputs, "receiverOutputs");
            receiverOutputs = List.copyOf(receiverOutputs);
        }
    }

    private record ReadoutApplyDebugState(
            String mode,
            boolean reliable,
            boolean systemRebuildPending,
            int directSourceOutputCount,
            boolean systemAvailable,
            int systemId,
            int systemSourceCount,
            boolean systemUsableForGameplay
    ) {
        private ReadoutApplyDebugState {
            Objects.requireNonNull(mode, "mode");
        }
    }

    private static final class LevelTraceCache {
        private final OpticalDependencyIndex dependencyIndex = new OpticalDependencyIndex();
        private final Map<SourceTraceKey, Integer> networkIdsBySource = new HashMap<>();
        private final Map<Integer, CachedOpticalTrace> cachedTracesByNetwork = new HashMap<>();
        private final Map<Integer, CachedOpticalSystem> cachedSystemsBySystem = new HashMap<>();
        private final Map<Integer, CachedOpticalSystem> lastUsableSystemsBySystem = new HashMap<>();
        private final Map<ReadoutSignature, List<ReceiverOutput>> lastReliableReceiverOutputsByReadout = new HashMap<>();
        private final Map<Integer, Integer> systemIdsByNetwork = new HashMap<>();
        private final Map<Integer, IntSet> networkIdsBySystem = new HashMap<>();
        private final Map<SourceTraceKey, DirectSourceRecord> directSourcesByKey = new HashMap<>();
        private final Map<Integer, LongSet> geometrySignaturePositionsByNetwork = new HashMap<>();
        private final Map<Integer, Long> lastRequestTicksByNetwork = new HashMap<>();
        private final Map<Integer, Long> lastDirtyTicksByNetwork = new HashMap<>();
        private final Map<DirectCompilationKey, DirectCompilation> directCompilationCache =
                new LinkedHashMap<>(128, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<DirectCompilationKey, DirectCompilation> eldest) {
                        return size() > directCompilationCacheEntryLimit;
                    }
                };
        private final Map<SystemCompilationKey, CachedOpticalSystem> systemCompilationCache =
                new LinkedHashMap<>(MIN_SYSTEM_COMPILATION_CACHE_ENTRIES, 0.75F, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<SystemCompilationKey, CachedOpticalSystem> eldest) {
                        return size() > SpectralizationConfig.opticalCompilerSystemCacheMaxEntries();
                    }
                };
        private final Map<SourceTraceKey, Long> lastCompilerDebugTickBySource = new HashMap<>();
        private final Map<Integer, Long> lastReadoutApplyDebugTickByNetwork = new HashMap<>();
        private final Map<Integer, ReadoutApplyDebugState> lastReadoutApplyDebugStateByNetwork = new HashMap<>();
        private final ArrayDeque<TraceRequest> pendingRequests = new ArrayDeque<>();
        private final IntSet queuedNetworkIds = new IntOpenHashSet();
        private final ArrayDeque<Integer> pendingSystemRebuilds = new ArrayDeque<>();
        private final IntSet queuedSystemRebuildNetworkIds = new IntOpenHashSet();
        private final IntSet appliedSystemIdsThisTick = new IntOpenHashSet();
        private final Set<ReadoutSignature> appliedHeldReadoutsThisTick = new HashSet<>();
        private int nextNetworkId = 1;
        private int directCompilationCacheEntryLimit = MIN_DIRECT_GEOMETRY_CACHE_ENTRIES;
        private OpticalEpochs epochs = OpticalEpochs.ZERO;
        private long compilerDebugRunTick = Long.MIN_VALUE;
        private int compilerDebugRunsThisTick = 0;
        private long systemApplyTick = Long.MIN_VALUE;
        private long lastDirectWorkTick = Long.MIN_VALUE;
        private long nextReadoutStep = 1L;
        private boolean lastSystemRebuildCacheHit = false;

        int networkIdFor(SourceTraceKey key) {
            return networkIdsBySource.computeIfAbsent(key, ignored -> nextNetworkId++);
        }

        void enqueue(TraceRequest request, long gameTime) {
            lastRequestTicksByNetwork.put(request.networkId(), gameTime);

            if (queuedNetworkIds.add(request.networkId())) {
                pendingRequests.addLast(request);
            }
        }

        void requeueDeferred(TraceRequest request) {
            if (queuedNetworkIds.add(request.networkId())) {
                pendingRequests.addLast(request);
            }
        }

        void enqueueSystemRebuild(int networkId) {
            if (queuedSystemRebuildNetworkIds.add(networkId)) {
                pendingSystemRebuilds.addLast(networkId);
            }
        }

        void unregisterDirectSourceForNetwork(int networkId) {
            directSourcesByKey.entrySet().removeIf(entry -> entry.getValue().networkId() == networkId);
            IntSet networkIds = new IntOpenHashSet();
            networkIds.add(networkId);
            removeSystemsForNetworkIds(networkIds);
            queuedSystemRebuildNetworkIds.remove(networkId);
            pendingSystemRebuilds.removeIf(queuedNetworkId -> queuedNetworkId == networkId);
        }

        boolean hasPendingSystemRebuilds() {
            return !pendingSystemRebuilds.isEmpty();
        }

        int pendingSystemRebuildCount() {
            return pendingSystemRebuilds.size();
        }

        boolean isSystemRebuildPending(int networkId) {
            return queuedSystemRebuildNetworkIds.contains(networkId);
        }

        Integer pollPendingSystemRebuildNetworkId() {
            if (pendingSystemRebuilds.isEmpty()) {
                return null;
            }

            int networkId = pendingSystemRebuilds.removeFirst();
            queuedSystemRebuildNetworkIds.remove(networkId);
            return networkId;
        }

        boolean quietEnoughForSystemRebuild(long gameTime) {
            if (lastDirectWorkTick == Long.MIN_VALUE) {
                return true;
            }

            return gameTime - lastDirectWorkTick >= SpectralizationConfig.opticalCompilerSystemRebuildQuietTicks();
        }

        long lastActivityTickForNetwork(int networkId) {
            long requestTick = lastRequestTicksByNetwork.getOrDefault(networkId, Long.MIN_VALUE);
            long dirtyTick = lastDirtyTicksByNetwork.getOrDefault(networkId, Long.MIN_VALUE);

            return Math.max(requestTick, dirtyTick);
        }

        boolean shouldRunCompilerDebug(SourceTraceKey key, long gameTime) {
            if (compilerDebugRunTick != gameTime) {
                compilerDebugRunTick = gameTime;
                compilerDebugRunsThisTick = 0;
            }

            if (compilerDebugRunsThisTick >= SpectralizationConfig.opticalCompilerDebugMaxRunsPerTick()) {
                return false;
            }

            long lastRun = lastCompilerDebugTickBySource.getOrDefault(key, Long.MIN_VALUE);

            if (lastRun != Long.MIN_VALUE
                    && gameTime - lastRun < SpectralizationConfig.opticalCompilerDebugSampleIntervalTicks()) {
                return false;
            }

            lastCompilerDebugTickBySource.put(key, gameTime);
            compilerDebugRunsThisTick++;
            return true;
        }

        boolean shouldRunReadoutApplyDebug(
                int networkId,
                ReadoutApplyDebugState state,
                long gameTime
        ) {
            ReadoutApplyDebugState lastState = lastReadoutApplyDebugStateByNetwork.get(networkId);
            long lastRun = lastReadoutApplyDebugTickByNetwork.getOrDefault(networkId, Long.MIN_VALUE);
            boolean stateChanged = !state.equals(lastState);

            if (!stateChanged
                    && lastRun != Long.MIN_VALUE
                    && gameTime - lastRun < SpectralizationConfig.opticalCompilerDebugReadoutApplyIntervalTicks()) {
                return false;
            }

            lastReadoutApplyDebugStateByNetwork.put(networkId, state);
            lastReadoutApplyDebugTickByNetwork.put(networkId, gameTime);
            return true;
        }

        long nextReadoutStep() {
            return nextReadoutStep++;
        }

        void applyPendingCachedOutputs(Level level, boolean reliable) {
            long readoutStep = nextReadoutStep();
            Set<Integer> appliedNetworkIds = new HashSet<>();

            for (TraceRequest request : pendingRequests) {
                if (!appliedNetworkIds.add(request.networkId())) {
                    continue;
                }

                CachedOpticalTrace cachedTrace = cachedTracesByNetwork.get(request.networkId());

                if (cachedTrace != null) {
                    cachedTrace.applyOutputs(level, reliable, readoutStep);
                }
            }
        }

        boolean markChanged(BlockPos pos, OpticalDirtyKind dirtyKind, long gameTime) {
            IntSet affectedNetworkIds = dependencyIndex.markChangedAndGet(pos);

            if (affectedNetworkIds.isEmpty()) {
                return false;
            }

            epochs = epochs.advance(dirtyKind);

            for (int networkId : affectedNetworkIds) {
                lastDirtyTicksByNetwork.put(networkId, gameTime);
                markSystemDirty(networkId, dirtyKind);
            }

            return true;
        }

        boolean markParameterChanged(ServerLevel level, BlockPos pos) {
            boolean changed = false;

            if (isDirectOpticalParameterDependency(level, pos)) {
                changed |= markChanged(pos, OpticalDirtyKind.PARAMETER, level.getGameTime());
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);

                if (isPoweredMaterialParameterDependency(level, neighborPos)) {
                    OpticalWorldIndex.markChanged(level, neighborPos, OpticalDirtyKind.PARAMETER);
                    changed |= markChanged(neighborPos, OpticalDirtyKind.PARAMETER, level.getGameTime());
                }
            }

            return changed;
        }

        private static boolean isDirectOpticalParameterDependency(ServerLevel level, BlockPos pos) {
            if (!level.isLoaded(pos)) {
                return false;
            }

            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof CmosSensorBlock || state.getBlock() instanceof PassThroughSensorBlock) {
                return false;
            }

            return state.is(Blocks.GLOWSTONE)
                    || state.getBlock() instanceof OpticalSource
                    || state.getBlock() instanceof OpticalElement;
        }

        private static boolean isPoweredMaterialParameterDependency(ServerLevel level, BlockPos pos) {
            return level.isLoaded(pos) && level.getBlockState(pos).is(Blocks.GLOWSTONE);
        }

        CachedOpticalSystem systemForNetwork(int networkId) {
            Integer systemId = systemIdsByNetwork.get(networkId);

            if (systemId == null) {
                return null;
            }

            return cachedSystemsBySystem.get(systemId);
        }

        CachedOpticalSystem lastUsableSystemForNetwork(int networkId) {
            Integer systemId = systemIdsByNetwork.get(networkId);

            if (systemId == null) {
                return null;
            }

            return lastUsableSystemsBySystem.get(systemId);
        }

        void rememberUsableSystem(CachedOpticalSystem system) {
            lastUsableSystemsBySystem.put(system.systemId(), system);
            rememberReliableReceiverOutputs(system.receiverOutputs());
        }

        List<ReceiverOutput> lastReliableReceiverOutputsFor(CachedOpticalSystem system) {
            ReadoutSignature signature = ReadoutSignature.of(system.receiverOutputs());

            if (signature.empty()) {
                return List.of();
            }

            return lastReliableReceiverOutputsByReadout.getOrDefault(signature, List.of());
        }

        SourceTraceKey sourceKeyForNetwork(int networkId) {
            for (Map.Entry<SourceTraceKey, DirectSourceRecord> entry : directSourcesByKey.entrySet()) {
                if (entry.getValue().networkId() == networkId) {
                    return entry.getKey();
                }
            }

            return null;
        }

        IntSet systemNetworkIdsForNetwork(int networkId) {
            Integer systemId = systemIdsByNetwork.get(networkId);
            IntSet networkIds = new IntOpenHashSet();

            if (systemId == null) {
                networkIds.add(networkId);
                return networkIds;
            }

            IntSet systemNetworkIds = networkIdsBySystem.get(systemId);

            if (systemNetworkIds == null || systemNetworkIds.isEmpty()) {
                networkIds.add(networkId);
            } else {
                networkIds.addAll(systemNetworkIds);
            }

            return networkIds;
        }

        void installSystem(CachedOpticalSystem system, IntSet networkIds) {
            removeSystemsForNetworkIds(networkIds);
            cachedSystemsBySystem.put(system.systemId(), system);
            networkIdsBySystem.put(system.systemId(), new IntOpenHashSet(networkIds));

            for (int networkId : networkIds) {
                systemIdsByNetwork.put(networkId, system.systemId());
            }
        }

        void dropQueuedSystemRebuilds(IntSet networkIds) {
            if (networkIds.isEmpty()) {
                return;
            }

            for (int networkId : networkIds) {
                queuedSystemRebuildNetworkIds.remove(networkId);
            }

            pendingSystemRebuilds.removeIf(networkIds::contains);
        }

        void clearSystemMappingForNetwork(int networkId) {
            Integer systemId = systemIdsByNetwork.remove(networkId);

            if (systemId == null) {
                return;
            }

            IntSet networkIds = networkIdsBySystem.get(systemId);

            if (networkIds != null) {
                networkIds.remove(networkId);

                if (networkIds.isEmpty()) {
                    networkIdsBySystem.remove(systemId);
                    cachedSystemsBySystem.remove(systemId);
                }
            }
        }

        private void removeSystemsForNetworkIds(IntSet networkIds) {
            IntSet oldSystemIds = new IntOpenHashSet();

            for (int networkId : networkIds) {
                Integer oldSystemId = systemIdsByNetwork.remove(networkId);

                if (oldSystemId != null) {
                    oldSystemIds.add(oldSystemId);
                }
            }

            for (int oldSystemId : oldSystemIds) {
                cachedSystemsBySystem.remove(oldSystemId);
                IntSet oldNetworkIds = networkIdsBySystem.remove(oldSystemId);

                if (oldNetworkIds == null) {
                    continue;
                }

                for (int oldNetworkId : oldNetworkIds) {
                    if (!networkIds.contains(oldNetworkId)) {
                        systemIdsByNetwork.remove(oldNetworkId);
                        enqueueSystemRebuild(oldNetworkId);
                    }
                }
            }
        }

        boolean markHeldReadoutApplied(CachedOpticalSystem system, long gameTime) {
            if (systemApplyTick != gameTime) {
                systemApplyTick = gameTime;
                appliedSystemIdsThisTick.clear();
                appliedHeldReadoutsThisTick.clear();
            }

            ReadoutSignature signature = ReadoutSignature.of(system.receiverOutputs());

            return !signature.empty() && appliedHeldReadoutsThisTick.add(signature);
        }

        GeometrySignature geometrySignatureFor(ServerLevel level, int networkId) {
            LongSet positions = geometrySignaturePositionsByNetwork.get(networkId);

            if (positions == null || positions.isEmpty()) {
                positions = dependencyIndex.dependenciesFor(networkId);
            }

            return GeometrySignature.capture(level, positions);
        }

        GeometrySignature rememberGeometrySignaturePositions(ServerLevel level, int networkId, LongSet dependencies) {
            LongSet positions = geometrySignaturePositionsByNetwork.computeIfAbsent(
                    networkId,
                    ignored -> new LongOpenHashSet()
            );

            if (positions.size() + dependencies.size() > MAX_GEOMETRY_SIGNATURE_POSITIONS) {
                positions.clear();
            }

            positions.addAll(dependencies);
            updateDirectCompilationCacheEntryLimit(positions.size());
            return GeometrySignature.capture(level, positions);
        }

        private void updateDirectCompilationCacheEntryLimit(int signaturePositions) {
            int nextLimit = directGeometryCacheLimitFor(signaturePositions);

            if (nextLimit > directCompilationCacheEntryLimit) {
                directCompilationCacheEntryLimit = nextLimit;
            }
        }

        boolean markSystemApplied(int systemId, long gameTime) {
            if (systemApplyTick != gameTime) {
                systemApplyTick = gameTime;
                appliedSystemIdsThisTick.clear();
                appliedHeldReadoutsThisTick.clear();
            }

            return appliedSystemIdsThisTick.add(systemId);
        }

        private void rememberReliableReceiverOutputs(List<ReceiverOutput> receiverOutputs) {
            ReadoutSignature signature = ReadoutSignature.of(receiverOutputs);

            if (!signature.empty()) {
                lastReliableReceiverOutputsByReadout.put(signature, List.copyOf(receiverOutputs));
            }
        }

        private void markSystemDirty(int networkId, OpticalDirtyKind dirtyKind) {
            Integer systemId = systemIdsByNetwork.get(networkId);

            if (systemId == null) {
                dependencyIndex.markDirty(networkId);
                return;
            }

            cachedSystemsBySystem.remove(systemId);
            if (dirtyKind == OpticalDirtyKind.STRUCTURE) {
                lastUsableSystemsBySystem.remove(systemId);
            }
            IntSet systemNetworkIds = networkIdsBySystem.get(systemId);

            if (systemNetworkIds == null || systemNetworkIds.isEmpty()) {
                dependencyIndex.markDirty(networkId);
                return;
            }

            for (int systemNetworkId : systemNetworkIds) {
                dependencyIndex.markDirty(systemNetworkId);
            }
        }
    }

    private OpticalTraceCache() {
    }
}
