package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SpectralRadiusEstimator {
    private static final int POWER_ITERATIONS = 32;

    double estimate(CompiledPortGraph graph, GainGraphIndex index, EdgeGain edgeGain) {
        double rho = 0.0;

        for (PortGraphScc scc : graph.sccs()) {
            if (!scc.feedback()) {
                continue;
            }

            rho = Math.max(rho, estimateScc(scc, index, edgeGain));
        }

        return rho;
    }

    double estimateScc(PortGraphScc scc, GainGraphIndex index, EdgeGain edgeGain) {
        List<PortGraphNode> nodes = new ArrayList<>(scc.nodes());
        Map<PortGraphNode, Integer> nodeIds = new HashMap<>();

        for (int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
            nodeIds.put(nodes.get(nodeIndex), nodeIndex);
        }

        List<PortGraphEdge> internalEdges = index.internalEdges(scc);

        if (nodes.isEmpty() || internalEdges.isEmpty()) {
            return 0.0;
        }

        double[] current = new double[nodes.size()];
        double seed = 1.0 / nodes.size();

        for (int nodeIndex = 0; nodeIndex < current.length; nodeIndex++) {
            current[nodeIndex] = seed;
        }

        double norm = 0.0;

        for (int iteration = 0; iteration < POWER_ITERATIONS; iteration++) {
            double[] next = new double[nodes.size()];

            for (PortGraphEdge edge : internalEdges) {
                Integer fromId = nodeIds.get(edge.from());
                Integer toId = nodeIds.get(edge.to());

                if (fromId == null || toId == null) {
                    continue;
                }

                next[toId] += current[fromId] * edge.sampleGain() * edgeGain.gainFor(edge);
            }

            norm = l1Norm(next);

            if (!Double.isFinite(norm) || norm <= 0.0) {
                return 0.0;
            }

            for (int nodeIndex = 0; nodeIndex < next.length; nodeIndex++) {
                next[nodeIndex] /= norm;
            }

            current = next;
        }

        return norm;
    }

    private static double l1Norm(double[] values) {
        double norm = 0.0;

        for (double value : values) {
            norm += Math.abs(value);
        }

        return norm;
    }

    @FunctionalInterface
    interface EdgeGain {
        double gainFor(PortGraphEdge edge);
    }
}
