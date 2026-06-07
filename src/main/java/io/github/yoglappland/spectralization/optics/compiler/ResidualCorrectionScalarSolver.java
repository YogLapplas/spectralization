package io.github.yoglappland.spectralization.optics.compiler;

import java.util.Map;
import java.util.Optional;

public final class ResidualCorrectionScalarSolver {
    public static boolean implemented() {
        return false;
    }

    public static Optional<ScalarPowerSolution> solve(
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            ScalarSolverPlan solverPlan
    ) {
        return Optional.empty();
    }

    private ResidualCorrectionScalarSolver() {
    }
}
