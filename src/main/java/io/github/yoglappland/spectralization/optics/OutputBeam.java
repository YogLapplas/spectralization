package io.github.yoglappland.spectralization.optics;

import java.util.Objects;
import net.minecraft.core.Direction;

public record OutputBeam(Direction outgoingDirection, BeamPacket beam) {
    public OutputBeam {
        Objects.requireNonNull(outgoingDirection, "outgoingDirection");
        Objects.requireNonNull(beam, "beam");
    }
}
