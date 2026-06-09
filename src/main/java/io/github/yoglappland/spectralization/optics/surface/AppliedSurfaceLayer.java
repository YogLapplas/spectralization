package io.github.yoglappland.spectralization.optics.surface;

import java.util.Objects;

public record AppliedSurfaceLayer(SurfaceKey key, SurfaceProfile profile) {
    public AppliedSurfaceLayer {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(profile, "profile");
    }
}
