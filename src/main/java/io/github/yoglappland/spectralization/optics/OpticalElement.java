package io.github.yoglappland.spectralization.optics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface OpticalElement {
    default CompiledOpticalNetwork compileOpticalNetwork(
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return CompiledOpticalNetwork.legacy(this, state, level, pos);
    }

    OpticalResult interact(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    );
}
