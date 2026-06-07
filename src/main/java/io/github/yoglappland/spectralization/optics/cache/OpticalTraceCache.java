package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.OpticalPort;
import io.github.yoglappland.spectralization.optics.OpticalPathTracer;
import io.github.yoglappland.spectralization.optics.OpticalTraceStep;
import io.github.yoglappland.spectralization.optics.OpticalTraceTermination;
import io.github.yoglappland.spectralization.optics.OpticalTraceTerminationReason;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
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
    private static final int MAX_DISCOVERED_SYSTEM_SOURCES = 32;

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

    public static boolean markChanged(LevelAccessor level, BlockPos pos) {
        return markChanged(level, pos, OpticalDirtyKind.STRUCTURE);
    }

    public static boolean markChanged(LevelAccessor level, BlockPos pos, OpticalDirtyKind dirtyKind) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        LevelTraceCache cache = cacheFor(serverLevel);
        return cache.markChanged(pos, dirtyKind);
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
            SourceTraceKey sourceKey = new SourceTraceKey(request.sourcePos(), request.sourceOutput().outgoingDirection());

            if (!SpectralizationConfig.opticalCompilerDebugLog()
                    || !cache.shouldRunCompilerDebug(sourceKey, level.getGameTime())) {
                cacheLegacyTrace(level, cache, request, trace);
                processed++;
                continue;
            }

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

            if (directGraph.nodes().size() <= SpectralizationConfig.opticalCompilerDebugFullNetworkMaxNodes()) {
                cache.directSourcesByKey.put(
                        sourceKey,
                        new DirectSourceRecord(
                                request.networkId(),
                                directGraph,
                                request.sourceOutput(),
                                cache.epochs
                        )
                );
                pruneInactiveSources(level, cache);
                rebuildDirectSystems(level, cache);
                cachedSystem = cache.systemForNetwork(request.networkId());

                if (cachedSystem == null) {
                    cachedSystem = buildSingleSourceSystem(level, cache, sourceKey);
                }

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

            OpticalCompilerDebugLogger.logObservedDirectAndNetwork(
                    level,
                    trace,
                    observedGraph,
                    directGraph,
                    scalarPowerSolution,
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
            cache.cachedTracesByNetwork.put(request.networkId(), cachedTrace);
            cache.dependencyIndex.replaceDependencies(request.networkId(), cachedTrace.dependencies());
            cache.dependencyIndex.clearDirty(request.networkId());
            if (cachedSystem != null) {
                OpticalTraceValidator.validate(level, request.sourcePos(), request.sourceOutput(), trace);
            }
            processed++;
        }
    }

    private static void cacheLegacyTrace(
            ServerLevel level,
            LevelTraceCache cache,
            TraceRequest request,
            CompiledOpticalTrace trace
    ) {
        CachedOpticalTrace cachedTrace = buildCachedTrace(
                level,
                request,
                trace,
                minimalPortGraph(request.sourcePos(), request.sourceOutput().outgoingDirection()),
                CompiledReadoutLayer.EMPTY,
                ScalarPowerSolution.empty()
        );
        cache.cachedTracesByNetwork.put(request.networkId(), cachedTrace);
        cache.dependencyIndex.replaceDependencies(request.networkId(), cachedTrace.dependencies());
        cache.dependencyIndex.clearDirty(request.networkId());
    }

    private static CompiledPortGraph minimalPortGraph(BlockPos sourcePos, Direction sourceDirection) {
        PortGraphNode sourceNode = PortGraphNode.outgoing(new OpticalPort(sourcePos, sourceDirection));

        return new CompiledPortGraph(
                sourcePos,
                sourceDirection,
                sourceNode,
                List.of(sourceNode),
                List.of(),
                List.of(),
                List.of(),
                0
        );
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
        boolean parametricallyFresh = true;
        OpticalEpochs currentEpochs = cache.epochs;
        List<CompiledPortGraph> graphs = new ArrayList<>();

        for (SourceTraceKey sourceKey : sourceKeys) {
            DirectSourceRecord record = cache.directSourcesByKey.get(sourceKey);

            if (record == null) {
                continue;
            }

            systemId = Math.min(systemId, record.networkId());
            structurallyFresh &= record.epochs().structurallyMatches(currentEpochs);
            parametricallyFresh &= record.epochs().parametersMatch(currentEpochs);
            graphs.add(record.graph());
        }

        if (graphs.isEmpty()) {
            throw new IllegalStateException("Cannot build an optical system without direct source graphs");
        }

        graphs = expandGraphsWithDiscoveredSources(level, graphs);
        CompiledPortGraph graph = graphs.size() == 1
                ? graphs.getFirst()
                : PortGraphCompiler.unionDirectGraphs(graphs);
        Map<PortGraphNode, Double> sourcePowersByNode = collectGraphSourcePowers(level, graph);
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
                currentEpochs,
                structurallyFresh,
                parametricallyFresh,
                structurallyFresh && parametricallyFresh && canUseForGameplay(graph, solution)
        );
    }

    private static List<CompiledPortGraph> expandGraphsWithDiscoveredSources(
            ServerLevel level,
            List<CompiledPortGraph> initialGraphs
    ) {
        List<CompiledPortGraph> graphs = new ArrayList<>(initialGraphs);
        Set<SourceTraceKey> compiledSourceKeys = new HashSet<>();

        for (CompiledPortGraph graph : graphs) {
            compiledSourceKeys.add(new SourceTraceKey(graph.sourcePos(), graph.sourceDirection()));
        }

        boolean added;

        do {
            added = false;
            CompiledPortGraph graph = graphs.size() == 1
                    ? graphs.getFirst()
                    : PortGraphCompiler.unionDirectGraphs(graphs);

            for (GraphSourceOutput sourceOutput : discoverGraphSourceOutputs(level, graph)) {
                if (compiledSourceKeys.size() >= MAX_DISCOVERED_SYSTEM_SOURCES) {
                    return graphs;
                }

                if (!compiledSourceKeys.add(sourceOutput.key())) {
                    continue;
                }

                graphs.add(PortGraphCompiler.compileDirect(
                        level,
                        sourceOutput.key().sourcePos(),
                        sourceOutput.outputBeam()
                ));
                added = true;
            }
        } while (added);

        return graphs;
    }

    private static Map<PortGraphNode, Double> collectGraphSourcePowers(ServerLevel level, CompiledPortGraph graph) {
        Map<PortGraphNode, Double> sourcePowersByNode = new HashMap<>();
        Set<PortGraphNode> graphNodes = new HashSet<>(graph.nodes());

        for (GraphSourceOutput sourceOutput : discoverGraphSourceOutputs(level, graph)) {
            PortGraphNode sourceNode = sourceOutput.sourceNode();

            if (!graphNodes.contains(sourceNode)) {
                continue;
            }

            sourcePowersByNode.merge(
                    sourceNode,
                    sourceOutput.outputBeam().beam().totalPower(),
                    Double::sum
            );
        }

        return sourcePowersByNode;
    }

    private static List<GraphSourceOutput> discoverGraphSourceOutputs(ServerLevel level, CompiledPortGraph graph) {
        Set<BlockPos> positions = new HashSet<>();
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
                sourceOutputs.add(new GraphSourceOutput(key, sourceNode, outputBeam));
            }
        }

        return sourceOutputs;
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
            OpticalEpochs epochs
    ) {
        private DirectSourceRecord {
            if (networkId <= 0) {
                throw new IllegalArgumentException("Direct source network id must be positive");
            }

            Objects.requireNonNull(graph, "graph");
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

    private record SystemLegacyReadout(List<ReceiverOutput> receiverOutputs, boolean complete, boolean comparable) {
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
        private final Map<SourceTraceKey, Long> lastCompilerDebugTickBySource = new HashMap<>();
        private final ArrayDeque<TraceRequest> pendingRequests = new ArrayDeque<>();
        private final IntSet queuedNetworkIds = new IntOpenHashSet();
        private int nextNetworkId = 1;
        private OpticalEpochs epochs = OpticalEpochs.ZERO;
        private long compilerDebugRunTick = Long.MIN_VALUE;
        private int compilerDebugRunsThisTick = 0;

        int networkIdFor(SourceTraceKey key) {
            return networkIdsBySource.computeIfAbsent(key, ignored -> nextNetworkId++);
        }

        void enqueue(TraceRequest request) {
            if (queuedNetworkIds.add(request.networkId())) {
                pendingRequests.addLast(request);
            }
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

        boolean markChanged(BlockPos pos, OpticalDirtyKind dirtyKind) {
            IntSet affectedNetworkIds = dependencyIndex.markChangedAndGet(pos);

            if (affectedNetworkIds.isEmpty()) {
                return false;
            }

            epochs = epochs.advance(dirtyKind);

            for (int networkId : affectedNetworkIds) {
                markSystemDirty(networkId);
            }

            return true;
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
