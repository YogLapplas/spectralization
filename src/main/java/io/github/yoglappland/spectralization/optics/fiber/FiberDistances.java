package io.github.yoglappland.spectralization.optics.fiber;

import net.minecraft.core.BlockPos;

public final class FiberDistances {
    public static double segmentLength(BlockPos from, BlockPos to) {
        long dx = (long) to.getX() - from.getX();
        long dy = (long) to.getY() - from.getY();
        long dz = (long) to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static int materialLength(double length) {
        if (!Double.isFinite(length) || length <= 0.0) {
            return 0;
        }

        return (int) Math.ceil(length);
    }

    private FiberDistances() {
    }
}
