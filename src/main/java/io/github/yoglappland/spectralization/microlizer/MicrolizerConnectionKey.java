package io.github.yoglappland.spectralization.microlizer;

import net.minecraft.core.BlockPos;

record MicrolizerConnectionKey(BlockPos first, BlockPos second) {
    static MicrolizerConnectionKey of(BlockPos left, BlockPos right) {
        return compare(left, right) <= 0
                ? new MicrolizerConnectionKey(left, right)
                : new MicrolizerConnectionKey(right, left);
    }

    MicrolizerConnectionKey {
        first = first.immutable();
        second = second.immutable();
    }

    private static int compare(BlockPos left, BlockPos right) {
        int dx = Integer.compare(left.getX(), right.getX());
        if (dx != 0) {
            return dx;
        }

        int dy = Integer.compare(left.getY(), right.getY());
        if (dy != 0) {
            return dy;
        }

        return Integer.compare(left.getZ(), right.getZ());
    }
}
