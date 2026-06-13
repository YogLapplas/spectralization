package io.github.yoglappland.spectralization.optics.fiber;

import java.util.Objects;
import net.minecraft.core.BlockPos;

public record FiberSegment(
        BlockPos from,
        BlockPos to,
        double length
) {
    public FiberSegment {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        from = from.immutable();
        to = to.immutable();

        if (from.equals(to)) {
            throw new IllegalArgumentException("Fiber segment endpoints must differ");
        }

        if (!Double.isFinite(length) || length <= 0.0) {
            throw new IllegalArgumentException("Fiber segment length must be finite and positive");
        }
    }

    public static FiberSegment between(BlockPos from, BlockPos to) {
        return new FiberSegment(from, to, FiberDistances.segmentLength(from, to));
    }
}
