package io.github.yoglappland.spectralization.compact;

import net.minecraft.core.BlockPos;

record CompactMachineConnectionKey(BlockPos first, BlockPos second) {
    static CompactMachineConnectionKey of(BlockPos left, BlockPos right) {
        return compare(left, right) <= 0
                ? new CompactMachineConnectionKey(left, right)
                : new CompactMachineConnectionKey(right, left);
    }

    CompactMachineConnectionKey {
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
