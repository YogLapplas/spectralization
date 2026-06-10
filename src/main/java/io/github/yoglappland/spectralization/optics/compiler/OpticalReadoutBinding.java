package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.cache.ReceiverOutput;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutputKind;
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

    public ReceiverOutput sample(ScalarPowerSolution solution) {
        double totalPower = inputNode == null ? 0.0 : solution.powerAt(inputNode);
        double coherentPower = inputNode == null ? 0.0 : solution.coherentPowerAt(inputNode);

        return switch (kind) {
            case CMOS -> ReceiverOutput.cmos(pos, totalPower);
            case PASS_THROUGH_SENSOR -> ReceiverOutput.passThroughSensor(pos, positiveZ, coherentPower);
            case SPECTROMETER -> ReceiverOutput.spectrometer(
                    pos,
                    inputNode == null ? Map.of() : solution.powerByFrequencyAt(inputNode)
            );
            case BEAM_PROFILER -> ReceiverOutput.beamProfiler(
                    pos,
                    totalPower,
                    coherentPower,
                    Math.max(0.0, totalPower - coherentPower),
                    io.github.yoglappland.spectralization.optics.BeamEnvelope.DEFAULT_COLLIMATED
            );
        };
    }
}
