package io.github.yoglappland.spectralization.optics.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MixedRegionScalarSolver {
    private static final double RESIDUAL_EPSILON = 1.0E-6;
    private static final double RELATIVE_RESIDUAL_EPSILON = 1.0E-7;
    private static final double MAX_TOTAL_POWER = 1.0E12;

    public static boolean implemented() {
        return true;
    }

    public static Optional<ScalarPowerSolution> solve(
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            ScalarSolverPlan solverPlan
    ) {
        if (solverPlan.primarySolverKind() != ScalarSolverKind.MIXED_REGION) {
            return Optional.empty();
        }

        int nodeCount = graph.nodes().size();
        double[] powers = new double[nodeCount];
        System.arraycopy(source, 0, powers, 0, source.length);

        SccExecutionGraph executionGraph = buildSccExecutionGraph(graph);
        Map<Integer, ScalarSolverRegion> feedbackRegionsByGraphSccId = feedbackRegionsByGraphSccId(solverPlan);
        ArrayDeque<Integer> pendingSccs = new ArrayDeque<>();

        for (int sccIndex = 0; sccIndex < executionGraph.sccs().size(); sccIndex++) {
            if (executionGraph.inDegrees()[sccIndex] == 0) {
                pendingSccs.addLast(sccIndex);
            }
        }

        int visitedSccs = 0;

        while (!pendingSccs.isEmpty()) {
            int sccIndex = pendingSccs.removeFirst();
            PortGraphScc scc = executionGraph.sccs().get(sccIndex);
            visitedSccs++;

            if (scc.feedback() && !solveFeedbackRegion(
                    scc,
                    feedbackRegionsByGraphSccId.get(scc.id()),
                    solverPlan,
                    executionGraph.internalEdgesByScc().get(sccIndex),
                    nodeIds,
                    powers
            )) {
                return Optional.empty();
            }

            for (PortGraphEdge edge : executionGraph.outgoingEdgesByScc().get(sccIndex)) {
                Integer fromId = nodeIds.get(edge.from());
                Integer toId = nodeIds.get(edge.to());

                if (fromId != null && toId != null) {
                    powers[toId] += powers[fromId] * edge.sampleGain();
                }

                int targetSccIndex = executionGraph.sccIndexByNode().get(edge.to());
                executionGraph.inDegrees()[targetSccIndex]--;

                if (executionGraph.inDegrees()[targetSccIndex] == 0) {
                    pendingSccs.addLast(targetSccIndex);
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
                ScalarSolverKind.MIXED_REGION,
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

    private static boolean solveFeedbackRegion(
            PortGraphScc scc,
            ScalarSolverRegion region,
            ScalarSolverPlan solverPlan,
            List<PortGraphEdge> internalEdges,
            Map<PortGraphNode, Integer> nodeIds,
            double[] powers
    ) {
        if (region == null) {
            return false;
        }

        return switch (region.executionSolverKind()) {
            case FEEDBACK_SCC_EXACT -> FeedbackSccScalarSolver.solveFeedbackScc(scc, internalEdges, nodeIds, powers);
            case FEEDBACK_CHORD -> ChordFeedbackScalarSolver.systemFor(solverPlan.chordFeedbackPlan(), scc.id())
                    .filter(ChordFeedbackSystem::compilable)
                    .filter(system -> ChordFeedbackScalarSolver.solveChordScc(scc, system, internalEdges, nodeIds, powers))
                    .isPresent();
            default -> false;
        };
    }

    private static Map<Integer, ScalarSolverRegion> feedbackRegionsByGraphSccId(ScalarSolverPlan solverPlan) {
        Map<Integer, ScalarSolverRegion> regionsByGraphSccId = new HashMap<>();

        for (ScalarSolverRegion region : solverPlan.regions()) {
            if (region.feedback()) {
                regionsByGraphSccId.put(region.graphSccId(), region);
            }
        }

        return regionsByGraphSccId;
    }

    private static SccExecutionGraph buildSccExecutionGraph(CompiledPortGraph graph) {
        List<PortGraphScc> sccs = new ArrayList<>(graph.sccs());
        sccs.sort((left, right) -> Integer.compare(left.id(), right.id()));
        Map<PortGraphNode, Integer> sccIndexByNode = new HashMap<>();
        List<List<PortGraphEdge>> internalEdgesByScc = new ArrayList<>(sccs.size());
        List<List<PortGraphEdge>> outgoingEdgesByScc = new ArrayList<>(sccs.size());
        int[] inDegrees = new int[sccs.size()];

        for (int sccIndex = 0; sccIndex < sccs.size(); sccIndex++) {
            internalEdgesByScc.add(new ArrayList<>());
            outgoingEdgesByScc.add(new ArrayList<>());

            for (PortGraphNode node : sccs.get(sccIndex).nodes()) {
                sccIndexByNode.put(node, sccIndex);
            }
        }

        for (PortGraphEdge edge : graph.edges()) {
            Integer fromSccIndex = sccIndexByNode.get(edge.from());
            Integer toSccIndex = sccIndexByNode.get(edge.to());

            if (fromSccIndex == null || toSccIndex == null) {
                continue;
            }

            if (fromSccIndex.equals(toSccIndex)) {
                internalEdgesByScc.get(fromSccIndex).add(edge);
            } else {
                outgoingEdgesByScc.get(fromSccIndex).add(edge);
                inDegrees[toSccIndex]++;
            }
        }

        return new SccExecutionGraph(sccs, sccIndexByNode, internalEdgesByScc, outgoingEdgesByScc, inDegrees);
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

    private record SccExecutionGraph(
            List<PortGraphScc> sccs,
            Map<PortGraphNode, Integer> sccIndexByNode,
            List<List<PortGraphEdge>> internalEdgesByScc,
            List<List<PortGraphEdge>> outgoingEdgesByScc,
            int[] inDegrees
    ) {
    }

    private MixedRegionScalarSolver() {
    }
}
