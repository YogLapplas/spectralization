package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.optics.surface.EffectiveSurfaceResponse;
import io.github.yoglappland.spectralization.optics.surface.OpticalInterfaceResolver;
import io.github.yoglappland.spectralization.optics.surface.OpticalSurfaceResolver;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import io.github.yoglappland.spectralization.optics.surface.SurfaceProfile;
import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalBlockProperties {
    public static CompiledOpticalNetwork compile(BlockState state) {
        return compile(null, null, state);
    }

    public static CompiledOpticalNetwork compile(Level level, BlockPos pos, BlockState state) {
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder()
                .absorptionModel((input, incomingDirection, outputs) -> absorbedPower(level, pos, state, input, incomingDirection));

        for (Direction incomingDirection : Direction.values()) {
            Direction transmittedDirection = incomingDirection.getOpposite();
            builder.addRule(
                    incomingDirection,
                    transmittedDirection,
                    (input, ignoredIncoming, outgoingDirection) ->
                            transform(level, pos, state, input, incomingDirection, outgoingDirection, ResponseChannel.TRANSMITTANCE)
            );
            builder.addRule(
                    incomingDirection,
                    incomingDirection,
                    (input, ignoredIncoming, outgoingDirection) ->
                            transform(level, pos, state, input, incomingDirection, outgoingDirection, ResponseChannel.REFLECTANCE)
            );
        }

        return builder.build();
    }

    public static OpticalResult interact(BlockState state, BeamPacket beam, Direction direction) {
        if (beam.isEmpty()) {
            return OpticalResult.empty();
        }

        return compile(state).interact(beam, direction.getOpposite());
    }

    private static BeamPacket transform(
            Level level,
            BlockPos pos,
            BlockState state,
            BeamPacket beam,
            Direction incomingDirection,
            Direction direction,
            ResponseChannel channel
    ) {
        List<PlaneWaveComponent> components = beam.components().stream()
                .map(component -> transform(level, pos, state, component, incomingDirection, direction, channel))
                .filter(component -> component.power() > 0.0)
                .toList();

        if (components.isEmpty()) {
            return BeamPacket.empty(beam.envelope());
        }

        return new BeamPacket(components, beam.envelope());
    }

    private static PlaneWaveComponent transform(
            Level level,
            BlockPos pos,
            BlockState state,
            PlaneWaveComponent component,
            Direction incomingDirection,
            Direction direction,
            ResponseChannel channel
    ) {
        OpticalMaterialResponse response = responseFor(level, pos, state, incomingDirection, component.frequency());
        double factor = switch (channel) {
            case TRANSMITTANCE -> response.transmittance();
            case REFLECTANCE -> response.reflectance();
        };
        double gainFactor = component.coherence() == CoherenceKind.COHERENT
                ? OpticalMaterialProfiles.gainFactorFor(level, pos, state, component.frequency())
                : 1.0;

        return component.withDirection(direction).withPower(component.power() * factor * gainFactor);
    }

    private static double absorbedPower(Level level, BlockPos pos, BlockState state, BeamPacket beam, Direction incomingDirection) {
        double absorbedPower = 0.0;

        for (PlaneWaveComponent component : beam.components()) {
            absorbedPower += component.power() * responseFor(level, pos, state, incomingDirection, component.frequency()).absorption();
        }

        return absorbedPower;
    }

    private static OpticalMaterialResponse responseFor(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction incomingDirection,
            FrequencyKey frequency
    ) {
        SurfaceProfile incidentSurface = surfaceFor(level, pos, state, incomingDirection);

        if (incidentSurface.treatmentKind() == SurfaceTreatmentKind.NONE || level == null || pos == null) {
            return incidentSurface.materialProfile().responseAt(frequency);
        }

        SurfaceKey neighborKey = new SurfaceKey(pos, incomingDirection).neighborKey();
        SurfaceProfile neighborSurface = OpticalSurfaceResolver.surfaceFor(level, neighborKey);
        EffectiveSurfaceResponse response = OpticalInterfaceResolver.effectiveResponseBetween(
                frequency,
                incidentSurface,
                neighborSurface
        );

        return response.asMaterialResponse();
    }

    private static SurfaceProfile surfaceFor(Level level, BlockPos pos, BlockState state, Direction incomingDirection) {
        if (level == null || pos == null) {
            return SurfaceProfile.rawMaterial(
                    OpticalMaterialProfiles.profileFor(state),
                    io.github.yoglappland.spectralization.optics.medium.OpticalMediumProfiles.profileFor(level, pos, state)
            );
        }

        return OpticalSurfaceResolver.surfaceFor(level, pos, state, incomingDirection);
    }

    private enum ResponseChannel {
        TRANSMITTANCE,
        REFLECTANCE
    }

    private OpticalBlockProperties() {
    }
}
