package io.github.yoglappland.spectralization.optics.fiber;

import net.minecraft.core.BlockPos;

public record FiberSegmentKey(long first, long second) {
    public FiberSegmentKey {
        if (first == second) {
            throw new IllegalArgumentException("Fiber segment key endpoints must differ");
        }
    }

    public static FiberSegmentKey of(BlockPos from, BlockPos to) {
        long a = from.asLong();
        long b = to.asLong();
        return a < b ? new FiberSegmentKey(a, b) : new FiberSegmentKey(b, a);
    }

    public BlockPos firstPos() {
        return BlockPos.of(first);
    }

    public BlockPos secondPos() {
        return BlockPos.of(second);
    }
}
