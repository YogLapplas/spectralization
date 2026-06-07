package io.github.yoglappland.spectralization.optics;

import java.util.Objects;

public record OpticalPropagationEdge(
        OpticalPort from,
        OpticalPort to,
        BeamPacket beam
) {
    public OpticalPropagationEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(beam, "beam");
    }
}
