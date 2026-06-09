package io.github.yoglappland.spectralization.optics.surface;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record SurfaceTreatment(
        ResourceLocation id,
        SurfaceTreatmentKind kind,
        SurfacePersistence persistence
) {
    public SurfaceTreatment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistence, "persistence");
    }
}
