package io.github.yoglappland.spectralization.optics.surface;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalMaterialResponse;
import io.github.yoglappland.spectralization.optics.medium.OpticalMediumResponse;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalInterfaceResolver {
    public static OpticalInterfaceResponse responseBetween(
            FrequencyKey frequency,
            SurfaceProfile incidentSurface,
            SurfaceProfile transmittedSurface
    ) {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(incidentSurface, "incidentSurface");
        Objects.requireNonNull(transmittedSurface, "transmittedSurface");

        OpticalMediumResponse incidentMedium = incidentSurface.mediumProfile().responseAt(frequency);
        OpticalMediumResponse transmittedMedium = transmittedSurface.mediumProfile().responseAt(frequency);
        double impedanceReflectance = impedanceReflectance(
                incidentMedium.relativeImpedance(),
                transmittedMedium.relativeImpedance()
        );
        double surfaceReflectance = combineReflectance(
                incidentSurface.interfaceProfile().impedanceMismatchReflectance(),
                transmittedSurface.interfaceProfile().impedanceMismatchReflectance()
        );
        double reflectance = combineReflectance(impedanceReflectance, surfaceReflectance);

        return OpticalInterfaceResponse.of(1.0 - reflectance, reflectance, 0.0);
    }

    public static EffectiveSurfaceResponse effectiveResponseBetween(
            FrequencyKey frequency,
            SurfaceProfile incidentSurface,
            SurfaceProfile transmittedSurface
    ) {
        OpticalMaterialResponse surfaceResponse = incidentSurface.materialProfile().responseAt(frequency);
        OpticalInterfaceResponse interfaceResponse = responseBetween(frequency, incidentSurface, transmittedSurface);

        return new EffectiveSurfaceResponse(surfaceResponse, interfaceResponse);
    }

    public static OpticalInterfaceResponse responseAtBoundary(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction side,
            FrequencyKey frequency
    ) {
        SurfaceKey localKey = new SurfaceKey(pos, side);
        SurfaceKey neighborKey = localKey.neighborKey();
        BlockState neighborState = level.getBlockState(neighborKey.pos());
        SurfaceProfile localSurface = OpticalSurfaceResolver.surfaceFor(level, localKey.pos(), state, localKey.side());
        SurfaceProfile neighborSurface = OpticalSurfaceResolver.surfaceFor(
                level,
                neighborKey.pos(),
                neighborState,
                neighborKey.side()
        );

        return responseBetween(frequency, localSurface, neighborSurface);
    }

    public static double impedanceReflectance(double incidentImpedance, double transmittedImpedance) {
        if (!Double.isFinite(incidentImpedance)
                || !Double.isFinite(transmittedImpedance)
                || incidentImpedance <= 0.0
                || transmittedImpedance <= 0.0) {
            throw new IllegalArgumentException("Interface impedances must be finite and positive");
        }

        double numerator = transmittedImpedance - incidentImpedance;
        double denominator = transmittedImpedance + incidentImpedance;
        double amplitude = numerator / denominator;

        return clamp01(amplitude * amplitude);
    }

    private static double combineReflectance(double first, double second) {
        return clamp01(1.0 - (1.0 - clamp01(first)) * (1.0 - clamp01(second)));
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }

        if (value <= 0.0) {
            return 0.0;
        }

        return Math.min(1.0, value);
    }

    private OpticalInterfaceResolver() {
    }
}
