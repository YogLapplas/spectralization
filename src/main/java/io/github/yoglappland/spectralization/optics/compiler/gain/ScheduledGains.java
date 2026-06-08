package io.github.yoglappland.spectralization.optics.compiler.gain;

import java.util.Map;

record ScheduledGains(
        Map<Integer, Double> effectiveGainsByEdgeId,
        double passiveRho,
        double totalGainHeadroom,
        double maxModeWeight,
        double maxSourceCap
) {
}
