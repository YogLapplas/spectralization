package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.blockentity.CmosSensorBlockEntity;
import io.github.yoglappland.spectralization.blockentity.PassThroughSensorBlockEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record ReceiverOutput(
        BlockPos pos,
        ReceiverOutputKind kind,
        double power,
        boolean positiveZ
) {
    public ReceiverOutput {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(kind, "kind");
        pos = pos.immutable();

        if (!Double.isFinite(power) || power < 0.0D) {
            throw new IllegalArgumentException("Receiver output power must be finite and non-negative");
        }
    }

    public static ReceiverOutput cmos(BlockPos pos, double power) {
        return new ReceiverOutput(pos, ReceiverOutputKind.CMOS, power, false);
    }

    public static ReceiverOutput passThroughSensor(BlockPos pos, boolean positiveZ, double power) {
        return new ReceiverOutput(pos, ReceiverOutputKind.PASS_THROUGH_SENSOR, power, positiveZ);
    }

    public void apply(Level level) {
        if (power <= 0.0D || !level.isLoaded(pos)) {
            return;
        }

        switch (kind) {
            case CMOS -> {
                if (level.getBlockEntity(pos) instanceof CmosSensorBlockEntity cmosSensor) {
                    cmosSensor.receivePower(power);
                }
            }
            case PASS_THROUGH_SENSOR -> {
                if (level.getBlockEntity(pos) instanceof PassThroughSensorBlockEntity passThroughSensor) {
                    passThroughSensor.receivePower(positiveZ, power);
                }
            }
        }
    }
}
