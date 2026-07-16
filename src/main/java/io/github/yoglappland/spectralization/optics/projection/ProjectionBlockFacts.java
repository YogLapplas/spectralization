package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.OpticalMaterialProfile;
import java.util.List;
import java.util.Objects;

/** Immutable Minecraft-free facts consumed by one projection job. */
public record ProjectionBlockFacts(
        boolean loaded,
        boolean airLike,
        List<ProjectionLocalBox> localOpticalBoxes,
        OpticalMaterialProfile materialProfile,
        String diagnosticState
) {
    public static final ProjectionBlockFacts UNLOADED =
            new ProjectionBlockFacts(false, false, List.of(), null, "unloaded");

    public ProjectionBlockFacts {
        localOpticalBoxes = List.copyOf(Objects.requireNonNull(localOpticalBoxes, "localOpticalBoxes"));
        diagnosticState = Objects.requireNonNullElse(diagnosticState, "unknown");
        if (!loaded && (!localOpticalBoxes.isEmpty() || materialProfile != null)) {
            throw new IllegalArgumentException("Unloaded projection facts cannot contain world-derived data");
        }
        if (!localOpticalBoxes.isEmpty() && materialProfile == null) {
            throw new IllegalArgumentException("Projectable block facts require an immutable material profile");
        }
    }

    public boolean projectable() {
        return loaded && !airLike && !localOpticalBoxes.isEmpty();
    }
}
