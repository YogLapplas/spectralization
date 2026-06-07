package io.github.yoglappland.spectralization.optics.compiler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScalarPowerSolver {
    private static final int MAX_ITERATIONS = 256;
    private static final double ABSOLUTE_EPSILON = 1.0E-6;
    private static final double RELATIVE_EPSILON = 1.0E-7;
    private static final double MAX_TOTAL_POWER = 1.0E12;

    public static ScalarPowerSolution solve(CompiledPortGraph graph, double sourcePower) {
        if (sourcePower <= 0.0) {
            return ScalarPowerSolution.empty();
        }

        return solve(graph, Map.of(graph.sourceNode(), sourcePower));
    }

    public static ScalarPowerSolution solve(CompiledPortGraph graph, Map<PortGraphNode, Double> sourcePowersByNode) {
        if (sourcePowersByNode.isEmpty()) {
            return ScalarPowerSolution.empty();
        }

        if (graph.nodes().isEmpty()) {
            return ScalarPowerSolution.empty();
        }

        Map<PortGraphNode, Integer> nodeIds = nodeIds(graph.nodes());
        double[] source = new double[graph.nodes().size()];
        double[] current = new double[graph.nodes().size()];
        double[] next = new double[graph.nodes().size()];
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
            return ScalarPowerSolution.empty();
        }
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
                return buildSolution(true, false, iteration + 1, residual, graph.nodes(), current);
            }
        }

        return buildSolution(false, unstable, iteration, residual, graph.nodes(), current);
    }

    private static ScalarPowerSolution buildSolution(
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
                converged,
                unstable,
                iterations,
                residual,
                maxPower,
                totalPower,
                powerByNode
        );
    }

    private static Map<PortGraphNode, Integer> nodeIds(List<PortGraphNode> nodes) {
        Map<PortGraphNode, Integer> nodeIds = new HashMap<>();

        for (int index = 0; index < nodes.size(); index++) {
            nodeIds.put(nodes.get(index), index);
        }

        return nodeIds;
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

    private ScalarPowerSolver() {
    }
}
