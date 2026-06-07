package io.github.yoglappland.spectralization.optics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SpotRecord(
        BlockPos pos,
        Direction face,
        int brightnessLevel,
        int radiusLevel,
        int colorBin
) {
    public SpotRecord {
        if (brightnessLevel < 0 || brightnessLevel > 7) {
            throw new IllegalArgumentException("Spot brightness level must be between 0 and 7");
        }

        if (radiusLevel < 0 || radiusLevel > 7) {
            throw new IllegalArgumentException("Spot radius level must be between 0 and 7");
        }

        if (colorBin < 0 || colorBin > 63) {
            throw new IllegalArgumentException("Spot color bin must be between 0 and 63");
        }
    }
}
