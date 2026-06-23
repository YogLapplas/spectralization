package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;

public interface SpatialProfileElement {
    default PhaseSpaceMap profilePhaseSpaceMap(SpatialTransformContext context) {
        return PhaseSpaceMap.IDENTITY;
    }

    default String profileTransitionSignature(SpatialTransformContext context) {
        return context.state().toString();
    }

    default SpatialModeCoupling transformSpatialProfile(
            BeamEnvelope inputEnvelope,
            SpatialTransformContext context
    ) {
        return SpatialModeCoupling.ordered(inputEnvelope);
    }

    default BeamProfileTransfer transformProfileState(
            BeamProfileKey inputProfile,
            SpatialTransformContext context
    ) {
        SpatialModeCoupling coupling = transformSpatialProfile(inputProfile.toEnvelope(), context);
        double gain = BeamGeometryOps.clamp01(coupling.orderedFraction());
        return BeamProfileTransfer.of(BeamProfileKey.fromEnvelope(coupling.orderedEnvelope()), gain);
    }
}
