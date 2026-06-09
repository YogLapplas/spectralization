package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;

public interface SpatialProfileElement {
    default SpatialModeCoupling transformSpatialProfile(
            BeamEnvelope inputEnvelope,
            SpatialTransformContext context
    ) {
        return SpatialModeCoupling.ordered(inputEnvelope);
    }
}
