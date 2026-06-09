package io.github.yoglappland.spectralization.optics.surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks with explicit face-level optical state can implement this instead of relying on the default material surface.
 */
public interface OpticalSurfaceProvider {
    SurfaceProfile surfaceAt(BlockState state, Level level, BlockPos pos, Direction side);

    default SurfaceApplicability surfaceApplicability(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction side,
            SurfaceTreatment treatment
    ) {
        return SurfaceApplicability.REJECTED;
    }
}
