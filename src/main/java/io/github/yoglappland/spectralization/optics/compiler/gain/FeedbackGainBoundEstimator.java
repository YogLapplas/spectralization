package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FeedbackGainBoundEstimator {
    private static final int COLLATZ_ITERATIONS = 64;
    private static final double EPSILON = 1.0E-12;

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

        if (scc.beta1() == 1) {
            return simpleCycleSpectralRadius(scc, internalEdges, edgeGain);
        }

        return collatzUpperBound(scc, internalEdges, edgeGain);
    }

    private static double simpleCycleSpectralRadius(
            PortGraphScc scc,
            List<PortGraphEdge> internalEdges,
            EdgeGain edgeGain
    ) {
        double logProduct = 0.0D;
        int positiveEdges = 0;

        for (PortGraphEdge edge : internalEdges) {
            double gain = effectiveEdgeGain(edge, edgeGain);

            if (gain <= 0.0D) {
                return 0.0D;
            }

            logProduct += Math.log(gain);
            positiveEdges++;
        }

        if (positiveEdges == 0) {
            return 0.0D;
        }

        return Math.exp(logProduct / Math.max(1, scc.nodes().size()));
    }

    private static double collatzUpperBound(
            PortGraphScc scc,
            List<PortGraphEdge> internalEdges,
            EdgeGain edgeGain
    ) {
        List<PortGraphNode> nodes = new ArrayList<>(scc.nodes());
        Map<PortGraphNode, Integer> nodeIds = new HashMap<>();

        for (int index = 0; index < nodes.size(); index++) {
            nodeIds.put(nodes.get(index), index);
        }

        int nodeCount = nodes.size();
        double[] vector = new double[nodeCount];
        double[] next = new double[nodeCount];

        for (int index = 0; index < nodeCount; index++) {
            vector[index] = 1.0D / nodeCount;
        }

        double bound = rowColumnSumBound(scc, internalEdges, edgeGain);

        for (int iteration = 0; iteration < COLLATZ_ITERATIONS; iteration++) {
            for (int index = 0; index < nodeCount; index++) {
                next[index] = 0.0D;
            }

            for (PortGraphEdge edge : internalEdges) {
                Integer from = nodeIds.get(edge.from());
                Integer to = nodeIds.get(edge.to());

                if (from == null || to == null) {
                    continue;
                }

                double gain = effectiveEdgeGain(edge, edgeGain);

                if (gain > 0.0D) {
                    next[to] += gain * vector[from];
                }
            }

            double nextBound = 0.0D;
            double norm = 0.0D;

            for (int index = 0; index < nodeCount; index++) {
                if (vector[index] > EPSILON) {
                    nextBound = Math.max(nextBound, next[index] / vector[index]);
                }

                norm += next[index];
            }

            if (nextBound > 0.0D && Double.isFinite(nextBound)) {
                bound = Math.min(bound, nextBound);
            }

            if (!Double.isFinite(norm) || norm <= EPSILON) {
                return 0.0D;
            }

            for (int index = 0; index < nodeCount; index++) {
                vector[index] = Math.max(EPSILON, next[index] / norm);
            }
        }

        return bound;
    }

    private static double rowColumnSumBound(
            PortGraphScc scc,
            List<PortGraphEdge> internalEdges,
            EdgeGain edgeGain
    ) {
        Map<PortGraphNode, Double> outgoingSums = new HashMap<>();
        Map<PortGraphNode, Double> incomingSums = new HashMap<>();

        for (PortGraphEdge edge : internalEdges) {
            double gain = effectiveEdgeGain(edge, edgeGain);

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

    private static double effectiveEdgeGain(PortGraphEdge edge, EdgeGain edgeGain) {
        double gain = edge.sampleGain() * edgeGain.gainFor(edge);
        return Double.isFinite(gain) && gain > 0.0D ? gain : 0.0D;
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
