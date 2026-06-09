package io.github.yoglappland.spectralization.optics.surface;

import io.github.yoglappland.spectralization.optics.OpticalMaterialResponse;

public record EffectiveSurfaceResponse(
        OpticalMaterialResponse surfaceResponse,
        OpticalInterfaceResponse interfaceResponse
) {
    public EffectiveSurfaceResponse {
        if (surfaceResponse == null) {
            throw new NullPointerException("surfaceResponse");
        }

        if (interfaceResponse == null) {
            throw new NullPointerException("interfaceResponse");
        }
    }

    public double totalReflectance() {
        return combine(surfaceResponse.reflectance(), interfaceResponse.reflectance());
    }

    public double totalTransmittance() {
        return surfaceResponse.transmittance() * interfaceResponse.transmittance();
    }

    public double totalAbsorption() {
        double reflected = totalReflectance();
        double transmitted = totalTransmittance();
        return Math.max(0.0, 1.0 - reflected - transmitted);
    }

    public OpticalMaterialResponse asMaterialResponse() {
        return OpticalMaterialResponse.of(totalTransmittance(), totalReflectance(), totalAbsorption());
    }

    private static double combine(double firstReflectance, double secondReflectance) {
        return 1.0 - (1.0 - firstReflectance) * (1.0 - secondReflectance);
    }
}
