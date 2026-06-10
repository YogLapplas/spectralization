package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GainGraphRewriter {
    static CompiledPortGraph applyEffectiveGains(
            CompiledPortGraph graph,
            Map<Integer, Double> effectiveGainsByEdgeId
    ) {
        if (!hasActiveScheduledGain(effectiveGainsByEdgeId)) {
            return graph;
        }

        List<PortGraphEdge> edges = new ArrayList<>(graph.edges().size());

        for (PortGraphEdge edge : graph.edges()) {
            double gain = effectiveGainsByEdgeId.getOrDefault(edge.id(), 1.0);

            if (gain <= 1.0 + GainSourceCollector.MIN_GAIN_DELTA) {
                edges.add(edge);
                continue;
            }

            edges.add(new PortGraphEdge(
                    edge.id(),
                    edge.kind(),
                    edge.from(),
                    edge.to(),
                    edge.distance(),
                    edge.sampleInputPower(),
                    edge.sampleOutputPower() * gain,
                    edge.sampleFrequency(),
                    scaledFrequencyGains(edge.sampleGainByFrequency(), edge.sampleFrequency(), gain)
            ));
        }

        return new CompiledPortGraph(
                graph.sourcePos(),
                graph.sourceDirection(),
                graph.sourceNode(),
                graph.nodes(),
                edges,
                graph.sccs(),
                graph.chords(),
                graph.terminationCount()
        );
    }

    private static boolean hasActiveScheduledGain(Map<Integer, Double> effectiveGainsByEdgeId) {
        for (double gain : effectiveGainsByEdgeId.values()) {
            if (gain > 1.0 + GainSourceCollector.MIN_GAIN_DELTA) {
                return true;
            }
        }

        return false;
    }

    private static Map<FrequencyKey, Double> scaledFrequencyGains(
            Map<FrequencyKey, Double> gains,
            FrequencyKey targetFrequency,
            double factor
    ) {
        if (gains.isEmpty()) {
            return Map.of();
        }

        Map<FrequencyKey, Double> scaled = new HashMap<>();

        for (Map.Entry<FrequencyKey, Double> entry : gains.entrySet()) {
            double gain = entry.getKey().equals(targetFrequency)
                    ? entry.getValue() * factor
                    : entry.getValue();
            scaled.put(entry.getKey(), gain);
        }

        return scaled;
    }

    private GainGraphRewriter() {
    }
}
