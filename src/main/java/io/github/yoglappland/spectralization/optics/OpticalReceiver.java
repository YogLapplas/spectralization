package io.github.yoglappland.spectralization.optics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface OpticalReceiver extends OpticalElement {
    @Override
    default OpticalResult interact(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return receiveBeam(input, incomingDirection, state, level, pos);
    }

    OpticalResult receiveBeam(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    );
}
