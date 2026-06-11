package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.block.SpectrometerBlock;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.IntrinsicOpticalSources;
import io.github.yoglappland.spectralization.optics.OpticalPort;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalPathTracer;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.OpticalTraceStep;
import io.github.yoglappland.spectralization.optics.OpticalTraceTermination;
import io.github.yoglappland.spectralization.optics.OpticalTraceTerminationReason;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.OpticalSpotTracker;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.geometry.BeamPathOverlayTracker;
import io.github.yoglappland.spectralization.optics.pump.OpticalPumpSources;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.CompiledReadoutLayer;
import io.github.yoglappland.spectralization.optics.compiler.CompiledSpotLayer;
import io.github.yoglappland.spectralization.optics.compiler.OpticalCompilerDebugLogger;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphCompiler;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortWaveKind;
import io.github.yoglappland.spectralization.optics.compiler.OpticalReadoutLayerCompiler;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolver;
import io.github.yoglappland.spectralization.optics.compiler.ScalarSolverKind;
import io.github.yoglappland.spectralization.optics.compiler.ScalarSolverPlan;
import io.github.yoglappland.spectralization.optics.compiler.SpectralPowerLane;
import io.github.yoglappland.spectralization.optics.compiler.gain.GainSchedule;
import io.github.yoglappland.spectralization.optics.compiler.gain.GainSchedulers;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import io.github.yoglappland.spectralization.optics.validation.OpticalTraceValidator;
import io.github.yoglappland.spectralization.optics.world.OpticalDormantSourceData;
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
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
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
    private static final long HUD_OVERLAY_SEND_INTERVAL_TICKS = 2L;
    private static final long HUD_OVERLAY_REFRESH_INTERVAL_TICKS = 80L;
    private static final int DORMANT_SOURCE_WAKE_BUDGET_PER_TICK = 64;
    private static final int MAX_RUBY_DERIVED_COHERENT_SOURCES = 96;
    private static final double RUBY_DERIVED_COHERENT_SOURCE_MIN_POWER = 1.0E-6D;
    private static final int SPECTRAL_LANE_PARALLEL_MIN_LANES = 2;
    private static final int SPECTRAL_LANE_PARALLEL_MIN_WORK = 32;
    private static final Comparator<SourceTraceKey> SOURCE_TRACE_KEY_COMPARATOR = Comparator
            .comparingInt((SourceTraceKey key) -> key.sourcePos().getX())
            .thenComparingInt(key -> key.sourcePos().getY())
            .thenComparingInt(key -> key.sourcePos().getZ())
            .thenComparingInt(key -> key.direction().ordinal());
    private static final Comparator<RubyModeKey> RUBY_MODE_KEY_COMPARATOR = Comparator
            .comparingInt((RubyModeKey key) -> key.pos().getX())
            .thenComparingInt(key -> key.pos().getY())
            .thenComparingInt(key -> key.pos().getZ())
            .thenComparingInt(key -> key.direction().ordinal());
    private static final Comparator<RubyCoherentSource> RUBY_COHERENT_SOURCE_COMPARATOR = Comparator
            .comparingInt((RubyCoherentSource source) -> source.pos().getX())
            .thenComparingInt(source -> source.pos().getY())
            .thenComparingInt(source -> source.pos().getZ())
            .thenComparingInt(source -> source.direction().ordinal());

    public static void requestOrApply(Level level, BlockPos sourcePos, OutputBeam sourceOutput) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || sourceOutput.beam().isEmpty()) {
            return;
        }

        rememberPersistentSourceState(serverLevel, sourcePos);

        LevelTraceCache cache = cacheFor(serverLevel);
        SourceTraceKey key = new SourceTraceKey(sourcePos, sourceOutput.outgoingDirection());
        int networkId = cache.networkIdFor(key);
        CachedOpticalTrace cachedTrace = cache.cachedTracesByNetwork.get(networkId);

        if (!OpticalWorldIndex.canRunDerived(serverLevel)) {
            if (cachedTrace != null) {
                applyInterruptedAuthoritativeOutputs(
                        level,
                        cache,
                        networkId,
                        cachedTrace,
                        "derived_interrupted"
                );
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

        if (cachedTrace != null) {
            applyInterruptedAuthoritativeOutputs(
                    level,
                    cache,
                    networkId,
                    cachedTrace,
                    "dirty_or_changed_trace"
            );
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
            boolean changed = cache.markParameterChanged(serverLevel, pos);
            if (changed) {
                OpticalSpotTracker.clear(serverLevel);
                flushRetiredHudOwners(serverLevel, cache);
            }
            return changed;
        }

        boolean changed = cache.markChanged(serverLevel, pos, dirtyKind, serverLevel.getGameTime());
        if (changed) {
            OpticalSpotTracker.clear(serverLevel);
            flushRetiredHudOwners(serverLevel, cache);
        }
        return changed;
    }

    public static void markSurfaceChanged(LevelAccessor level, SurfaceKey key) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LevelTraceCache cache = cacheFor(serverLevel);
        OpticalSpotTracker.clear(serverLevel);
        cache.applyKnownCachedOutputsAsInterrupted(serverLevel);
        cache.invalidateSurfaceParameterData();
        cache.markChanged(serverLevel, key.pos(), OpticalDirtyKind.PARAMETER, serverLevel.getGameTime());
        cache.markChanged(serverLevel, key.pos().relative(key.side()), OpticalDirtyKind.PARAMETER, serverLevel.getGameTime());
        OpticalWorldIndex.markDataChanged(serverLevel, key.pos());
        OpticalWorldIndex.markDataChanged(serverLevel, key.pos().relative(key.side()));
    }

    public static void requestIntrinsicSourcesNear(LevelAccessor accessor, BlockPos center) {
        if (!(accessor instanceof ServerLevel level)) {
            return;
        }

        LevelTraceCache cache = cacheFor(level);
        long gameTime = level.getGameTime();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);

                    if (!level.isLoaded(pos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);

                    if (!IntrinsicOpticalSources.isBuiltInSource(state)) {
                        continue;
                    }

                    for (OutputBeam outputBeam : IntrinsicOpticalSources.builtInOutputBeams(state)) {
                        if (outputBeam.beam().isEmpty()) {
                            continue;
                        }

                        SourceTraceKey key = new SourceTraceKey(pos, outputBeam.outgoingDirection());
                        int networkId = cache.networkIdFor(key);
                        cache.enqueuePriority(new TraceRequest(networkId, pos, outputBeam), gameTime);
                    }
                }
            }
        }
    }

    public static void rememberSourceState(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel level) || !level.isLoaded(pos)) {
            return;
        }

        if (isPersistentDormantSource(level.getBlockState(pos))) {
            OpticalDormantSourceData.recordSource(level, pos);
        } else {
            OpticalDormantSourceData.removeSource(level, pos);
        }
    }

    public static void forgetDormantSource(LevelAccessor accessor, BlockPos pos) {
        if (accessor instanceof ServerLevel level) {
            OpticalDormantSourceData.removeSource(level, pos);
        }
    }

    public static void processQueues(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            processQueue(level);
        }
        OpticalSpotTracker.refresh(server);
    }

    public static void clearAll() {
        synchronized (CACHES) {
            CACHES.clear();
        }
    }

    public static void clear(LevelAccessor level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        synchronized (CACHES) {
            CACHES.remove(serverLevel);
        }
    }

    private static void processQueue(ServerLevel level) {
        LevelTraceCache cache = cacheFor(level);
        wakeDormantSources(level, cache);
        pruneInactiveSources(level, cache);

        if (!OpticalWorldIndex.canRunDerived(level)) {
            cache.applyPendingCachedOutputs(level, false);
            return;
        }

        long deadlineNanos = System.nanoTime() + SpectralizationConfig.opticalSolverBudgetMicros() * 1_000L;
        int maxRequests = SpectralizationConfig.opticalSolverMaxRequestsPerTick();
        IntSet processedNetworkIds = new IntOpenHashSet();
        IntSet fastSystemRefreshNetworkIds = new IntOpenHashSet();
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

            if (!IntrinsicOpticalSources.isSource(level.getBlockState(request.sourcePos()))) {
                cache.unregisterDirectSourceForNetwork(request.networkId());
                continue;
            }

            CachedOpticalTrace existingTrace = cache.cachedTracesByNetwork.get(request.networkId());
            SourceTraceKey sourceKey = new SourceTraceKey(request.sourcePos(), request.sourceOutput().outgoingDirection());

            GeometrySignature geometrySignature = cache.geometrySignatureFor(level, request.networkId());
            DirectCompilationKey directCompilationKey = geometrySignature == null
                    ? null
                    : new DirectCompilationKey(sourceKey, topologyOutput(request.sourceOutput()), geometrySignature);
            DirectCompilation directCompilation = directCompilationKey == null
                    ? null
                    : cache.directCompilationCache.get(directCompilationKey);
            boolean directGeometryCacheHit = directCompilation != null;

            if (directCompilation == null && shouldDeferDirectRecompile(level, cache, request, existingTrace)) {
                applyInterruptedAuthoritativeOutputs(
                        level,
                        cache,
                        request.networkId(),
                        existingTrace,
                        "deferred_direct_recompile"
                );
                cache.requeueDeferred(request);
                deferredDirectWork = true;
                continue;
            }

            boolean shouldLogCompilerDebug = SpectralizationConfig.opticalCompilerDebugLog()
                    && cache.shouldRunCompilerDebug(sourceKey, level.getGameTime());
            CompiledPortGraph directGraph;
            CompiledPortGraph passiveDirectGraph;
            CompiledPortGraph coherentDirectGraph;
            CompiledPortGraph rawDirectGraph;
            ScalarPowerSolution scalarPowerSolution;
            CompiledReadoutLayer directReadoutLayer;
            PowerChannelSolveResult solvedChannels;

            if (directCompilation != null) {
                rawDirectGraph = directCompilation.graph();
                passiveDirectGraph = directCompilation.passiveGraph();
                coherentDirectGraph = directCompilation.coherentGraph();
                solvedChannels = solvePowerChannels(
                        level,
                        passiveDirectGraph,
                        coherentDirectGraph,
                        sourcePowerMapByLane(passiveDirectGraph.sourceNode(), request.sourceOutput(), CoherenceKind.INCOHERENT),
                        sourcePowerMapByLane(coherentDirectGraph.sourceNode(), request.sourceOutput(), CoherenceKind.COHERENT)
                );
                directGraph = solvedChannels.graph();
                scalarPowerSolution = solvedChannels.solution();
                directReadoutLayer = OpticalReadoutLayerCompiler.compile(level, directGraph);
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
                rawDirectGraph = unionGraphs(List.of(passiveDirectGraph, coherentDirectGraph));
                solvedChannels = solvePowerChannels(
                        level,
                        passiveDirectGraph,
                        coherentDirectGraph,
                        sourcePowerMapByLane(passiveDirectGraph.sourceNode(), request.sourceOutput(), CoherenceKind.INCOHERENT),
                        sourcePowerMapByLane(coherentDirectGraph.sourceNode(), request.sourceOutput(), CoherenceKind.COHERENT)
                );
                directGraph = solvedChannels.graph();
                scalarPowerSolution = solvedChannels.solution();
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
                    directReceiverOutputs,
                    solvedChannels.coherentHudIntent()
            );
            GeometrySignature updatedGeometrySignature = cache.rememberGeometrySignaturePositions(
                    level,
                    request.networkId(),
                    cachedTrace.dependencies()
            );
            DirectCompilationKey updatedDirectCompilationKey = updatedGeometrySignature == null
                    ? directCompilationKey
                    : new DirectCompilationKey(sourceKey, topologyOutput(request.sourceOutput()), updatedGeometrySignature);
            if (updatedGeometrySignature != null) {
                cache.directCompilationCache.put(
                        updatedDirectCompilationKey,
                        new DirectCompilation(
                                rawDirectGraph,
                                passiveDirectGraph,
                                coherentDirectGraph,
                                directReadoutLayer
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
            boolean canTrackDirectSource =
                    directGraph.nodes().size() <= SpectralizationConfig.opticalCompilerFullNetworkMaxNodes();

            if (canTrackDirectSource) {
                cache.directSourcesByKey.put(
                        sourceKey,
                        new DirectSourceRecord(
                                request.networkId(),
                                rawDirectGraph,
                                passiveDirectGraph,
                                coherentDirectGraph,
                                request.sourceOutput(),
                                updatedDirectCompilationKey,
                                cache.epochs
                        )
                );
            } else {
                cache.unregisterDirectSourceForNetwork(request.networkId());
            }

            if (!directReadoutDemand) {
                cache.clearSystemMappingForNetwork(request.networkId());
            }

            if (directReadoutDemand && canTrackDirectSource) {
                cache.enqueueSystemRebuild(request.networkId());

                if (directGeometryCacheHit) {
                    fastSystemRefreshNetworkIds.add(request.networkId());
                }

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

        if (!fastSystemRefreshNetworkIds.isEmpty()) {
            processedNetworkIds.addAll(processFastSystemRefreshes(level, cache, fastSystemRefreshNetworkIds, deadlineNanos));
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

        cache.applyStableCachedSystems(level);
        syncHudViewers(level, cache);
        flushRetiredHudOwners(level, cache);
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
                rebuiltSystem == null ? "none" : rebuiltSystem.solution().solverKind().name(),
                rebuiltSystem == null ? null : rebuiltSystem.baseCoherentGraph(),
                rebuiltSystem == null ? null : rebuiltSystem.coherentGraph()
        );
        return rebuiltNetworkIds;
    }

    private static IntSet processFastSystemRefreshes(
            ServerLevel level,
            LevelTraceCache cache,
            IntSet networkIds,
            long deadlineNanos
    ) {
        IntSet rebuiltNetworkIds = new IntOpenHashSet();

        for (int networkId : networkIds) {
            if (!rebuiltNetworkIds.isEmpty() && System.nanoTime() >= deadlineNanos) {
                break;
            }

            if (!cache.isSystemRebuildPending(networkId)) {
                continue;
            }

            IntSet refreshedNetworkIds = rebuildDirectSystemForNetwork(level, cache, networkId);
            rebuiltNetworkIds.addAll(refreshedNetworkIds);
            CachedOpticalSystem rebuiltSystem = cache.systemForNetwork(networkId);
            OpticalCompilerDebugLogger.logSystemRebuild(
                    level,
                    networkId,
                    refreshedNetworkIds.size(),
                    cache.pendingSystemRebuildCount(),
                    0,
                    cache.lastSystemRebuildCacheHit,
                    cache.systemCompilationCache.size(),
                    SpectralizationConfig.opticalCompilerSystemCacheMaxEntries(),
                    rebuiltSystem == null ? 0 : rebuiltSystem.systemId(),
                    rebuiltSystem == null ? 0 : rebuiltSystem.sourceCount(),
                    rebuiltSystem == null ? 0 : rebuiltSystem.readoutLayer().size(),
                    rebuiltSystem == null ? 0 : rebuiltSystem.receiverOutputs().size(),
                    rebuiltSystem != null && rebuiltSystem.usableForGameplay(),
                    rebuiltSystem == null ? "none" : rebuiltSystem.solution().solverKind().name(),
                    rebuiltSystem == null ? null : rebuiltSystem.baseCoherentGraph(),
                    rebuiltSystem == null ? null : rebuiltSystem.coherentGraph()
            );
        }

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
                && !SpectralizationConfig.laserDamage()
                && !SpectralizationConfig.laserBlindness()) {
            return false;
        }

        int maxGraphNodes = SpectralizationConfig.opticalCompilerLegacyEffectMaxGraphNodes();

        return directGraph.feedbackSccCount() == 0
                && maxGraphNodes > 0
                && directGraph.nodes().size() <= maxGraphNodes;
    }

    private static boolean applyInterruptedAuthoritativeOutputs(
            Level level,
            LevelTraceCache cache,
            int networkId,
            CachedOpticalTrace cachedTrace,
            String modePrefix
    ) {
        long readoutStep = cache.nextReadoutStep();
        CachedOpticalSystem cachedSystem = cache.systemForNetwork(networkId);
        CachedOpticalSystem heldSystem = cache.lastUsableSystemForNetwork(networkId);
        if (heldSystem != null) {
            publishSystemHudOverlay(level, cache, heldSystem, modePrefix + "_held_system");
        } else if (cachedSystem != null) {
            publishSystemHudOverlay(level, cache, cachedSystem, modePrefix + "_current_system");
        } else {
            publishCachedHudOverlay(level, cache, networkId, cachedTrace, modePrefix);
        }
        publishCompiledSpotLayer(level, networkId, cachedTrace, false);

        if (heldSystem != null && cache.markSystemApplied(heldSystem.systemId(), level.getGameTime())) {
            cache.applySystemOutputs(level, heldSystem, false, readoutStep);
            logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, heldSystem,
                    modePrefix + "_held_system", false, readoutStep);
            return false;
        }

        if (heldSystem != null) {
            return false;
        }

        if (cachedSystem != null) {
            List<ReceiverOutput> heldReceiverOutputs = cache.lastReliableReceiverOutputsFor(cachedSystem);
            if (!heldReceiverOutputs.isEmpty() && cache.markHeldReadoutApplied(cachedSystem, level.getGameTime())) {
                applyReceiverOutputs(level, heldReceiverOutputs, false, readoutStep);
                logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, cachedSystem,
                        modePrefix + "_held_readout", false, readoutStep);
                return false;
            }

            if (!heldReceiverOutputs.isEmpty()) {
                return false;
            }

            if (cache.markSystemApplied(cachedSystem.systemId(), level.getGameTime())) {
                cache.applySystemOutputs(level, cachedSystem, false, readoutStep);
                logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, cachedSystem,
                        modePrefix + "_current_system", false, readoutStep);
                return false;
            }

            return false;
        }

        if (cache.hasActiveSystemReadoutOverlap(cachedTrace.sampleCompiledOutputs())) {
            logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, null,
                    modePrefix + "_suppressed_active_system_readout", false, readoutStep);
            return false;
        }

        cachedTrace.applyOutputs(level, false, readoutStep);
        logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, null,
                modePrefix + "_direct_provisional", false, readoutStep);
        return false;
    }

    private static boolean applyCachedOutputs(
            Level level,
            LevelTraceCache cache,
            int networkId,
            CachedOpticalTrace cachedTrace
    ) {
        CachedOpticalSystem cachedSystem = cache.systemForNetwork(networkId);

        if (cache.isSystemRebuildPending(networkId)) {
            return applyInterruptedAuthoritativeOutputs(level, cache, networkId, cachedTrace, "pending_rebuild");
        }

        if (cachedSystem != null) {
            publishSystemHudOverlay(level, cache, cachedSystem, "cached_system");
        } else {
            publishCachedHudOverlay(level, cache, networkId, cachedTrace, "cached");
        }
        publishCompiledSpotLayer(
                level,
                networkId,
                cachedTrace,
                cachedTrace.scalarPowerSolution().reliableForReadout()
        );

        long readoutStep = cache.nextReadoutStep();

        if (cachedSystem != null) {
            if (cachedSystem.usableForGameplay()) {
                if (cache.markSystemApplied(cachedSystem.systemId(), level.getGameTime())) {
                    cache.applySystemOutputs(level, cachedSystem, true, readoutStep);
                    logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, cachedSystem,
                            "cached_system_reliable", true, readoutStep);
                }
                return true;
            }

            CachedOpticalSystem heldSystem = cache.lastUsableSystemForNetwork(networkId);
            if (heldSystem != null && cache.markSystemApplied(heldSystem.systemId(), level.getGameTime())) {
                cache.applySystemOutputs(level, heldSystem, false, readoutStep);
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
                cache.applySystemOutputs(level, cachedSystem, false, readoutStep);
                logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, cachedSystem,
                        "unusable_system_current_outputs", false, readoutStep);
                return false;
            }
        }

        List<ReceiverOutput> directOutputs = cachedTrace.sampleCompiledOutputs();

        if (cache.hasActiveSystemReadoutOverlap(directOutputs)) {
            logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, null,
                    "direct_trace_suppressed_active_system_readout", false, readoutStep);
            return false;
        }

        boolean reliable = !cache.hasSystemAuthorityForNetwork(networkId)
                && cachedTrace.scalarPowerSolution().reliableForReadout();
        applyReceiverOutputs(level, directOutputs, reliable, readoutStep);
        logReadoutApplyIfNeeded(level, cache, networkId, cachedTrace, null,
                reliable ? "direct_trace" : "direct_trace_unreliable_system_authority", reliable, readoutStep);
        return reliable;
    }

    private static void publishCompiledSpotLayer(
            Level level,
            int networkId,
            CachedOpticalTrace cachedTrace,
            boolean reliable
    ) {
        if (!(level instanceof ServerLevel serverLevel) || cachedTrace == null) {
            return;
        }

        OpticalSpotTracker.publishCompiledSpots(
                serverLevel,
                networkId,
                reliable ? cachedTrace.spotRecords() : List.of()
        );
    }

    private static void publishCachedHudOverlay(
            Level level,
            LevelTraceCache cache,
            int networkId,
            CachedOpticalTrace cachedTrace,
            String mode
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || cachedTrace == null) {
            return;
        }

        if (cache.hasSystemAuthorityForNetwork(networkId)) {
            cache.retiredHudOwnerIds.add(networkId);
            return;
        }

        BlockPos sourcePos = cachedTrace.portGraph().sourcePos();
        cache.retiredHudOwnerIds.remove(networkId);

        boolean hasSegments = !cachedTrace.hudSegments().isEmpty();

        if (!hasSegments && !cache.hasSentNonEmptyHudOverlay(networkId)) {
            return;
        }

        if (!cache.shouldAttemptHudOverlay(networkId, serverLevel.getGameTime(), cachedTrace.hudSegments())
                || !(hasSegments
                ? BeamPathOverlayTracker.hasHudViewerNear(serverLevel, sourcePos, cachedTrace.hudSegments())
                : BeamPathOverlayTracker.hasHudViewerNear(serverLevel, sourcePos))) {
            return;
        }

        int sentPlayers = BeamPathOverlayTracker.publish(serverLevel, sourcePos, networkId, cachedTrace.hudSegments());

        if (sentPlayers > 0) {
            cache.markHudOverlaySent(networkId, serverLevel.getGameTime(), cachedTrace.hudSegments());
        }

        if (sentPlayers <= 0 || !SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        BeamHudOverlayDebugState debugState = new BeamHudOverlayDebugState(
                mode,
                cachedTrace.hudSegments().size(),
                sentPlayers
        );

        if (!cache.shouldRunHudOverlayDebug(networkId, debugState, serverLevel.getGameTime())) {
            return;
        }

        OpticalCompilerDebugLogger.logBeamHudOverlay(
                serverLevel,
                networkId,
                sourcePos,
                cachedTrace.portGraph().sourceDirection(),
                mode,
                cachedTrace.hudSegments().size(),
                sentPlayers,
                BeamPathOverlayTracker.terminalRayBlocks(),
                serverLevel.getGameTime()
        );
    }

    private static void publishSystemHudOverlay(
            Level level,
            LevelTraceCache cache,
            CachedOpticalSystem system,
            String mode
    ) {
        if (!(level instanceof ServerLevel serverLevel) || system == null) {
            return;
        }

        List<BeamPathOverlayPayload.Segment> segments = system.hudSegments();
        BlockPos sourcePos = system.graph().sourcePos();
        int overlayId = -system.systemId();
        cache.retiredHudOwnerIds.remove(overlayId);

        boolean hasSegments = !segments.isEmpty();

        if (!hasSegments && !cache.hasSentNonEmptyHudOverlay(overlayId)) {
            return;
        }

        if (!cache.shouldAttemptHudOverlay(overlayId, serverLevel.getGameTime(), segments)
                || !(hasSegments
                ? BeamPathOverlayTracker.hasHudViewerNear(serverLevel, sourcePos, segments)
                : BeamPathOverlayTracker.hasHudViewerNear(serverLevel, sourcePos))) {
            return;
        }

        int sentPlayers = BeamPathOverlayTracker.publish(serverLevel, sourcePos, overlayId, segments);

        if (sentPlayers > 0) {
            cache.markHudOverlaySent(overlayId, serverLevel.getGameTime(), segments);
        }

        if (sentPlayers <= 0 || !SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        BeamHudOverlayDebugState debugState = new BeamHudOverlayDebugState(
                mode,
                segments.size(),
                sentPlayers
        );

        if (!cache.shouldRunHudOverlayDebug(overlayId, debugState, serverLevel.getGameTime())) {
            return;
        }

        OpticalCompilerDebugLogger.logBeamHudOverlay(
                serverLevel,
                overlayId,
                sourcePos,
                system.graph().sourceDirection(),
                mode,
                segments.size(),
                sentPlayers,
                BeamPathOverlayTracker.terminalRayBlocks(),
                serverLevel.getGameTime()
        );
    }

    private static void syncHudViewers(ServerLevel level, LevelTraceCache cache) {
        Set<UUID> currentHudPlayers = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            UUID playerId = player.getUUID();

            if (!BeamPathOverlayTracker.hasHudHelmet(player)) {
                cache.hudHelmetPlayers.remove(playerId);
                continue;
            }

            currentHudPlayers.add(playerId);

            if (cache.hudHelmetPlayers.add(playerId)) {
                publishAllHudOverlaysToPlayer(cache, player);
            }
        }

        cache.hudHelmetPlayers.retainAll(currentHudPlayers);
    }

    private static void publishAllHudOverlaysToPlayer(LevelTraceCache cache, ServerPlayer player) {
        List<CachedOpticalSystem> systems = new ArrayList<>(cache.cachedSystemsBySystem.values());
        systems.sort(Comparator.comparingInt(CachedOpticalSystem::systemId));
        IntSet systemNetworkIds = new IntOpenHashSet();

        for (CachedOpticalSystem system : systems) {
            cache.retiredHudOwnerIds.remove(-system.systemId());

            IntSet networkIds = cache.networkIdsBySystem.get(system.systemId());

            if (networkIds != null) {
                systemNetworkIds.addAll(networkIds);
            }

            if (system.hudSegments().isEmpty()) {
                continue;
            }

            BeamPathOverlayTracker.publishToPlayer(
                    player,
                    system.graph().sourcePos(),
                    -system.systemId(),
                    system.hudSegments()
            );
        }

        List<Map.Entry<Integer, CachedOpticalTrace>> traces = new ArrayList<>(cache.cachedTracesByNetwork.entrySet());
        traces.sort(Map.Entry.comparingByKey());

        for (Map.Entry<Integer, CachedOpticalTrace> entry : traces) {
            int networkId = entry.getKey();

            if (systemNetworkIds.contains(networkId) || cache.hasSystemAuthorityForNetwork(networkId)) {
                BeamPathOverlayTracker.clearForPlayer(player, networkId);
                continue;
            }

            CachedOpticalTrace trace = entry.getValue();
            cache.retiredHudOwnerIds.remove(networkId);

            if (trace.hudSegments().isEmpty()) {
                continue;
            }

            BeamPathOverlayTracker.publishToPlayer(
                    player,
                    trace.portGraph().sourcePos(),
                    networkId,
                    trace.hudSegments()
            );
        }
    }

    private static void flushRetiredHudOwners(ServerLevel level, LevelTraceCache cache) {
        if (cache.retiredHudOwnerIds.isEmpty()) {
            return;
        }

        IntSet ownerIds = new IntOpenHashSet(cache.retiredHudOwnerIds);
        cache.retiredHudOwnerIds.clear();

        for (int ownerId : ownerIds) {
            BeamPathOverlayTracker.clearForHudPlayers(level, ownerId);
        }
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

        List<ReceiverOutput> directOutputs = cachedTrace.sampleCompiledOutputs();
        List<ReceiverOutput> systemOutputs = appliedSystem == null
                ? List.of()
                : cache.mergedActiveSystemOutputsFor(appliedSystem);
        List<ReceiverOutput> heldOutputs = appliedSystem == null
                ? cache.lastReliableReceiverOutputsByReadout.getOrDefault(ReadoutSignature.of(directOutputs), List.of())
                : cache.lastReliableReceiverOutputsFor(appliedSystem);

        OpticalCompilerDebugLogger.logReadoutApplyDetails(
                level,
                networkId,
                cachedTrace.portGraph().sourcePos(),
                cachedTrace.portGraph().sourceDirection(),
                mode,
                reliable,
                readoutStep,
                directOutputs,
                systemOutputs,
                heldOutputs
        );
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
            List<ReceiverOutput> directReceiverOutputs,
            boolean coherentHudIntent
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
        List<SpotRecord> spotRecords = scalarPowerSolution.reliableForReadout()
                ? CompiledSpotLayer.sample(level, portGraph, scalarPowerSolution, request.sourceOutput())
                : List.of();

        return new CachedOpticalTrace(
                request.networkId(),
                request.sourceOutput(),
                receiverOutputs,
                dependencies,
                portGraph,
                readoutLayer,
                scalarPowerSolution,
                BeamPathOverlayTracker.topologySegments(
                        portGraph,
                        coherentHudIntent || BeamPathOverlayTracker.hasCoherentSignal(scalarPowerSolution),
                        scalarPowerSolution
                ),
                spotRecords,
                trace == null ? !scalarPowerSolution.reliableForReadout() : isUnstable(trace)
        );
    }

    private static void addDependency(ServerLevel level, BlockPos pos, LongSet dependencies) {
        dependencies.add(pos.asLong());
        OpticalFieldSources.addPotentialFieldSourceDependencies(level, pos, dependencies);
    }

    private static void addGraphDependencies(ServerLevel level, CompiledPortGraph portGraph, LongSet dependencies) {
        Set<PortGraphNode> outgoingNodesWithPropagation = new HashSet<>();

        for (PortGraphNode node : portGraph.nodes()) {
            addDependency(level, node.pos(), dependencies);
        }

        for (PortGraphEdge edge : portGraph.edges()) {
            addEdgePathDependencies(level, edge.from().pos(), edge.to().pos(), dependencies);

            if (edge.kind() == PortGraphEdgeKind.PROPAGATION && !edge.from().pos().equals(edge.to().pos())) {
                outgoingNodesWithPropagation.add(edge.from());
            }
        }

        for (PortGraphNode node : portGraph.nodes()) {
            if (node.waveKind() == PortWaveKind.OUTGOING && !outgoingNodesWithPropagation.contains(node)) {
                addTerminalRayDependencies(level, node, dependencies);
            }
        }
    }

    private static void addTerminalRayDependencies(ServerLevel level, PortGraphNode node, LongSet dependencies) {
        BlockPos.MutableBlockPos cursor = node.pos().mutable();

        for (int distance = 1; distance <= BeamPathOverlayTracker.terminalRayBlocks(); distance++) {
            cursor.move(node.side());
            addDependency(level, cursor, dependencies);
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

        if (state.getBlock() instanceof BeamProfilerBlock) {
            Direction receivingSide = BeamProfilerBlock.getReceivingSide(state);

            if (step.incomingDirection() == receivingSide) {
                BeamPacket beam = step.interactingBeam();
                double coherentPower = coherentPower(beam);
                receiverOutputs.add(ReceiverOutput.beamProfiler(
                        step.pos(),
                        beam.totalPower(),
                        coherentPower,
                        Math.max(0.0, beam.totalPower() - coherentPower),
                        beam.envelope()
                ));
            }

            return;
        }

        if (state.getBlock() instanceof SpectrometerBlock) {
            Direction receivingSide = SpectrometerBlock.getReceivingSide(state);

            if (step.incomingDirection() == receivingSide) {
                receiverOutputs.add(ReceiverOutput.spectrometer(step.pos(), step.interactingBeam().powerByFrequency()));
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

            for (SourceTraceKey candidateKey : orderedSourceKeys(remainingKeys)) {
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
        List<SourceTraceKey> orderedSourceKeys = orderedSourceKeys(sourceKeys);
        int systemId = Integer.MAX_VALUE;
        boolean structurallyFresh = true;
        boolean parametricallyFresh = true;
        OpticalEpochs currentEpochs = cache.epochs;
        List<CompiledPortGraph> graphs = new ArrayList<>();
        List<CompiledPortGraph> passiveGraphs = new ArrayList<>();
        List<CompiledPortGraph> coherentGraphs = new ArrayList<>();

        for (SourceTraceKey sourceKey : orderedSourceKeys) {
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
                Map<SpectralPowerLane, Map<PortGraphNode, Double>> incoherentSourcePowersByLane =
                        collectGraphSourcePowersByLane(level, cachedSystem.passiveGraph(), CoherenceKind.INCOHERENT);
                Map<SpectralPowerLane, Map<PortGraphNode, Double>> directCoherentSourcePowersByLane =
                        collectGraphSourcePowersByLane(level, cachedSystem.baseCoherentGraph(), CoherenceKind.COHERENT);
                PowerChannelSolveResult solvedChannels = solvePowerChannels(
                        level,
                        cachedSystem.passiveGraph(),
                        cachedSystem.baseCoherentGraph(),
                        incoherentSourcePowersByLane,
                        directCoherentSourcePowersByLane
                );
                ScalarPowerSolution solution = solvedChannels.solution();
                Map<PortGraphNode, Double> sourcePowersByNode = mergedPowerMap(
                        collapseLanePowerMap(incoherentSourcePowersByLane),
                        solvedChannels.coherentSourcePowersByNode()
                );
                CompiledReadoutLayer readoutLayer = OpticalReadoutLayerCompiler.compile(level, solvedChannels.graph());
                List<ReceiverOutput> receiverOutputs = readoutLayer.sample(solution);
                boolean usableForGameplay = structurallyFresh && canUseForGameplay(solvedChannels.graph(), solution);
                boolean coherentHudIntent = solvedChannels.coherentHudIntent()
                        || BeamPathOverlayTracker.hasCoherentSignal(solution);
                List<BeamPathOverlayPayload.Segment> hudSegments = BeamPathOverlayTracker.topologySegments(
                        solvedChannels.coherentGraph(),
                        coherentHudIntent,
                        solution
                );
                CachedOpticalSystem refreshedSystem = new CachedOpticalSystem(
                        systemId,
                        solvedChannels.graph(),
                        cachedSystem.passiveGraph(),
                        cachedSystem.baseCoherentGraph(),
                        solvedChannels.coherentGraph(),
                        sourcePowersByNode,
                        readoutLayer,
                        solution,
                        receiverOutputs,
                        hudSegments,
                        sourcePowersByNode.size(),
                        coherentHudIntent,
                        currentEpochs,
                        structurallyFresh,
                        true,
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
        CompiledPortGraph baseCoherentGraph = unionGraphs(coherentGraphs);
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> incoherentSourcePowersByLane =
                collectGraphSourcePowersByLane(level, passiveGraph, CoherenceKind.INCOHERENT);
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> directCoherentSourcePowersByLane =
                collectGraphSourcePowersByLane(level, baseCoherentGraph, CoherenceKind.COHERENT);
        PowerChannelSolveResult solvedChannels = solvePowerChannels(
                level,
                passiveGraph,
                baseCoherentGraph,
                incoherentSourcePowersByLane,
                directCoherentSourcePowersByLane
        );
        graph = solvedChannels.graph();
        CompiledPortGraph coherentGraph = solvedChannels.coherentGraph();
        ScalarPowerSolution solution = solvedChannels.solution();
        Map<PortGraphNode, Double> sourcePowersByNode = mergedPowerMap(
                collapseLanePowerMap(incoherentSourcePowersByLane),
                solvedChannels.coherentSourcePowersByNode()
        );
        CompiledReadoutLayer readoutLayer = OpticalReadoutLayerCompiler.compile(level, graph);
        List<ReceiverOutput> receiverOutputs = readoutLayer.sample(solution);
        boolean coherentHudIntent = solvedChannels.coherentHudIntent()
                || BeamPathOverlayTracker.hasCoherentSignal(solution);
        List<BeamPathOverlayPayload.Segment> hudSegments = BeamPathOverlayTracker.topologySegments(
                coherentGraph,
                coherentHudIntent,
                solution
        );
        CachedOpticalSystem system = new CachedOpticalSystem(
                systemId,
                graph,
                passiveGraph,
                baseCoherentGraph,
                coherentGraph,
                sourcePowersByNode,
                readoutLayer,
                solution,
                receiverOutputs,
                hudSegments,
                sourcePowersByNode.size(),
                coherentHudIntent,
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
        sortGraphsBySourceKey(graphs);
        Set<SourceTraceKey> compiledSourceKeys = new HashSet<>();

        for (CompiledPortGraph graph : graphs) {
            compiledSourceKeys.add(new SourceTraceKey(graph.sourcePos(), graph.sourceDirection()));
        }

        Set<SourceTraceKey> sourceKeys = new HashSet<>(compiledSourceKeys);

        boolean added;

        do {
            added = false;
            sortGraphsBySourceKey(graphs);
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

    private static Map<SpectralPowerLane, Map<PortGraphNode, Double>> collectGraphSourcePowersByLane(
            ServerLevel level,
            CompiledPortGraph graph,
            CoherenceKind coherence
    ) {
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowersByLane = new HashMap<>();
        Set<PortGraphNode> graphNodes = new HashSet<>(graph.nodes());

        for (GraphSourceOutput sourceOutput : discoverGraphSourceOutputs(level, graph)) {
            PortGraphNode sourceNode = sourceOutput.sourceNode();

            if (!graphNodes.contains(sourceNode)) {
                continue;
            }

            addOutputBeamPowerByLane(sourcePowersByLane, sourceNode, sourceOutput.outputBeam(), coherence);
        }

        return sourcePowersByLane;
    }

    private static PowerChannelSolveResult solvePowerChannels(
            ServerLevel level,
            CompiledPortGraph passiveGraph,
            CompiledPortGraph coherentGraph,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> incoherentSourcePowersByLane,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> directCoherentSourcePowersByLane
    ) {
        ScalarPowerSolution incoherentSolution = solvePowerLanes(
                level,
                "incoherent",
                passiveGraph,
                incoherentSourcePowersByLane
        );
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> coherentSourcePowersByLane =
                copyLanePowerMap(directCoherentSourcePowersByLane);
        RubySeedSynthesis rubySeedSynthesis = synthesizeRubyCoherentSources(level, passiveGraph, incoherentSolution);
        coherentGraph = expandCoherentGraphWithRubySources(level, coherentGraph, rubySeedSynthesis.sources());
        addRubyCoherentSourcePowers(coherentGraph, rubySeedSynthesis.sources(), coherentSourcePowersByLane);
        OpticalCompilerDebugLogger.logRubySeedSynthesis(
                level,
                coherentGraph,
                rubySeedSynthesis.seedNodeCount(),
                rubySeedSynthesis.sourceCount(),
                rubySeedSynthesis.totalStraySeedPower(),
                rubySeedSynthesis.totalCoherentSourcePower(),
                rubySeedSynthesis.maxNodeSourcePower(),
                rubySeedSynthesis.maxPumpRate()
        );
        GainSchedule gainSchedule = GainSchedulers.stableFeedback().schedule(level, coherentGraph);
        OpticalCompilerDebugLogger.logGainSchedule(level, coherentGraph, gainSchedule);
        CompiledPortGraph scheduledCoherentGraph = gainSchedule.graph();
        ScalarPowerSolution coherentSolution = solvePowerLanes(
                level,
                "coherent",
                scheduledCoherentGraph,
                coherentSourcePowersByLane
        );
        Map<PortGraphNode, Double> incoherentSourcePowersByNode = collapseLanePowerMap(incoherentSourcePowersByLane);
        Map<PortGraphNode, Double> directCoherentSourcePowersByNode = collapseLanePowerMap(directCoherentSourcePowersByLane);
        Map<PortGraphNode, Double> coherentSourcePowersByNode = collapseLanePowerMap(coherentSourcePowersByLane);

        ScalarPowerSolution combinedSolution = combineChannelSolutions(incoherentSolution, coherentSolution);
        OpticalCompilerDebugLogger.logPowerChannelSolve(
                level,
                passiveGraph,
                scheduledCoherentGraph,
                incoherentSourcePowersByNode.size(),
                totalSourcePower(incoherentSourcePowersByNode),
                directCoherentSourcePowersByNode.size(),
                totalSourcePower(directCoherentSourcePowersByNode),
                rubySeedSynthesis.seedNodeCount(),
                rubySeedSynthesis.sourceCount(),
                rubySeedSynthesis.totalStraySeedPower(),
                rubySeedSynthesis.totalCoherentSourcePower(),
                coherentSourcePowersByNode.size(),
                totalSourcePower(coherentSourcePowersByNode),
                gainSchedule,
                incoherentSolution,
                coherentSolution,
                combinedSolution
        );

        return new PowerChannelSolveResult(
                unionGraphs(List.of(passiveGraph, scheduledCoherentGraph)),
                scheduledCoherentGraph,
                coherentSourcePowersByNode,
                coherentSourcePowersByLane,
                hasPositiveSource(coherentSourcePowersByNode),
                combinedSolution
        );
    }

    private static RubySeedSynthesis synthesizeRubyCoherentSources(
            ServerLevel level,
            CompiledPortGraph passiveGraph,
            ScalarPowerSolution incoherentSolution
    ) {
        Map<RubyModeKey, Double> seedPowerByMode = new HashMap<>();
        Set<BlockPos> rubySeedPositions = new HashSet<>();
        int maxPumpRate = 0;
        double totalStraySeedPower = 0.0;
        double totalCoherentSourcePower = 0.0;
        double maxNodeSourcePower = 0.0;
        List<RubyCoherentSource> sources = new ArrayList<>();

        SpectralPowerLane rubyStrayLane = new SpectralPowerLane(RubyBlock.RUBY_LINE, CoherenceKind.INCOHERENT);

        for (Map.Entry<PortGraphNode, Double> entry : incoherentSolution.powerByNodeForLane(rubyStrayLane).entrySet()) {
            PortGraphNode node = entry.getKey();

            if (node.waveKind() != PortWaveKind.OUTGOING || !level.isLoaded(node.pos())) {
                continue;
            }

            BlockState state = level.getBlockState(node.pos());

            if (state.getBlock() != Spectralization.RUBY_BLOCK.get()) {
                continue;
            }

            double seedPower = entry.getValue();

            if (seedPower <= 0.0) {
                continue;
            }

            totalStraySeedPower += seedPower;
            int pumpRate = OpticalPumpSources.adjacentPumpRate(level, node.pos());
            maxPumpRate = Math.max(maxPumpRate, pumpRate);
            seedPowerByMode.merge(new RubyModeKey(node.pos(), node.side()), seedPower, Double::sum);
            rubySeedPositions.add(node.pos());
        }

        List<Map.Entry<RubyModeKey, Double>> seedEntries = new ArrayList<>(seedPowerByMode.entrySet());
        seedEntries.sort(Map.Entry.comparingByKey(RUBY_MODE_KEY_COMPARATOR));

        for (Map.Entry<RubyModeKey, Double> entry : seedEntries) {
            RubyModeKey key = entry.getKey();
            BlockPos pos = key.pos();
            double seedPower = entry.getValue();

            if (seedPower <= 0.0 || !level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            double convertedPower = OpticalMaterialProfiles.saturatedCoherentEmissionFor(
                    level,
                    pos,
                    state,
                    seedPower
            );

            if (convertedPower > 0.0) {
                if (convertedPower <= RUBY_DERIVED_COHERENT_SOURCE_MIN_POWER) {
                    continue;
                }

                if (sources.size() >= MAX_RUBY_DERIVED_COHERENT_SOURCES) {
                    return new RubySeedSynthesis(
                            sources,
                            rubySeedPositions.size(),
                            maxPumpRate,
                            totalStraySeedPower,
                            totalCoherentSourcePower,
                            maxNodeSourcePower
                    );
                }

                sources.add(new RubyCoherentSource(pos, key.direction(), convertedPower));
                totalCoherentSourcePower += convertedPower;
                maxNodeSourcePower = Math.max(maxNodeSourcePower, convertedPower);
            }
        }

        return new RubySeedSynthesis(
                sources,
                rubySeedPositions.size(),
                maxPumpRate,
                totalStraySeedPower,
                totalCoherentSourcePower,
                maxNodeSourcePower
        );
    }

    private static CompiledPortGraph expandCoherentGraphWithRubySources(
            ServerLevel level,
            CompiledPortGraph coherentGraph,
            List<RubyCoherentSource> rubySources
    ) {
        if (rubySources.isEmpty()) {
            return coherentGraph;
        }

        List<CompiledPortGraph> graphs = new ArrayList<>();
        graphs.add(coherentGraph);

        List<RubyCoherentSource> orderedRubySources = new ArrayList<>(rubySources);
        orderedRubySources.sort(RUBY_COHERENT_SOURCE_COMPARATOR);

        for (RubyCoherentSource rubySource : orderedRubySources) {
            graphs.add(PortGraphCompiler.compileDirect(
                    level,
                    rubySource.pos(),
                    rubyCoherentOutputBeam(rubySource.direction(), rubySource.power())
            ));
        }

        return unionGraphs(graphs);
    }

    private static void addRubyCoherentSourcePowers(
            CompiledPortGraph coherentGraph,
            List<RubyCoherentSource> rubySources,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> coherentSourcePowersByLane
    ) {
        if (rubySources.isEmpty()) {
            return;
        }

        Set<PortGraphNode> coherentNodes = new HashSet<>(coherentGraph.nodes());
        SpectralPowerLane rubyCoherentLane = new SpectralPowerLane(RubyBlock.RUBY_LINE, CoherenceKind.COHERENT);
        Map<PortGraphNode, Double> rubyCoherentSourcePowersByNode =
                coherentSourcePowersByLane.computeIfAbsent(rubyCoherentLane, ignored -> new HashMap<>());

        for (RubyCoherentSource rubySource : rubySources) {
            PortGraphNode sourceNode = rubySource.sourceNode();

            if (coherentNodes.contains(sourceNode)) {
                rubyCoherentSourcePowersByNode.merge(sourceNode, rubySource.power(), Double::sum);
            }
        }
    }

    private static OutputBeam rubyCoherentOutputBeam(Direction direction, double power) {
        return new OutputBeam(
                direction,
                BeamPacket.single(
                        new PlaneWaveComponent(RubyBlock.RUBY_LINE, power, direction, CoherenceKind.COHERENT),
                        BeamEnvelope.collimated(0.25)
                )
        );
    }

    private static ScalarPowerSolution combineChannelSolutions(
            ScalarPowerSolution incoherentSolution,
            ScalarPowerSolution coherentSolution
    ) {
        Map<PortGraphNode, Double> totalPowerByNode = mergedPowerMap(
                incoherentSolution.powerByNode(),
                coherentSolution.powerByNode()
        );
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> powerByLane = copyLanePowerMap(incoherentSolution.powerByLane());

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : coherentSolution.powerByLane().entrySet()) {
            Map<PortGraphNode, Double> lanePowers = powerByLane.computeIfAbsent(entry.getKey(), ignored -> new HashMap<>());

            for (Map.Entry<PortGraphNode, Double> powerEntry : entry.getValue().entrySet()) {
                lanePowers.merge(powerEntry.getKey(), powerEntry.getValue(), Double::sum);
            }
        }

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
                powerByLane,
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

    private static Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowerMapByLane(
            PortGraphNode sourceNode,
            OutputBeam outputBeam,
            CoherenceKind coherence
    ) {
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowersByLane = new HashMap<>();
        addOutputBeamPowerByLane(sourcePowersByLane, sourceNode, outputBeam, coherence);
        return sourcePowersByLane;
    }

    private static void addOutputBeamPowerByLane(
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowersByLane,
            PortGraphNode sourceNode,
            OutputBeam outputBeam,
            CoherenceKind coherence
    ) {
        for (PlaneWaveComponent component : outputBeam.beam().components()) {
            if (component.coherence() != coherence || component.power() <= 0.0) {
                continue;
            }

            SpectralPowerLane lane = new SpectralPowerLane(component.frequency(), coherence);
            sourcePowersByLane.computeIfAbsent(lane, ignored -> new HashMap<>())
                    .merge(sourceNode, component.power(), Double::sum);
        }
    }

    private static ScalarPowerSolution solvePowerLanes(
            ServerLevel level,
            String channel,
            CompiledPortGraph graph,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowersByLane
    ) {
        if (sourcePowersByLane.isEmpty()) {
            return ScalarPowerSolution.empty();
        }

        List<Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>>> lanes =
                new ArrayList<>(sourcePowersByLane.entrySet());
        lanes.sort(Map.Entry.comparingByKey(SpectralPowerLane.COMPARATOR));

        boolean parallel = shouldSolveLanesInParallel(graph, lanes.size());
        long startNanos = System.nanoTime();
        List<LaneScalarSolution> laneSolutions = parallel
                ? lanes.parallelStream()
                        .map(entry -> solveSingleLane(graph, entry))
                        .filter(Objects::nonNull)
                        .toList()
                : lanes.stream()
                        .map(entry -> solveSingleLane(graph, entry))
                        .filter(Objects::nonNull)
                        .toList();
        long elapsedNanos = System.nanoTime() - startNanos;
        ScalarPowerSolution solution = combineLaneSolutions(laneSolutions);
        OpticalCompilerDebugLogger.logSpectralLaneSolve(
                level,
                channel,
                graph,
                lanes.size(),
                laneSolutions.size(),
                parallel,
                elapsedNanos,
                solution
        );
        return solution;
    }

    private static LaneScalarSolution solveSingleLane(
            CompiledPortGraph graph,
            Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry
    ) {
        Map<PortGraphNode, Double> sourcePowersByNode = positivePowerMap(entry.getValue());

        if (sourcePowersByNode.isEmpty()) {
            return null;
        }

        CompiledPortGraph laneGraph = graphForFrequency(graph, entry.getKey().frequency());
        return new LaneScalarSolution(
                entry.getKey(),
                ScalarPowerSolver.solve(laneGraph, sourcePowersByNode)
        );
    }

    private static boolean shouldSolveLanesInParallel(CompiledPortGraph graph, int laneCount) {
        if (laneCount < SPECTRAL_LANE_PARALLEL_MIN_LANES || Runtime.getRuntime().availableProcessors() <= 1) {
            return false;
        }

        int workEstimate = laneCount * Math.max(1, graph.edges().size() + graph.nodes().size());
        return workEstimate >= SPECTRAL_LANE_PARALLEL_MIN_WORK;
    }

    private static CompiledPortGraph graphForFrequency(CompiledPortGraph graph, FrequencyKey frequency) {
        List<PortGraphEdge> edges = new ArrayList<>(graph.edges().size());

        for (PortGraphEdge edge : graph.edges()) {
            double gain = edge.sampleGainFor(frequency);
            edges.add(new PortGraphEdge(
                    edge.id(),
                    edge.kind(),
                    edge.from(),
                    edge.to(),
                    edge.distance(),
                    1.0,
                    Math.max(0.0, gain),
                    frequency,
                    Map.of(frequency, Math.max(0.0, gain))
            ));
        }

        return new CompiledPortGraph(
                graph.sourcePos(),
                graph.sourceDirection(),
                graph.sourceNode(),
                graph.nodes(),
                edges,
                graph.sccs(),
                graph.chords(),
                graph.terminationCount()
        );
    }

    private static ScalarPowerSolution combineLaneSolutions(List<LaneScalarSolution> laneSolutions) {
        if (laneSolutions.isEmpty()) {
            return ScalarPowerSolution.empty();
        }

        Map<PortGraphNode, Double> totalPowerByNode = new HashMap<>();
        Map<PortGraphNode, Double> coherentPowerByNode = new HashMap<>();
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> powerByLane = new HashMap<>();
        List<io.github.yoglappland.spectralization.optics.compiler.ScalarSolverRegionResult> regionResults = new ArrayList<>();
        boolean converged = true;
        boolean unstable = false;
        int iterations = 0;
        double residual = 0.0;
        ScalarSolverKind solverKind = ScalarSolverKind.NONE;
        ScalarSolverPlan solverPlan = ScalarSolverPlan.empty();

        for (LaneScalarSolution laneSolution : laneSolutions) {
            ScalarPowerSolution solution = laneSolution.solution();

            if (solverKind == ScalarSolverKind.NONE && solution.solverKind() != ScalarSolverKind.NONE) {
                solverKind = solution.solverKind();
                solverPlan = solution.solverPlan();
            }

            converged &= solution.converged();
            unstable |= solution.unstable();
            iterations += solution.iterations();
            residual = Math.max(residual, solution.residual());
            regionResults.addAll(solution.regionResults());
            powerByLane.put(laneSolution.lane(), solution.powerByNode());

            for (Map.Entry<PortGraphNode, Double> entry : solution.powerByNode().entrySet()) {
                totalPowerByNode.merge(entry.getKey(), entry.getValue(), Double::sum);

                if (laneSolution.lane().coherence() == CoherenceKind.COHERENT) {
                    coherentPowerByNode.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
        }

        double maxPower = 0.0;
        double totalPower = 0.0;

        for (double power : totalPowerByNode.values()) {
            maxPower = Math.max(maxPower, power);
            totalPower += power;
        }

        return new ScalarPowerSolution(
                solverKind,
                solverPlan,
                converged,
                unstable,
                iterations,
                residual,
                maxPower,
                totalPower,
                totalPowerByNode,
                coherentPowerByNode,
                powerByLane,
                regionResults
        );
    }

    private static Map<SpectralPowerLane, Map<PortGraphNode, Double>> copyLanePowerMap(
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> source
    ) {
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> copy = new HashMap<>();

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }

        return copy;
    }

    private static Map<PortGraphNode, Double> collapseLanePowerMap(
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> source
    ) {
        Map<PortGraphNode, Double> collapsed = new HashMap<>();

        for (Map<PortGraphNode, Double> lanePowers : source.values()) {
            for (Map.Entry<PortGraphNode, Double> entry : lanePowers.entrySet()) {
                if (entry.getValue() > 0.0) {
                    collapsed.merge(entry.getKey(), entry.getValue(), Double::sum);
                }
            }
        }

        return collapsed;
    }

    private static Map<PortGraphNode, Double> positivePowerMap(Map<PortGraphNode, Double> source) {
        Map<PortGraphNode, Double> positive = new HashMap<>();

        for (Map.Entry<PortGraphNode, Double> entry : source.entrySet()) {
            if (entry.getValue() > 0.0) {
                positive.put(entry.getKey(), entry.getValue());
            }
        }

        return positive;
    }

    private static double totalSourcePower(Map<PortGraphNode, Double> sourcePowersByNode) {
        double totalPower = 0.0;

        for (double power : sourcePowersByNode.values()) {
            totalPower += Math.max(0.0, power);
        }

        return totalPower;
    }

    private static boolean hasPositiveSource(Map<PortGraphNode, Double> sourcePowersByNode) {
        for (double power : sourcePowersByNode.values()) {
            if (power > RUBY_DERIVED_COHERENT_SOURCE_MIN_POWER) {
                return true;
            }
        }

        return false;
    }

    private record PowerChannelSolveResult(
            CompiledPortGraph graph,
            CompiledPortGraph coherentGraph,
            Map<PortGraphNode, Double> coherentSourcePowersByNode,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> coherentSourcePowersByLane,
            boolean coherentHudIntent,
            ScalarPowerSolution solution
    ) {
        private PowerChannelSolveResult {
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(coherentGraph, "coherentGraph");
            Objects.requireNonNull(coherentSourcePowersByNode, "coherentSourcePowersByNode");
            Objects.requireNonNull(coherentSourcePowersByLane, "coherentSourcePowersByLane");
            Objects.requireNonNull(solution, "solution");
            coherentSourcePowersByNode = Map.copyOf(coherentSourcePowersByNode);
            coherentSourcePowersByLane = copyLanePowerMap(coherentSourcePowersByLane);
        }
    }

    private record LaneScalarSolution(SpectralPowerLane lane, ScalarPowerSolution solution) {
        private LaneScalarSolution {
            Objects.requireNonNull(lane, "lane");
            Objects.requireNonNull(solution, "solution");
        }
    }

    private record RubySeedSynthesis(
            List<RubyCoherentSource> sources,
            int seedNodeCount,
            int maxPumpRate,
            double totalStraySeedPower,
            double totalCoherentSourcePower,
            double maxNodeSourcePower
    ) {
        private RubySeedSynthesis {
            Objects.requireNonNull(sources, "sources");
            sources = List.copyOf(sources);
        }

        private int sourceCount() {
            return sources.size();
        }
    }

    private record RubyModeKey(BlockPos pos, Direction direction) {
        private RubyModeKey {
            pos = pos.immutable();
            Objects.requireNonNull(direction, "direction");
        }
    }

    private record RubyCoherentSource(BlockPos pos, Direction direction, double power) {
        private RubyCoherentSource {
            pos = pos.immutable();
            Objects.requireNonNull(direction, "direction");

            if (!Double.isFinite(power) || power <= 0.0D) {
                throw new IllegalArgumentException("Ruby coherent source power must be positive and finite");
            }
        }

        private PortGraphNode sourceNode() {
            return new PortGraphNode(pos, direction, PortWaveKind.OUTGOING);
        }
    }

    private static CompiledPortGraph unionGraphs(List<CompiledPortGraph> graphs) {
        if (graphs.size() == 1) {
            return graphs.getFirst();
        }

        return PortGraphCompiler.unionDirectGraphs(graphs);
    }

    private static List<SourceTraceKey> orderedSourceKeys(Set<SourceTraceKey> sourceKeys) {
        List<SourceTraceKey> orderedKeys = new ArrayList<>(sourceKeys);
        orderedKeys.sort(SOURCE_TRACE_KEY_COMPARATOR);
        return orderedKeys;
    }

    private static void sortGraphsBySourceKey(List<CompiledPortGraph> graphs) {
        graphs.sort(Comparator.comparing(
                graph -> new SourceTraceKey(graph.sourcePos(), graph.sourceDirection()),
                SOURCE_TRACE_KEY_COMPARATOR
        ));
    }

    private static OutputBeam withCoherence(OutputBeam outputBeam, CoherenceKind coherence) {
        return new OutputBeam(outputBeam.outgoingDirection(), outputBeam.beam().withCoherence(coherence));
    }

    private static OutputBeam topologyOutput(OutputBeam outputBeam) {
        BeamPacket beam = outputBeam.beam();
        double totalPower = beam.totalPower();

        if (totalPower > 0.0) {
            beam = beam.scalePower(1.0 / totalPower);
        }

        return new OutputBeam(outputBeam.outgoingDirection(), beam);
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

            if (!IntrinsicOpticalSources.isSource(state)) {
                continue;
            }

            rememberPersistentSourceState(level, pos);

            for (OutputBeam outputBeam : IntrinsicOpticalSources.outputBeams(state, level, pos)) {
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

        sourceOutputs.sort(Comparator.comparing(GraphSourceOutput::key, SOURCE_TRACE_KEY_COMPARATOR));
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
                    || !IntrinsicOpticalSources.isSource(level.getBlockState(sourceKey.sourcePos()))) {
                inactiveKeys.add(sourceKey);
            }
        }

        for (SourceTraceKey inactiveKey : inactiveKeys) {
            DirectSourceRecord record = cache.directSourcesByKey.get(inactiveKey);

            if (record != null) {
                cache.unregisterDirectSourceForNetwork(record.networkId());
            }
        }
    }

    private static void wakeDormantSources(ServerLevel level, LevelTraceCache cache) {
        OpticalDormantSourceData maybeData = OpticalDormantSourceData.maybeGet(level).orElse(null);

        if (maybeData == null) {
            return;
        }

        long[] sourcePositions = maybeData.sourcePositions().toLongArray();

        if (sourcePositions.length == 0) {
            return;
        }

        long gameTime = level.getGameTime();
        int checked = 0;
        int cursor = Math.floorMod(cache.dormantSourceWakeCursor, sourcePositions.length);

        while (checked < DORMANT_SOURCE_WAKE_BUDGET_PER_TICK && checked < sourcePositions.length) {
            long encodedPos = sourcePositions[cursor];
            cursor = (cursor + 1) % sourcePositions.length;
            checked++;

            if (cache.awakenedDormantSourcePositions.contains(encodedPos)) {
                continue;
            }

            BlockPos pos = BlockPos.of(encodedPos);

            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);

            if (!isPersistentDormantSource(state)) {
                OpticalDormantSourceData.removeSource(level, pos);
                cache.awakenedDormantSourcePositions.add(encodedPos);
                continue;
            }

            cache.awakenedDormantSourcePositions.add(encodedPos);

            for (OutputBeam outputBeam : IntrinsicOpticalSources.outputBeams(state, level, pos)) {
                if (outputBeam.beam().isEmpty()) {
                    continue;
                }

                SourceTraceKey key = new SourceTraceKey(pos, outputBeam.outgoingDirection());
                int networkId = cache.networkIdFor(key);
                cache.enqueuePriority(new TraceRequest(networkId, pos, outputBeam), gameTime);
            }
        }

        cache.dormantSourceWakeCursor = cursor;
    }

    private static void rememberPersistentSourceState(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }

        if (isPersistentDormantSource(level.getBlockState(pos))) {
            OpticalDormantSourceData.recordSource(level, pos);
        } else {
            OpticalDormantSourceData.removeSource(level, pos);
        }
    }

    private static boolean isPersistentDormantSource(BlockState state) {
        return state.getBlock() instanceof OpticalSource;
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

            return Block.getId(state);
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
            CompiledReadoutLayer readoutLayer
    ) {
        private DirectCompilation {
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(passiveGraph, "passiveGraph");
            Objects.requireNonNull(coherentGraph, "coherentGraph");
            Objects.requireNonNull(readoutLayer, "readoutLayer");
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

    private record BeamHudOverlayDebugState(
            String mode,
            int segmentCount,
            int sentPlayers
    ) {
        private BeamHudOverlayDebugState {
            Objects.requireNonNull(mode, "mode");
        }
    }

    private record HudOverlaySendState(long signature, long gameTime, int segmentCount) {
    }

    private static final class ReceiverOutputAccumulator {
        private final BlockPos pos;
        private final ReceiverOutputKind kind;
        private final boolean positiveZ;
        private final BeamEnvelope envelope;
        private final Map<FrequencyKey, Double> powerByFrequency = new HashMap<>();
        private double power;
        private double coherentPower;
        private double strayPower;

        private ReceiverOutputAccumulator(ReceiverOutput receiverOutput) {
            this.pos = receiverOutput.pos();
            this.kind = receiverOutput.kind();
            this.positiveZ = receiverOutput.positiveZ();
            this.envelope = receiverOutput.envelope();
            add(receiverOutput);
        }

        private void add(ReceiverOutput receiverOutput) {
            power += receiverOutput.power();
            coherentPower += receiverOutput.coherentPower();
            strayPower += receiverOutput.strayPower();

            for (Map.Entry<FrequencyKey, Double> entry : receiverOutput.powerByFrequency().entrySet()) {
                powerByFrequency.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        private ReceiverOutput toReceiverOutput() {
            return new ReceiverOutput(
                    pos,
                    kind,
                    power,
                    positiveZ,
                    coherentPower,
                    strayPower,
                    envelope,
                    powerByFrequency
            );
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
        private final Map<Long, Integer> pumpRatesByPos = new HashMap<>();
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
        private final Map<Integer, HudOverlaySendState> lastHudOverlaySentByNetwork = new HashMap<>();
        private final Map<Integer, Long> lastHudOverlayDebugTickByNetwork = new HashMap<>();
        private final Map<Integer, BeamHudOverlayDebugState> lastHudOverlayDebugStateByNetwork = new HashMap<>();
        private final LongSet awakenedDormantSourcePositions = new LongOpenHashSet();
        private final IntSet retiredHudOwnerIds = new IntOpenHashSet();
        private final Set<UUID> hudHelmetPlayers = new HashSet<>();
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
        private int dormantSourceWakeCursor = 0;
        private boolean lastSystemRebuildCacheHit = false;

        int networkIdFor(SourceTraceKey key) {
            return networkIdsBySource.computeIfAbsent(key, ignored -> nextNetworkId++);
        }

        void enqueue(TraceRequest request, long gameTime) {
            enqueue(request, gameTime, false);
        }

        void enqueuePriority(TraceRequest request, long gameTime) {
            enqueue(request, gameTime, true);
        }

        private void enqueue(TraceRequest request, long gameTime, boolean priority) {
            lastRequestTicksByNetwork.put(request.networkId(), gameTime);

            if (queuedNetworkIds.add(request.networkId())) {
                if (priority) {
                    pendingRequests.addFirst(request);
                } else {
                    pendingRequests.addLast(request);
                }
            } else if (priority) {
                pendingRequests.removeIf(pendingRequest -> pendingRequest.networkId() == request.networkId());
                pendingRequests.addFirst(request);
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

        void enqueueKnownSourceRefresh(ServerLevel level, int networkId, long gameTime, boolean priority) {
            SourceTraceKey sourceKey = sourceKeyForNetwork(networkId);

            if (sourceKey == null) {
                return;
            }

            OutputBeam sourceOutput = currentSourceOutput(level, sourceKey);
            if (sourceOutput == null) {
                unregisterDirectSourceForNetwork(networkId);
                return;
            }

            TraceRequest request = new TraceRequest(networkId, sourceKey.sourcePos(), sourceOutput);
            if (priority) {
                enqueuePriority(request, gameTime);
            } else {
                enqueue(request, gameTime);
            }
        }

        private static OutputBeam currentSourceOutput(ServerLevel level, SourceTraceKey sourceKey) {
            if (!level.isLoaded(sourceKey.sourcePos())) {
                return null;
            }

            BlockState state = level.getBlockState(sourceKey.sourcePos());

            if (!IntrinsicOpticalSources.isSource(state)) {
                return null;
            }

            for (OutputBeam outputBeam : IntrinsicOpticalSources.outputBeams(state, level, sourceKey.sourcePos())) {
                if (outputBeam.outgoingDirection() == sourceKey.direction() && !outputBeam.beam().isEmpty()) {
                    return outputBeam;
                }
            }

            return null;
        }

        void unregisterDirectSourceForNetwork(int networkId) {
            directSourcesByKey.entrySet().removeIf(entry -> entry.getValue().networkId() == networkId);
            IntSet networkIds = new IntOpenHashSet();
            networkIds.add(networkId);
            removeSystemsForNetworkIds(networkIds);
            queuedSystemRebuildNetworkIds.remove(networkId);
            pendingSystemRebuilds.removeIf(queuedNetworkId -> queuedNetworkId == networkId);
            cachedTracesByNetwork.remove(networkId);
            geometrySignaturePositionsByNetwork.remove(networkId);
            dependencyIndex.removeNetwork(networkId);
            retiredHudOwnerIds.add(networkId);
            lastHudOverlaySentByNetwork.remove(networkId);
            lastHudOverlayDebugTickByNetwork.remove(networkId);
            lastHudOverlayDebugStateByNetwork.remove(networkId);
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

        boolean hasSystemAuthorityForNetwork(int networkId) {
            return queuedSystemRebuildNetworkIds.contains(networkId) || systemIdsByNetwork.containsKey(networkId);
        }

        boolean hasActiveSystemReadoutOverlap(List<ReceiverOutput> receiverOutputs) {
            if (receiverOutputs.isEmpty() || cachedSystemsBySystem.isEmpty()) {
                return false;
            }

            Set<ReceiverOutput.ReceiverOutputKey> directKeys = new HashSet<>();

            for (ReceiverOutput receiverOutput : receiverOutputs) {
                directKeys.add(receiverOutput.key());
            }

            for (CachedOpticalSystem system : cachedSystemsBySystem.values()) {
                if (!system.usableForGameplay()) {
                    continue;
                }

                for (ReceiverOutput receiverOutput : system.receiverOutputs()) {
                    if (directKeys.contains(receiverOutput.key())) {
                        return true;
                    }
                }
            }

            return false;
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

        boolean shouldAttemptHudOverlay(int networkId, long gameTime, List<BeamPathOverlayPayload.Segment> segments) {
            HudOverlaySendState lastSent = lastHudOverlaySentByNetwork.get(networkId);
            long signature = BeamPathOverlayTracker.signature(segments);

            if (lastSent != null
                    && lastSent.signature() == signature
                    && gameTime - lastSent.gameTime() < HUD_OVERLAY_REFRESH_INTERVAL_TICKS) {
                return false;
            }

            if (lastSent != null
                    && lastSent.signature() != signature
                    && gameTime - lastSent.gameTime() < HUD_OVERLAY_SEND_INTERVAL_TICKS) {
                return false;
            }

            return true;
        }

        boolean hasSentNonEmptyHudOverlay(int networkId) {
            HudOverlaySendState lastSent = lastHudOverlaySentByNetwork.get(networkId);
            return lastSent != null && lastSent.segmentCount() > 0;
        }

        void markHudOverlaySent(int networkId, long gameTime, List<BeamPathOverlayPayload.Segment> segments) {
            long signature = BeamPathOverlayTracker.signature(segments);
            lastHudOverlaySentByNetwork.put(networkId, new HudOverlaySendState(signature, gameTime, segments.size()));
        }

        boolean shouldRunHudOverlayDebug(
                int networkId,
                BeamHudOverlayDebugState state,
                long gameTime
        ) {
            BeamHudOverlayDebugState lastState = lastHudOverlayDebugStateByNetwork.get(networkId);
            long lastRun = lastHudOverlayDebugTickByNetwork.getOrDefault(networkId, Long.MIN_VALUE);
            boolean stateChanged = !state.equals(lastState);

            if (!stateChanged
                    && lastRun != Long.MIN_VALUE
                    && gameTime - lastRun < SpectralizationConfig.opticalCompilerDebugReadoutApplyIntervalTicks()) {
                return false;
            }

            lastHudOverlayDebugStateByNetwork.put(networkId, state);
            lastHudOverlayDebugTickByNetwork.put(networkId, gameTime);
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
                    if (reliable) {
                        cachedTrace.applyOutputs(level, true, readoutStep);
                    } else {
                        applyInterruptedAuthoritativeOutputs(
                                level,
                                this,
                                request.networkId(),
                                cachedTrace,
                                "pending_global_interrupted"
                        );
                    }
                }
            }
        }

        void applyStableCachedSystems(Level level) {
            if (cachedSystemsBySystem.isEmpty()) {
                return;
            }

            long gameTime = level.getGameTime();
            long readoutStep = nextReadoutStep();

            for (CachedOpticalSystem system : cachedSystemsBySystem.values()) {
                if (!system.usableForGameplay() || !markSystemApplied(system.systemId(), gameTime)) {
                    continue;
                }

                applySystemOutputs(level, system, true, readoutStep);
                OpticalTraceCache.publishSystemHudOverlay(level, this, system, "stable_system");
            }
        }

        void applySystemOutputs(Level level, CachedOpticalSystem system, boolean reliable, long readoutStep) {
            if (system == null || (reliable && !system.usableForGameplay())) {
                return;
            }

            boolean sampleReliable = reliable && system.usableForGameplay();
            List<ReceiverOutput> outputs = mergedActiveSystemOutputsFor(system);
            OpticalTraceCache.applyReceiverOutputs(level, outputs, sampleReliable, readoutStep);

            if (sampleReliable) {
                rememberReliableReceiverOutputs(outputs);
            }
        }

        private List<ReceiverOutput> mergedActiveSystemOutputsFor(CachedOpticalSystem targetSystem) {
            if (targetSystem.receiverOutputs().isEmpty()) {
                return List.of();
            }

            Set<ReceiverOutput.ReceiverOutputKey> targetKeys = new HashSet<>();
            Map<ReceiverOutput.ReceiverOutputKey, ReceiverOutputAccumulator> mergedOutputs = new LinkedHashMap<>();

            for (ReceiverOutput receiverOutput : targetSystem.receiverOutputs()) {
                targetKeys.add(receiverOutput.key());
                ReceiverOutputAccumulator accumulator = mergedOutputs.get(receiverOutput.key());

                if (accumulator == null) {
                    mergedOutputs.put(receiverOutput.key(), new ReceiverOutputAccumulator(receiverOutput));
                } else {
                    accumulator.add(receiverOutput);
                }
            }

            for (CachedOpticalSystem system : cachedSystemsBySystem.values()) {
                if (!system.usableForGameplay()) {
                    continue;
                }

                if (system.systemId() == targetSystem.systemId()) {
                    continue;
                }

                for (ReceiverOutput receiverOutput : system.receiverOutputs()) {
                    ReceiverOutput.ReceiverOutputKey key = receiverOutput.key();

                    if (!targetKeys.contains(key)) {
                        continue;
                    }

                    ReceiverOutputAccumulator accumulator = mergedOutputs.get(key);

                    if (accumulator != null) {
                        accumulator.add(receiverOutput);
                    }
                }
            }

            List<ReceiverOutput> outputs = new ArrayList<>(mergedOutputs.size());

            for (ReceiverOutputAccumulator accumulator : mergedOutputs.values()) {
                outputs.add(accumulator.toReceiverOutput());
            }

            return outputs;
        }

        void applyKnownCachedOutputsAsInterrupted(Level level) {
            long readoutStep = nextReadoutStep();
            Set<ReadoutSignature> appliedSignatures = new HashSet<>();

            for (CachedOpticalSystem system : cachedSystemsBySystem.values()) {
                ReadoutSignature signature = ReadoutSignature.of(system.receiverOutputs());

                if (signature.empty() || !appliedSignatures.add(signature)) {
                    continue;
                }

                List<ReceiverOutput> heldOutputs = lastReliableReceiverOutputsByReadout.get(signature);
                if (heldOutputs == null || heldOutputs.isEmpty()) {
                    applySystemOutputs(level, system, false, readoutStep);
                } else {
                    OpticalTraceCache.applyReceiverOutputs(level, heldOutputs, false, readoutStep);
                }
            }

            for (CachedOpticalTrace trace : cachedTracesByNetwork.values()) {
                List<ReceiverOutput> outputs = trace.sampleCompiledOutputs();
                ReadoutSignature signature = ReadoutSignature.of(outputs);

                if (signature.empty() || !appliedSignatures.add(signature)) {
                    continue;
                }

                List<ReceiverOutput> heldOutputs = lastReliableReceiverOutputsByReadout.get(signature);
                if (heldOutputs == null || heldOutputs.isEmpty()) {
                    OpticalTraceCache.applyReceiverOutputs(level, outputs, false, readoutStep);
                } else {
                    OpticalTraceCache.applyReceiverOutputs(level, heldOutputs, false, readoutStep);
                }
            }
        }

        boolean markChanged(ServerLevel level, BlockPos pos, OpticalDirtyKind dirtyKind, long gameTime) {
            if (dirtyKind == OpticalDirtyKind.STRUCTURE) {
                pumpRatesByPos.remove(pos.asLong());
            }

            IntSet affectedNetworkIds = dependencyIndex.markChangedAndGet(pos);

            if (affectedNetworkIds.isEmpty()) {
                return false;
            }

            epochs = epochs.advance(dirtyKind);

            for (int networkId : affectedNetworkIds) {
                lastDirtyTicksByNetwork.put(networkId, gameTime);
                markSystemDirty(networkId, dirtyKind);
                retireHudOwner(networkId);
                enqueueKnownSourceRefresh(level, networkId, gameTime, dirtyKind != OpticalDirtyKind.SOURCE);
            }

            return true;
        }

        void invalidateSurfaceParameterData() {
            directCompilationCache.clear();
            systemCompilationCache.clear();
        }

        boolean markParameterChanged(ServerLevel level, BlockPos pos) {
            boolean changed = false;

            if (markIntrinsicDataIfChanged(level, pos)) {
                changed |= markChanged(level, pos, OpticalDirtyKind.PARAMETER, level.getGameTime());
            } else if (isDirectOpticalParameterDependency(level, pos)) {
                changed |= markChanged(level, pos, OpticalDirtyKind.PARAMETER, level.getGameTime());
            }

            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);

                if (markPumpSourceDataIfChanged(level, neighborPos)) {
                    OpticalWorldIndex.markChanged(level, neighborPos, OpticalDirtyKind.PARAMETER);
                    changed |= markChanged(level, neighborPos, OpticalDirtyKind.PARAMETER, level.getGameTime());
                }
            }

            return changed;
        }

        private boolean markIntrinsicDataIfChanged(ServerLevel level, BlockPos pos) {
            if (!level.isLoaded(pos)) {
                return false;
            }

            BlockState state = level.getBlockState(pos);

            if (IntrinsicOpticalSources.isBuiltInSource(state) || OpticalPumpSources.isPumpSource(level, pos, state)) {
                return markPumpSourceDataIfChanged(level, pos);
            }

            return false;
        }

        private static boolean isDirectOpticalParameterDependency(ServerLevel level, BlockPos pos) {
            if (!level.isLoaded(pos)) {
                return false;
            }

            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof CmosSensorBlock || state.getBlock() instanceof PassThroughSensorBlock) {
                return false;
            }

            return state.getBlock() instanceof OpticalElement;
        }

        private boolean markPumpSourceDataIfChanged(ServerLevel level, BlockPos pos) {
            if (!level.isLoaded(pos)) {
                pumpRatesByPos.remove(pos.asLong());
                return false;
            }

            BlockState state = level.getBlockState(pos);

            if (!IntrinsicOpticalSources.isBuiltInSource(state) && !OpticalPumpSources.isPumpSource(level, pos, state)) {
                pumpRatesByPos.remove(pos.asLong());
                return false;
            }

            int pumpRate = OpticalPumpSources.pumpRateFor(level, pos, state);
            Integer previous = pumpRatesByPos.put(pos.asLong(), pumpRate);

            return previous == null || previous != pumpRate;
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
            retiredHudOwnerIds.remove(-system.systemId());

            for (int networkId : networkIds) {
                systemIdsByNetwork.put(networkId, system.systemId());
                retiredHudOwnerIds.add(networkId);
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
                    lastUsableSystemsBySystem.remove(systemId);
                    retiredHudOwnerIds.add(-systemId);
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
                lastUsableSystemsBySystem.remove(oldSystemId);
                retiredHudOwnerIds.add(-oldSystemId);
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
            retireHudOwner(-systemId);
            if (dirtyKind == OpticalDirtyKind.STRUCTURE || dirtyKind == OpticalDirtyKind.TOPOLOGY) {
                lastUsableSystemsBySystem.remove(systemId);
            }
            IntSet systemNetworkIds = networkIdsBySystem.get(systemId);

            if (systemNetworkIds == null || systemNetworkIds.isEmpty()) {
                dependencyIndex.markDirty(networkId);
                return;
            }

            for (int systemNetworkId : systemNetworkIds) {
                dependencyIndex.markDirty(systemNetworkId);
                retireHudOwner(systemNetworkId);
            }
        }

        private void retireHudOwner(int ownerId) {
            retiredHudOwnerIds.add(ownerId);
            lastHudOverlaySentByNetwork.remove(ownerId);
            lastHudOverlayDebugTickByNetwork.remove(ownerId);
            lastHudOverlayDebugStateByNetwork.remove(ownerId);
        }
    }

    private OpticalTraceCache() {
    }
}
