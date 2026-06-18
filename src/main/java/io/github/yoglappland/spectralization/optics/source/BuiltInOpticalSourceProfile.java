package io.github.yoglappland.spectralization.optics.source;

import io.github.yoglappland.spectralization.optics.OutputBeam;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface BuiltInOpticalSourceProfile {
    boolean matches(BlockState state);

    List<OutputBeam> outputBeams(BlockState state, Level level, BlockPos pos);
}
