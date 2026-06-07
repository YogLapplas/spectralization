package io.github.yoglappland.spectralization.optics;

import java.util.Objects;
import net.minecraft.core.Direction;

public record OpticalScatteringRule(
        Direction incomingDirection,
        Direction outgoingDirection,
        CompiledOpticalNetwork.BeamTransform transform
) {
    public OpticalScatteringRule {
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(outgoingDirection, "outgoingDirection");
        Objects.requireNonNull(transform, "transform");
    }

    public boolean matches(Direction direction) {
        return incomingDirection == direction;
    }

    public OutputBeam apply(BeamPacket input, Direction incomingDirection) {
        BeamPacket outputBeam = transform.transform(input, incomingDirection, outgoingDirection);
        return new OutputBeam(outgoingDirection, outputBeam.withDirection(outgoingDirection));
    }
}
