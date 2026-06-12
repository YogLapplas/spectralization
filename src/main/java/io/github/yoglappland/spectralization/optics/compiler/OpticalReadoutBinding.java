package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.cache.ReceiverOutput;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutputKind;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record OpticalReadoutBinding(
        BlockPos pos,
        ReceiverOutputKind kind,
        PortGraphNode inputNode,
        boolean positiveZ
) {
    public OpticalReadoutBinding {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(kind, "kind");
        pos = pos.immutable();
    }

    public ReceiverOutput sample(ScalarPowerSolution solution, CompiledBeamProfileLayer beamProfileLayer) {
        Objects.requireNonNull(beamProfileLayer, "beamProfileLayer");

        double totalPower = inputNode == null ? 0.0 : solution.powerAt(inputNode);
        double coherentPower = inputNode == null ? 0.0 : solution.coherentPowerAt(inputNode);
        BeamEnvelope envelope = beamProfileLayer.envelopeAt(inputNode, solution);

        return switch (kind) {
            case CMOS -> ReceiverOutput.cmos(pos, totalPower);
            case PASS_THROUGH_SENSOR -> ReceiverOutput.passThroughSensor(pos, positiveZ, coherentPower);
            case SPECTROMETER -> ReceiverOutput.spectrometer(
                    pos,
                    inputNode == null ? Map.of() : solution.powerByFrequencyAt(inputNode)
            );
            case PHOTOTHERMAL_GENERATOR -> ReceiverOutput.photothermalGenerator(
                    pos,
                    totalPower,
                    coherentPower,
                    Math.max(0.0, totalPower - coherentPower),
                    envelope,
                    inputNode == null ? Map.of() : solution.powerByFrequencyAt(inputNode)
            );
            case BEAM_PROFILER -> ReceiverOutput.beamProfiler(
                    pos,
                    totalPower,
                    coherentPower,
                    Math.max(0.0, totalPower - coherentPower),
                    envelope
            );
        };
    }
}
