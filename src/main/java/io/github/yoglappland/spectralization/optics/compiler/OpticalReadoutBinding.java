package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.cache.ReceiverOutput;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutputKind;
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
        Objects.requireNonNull(inputNode, "inputNode");
        pos = pos.immutable();
    }

    public ReceiverOutput sample(ScalarPowerSolution solution) {
        double power = solution.powerAt(inputNode);

        if (power <= 0.0) {
            return null;
        }

        return switch (kind) {
            case CMOS -> ReceiverOutput.cmos(pos, power);
            case PASS_THROUGH_SENSOR -> ReceiverOutput.passThroughSensor(pos, positiveZ, power);
        };
    }
}
