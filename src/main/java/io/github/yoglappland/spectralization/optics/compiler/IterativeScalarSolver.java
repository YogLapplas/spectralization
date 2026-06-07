package io.github.yoglappland.spectralization.optics.compiler;

import java.util.Map;

public final class IterativeScalarSolver {
    private static final int MAX_ITERATIONS = 256;
    private static final double ABSOLUTE_EPSILON = 1.0E-6;
    private static final double RELATIVE_EPSILON = 1.0E-7;
    private static final double MAX_TOTAL_POWER = 1.0E12;

    public static ScalarPowerSolution solve(
            CompiledPortGraph graph,
            Map<PortGraphNode, Integer> nodeIds,
            double[] source,
            ScalarSolverPlan solverPlan
    ) {
        double[] current = new double[graph.nodes().size()];
        double[] next = new double[graph.nodes().size()];
        double residual = 0.0;
        boolean unstable = false;
        int iteration = 0;

        for (; iteration < MAX_ITERATIONS; iteration++) {
            System.arraycopy(source, 0, next, 0, source.length);

            for (PortGraphEdge edge : graph.edges()) {
                Integer fromId = nodeIds.get(edge.from());
                Integer toId = nodeIds.get(edge.to());

                if (fromId == null || toId == null) {
                    continue;
                }

                next[toId] += current[fromId] * edge.sampleGain();
            }

            residual = maxDifference(current, next);
            double totalPower = total(next);

            if (!Double.isFinite(totalPower) || totalPower > MAX_TOTAL_POWER) {
                unstable = true;
                break;
            }

            double scale = Math.max(1.0, totalPower);
            double[] swap = current;
            current = next;
            next = swap;
            fillZero(next);

            if (residual <= ABSOLUTE_EPSILON || residual <= RELATIVE_EPSILON * scale) {
                return ScalarPowerSolutions.fromGraphPowers(
                        ScalarSolverKind.ITERATIVE_FIXED_POINT,
                        solverPlan,
                        true,
                        false,
                        iteration + 1,
                        residual,
                        graph,
                        nodeIds,
                        source,
                        current
                );
            }
        }

        return ScalarPowerSolutions.fromGraphPowers(
                ScalarSolverKind.ITERATIVE_FIXED_POINT,
                solverPlan,
                false,
                unstable,
                iteration,
                residual,
                graph,
                nodeIds,
                source,
                current
        );
    }

    private static double maxDifference(double[] left, double[] right) {
        double maxDifference = 0.0;

        for (int index = 0; index < left.length; index++) {
            maxDifference = Math.max(maxDifference, Math.abs(left[index] - right[index]));
        }

        return maxDifference;
    }

    private static double total(double[] values) {
        double total = 0.0;

        for (double value : values) {
            total += value;
        }

        return total;
    }

    private static void fillZero(double[] values) {
        for (int index = 0; index < values.length; index++) {
            values[index] = 0.0;
        }
    }

    private IterativeScalarSolver() {
    }
}
