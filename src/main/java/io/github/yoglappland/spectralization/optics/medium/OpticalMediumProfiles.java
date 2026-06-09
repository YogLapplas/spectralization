package io.github.yoglappland.spectralization.optics.medium;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalMediumProfiles {
    public static final OpticalMediumProfile AIR = OpticalMediumProfile.constant(
            OpticalMediumResponse.nonMagnetic(1.0, 0.0)
    );
    public static final OpticalMediumProfile DEFAULT_SOLID = OpticalMediumProfile.constant(
            OpticalMediumResponse.nonMagnetic(1.50, 0.02)
    );
    public static final OpticalMediumProfile GLASS = OpticalMediumProfile.constant(
            OpticalMediumResponse.nonMagnetic(1.50, 0.005)
    );
    public static final OpticalMediumProfile WATER = OpticalMediumProfile.constant(
            OpticalMediumResponse.nonMagnetic(1.33, 0.02)
    );
    public static final OpticalMediumProfile RUBY = OpticalMediumProfile.constant(
            OpticalMediumResponse.nonMagnetic(1.76, 0.03)
    );
    public static final OpticalMediumProfile METAL = OpticalMediumProfile.constant(
            new OpticalMediumResponse(0.20, 0.20, 0.75)
    );

    private static final Set<Block> GLASS_LIKE_BLOCKS = Set.of(
            Blocks.GLASS,
            Blocks.GLASS_PANE,
            Blocks.TINTED_GLASS,
            Blocks.WHITE_STAINED_GLASS,
            Blocks.WHITE_STAINED_GLASS_PANE,
            Blocks.ORANGE_STAINED_GLASS,
            Blocks.ORANGE_STAINED_GLASS_PANE,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS_PANE,
            Blocks.LIGHT_BLUE_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
            Blocks.YELLOW_STAINED_GLASS,
            Blocks.YELLOW_STAINED_GLASS_PANE,
            Blocks.LIME_STAINED_GLASS,
            Blocks.LIME_STAINED_GLASS_PANE,
            Blocks.PINK_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS_PANE,
            Blocks.GRAY_STAINED_GLASS,
            Blocks.GRAY_STAINED_GLASS_PANE,
            Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.LIGHT_GRAY_STAINED_GLASS_PANE,
            Blocks.CYAN_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS_PANE,
            Blocks.PURPLE_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS_PANE,
            Blocks.BLUE_STAINED_GLASS,
            Blocks.BLUE_STAINED_GLASS_PANE,
            Blocks.BROWN_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS_PANE,
            Blocks.GREEN_STAINED_GLASS,
            Blocks.GREEN_STAINED_GLASS_PANE,
            Blocks.RED_STAINED_GLASS,
            Blocks.RED_STAINED_GLASS_PANE,
            Blocks.BLACK_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS_PANE
    );
    private static final Set<Block> METAL_LIKE_BLOCKS = Set.of(
            Blocks.GOLD_BLOCK,
            Blocks.IRON_BLOCK,
            Blocks.COPPER_BLOCK,
            Blocks.EXPOSED_COPPER,
            Blocks.WEATHERED_COPPER,
            Blocks.OXIDIZED_COPPER,
            Blocks.WAXED_COPPER_BLOCK,
            Blocks.WAXED_EXPOSED_COPPER,
            Blocks.WAXED_WEATHERED_COPPER,
            Blocks.WAXED_OXIDIZED_COPPER
    );

    public static OpticalMediumProfile profileFor(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (OpticalMaterialProfiles.isAirLike(state)) {
            return AIR;
        }

        if (state.getFluidState().is(FluidTags.WATER)) {
            return WATER;
        }

        if (GLASS_LIKE_BLOCKS.contains(block)) {
            return GLASS;
        }

        if (block == Spectralization.RUBY_BLOCK.get()) {
            return RUBY;
        }

        if (block == Spectralization.SILVER_BLOCK.get() || METAL_LIKE_BLOCKS.contains(block)) {
            return METAL;
        }

        return DEFAULT_SOLID;
    }

    private OpticalMediumProfiles() {
    }
}
