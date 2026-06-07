package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.OpticalPathTracer;
import io.github.yoglappland.spectralization.optics.OpticalTraceStep;
import io.github.yoglappland.spectralization.optics.OpticalTraceTermination;
import io.github.yoglappland.spectralization.optics.OpticalTraceTerminationReason;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.CompiledReadoutLayer;
import io.github.yoglappland.spectralization.optics.compiler.OpticalCompilerDebugLogger;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphCompiler;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.OpticalReadoutLayerCompiler;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolver;
import io.github.yoglappland.spectralization.optics.validation.OpticalTraceValidator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalTraceCache {
    private static final Map<Level, LevelTraceCache> CACHES = new WeakHashMap<>();

    public static void requestOrApply(Level level, BlockPos sourcePos, OutputBeam sourceOutput) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || sourceOutput.beam().isEmpty()) {
            return;
        }

        LevelTraceCache cache = cacheFor(serverLevel);
        SourceTraceKey key = new SourceTraceKey(sourcePos, sourceOutput.outgoingDirection());
        int networkId = cache.networkIdFor(key);
        CachedOpticalTrace cachedTrace = cache.cachedTracesByNetwork.get(networkId);

        if (cachedTrace != null
                && cachedTrace.matches(sourceOutput)
                && !cache.dependencyIndex.isDirty(networkId)) {
            cachedTrace.applyOutputs(level);
            return;
        }

        cache.enqueue(new TraceRequest(networkId, sourcePos, sourceOutput));
    }

    public static void markChanged(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        LevelTraceCache cache = cacheFor(serverLevel);
        cache.markChanged(pos);
    }

    public static void processQueues(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            processQueue(level);
        }
    }

    private static void processQueue(ServerLevel level) {
        LevelTraceCache cache = cacheFor(level);
        long deadlineNanos = System.nanoTime() + SpectralizationConfig.opticalSolverBudgetMicros() * 1_000L;
        int maxRequests = SpectralizationConfig.opticalSolverMaxRequestsPerTick();
        int processed = 0;

        while (!cache.pendingRequests.isEmpty() && processed < maxRequests) {
            if (processed > 0 && System.nanoTime() >= deadlineNanos) {
                break;
            }

            TraceRequest request = cache.pendingRequests.removeFirst();
            cache.queuedNetworkIds.remove(request.networkId());

            if (!level.isLoaded(request.sourcePos())) {
                continue;
            }

            CompiledOpticalTrace trace = OpticalPathTracer.trace(level, request.sourcePos(), request.sourceOutput());
            CompiledPortGraph observedGraph = PortGraphCompiler.compileObservedTrace(trace);
            CompiledPortGraph directGraph = PortGraphCompiler.compileDirect(
                    level,
                    request.sourcePos(),
                    request.sourceOutput()
            );
            ScalarPowerSolution scalarPowerSolution = ScalarPowerSolver.solve(
                    directGraph,
                    request.sourceOutput().beam().totalPower()
            );
            SourceTraceKey sourceKey = new SourceTraceKey(request.sourcePos(), request.sourceOutput().outgoingDirection());
            cache.directSourcesByKey.put(
                    sourceKey,
                    new DirectSourceRecord(
                            request.networkId(),
                            directGraph,
                            request.sourceOutput(),
                            cache.structureEpoch
                    )
            );
            pruneInactiveSources(level, cache);
            rebuildDirectSystems(level, cache);
            CachedOpticalSystem cachedSystem = cache.systemForNetwork(request.networkId());

            if (cachedSystem == null) {
                cachedSystem = buildSingleSourceSystem(level, cache, sourceKey);
            }

            ScalarPowerSolution networkSolution = cachedSystem.solution();
            CompiledReadoutLayer directReadoutLayer = OpticalReadoutLayerCompiler.compile(level, directGraph);
            CachedOpticalTrace cachedTrace = buildCachedTrace(
                    level,
                    request,
                    trace,
                    directGraph,
                    directReadoutLayer,
                    scalarPowerSolution
            );
            List<ReceiverOutput> directReceiverOutputs = directReadoutLayer.sample(scalarPowerSolution);
            List<ReceiverOutput> networkReceiverOutputs = cachedSystem.receiverOutputs();
            SystemLegacyReadout systemLegacyReadout = collectSystemLegacyReadout(
                    cache,
                    cachedSystem.systemId(),
                    request.networkId(),
                    cachedTrace
            );
            OpticalCompilerDebugLogger.logObservedDirectAndNetwork(
                    level,
                    trace,
                    observedGraph,
                    directGraph,
                    scalarPowerSolution,
                    cachedSystem.graph(),
                    networkSolution,
                    cachedSystem.sourceCount(),
                    cachedSystem.systemId(),
                    cachedSystem.structurallyFresh(),
                    cachedSystem.usableForGameplay(),
                    cachedTrace.receiverOutputs(),
                    directReceiverOutputs,
                    networkReceiverOutputs,
                    directReadoutLayer.size(),
                    cachedSystem.readoutLayer().size(),
                    systemLegacyReadout.receiverOutputs(),
                    systemLegacyReadout.complete()
            );
            cache.cachedTracesByNetwork.put(request.networkId(), cachedTrace);
            cache.dependencyIndex.replaceDependencies(request.networkId(), cachedTrace.dependencies());
            cache.dependencyIndex.clearDirty(request.networkId());
            OpticalTraceValidator.validate(level, request.sourcePos(), request.sourceOutput(), trace);
            processed++;
        }
    }

    private static CachedOpticalTrace buildCachedTrace(
            ServerLevel level,
            TraceRequest request,
            CompiledOpticalTrace trace,
            CompiledPortGraph portGraph,
            CompiledReadoutLayer readoutLayer,
            ScalarPowerSolution scalarPowerSolution
    ) {
        LongSet dependencies = new LongOpenHashSet();
        List<ReceiverOutput> receiverOutputs = new ArrayList<>();
        dependencies.add(request.sourcePos().asLong());
        OpticalFieldSources.addPotentialFieldSourceDependencies(level, request.sourcePos(), dependencies);

        for (OpticalTraceStep step : trace.steps()) {
            addDependency(level, step.pos(), dependencies);
            collectReceiverOutput(level, step, receiverOutputs);
        }

        for (OpticalTraceTermination termination : trace.terminations()) {
            addDependency(level, termination.pos(), dependencies);
        }

        return new CachedOpticalTrace(
                request.networkId(),
                request.sourceOutput(),
                receiverOutputs,
                dependencies,
                portGraph,
                readoutLayer,
                scalarPowerSolution,
                isUnstable(trace)
        );
    }

    private static void addDependency(ServerLevel level, BlockPos pos, LongSet dependencies) {
        dependencies.add(pos.asLong());
        OpticalFieldSources.addPotentialFieldSourceDependencies(level, pos, dependencies);
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
                        step.interactingBeam().totalPower()
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
        return graph.beta1() == 0 && solution.converged() && !solution.unstable();
    }

    private static SystemLegacyReadout collectSystemLegacyReadout(
            LevelTraceCache cache,
            int systemId,
            int currentNetworkId,
            CachedOpticalTrace currentTrace
    ) {
        IntSet networkIds = cache.networkIdsBySystem.get(systemId);

        if (networkIds == null || networkIds.isEmpty()) {
            return new SystemLegacyReadout(currentTrace.receiverOutputs(), true);
        }

        List<ReceiverOutput> receiverOutputs = new ArrayList<>();
        boolean complete = true;

        for (int networkId : networkIds) {
            CachedOpticalTrace trace = networkId == currentNetworkId
                    ? currentTrace
                    : cache.cachedTracesByNetwork.get(networkId);

            if (trace == null) {
                complete = false;
                continue;
            }

            receiverOutputs.addAll(trace.receiverOutputs());
        }

        return new SystemLegacyReadout(receiverOutputs, complete);
    }

    private static void rebuildDirectSystems(ServerLevel level, LevelTraceCache cache) {
        cache.systemIdsByNetwork.clear();
        cache.networkIdsBySystem.clear();
        cache.cachedSystemsBySystem.clear();

        Set<SourceTraceKey> remainingKeys = new HashSet<>(cache.directSourcesByKey.keySet());

        while (!remainingKeys.isEmpty()) {
            SourceTraceKey seedKey = remainingKeys.iterator().next();
            remainingKeys.remove(seedKey);
            Set<SourceTraceKey> componentKeys = collectConnectedSourceKeys(cache, seedKey, remainingKeys);

            if (!componentKeys.isEmpty()) {
                CachedOpticalSystem system = buildCachedSystem(level, cache, componentKeys);
                cache.cachedSystemsBySystem.put(system.systemId(), system);
                cache.networkIdsBySystem.put(system.systemId(), networkIdsFor(cache, componentKeys));

                for (SourceTraceKey componentKey : componentKeys) {
                    DirectSourceRecord record = cache.directSourcesByKey.get(componentKey);

                    if (record != null) {
                        cache.systemIdsByNetwork.put(record.networkId(), system.systemId());
                    }
                }
            }
        }
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

    private static CachedOpticalSystem buildCachedSystem(
            ServerLevel level,
            LevelTraceCache cache,
            Set<SourceTraceKey> sourceKeys
    ) {
        int systemId = Integer.MAX_VALUE;
        boolean structurallyFresh = true;
        List<CompiledPortGraph> graphs = new ArrayList<>();
        Map<PortGraphNode, Double> sourcePowersByNode = new HashMap<>();

        for (SourceTraceKey sourceKey : sourceKeys) {
            DirectSourceRecord record = cache.directSourcesByKey.get(sourceKey);

            if (record == null) {
                continue;
            }

            systemId = Math.min(systemId, record.networkId());
            structurallyFresh &= record.structureEpoch() == cache.structureEpoch;
            graphs.add(record.graph());
            sourcePowersByNode.merge(record.graph().sourceNode(), record.sourceOutput().beam().totalPower(), Double::sum);
        }

        if (graphs.isEmpty()) {
            throw new IllegalStateException("Cannot build an optical system without direct source graphs");
        }

        CompiledPortGraph graph = graphs.size() == 1
                ? graphs.getFirst()
                : PortGraphCompiler.unionDirectGraphs(graphs);
        ScalarPowerSolution solution = ScalarPowerSolver.solve(graph, sourcePowersByNode);
        CompiledReadoutLayer readoutLayer = OpticalReadoutLayerCompiler.compile(level, graph);
        List<ReceiverOutput> receiverOutputs = readoutLayer.sample(solution);

        return new CachedOpticalSystem(
                systemId,
                graph,
                sourcePowersByNode,
                readoutLayer,
                solution,
                receiverOutputs,
                sourcePowersByNode.size(),
                cache.structureEpoch,
                structurallyFresh,
                structurallyFresh && canUseForGameplay(graph, solution)
        );
    }

    private static CachedOpticalSystem buildSingleSourceSystem(
            ServerLevel level,
            LevelTraceCache cache,
            SourceTraceKey sourceKey
    ) {
        DirectSourceRecord record = cache.directSourcesByKey.get(sourceKey);

        if (record == null) {
            throw new IllegalStateException("Cannot build fallback optical system for a missing source graph");
        }

        return buildCachedSystem(level, cache, Set.of(sourceKey));
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
            OutputBeam sourceOutput,
            long structureEpoch
    ) {
        private DirectSourceRecord {
            if (networkId <= 0) {
                throw new IllegalArgumentException("Direct source network id must be positive");
            }

            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(sourceOutput, "sourceOutput");
        }
    }

    private record SystemLegacyReadout(List<ReceiverOutput> receiverOutputs, boolean complete) {
        private SystemLegacyReadout {
            Objects.requireNonNull(receiverOutputs, "receiverOutputs");
            receiverOutputs = List.copyOf(receiverOutputs);
        }
    }

    private static final class LevelTraceCache {
        private final OpticalDependencyIndex dependencyIndex = new OpticalDependencyIndex();
        private final Map<SourceTraceKey, Integer> networkIdsBySource = new HashMap<>();
        private final Map<Integer, CachedOpticalTrace> cachedTracesByNetwork = new HashMap<>();
        private final Map<Integer, CachedOpticalSystem> cachedSystemsBySystem = new HashMap<>();
        private final Map<Integer, Integer> systemIdsByNetwork = new HashMap<>();
        private final Map<Integer, IntSet> networkIdsBySystem = new HashMap<>();
        private final Map<SourceTraceKey, DirectSourceRecord> directSourcesByKey = new HashMap<>();
        private final ArrayDeque<TraceRequest> pendingRequests = new ArrayDeque<>();
        private final IntSet queuedNetworkIds = new IntOpenHashSet();
        private int nextNetworkId = 1;
        private long structureEpoch = 0L;

        int networkIdFor(SourceTraceKey key) {
            return networkIdsBySource.computeIfAbsent(key, ignored -> nextNetworkId++);
        }

        void enqueue(TraceRequest request) {
            if (queuedNetworkIds.add(request.networkId())) {
                pendingRequests.addLast(request);
            }
        }

        void markChanged(BlockPos pos) {
            IntSet affectedNetworkIds = dependencyIndex.markChangedAndGet(pos);

            if (affectedNetworkIds.isEmpty()) {
                return;
            }

            structureEpoch++;

            for (int networkId : affectedNetworkIds) {
                markSystemDirty(networkId);
            }
        }

        CachedOpticalSystem systemForNetwork(int networkId) {
            Integer systemId = systemIdsByNetwork.get(networkId);

            if (systemId == null) {
                return null;
            }

            return cachedSystemsBySystem.get(systemId);
        }

        private void markSystemDirty(int networkId) {
            Integer systemId = systemIdsByNetwork.get(networkId);

            if (systemId == null) {
                dependencyIndex.markDirty(networkId);
                return;
            }

            cachedSystemsBySystem.remove(systemId);
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
