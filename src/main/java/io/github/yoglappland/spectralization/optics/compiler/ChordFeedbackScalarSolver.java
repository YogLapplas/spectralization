package io.github.yoglappland.spectralization.optics.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ChordFeedbackScalarSolver {
    private static final int MAX_CHORD_VARIABLES = 512;
    private static final double PIVOT_EPSILON = 1.0E-10;
    private static final double RESIDUAL_EPSILON = 1.0E-6;
    private static final double RELATIVE_RESIDUAL_EPSILON = 1.0E-7;
    private static final double MAX_TOTAL_POWER = 1.0E12;
    private static final Comparator<PortGraphNode> NODE_COMPARATOR = Comparator
            .comparingInt((PortGraphNode node) -> node.pos().getX())
            .thenComparingInt(node -> node.pos().getY())
            .thenComparingInt(node -> node.pos().getZ())
            .thenComparingInt(node -> node.side().ordinal())
            .thenComparingInt(node -> node.waveKind().ordinal());

    public static boolean planningImplemented() {
        return true;
    }

    public static boolean implemented() {
        return true;
    }

    public static boolean supports(ScalarSolverRegion region) {
        return region.feedback() && region.executionSolverKind() == ScalarSolverKind.FEEDBACK_CHORD;
    }

    public static boolean hasCompilableSystem(ChordFeedbackPlan chordPlan, int graphSccId) {
        return systemFor(chordPlan, graphSccId)
                .filter(ChordFeedbackSystem::compilable)
                .filter(system -> system.variableCount() <= MAX_CHORD_VARIABLES)
                .isPresent();
    }

    public static Optional<ChordFeedbackSystem> systemFor(ChordFeedbackPlan chordPlan, int graphSccId) {
        for (ChordFeedbackSystem system : chordPlan.systems()) {
            if (system.graphSccId() == graphSccId) {
                return Optional.of(system);
            }
        }

        return Optional.empty();
    }

    public static Optional<ScalarPowerSolution> solve(
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            ScalarSolverPlan solverPlan
    ) {
        if (solverPlan.primarySolverKind() != ScalarSolverKind.FEEDBACK_CHORD
                || !solverPlan.chordFeedbackPlan().complete()
                || !implemented()) {
            return Optional.empty();
        }

        int nodeCount = graph.nodes().size();
        double[] powers = new double[nodeCount];
        System.arraycopy(source, 0, powers, 0, source.length);

        SccExecutionGraph executionGraph = buildSccExecutionGraph(graph);
        ArrayDeque<Integer> pendingSccs = new ArrayDeque<>();

        for (int sccId = 0; sccId < executionGraph.sccs().size(); sccId++) {
            if (executionGraph.inDegrees()[sccId] == 0) {
                pendingSccs.addLast(sccId);
            }
        }

        int visitedSccs = 0;

        while (!pendingSccs.isEmpty()) {
            int sccId = pendingSccs.removeFirst();
            PortGraphScc scc = executionGraph.sccs().get(sccId);
            visitedSccs++;

            if (scc.feedback()) {
                Optional<ChordFeedbackSystem> system = systemFor(solverPlan.chordFeedbackPlan(), scc.id());

                if (system.isEmpty()
                        || !system.get().compilable()
                        || system.get().variableCount() > MAX_CHORD_VARIABLES
                        || !solveChordScc(scc, system.get(), executionGraph.internalEdgesByScc().get(sccId), nodeIds, powers)) {
                    return Optional.empty();
                }
            }

            for (PortGraphEdge edge : executionGraph.outgoingEdgesByScc().get(sccId)) {
                Integer fromId = nodeIds.get(edge.from());
                Integer toId = nodeIds.get(edge.to());

                if (fromId != null && toId != null) {
                    powers[toId] += powers[fromId] * edge.sampleGain();
                }

                int targetSccId = executionGraph.sccIdByNode().get(edge.to());
                executionGraph.inDegrees()[targetSccId]--;

                if (executionGraph.inDegrees()[targetSccId] == 0) {
                    pendingSccs.addLast(targetSccId);
                }
            }
        }

        if (visitedSccs != executionGraph.sccs().size() || hasNonFinitePower(powers)) {
            return Optional.empty();
        }

        double residual = residual(graph, nodeIds, source, powers);
        double totalPower = total(powers);
        boolean unstable = !Double.isFinite(residual)
                || !Double.isFinite(totalPower)
                || totalPower > MAX_TOTAL_POWER
                || hasMeaningfulNegativePower(powers);
        boolean converged = !unstable
                && (residual <= RESIDUAL_EPSILON
                || residual <= RELATIVE_RESIDUAL_EPSILON * Math.max(1.0, totalPower));

        clampTinyNegativePowers(powers);

        return Optional.of(ScalarPowerSolutions.fromGraphPowers(
                ScalarSolverKind.FEEDBACK_CHORD,
                solverPlan,
                converged,
                unstable,
                1,
                Double.isFinite(residual) ? residual : MAX_TOTAL_POWER,
                graph,
                nodeIds,
                source,
                powers
        ));
    }

    static boolean solveChordScc(
            PortGraphScc scc,
            ChordFeedbackSystem system,
            List<PortGraphEdge> internalEdges,
            Map<PortGraphNode, Integer> nodeIds,
            double[] powers
    ) {
        SccLocalGraph localGraph = buildLocalGraph(scc, system, internalEdges, nodeIds);

        if (!localGraph.valid()) {
            return false;
        }

        double[] base = new double[localGraph.nodes().size()];

        for (int localId = 0; localId < localGraph.nodes().size(); localId++) {
            Integer globalId = nodeIds.get(localGraph.nodes().get(localId));

            if (globalId == null) {
                return false;
            }

            base[localId] = powers[globalId];
        }

        Optional<double[]> baseResponse = solveTreeResponse(localGraph.nodes().size(), localGraph.treeEdges(), base);

        if (baseResponse.isEmpty()) {
            return false;
        }

        int variableCount = system.variableCount();
        double[] u = new double[variableCount];
        double[][] matrix = new double[variableCount][variableCount + 1];

        for (int row = 0; row < variableCount; row++) {
            matrix[row][row] = 1.0;
            ChordFeedbackVariable variable = system.variables().get(row);
            u[row] = variable.gain() * baseResponse.get()[variable.fromLocalNodeId()];
            matrix[row][variableCount] = u[row];
        }

        for (int column = 0; column < variableCount; column++) {
            ChordFeedbackVariable sourceVariable = system.variables().get(column);
            double[] injection = new double[localGraph.nodes().size()];
            injection[sourceVariable.toLocalNodeId()] = 1.0;
            Optional<double[]> response = solveTreeResponse(localGraph.nodes().size(), localGraph.treeEdges(), injection);

            if (response.isEmpty()) {
                return false;
            }

            for (int row = 0; row < variableCount; row++) {
                ChordFeedbackVariable targetVariable = system.variables().get(row);
                double h = targetVariable.gain() * response.get()[targetVariable.fromLocalNodeId()];
                matrix[row][column] -= h;
            }
        }

        LinearSolveResult chordSolve = solveLinearSystem(matrix, variableCount);

        if (!chordSolve.solved() || hasNonFinitePower(chordSolve.solution()) || hasMeaningfulNegativePower(chordSolve.solution())) {
            return false;
        }

        double[] reconstructedBase = base.clone();

        for (int variableId = 0; variableId < variableCount; variableId++) {
            ChordFeedbackVariable variable = system.variables().get(variableId);
            reconstructedBase[variable.toLocalNodeId()] += chordSolve.solution()[variableId];
        }

        Optional<double[]> finalResponse = solveTreeResponse(
                localGraph.nodes().size(),
                localGraph.treeEdges(),
                reconstructedBase
        );

        if (finalResponse.isEmpty() || hasNonFinitePower(finalResponse.get()) || hasMeaningfulNegativePower(finalResponse.get())) {
            return false;
        }

        for (int localId = 0; localId < localGraph.nodes().size(); localId++) {
            Integer globalId = nodeIds.get(localGraph.nodes().get(localId));

            if (globalId == null) {
                return false;
            }

            powers[globalId] = finalResponse.get()[localId];
        }

        return true;
    }

    private static SccLocalGraph buildLocalGraph(
            PortGraphScc scc,
            ChordFeedbackSystem system,
            List<PortGraphEdge> internalEdges,
            Map<PortGraphNode, Integer> nodeIds
    ) {
        List<PortGraphNode> nodes = new ArrayList<>(scc.nodes());
        nodes.sort(NODE_COMPARATOR);
        Map<Integer, Integer> localIdsByGlobalId = new HashMap<>();

        for (int localId = 0; localId < nodes.size(); localId++) {
            Integer globalId = nodeIds.get(nodes.get(localId));

            if (globalId == null) {
                return SccLocalGraph.invalid();
            }

            localIdsByGlobalId.put(globalId, localId);
        }

        Set<Integer> chordEdgeIds = new HashSet<>();

        for (ChordFeedbackVariable variable : system.variables()) {
            chordEdgeIds.add(variable.edgeId());
        }

        List<LocalTreeEdge> treeEdges = new ArrayList<>();

        for (PortGraphEdge edge : internalEdges) {
            if (chordEdgeIds.contains(edge.id())) {
                continue;
            }

            Integer fromGlobalId = nodeIds.get(edge.from());
            Integer toGlobalId = nodeIds.get(edge.to());

            if (fromGlobalId == null || toGlobalId == null) {
                return SccLocalGraph.invalid();
            }

            Integer fromLocalId = localIdsByGlobalId.get(fromGlobalId);
            Integer toLocalId = localIdsByGlobalId.get(toGlobalId);

            if (fromLocalId == null || toLocalId == null) {
                return SccLocalGraph.invalid();
            }

            treeEdges.add(new LocalTreeEdge(fromLocalId, toLocalId, edge.sampleGain()));
        }

        return new SccLocalGraph(true, nodes, treeEdges);
    }

    private static Optional<double[]> solveTreeResponse(
            int nodeCount,
            List<LocalTreeEdge> treeEdges,
            double[] input
    ) {
        double[] powers = input.clone();
        int[] inDegrees = new int[nodeCount];
        List<List<LocalTreeEdge>> outgoingEdges = new ArrayList<>(nodeCount);

        for (int index = 0; index < nodeCount; index++) {
            outgoingEdges.add(new ArrayList<>());
        }

        for (LocalTreeEdge edge : treeEdges) {
            outgoingEdges.get(edge.fromLocalNodeId()).add(edge);
            inDegrees[edge.toLocalNodeId()]++;
        }

        ArrayDeque<Integer> pendingNodes = new ArrayDeque<>();

        for (int index = 0; index < nodeCount; index++) {
            if (inDegrees[index] == 0) {
                pendingNodes.addLast(index);
            }
        }

        int visitedNodes = 0;

        while (!pendingNodes.isEmpty()) {
            int fromId = pendingNodes.removeFirst();
            visitedNodes++;

            for (LocalTreeEdge edge : outgoingEdges.get(fromId)) {
                powers[edge.toLocalNodeId()] += powers[fromId] * edge.gain();
                inDegrees[edge.toLocalNodeId()]--;

                if (inDegrees[edge.toLocalNodeId()] == 0) {
                    pendingNodes.addLast(edge.toLocalNodeId());
                }
            }
        }

        if (visitedNodes != nodeCount) {
            return Optional.empty();
        }

        return Optional.of(powers);
    }

    private static SccExecutionGraph buildSccExecutionGraph(CompiledPortGraph graph) {
        List<PortGraphScc> sccs = new ArrayList<>(graph.sccs());
        sccs.sort((left, right) -> Integer.compare(left.id(), right.id()));
        Map<PortGraphNode, Integer> sccIdByNode = new HashMap<>();
        List<List<PortGraphEdge>> internalEdgesByScc = new ArrayList<>(sccs.size());
        List<List<PortGraphEdge>> outgoingEdgesByScc = new ArrayList<>(sccs.size());
        int[] inDegrees = new int[sccs.size()];

        for (int sccId = 0; sccId < sccs.size(); sccId++) {
            internalEdgesByScc.add(new ArrayList<>());
            outgoingEdgesByScc.add(new ArrayList<>());

            for (PortGraphNode node : sccs.get(sccId).nodes()) {
                sccIdByNode.put(node, sccId);
            }
        }

        for (PortGraphEdge edge : graph.edges()) {
            Integer fromSccId = sccIdByNode.get(edge.from());
            Integer toSccId = sccIdByNode.get(edge.to());

            if (fromSccId == null || toSccId == null) {
                continue;
            }

            if (fromSccId.equals(toSccId)) {
                internalEdgesByScc.get(fromSccId).add(edge);
            } else {
                outgoingEdgesByScc.get(fromSccId).add(edge);
                inDegrees[toSccId]++;
            }
        }

        return new SccExecutionGraph(sccs, sccIdByNode, internalEdgesByScc, outgoingEdgesByScc, inDegrees);
    }

    private static LinearSolveResult solveLinearSystem(double[][] matrix, int nodeCount) {
        for (int pivotColumn = 0; pivotColumn < nodeCount; pivotColumn++) {
            int pivotRow = pivotColumn;
            double pivotMagnitude = Math.abs(matrix[pivotRow][pivotColumn]);

            for (int row = pivotColumn + 1; row < nodeCount; row++) {
                double candidateMagnitude = Math.abs(matrix[row][pivotColumn]);

                if (candidateMagnitude > pivotMagnitude) {
                    pivotRow = row;
                    pivotMagnitude = candidateMagnitude;
                }
            }

            if (!Double.isFinite(pivotMagnitude) || pivotMagnitude <= PIVOT_EPSILON) {
                return LinearSolveResult.unsolved();
            }

            if (pivotRow != pivotColumn) {
                double[] swap = matrix[pivotColumn];
                matrix[pivotColumn] = matrix[pivotRow];
                matrix[pivotRow] = swap;
            }

            double pivot = matrix[pivotColumn][pivotColumn];

            for (int column = pivotColumn; column <= nodeCount; column++) {
                matrix[pivotColumn][column] /= pivot;
            }

            for (int row = 0; row < nodeCount; row++) {
                if (row == pivotColumn) {
                    continue;
                }

                double factor = matrix[row][pivotColumn];

                if (factor == 0.0) {
                    continue;
                }

                for (int column = pivotColumn; column <= nodeCount; column++) {
                    matrix[row][column] -= factor * matrix[pivotColumn][column];
                }
            }
        }

        double[] solution = new double[nodeCount];

        for (int row = 0; row < nodeCount; row++) {
            solution[row] = matrix[row][nodeCount];
        }

        return LinearSolveResult.solved(solution);
    }

    private static double residual(
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            double[] powers
    ) {
        double[] reconstructed = new double[powers.length];
        System.arraycopy(source, 0, reconstructed, 0, source.length);

        for (PortGraphEdge edge : graph.edges()) {
            Integer fromId = nodeIds.get(edge.from());
            Integer toId = nodeIds.get(edge.to());

            if (fromId == null || toId == null) {
                continue;
            }

            reconstructed[toId] += powers[fromId] * edge.sampleGain();
        }

        double residual = 0.0;

        for (int index = 0; index < powers.length; index++) {
            residual = Math.max(residual, Math.abs(reconstructed[index] - powers[index]));
        }

        return residual;
    }

    private static double total(double[] powers) {
        double total = 0.0;

        for (double power : powers) {
            if (power > 0.0) {
                total += power;
            }
        }

        return total;
    }

    private static boolean hasMeaningfulNegativePower(double[] powers) {
        for (double power : powers) {
            if (power < -RESIDUAL_EPSILON) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasNonFinitePower(double[] powers) {
        for (double power : powers) {
            if (!Double.isFinite(power)) {
                return true;
            }
        }

        return false;
    }

    private static void clampTinyNegativePowers(double[] powers) {
        for (int index = 0; index < powers.length; index++) {
            if (powers[index] < 0.0 && powers[index] >= -RESIDUAL_EPSILON) {
                powers[index] = 0.0;
            }
        }
    }

    private record LocalTreeEdge(int fromLocalNodeId, int toLocalNodeId, double gain) {
    }

    private record SccLocalGraph(boolean valid, List<PortGraphNode> nodes, List<LocalTreeEdge> treeEdges) {
        private static SccLocalGraph invalid() {
            return new SccLocalGraph(false, List.of(), List.of());
        }
    }

    private record LinearSolveResult(boolean solved, double[] solution) {
        private static LinearSolveResult solved(double[] solution) {
            return new LinearSolveResult(true, solution);
        }

        private static LinearSolveResult unsolved() {
            return new LinearSolveResult(false, new double[0]);
        }
    }

    private record SccExecutionGraph(
            List<PortGraphScc> sccs,
            Map<PortGraphNode, Integer> sccIdByNode,
            List<List<PortGraphEdge>> internalEdgesByScc,
            List<List<PortGraphEdge>> outgoingEdgesByScc,
            int[] inDegrees
    ) {
    }

    private ChordFeedbackScalarSolver() {
    }
}
