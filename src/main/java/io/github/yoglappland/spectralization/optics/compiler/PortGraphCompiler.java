package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.OpticalInteractionKind;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalPort;
import io.github.yoglappland.spectralization.optics.OpticalPropagationLoss;
import io.github.yoglappland.spectralization.optics.OpticalPropagationEdge;
import io.github.yoglappland.spectralization.optics.OpticalTraceStep;
import io.github.yoglappland.spectralization.optics.OpticalTraceTermination;
import io.github.yoglappland.spectralization.optics.OpticalTransferEdge;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class PortGraphCompiler {
    private static final int MAX_DIRECT_SCAN_DISTANCE = 128;
    private static final double DIRECT_MIN_RELATIVE_POWER = 1.0E-4;
    private static final Comparator<PortGraphNode> NODE_COMPARATOR = Comparator
            .comparingInt((PortGraphNode node) -> node.pos().getX())
            .thenComparingInt(node -> node.pos().getY())
            .thenComparingInt(node -> node.pos().getZ())
            .thenComparingInt(node -> node.side().ordinal())
            .thenComparingInt(node -> node.waveKind().ordinal());
    private static final Comparator<PortGraphEdge> EDGE_COMPARATOR = Comparator
            .comparing((PortGraphEdge edge) -> edge.from(), NODE_COMPARATOR)
            .thenComparing(edge -> edge.to(), NODE_COMPARATOR)
            .thenComparing(edge -> edge.kind().ordinal())
            .thenComparingInt(PortGraphEdge::id);

    public static CompiledPortGraph compileObservedTrace(CompiledOpticalTrace trace) {
        Objects.requireNonNull(trace, "trace");

        Map<EdgeKey, EdgeAccumulator> rawEdgeAccumulators = new LinkedHashMap<>();
        Set<PortGraphNode> interestingNodes = new TreeSet<>(NODE_COMPARATOR);
        PortGraphNode sourceNode = PortGraphNode.outgoing(new OpticalPort(
                trace.sourcePos(),
                trace.sourceOutput().outgoingDirection()
        ));
        interestingNodes.add(sourceNode);

        for (OpticalTraceStep step : trace.steps()) {
            OpticalPropagationEdge propagationEdge = step.propagationEdge();
            addRawEdge(
                    rawEdgeAccumulators,
                    PortGraphEdgeKind.PROPAGATION,
                    PortGraphNode.outgoing(propagationEdge.from()),
                    PortGraphNode.incoming(propagationEdge.to()),
                    manhattanDistance(propagationEdge.from().pos(), propagationEdge.to().pos()),
                    propagationEdge.beam().totalPower(),
                    propagationEdge.beam().totalPower()
            );

            boolean interestingStep = step.interactionKind() != OpticalInteractionKind.AIR;

            if (interestingStep) {
                interestingNodes.add(PortGraphNode.incoming(step.inputPort()));
            }

            for (OpticalTransferEdge transferEdge : step.transferEdges()) {
                PortGraphNode from = PortGraphNode.incoming(transferEdge.from());
                PortGraphNode to = PortGraphNode.outgoing(transferEdge.to());
                addRawEdge(
                        rawEdgeAccumulators,
                        PortGraphEdgeKind.LOCAL_SCATTERING,
                        from,
                        to,
                        0,
                        transferEdge.inputBeam().totalPower(),
                        transferEdge.outputBeam().totalPower()
                );

                if (interestingStep) {
                    interestingNodes.add(to);
                }
            }
        }

        for (OpticalTraceTermination termination : trace.terminations()) {
            PortGraphNode from = PortGraphNode.outgoing(new OpticalPort(
                    termination.pos().relative(termination.travelDirection().getOpposite()),
                    termination.travelDirection()
            ));
            PortGraphNode to = PortGraphNode.incoming(termination.inputPort());
            interestingNodes.add(to);
            addRawEdge(
                    rawEdgeAccumulators,
                    PortGraphEdgeKind.PROPAGATION,
                    from,
                    to,
                    manhattanDistance(from.pos(), termination.pos()),
                    termination.beam().totalPower(),
                    termination.beam().totalPower()
            );
        }

        List<PortGraphEdge> rawEdges = buildEdges(rawEdgeAccumulators);
        List<PortGraphEdge> compactEdges = compactEdges(rawEdges, interestingNodes);
        List<PortGraphNode> orderedNodes = orderedNodes(sourceNode, interestingNodes, compactEdges);
        List<PortGraphScc> sccs = buildSccs(orderedNodes, compactEdges);
        List<PortGraphChord> chords = buildChords(orderedNodes, compactEdges, sccs);

        return new CompiledPortGraph(
                trace.sourcePos(),
                trace.sourceOutput().outgoingDirection(),
                sourceNode,
                orderedNodes,
                compactEdges,
                sccs,
                chords,
                trace.terminations().size()
        );
    }

    public static CompiledPortGraph compileDirect(Level level, BlockPos sourcePos, OutputBeam sourceOutput) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(sourcePos, "sourcePos");
        Objects.requireNonNull(sourceOutput, "sourceOutput");

        Map<EdgeKey, EdgeAccumulator> edgeAccumulators = new LinkedHashMap<>();
        Set<PortGraphNode> interestingNodes = new TreeSet<>(NODE_COMPARATOR);
        PortGraphNode sourceNode = PortGraphNode.outgoing(new OpticalPort(
                sourcePos,
                sourceOutput.outgoingDirection()
        ));
        BeamPacket sampleBeam = normalizedSampleBeam(sourceOutput);
        ArrayDeque<DirectPendingNode> pendingOutgoingNodes = new ArrayDeque<>();
        Map<PortGraphNode, Double> bestEstimatedOutgoingPower = new HashMap<>();
        Set<PortGraphNode> processedOutgoingNodes = new HashSet<>();
        int maxOutgoingNodes = SpectralizationConfig.opticalCompilerMaxDirectOutgoingNodes();
        int terminationCount = 0;

        interestingNodes.add(sourceNode);
        enqueueDirectOutgoing(pendingOutgoingNodes, bestEstimatedOutgoingPower, sourceNode, 1.0);

        while (!pendingOutgoingNodes.isEmpty() && processedOutgoingNodes.size() < maxOutgoingNodes) {
            DirectPendingNode pendingNode = pendingOutgoingNodes.removeFirst();
            PortGraphNode outgoingNode = pendingNode.node();

            if (outgoingNode.waveKind() != PortWaveKind.OUTGOING || !processedOutgoingNodes.add(outgoingNode)) {
                continue;
            }

            double estimatedOutgoingPower = Math.max(
                    pendingNode.estimatedPower(),
                    bestEstimatedOutgoingPower.getOrDefault(outgoingNode, 0.0)
            );

            if (estimatedOutgoingPower < DIRECT_MIN_RELATIVE_POWER) {
                continue;
            }

            terminationCount += scanDirectPropagation(
                    level,
                    outgoingNode,
                    estimatedOutgoingPower,
                    sampleBeam,
                    edgeAccumulators,
                    interestingNodes,
                    pendingOutgoingNodes,
                    bestEstimatedOutgoingPower
            );
        }

        if (!pendingOutgoingNodes.isEmpty()) {
            terminationCount++;
        }

        List<PortGraphEdge> edges = buildEdges(edgeAccumulators);
        List<PortGraphNode> orderedNodes = orderedNodes(sourceNode, interestingNodes, edges);
        List<PortGraphScc> sccs = buildSccs(orderedNodes, edges);
        List<PortGraphChord> chords = buildChords(orderedNodes, edges, sccs);

        return new CompiledPortGraph(
                sourcePos,
                sourceOutput.outgoingDirection(),
                sourceNode,
                orderedNodes,
                edges,
                sccs,
                chords,
                terminationCount
        );
    }

    public static CompiledPortGraph unionDirectGraphs(List<CompiledPortGraph> graphs) {
        if (graphs.isEmpty()) {
            throw new IllegalArgumentException("Cannot union an empty direct graph list");
        }

        CompiledPortGraph firstGraph = graphs.getFirst();
        Map<EdgeKey, EdgeAccumulator> edgeAccumulators = new LinkedHashMap<>();
        Set<PortGraphNode> nodes = new TreeSet<>(NODE_COMPARATOR);
        int terminationCount = 0;

        for (CompiledPortGraph graph : graphs) {
            nodes.addAll(graph.nodes());
            terminationCount += graph.terminationCount();

            for (PortGraphEdge edge : graph.edges()) {
                EdgeKey key = new EdgeKey(edge.kind(), edge.from(), edge.to());
                edgeAccumulators.computeIfAbsent(
                                key,
                                ignored -> new EdgeAccumulator(edge.kind(), edge.from(), edge.to(), edge.distance())
                        )
                        .accept(edge.distance(), edge.sampleInputPower(), edge.sampleOutputPower());
            }
        }

        List<PortGraphEdge> edges = buildEdges(edgeAccumulators);
        List<PortGraphNode> orderedNodes = orderedNodes(firstGraph.sourceNode(), nodes, edges);
        List<PortGraphScc> sccs = buildSccs(orderedNodes, edges);
        List<PortGraphChord> chords = buildChords(orderedNodes, edges, sccs);

        return new CompiledPortGraph(
                firstGraph.sourcePos(),
                firstGraph.sourceDirection(),
                firstGraph.sourceNode(),
                orderedNodes,
                edges,
                sccs,
                chords,
                terminationCount
        );
    }

    private static int scanDirectPropagation(
            Level level,
            PortGraphNode outgoingNode,
            double estimatedOutgoingPower,
            BeamPacket sampleBeam,
            Map<EdgeKey, EdgeAccumulator> edgeAccumulators,
            Set<PortGraphNode> interestingNodes,
            ArrayDeque<DirectPendingNode> pendingOutgoingNodes,
            Map<PortGraphNode, Double> bestEstimatedOutgoingPower
    ) {
        Direction travelDirection = outgoingNode.side();
        BlockPos cursor = outgoingNode.pos().relative(travelDirection);
        double propagationFactor = 1.0;

        for (int distance = 1; distance <= MAX_DIRECT_SCAN_DISTANCE; distance++) {
            if (!level.isLoaded(cursor)) {
                return 1;
            }

            propagationFactor *= OpticalPropagationLoss.factor(level, cursor, sampleBeam);
            double estimatedIncomingPower = estimatedOutgoingPower * propagationFactor;

            if (estimatedIncomingPower < DIRECT_MIN_RELATIVE_POWER) {
                return 0;
            }

            BlockState state = level.getBlockState(cursor);

            if (OpticalMaterialProfiles.isAirLike(state)) {
                cursor = cursor.relative(travelDirection);
                continue;
            }

            Direction incomingDirection = travelDirection.getOpposite();
            PortGraphNode incomingNode = PortGraphNode.incoming(new OpticalPort(cursor, incomingDirection));
            interestingNodes.add(incomingNode);
            addRawEdge(
                    edgeAccumulators,
                    PortGraphEdgeKind.PROPAGATION,
                    outgoingNode,
                    incomingNode,
                    distance,
                    sampleBeam.totalPower(),
                    sampleBeam.totalPower() * propagationFactor
            );
            addDirectLocalScattering(
                    level,
                    cursor,
                    state,
                    incomingDirection,
                    sampleBeam.withDirection(travelDirection),
                    estimatedIncomingPower,
                    edgeAccumulators,
                    interestingNodes,
                    pendingOutgoingNodes,
                    bestEstimatedOutgoingPower
            );
            return 0;
        }

        return 1;
    }

    private static void addDirectLocalScattering(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction incomingDirection,
            BeamPacket inputBeam,
            double estimatedInputPower,
            Map<EdgeKey, EdgeAccumulator> edgeAccumulators,
            Set<PortGraphNode> interestingNodes,
            ArrayDeque<DirectPendingNode> pendingOutgoingNodes,
            Map<PortGraphNode, Double> bestEstimatedOutgoingPower
    ) {
        OpticalLocalTopology localTopology = OpticalLocalTopologyCompiler.compile(
                level,
                pos,
                state,
                incomingDirection,
                inputBeam
        );

        for (OpticalLocalScattering scattering : localTopology.scattering()) {
            PortGraphNode incomingNode = scattering.inputPort().incomingNode();
            PortGraphNode outgoingNode = scattering.outputPort().outgoingNode();
            double estimatedOutputPower = estimatedInputPower * scatteringGain(scattering);

            if (estimatedOutputPower < DIRECT_MIN_RELATIVE_POWER) {
                continue;
            }

            interestingNodes.add(outgoingNode);
            enqueueDirectOutgoing(
                    pendingOutgoingNodes,
                    bestEstimatedOutgoingPower,
                    outgoingNode,
                    estimatedOutputPower
            );
            addRawEdge(
                    edgeAccumulators,
                    PortGraphEdgeKind.LOCAL_SCATTERING,
                    incomingNode,
                    outgoingNode,
                    0,
                    scattering.sampleInputPower(),
                    scattering.sampleOutputPower()
            );
        }
    }

    private static double scatteringGain(OpticalLocalScattering scattering) {
        double inputPower = scattering.sampleInputPower();

        if (inputPower <= 0.0) {
            return 0.0;
        }

        return scattering.sampleOutputPower() / inputPower;
    }

    private static void enqueueDirectOutgoing(
            ArrayDeque<DirectPendingNode> pendingOutgoingNodes,
            Map<PortGraphNode, Double> bestEstimatedOutgoingPower,
            PortGraphNode outgoingNode,
            double estimatedPower
    ) {
        if (estimatedPower < DIRECT_MIN_RELATIVE_POWER) {
            return;
        }

        double previousPower = bestEstimatedOutgoingPower.getOrDefault(outgoingNode, 0.0);

        if (estimatedPower <= previousPower) {
            return;
        }

        bestEstimatedOutgoingPower.put(outgoingNode, estimatedPower);
        pendingOutgoingNodes.addLast(new DirectPendingNode(outgoingNode, estimatedPower));
    }

    private static BeamPacket normalizedSampleBeam(OutputBeam sourceOutput) {
        BeamPacket beam = sourceOutput.beam().withDirection(sourceOutput.outgoingDirection());
        double totalPower = beam.totalPower();

        if (totalPower <= 0.0) {
            return beam;
        }

        return beam.scalePower(1.0 / totalPower);
    }

    private static void addRawEdge(
            Map<EdgeKey, EdgeAccumulator> edgeAccumulators,
            PortGraphEdgeKind kind,
            PortGraphNode from,
            PortGraphNode to,
            int distance,
            double sampleInputPower,
            double sampleOutputPower
    ) {
        if (sampleInputPower <= 0.0 && sampleOutputPower <= 0.0) {
            return;
        }

        EdgeKey key = new EdgeKey(kind, from, to);
        edgeAccumulators.computeIfAbsent(key, ignored -> new EdgeAccumulator(kind, from, to, distance))
                .accept(distance, sampleInputPower, sampleOutputPower);
    }

    private static List<PortGraphEdge> compactEdges(
            List<PortGraphEdge> rawEdges,
            Set<PortGraphNode> interestingNodes
    ) {
        Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges = new HashMap<>();

        for (PortGraphEdge edge : rawEdges) {
            outgoingEdges.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }

        for (List<PortGraphEdge> edges : outgoingEdges.values()) {
            edges.sort(EDGE_COMPARATOR);
        }

        Map<EdgeKey, EdgeAccumulator> compactAccumulators = new LinkedHashMap<>();
        List<PortGraphNode> orderedStarts = new ArrayList<>(interestingNodes);
        orderedStarts.sort(NODE_COMPARATOR);

        for (PortGraphNode start : orderedStarts) {
            for (PortGraphEdge edge : outgoingEdges.getOrDefault(start, List.of())) {
                compactFrom(start, edge, outgoingEdges, interestingNodes, compactAccumulators);
            }
        }

        return buildEdges(compactAccumulators);
    }

    private static void compactFrom(
            PortGraphNode start,
            PortGraphEdge firstEdge,
            Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges,
            Set<PortGraphNode> interestingNodes,
            Map<EdgeKey, EdgeAccumulator> compactAccumulators
    ) {
        ArrayDeque<PathState> pending = new ArrayDeque<>();
        Set<PortGraphNode> firstVisited = new HashSet<>();
        firstVisited.add(start);
        pending.add(new PathState(
                firstEdge.to(),
                compactKind(firstEdge, interestingNodes),
                firstEdge.distance(),
                firstEdge.sampleInputPower(),
                firstEdge.sampleOutputPower(),
                firstVisited
        ));

        while (!pending.isEmpty()) {
            PathState current = pending.removeFirst();

            if (interestingNodes.contains(current.node())) {
                addCompactEdge(compactAccumulators, start, current);
                continue;
            }

            if (!current.visited().add(current.node())) {
                continue;
            }

            for (PortGraphEdge nextEdge : outgoingEdges.getOrDefault(current.node(), List.of())) {
                Set<PortGraphNode> visited = new HashSet<>(current.visited());
                pending.add(new PathState(
                        nextEdge.to(),
                        current.kind(),
                        current.distance() + nextEdge.distance(),
                        current.sampleInputPower(),
                        nextEdge.sampleOutputPower(),
                        visited
                ));
            }
        }
    }

    private static PortGraphEdgeKind compactKind(PortGraphEdge firstEdge, Set<PortGraphNode> interestingNodes) {
        if (firstEdge.kind() == PortGraphEdgeKind.LOCAL_SCATTERING && interestingNodes.contains(firstEdge.to())) {
            return PortGraphEdgeKind.LOCAL_SCATTERING;
        }

        return PortGraphEdgeKind.PROPAGATION;
    }

    private static void addCompactEdge(
            Map<EdgeKey, EdgeAccumulator> compactAccumulators,
            PortGraphNode from,
            PathState path
    ) {
        EdgeKey key = new EdgeKey(path.kind(), from, path.node());
        compactAccumulators.computeIfAbsent(
                        key,
                        ignored -> new EdgeAccumulator(path.kind(), from, path.node(), path.distance())
                )
                .accept(path.distance(), path.sampleInputPower(), path.sampleOutputPower());
    }

    private static List<PortGraphNode> orderedNodes(
            PortGraphNode sourceNode,
            Set<PortGraphNode> interestingNodes,
            List<PortGraphEdge> edges
    ) {
        Set<PortGraphNode> nodes = new TreeSet<>(NODE_COMPARATOR);
        nodes.add(sourceNode);
        nodes.addAll(interestingNodes);

        for (PortGraphEdge edge : edges) {
            nodes.add(edge.from());
            nodes.add(edge.to());
        }

        return new ArrayList<>(nodes);
    }

    private static List<PortGraphEdge> buildEdges(Map<EdgeKey, EdgeAccumulator> edgeAccumulators) {
        List<EdgeAccumulator> accumulators = new ArrayList<>(edgeAccumulators.values());
        accumulators.sort(Comparator
                .comparing((EdgeAccumulator accumulator) -> accumulator.from, NODE_COMPARATOR)
                .thenComparing(accumulator -> accumulator.to, NODE_COMPARATOR)
                .thenComparing(accumulator -> accumulator.kind.ordinal()));

        List<PortGraphEdge> edges = new ArrayList<>();

        for (int index = 0; index < accumulators.size(); index++) {
            EdgeAccumulator accumulator = accumulators.get(index);
            edges.add(new PortGraphEdge(
                    index,
                    accumulator.kind,
                    accumulator.from,
                    accumulator.to,
                    accumulator.distance,
                    accumulator.sampleInputPower,
                    accumulator.sampleOutputPower
            ));
        }

        return edges;
    }

    private static List<PortGraphScc> buildSccs(List<PortGraphNode> nodes, List<PortGraphEdge> edges) {
        Map<PortGraphNode, Integer> nodeIds = nodeIds(nodes);
        List<List<Integer>> adjacency = new ArrayList<>();

        for (int index = 0; index < nodes.size(); index++) {
            adjacency.add(new ArrayList<>());
        }

        for (PortGraphEdge edge : edges) {
            Integer fromId = nodeIds.get(edge.from());
            Integer toId = nodeIds.get(edge.to());

            if (fromId != null && toId != null) {
                adjacency.get(fromId).add(toId);
            }
        }

        for (List<Integer> outgoing : adjacency) {
            outgoing.sort(Comparator.comparing(nodes::get, NODE_COMPARATOR));
        }

        TarjanState state = new TarjanState(nodes.size());

        for (int index = 0; index < nodes.size(); index++) {
            if (state.indices[index] == -1) {
                strongConnect(index, adjacency, state);
            }
        }

        List<Set<PortGraphNode>> rawSccs = new ArrayList<>();

        for (Set<Integer> component : state.components) {
            Set<PortGraphNode> componentNodes = new TreeSet<>(NODE_COMPARATOR);

            for (int nodeId : component) {
                componentNodes.add(nodes.get(nodeId));
            }

            rawSccs.add(componentNodes);
        }

        rawSccs.sort(Comparator.comparing(component -> component.iterator().next(), NODE_COMPARATOR));

        List<PortGraphScc> sccs = new ArrayList<>();

        for (int sccId = 0; sccId < rawSccs.size(); sccId++) {
            Set<PortGraphNode> componentNodes = rawSccs.get(sccId);
            List<Integer> edgeIds = edgesInside(componentNodes, edges);
            int beta1 = Math.max(0, edgeIds.size() - componentNodes.size() + 1);
            boolean feedback = beta1 > 0 || hasSelfLoop(componentNodes, edges);
            sccs.add(new PortGraphScc(sccId, componentNodes, edgeIds, beta1, feedback));
        }

        return sccs;
    }

    private static void strongConnect(int nodeId, List<List<Integer>> adjacency, TarjanState state) {
        state.indices[nodeId] = state.nextIndex;
        state.lowLinks[nodeId] = state.nextIndex;
        state.nextIndex++;
        state.stack.push(nodeId);
        state.onStack.add(nodeId);

        for (int nextNodeId : adjacency.get(nodeId)) {
            if (state.indices[nextNodeId] == -1) {
                strongConnect(nextNodeId, adjacency, state);
                state.lowLinks[nodeId] = Math.min(state.lowLinks[nodeId], state.lowLinks[nextNodeId]);
            } else if (state.onStack.contains(nextNodeId)) {
                state.lowLinks[nodeId] = Math.min(state.lowLinks[nodeId], state.indices[nextNodeId]);
            }
        }

        if (state.lowLinks[nodeId] != state.indices[nodeId]) {
            return;
        }

        Set<Integer> component = new HashSet<>();
        int currentNodeId;

        do {
            currentNodeId = state.stack.pop();
            state.onStack.remove(currentNodeId);
            component.add(currentNodeId);
        } while (currentNodeId != nodeId);

        state.components.add(component);
    }

    private static List<PortGraphChord> buildChords(
            List<PortGraphNode> nodes,
            List<PortGraphEdge> edges,
            List<PortGraphScc> sccs
    ) {
        Map<PortGraphNode, Integer> nodeIds = nodeIds(nodes);
        List<PortGraphChord> chords = new ArrayList<>();

        for (PortGraphScc scc : sccs) {
            if (!scc.feedback()) {
                continue;
            }

            List<PortGraphEdge> sccEdges = new ArrayList<>();

            for (int edgeId : scc.edgeIds()) {
                sccEdges.add(edges.get(edgeId));
            }

            sccEdges.sort(EDGE_COMPARATOR);

            UnionFind unionFind = new UnionFind(nodes.size());

            for (PortGraphEdge edge : sccEdges) {
                int fromId = nodeIds.get(edge.from());
                int toId = nodeIds.get(edge.to());

                if (fromId != toId && unionFind.union(fromId, toId)) {
                    continue;
                }

                chords.add(new PortGraphChord(
                        chords.size(),
                        scc.id(),
                        edge.id(),
                        edge.from(),
                        edge.to()
                ));
            }
        }

        return chords;
    }

    private static List<Integer> edgesInside(Set<PortGraphNode> componentNodes, List<PortGraphEdge> edges) {
        List<Integer> edgeIds = new ArrayList<>();

        for (PortGraphEdge edge : edges) {
            if (componentNodes.contains(edge.from()) && componentNodes.contains(edge.to())) {
                edgeIds.add(edge.id());
            }
        }

        return edgeIds;
    }

    private static boolean hasSelfLoop(Set<PortGraphNode> componentNodes, List<PortGraphEdge> edges) {
        for (PortGraphEdge edge : edges) {
            if (componentNodes.contains(edge.from()) && edge.from().equals(edge.to())) {
                return true;
            }
        }

        return false;
    }

    private static Map<PortGraphNode, Integer> nodeIds(List<PortGraphNode> nodes) {
        Map<PortGraphNode, Integer> nodeIds = new HashMap<>();

        for (int index = 0; index < nodes.size(); index++) {
            nodeIds.put(nodes.get(index), index);
        }

        return nodeIds;
    }

    private static int manhattanDistance(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX())
                + Math.abs(from.getY() - to.getY())
                + Math.abs(from.getZ() - to.getZ());
    }

    private record EdgeKey(PortGraphEdgeKind kind, PortGraphNode from, PortGraphNode to) {
        private EdgeKey {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    private record PathState(
            PortGraphNode node,
            PortGraphEdgeKind kind,
            int distance,
            double sampleInputPower,
            double sampleOutputPower,
            Set<PortGraphNode> visited
    ) {
        private PathState {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(visited, "visited");
        }
    }

    private record DirectPendingNode(PortGraphNode node, double estimatedPower) {
        private DirectPendingNode {
            Objects.requireNonNull(node, "node");
        }
    }

    private static final class EdgeAccumulator {
        private final PortGraphEdgeKind kind;
        private final PortGraphNode from;
        private final PortGraphNode to;
        private int distance;
        private double sampleInputPower;
        private double sampleOutputPower;

        private EdgeAccumulator(PortGraphEdgeKind kind, PortGraphNode from, PortGraphNode to, int distance) {
            this.kind = kind;
            this.from = from;
            this.to = to;
            this.distance = distance;
        }

        private void accept(int distance, double sampleInputPower, double sampleOutputPower) {
            this.distance = Math.max(this.distance, distance);
            this.sampleInputPower = Math.max(this.sampleInputPower, sampleInputPower);
            this.sampleOutputPower = Math.max(this.sampleOutputPower, sampleOutputPower);
        }
    }

    private static final class TarjanState {
        private final int[] indices;
        private final int[] lowLinks;
        private final ArrayDeque<Integer> stack = new ArrayDeque<>();
        private final Set<Integer> onStack = new HashSet<>();
        private final List<Set<Integer>> components = new ArrayList<>();
        private int nextIndex;

        private TarjanState(int size) {
            this.indices = new int[size];
            this.lowLinks = new int[size];

            for (int index = 0; index < size; index++) {
                this.indices[index] = -1;
                this.lowLinks[index] = -1;
            }
        }
    }

    private static final class UnionFind {
        private final int[] parents;
        private final byte[] ranks;

        private UnionFind(int size) {
            this.parents = new int[size];
            this.ranks = new byte[size];

            for (int index = 0; index < size; index++) {
                this.parents[index] = index;
            }
        }

        private boolean union(int left, int right) {
            int rootLeft = find(left);
            int rootRight = find(right);

            if (rootLeft == rootRight) {
                return false;
            }

            if (ranks[rootLeft] < ranks[rootRight]) {
                parents[rootLeft] = rootRight;
            } else if (ranks[rootLeft] > ranks[rootRight]) {
                parents[rootRight] = rootLeft;
            } else {
                parents[rootRight] = rootLeft;
                ranks[rootLeft]++;
            }

            return true;
        }

        private int find(int value) {
            int parent = parents[value];

            if (parent != value) {
                parents[value] = find(parent);
            }

            return parents[value];
        }
    }

    private PortGraphCompiler() {
    }
}
