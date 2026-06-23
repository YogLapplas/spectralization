package io.github.yoglappland.spectralization.optics.compiler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ScalarPowerSolutions {
    static ScalarPowerSolution empty(ScalarSolverKind solverKind, ScalarSolverPlan solverPlan) {
        return new ScalarPowerSolution(
                solverKind,
                solverPlan,
                true,
                false,
                0,
                0.0,
                0.0,
                0.0,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of()
        );
    }

    static ScalarPowerSolution fromPowers(
            ScalarSolverKind solverKind,
            ScalarSolverPlan solverPlan,
            boolean converged,
            boolean unstable,
            int iterations,
            double residual,
            List<PortGraphNode> nodes,
            double[] powers
    ) {
        Map<PortGraphNode, Double> powerByNode = new HashMap<>();
        double maxPower = 0.0;
        double totalPower = 0.0;

        for (int index = 0; index < nodes.size(); index++) {
            double power = powers[index];

            if (power <= 0.0) {
                continue;
            }

            powerByNode.put(nodes.get(index), power);
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
                powerByNode,
                powerByNode,
                Map.of(),
                List.of()
        );
    }

    static ScalarPowerSolution fromGraphPowers(
            ScalarSolverKind solverKind,
            ScalarSolverPlan solverPlan,
            boolean converged,
            boolean unstable,
            int iterations,
            double residual,
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            double[] powers
    ) {
        Map<PortGraphNode, Double> powerByNode = new HashMap<>();
        double maxPower = 0.0;
        double totalPower = 0.0;

        for (int index = 0; index < graph.nodes().size(); index++) {
            double power = powers[index];

            if (power <= 0.0) {
                continue;
            }

            powerByNode.put(graph.nodes().get(index), power);
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
                powerByNode,
                powerByNode,
                Map.of(),
                regionResults(solverKind, solverPlan, graph, nodeIds, source, powers, converged, unstable, iterations)
        );
    }

    static Map<PortGraphNode, Integer> nodeIds(List<PortGraphNode> nodes) {
        Map<PortGraphNode, Integer> nodeIds = new HashMap<>();

        for (int index = 0; index < nodes.size(); index++) {
            nodeIds.put(nodes.get(index), index);
        }

        return nodeIds;
    }

    private static List<ScalarSolverRegionResult> regionResults(
            ScalarSolverKind solverKind,
            ScalarSolverPlan solverPlan,
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            double[] powers,
            boolean converged,
            boolean unstable,
            int iterations
    ) {
        return solverPlan.regions().stream()
                .map(region -> regionResult(
                        solverKind,
                        region,
                        graph,
                        nodeIds,
                        source,
                        powers,
                        converged,
                        unstable,
                        iterations
                ))
                .toList();
    }

    private static ScalarSolverRegionResult regionResult(
            ScalarSolverKind solverKind,
            ScalarSolverRegion region,
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            double[] powers,
            boolean globalConverged,
            boolean globalUnstable,
            int iterations
    ) {
        Set<PortGraphNode> regionNodes = new HashSet<>(region.nodes());
        double maxPower = 0.0;
        double totalPower = 0.0;
        double residual = 0.0;

        for (PortGraphNode node : regionNodes) {
            Integer nodeId = nodeIds.get(node);

            if (nodeId == null) {
                continue;
            }

            double power = powers[nodeId];

            if (power > 0.0) {
                maxPower = Math.max(maxPower, power);
                totalPower += power;
            }
        }

        for (PortGraphNode node : regionNodes) {
            Integer nodeId = nodeIds.get(node);

            if (nodeId == null) {
                continue;
            }

            double reconstructed = source[nodeId];

            for (PortGraphEdge edge : graph.edges()) {
                if (!edge.to().equals(node)) {
                    continue;
                }

                Integer fromId = nodeIds.get(edge.from());

                if (fromId == null) {
                    continue;
                }

                reconstructed += powers[fromId] * edge.sampleGain();
            }

            residual = Math.max(residual, Math.abs(reconstructed - powers[nodeId]));
        }

        return new ScalarSolverRegionResult(
                region.id(),
                region.graphSccId(),
                executedSolverKind(solverKind, region),
                globalConverged && Double.isFinite(residual),
                globalUnstable || !Double.isFinite(residual),
                iterations,
                Double.isFinite(residual) ? residual : Double.MAX_VALUE,
                maxPower,
                totalPower
        );
    }

    private static ScalarSolverKind executedSolverKind(ScalarSolverKind solverKind, ScalarSolverRegion region) {
        if (solverKind == ScalarSolverKind.NO_EXACT_SOLVER) {
            return ScalarSolverKind.NO_EXACT_SOLVER;
        }

        if (solverKind == ScalarSolverKind.NONE) {
            return ScalarSolverKind.NONE;
        }

        return region.executionSolverKind();
    }

    private ScalarPowerSolutions() {
    }
}
