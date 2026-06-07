package io.github.yoglappland.spectralization.optics;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record OpticalTraceTermination(
        BlockPos pos,
        Direction travelDirection,
        Direction incomingDirection,
        BeamPacket beam,
        OpticalTraceTerminationReason reason
) {
    public OpticalTraceTermination {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(travelDirection, "travelDirection");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(beam, "beam");
        Objects.requireNonNull(reason, "reason");

        pos = pos.immutable();
    }

    public OpticalPort inputPort() {
        return new OpticalPort(pos, incomingDirection);
    }
}
