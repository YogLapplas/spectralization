package io.github.yoglappland.spectralization.optics.surface;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfile;
import io.github.yoglappland.spectralization.optics.OpticalMaterialResponse;
import io.github.yoglappland.spectralization.optics.OpticalMaterialSample;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.medium.OpticalMediumProfiles;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public final class SurfaceTreatments {
    private static final OpticalMaterialProfile SILVERING_PROFILE = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.0, 0.985, 0.010),
            sample(SpectralRegion.VISIBLE, 16, 0.0, 0.970, 0.020),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.0, 0.620, 0.300)
    );
    private static final OpticalMaterialProfile GOLDING_PROFILE = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.0, 0.960, 0.025),
            sample(SpectralRegion.VISIBLE, SpectralColorMap.VISIBLE_ORANGE_BIN, 0.0, 0.930, 0.050),
            sample(SpectralRegion.VISIBLE, SpectralColorMap.VISIBLE_PURPLE_BIN, 0.0, 0.420, 0.480),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.0, 0.320, 0.580)
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

    private static OpticalMaterialSample sample(
            SpectralRegion region,
            int bin,
            double transmittance,
            double reflectance,
            double absorption
    ) {
        return new OpticalMaterialSample(
                new FrequencyKey(region, bin),
                OpticalMaterialResponse.of(transmittance, reflectance, absorption)
        );
    }

    private SurfaceTreatments() {
    }
}
