package io.github.yoglappland.spectralization.heat;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometrySample;
import java.util.Map;
import java.util.Objects;

public final class PhotothermalCouplingModel {
    public static PhotothermalCouplingResult calculate(
            PhotothermalReadoutSample input,
            PhotothermalAbsorberProfile profile
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(profile, "profile");

        if (!input.reliable() || input.power() <= 0.0) {
            return PhotothermalCouplingResult.zero();
        }

        BeamGeometrySample geometry = BeamGeometryOps.sample(input.envelope(), input.power());
        double spectralEfficiency = spectralEfficiency(input, profile);
        double radiusEfficiency = radiusEfficiency(input.envelope().radius(), profile);
        double uniformityEfficiency = uniformityEfficiency(input, geometry, profile);
        double totalEfficiency = clamp01(spectralEfficiency
                * profile.heatConversionEfficiency()
                * radiusEfficiency
                * uniformityEfficiency);
        double absorbedOpticalPower = input.power() * spectralEfficiency * radiusEfficiency;
        double heatPower = input.power() * totalEfficiency;

        return new PhotothermalCouplingResult(
                input.power(),
                absorbedOpticalPower,
                heatPower,
                spectralEfficiency,
                radiusEfficiency,
                uniformityEfficiency,
                totalEfficiency,
                input.envelope().radius(),
                geometry.irradiance(),
                state(input.envelope().radius(), radiusEfficiency, uniformityEfficiency, profile)
        );
    }

    private static double spectralEfficiency(PhotothermalReadoutSample input, PhotothermalAbsorberProfile profile) {
        if (input.powerByFrequency().isEmpty()) {
            return clamp01(profile.absorptionCurve().absorption(FrequencyKey.DEBUG_VISIBLE));
        }

        double weightedAbsorption = 0.0;
        double totalPower = 0.0;

        for (Map.Entry<FrequencyKey, Double> entry : input.powerByFrequency().entrySet()) {
            double power = entry.getValue();

            if (power <= 0.0) {
                continue;
            }

            totalPower += power;
            weightedAbsorption += power * profile.absorptionCurve().absorption(entry.getKey());
        }

        if (totalPower <= 0.0) {
            return 0.0;
        }

        return clamp01(weightedAbsorption / totalPower);
    }

    private static double radiusEfficiency(double radius, PhotothermalAbsorberProfile profile) {
        if (radius <= 0.0) {
            return 0.0;
        }

        if (radius < profile.minFullEfficiencyRadius()) {
            return clamp01(radius / profile.minFullEfficiencyRadius());
        }

        if (radius <= profile.maxFullEfficiencyRadius()) {
            return 1.0;
        }

        if (radius >= profile.cutoffRadius()) {
            return 0.0;
        }

        return clamp01((profile.cutoffRadius() - radius)
                / (profile.cutoffRadius() - profile.maxFullEfficiencyRadius()));
    }

    private static double uniformityEfficiency(
            PhotothermalReadoutSample input,
            BeamGeometrySample geometry,
            PhotothermalAbsorberProfile profile
    ) {
        if (geometry.irradiance() <= profile.maxUniformIrradiance()) {
            return 1.0;
        }

        double coherentFraction = input.power() <= 0.0 ? 0.0 : clamp01(input.coherentPower() / input.power());
        double overdrive = geometry.irradiance() / profile.maxUniformIrradiance() - 1.0;
        return clamp01(1.0 / (1.0 + coherentFraction * profile.coherentHotspotPenalty() * overdrive));
    }

    private static PhotothermalCouplingState state(
            double radius,
            double radiusEfficiency,
            double uniformityEfficiency,
            PhotothermalAbsorberProfile profile
    ) {
        if (uniformityEfficiency < 0.999) {
            return PhotothermalCouplingState.HOTSPOT_LIMITED;
        }

        if (radius < profile.minFullEfficiencyRadius()) {
            return PhotothermalCouplingState.TOO_FOCUSED;
        }

        if (radius <= profile.maxFullEfficiencyRadius()) {
            return PhotothermalCouplingState.MATCHED;
        }

        return radiusEfficiency <= 0.0
                ? PhotothermalCouplingState.OUTSIDE_ABSORBER
                : PhotothermalCouplingState.SPILLING;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }

    private PhotothermalCouplingModel() {
    }
}
