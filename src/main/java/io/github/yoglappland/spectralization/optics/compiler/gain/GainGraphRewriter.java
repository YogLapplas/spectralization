package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.compiler.SaturatingEdgeGain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class GainGraphRewriter {
    static CompiledPortGraph applyEffectiveGains(
            CompiledPortGraph graph,
            Map<Integer, GainSource> gainSourcesByEdgeId,
            Map<Integer, Double> effectiveGainsByEdgeId
    ) {
        if (!hasActiveScheduledGain(effectiveGainsByEdgeId)) {
            return graph;
        }

        List<PortGraphEdge> edges = new ArrayList<>(graph.edges().size());

        for (PortGraphEdge edge : graph.edges()) {
            double gain = effectiveGainsByEdgeId.getOrDefault(edge.id(), 1.0);
            GainSource source = gainSourcesByEdgeId.getOrDefault(edge.id(), GainSource.passive());

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
                    edge.sampleOutputPower(),
                    edge.sampleFrequency(),
                    scaledFrequencyGains(edge, source),
                    saturatingFrequencyGains(edge, source)
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

    private static Map<FrequencyKey, Double> scaledFrequencyGains(PortGraphEdge edge, GainSource source) {
        Map<FrequencyKey, Double> scaled = new HashMap<>();

        for (Map.Entry<FrequencyKey, Double> entry : edge.sampleGainByFrequency().entrySet()) {
            FrequencyKey frequency = entry.getKey();
            double activeGain = source.baseGainFor(frequency);
            double gain = activeGain > 1.0D + GainSourceCollector.MIN_GAIN_DELTA
                    ? entry.getValue() * activeGain
                    : entry.getValue();
            scaled.put(frequency, gain);
        }

        for (Map.Entry<FrequencyKey, Double> entry : source.baseGainByFrequency().entrySet()) {
            FrequencyKey frequency = entry.getKey();
            double activeGain = entry.getValue();

            if (activeGain <= 1.0D + GainSourceCollector.MIN_GAIN_DELTA || scaled.containsKey(frequency)) {
                continue;
            }

            scaled.put(frequency, edge.sampleGainFor(frequency) * activeGain);
        }

        if (scaled.isEmpty()) {
            return Map.of();
        }

        return Map.copyOf(scaled);
    }

    private static Map<FrequencyKey, SaturatingEdgeGain> saturatingFrequencyGains(
            PortGraphEdge edge,
            GainSource source
    ) {
        if (source.saturatedExtraOutput() <= 0.0D) {
            return Map.of();
        }

        Map<FrequencyKey, SaturatingEdgeGain> gains = new HashMap<>();
        Map<FrequencyKey, Double> frequencies = new HashMap<>(edge.sampleGainByFrequency());

        for (FrequencyKey frequency : source.baseGainByFrequency().keySet()) {
            frequencies.putIfAbsent(frequency, edge.sampleGainFor(frequency));
        }

        if (frequencies.isEmpty()) {
            frequencies.put(edge.sampleFrequency(), edge.sampleGainFor(edge.sampleFrequency()));
        }

        for (Map.Entry<FrequencyKey, Double> entry : frequencies.entrySet()) {
            FrequencyKey frequency = entry.getKey();
            double activeGain = source.baseGainFor(frequency);
            double saturatedExtraOutput = source.saturatedExtraOutputFor(frequency);

            if (saturatedExtraOutput <= 0.0D || activeGain <= 1.0D + GainSourceCollector.MIN_GAIN_DELTA) {
                continue;
            }

            double passiveGain = Math.max(0.0D, entry.getValue());

            if (passiveGain > 0.0D) {
                gains.put(frequency, new SaturatingEdgeGain(passiveGain, saturatedExtraOutput));
            }
        }

        return gains.isEmpty() ? Map.of() : Map.copyOf(gains);
    }

    private GainGraphRewriter() {
    }
}
