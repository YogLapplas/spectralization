package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.geometry.BeamProfileReadoutSample;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BeamProfilerBlockEntity extends BlockEntity {
    private static final long SAMPLE_HOLD_TICKS = 1L;
    private static final double EPSILON = 1.0E-6;
    private static final String POWER_TAG = "power";
    private static final String COHERENT_POWER_TAG = "coherent_power";
    private static final String STRAY_POWER_TAG = "stray_power";
    private static final String RADIUS_TAG = "radius";
    private static final String DIVERGENCE_TAG = "divergence";
    private static final String IRRADIANCE_TAG = "irradiance";
    private static final String BEAM_QUALITY_TAG = "beam_quality";
    private static final String SCATTER_TAG = "scatter";
    private static final String VISUAL_LEVEL_TAG = "visual_level";
    private static final String RELIABLE_TAG = "reliable";

    private long lastReceivedGameTime = Long.MIN_VALUE;
    private long lastReceivedSampleStep = Long.MIN_VALUE;
    private long lastObservedSampleStep = Long.MIN_VALUE;
    private BeamProfileReadoutSample receivedSample = BeamProfileReadoutSample.zero(true, Long.MIN_VALUE);
    private BeamProfileReadoutSample committedSample = BeamProfileReadoutSample.zero(true, Long.MIN_VALUE);
    private boolean reliable = true;

    public BeamProfilerBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.BEAM_PROFILER.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BeamProfilerBlockEntity profiler) {
        if (level.isClientSide) {
            return;
        }

        if (profiler.tickSample(level)) {
            profiler.syncToClient();
        }
    }

    public void receiveSample(BeamProfileReadoutSample sample) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        this.lastReceivedGameTime = this.level.getGameTime();

        if (this.lastReceivedSampleStep == sample.step()) {
            this.receivedSample = combine(this.receivedSample, sample);
        } else {
            this.lastReceivedSampleStep = sample.step();
            this.receivedSample = sample;
        }
    }

    public BeamProfileReadoutSample sample() {
        return this.committedSample;
    }

    public boolean isOutputReliable() {
        return this.reliable;
    }

    private boolean tickSample(Level level) {
        if (level.getGameTime() - this.lastReceivedGameTime > SAMPLE_HOLD_TICKS) {
            return commit(BeamProfileReadoutSample.zero(true, level.getGameTime()));
        }

        if (this.lastReceivedSampleStep == this.lastObservedSampleStep) {
            return false;
        }

        this.lastObservedSampleStep = this.lastReceivedSampleStep;

        if (!this.receivedSample.reliable()) {
            return markUnreliable();
        }

        return commit(this.receivedSample);
    }

    private boolean commit(BeamProfileReadoutSample sample) {
        boolean changed = !this.reliable || !closeEnough(this.committedSample, sample);
        this.committedSample = sample;
        this.reliable = true;

        if (changed) {
            this.setChanged();
        }

        return changed;
    }

    private boolean markUnreliable() {
        if (!this.reliable) {
            return false;
        }

        this.reliable = false;
        this.committedSample = new BeamProfileReadoutSample(
                committedSample.power(),
                committedSample.coherentPower(),
                committedSample.strayPower(),
                committedSample.radius(),
                committedSample.divergence(),
                committedSample.irradiance(),
                committedSample.beamQuality(),
                committedSample.scatter(),
                committedSample.visualLevel(),
                false,
                committedSample.step()
        );
        this.setChanged();
        return true;
    }

    private static BeamProfileReadoutSample combine(BeamProfileReadoutSample left, BeamProfileReadoutSample right) {
        double totalPower = left.power() + right.power();

        if (totalPower <= 0.0) {
            return new BeamProfileReadoutSample(
                    0.0,
                    0.0,
                    0.0,
                    Math.max(left.radius(), right.radius()),
                    Math.max(left.divergence(), right.divergence()),
                    0.0,
                    Math.max(left.beamQuality(), right.beamQuality()),
                    Math.max(left.scatter(), right.scatter()),
                    Math.max(left.visualLevel(), right.visualLevel()),
                    left.reliable() && right.reliable(),
                    Math.max(left.step(), right.step())
            );
        }

        double leftWeight = left.power() / totalPower;
        double rightWeight = right.power() / totalPower;

        return new BeamProfileReadoutSample(
                totalPower,
                left.coherentPower() + right.coherentPower(),
                left.strayPower() + right.strayPower(),
                left.radius() * leftWeight + right.radius() * rightWeight,
                left.divergence() * leftWeight + right.divergence() * rightWeight,
                left.irradiance() + right.irradiance(),
                left.beamQuality() * leftWeight + right.beamQuality() * rightWeight,
                left.scatter() * leftWeight + right.scatter() * rightWeight,
                Math.max(left.visualLevel(), right.visualLevel()),
                left.reliable() && right.reliable(),
                Math.max(left.step(), right.step())
        );
    }

    private static boolean closeEnough(BeamProfileReadoutSample left, BeamProfileReadoutSample right) {
        return closeEnough(left.power(), right.power())
                && closeEnough(left.coherentPower(), right.coherentPower())
                && closeEnough(left.strayPower(), right.strayPower())
                && closeEnough(left.radius(), right.radius())
                && closeEnough(left.divergence(), right.divergence())
                && closeEnough(left.irradiance(), right.irradiance())
                && closeEnough(left.beamQuality(), right.beamQuality())
                && closeEnough(left.scatter(), right.scatter())
                && left.visualLevel() == right.visualLevel()
                && left.reliable() == right.reliable();
    }

    private static boolean closeEnough(double left, double right) {
        return Math.abs(left - right) <= Math.max(EPSILON, Math.max(Math.abs(left), Math.abs(right)) * 1.0E-4);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.reliable = !tag.contains(RELIABLE_TAG) || tag.getBoolean(RELIABLE_TAG);
        this.committedSample = readSample(tag, this.reliable, this.level == null ? 0L : this.level.getGameTime());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeSample(tag, this.committedSample);
        tag.putBoolean(RELIABLE_TAG, this.reliable);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeSample(tag, this.committedSample);
        tag.putBoolean(RELIABLE_TAG, this.reliable);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncToClient() {
        if (this.level == null) {
            return;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
    }

    private static BeamProfileReadoutSample readSample(CompoundTag tag, boolean reliable, long step) {
        return new BeamProfileReadoutSample(
                tag.getDouble(POWER_TAG),
                tag.getDouble(COHERENT_POWER_TAG),
                tag.getDouble(STRAY_POWER_TAG),
                tag.getDouble(RADIUS_TAG),
                tag.getDouble(DIVERGENCE_TAG),
                tag.getDouble(IRRADIANCE_TAG),
                tag.contains(BEAM_QUALITY_TAG) ? tag.getDouble(BEAM_QUALITY_TAG) : 1.0,
                tag.getDouble(SCATTER_TAG),
                tag.getInt(VISUAL_LEVEL_TAG),
                reliable,
                step
        );
    }

    private static void writeSample(CompoundTag tag, BeamProfileReadoutSample sample) {
        tag.putDouble(POWER_TAG, sample.power());
        tag.putDouble(COHERENT_POWER_TAG, sample.coherentPower());
        tag.putDouble(STRAY_POWER_TAG, sample.strayPower());
        tag.putDouble(RADIUS_TAG, sample.radius());
        tag.putDouble(DIVERGENCE_TAG, sample.divergence());
        tag.putDouble(IRRADIANCE_TAG, sample.irradiance());
        tag.putDouble(BEAM_QUALITY_TAG, sample.beamQuality());
        tag.putDouble(SCATTER_TAG, sample.scatter());
        tag.putInt(VISUAL_LEVEL_TAG, sample.visualLevel());
    }
}
