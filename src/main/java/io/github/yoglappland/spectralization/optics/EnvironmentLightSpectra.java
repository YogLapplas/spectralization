package io.github.yoglappland.spectralization.optics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
        Map<FrequencyKey, Double> spectrumShapeSum = new LinkedHashMap<>();
        int sourceCount = 0;
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

                Map<FrequencyKey, Double> normalizedSpectrum = normalizedSpectrum(sampleState);

                for (Map.Entry<FrequencyKey, Double> entry : normalizedSpectrum.entrySet()) {
                    spectrumShapeSum.merge(entry.getKey(), entry.getValue(), Double::sum);
                }

                sourceCount++;
                totalLightPower += light * BASE_POWER_PER_FULL_LIGHT / 15.0;
            }
        }

        if (sourceCount <= 0 || totalLightPower <= 0.0) {
            return BeamPacket.empty(STRAY_ENVIRONMENT_ENVELOPE);
        }

        double outputPower = totalLightPower * efficiency;
        int sampledSourceCount = sourceCount;
        List<PlaneWaveComponent> components = spectrumShapeSum.entrySet().stream()
                .map(entry -> new PlaneWaveComponent(
                        entry.getKey(),
                        outputPower * entry.getValue() / sampledSourceCount,
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

    private static Map<FrequencyKey, Double> normalizedSpectrum(BlockState state) {
        Block block = state.getBlock();

        if (block == Blocks.LAVA
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CAMPFIRE
                || block == Blocks.FIRE
                || block == Blocks.LANTERN
                || block == Blocks.TORCH
                || block == Blocks.WALL_TORCH) {
            return warmCombustionSpectrum();
        }

        if (block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.SOUL_FIRE
                || block == Blocks.SOUL_LANTERN
                || block == Blocks.SOUL_TORCH
                || block == Blocks.SOUL_WALL_TORCH) {
            return soulSpectrum();
        }

        if (block == Blocks.REDSTONE_TORCH
                || block == Blocks.REDSTONE_WALL_TORCH
                || block == Blocks.REDSTONE_LAMP) {
            return redSpectrum();
        }

        if (block == Blocks.SEA_LANTERN
                || block == Blocks.END_ROD
                || block == Blocks.PEARLESCENT_FROGLIGHT) {
            return coolWhiteSpectrum();
        }

        if (block == Blocks.OCHRE_FROGLIGHT
                || block == Blocks.VERDANT_FROGLIGHT
                || block == Blocks.SHROOMLIGHT
                || block == Blocks.GLOWSTONE) {
            return warmWhiteSpectrum();
        }

        return neutralWhiteSpectrum();
    }

    private static Map<FrequencyKey, Double> warmCombustionSpectrum() {
        return spectrum(
                component(SpectralRegion.INFRARED, 9, 0.55),
                component(SpectralRegion.VISIBLE, 19, 0.10),
                component(SpectralRegion.VISIBLE, 22, 0.15),
                component(SpectralRegion.VISIBLE, 25, 0.20)
        );
    }

    private static Map<FrequencyKey, Double> warmWhiteSpectrum() {
        return spectrum(
                component(SpectralRegion.INFRARED, 7, 0.15),
                component(SpectralRegion.VISIBLE, 15, 0.12),
                component(SpectralRegion.VISIBLE, 18, 0.20),
                component(SpectralRegion.VISIBLE, 21, 0.28),
                component(SpectralRegion.VISIBLE, 24, 0.25)
        );
    }

    private static Map<FrequencyKey, Double> coolWhiteSpectrum() {
        return spectrum(
                component(SpectralRegion.VISIBLE, 5, 0.18),
                component(SpectralRegion.VISIBLE, 8, 0.25),
                component(SpectralRegion.VISIBLE, 12, 0.25),
                component(SpectralRegion.VISIBLE, 16, 0.20),
                component(SpectralRegion.VISIBLE, 20, 0.12)
        );
    }

    private static Map<FrequencyKey, Double> neutralWhiteSpectrum() {
        return spectrum(
                component(SpectralRegion.VISIBLE, 7, 0.16),
                component(SpectralRegion.VISIBLE, 11, 0.22),
                component(SpectralRegion.VISIBLE, 15, 0.24),
                component(SpectralRegion.VISIBLE, 19, 0.22),
                component(SpectralRegion.VISIBLE, 23, 0.16)
        );
    }

    private static Map<FrequencyKey, Double> redSpectrum() {
        return spectrum(component(SpectralRegion.VISIBLE, 25, 1.0));
    }

    private static Map<FrequencyKey, Double> soulSpectrum() {
        return spectrum(
                component(SpectralRegion.VISIBLE, 5, 0.25),
                component(SpectralRegion.VISIBLE, 8, 0.35),
                component(SpectralRegion.VISIBLE, 11, 0.25),
                component(SpectralRegion.INFRARED, 4, 0.15)
        );
    }

    private static Map<FrequencyKey, Double> spectrum(SpectrumComponent... components) {
        Map<FrequencyKey, Double> spectrum = new LinkedHashMap<>();
        double total = 0.0;

        for (SpectrumComponent component : components) {
            if (component.weight() <= 0.0) {
                continue;
            }

            total += component.weight();
        }

        if (total <= 0.0) {
            return Map.of(FrequencyKey.DEBUG_VISIBLE, 1.0);
        }

        for (SpectrumComponent component : components) {
            if (component.weight() <= 0.0) {
                continue;
            }

            spectrum.merge(component.frequency(), component.weight() / total, Double::sum);
        }

        return Map.copyOf(spectrum);
    }

    private static SpectrumComponent component(SpectralRegion region, int bin, double weight) {
        return new SpectrumComponent(new FrequencyKey(region, bin), weight);
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

    private record SpectrumComponent(FrequencyKey frequency, double weight) {
    }
}
