package io.github.yoglappland.spectralization.optics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class EnvironmentLightSpectra {
    public static final BeamEnvelope STRAY_ENVIRONMENT_ENVELOPE =
            BeamEnvelope.collimated(0.75).withBeamQuality(16.0).withScatter(1.0);
    public static final double BASIC_EFFICIENCY = 1.0;
    public static final double ADVANCED_EFFICIENCY = 1.5;

    private static final double BASE_POWER_PER_FULL_LIGHT = 1.0;
    private static final SpectrumProfile CREATIVE_VISIBLE_WHITE = profile(
            band(SpectralRegion.VISIBLE, 7.0, 7.0, 0.55, 0, 31, 1),
            band(SpectralRegion.VISIBLE, 16.0, 8.5, 0.70, 0, 31, 1),
            band(SpectralRegion.VISIBLE, 24.0, 7.0, 0.55, 0, 31, 1)
    );
    private static final SpectrumProfile NEUTRAL_WHITE = profile(
            band(SpectralRegion.INFRARED, 14.0, 7.0, 0.04, 0, 31, 4),
            band(SpectralRegion.VISIBLE, 7.0, 7.0, 0.45, 0, 31, 1),
            band(SpectralRegion.VISIBLE, 16.0, 8.0, 0.62, 0, 31, 1),
            band(SpectralRegion.VISIBLE, 24.0, 7.0, 0.45, 0, 31, 1),
            band(SpectralRegion.ULTRAVIOLET, 4.0, 3.5, 0.03, 0, 12, 3)
    );
    private static final SpectrumProfile WARM_WHITE = profile(
            band(SpectralRegion.INFRARED, 12.0, 8.0, 0.12, 0, 31, 3),
            band(SpectralRegion.VISIBLE, 10.0, 8.5, 0.25, 0, 31, 1),
            band(SpectralRegion.VISIBLE, 20.0, 8.0, 0.70, 0, 31, 1),
            band(SpectralRegion.VISIBLE, 27.0, 5.0, 0.45, 0, 31, 1)
    );
    private static final SpectrumProfile COOL_WHITE = profile(
            band(SpectralRegion.INFRARED, 12.0, 6.0, 0.02, 0, 31, 4),
            band(SpectralRegion.VISIBLE, 5.0, 6.0, 0.52, 0, 31, 1),
            band(SpectralRegion.VISIBLE, 12.0, 7.0, 0.60, 0, 31, 1),
            band(SpectralRegion.VISIBLE, 18.0, 8.0, 0.35, 0, 31, 1),
            band(SpectralRegion.ULTRAVIOLET, 4.0, 3.5, 0.08, 0, 14, 2)
    );
    private static final SpectrumProfile WHITE_LED = profile(
            band(SpectralRegion.VISIBLE, 4.5, 2.0, 0.58, 0, 12, 1),
            band(SpectralRegion.VISIBLE, 16.5, 7.2, 0.78, 4, 30, 1),
            band(SpectralRegion.VISIBLE, 25.0, 5.4, 0.34, 12, 31, 1)
    );
    private static final SpectrumProfile WARM_COMBUSTION = profile(
            band(SpectralRegion.THZ, 8.0, 5.5, 0.05, 0, 15, 3),
            band(SpectralRegion.INFRARED, 11.0, 9.0, 0.46, 0, 31, 2),
            band(SpectralRegion.VISIBLE, 17.0, 5.5, 0.16, 8, 31, 1),
            band(SpectralRegion.VISIBLE, 24.0, 4.8, 0.26, 8, 31, 1),
            band(SpectralRegion.VISIBLE, 29.0, 2.8, 0.07, 18, 31, 1)
    );
    private static final SpectrumProfile HOT_MOLTEN = profile(
            band(SpectralRegion.THZ, 9.0, 5.5, 0.05, 0, 15, 3),
            band(SpectralRegion.INFRARED, 14.0, 9.5, 0.55, 0, 31, 2),
            band(SpectralRegion.VISIBLE, 20.0, 5.0, 0.18, 10, 31, 1),
            band(SpectralRegion.VISIBLE, 27.0, 4.0, 0.22, 14, 31, 1)
    );
    private static final SpectrumProfile REDSTONE_GLOW = profile(
            band(SpectralRegion.MICROWAVE, 8.0, 4.0, 0.03, 0, 15, 4),
            band(SpectralRegion.THZ, 8.0, 5.0, 0.07, 0, 15, 3),
            band(SpectralRegion.INFRARED, 21.0, 7.0, 0.25, 5, 31, 2),
            band(SpectralRegion.VISIBLE, 25.5, 4.0, 0.65, 15, 31, 1)
    );
    private static final SpectrumProfile SOUL_LIGHT = profile(
            band(SpectralRegion.INFRARED, 6.0, 4.0, 0.03, 0, 20, 4),
            band(SpectralRegion.VISIBLE, 4.0, 5.0, 0.45, 0, 18, 1),
            band(SpectralRegion.VISIBLE, 10.0, 4.5, 0.45, 0, 22, 1),
            band(SpectralRegion.ULTRAVIOLET, 5.0, 4.0, 0.12, 0, 16, 2)
    );
    private static final SpectrumProfile BIOLUMINESCENT = profile(
            band(SpectralRegion.INFRARED, 8.0, 5.0, 0.02, 0, 20, 4),
            band(SpectralRegion.VISIBLE, 8.0, 5.5, 0.45, 0, 22, 1),
            band(SpectralRegion.VISIBLE, 14.0, 5.5, 0.55, 2, 26, 1),
            band(SpectralRegion.ULTRAVIOLET, 3.0, 3.0, 0.03, 0, 12, 3)
    );
    private static final SpectrumProfile ARCANE_PURPLE = profile(
            band(SpectralRegion.VISIBLE, 3.0, 4.0, 0.42, 0, 14, 1),
            band(SpectralRegion.VISIBLE, 28.0, 3.0, 0.22, 20, 31, 1),
            band(SpectralRegion.ULTRAVIOLET, 6.0, 4.0, 0.26, 0, 18, 2),
            band(SpectralRegion.FAR_ULTRAVIOLET, 4.0, 3.0, 0.07, 0, 12, 3)
    );
    private static final SpectrumProfile TECHNOLOGY_CYAN = profile(
            band(SpectralRegion.INFRARED, 10.0, 5.0, 0.04, 0, 24, 4),
            band(SpectralRegion.VISIBLE, 7.0, 5.5, 0.46, 0, 24, 1),
            band(SpectralRegion.VISIBLE, 13.0, 5.5, 0.46, 0, 28, 1),
            band(SpectralRegion.ULTRAVIOLET, 5.0, 3.8, 0.10, 0, 16, 2)
    );
    private static final SpectrumProfile TECHNOLOGY_BLUE = profile(
            band(SpectralRegion.INFRARED, 9.0, 5.0, 0.03, 0, 24, 4),
            band(SpectralRegion.VISIBLE, 4.0, 4.8, 0.56, 0, 20, 1),
            band(SpectralRegion.VISIBLE, 10.0, 5.5, 0.38, 0, 24, 1),
            band(SpectralRegion.ULTRAVIOLET, 4.0, 3.5, 0.11, 0, 14, 2)
    );
    private static volatile Map<ResourceLocation, SpectrumProfile> editorSpectrumOverrides = Map.of();

    private static final Set<Block> WARM_COMBUSTION_BLOCKS = Set.of(
            Blocks.LAVA,
            Blocks.MAGMA_BLOCK,
            Blocks.CAMPFIRE,
            Blocks.FIRE,
            Blocks.LANTERN,
            Blocks.TORCH,
            Blocks.WALL_TORCH,
            Blocks.CANDLE,
            Blocks.WHITE_CANDLE,
            Blocks.ORANGE_CANDLE,
            Blocks.MAGENTA_CANDLE,
            Blocks.LIGHT_BLUE_CANDLE,
            Blocks.YELLOW_CANDLE,
            Blocks.LIME_CANDLE,
            Blocks.PINK_CANDLE,
            Blocks.GRAY_CANDLE,
            Blocks.LIGHT_GRAY_CANDLE,
            Blocks.CYAN_CANDLE,
            Blocks.PURPLE_CANDLE,
            Blocks.BLUE_CANDLE,
            Blocks.BROWN_CANDLE,
            Blocks.GREEN_CANDLE,
            Blocks.RED_CANDLE,
            Blocks.BLACK_CANDLE,
            Blocks.CANDLE_CAKE,
            Blocks.WHITE_CANDLE_CAKE,
            Blocks.ORANGE_CANDLE_CAKE,
            Blocks.MAGENTA_CANDLE_CAKE,
            Blocks.LIGHT_BLUE_CANDLE_CAKE,
            Blocks.YELLOW_CANDLE_CAKE,
            Blocks.LIME_CANDLE_CAKE,
            Blocks.PINK_CANDLE_CAKE,
            Blocks.GRAY_CANDLE_CAKE,
            Blocks.LIGHT_GRAY_CANDLE_CAKE,
            Blocks.CYAN_CANDLE_CAKE,
            Blocks.PURPLE_CANDLE_CAKE,
            Blocks.BLUE_CANDLE_CAKE,
            Blocks.BROWN_CANDLE_CAKE,
            Blocks.GREEN_CANDLE_CAKE,
            Blocks.RED_CANDLE_CAKE,
            Blocks.BLACK_CANDLE_CAKE,
            Blocks.JACK_O_LANTERN
    );
    private static final Set<Block> SOUL_LIGHT_BLOCKS = Set.of(
            Blocks.SOUL_CAMPFIRE,
            Blocks.SOUL_FIRE,
            Blocks.SOUL_LANTERN,
            Blocks.SOUL_TORCH,
            Blocks.SOUL_WALL_TORCH
    );
    private static final Set<Block> REDSTONE_GLOW_BLOCKS = Set.of(
            Blocks.REDSTONE_TORCH,
            Blocks.REDSTONE_WALL_TORCH,
            Blocks.REDSTONE_LAMP,
            Blocks.REDSTONE_ORE,
            Blocks.DEEPSLATE_REDSTONE_ORE
    );
    private static final Set<Block> WARM_WHITE_BLOCKS = Set.of(
            Blocks.GLOWSTONE,
            Blocks.SHROOMLIGHT,
            Blocks.OCHRE_FROGLIGHT,
            Blocks.COPPER_BULB,
            Blocks.EXPOSED_COPPER_BULB,
            Blocks.WEATHERED_COPPER_BULB,
            Blocks.OXIDIZED_COPPER_BULB,
            Blocks.WAXED_COPPER_BULB,
            Blocks.WAXED_EXPOSED_COPPER_BULB,
            Blocks.WAXED_WEATHERED_COPPER_BULB,
            Blocks.WAXED_OXIDIZED_COPPER_BULB
    );
    private static final Set<Block> COOL_WHITE_BLOCKS = Set.of(
            Blocks.SEA_LANTERN,
            Blocks.END_ROD,
            Blocks.PEARLESCENT_FROGLIGHT,
            Blocks.BEACON,
            Blocks.CONDUIT
    );
    private static final Set<Block> BIOLUMINESCENT_BLOCKS = Set.of(
            Blocks.GLOW_LICHEN,
            Blocks.CAVE_VINES,
            Blocks.CAVE_VINES_PLANT,
            Blocks.SEA_PICKLE,
            Blocks.VERDANT_FROGLIGHT
    );
    private static final Set<Block> ARCANE_PURPLE_BLOCKS = Set.of(
            Blocks.NETHER_PORTAL,
            Blocks.END_PORTAL,
            Blocks.END_GATEWAY,
            Blocks.END_PORTAL_FRAME,
            Blocks.ENDER_CHEST,
            Blocks.RESPAWN_ANCHOR,
            Blocks.ENCHANTING_TABLE,
            Blocks.AMETHYST_CLUSTER,
            Blocks.LARGE_AMETHYST_BUD,
            Blocks.MEDIUM_AMETHYST_BUD,
            Blocks.SMALL_AMETHYST_BUD
    );
    private static final Set<Block> SCULK_LIGHT_BLOCKS = Set.of(
            Blocks.SCULK_SENSOR,
            Blocks.CALIBRATED_SCULK_SENSOR,
            Blocks.SCULK_CATALYST,
            Blocks.SCULK_SHRIEKER
    );

    public static BeamPacket collect(
            Level level,
            BlockPos collectorPos,
            Direction outputDirection,
            int sampleRadius,
            double efficiency
    ) {
        Direction detectionDirection = outputDirection.getOpposite();
        Direction[] axes = samplingAxes(detectionDirection);
        BlockPos center = collectorPos.relative(detectionDirection);
        Map<FrequencyKey, Double> weightedSpectrum = new LinkedHashMap<>();
        double totalLightPower = 0.0;

        for (int u = -sampleRadius; u <= sampleRadius; u++) {
            for (int v = -sampleRadius; v <= sampleRadius; v++) {
                BlockPos samplePos = center
                        .offset(axes[0].getStepX() * u + axes[1].getStepX() * v,
                                axes[0].getStepY() * u + axes[1].getStepY() * v,
                                axes[0].getStepZ() * u + axes[1].getStepZ() * v);

                if (!level.isLoaded(samplePos)) {
                    continue;
                }

                BlockState sampleState = level.getBlockState(samplePos);
                int light = lightEmission(level, samplePos, sampleState);

                if (light <= 0) {
                    continue;
                }

                double samplePower = light * BASE_POWER_PER_FULL_LIGHT / 15.0;
                Map<FrequencyKey, Double> normalizedSpectrum = normalizedSpectrum(sampleState);

                for (Map.Entry<FrequencyKey, Double> entry : normalizedSpectrum.entrySet()) {
                    weightedSpectrum.merge(entry.getKey(), entry.getValue() * samplePower, Double::sum);
                }

                totalLightPower += samplePower;
            }
        }

        if (weightedSpectrum.isEmpty() || totalLightPower <= 0.0) {
            return BeamPacket.empty(STRAY_ENVIRONMENT_ENVELOPE);
        }

        List<PlaneWaveComponent> components = weightedSpectrum.entrySet().stream()
                .map(entry -> new PlaneWaveComponent(
                        entry.getKey(),
                        efficiency * entry.getValue(),
                        outputDirection,
                        CoherenceKind.INCOHERENT
                ))
                .filter(component -> component.power() > 0.0)
                .sorted(Comparator.comparingDouble(PlaneWaveComponent::power).reversed())
                .limit(BeamPacket.MAX_COMPONENTS)
                .toList();

        return new BeamPacket(components, STRAY_ENVIRONMENT_ENVELOPE);
    }

    public static long sampleSignature(Level level, BlockPos collectorPos, Direction outputDirection, int sampleRadius) {
        Direction detectionDirection = outputDirection.getOpposite();
        Direction[] axes = samplingAxes(detectionDirection);
        BlockPos center = collectorPos.relative(detectionDirection);
        long signature = 0x6A09E667F3BCC909L;
        signature = mix(signature, outputDirection.ordinal());
        signature = mix(signature, sampleRadius);

        for (int u = -sampleRadius; u <= sampleRadius; u++) {
            for (int v = -sampleRadius; v <= sampleRadius; v++) {
                BlockPos samplePos = center
                        .offset(axes[0].getStepX() * u + axes[1].getStepX() * v,
                                axes[0].getStepY() * u + axes[1].getStepY() * v,
                                axes[0].getStepZ() * u + axes[1].getStepZ() * v);

                if (!level.isLoaded(samplePos)) {
                    signature = mix(signature, samplePos.asLong());
                    signature = mix(signature, -1);
                    continue;
                }

                BlockState sampleState = level.getBlockState(samplePos);
                int light = lightEmission(level, samplePos, sampleState);

                signature = mix(signature, samplePos.asLong());
                signature = mix(signature, BuiltInRegistries.BLOCK.getId(sampleState.getBlock()));
                signature = mix(signature, light);
            }
        }

        return signature;
    }

    private static int lightEmission(Level level, BlockPos pos, BlockState state) {
        return state.getLightEmission(level, pos);
    }

    public static Map<FrequencyKey, Double> normalizedSpectrum(BlockState state) {
        Block block = state.getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        SpectrumProfile override = editorSpectrumOverrides.get(blockId);

        if (override != null) {
            return override.normalizedSpectrum();
        }

        return profileFor(block, blockId).normalizedSpectrum();
    }

    public static SpectrumProfile creativeVisibleWhiteProfile() {
        return CREATIVE_VISIBLE_WHITE;
    }

    public static void replaceEditorSpectrumOverrides(Map<ResourceLocation, SpectrumProfile> overrides) {
        Objects.requireNonNull(overrides, "overrides");

        Map<ResourceLocation, SpectrumProfile> copied = new HashMap<>();

        for (Map.Entry<ResourceLocation, SpectrumProfile> entry : overrides.entrySet()) {
            copied.put(
                    Objects.requireNonNull(entry.getKey(), "override block id"),
                    Objects.requireNonNull(entry.getValue(), "override spectrum profile")
            );
        }

        editorSpectrumOverrides = Map.copyOf(copied);
    }

    private static SpectrumProfile profileFor(Block block, ResourceLocation blockId) {
        if (isSpectralization(blockId, "molten_")) {
            return HOT_MOLTEN;
        }

        if (isSpectralization(blockId, "holographic_storage_")
                || isSpectralization(blockId, "stable_holographic_storage_")) {
            return TECHNOLOGY_CYAN;
        }

        if (isSpectralization(blockId, "compact_machine_")) {
            return TECHNOLOGY_BLUE;
        }

        if (isSpectralizationBlock(blockId, "basic_led")
                || isSpectralizationBlock(blockId, "advanced_led")) {
            return WHITE_LED;
        }

        if (block == Blocks.LAVA || WARM_COMBUSTION_BLOCKS.contains(block)) {
            return WARM_COMBUSTION;
        }

        if (SOUL_LIGHT_BLOCKS.contains(block)) {
            return SOUL_LIGHT;
        }

        if (REDSTONE_GLOW_BLOCKS.contains(block)) {
            return REDSTONE_GLOW;
        }

        if (WARM_WHITE_BLOCKS.contains(block)) {
            return WARM_WHITE;
        }

        if (COOL_WHITE_BLOCKS.contains(block)) {
            return COOL_WHITE;
        }

        if (BIOLUMINESCENT_BLOCKS.contains(block) || SCULK_LIGHT_BLOCKS.contains(block)) {
            return BIOLUMINESCENT;
        }

        if (ARCANE_PURPLE_BLOCKS.contains(block)) {
            return ARCANE_PURPLE;
        }

        return NEUTRAL_WHITE;
    }

    private static boolean isSpectralization(ResourceLocation blockId, String pathPrefix) {
        return "spectralization".equals(blockId.getNamespace()) && blockId.getPath().startsWith(pathPrefix);
    }

    private static boolean isSpectralizationBlock(ResourceLocation blockId, String path) {
        return "spectralization".equals(blockId.getNamespace()) && path.equals(blockId.getPath());
    }

    private static SpectrumProfile profile(SpectrumBand... bands) {
        return new SpectrumProfile(List.of(bands));
    }

    public static SpectrumBand band(
            SpectralRegion region,
            double centerBin,
            double widthBins,
            double weight,
            int minBin,
            int maxBin,
            int step
    ) {
        return new SpectrumBand(region, centerBin, widthBins, weight, minBin, maxBin, step);
    }

    private static Map<FrequencyKey, Double> normalizeAndLimit(Map<FrequencyKey, Double> rawSpectrum) {
        double total = 0.0;

        for (double weight : rawSpectrum.values()) {
            total += Math.max(0.0, weight);
        }

        if (total <= 0.0) {
            return Map.of(FrequencyKey.DEBUG_VISIBLE, 1.0);
        }

        Map<FrequencyKey, Double> limited = new LinkedHashMap<>();
        rawSpectrum.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.0)
                .sorted(Map.Entry.<FrequencyKey, Double>comparingByValue().reversed())
                .limit(BeamPacket.MAX_COMPONENTS)
                .forEach(entry -> limited.put(entry.getKey(), entry.getValue()));

        double limitedTotal = 0.0;
        for (double weight : limited.values()) {
            limitedTotal += weight;
        }

        if (limitedTotal <= 0.0) {
            return Map.of(FrequencyKey.DEBUG_VISIBLE, 1.0);
        }

        Map<FrequencyKey, Double> spectrum = new LinkedHashMap<>();
        List<Map.Entry<FrequencyKey, Double>> orderedEntries = limited.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> spectralCoordinate(entry.getKey())))
                .toList();

        for (Map.Entry<FrequencyKey, Double> entry : orderedEntries) {
            spectrum.put(entry.getKey(), entry.getValue() / limitedTotal);
        }

        return Map.copyOf(spectrum);
    }

    private static int spectralCoordinate(FrequencyKey frequency) {
        return frequency.region().ordinal() * 1024 + frequency.bin();
    }

    private static Direction[] samplingAxes(Direction direction) {
        return switch (direction.getAxis()) {
            case X -> new Direction[]{Direction.UP, Direction.SOUTH};
            case Y -> new Direction[]{Direction.EAST, Direction.SOUTH};
            case Z -> new Direction[]{Direction.EAST, Direction.UP};
        };
    }

    private static long mix(long seed, long value) {
        long mixed = value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2);
        return seed ^ mixed;
    }

    private EnvironmentLightSpectra() {
    }

    public record SpectrumProfile(List<SpectrumBand> bands) {
        public SpectrumProfile {
            Objects.requireNonNull(bands, "bands");
            bands = List.copyOf(bands);
        }

        public Map<FrequencyKey, Double> normalizedSpectrum() {
            Map<FrequencyKey, Double> rawSpectrum = new LinkedHashMap<>();

            for (SpectrumBand band : bands) {
                band.addTo(rawSpectrum);
            }

            return normalizeAndLimit(rawSpectrum);
        }
    }

    public record SpectrumBand(
            SpectralRegion region,
            double centerBin,
            double widthBins,
            double weight,
            int minBin,
            int maxBin,
            int step
    ) {
        public SpectrumBand {
            Objects.requireNonNull(region, "region");

            if (!Double.isFinite(centerBin)) {
                throw new IllegalArgumentException("Spectrum band center must be finite");
            }

            if (!Double.isFinite(widthBins) || widthBins <= 0.0) {
                throw new IllegalArgumentException("Spectrum band width must be positive and finite");
            }

            if (!Double.isFinite(weight) || weight < 0.0) {
                throw new IllegalArgumentException("Spectrum band weight must be finite and non-negative");
            }

            if (minBin < 0 || maxBin < minBin || step <= 0) {
                throw new IllegalArgumentException("Spectrum band bin range is invalid");
            }
        }

        private void addTo(Map<FrequencyKey, Double> rawSpectrum) {
            int clampedMax = Math.min(maxBin, region.defaultBins() - 1);

            for (int bin = minBin; bin <= clampedMax; bin += step) {
                double distance = (bin - centerBin) / widthBins;
                double contribution = weight * Math.exp(-0.5 * distance * distance);

                if (contribution > 0.0) {
                    rawSpectrum.merge(new FrequencyKey(region, bin), contribution, Double::sum);
                }
            }
        }
    }
}
