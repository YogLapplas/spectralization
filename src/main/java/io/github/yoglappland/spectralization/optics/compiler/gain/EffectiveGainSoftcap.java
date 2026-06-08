package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EffectiveGainSoftcap {
    static final double HARD_RHO = 0.985;

    private static final int SAFETY_BISECTION_STEPS = 14;
    private static final double SOFTCAP_SHARPNESS = 4.0;

    private final SpectralRadiusEstimator spectralRadiusEstimator;

    EffectiveGainSoftcap(SpectralRadiusEstimator spectralRadiusEstimator) {
        this.spectralRadiusEstimator = spectralRadiusEstimator;
    }

    ScheduledGains schedule(GainGraphIndex index, List<GainSource> gainSources) {
        Map<Integer, Double> effectiveGainsByEdgeId = new HashMap<>();
        Map<Integer, List<GainSource>> sourcesBySccId = sourcesBySccId(gainSources);
        double maxPassiveRho = 0.0;
        double totalGainHeadroom = 0.0;
        double maxModeWeight = 0.0;
        double maxSourceCap = 1.0;

        for (Map.Entry<Integer, List<GainSource>> entry : sourcesBySccId.entrySet()) {
            PortGraphScc scc = index.feedbackSccsById().get(entry.getKey());

            if (scc == null) {
                continue;
            }

            double passiveRho = spectralRadiusEstimator.estimateScc(scc, index, edge -> 1.0);
            maxPassiveRho = Math.max(maxPassiveRho, passiveRho);

            for (GainSource source : entry.getValue()) {
                double sourceCap = sourceCap(source);
                double effectiveGain = softCappedGain(source.baseGain(), sourceCap);
                effectiveGainsByEdgeId.put(source.edgeId(), effectiveGain);
                totalGainHeadroom += Math.max(0.0, sourceCap - 1.0);
                maxModeWeight = Math.max(maxModeWeight, source.modeWeight());
                maxSourceCap = Math.max(maxSourceCap, sourceCap);
            }
        }

        return new ScheduledGains(
                effectiveGainsByEdgeId,
                maxPassiveRho,
                totalGainHeadroom,
                maxModeWeight,
                maxSourceCap
        );
    }

    double estimateWithGains(
            CompiledPortGraph graph,
            GainGraphIndex index,
            Map<Integer, Double> effectiveGainsByEdgeId
    ) {
        return spectralRadiusEstimator.estimate(
                graph,
                index,
                edge -> effectiveGainsByEdgeId.getOrDefault(edge.id(), 1.0)
        );
    }

    Map<Integer, Double> shrinkExtraLogGainsToStable(
            CompiledPortGraph graph,
            GainGraphIndex index,
            Map<Integer, Double> effectiveGainsByEdgeId
    ) {
        double low = 0.0;
        double high = 1.0;

        for (int step = 0; step < SAFETY_BISECTION_STEPS; step++) {
            double middle = (low + high) * 0.5;
            Map<Integer, Double> gains = scaledExtraLogGains(effectiveGainsByEdgeId, middle);
            double rho = estimateWithGains(graph, index, gains);

            if (rho < HARD_RHO) {
                low = middle;
            } else {
                high = middle;
            }
        }

        return scaledExtraLogGains(effectiveGainsByEdgeId, low);
    }

    boolean hasActiveScheduledGain(Map<Integer, Double> effectiveGainsByEdgeId) {
        for (double gain : effectiveGainsByEdgeId.values()) {
            if (gain > 1.0 + GainSourceCollector.MIN_GAIN_DELTA) {
                return true;
            }
        }

        return false;
    }

    double maxEffectiveGain(Map<Integer, Double> effectiveGainsByEdgeId) {
        double maxGain = 1.0;

        for (double gain : effectiveGainsByEdgeId.values()) {
            maxGain = Math.max(maxGain, gain);
        }

        return maxGain;
    }

    private static Map<Integer, List<GainSource>> sourcesBySccId(List<GainSource> gainSources) {
        Map<Integer, List<GainSource>> sourcesBySccId = new HashMap<>();

        for (GainSource source : gainSources) {
            sourcesBySccId.computeIfAbsent(source.sccId(), ignored -> new ArrayList<>()).add(source);
        }

        return sourcesBySccId;
    }

    private static double sourceCap(GainSource source) {
        double rawExtraGain = Math.max(0.0, source.baseGain() - 1.0);
        double coupledExtraGain = rawExtraGain * Math.max(0.0, Math.min(1.0, source.modeWeight()));
        return 1.0 + coupledExtraGain;
    }

    private static double softCappedGain(double baseGain, double sourceCap) {
        if (baseGain <= 1.0 + GainSourceCollector.MIN_GAIN_DELTA
                || sourceCap <= 1.0 + GainSourceCollector.MIN_GAIN_DELTA) {
            return 1.0;
        }

        double rawExtraGain = Math.max(0.0, baseGain - 1.0);
        double capExtraGain = Math.max(0.0, sourceCap - 1.0);

        if (rawExtraGain <= 0.0 || capExtraGain <= 0.0) {
            return 1.0;
        }

        double ratio = rawExtraGain / capExtraGain;
        double denominator = Math.pow(1.0 + Math.pow(ratio, SOFTCAP_SHARPNESS), 1.0 / SOFTCAP_SHARPNESS);
        double effectiveExtraGain = rawExtraGain / denominator;

        if (!Double.isFinite(effectiveExtraGain) || effectiveExtraGain <= 0.0) {
            return 1.0;
        }

        return 1.0 + effectiveExtraGain;
    }

    private static Map<Integer, Double> scaledExtraLogGains(
            Map<Integer, Double> effectiveGainsByEdgeId,
            double scale
    ) {
        Map<Integer, Double> scaledGains = new HashMap<>();

        for (Map.Entry<Integer, Double> entry : effectiveGainsByEdgeId.entrySet()) {
            double gain = entry.getValue();

            if (gain <= 1.0 + GainSourceCollector.MIN_GAIN_DELTA) {
                scaledGains.put(entry.getKey(), 1.0);
                continue;
            }

            scaledGains.put(entry.getKey(), Math.exp(Math.log(gain) * scale));
        }

        return scaledGains;
    }
}
