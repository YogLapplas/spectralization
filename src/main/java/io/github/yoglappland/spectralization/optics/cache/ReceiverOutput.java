package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.blockentity.BeamProfilerBlockEntity;
import io.github.yoglappland.spectralization.blockentity.CmosSensorBlockEntity;
import io.github.yoglappland.spectralization.blockentity.PassThroughSensorBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometrySample;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileReadoutSample;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public record ReceiverOutput(
        BlockPos pos,
        ReceiverOutputKind kind,
        double power,
        boolean positiveZ,
        double coherentPower,
        double strayPower,
        BeamEnvelope envelope
) {
    public ReceiverOutput {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(envelope, "envelope");
        pos = pos.immutable();

        if (!Double.isFinite(power) || power < 0.0D) {
            throw new IllegalArgumentException("Receiver output power must be finite and non-negative");
        }

        if (!Double.isFinite(coherentPower) || coherentPower < 0.0D) {
            throw new IllegalArgumentException("Receiver output coherent power must be finite and non-negative");
        }

        if (!Double.isFinite(strayPower) || strayPower < 0.0D) {
            throw new IllegalArgumentException("Receiver output stray power must be finite and non-negative");
        }
    }

    public static ReceiverOutput cmos(BlockPos pos, double power) {
        return new ReceiverOutput(pos, ReceiverOutputKind.CMOS, power, false, 0.0, power, BeamEnvelope.DEFAULT_COLLIMATED);
    }

    public static ReceiverOutput passThroughSensor(BlockPos pos, boolean positiveZ, double power) {
        return new ReceiverOutput(pos, ReceiverOutputKind.PASS_THROUGH_SENSOR, power, positiveZ, power, 0.0, BeamEnvelope.DEFAULT_COLLIMATED);
    }

    public static ReceiverOutput beamProfiler(
            BlockPos pos,
            double power,
            double coherentPower,
            double strayPower,
            BeamEnvelope envelope
    ) {
        return new ReceiverOutput(
                pos,
                ReceiverOutputKind.BEAM_PROFILER,
                power,
                false,
                coherentPower,
                strayPower,
                envelope
        );
    }

    public ReceiverOutputKey key() {
        return new ReceiverOutputKey(pos, kind, positiveZ);
    }

    public void apply(Level level, boolean reliable, long step) {
        if (!level.isLoaded(pos)) {
            return;
        }

        ReadoutSample sample = new ReadoutSample(power, reliable, step);

        switch (kind) {
            case CMOS -> {
                if (level.getBlockEntity(pos) instanceof CmosSensorBlockEntity cmosSensor) {
                    cmosSensor.receiveSample(sample);
                }
            }
            case PASS_THROUGH_SENSOR -> {
                if (level.getBlockEntity(pos) instanceof PassThroughSensorBlockEntity passThroughSensor) {
                    passThroughSensor.receiveSample(positiveZ, sample);
                }
            }
            case BEAM_PROFILER -> {
                if (level.getBlockEntity(pos) instanceof BeamProfilerBlockEntity beamProfiler) {
                    beamProfiler.receiveSample(profileSample(reliable, step));
                }
            }
        }
    }

    private BeamProfileReadoutSample profileSample(boolean reliable, long step) {
        BeamGeometrySample geometry = BeamGeometryOps.sample(envelope, power);
        return new BeamProfileReadoutSample(
                power,
                coherentPower,
                strayPower,
                envelope.radius(),
                envelope.divergence(),
                geometry.irradiance(),
                envelope.beamQuality(),
                envelope.scatter(),
                geometry.visualLevel(),
                reliable,
                step
        );
    }

    public record ReceiverOutputKey(BlockPos pos, ReceiverOutputKind kind, boolean positiveZ) {
        public ReceiverOutputKey {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(kind, "kind");
            pos = pos.immutable();
        }
    }
}
