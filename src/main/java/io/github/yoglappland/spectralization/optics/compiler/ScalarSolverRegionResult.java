package io.github.yoglappland.spectralization.optics.compiler;

import java.util.Objects;

public record ScalarSolverRegionResult(
        int regionId,
        int graphSccId,
        ScalarSolverKind solverKind,
        boolean converged,
        boolean unstable,
        int iterations,
        double residual,
        double maxNodePower,
        double totalNodePower
) {
    public ScalarSolverRegionResult {
        Objects.requireNonNull(solverKind, "solverKind");

        if (regionId < 0) {
            throw new IllegalArgumentException("Solver region result id must be non-negative");
        }

        if (graphSccId < -1) {
            throw new IllegalArgumentException("Solver region result graph SCC id must be -1 or non-negative");
        }

        if (iterations < 0) {
            throw new IllegalArgumentException("Solver region result iterations must be non-negative");
        }

        if (!Double.isFinite(residual) || residual < 0.0) {
            throw new IllegalArgumentException("Solver region result residual must be finite and non-negative");
        }

        if (!Double.isFinite(maxNodePower) || maxNodePower < 0.0) {
            throw new IllegalArgumentException("Solver region result max power must be finite and non-negative");
        }

        if (!Double.isFinite(totalNodePower) || totalNodePower < 0.0) {
            throw new IllegalArgumentException("Solver region result total power must be finite and non-negative");
        }
    }
}
