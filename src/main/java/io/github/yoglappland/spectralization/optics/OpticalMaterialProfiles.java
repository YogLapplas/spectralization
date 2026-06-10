package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.optics.pump.OpticalPumpSources;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalMaterialProfiles {
    private static final OpticalMaterialProfile AIR = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(1.0, 0.0, 0.0)
    );
    private static final OpticalMaterialProfile OPAQUE = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.0, 0.0, 1.0)
    );
    private static final OpticalMaterialProfile CLEAR_GLASS = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.96, 0.03, 0.005)
    );
    private static final OpticalMaterialProfile TINTED_GLASS = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.02, 0.05, 0.88)
    );
    private static final OpticalMaterialProfile SAND = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.03, 0.35, 0.55)
    );
    private static final OpticalMaterialProfile METAL = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.0, 0.78, 0.17)
    );
    private static final OpticalMaterialProfile POLISHED_SILVER = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.0, 0.96, 0.02)
    );
    private static final OpticalMaterialProfile RUBY = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 32, 0.58, 0.03, 0.26),
            sample(SpectralRegion.VISIBLE, 4, 0.88, 0.04, 0.04),
            sample(SpectralRegion.VISIBLE, 32, 0.35, 0.04, 0.50),
            sample(SpectralRegion.VISIBLE, 52, 0.18, 0.05, 0.65),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.05, 0.05, 0.80)
    );
    private static final OpticalMaterialProfile WATER = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 32, 0.45, 0.02, 0.45),
            sample(SpectralRegion.VISIBLE, 32, 0.88, 0.02, 0.08),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.35, 0.02, 0.55)
    );
    private static final double RUBY_COHERENT_POWER_BUDGET_PER_PUMP_RATE = 0.45;
    private static final double RUBY_COHERENT_SEED_SATURATION_POWER = 0.35;
    private static final double RUBY_SCHEDULED_GAIN_PER_PUMP_RATE = 0.35;
    private static final double RUBY_MAX_SCHEDULED_GAIN = 4.0;
    private static final double RUBY_GAIN_MATERIAL_WEIGHT = 2.0;

    private static final Set<Block> AIR_LIKE_BLOCKS = Set.of(
            Blocks.REDSTONE_WIRE,
            Blocks.RAIL,
            Blocks.POWERED_RAIL,
            Blocks.DETECTOR_RAIL,
            Blocks.ACTIVATOR_RAIL,
            Blocks.REPEATER,
            Blocks.COMPARATOR
    );
    private static final Set<Block> CLEAR_GLASS_BLOCKS = Set.of(
            Blocks.GLASS,
            Blocks.GLASS_PANE
    );
    private static final Set<Block> SAND_BLOCKS = Set.of(
            Blocks.SAND,
            Blocks.RED_SAND,
            Blocks.GRAVEL,
            Blocks.SUSPICIOUS_SAND,
            Blocks.SUSPICIOUS_GRAVEL
    );
    private static final Set<Block> METAL_BLOCKS = Set.of(
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
    private static final Map<Block, OpticalMaterialProfile> STAINED_GLASS_BLOCKS = Map.ofEntries(
            Map.entry(Blocks.WHITE_STAINED_GLASS, neutralGlass(0.86)),
            Map.entry(Blocks.WHITE_STAINED_GLASS_PANE, neutralGlass(0.86)),
            Map.entry(Blocks.ORANGE_STAINED_GLASS, coloredGlass(10)),
            Map.entry(Blocks.ORANGE_STAINED_GLASS_PANE, coloredGlass(10)),
            Map.entry(Blocks.MAGENTA_STAINED_GLASS, coloredGlass(58)),
            Map.entry(Blocks.MAGENTA_STAINED_GLASS_PANE, coloredGlass(58)),
            Map.entry(Blocks.LIGHT_BLUE_STAINED_GLASS, coloredGlass(40)),
            Map.entry(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, coloredGlass(40)),
            Map.entry(Blocks.YELLOW_STAINED_GLASS, coloredGlass(16)),
            Map.entry(Blocks.YELLOW_STAINED_GLASS_PANE, coloredGlass(16)),
            Map.entry(Blocks.LIME_STAINED_GLASS, coloredGlass(28)),
            Map.entry(Blocks.LIME_STAINED_GLASS_PANE, coloredGlass(28)),
            Map.entry(Blocks.PINK_STAINED_GLASS, coloredGlass(60)),
            Map.entry(Blocks.PINK_STAINED_GLASS_PANE, coloredGlass(60)),
            Map.entry(Blocks.GRAY_STAINED_GLASS, neutralGlass(0.34)),
            Map.entry(Blocks.GRAY_STAINED_GLASS_PANE, neutralGlass(0.34)),
            Map.entry(Blocks.LIGHT_GRAY_STAINED_GLASS, neutralGlass(0.58)),
            Map.entry(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, neutralGlass(0.58)),
            Map.entry(Blocks.CYAN_STAINED_GLASS, coloredGlass(36)),
            Map.entry(Blocks.CYAN_STAINED_GLASS_PANE, coloredGlass(36)),
            Map.entry(Blocks.PURPLE_STAINED_GLASS, coloredGlass(52)),
            Map.entry(Blocks.PURPLE_STAINED_GLASS_PANE, coloredGlass(52)),
            Map.entry(Blocks.BLUE_STAINED_GLASS, coloredGlass(46)),
            Map.entry(Blocks.BLUE_STAINED_GLASS_PANE, coloredGlass(46)),
            Map.entry(Blocks.BROWN_STAINED_GLASS, coloredGlass(8)),
            Map.entry(Blocks.BROWN_STAINED_GLASS_PANE, coloredGlass(8)),
            Map.entry(Blocks.GREEN_STAINED_GLASS, coloredGlass(26)),
            Map.entry(Blocks.GREEN_STAINED_GLASS_PANE, coloredGlass(26)),
            Map.entry(Blocks.RED_STAINED_GLASS, coloredGlass(4)),
            Map.entry(Blocks.RED_STAINED_GLASS_PANE, coloredGlass(4)),
            Map.entry(Blocks.BLACK_STAINED_GLASS, neutralGlass(0.08)),
            Map.entry(Blocks.BLACK_STAINED_GLASS_PANE, neutralGlass(0.08))
    );

    public static OpticalMaterialProfile profileFor(BlockState state) {
        Block block = state.getBlock();

        if (isAirLike(state)) {
            return AIR;
        }

        if (state.getFluidState().is(FluidTags.WATER)) {
            return WATER;
        }

        if (CLEAR_GLASS_BLOCKS.contains(block)) {
            return CLEAR_GLASS;
        }

        if (block == Blocks.TINTED_GLASS) {
            return TINTED_GLASS;
        }

        OpticalMaterialProfile stainedGlassProfile = STAINED_GLASS_BLOCKS.get(block);

        if (stainedGlassProfile != null) {
            return stainedGlassProfile;
        }

        if (SAND_BLOCKS.contains(block)) {
            return SAND;
        }

        if (METAL_BLOCKS.contains(block)) {
            return METAL;
        }

        if (block == Spectralization.SILVER_BLOCK.get()) {
            return POLISHED_SILVER;
        }

        if (block == Spectralization.RUBY_BLOCK.get()) {
            return RUBY;
        }

        return OPAQUE;
    }

    public static boolean isAirLike(BlockState state) {
        return state.isAir() || AIR_LIKE_BLOCKS.contains(state.getBlock());
    }

    public static boolean isExplicitOpticalMaterial(BlockState state) {
        Block block = state.getBlock();

        return state.getFluidState().is(FluidTags.WATER)
                || CLEAR_GLASS_BLOCKS.contains(block)
                || block == Blocks.TINTED_GLASS
                || STAINED_GLASS_BLOCKS.containsKey(block)
                || METAL_BLOCKS.contains(block)
                || block == Spectralization.SILVER_BLOCK.get()
                || block == Spectralization.RUBY_BLOCK.get()
                || block == Blocks.GLOWSTONE;
    }

    public static boolean isScatteringMarker(BlockState state) {
        return SAND_BLOCKS.contains(state.getBlock());
    }

    public static OpticalMaterialProfile profileFor(Level level, BlockPos pos, BlockState state) {
        return profileFor(state);
    }

    public static double gainFactorFor(Level level, BlockPos pos, BlockState state) {
        return gainFactorFor(level, pos, state, FrequencyKey.DEBUG_VISIBLE);
    }

    public static double gainFactorFor(Level level, BlockPos pos, BlockState state, FrequencyKey frequency) {
        return 1.0;
    }

    public static double scheduledCoherentBaseGainFor(Level level, BlockPos pos, BlockState state) {
        return scheduledCoherentBaseGainFor(level, pos, state, FrequencyKey.DEBUG_VISIBLE);
    }

    public static double scheduledCoherentBaseGainFor(Level level, BlockPos pos, BlockState state, FrequencyKey frequency) {
        if (state.getBlock() != Spectralization.RUBY_BLOCK.get()) {
            return 1.0;
        }

        int pumpRate = OpticalPumpSources.adjacentPumpRate(level, pos);

        if (pumpRate <= 0) {
            return 1.0;
        }

        double spectralCoupling = rubyEmissionCoupling(frequency);

        if (spectralCoupling <= 0.0) {
            return 1.0;
        }

        return Math.min(RUBY_MAX_SCHEDULED_GAIN, 1.0 + RUBY_SCHEDULED_GAIN_PER_PUMP_RATE * pumpRate * spectralCoupling);
    }

    public static double gainMaterialWeightFor(BlockState state) {
        if (state.getBlock() == Spectralization.RUBY_BLOCK.get()) {
            return RUBY_GAIN_MATERIAL_WEIGHT;
        }

        return 0.0;
    }

    public static double saturatedCoherentEmissionFor(Level level, BlockPos pos, BlockState state, double seedPower) {
        if (state.getBlock() != Spectralization.RUBY_BLOCK.get()) {
            return 0.0;
        }

        if (!Double.isFinite(seedPower) || seedPower <= 0.0) {
            return 0.0;
        }

        int pumpRate = OpticalPumpSources.adjacentPumpRate(level, pos);

        if (pumpRate <= 0) {
            return 0.0;
        }

        double spectralCoupling = rubyEmissionCoupling(RubyBlock.RUBY_LINE);
        double budget = RUBY_COHERENT_POWER_BUDGET_PER_PUMP_RATE * pumpRate * spectralCoupling;

        return budget * seedPower / (seedPower + RUBY_COHERENT_SEED_SATURATION_POWER);
    }

    private static double rubyEmissionCoupling(FrequencyKey frequency) {
        if (frequency.region() != RubyBlock.RUBY_LINE.region()) {
            return 0.0;
        }

        int distance = Math.abs(frequency.bin() - RubyBlock.RUBY_LINE.bin());

        if (distance >= 12) {
            return 0.0;
        }

        return Math.max(0.0, 1.0 - distance / 12.0);
    }

    private static OpticalMaterialProfile coloredGlass(int centerBin) {
        int lowBin = Math.max(0, centerBin - 18);
        int highBin = Math.min(SpectralRegion.VISIBLE.defaultBins() - 1, centerBin + 18);

        return OpticalMaterialProfile.of(
                new OpticalMaterialSample(FrequencyKey.visible(0), OpticalMaterialResponse.of(0.18, 0.03, 0.60)),
                new OpticalMaterialSample(FrequencyKey.visible(lowBin), OpticalMaterialResponse.of(0.32, 0.04, 0.48)),
                new OpticalMaterialSample(FrequencyKey.visible(centerBin), OpticalMaterialResponse.of(0.72, 0.12, 0.10)),
                new OpticalMaterialSample(FrequencyKey.visible(highBin), OpticalMaterialResponse.of(0.32, 0.04, 0.48)),
                new OpticalMaterialSample(
                        FrequencyKey.visible(SpectralRegion.VISIBLE.defaultBins() - 1),
                        OpticalMaterialResponse.of(0.18, 0.03, 0.60)
                )
        );
    }

    private static OpticalMaterialProfile neutralGlass(double transmittance) {
        double absorption = Math.max(0.0, 0.92 - transmittance);
        return OpticalMaterialProfile.constant(OpticalMaterialResponse.of(transmittance, 0.04, absorption));
    }

    private static OpticalMaterialSample sample(
            SpectralRegion region,
            int bin,
            double transmittance,
            double reflectance,
            double absorption
    ) {
        return new OpticalMaterialSample(
                new FrequencyKey(region, bin),
                OpticalMaterialResponse.of(transmittance, reflectance, absorption)
        );
    }

    private OpticalMaterialProfiles() {
    }
}
