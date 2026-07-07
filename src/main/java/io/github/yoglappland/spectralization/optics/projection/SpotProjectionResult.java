package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Objects;

public record SpotProjectionResult(
        List<SpotRecord> spots,
        LongSet dependencies,
        List<SpotProjectionAllocation> allocations
) {
    public static final SpotProjectionResult EMPTY =
            new SpotProjectionResult(List.of(), new LongOpenHashSet(), List.of());

    public SpotProjectionResult(List<SpotRecord> spots, LongSet dependencies) {
        this(spots, dependencies, List.of());
    }

    public SpotProjectionResult {
        Objects.requireNonNull(spots, "spots");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(allocations, "allocations");
        spots = List.copyOf(spots);
        dependencies = new LongOpenHashSet(dependencies);
        allocations = List.copyOf(allocations);
    }
}
