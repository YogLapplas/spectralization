package io.github.yoglappland.spectralization.optics.surface;

import java.util.Optional;

public interface SurfaceProfileSource {
    Optional<SurfaceProfile> profileFor(SurfaceKey key);
}
