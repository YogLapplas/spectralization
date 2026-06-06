package io.github.yoglappland.spectralization.optics;

import net.minecraft.world.level.block.state.BlockState;

public final class OpticalBlockProperties {
    public static OpticalTransmission transmissionFor(BlockState state, BeamPacket beam) {
        if (beam.isEmpty()) {
            return OpticalTransmission.BLOCKED;
        }

        if (state.isAir()) {
            return OpticalTransmission.FULL;
        }

        return OpticalTransmission.BLOCKED;
    }

    private OpticalBlockProperties() {
    }
}
