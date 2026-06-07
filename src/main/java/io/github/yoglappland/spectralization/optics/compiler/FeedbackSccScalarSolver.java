package io.github.yoglappland.spectralization.optics.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FeedbackSccScalarSolver {
    private static final double PIVOT_EPSILON = 1.0E-10;
    private static final double RESIDUAL_EPSILON = 1.0E-6;
    private static final double RELATIVE_RESIDUAL_EPSILON = 1.0E-7;
    private static final double MAX_TOTAL_POWER = 1.0E12;

    public static boolean implemented() {
        return true;
    }

    public static boolean supports(ScalarSolverRegion region) {
        return region.feedback() && region.executionSolverKind() == ScalarSolverKind.FEEDBACK_SCC_EXACT;
    }

    public static Optional<ScalarPowerSolution> solve(
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            ScalarSolverPlan solverPlan
    ) {
        if (solverPlan.primarySolverKind() != ScalarSolverKind.FEEDBACK_SCC_EXACT) {
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
                if (!solveFeedbackScc(scc, executionGraph.internalEdgesByScc().get(sccId), nodeIds, powers)) {
                    return Optional.of(unstableSolution(graph, solverPlan, nodeCount));
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

        if (visitedSccs != executionGraph.sccs().size()) {
            return Optional.of(unstableSolution(graph, solverPlan, nodeCount));
        }

        if (hasNonFinitePower(powers)) {
            return Optional.of(unstableSolution(graph, solverPlan, nodeCount));
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
                ScalarSolverKind.FEEDBACK_SCC_EXACT,
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

    static boolean solveFeedbackScc(
            PortGraphScc scc,
            List<PortGraphEdge> internalEdges,
            Map<PortGraphNode, Integer> nodeIds,
            double[] powers
    ) {
        List<PortGraphNode> sccNodes = new ArrayList<>(scc.nodes());
        Map<Integer, Integer> localIdsByGlobalId = new HashMap<>();

        for (int localId = 0; localId < sccNodes.size(); localId++) {
            Integer globalId = nodeIds.get(sccNodes.get(localId));

            if (globalId == null) {
                return false;
            }

            localIdsByGlobalId.put(globalId, localId);
        }

        int localNodeCount = sccNodes.size();
        double[][] matrix = new double[localNodeCount][localNodeCount + 1];

        for (int row = 0; row < localNodeCount; row++) {
            Integer globalId = nodeIds.get(sccNodes.get(row));

            if (globalId == null) {
                return false;
            }

            matrix[row][row] = 1.0;
            matrix[row][localNodeCount] = powers[globalId];
        }

        for (PortGraphEdge edge : internalEdges) {
            Integer fromGlobalId = nodeIds.get(edge.from());
            Integer toGlobalId = nodeIds.get(edge.to());

            if (fromGlobalId == null || toGlobalId == null) {
                return false;
            }

            Integer fromLocalId = localIdsByGlobalId.get(fromGlobalId);
            Integer toLocalId = localIdsByGlobalId.get(toGlobalId);

            if (fromLocalId == null || toLocalId == null) {
                return false;
            }

            matrix[toLocalId][fromLocalId] -= edge.sampleGain();
        }

        LinearSolveResult solveResult = solveLinearSystem(matrix, localNodeCount);

        if (!solveResult.solved() || hasNonFinitePower(solveResult.solution())) {
            return false;
        }

        for (int localId = 0; localId < localNodeCount; localId++) {
            Integer globalId = nodeIds.get(sccNodes.get(localId));

            if (globalId == null) {
                return false;
            }

            powers[globalId] = solveResult.solution()[localId];
        }

        return true;
    }

    private static ScalarPowerSolution unstableSolution(
            CompiledPortGraph graph,
            ScalarSolverPlan solverPlan,
            int nodeCount
    ) {
        double[] emptySource = new double[nodeCount];
        Map<PortGraphNode, Integer> nodeIds = ScalarPowerSolutions.nodeIds(graph.nodes());

        return ScalarPowerSolutions.fromGraphPowers(
                ScalarSolverKind.FEEDBACK_SCC_EXACT,
                solverPlan,
                false,
                true,
                1,
                MAX_TOTAL_POWER,
                graph,
                nodeIds,
                emptySource,
                new double[nodeCount]
        );
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

    private FeedbackSccScalarSolver() {
    }
}
