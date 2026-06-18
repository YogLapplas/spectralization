package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.optics.source.BuiltInOpticalSourceProfiles;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class IntrinsicOpticalSources {
    public static boolean isSource(BlockState state) {
        return state.getBlock() instanceof OpticalSource || isBuiltInSource(state);
    }

    public static boolean isBuiltInSource(BlockState state) {
        return BuiltInOpticalSourceProfiles.hasProfile(state);
    }

    public static List<OutputBeam> outputBeams(BlockState state, Level level, BlockPos pos) {
        if (state.getBlock() instanceof OpticalSource source) {
            return source.getOutputBeams(state, level, pos);
        }

        return builtInOutputBeams(state, level, pos);
    }

    public static List<OutputBeam> builtInOutputBeams(BlockState state) {
        return builtInOutputBeams(state, null, BlockPos.ZERO);
    }

    public static List<OutputBeam> builtInOutputBeams(BlockState state, Level level, BlockPos pos) {
        return BuiltInOpticalSourceProfiles.outputBeams(state, level, pos);
    }

    private IntrinsicOpticalSources() {
    }
}
