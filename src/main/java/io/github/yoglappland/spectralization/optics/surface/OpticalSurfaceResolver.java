package io.github.yoglappland.spectralization.optics.surface;

import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.medium.OpticalMediumProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalSurfaceResolver {
    public static SurfaceProfile surfaceFor(Level level, SurfaceKey key) {
        return surfaceFor(level, key.pos(), level.getBlockState(key.pos()), key.side());
    }

    public static SurfaceProfile surfaceFor(Level level, BlockPos pos, BlockState state, Direction side) {
        if (level != null && pos != null) {
            SurfaceKey key = new SurfaceKey(pos, side);
            java.util.Optional<SurfaceProfile> coatedProfile = SurfaceCoatingData.profileFor(level, key);

            if (coatedProfile.isPresent()) {
                return coatedProfile.get();
            }
        }

        Block block = state.getBlock();

        if (block instanceof OpticalSurfaceProvider provider) {
            return provider.surfaceAt(state, level, pos, side);
        }

        return SurfaceProfile.rawMaterial(
                OpticalMaterialProfiles.profileFor(level, pos, state),
                OpticalMediumProfiles.profileFor(level, pos, state)
        );
    }

    public static SurfaceApplicability applicabilityFor(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side,
            SurfaceTreatment treatment
    ) {
        Block block = state.getBlock();

        if (block instanceof OpticalSurfaceProvider provider) {
            return provider.surfaceApplicability(state, level, pos, side, treatment);
        }

        if (state.is(OpticalSurfaceTags.SURFACE_LOCKED)) {
            return SurfaceApplicability.REJECTED;
        }

        boolean worldCoatable = state.is(OpticalSurfaceTags.WORLD_COATABLE);
        boolean itemCoatable = state.is(OpticalSurfaceTags.ITEM_COATABLE);

        if (worldCoatable && itemCoatable) {
            return SurfaceApplicability.WORLD_AND_ITEM;
        }

        if (worldCoatable) {
            return SurfaceApplicability.WORLD_ONLY;
        }

        return itemCoatable ? SurfaceApplicability.ITEM_ONLY : SurfaceApplicability.REJECTED;
    }

    private OpticalSurfaceResolver() {
    }
}
