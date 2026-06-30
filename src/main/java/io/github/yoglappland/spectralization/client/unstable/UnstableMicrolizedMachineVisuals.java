package io.github.yoglappland.spectralization.client.unstable;

import net.minecraft.core.BlockPos;

public final class UnstableMicrolizedMachineVisuals {
    public static int seed(BlockPos pos) {
        int hash = pos.getX() * 73428767;
        hash ^= pos.getY() * 912931;
        hash ^= pos.getZ() * 42317861;
        return hash;
    }

    public static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private UnstableMicrolizedMachineVisuals() {
    }
}
