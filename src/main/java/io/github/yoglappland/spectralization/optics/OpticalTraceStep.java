package io.github.yoglappland.spectralization.optics;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record OpticalTraceStep(
        BlockPos pos,
        Direction travelDirection,
        Direction incomingDirection,
        OpticalInteractionKind interactionKind,
        BeamPacket incidentBeam,
        BeamPacket interactingBeam,
        OpticalResult result
) {
    public OpticalTraceStep {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(travelDirection, "travelDirection");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(interactionKind, "interactionKind");
        Objects.requireNonNull(incidentBeam, "incidentBeam");
        Objects.requireNonNull(interactingBeam, "interactingBeam");
        Objects.requireNonNull(result, "result");

        pos = pos.immutable();
    }

    public OpticalPort inputPort() {
        return new OpticalPort(pos, incomingDirection);
    }

    public OpticalPropagationEdge propagationEdge() {
        BlockPos fromPos = pos.relative(travelDirection.getOpposite());
        return new OpticalPropagationEdge(
                new OpticalPort(fromPos, travelDirection),
                inputPort(),
                incidentBeam
        );
    }

    public List<OpticalTransferEdge> transferEdges() {
        OpticalPort inputPort = inputPort();

        return result.outputs().stream()
                .map(output -> new OpticalTransferEdge(
                        inputPort,
                        new OpticalPort(pos, output.outgoingDirection()),
                        interactingBeam,
                        output.beam()
                ))
                .toList();
    }
}
