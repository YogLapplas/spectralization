package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Objects;

public record OpticalLocalTopology(
        OpticalComponentTuple component,
        List<OpticalLocalScattering> scattering
) {
    public OpticalLocalTopology {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(scattering, "scattering");
        scattering = List.copyOf(scattering);
    }
}
