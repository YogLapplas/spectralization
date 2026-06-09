package io.github.yoglappland.spectralization.optics.surface;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfile;
import io.github.yoglappland.spectralization.optics.OpticalMaterialResponse;
import io.github.yoglappland.spectralization.optics.medium.OpticalMediumProfiles;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public final class SurfaceTreatments {
    private static final OpticalMaterialProfile SILVERING_PROFILE = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.0, 0.96, 0.02)
    );
    private static final OpticalMaterialProfile GOLDING_PROFILE = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.0, 0.88, 0.04)
    );

    public static SurfaceProfile profileFor(SurfaceTreatmentKind kind) {
        Objects.requireNonNull(kind, "kind");

        return switch (kind) {
            case SILVERING -> coating("silvering", kind, SILVERING_PROFILE);
            case GOLDING -> coating("golding", kind, GOLDING_PROFILE);
            default -> throw new IllegalArgumentException("No coating profile for treatment kind: " + kind);
        };
    }

    private static SurfaceProfile coating(
            String path,
            SurfaceTreatmentKind kind,
            OpticalMaterialProfile materialProfile
    ) {
        return new SurfaceProfile(
                materialProfile,
                OpticalMediumProfiles.METAL,
                SurfacePreparation.RAW,
                ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, path),
                kind,
                SurfacePersistence.WORLD_STATE_ONLY,
                OpticalInterfaceProfile.MATCHED
        );
    }

    private SurfaceTreatments() {
    }
}
