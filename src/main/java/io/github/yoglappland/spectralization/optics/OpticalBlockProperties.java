package io.github.yoglappland.spectralization.optics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalBlockProperties {
    public static OpticalResult interact(BlockState state, BeamPacket beam, Direction direction) {
        if (beam.isEmpty()) {
            return OpticalResult.empty();
        }

        OpticalMaterialProfile profile = OpticalMaterialProfiles.profileFor(state);
        BeamPacket transmittedBeam = transform(beam, direction, profile, ResponseChannel.TRANSMITTANCE);
        BeamPacket reflectedBeam = transform(beam, direction.getOpposite(), profile, ResponseChannel.REFLECTANCE);
        double absorbedPower = absorbedPower(beam, profile);
        List<OutputBeam> outputs = new ArrayList<>(2);

        if (!transmittedBeam.isEmpty()) {
            outputs.add(new OutputBeam(direction, transmittedBeam));
        }

        if (!reflectedBeam.isEmpty()) {
            outputs.add(new OutputBeam(direction.getOpposite(), reflectedBeam));
        }

        return new OpticalResult(outputs, absorbedPower, absorbedPower);
    }

    private static BeamPacket transform(
            BeamPacket beam,
            Direction direction,
            OpticalMaterialProfile profile,
            ResponseChannel channel
    ) {
        List<PlaneWaveComponent> components = beam.components().stream()
                .map(component -> transform(component, direction, profile, channel))
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
            ResponseChannel channel
    ) {
        OpticalMaterialResponse response = profile.responseAt(component.frequency());
        double factor = switch (channel) {
            case TRANSMITTANCE -> response.transmittance();
            case REFLECTANCE -> response.reflectance();
        };

        return component.withDirection(direction).withPower(component.power() * factor);
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
