package io.github.yoglappland.spectralization.optics.compiler;

import java.util.Map;

public final class ScalarPowerSolver {
    public static ScalarPowerSolution solve(CompiledPortGraph graph, double sourcePower) {
        if (sourcePower <= 0.0) {
            return ScalarPowerSolutions.empty(ScalarSolverKind.NONE, ScalarSolverPlanner.plan(graph));
        }

        return solve(graph, Map.of(graph.sourceNode(), sourcePower));
    }

    public static ScalarPowerSolution solve(CompiledPortGraph graph, Map<PortGraphNode, Double> sourcePowersByNode) {
        ScalarSolverPlan solverPlan = ScalarSolverPlanner.plan(graph);

        if (sourcePowersByNode.isEmpty() || graph.nodes().isEmpty()) {
            return ScalarPowerSolutions.empty(ScalarSolverKind.NONE, solverPlan);
        }

        Map<PortGraphNode, Integer> nodeIds = ScalarPowerSolutions.nodeIds(graph.nodes());
        double[] source = new double[graph.nodes().size()];
        boolean hasMappedSource = false;

        for (Map.Entry<PortGraphNode, Double> entry : sourcePowersByNode.entrySet()) {
            Integer sourceId = nodeIds.get(entry.getKey());
            double sourcePower = entry.getValue();

            if (sourceId == null || sourcePower <= 0.0) {
                continue;
            }

            source[sourceId] += sourcePower;
            hasMappedSource = true;
        }

        if (!hasMappedSource) {
            return ScalarPowerSolutions.empty(ScalarSolverKind.NONE, solverPlan);
        }

        if (solverPlan.acyclic()) {
            return TopologicalScalarSolver.solve(graph, nodeIds, source, solverPlan);
        }

        if (solverPlan.primarySolverKind() == ScalarSolverKind.FEEDBACK_SCC_EXACT) {
            return FeedbackSccScalarSolver.solve(graph, nodeIds, source, solverPlan)
                    .orElseGet(() -> failedPlannedSolverSolution(
                            ScalarSolverKind.FEEDBACK_SCC_EXACT,
                            graph,
                            nodeIds,
                            source,
                            solverPlan
                    ));
        }

        if (solverPlan.primarySolverKind() == ScalarSolverKind.FEEDBACK_CHORD) {
            return ChordFeedbackScalarSolver.solve(graph, nodeIds, source, solverPlan)
                    .orElseGet(() -> failedPlannedSolverSolution(
                            ScalarSolverKind.FEEDBACK_CHORD,
                            graph,
                            nodeIds,
                            source,
                            solverPlan
                    ));
        }

        if (solverPlan.primarySolverKind() == ScalarSolverKind.MIXED_REGION) {
            return MixedRegionScalarSolver.solve(graph, nodeIds, source, solverPlan)
                    .orElseGet(() -> failedPlannedSolverSolution(
                            ScalarSolverKind.MIXED_REGION,
                            graph,
                            nodeIds,
                            source,
                            solverPlan
                    ));
        }

        return failedPlannedSolverSolution(
                ScalarSolverKind.NO_EXACT_SOLVER,
                graph,
                nodeIds,
                source,
                solverPlan
        );
    }

    private static ScalarPowerSolution failedPlannedSolverSolution(
            ScalarSolverKind solverKind,
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            ScalarSolverPlan solverPlan
    ) {
        return ScalarPowerSolutions.fromGraphPowers(
                solverKind,
                solverPlan,
                false,
                true,
                1,
                1.0E12,
                graph,
                nodeIds,
                source,
                new double[graph.nodes().size()]
        );
    }

    private ScalarPowerSolver() {
    }
}
