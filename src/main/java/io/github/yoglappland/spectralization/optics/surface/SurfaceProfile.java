package io.github.yoglappland.spectralization.optics.surface;

import io.github.yoglappland.spectralization.optics.OpticalMaterialProfile;
import io.github.yoglappland.spectralization.optics.medium.OpticalMediumProfile;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record SurfaceProfile(
        OpticalMaterialProfile materialProfile,
        OpticalMediumProfile mediumProfile,
        SurfacePreparation preparation,
        ResourceLocation treatmentId,
        SurfaceTreatmentKind treatmentKind,
        SurfacePersistence persistence,
        OpticalInterfaceProfile interfaceProfile
) {
    public SurfaceProfile {
        Objects.requireNonNull(materialProfile, "materialProfile");
        Objects.requireNonNull(mediumProfile, "mediumProfile");
        Objects.requireNonNull(preparation, "preparation");
        Objects.requireNonNull(treatmentKind, "treatmentKind");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(interfaceProfile, "interfaceProfile");
    }

    public static SurfaceProfile rawMaterial(OpticalMaterialProfile materialProfile) {
        return rawMaterial(materialProfile, io.github.yoglappland.spectralization.optics.medium.OpticalMediumProfiles.DEFAULT_SOLID);
    }

    public static SurfaceProfile rawMaterial(
            OpticalMaterialProfile materialProfile,
            OpticalMediumProfile mediumProfile
    ) {
        return new SurfaceProfile(
                materialProfile,
                mediumProfile,
                SurfacePreparation.RAW,
                null,
                SurfaceTreatmentKind.NONE,
                SurfacePersistence.WORLD_STATE_ONLY,
                OpticalInterfaceProfile.MATCHED
        );
    }

    public SurfaceProfile withInterfaceProfile(OpticalInterfaceProfile interfaceProfile) {
        return new SurfaceProfile(
                materialProfile,
                mediumProfile,
                preparation,
                treatmentId,
                treatmentKind,
                persistence,
                interfaceProfile
        );
    }

    public SurfaceProfile withMaterialProfile(OpticalMaterialProfile materialProfile) {
        return new SurfaceProfile(
                materialProfile,
                mediumProfile,
                preparation,
                treatmentId,
                treatmentKind,
                persistence,
                interfaceProfile
        );
    }

    public SurfaceProfile withMediumProfile(OpticalMediumProfile mediumProfile) {
        return new SurfaceProfile(
                materialProfile,
                mediumProfile,
                preparation,
                treatmentId,
                treatmentKind,
                persistence,
                interfaceProfile
        );
    }
}
