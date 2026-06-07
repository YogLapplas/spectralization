package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.OpticalPort;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record PortGraphNode(BlockPos pos, Direction side, PortWaveKind waveKind) {
    public PortGraphNode {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(waveKind, "waveKind");

        pos = pos.immutable();
    }

    public static PortGraphNode incoming(OpticalPort port) {
        return new PortGraphNode(port.pos(), port.side(), PortWaveKind.INCOMING);
    }

    public static PortGraphNode outgoing(OpticalPort port) {
        return new PortGraphNode(port.pos(), port.side(), PortWaveKind.OUTGOING);
    }

    public OpticalPort physicalPort() {
        return new OpticalPort(pos, side);
    }
}
