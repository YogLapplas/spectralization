package io.github.yoglappland.spectralization.optics.metasurface;

import java.util.Objects;

public record MetasurfaceDesignResult(
        MetasurfaceTarget target,
        MaterialBudget budget,
        MetasurfaceEnvelope envelope,
        boolean successful
) {
    public MetasurfaceDesignResult {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(envelope, "envelope");
    }
}
