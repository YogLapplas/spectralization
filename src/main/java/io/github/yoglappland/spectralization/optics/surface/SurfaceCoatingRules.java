package io.github.yoglappland.spectralization.optics.surface;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SurfaceCoatingRules {
    public static boolean canApplyWorldCoating(Level level, SurfaceKey key) {
        BlockState state = level.getBlockState(key.pos());
        return state.is(OpticalSurfaceTags.WORLD_COATABLE);
    }

    private SurfaceCoatingRules() {
    }
}
