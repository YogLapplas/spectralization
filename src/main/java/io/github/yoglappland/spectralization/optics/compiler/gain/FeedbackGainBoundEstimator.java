package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FeedbackGainBoundEstimator {
    double estimate(CompiledPortGraph graph, GainGraphIndex index, EdgeGain edgeGain) {
        double bound = 0.0;

        for (PortGraphScc scc : graph.sccs()) {
            if (scc.feedback()) {
                bound = Math.max(bound, estimateScc(scc, index, edgeGain));
            }
        }

        return bound;
    }

    double estimateScc(PortGraphScc scc, GainGraphIndex index, EdgeGain edgeGain) {
        List<PortGraphEdge> internalEdges = index.internalEdges(scc);

        if (scc.nodes().isEmpty() || internalEdges.isEmpty()) {
            return 0.0D;
        }

        Map<PortGraphNode, Double> outgoingSums = new HashMap<>();
        Map<PortGraphNode, Double> incomingSums = new HashMap<>();

        for (PortGraphEdge edge : internalEdges) {
            double gain = edge.sampleGain() * edgeGain.gainFor(edge);

            if (!Double.isFinite(gain) || gain <= 0.0D) {
                continue;
            }

            outgoingSums.merge(edge.from(), gain, Double::sum);
            incomingSums.merge(edge.to(), gain, Double::sum);
        }

        double outgoingBound = maxNodeSum(scc, outgoingSums);
        double incomingBound = maxNodeSum(scc, incomingSums);

        if (outgoingBound <= 0.0D) {
            return incomingBound;
        }

        if (incomingBound <= 0.0D) {
            return outgoingBound;
        }

        return Math.min(outgoingBound, incomingBound);
    }

    private static double maxNodeSum(PortGraphScc scc, Map<PortGraphNode, Double> sums) {
        double max = 0.0D;

        for (PortGraphNode node : scc.nodes()) {
            max = Math.max(max, sums.getOrDefault(node, 0.0D));
        }

        return max;
    }

    @FunctionalInterface
    interface EdgeGain {
        double gainFor(PortGraphEdge edge);
    }
}
