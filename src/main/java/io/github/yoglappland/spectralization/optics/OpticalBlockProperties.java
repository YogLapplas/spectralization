package io.github.yoglappland.spectralization.optics;

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
        OpticalMaterialProfile profile = OpticalMaterialProfiles.profileFor(level, pos, state);
        double gainFactor = OpticalMaterialProfiles.gainFactorFor(level, pos, state);
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder()
                .absorptionModel((input, incomingDirection, outputs) -> absorbedPower(input, profile));

        for (Direction incomingDirection : Direction.values()) {
            Direction transmittedDirection = incomingDirection.getOpposite();
            builder.addRule(
                    incomingDirection,
                    transmittedDirection,
                    (input, ignoredIncoming, outgoingDirection) ->
                            transform(input, outgoingDirection, profile, ResponseChannel.TRANSMITTANCE, gainFactor)
            );
            builder.addRule(
                    incomingDirection,
                    incomingDirection,
                    (input, ignoredIncoming, outgoingDirection) ->
                            transform(input, outgoingDirection, profile, ResponseChannel.REFLECTANCE, gainFactor)
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
            BeamPacket beam,
            Direction direction,
            OpticalMaterialProfile profile,
            ResponseChannel channel,
            double gainFactor
    ) {
        List<PlaneWaveComponent> components = beam.components().stream()
                .map(component -> transform(component, direction, profile, channel, gainFactor))
                .filter(component -> component.power() > 0.0)
                .toList();

        if (components.isEmpty()) {
            return BeamPacket.empty(beam.envelope());
        }

        return new BeamPacket(components, beam.envelope());
    }

    private static PlaneWaveComponent transform(
            PlaneWaveComponent component,
            Direction direction,
            OpticalMaterialProfile profile,
            ResponseChannel channel,
            double gainFactor
    ) {
        OpticalMaterialResponse response = profile.responseAt(component.frequency());
        double factor = switch (channel) {
            case TRANSMITTANCE -> response.transmittance();
            case REFLECTANCE -> response.reflectance();
        };

        return component.withDirection(direction).withPower(component.power() * factor * gainFactor);
    }

    private static double absorbedPower(BeamPacket beam, OpticalMaterialProfile profile) {
        double absorbedPower = 0.0;

        for (PlaneWaveComponent component : beam.components()) {
            absorbedPower += component.power() * profile.responseAt(component.frequency()).absorption();
        }

        return absorbedPower;
    }

    private enum ResponseChannel {
        TRANSMITTANCE,
        REFLECTANCE
    }

    private OpticalBlockProperties() {
    }
}
