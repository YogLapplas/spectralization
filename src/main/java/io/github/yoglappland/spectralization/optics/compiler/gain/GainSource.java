package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;

record GainSource(
        int edgeId,
        int sccId,
        BlockPos pos,
        String materialId,
        double baseGain,
        double saturatedExtraOutput,
        GainSpectralScope spectralScope,
        double materialWeight,
        Map<FrequencyKey, Double> baseGainByFrequency,
        Map<FrequencyKey, Double> saturatedExtraOutputByFrequency
) {
    GainSource {
        pos = pos == null ? BlockPos.ZERO : pos.immutable();
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(spectralScope, "spectralScope");
        Objects.requireNonNull(baseGainByFrequency, "baseGainByFrequency");
        Objects.requireNonNull(saturatedExtraOutputByFrequency, "saturatedExtraOutputByFrequency");
        baseGainByFrequency = Map.copyOf(baseGainByFrequency);
        saturatedExtraOutputByFrequency = Map.copyOf(saturatedExtraOutputByFrequency);
    }

    double baseGainFor(FrequencyKey frequency) {
        Objects.requireNonNull(frequency, "frequency");
        Double gain = baseGainByFrequency.get(frequency);

        if (gain != null) {
            return gain;
        }

        return spectralScope == GainSpectralScope.ALL_PRESENT_FREQUENCIES ? baseGain : 1.0D;
    }

    double saturatedExtraOutputFor(FrequencyKey frequency) {
        Objects.requireNonNull(frequency, "frequency");
        Double extraOutput = saturatedExtraOutputByFrequency.get(frequency);

        if (extraOutput != null) {
            return extraOutput;
        }

        return spectralScope == GainSpectralScope.ALL_PRESENT_FREQUENCIES ? saturatedExtraOutput : 0.0D;
    }

    static GainSource passive() {
        return new GainSource(
                -1,
                -1,
                BlockPos.ZERO,
                "passive",
                1.0D,
                0.0D,
                GainSpectralScope.SAMPLE_FREQUENCY,
                0.0D,
                Map.of(),
                Map.of()
        );
    }
}
