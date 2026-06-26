package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.optics.fiber.FiberMaterialProfile;
import io.github.yoglappland.spectralization.optics.lens.LensMaterial;

public record FiberDrawingParameters(FiberMaterialProfile profile) {
    public static FiberDrawingParameters empty() {
        return new FiberDrawingParameters(null);
    }

    public static FiberDrawingParameters from(LensMaterial material, boolean singleMode) {
        return FiberMaterialProfile.fromMaterial(material, singleMode)
                .map(FiberDrawingParameters::new)
                .orElseGet(FiberDrawingParameters::empty);
    }

    public int[] values() {
        if (profile == null) {
            return new int[]{0, 0, 0};
        }

        return new int[]{profile.coreScore(), profile.capacityScore(), profile.lossScore()};
    }

    public String apertureText() {
        return profile == null ? "-" : profile.coreDiameterText();
    }

    public String maxCapacityText() {
        return profile == null ? "-" : profile.maxPowerText();
    }

    public String lossText() {
        return profile == null ? "-" : profile.portLossText();
    }
}
