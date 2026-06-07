package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ScalarPowerSolution(
        ScalarSolverKind solverKind,
        ScalarSolverPlan solverPlan,
        boolean converged,
        boolean unstable,
        int iterations,
        double residual,
        double maxNodePower,
        double totalNodePower,
        Map<PortGraphNode, Double> powerByNode,
        List<ScalarSolverRegionResult> regionResults
) {
    public ScalarPowerSolution {
        Objects.requireNonNull(solverKind, "solverKind");
        Objects.requireNonNull(solverPlan, "solverPlan");
        Objects.requireNonNull(powerByNode, "powerByNode");
        Objects.requireNonNull(regionResults, "regionResults");

        if (iterations < 0) {
            throw new IllegalArgumentException("Power solution iterations must be non-negative");
        }

        if (!Double.isFinite(residual) || residual < 0.0) {
            throw new IllegalArgumentException("Power solution residual must be finite and non-negative");
        }

        if (!Double.isFinite(maxNodePower) || maxNodePower < 0.0) {
            throw new IllegalArgumentException("Power solution max node power must be finite and non-negative");
        }

        if (!Double.isFinite(totalNodePower) || totalNodePower < 0.0) {
            throw new IllegalArgumentException("Power solution total node power must be finite and non-negative");
        }

        powerByNode = Map.copyOf(powerByNode);
        regionResults = List.copyOf(regionResults);
    }

    public static ScalarPowerSolution empty() {
        return ScalarPowerSolutions.empty(ScalarSolverKind.NONE, ScalarSolverPlan.empty());
    }

    public double powerAt(PortGraphNode node) {
        return powerByNode.getOrDefault(node, 0.0);
    }

    public boolean reliableForReadout() {
        return converged && !unstable;
    }

    public List<Map.Entry<PortGraphNode, Double>> strongestNodes(int limit) {
        return powerByNode.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.0)
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .limit(Math.max(0, limit))
                .toList();
    }
}
