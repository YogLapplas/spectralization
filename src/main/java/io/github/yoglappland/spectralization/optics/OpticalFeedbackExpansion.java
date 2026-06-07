package io.github.yoglappland.spectralization.optics;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record OpticalFeedbackExpansion(
        BlockPos pos,
        Direction incomingDirection,
        BeamPacket loopBeam,
        BeamPacket expandedBeam,
        double loopGain
) {
    public OpticalFeedbackExpansion {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(loopBeam, "loopBeam");
        Objects.requireNonNull(expandedBeam, "expandedBeam");

        if (!Double.isFinite(loopGain) || loopGain < 0.0) {
            throw new IllegalArgumentException("Loop gain must be finite and non-negative");
        }

        pos = pos.immutable();
    }

    public OpticalPort inputPort() {
        return new OpticalPort(pos, incomingDirection);
    }
}
