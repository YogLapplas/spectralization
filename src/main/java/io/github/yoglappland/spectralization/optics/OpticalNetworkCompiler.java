package io.github.yoglappland.spectralization.optics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalNetworkCompiler {
    public static CompiledOpticalNetwork compile(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (block instanceof OpticalElement opticalElement) {
            return opticalElement.compileOpticalNetwork(state, level, pos);
        }

        return OpticalBlockProperties.compile(level, pos, state);
    }

    private OpticalNetworkCompiler() {
    }
}
