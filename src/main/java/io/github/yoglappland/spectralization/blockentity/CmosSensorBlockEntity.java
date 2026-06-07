package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.optics.cache.ReadoutSample;
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

public class CmosSensorBlockEntity extends BlockEntity {
    private static final long SAMPLE_HOLD_TICKS = 1L;
    private static final int REQUIRED_STABLE_STEPS = 8;
    private static final double POWER_EPSILON = 1.0E-6;
    private static final String COMMITTED_POWER_TAG = "committed_power";
    private static final String RELIABLE_TAG = "reliable";

    private long lastReceivedGameTime = Long.MIN_VALUE;
    private long lastReceivedSampleStep = Long.MIN_VALUE;
    private long lastObservedSampleStep = Long.MIN_VALUE;
    private double receivedPowerThisStep = 0.0;
    private boolean receivedReliableThisStep = false;
    private double committedPower = 0.0;
    private double candidatePower = 0.0;
    private int stableCandidateSteps = 0;
    private boolean reliable = false;

    public CmosSensorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.CMOS_SENSOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CmosSensorBlockEntity cmosSensor) {
        if (level.isClientSide) {
            return;
        }

        if (cmosSensor.tickSample(level, pos, state)) {
            cmosSensor.syncToClient();
        }
    }

    public void receivePower(double power) {
        if (this.level == null) {
            return;
        }

        receiveSample(new ReadoutSample(power, true, this.level.getGameTime()));
    }

    public void receiveSample(ReadoutSample sample) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        long gameTime = this.level.getGameTime();
        this.lastReceivedGameTime = gameTime;

        if (this.lastReceivedSampleStep == sample.step()) {
            this.receivedPowerThisStep += sample.power();
            this.receivedReliableThisStep &= sample.reliable();
        } else {
            this.lastReceivedSampleStep = sample.step();
            this.receivedPowerThisStep = sample.power();
            this.receivedReliableThisStep = sample.reliable();
        }
    }

    public double getPowerForSignal() {
        return this.committedPower;
    }

    public boolean isOutputReliable() {
        return this.reliable;
    }

    private boolean tickSample(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() - this.lastReceivedGameTime > SAMPLE_HOLD_TICKS) {
            return markUnreliable();
        }

        if (this.lastReceivedSampleStep == this.lastObservedSampleStep) {
            return false;
        }

        this.lastObservedSampleStep = this.lastReceivedSampleStep;

        if (!this.receivedReliableThisStep) {
            return markUnreliable();
        }

        return observeReliablePower(level, pos, state, this.receivedPowerThisStep);
    }

    private boolean observeReliablePower(Level level, BlockPos pos, BlockState state, double observedPower) {
        int observedSignal = CmosSensorBlock.calculateSignal(
                observedPower,
                state.getValue(CmosSensorBlock.LOGARITHMIC)
        );

        if (closeEnough(observedPower, this.candidatePower)) {
            this.stableCandidateSteps++;
            this.candidatePower = observedPower;
        } else {
            this.candidatePower = observedPower;
            this.stableCandidateSteps = 1;
        }

        boolean changed = false;

        if (this.stableCandidateSteps >= REQUIRED_STABLE_STEPS) {
            if (state.getValue(CmosSensorBlock.POWER) != observedSignal) {
                CmosSensorBlock.setSignalFromPower(level, pos, state, this.candidatePower);
                changed = true;
            }

            if (!this.reliable || !closeEnough(this.committedPower, this.candidatePower)) {
                this.committedPower = this.candidatePower;
                this.reliable = true;
                changed = true;
            }
        } else if (this.reliable && !closeEnough(observedPower, this.committedPower)) {
            this.reliable = false;
            changed = true;
        }

        if (changed) {
            this.setChanged();
        }

        return changed;
    }

    private boolean markUnreliable() {
        this.stableCandidateSteps = 0;

        if (!this.reliable) {
            return false;
        }

        this.reliable = false;
        this.setChanged();
        return true;
    }

    private static boolean closeEnough(double left, double right) {
        return Math.abs(left - right) <= Math.max(POWER_EPSILON, Math.max(Math.abs(left), Math.abs(right)) * 1.0E-4);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.committedPower = tag.getDouble(COMMITTED_POWER_TAG);
        this.candidatePower = this.committedPower;
        this.reliable = tag.getBoolean(RELIABLE_TAG);
        this.stableCandidateSteps = this.reliable ? REQUIRED_STABLE_STEPS : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(COMMITTED_POWER_TAG, this.committedPower);
        tag.putBoolean(RELIABLE_TAG, this.reliable);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putDouble(COMMITTED_POWER_TAG, this.committedPower);
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
}
