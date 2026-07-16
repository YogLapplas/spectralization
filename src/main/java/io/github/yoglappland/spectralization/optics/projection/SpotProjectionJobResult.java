package io.github.yoglappland.spectralization.optics.projection;

import java.util.Objects;

/** Immutable worker output. Shared cache and publication remain main-thread responsibilities. */
public record SpotProjectionJobResult(
        SpotProjectionJob job,
        SpotProjectionResult result,
        long workerStartedNanos,
        long workerNanos,
        Throwable failure
) {
    public SpotProjectionJobResult {
        Objects.requireNonNull(job, "job");
        workerNanos = Math.max(0L, workerNanos);
        if ((result == null) == (failure == null)) {
            throw new IllegalArgumentException("Projection job result must contain exactly one result or failure");
        }
    }
}
