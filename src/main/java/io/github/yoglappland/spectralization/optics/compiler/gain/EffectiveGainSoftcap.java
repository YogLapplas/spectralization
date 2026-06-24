package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class EffectiveGainSoftcap {
    static final double HARD_RHO = 0.985;

    private static final double SOFTCAP_SHARPNESS = 4.0;

    private final FeedbackGainBoundEstimator boundEstimator;

    EffectiveGainSoftcap(FeedbackGainBoundEstimator boundEstimator) {
        this.boundEstimator = boundEstimator;
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

            double passiveRho = boundEstimator.estimateScc(scc, index, edge -> 1.0);
            maxPassiveRho = Math.max(maxPassiveRho, passiveRho);
            Map<Integer, Double> tentativeGainsByEdgeId = new HashMap<>();

            for (GainSource source : entry.getValue()) {
                double sourceCap = sourceCap(source);
                double effectiveGain = softCappedGain(source.baseGain(), sourceCap);
                tentativeGainsByEdgeId.put(source.edgeId(), effectiveGain);
                totalGainHeadroom += Math.max(0.0, sourceCap - 1.0);
                maxModeWeight = Math.max(maxModeWeight, source.modeWeight());
                maxSourceCap = Math.max(maxSourceCap, sourceCap);
            }

            effectiveGainsByEdgeId.putAll(scaledExtraLinearGains(
                    tentativeGainsByEdgeId,
                    extraGainScaleToTarget(scc, index, tentativeGainsByEdgeId, HARD_RHO)
            ));
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
        return boundEstimator.estimate(
                graph,
                index,
                edge -> effectiveGainsByEdgeId.getOrDefault(edge.id(), 1.0)
        );
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

    private double extraGainScaleToTarget(
            PortGraphScc scc,
            GainGraphIndex index,
            Map<Integer, Double> effectiveGainsByEdgeId,
            double targetRho
    ) {
        double fullScaleRho = estimateSccAtScale(scc, index, effectiveGainsByEdgeId, 1.0D);

        if (fullScaleRho < targetRho) {
            return 1.0D;
        }

        double passiveRho = estimateSccAtScale(scc, index, effectiveGainsByEdgeId, 0.0D);

        if (passiveRho >= targetRho) {
            return 0.0D;
        }

        double low = 0.0D;
        double high = 1.0D;

        for (int iteration = 0; iteration < 32; iteration++) {
            double mid = (low + high) * 0.5D;
            double rho = estimateSccAtScale(scc, index, effectiveGainsByEdgeId, mid);

            if (!Double.isFinite(rho) || rho >= targetRho) {
                high = mid;
            } else {
                low = mid;
            }
        }

        return Math.max(0.0D, Math.min(1.0D, low));
    }

    private double estimateSccAtScale(
            PortGraphScc scc,
            GainGraphIndex index,
            Map<Integer, Double> effectiveGainsByEdgeId,
            double scale
    ) {
        return boundEstimator.estimateScc(
                scc,
                index,
                edge -> scaledEffectiveGain(effectiveGainsByEdgeId.getOrDefault(edge.id(), 1.0D), scale)
        );
    }

    private static double scaledEffectiveGain(double effectiveGain, double scale) {
        if (!Double.isFinite(effectiveGain) || effectiveGain <= 1.0D + GainSourceCollector.MIN_GAIN_DELTA) {
            return 1.0D;
        }

        return 1.0D + Math.max(0.0D, effectiveGain - 1.0D) * Math.max(0.0D, Math.min(1.0D, scale));
    }

    private static Map<Integer, Double> scaledExtraLinearGains(
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

            scaledGains.put(entry.getKey(), 1.0D + (gain - 1.0D) * scale);
        }

        return scaledGains;
    }

}
