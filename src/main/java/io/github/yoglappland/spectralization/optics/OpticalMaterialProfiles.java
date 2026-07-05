package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.blockentity.FiberLaserBlockEntity;
import io.github.yoglappland.spectralization.blockentity.SeedLightBlockEntity;
import io.github.yoglappland.spectralization.optics.pump.OpticalPumpSources;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
    private static final OpticalMaterialProfile CLEAR_GLASS = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.92, 0.025, 0.035),
            sample(SpectralRegion.VISIBLE, 16, 0.97, 0.020, 0.005),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.72, 0.035, 0.200)
    );
    private static final OpticalMaterialProfile QUARTZ_GLASS = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.965, 0.015, 0.010),
            sample(SpectralRegion.VISIBLE, 16, 0.985, 0.012, 0.003),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.900, 0.025, 0.050)
    );
    private static final OpticalMaterialProfile BOROSILICATE_GLASS = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.940, 0.020, 0.025),
            sample(SpectralRegion.VISIBLE, 16, 0.975, 0.015, 0.004),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.700, 0.035, 0.200)
    );
    private static final OpticalMaterialProfile CROWN_GLASS = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.910, 0.025, 0.050),
            sample(SpectralRegion.VISIBLE, 16, 0.980, 0.018, 0.002),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.620, 0.040, 0.280)
    );
    private static final OpticalMaterialProfile FLINT_GLASS = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.840, 0.050, 0.080),
            sample(SpectralRegion.VISIBLE, 16, 0.955, 0.040, 0.005),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.350, 0.060, 0.500)
    );
    private static final OpticalMaterialProfile HEAVY_GLASS = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.720, 0.080, 0.120),
            sample(SpectralRegion.VISIBLE, 16, 0.940, 0.050, 0.010),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.180, 0.090, 0.650)
    );
    private static final OpticalMaterialProfile TINTED_GLASS = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.02, 0.05, 0.88)
    );
    private static final OpticalMaterialProfile SAND = OpticalMaterialProfile.constant(
            OpticalMaterialResponse.of(0.03, 0.35, 0.55)
    );
    private static final OpticalMaterialProfile IRON = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.0, 0.72, 0.22),
            sample(SpectralRegion.VISIBLE, 16, 0.0, 0.62, 0.30),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.0, 0.35, 0.50)
    );
    private static final OpticalMaterialProfile COPPER = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.0, 0.89, 0.07),
            sample(SpectralRegion.VISIBLE, 4, 0.0, 0.80, 0.15),
            sample(SpectralRegion.VISIBLE, 26, 0.0, 0.45, 0.45),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.0, 0.24, 0.65)
    );
    private static final OpticalMaterialProfile GOLD = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.0, 0.96, 0.025),
            sample(SpectralRegion.VISIBLE, 4, 0.0, 0.42, 0.48),
            sample(SpectralRegion.VISIBLE, 26, 0.0, 0.93, 0.05),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.0, 0.32, 0.58)
    );
    private static final OpticalMaterialProfile POLISHED_SILVER = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.0, 0.985, 0.010),
            sample(SpectralRegion.VISIBLE, 16, 0.0, 0.970, 0.020),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.0, 0.620, 0.300)
    );
    private static final OpticalMaterialProfile RUTILE = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.18, 0.42, 0.32),
            sample(SpectralRegion.VISIBLE, 16, 0.08, 0.55, 0.30),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.02, 0.30, 0.60)
    );
    private static final OpticalMaterialProfile CORUNDUM = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.82, 0.04, 0.08),
            sample(SpectralRegion.VISIBLE, 16, 0.88, 0.035, 0.05),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.46, 0.04, 0.42)
    );
    private static final OpticalMaterialProfile FLUORITE = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.93, 0.025, 0.030),
            sample(SpectralRegion.VISIBLE, 16, 0.97, 0.020, 0.005),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.90, 0.025, 0.045)
    );
    private static final OpticalMaterialProfile YAG = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.900, 0.025, 0.035),
            sample(SpectralRegion.VISIBLE, 16, 0.965, 0.015, 0.006),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.550, 0.030, 0.350)
    );
    private static final FrequencyKey CE_LINE = FrequencyKey.visible(14);
    private static final FrequencyKey ND_LINE = FrequencyKey.visible(26);
    private static final FrequencyKey YB_LINE = FrequencyKey.visible(5);
    private static final FrequencyKey ER_LINE = new FrequencyKey(SpectralRegion.INFRARED, 18);
    private static final OpticalMaterialProfile CE_YAG_UNPUMPED = rareEarthYagProfile(CE_LINE, false);
    private static final OpticalMaterialProfile CE_YAG_PUMPED = rareEarthYagProfile(CE_LINE, true);
    private static final OpticalMaterialProfile ND_YAG_UNPUMPED = rareEarthYagProfile(ND_LINE, false);
    private static final OpticalMaterialProfile ND_YAG_PUMPED = rareEarthYagProfile(ND_LINE, true);
    private static final OpticalMaterialProfile YB_YAG_UNPUMPED = rareEarthYagProfile(YB_LINE, false);
    private static final OpticalMaterialProfile YB_YAG_PUMPED = rareEarthYagProfile(YB_LINE, true);
    private static final OpticalMaterialProfile ER_YAG_UNPUMPED = rareEarthYagProfile(ER_LINE, false);
    private static final OpticalMaterialProfile ER_YAG_PUMPED = rareEarthYagProfile(ER_LINE, true);
    private static final OpticalMaterialProfile CE_FLUORITE_UNPUMPED = rareEarthFluoriteProfile(CE_LINE, false);
    private static final OpticalMaterialProfile CE_FLUORITE_PUMPED = rareEarthFluoriteProfile(CE_LINE, true);
    private static final OpticalMaterialProfile ND_FLUORITE_UNPUMPED = rareEarthFluoriteProfile(ND_LINE, false);
    private static final OpticalMaterialProfile ND_FLUORITE_PUMPED = rareEarthFluoriteProfile(ND_LINE, true);
    private static final OpticalMaterialProfile YB_FLUORITE_UNPUMPED = rareEarthFluoriteProfile(YB_LINE, false);
    private static final OpticalMaterialProfile YB_FLUORITE_PUMPED = rareEarthFluoriteProfile(YB_LINE, true);
    private static final OpticalMaterialProfile ER_FLUORITE_UNPUMPED = rareEarthFluoriteProfile(ER_LINE, false);
    private static final OpticalMaterialProfile ER_FLUORITE_PUMPED = rareEarthFluoriteProfile(ER_LINE, true);
    private static final OpticalMaterialProfile RUBY_UNPUMPED = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.60, 0.03, 0.24),
            sample(SpectralRegion.VISIBLE, 2, 0.18, 0.04, 0.65),
            sample(SpectralRegion.VISIBLE, 16, 0.35, 0.04, 0.50),
            sample(SpectralRegion.VISIBLE, 26, 0.929, 0.031, 0.04),
            sample(SpectralRegion.VISIBLE, 31, 0.82, 0.05, 0.08),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.05, 0.05, 0.80)
    );
    private static final OpticalMaterialProfile RUBY_PUMPED = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.64, 0.03, 0.20),
            sample(SpectralRegion.VISIBLE, 2, 0.18, 0.04, 0.65),
            sample(SpectralRegion.VISIBLE, 16, 0.38, 0.04, 0.46),
            sample(SpectralRegion.VISIBLE, 26, 0.929, 0.031, 0.04),
            sample(SpectralRegion.VISIBLE, 31, 0.88, 0.04, 0.07),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.05, 0.05, 0.80)
    );
    private static final OpticalMaterialProfile WATER = OpticalMaterialProfile.of(
            sample(SpectralRegion.INFRARED, 16, 0.45, 0.02, 0.45),
            sample(SpectralRegion.VISIBLE, 16, 0.88, 0.02, 0.08),
            sample(SpectralRegion.ULTRAVIOLET, 16, 0.35, 0.02, 0.55)
    );
    private static final int RUBY_DOMAIN_MAX_BLOCKS = 512;
    private static final double RUBY_MAX_EFFECTIVE_PUMP = 4.0;
    private static final double RUBY_SINGLE_PASS_GAIN_PER_PU = 0.07;
    private static final double RUBY_MATERIAL_SATURATION_POWER = 120.0;
    private static final double RUBY_HANDLING_LIMIT = 320.0;
    private static final double RUBY_EXCITED_SEED_POWER_PER_EXCITER = 7.5;
    private static final double RUBY_GAIN_MATERIAL_WEIGHT = 2.0;
    private static final GainMediumSpec RUBY_GAIN_MEDIUM = new GainMediumSpec(
            RubyBlock.RUBY_LINE,
            RUBY_MAX_EFFECTIVE_PUMP,
            true,
            RUBY_SINGLE_PASS_GAIN_PER_PU,
            RUBY_MATERIAL_SATURATION_POWER,
            RUBY_HANDLING_LIMIT,
            RUBY_EXCITED_SEED_POWER_PER_EXCITER,
            1.0,
            1.0,
            1.0,
            1.0,
            1.0,
            1.0,
            RUBY_GAIN_MATERIAL_WEIGHT,
            12.0,
            RUBY_UNPUMPED,
            RUBY_PUMPED
    );
    private static final GainMediumSpec CE_YAG_GAIN_MEDIUM = new GainMediumSpec(
            CE_LINE, 6.0, false, 0.062, 820.0, 2600.0, 28.0, 1.30, 1.18, 1.05, 1.45, 1.18, 1.04, 2.3, 4.0,
            CE_YAG_UNPUMPED, CE_YAG_PUMPED
    );
    private static final GainMediumSpec ND_YAG_GAIN_MEDIUM = new GainMediumSpec(
            ND_LINE, 8.0, false, 0.053, 1550.0, 4200.0, 22.0, 1.18, 1.32, 1.12, 1.16, 1.35, 1.18, 2.6, 4.0,
            ND_YAG_UNPUMPED, ND_YAG_PUMPED
    );
    private static final GainMediumSpec YB_YAG_GAIN_MEDIUM = new GainMediumSpec(
            YB_LINE, 10.0, false, 0.044, 2700.0, 6400.0, 12.0, 0.96, 1.05, 1.16, 0.98, 1.04, 1.08, 2.8, 4.0,
            YB_YAG_UNPUMPED, YB_YAG_PUMPED
    );
    private static final GainMediumSpec ER_YAG_GAIN_MEDIUM = new GainMediumSpec(
            ER_LINE, 8.0, false, 0.046, 2100.0, 6200.0, 15.0, 1.10, 1.22, 1.36, 1.06, 1.22, 1.45, 2.1, 4.0,
            ER_YAG_UNPUMPED, ER_YAG_PUMPED
    );
    private static final GainMediumSpec CE_FLUORITE_GAIN_MEDIUM = new GainMediumSpec(
            CE_LINE, 6.0, false, 0.050, 800.0, 2800.0, 26.0, 1.35, 1.20, 1.06, 1.50, 1.22, 1.08, 1.8, 3.0,
            CE_FLUORITE_UNPUMPED, CE_FLUORITE_PUMPED
    );
    private static final GainMediumSpec ND_FLUORITE_GAIN_MEDIUM = new GainMediumSpec(
            ND_LINE, 8.0, false, 0.044, 1400.0, 3800.0, 20.0, 1.22, 1.34, 1.14, 1.22, 1.40, 1.20, 2.0, 3.0,
            ND_FLUORITE_UNPUMPED, ND_FLUORITE_PUMPED
    );
    private static final GainMediumSpec YB_FLUORITE_GAIN_MEDIUM = new GainMediumSpec(
            YB_LINE, 10.0, false, 0.038, 2300.0, 6100.0, 15.0, 1.08, 1.12, 1.12, 1.10, 1.16, 1.12, 2.1, 3.0,
            YB_FLUORITE_UNPUMPED, YB_FLUORITE_PUMPED
    );
    private static final GainMediumSpec ER_FLUORITE_GAIN_MEDIUM = new GainMediumSpec(
            ER_LINE, 8.0, false, 0.038, 1900.0, 5400.0, 13.0, 1.12, 1.25, 1.42, 1.08, 1.25, 1.52, 1.7, 3.0,
            ER_FLUORITE_UNPUMPED, ER_FLUORITE_PUMPED
    );

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
    private static final Set<Block> IRON_BLOCKS = Set.of(
            Blocks.IRON_BLOCK,
            Blocks.RAW_IRON_BLOCK
    );
    private static final Set<Block> COPPER_BLOCKS = Set.of(
            Blocks.COPPER_BLOCK,
            Blocks.RAW_COPPER_BLOCK,
            Blocks.EXPOSED_COPPER,
            Blocks.WEATHERED_COPPER,
            Blocks.OXIDIZED_COPPER,
            Blocks.WAXED_COPPER_BLOCK,
            Blocks.WAXED_EXPOSED_COPPER,
            Blocks.WAXED_WEATHERED_COPPER,
            Blocks.WAXED_OXIDIZED_COPPER
    );
    private static final Set<Block> GOLD_BLOCKS = Set.of(
            Blocks.GOLD_BLOCK,
            Blocks.RAW_GOLD_BLOCK
    );
    private static final Map<Block, OpticalMaterialProfile> STAINED_GLASS_BLOCKS = Map.ofEntries(
            Map.entry(Blocks.WHITE_STAINED_GLASS, neutralGlass(0.86)),
            Map.entry(Blocks.WHITE_STAINED_GLASS_PANE, neutralGlass(0.86)),
            Map.entry(Blocks.ORANGE_STAINED_GLASS, coloredGlass(5)),
            Map.entry(Blocks.ORANGE_STAINED_GLASS_PANE, coloredGlass(5)),
            Map.entry(Blocks.MAGENTA_STAINED_GLASS, coloredGlass(29)),
            Map.entry(Blocks.MAGENTA_STAINED_GLASS_PANE, coloredGlass(29)),
            Map.entry(Blocks.LIGHT_BLUE_STAINED_GLASS, coloredGlass(20)),
            Map.entry(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE, coloredGlass(20)),
            Map.entry(Blocks.YELLOW_STAINED_GLASS, coloredGlass(8)),
            Map.entry(Blocks.YELLOW_STAINED_GLASS_PANE, coloredGlass(8)),
            Map.entry(Blocks.LIME_STAINED_GLASS, coloredGlass(14)),
            Map.entry(Blocks.LIME_STAINED_GLASS_PANE, coloredGlass(14)),
            Map.entry(Blocks.PINK_STAINED_GLASS, coloredGlass(30)),
            Map.entry(Blocks.PINK_STAINED_GLASS_PANE, coloredGlass(30)),
            Map.entry(Blocks.GRAY_STAINED_GLASS, neutralGlass(0.34)),
            Map.entry(Blocks.GRAY_STAINED_GLASS_PANE, neutralGlass(0.34)),
            Map.entry(Blocks.LIGHT_GRAY_STAINED_GLASS, neutralGlass(0.58)),
            Map.entry(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE, neutralGlass(0.58)),
            Map.entry(Blocks.CYAN_STAINED_GLASS, coloredGlass(18)),
            Map.entry(Blocks.CYAN_STAINED_GLASS_PANE, coloredGlass(18)),
            Map.entry(Blocks.PURPLE_STAINED_GLASS, coloredGlass(26)),
            Map.entry(Blocks.PURPLE_STAINED_GLASS_PANE, coloredGlass(26)),
            Map.entry(Blocks.BLUE_STAINED_GLASS, coloredGlass(23)),
            Map.entry(Blocks.BLUE_STAINED_GLASS_PANE, coloredGlass(23)),
            Map.entry(Blocks.BROWN_STAINED_GLASS, coloredGlass(4)),
            Map.entry(Blocks.BROWN_STAINED_GLASS_PANE, coloredGlass(4)),
            Map.entry(Blocks.GREEN_STAINED_GLASS, coloredGlass(13)),
            Map.entry(Blocks.GREEN_STAINED_GLASS_PANE, coloredGlass(13)),
            Map.entry(Blocks.RED_STAINED_GLASS, coloredGlass(2)),
            Map.entry(Blocks.RED_STAINED_GLASS_PANE, coloredGlass(2)),
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

        if (block == Spectralization.HOLOGRAPHIC_STORAGE_SHELL.get()
                || block == Spectralization.STABLE_HOLOGRAPHIC_STORAGE_SHELL.get()
                || block == Spectralization.HOLOGRAPHIC_STORAGE_CRYSTAL.get()) {
            return AIR;
        }

        if (CLEAR_GLASS_BLOCKS.contains(block)) {
            return CLEAR_GLASS;
        }

        OpticalMaterialProfile spectralGlassProfile = spectralGlassProfile(block);

        if (spectralGlassProfile != null) {
            return spectralGlassProfile;
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

        if (IRON_BLOCKS.contains(block)) {
            return IRON;
        }

        if (COPPER_BLOCKS.contains(block)) {
            return COPPER;
        }

        if (GOLD_BLOCKS.contains(block)) {
            return GOLD;
        }

        if (block == Spectralization.SILVER_BLOCK.get()) {
            return POLISHED_SILVER;
        }

        if (block == Spectralization.RUBY_BLOCK.get()) {
            return RUBY_UNPUMPED;
        }

        if (block == Spectralization.RUTILE_BLOCK.get()) {
            return RUTILE;
        }

        if (block == Spectralization.CORUNDUM_BLOCK.get()) {
            return CORUNDUM;
        }

        if (block == Spectralization.FLUORITE_BLOCK.get()) {
            return FLUORITE;
        }

        if (block == Spectralization.YAG_CRYSTAL_BLOCK.get()) {
            return YAG;
        }

        GainMediumSpec spec = gainMediumSpec(state);

        if (spec != null) {
            return spec.unpumpedProfile();
        }

        return OPAQUE;
    }

    public static boolean isAirLike(BlockState state) {
        return state.isAir()
                || AIR_LIKE_BLOCKS.contains(state.getBlock())
                || state.getBlock() == Spectralization.FIBER_RELAY.get();
    }

    public static boolean isExplicitOpticalMaterial(BlockState state) {
        Block block = state.getBlock();

        return state.getFluidState().is(FluidTags.WATER)
                || CLEAR_GLASS_BLOCKS.contains(block)
                || spectralGlassProfile(block) != null
                || block == Blocks.TINTED_GLASS
                || STAINED_GLASS_BLOCKS.containsKey(block)
                || IRON_BLOCKS.contains(block)
                || COPPER_BLOCKS.contains(block)
                || GOLD_BLOCKS.contains(block)
                || block == Spectralization.SILVER_BLOCK.get()
                || block == Spectralization.RUBY_BLOCK.get()
                || block == Spectralization.RUTILE_BLOCK.get()
                || block == Spectralization.CORUNDUM_BLOCK.get()
                || block == Spectralization.FLUORITE_BLOCK.get()
                || block == Spectralization.YAG_CRYSTAL_BLOCK.get()
                || gainMediumSpec(state) != null
                || block == Spectralization.HOLOGRAPHIC_STORAGE_SHELL.get()
                || block == Spectralization.STABLE_HOLOGRAPHIC_STORAGE_SHELL.get()
                || block == Spectralization.HOLOGRAPHIC_STORAGE_CRYSTAL.get();
    }

    public static boolean isScatteringMarker(BlockState state) {
        return SAND_BLOCKS.contains(state.getBlock());
    }

    public static OpticalMaterialProfile profileFor(Level level, BlockPos pos, BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (spec != null
                && level != null
                && pos != null
                && gainMediumDomainPumpDensity(level, pos, state) > 0.0) {
            return spec.pumpedProfile();
        }

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

    public static double rubyMaximumEffectiveSinglePassGain() {
        return 1.0 + RUBY_SINGLE_PASS_GAIN_PER_PU * RUBY_MAX_EFFECTIVE_PUMP;
    }

    public static double rubyScheduledGainUpperLimit() {
        return rubyMaximumEffectiveSinglePassGain();
    }

    public static double maximumEffectiveSinglePassGainFor(BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return 1.0D;
        }

        return 1.0D + spec.gainPerPumpUnit() * spec.referencePump();
    }

    public static double maximumEffectivePumpFor(BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return 0.0D;
        }

        return spec.referencePump();
    }

    public static double gainPerPumpUnitFor(BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return Double.NaN;
        }

        return spec.gainPerPumpUnit();
    }

    public static double referencePumpFor(BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return Double.NaN;
        }

        return spec.referencePump();
    }

    public static double saturationPowerFor(BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return Double.NaN;
        }

        return spec.saturationPower();
    }

    public static double handlingLimitFor(BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return Double.NaN;
        }

        return spec.handlingLimit();
    }

    public static FrequencyKey gainMediumEmissionLine(BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return null;
        }

        return spec.emissionLine();
    }

    public static double rubyMaterialSaturationPower() {
        return RUBY_MATERIAL_SATURATION_POWER;
    }

    public static double rubyHandlingLimit() {
        return RUBY_HANDLING_LIMIT;
    }

    public static double scheduledCoherentBaseGainFor(Level level, BlockPos pos, BlockState state, FrequencyKey frequency) {
        if (state.is(Spectralization.FIBER_LASER.get())) {
            if (level != null
                    && pos != null
                    && level.getBlockEntity(pos) instanceof FiberLaserBlockEntity fiberLaser) {
                return fiberLaser.scheduledCoherentBaseGain();
            }

            return 1.0;
        }

        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return 1.0;
        }

        double pumpDensity = gainMediumDomainStats(level, pos, state).pumpDensity();

        if (pumpDensity <= 0.0) {
            return 1.0;
        }

        double spectralCoupling = emissionCoupling(spec, frequency);

        if (spectralCoupling <= 0.0) {
            return 1.0;
        }

        double effectivePump = spec.pumpLimited()
                ? Math.min(spec.referencePump(), Math.max(0.0, pumpDensity))
                : Math.max(0.0, pumpDensity);
        return 1.0 + spec.gainPerPumpUnit() * effectivePump * spectralCoupling;
    }

    public static double saturatedCoherentExtraOutputFor(Level level, BlockPos pos, BlockState state, FrequencyKey frequency) {
        if (state.is(Spectralization.FIBER_LASER.get())) {
            if (level != null
                    && pos != null
                    && level.getBlockEntity(pos) instanceof FiberLaserBlockEntity fiberLaser) {
                return fiberLaser.saturatedCoherentExtraOutput();
            }

            return 0.0;
        }

        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return 0.0;
        }

        double baseGain = scheduledCoherentBaseGainFor(level, pos, state, frequency);

        if (baseGain <= 1.0) {
            return 0.0;
        }

        double spectralCoupling = emissionCoupling(spec, frequency);

        if (spectralCoupling <= 0.0) {
            return 0.0;
        }

        double passiveTransmittance = profileFor(level, pos, state).responseAt(frequency).transmittance();
        return passiveTransmittance * spec.saturationPower() * (1.0 - 1.0 / baseGain);
    }

    public static double rubyExcitedCoherentSeedPowerPerDirection(Level level, BlockPos pos, BlockState state) {
        return excitedCoherentSeedPowerPerDirection(level, pos, state);
    }

    public static double excitedCoherentSeedPowerPerDirection(Level level, BlockPos pos, BlockState state) {
        if (level == null
                || pos == null
                || state == null) {
            return 0.0;
        }

        GainMediumSpec spec = gainMediumSpec(state);

        if (spec == null) {
            return 0.0;
        }

        double exciterStrength = adjacentGainMediumExciterStrength(level, pos, spec);

        if (exciterStrength <= 0.0) {
            return 0.0;
        }

        double totalPower = spec.seedPowerPerExciter()
                * exciterStrength
                * emissionCoupling(spec, spec.emissionLine());

        return totalPower / Direction.values().length;
    }

    public static double rubyDomainPumpDensity(Level level, BlockPos pos, BlockState state) {
        return gainMediumDomainStats(level, pos, state).pumpDensity();
    }

    public static double gainMediumDomainPumpDensity(Level level, BlockPos pos, BlockState state) {
        return gainMediumDomainStats(level, pos, state).pumpDensity();
    }

    private static GainMediumDomainStats gainMediumDomainStats(Level level, BlockPos pos, BlockState state) {
        GainMediumSpec spec = gainMediumSpec(state);

        if (level == null
                || pos == null
                || state == null
                || spec == null) {
            return GainMediumDomainStats.EMPTY;
        }

        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        double totalPumpRate = 0.0;
        int gainMediumCount = 0;
        Block gainMediumBlock = state.getBlock();

        open.add(pos.immutable());

        while (!open.isEmpty() && gainMediumCount < RUBY_DOMAIN_MAX_BLOCKS) {
            BlockPos current = open.removeFirst();

            if (!visited.add(current)) {
                continue;
            }

            BlockState currentState = level.getBlockState(current);

            if (currentState.getBlock() != gainMediumBlock) {
                continue;
            }

            gainMediumCount++;
            totalPumpRate += effectiveAdjacentPumpRate(level, current, spec);

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = current.relative(direction);

                if (!visited.contains(neighbor)
                        && level.getBlockState(neighbor).getBlock() == gainMediumBlock) {
                    open.add(neighbor.immutable());
                }
            }
        }

        if (gainMediumCount <= 0) {
            return GainMediumDomainStats.EMPTY;
        }

        return new GainMediumDomainStats(gainMediumCount, totalPumpRate);
    }

    private static double effectiveAdjacentPumpRate(Level level, BlockPos pos, GainMediumSpec spec) {
        double pumpRate = 0.0;

        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);

            if (!level.isLoaded(neighbor)) {
                continue;
            }

            BlockState sourceState = level.getBlockState(neighbor);
            double rawPumpRate = OpticalPumpSources.pumpRateFor(level, neighbor, sourceState);

            if (rawPumpRate > 0) {
                pumpRate += rawPumpRate * pumpAffinity(spec, sourceState);
            }
        }

        return pumpRate;
    }

    private static double adjacentGainMediumExciterStrength(Level level, BlockPos pos, GainMediumSpec spec) {
        double strength = 0.0;

        for (Direction direction : Direction.values()) {
            BlockPos sourcePos = pos.relative(direction);
            strength += seedSourceStrength(level, sourcePos, spec, level.getBlockState(sourcePos));
        }

        return strength;
    }

    private static double pumpAffinity(GainMediumSpec spec, BlockState sourceState) {
        if (sourceState.getBlock() == Spectralization.PUMP_MAGMA_BLOCK.get()) {
            return spec.magmaPumpAffinity();
        }

        if (sourceState.getBlock() == Spectralization.HIGH_DENSITY_PUMP_MAGMA_BLOCK.get()) {
            return spec.denseMagmaPumpAffinity();
        }

        if (sourceState.getBlock() == Spectralization.DIODE_PUMP.get()) {
            return spec.diodePumpAffinity();
        }

        return 1.0D;
    }

    private static double seedSourceStrength(Level level, BlockPos pos, GainMediumSpec spec, BlockState sourceState) {
        Block block = sourceState.getBlock();
        double sourceStrength = SeedLightBlockEntity.seedStrengthFor(level, pos, sourceState);

        if (sourceStrength <= 0.0D) {
            return 0.0D;
        }

        if (block == Spectralization.LIGHT_SEED_GLOWSTONE_BLOCK.get()) {
            return sourceStrength * spec.glowstoneSeedAffinity();
        }

        if (block == Spectralization.HIGH_DENSITY_LIGHT_SEED_GLOWSTONE_BLOCK.get()) {
            return sourceStrength * spec.basicSeedDiodeAffinity();
        }

        if (block == Spectralization.DIODE_LIGHT_SEED.get()) {
            return sourceStrength * spec.tunedSeedDiodeAffinity();
        }

        return 0.0D;
    }

    public static double gainMaterialWeightFor(BlockState state) {
        if (state.is(Spectralization.FIBER_LASER.get())) {
            return FiberLaserBlockEntity.GAIN_MATERIAL_WEIGHT;
        }

        GainMediumSpec spec = gainMediumSpec(state);

        if (spec != null) {
            return spec.materialWeight();
        }

        return 0.0;
    }

    public static boolean scheduledCoherentGainAffectsAllPresentFrequencies(BlockState state) {
        return state.is(Spectralization.FIBER_LASER.get());
    }

    private static double emissionCoupling(GainMediumSpec spec, FrequencyKey frequency) {
        if (frequency.region() != spec.emissionLine().region()) {
            return 0.0;
        }

        int distance = Math.abs(frequency.bin() - spec.emissionLine().bin());

        if (distance >= spec.couplingHalfWidth()) {
            return 0.0;
        }

        return Math.max(0.0, 1.0 - distance / spec.couplingHalfWidth());
    }

    private static GainMediumSpec gainMediumSpec(BlockState state) {
        if (state == null) {
            return null;
        }

        Block block = state.getBlock();

        if (block == Spectralization.RUBY_BLOCK.get()) {
            return RUBY_GAIN_MEDIUM;
        }

        if (block == Spectralization.CE_YAG_CRYSTAL_BLOCK.get()) {
            return CE_YAG_GAIN_MEDIUM;
        }

        if (block == Spectralization.ND_YAG_CRYSTAL_BLOCK.get()) {
            return ND_YAG_GAIN_MEDIUM;
        }

        if (block == Spectralization.YB_YAG_CRYSTAL_BLOCK.get()) {
            return YB_YAG_GAIN_MEDIUM;
        }

        if (block == Spectralization.ER_YAG_CRYSTAL_BLOCK.get()) {
            return ER_YAG_GAIN_MEDIUM;
        }

        if (block == Spectralization.CE_FLUORITE_CRYSTAL_BLOCK.get()) {
            return CE_FLUORITE_GAIN_MEDIUM;
        }

        if (block == Spectralization.ND_FLUORITE_CRYSTAL_BLOCK.get()) {
            return ND_FLUORITE_GAIN_MEDIUM;
        }

        if (block == Spectralization.YB_FLUORITE_CRYSTAL_BLOCK.get()) {
            return YB_FLUORITE_GAIN_MEDIUM;
        }

        if (block == Spectralization.ER_FLUORITE_CRYSTAL_BLOCK.get()) {
            return ER_FLUORITE_GAIN_MEDIUM;
        }

        return null;
    }

    private static OpticalMaterialProfile spectralGlassProfile(Block block) {
        if (block == Spectralization.QUARTZ_GLASS.get()) {
            return QUARTZ_GLASS;
        }

        if (block == Spectralization.BOROSILICATE_GLASS.get()) {
            return BOROSILICATE_GLASS;
        }

        if (block == Spectralization.CROWN_GLASS.get()) {
            return CROWN_GLASS;
        }

        if (block == Spectralization.FLINT_GLASS.get()) {
            return FLINT_GLASS;
        }

        if (block == Spectralization.HEAVY_GLASS.get()) {
            return HEAVY_GLASS;
        }

        return null;
    }

    private static OpticalMaterialProfile rareEarthYagProfile(FrequencyKey line, boolean pumped) {
        return rareEarthCrystalProfile(line, pumped, true);
    }

    private static OpticalMaterialProfile rareEarthFluoriteProfile(FrequencyKey line, boolean pumped) {
        return rareEarthCrystalProfile(line, pumped, false);
    }

    private static OpticalMaterialProfile rareEarthCrystalProfile(
            FrequencyKey line,
            boolean pumped,
            boolean yag
    ) {
        OpticalMaterialResponse lineResponse = yag
                ? OpticalMaterialResponse.of(pumped ? 0.972 : 0.955, pumped ? 0.012 : 0.015, pumped ? 0.004 : 0.012)
                : OpticalMaterialResponse.of(pumped ? 0.985 : 0.975, pumped ? 0.010 : 0.012, pumped ? 0.002 : 0.006);
        OpticalMaterialResponse visibleOffLine = yag
                ? OpticalMaterialResponse.of(pumped ? 0.740 : 0.780, 0.025, pumped ? 0.090 : 0.070)
                : OpticalMaterialResponse.of(pumped ? 0.890 : 0.910, 0.018, pumped ? 0.040 : 0.030);
        OpticalMaterialResponse infraredOffLine = yag
                ? OpticalMaterialResponse.of(0.900, 0.025, 0.035)
                : OpticalMaterialResponse.of(0.940, 0.020, 0.025);
        OpticalMaterialResponse ultravioletOffLine = yag
                ? OpticalMaterialResponse.of(0.480, 0.030, 0.420)
                : OpticalMaterialResponse.of(0.880, 0.025, 0.055);

        if (line.region() == SpectralRegion.VISIBLE) {
            int lowBin = Math.max(0, line.bin() - 4);
            int highBin = Math.min(SpectralRegion.VISIBLE.defaultBins() - 1, line.bin() + 4);

            return OpticalMaterialProfile.of(
                    new OpticalMaterialSample(new FrequencyKey(SpectralRegion.INFRARED, 16), infraredOffLine),
                    new OpticalMaterialSample(FrequencyKey.visible(0), visibleOffLine),
                    new OpticalMaterialSample(FrequencyKey.visible(lowBin), visibleOffLine),
                    new OpticalMaterialSample(line, lineResponse),
                    new OpticalMaterialSample(FrequencyKey.visible(highBin), visibleOffLine),
                    new OpticalMaterialSample(
                            FrequencyKey.visible(SpectralRegion.VISIBLE.defaultBins() - 1),
                            visibleOffLine
                    ),
                    new OpticalMaterialSample(new FrequencyKey(SpectralRegion.ULTRAVIOLET, 16), ultravioletOffLine)
            );
        }

        return OpticalMaterialProfile.of(
                new OpticalMaterialSample(new FrequencyKey(SpectralRegion.INFRARED, 0), infraredOffLine),
                new OpticalMaterialSample(line, lineResponse),
                new OpticalMaterialSample(
                        new FrequencyKey(SpectralRegion.INFRARED, SpectralRegion.INFRARED.defaultBins() - 1),
                        infraredOffLine
                ),
                new OpticalMaterialSample(FrequencyKey.visible(16), visibleOffLine),
                new OpticalMaterialSample(new FrequencyKey(SpectralRegion.ULTRAVIOLET, 16), ultravioletOffLine)
        );
    }

    private record GainMediumSpec(
            FrequencyKey emissionLine,
            double referencePump,
            boolean pumpLimited,
            double gainPerPumpUnit,
            double saturationPower,
            double handlingLimit,
            double seedPowerPerExciter,
            double magmaPumpAffinity,
            double denseMagmaPumpAffinity,
            double diodePumpAffinity,
            double glowstoneSeedAffinity,
            double basicSeedDiodeAffinity,
            double tunedSeedDiodeAffinity,
            double materialWeight,
            double couplingHalfWidth,
            OpticalMaterialProfile unpumpedProfile,
            OpticalMaterialProfile pumpedProfile
    ) {
        private GainMediumSpec {
            if (referencePump <= 0.0D
                    || gainPerPumpUnit <= 0.0D
                    || saturationPower <= 0.0D
                    || handlingLimit <= 0.0D
                    || seedPowerPerExciter <= 0.0D
                    || magmaPumpAffinity <= 0.0D
                    || denseMagmaPumpAffinity <= 0.0D
                    || diodePumpAffinity <= 0.0D
                    || glowstoneSeedAffinity <= 0.0D
                    || basicSeedDiodeAffinity <= 0.0D
                    || tunedSeedDiodeAffinity <= 0.0D
                    || materialWeight <= 0.0D
                    || couplingHalfWidth <= 0.0D) {
                throw new IllegalArgumentException("Gain medium spec values must be positive");
            }
        }
    }

    private record GainMediumDomainStats(int blockCount, double pumpRate) {
        private static final GainMediumDomainStats EMPTY = new GainMediumDomainStats(0, 0.0);

        private GainMediumDomainStats {
            if (blockCount < 0) {
                throw new IllegalArgumentException("Gain medium domain block count must be non-negative");
            }

            if (!Double.isFinite(pumpRate) || pumpRate < 0.0) {
                throw new IllegalArgumentException("Gain medium domain pump rate must be finite and non-negative");
            }
        }

        private double pumpDensity() {
            if (blockCount <= 0) {
                return 0.0;
            }

            return pumpRate / blockCount;
        }
    }

    private static OpticalMaterialProfile coloredGlass(int centerBin) {
        int lowBin = Math.max(0, centerBin - 9);
        int highBin = Math.min(SpectralRegion.VISIBLE.defaultBins() - 1, centerBin + 9);

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
