package io.github.yoglappland.spectralization.optics.compiler;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record OpticalComponentPort(
        BlockPos pos,
        OpticalComponentPortKind kind,
        Direction side
) {
    public OpticalComponentPort {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(side, "side");
        pos = pos.immutable();
    }

    public static OpticalComponentPort face(BlockPos pos, Direction side) {
        return new OpticalComponentPort(pos, OpticalComponentPortKind.FACE, side);
    }

    public static OpticalComponentPort axis(BlockPos pos, Direction side) {
        return new OpticalComponentPort(pos, axisKind(side), side);
    }

    public PortGraphNode incomingNode() {
        return new PortGraphNode(pos, side, PortWaveKind.INCOMING);
    }

    public PortGraphNode outgoingNode() {
        return new PortGraphNode(pos, side, PortWaveKind.OUTGOING);
    }

    private static OpticalComponentPortKind axisKind(Direction side) {
        return switch (side.getAxis()) {
            case X -> OpticalComponentPortKind.AXIS_X;
            case Y -> OpticalComponentPortKind.AXIS_Y;
            case Z -> OpticalComponentPortKind.AXIS_Z;
        };
    }
}
