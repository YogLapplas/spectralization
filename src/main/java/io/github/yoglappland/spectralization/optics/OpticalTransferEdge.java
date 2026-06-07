package io.github.yoglappland.spectralization.optics;

import java.util.Objects;

public record OpticalTransferEdge(
        OpticalPort from,
        OpticalPort to,
        BeamPacket inputBeam,
        BeamPacket outputBeam
) {
    public OpticalTransferEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(inputBeam, "inputBeam");
        Objects.requireNonNull(outputBeam, "outputBeam");
    }
}
