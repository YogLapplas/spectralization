package io.github.yoglappland.spectralization.compact;

import net.minecraft.core.BlockPos;

public record CompactMachineFrameInfo(
        boolean present,
        boolean valid,
        BlockPos min,
        BlockPos max,
        int sizeX,
        int sizeY,
        int sizeZ,
        BlockPos workMin,
        BlockPos workMax,
        int workSizeX,
        int workSizeY,
        int workSizeZ,
        int connectionCount,
        int framePartCount,
        int compactBlockCount,
        int compactTypeCount,
        int ioPortCount,
        int payloadBlockCount,
        int payloadTypeCount,
        String reason
) {
    public static CompactMachineFrameInfo missing() {
        return new CompactMachineFrameInfo(
                false,
                false,
                BlockPos.ZERO,
                BlockPos.ZERO,
                0,
                0,
                0,
                BlockPos.ZERO,
                BlockPos.ZERO,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "missing"
        );
    }
}
