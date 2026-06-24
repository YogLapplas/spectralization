package io.github.yoglappland.spectralization.optics.compiler.gain;

record GainSource(
        int edgeId,
        int sccId,
        double baseGain,
        GainSpectralScope spectralScope,
        double materialWeight,
        double graphWeight,
        double totalWeight,
        double modeWeight
) {
    GainSource(
            int edgeId,
            int sccId,
            double baseGain,
            GainSpectralScope spectralScope,
            double materialWeight,
            double graphWeight,
            double totalWeight
    ) {
        this(edgeId, sccId, baseGain, spectralScope, materialWeight, graphWeight, totalWeight, 1.0);
    }

    static GainSource passive() {
        return new GainSource(-1, -1, 1.0, GainSpectralScope.SAMPLE_FREQUENCY, 0.0, 0.0, 0.0, 0.0);
    }

    GainSource withWeights(double graphWeight, double totalWeight) {
        return new GainSource(edgeId, sccId, baseGain, spectralScope, materialWeight, graphWeight, totalWeight, modeWeight);
    }

    GainSource withModeWeight(double modeWeight) {
        return new GainSource(edgeId, sccId, baseGain, spectralScope, materialWeight, graphWeight, totalWeight, modeWeight);
    }
}
