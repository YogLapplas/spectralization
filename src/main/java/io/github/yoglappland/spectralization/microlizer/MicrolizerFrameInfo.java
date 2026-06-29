package io.github.yoglappland.spectralization.microlizer;

import net.minecraft.core.BlockPos;

public record MicrolizerFrameInfo(
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
        int microlizerBlockCount,
        int microlizerTypeCount,
        int ioPortCount,
        int payloadBlockCount,
        int payloadTypeCount,
        String reason
) {
    public static MicrolizerFrameInfo missing() {
        return new MicrolizerFrameInfo(
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
