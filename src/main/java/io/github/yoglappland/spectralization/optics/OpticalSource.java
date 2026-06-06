package io.github.yoglappland.spectralization.optics;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface OpticalSource {
    List<OutputBeam> getOutputBeams(BlockState state, Level level, BlockPos pos);
}
