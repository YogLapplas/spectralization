package io.github.yoglappland.spectralization.blockentity;

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

public class PassThroughSensorBlockEntity extends BlockEntity {
    private static final long SAMPLE_HOLD_TICKS = 1L;
    private static final double POWER_EPSILON = 1.0E-6;
    private static final String POSITIVE_Z_POWER_TAG = "positive_z_power";
    private static final String NEGATIVE_Z_POWER_TAG = "negative_z_power";
    private static final String POSITIVE_Z_RELIABLE_TAG = "positive_z_reliable";
    private static final String NEGATIVE_Z_RELIABLE_TAG = "negative_z_reliable";

    private final StablePowerChannel positiveZChannel = new StablePowerChannel();
    private final StablePowerChannel negativeZChannel = new StablePowerChannel();

    public PassThroughSensorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.PASS_THROUGH_SENSOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, PassThroughSensorBlockEntity sensor) {
        boolean needsSync = sensor.positiveZChannel.tick(level) | sensor.negativeZChannel.tick(level);

        if (needsSync) {
            sensor.syncToClient();
        }
    }

    public void receivePower(boolean positiveZ, double power) {
        if (this.level == null) {
            return;
        }

        receiveSample(positiveZ, new ReadoutSample(power, true, this.level.getGameTime()));
    }

    public void receiveSample(boolean positiveZ, ReadoutSample sample) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        long gameTime = this.level.getGameTime();

        if (positiveZ) {
            this.positiveZChannel.receive(gameTime, sample);
        } else {
            this.negativeZChannel.receive(gameTime, sample);
        }
    }

    public double getPositiveZPower() {
        return this.positiveZChannel.committedPower();
    }

    public double getNegativeZPower() {
        return this.negativeZChannel.committedPower();
    }

    public boolean isPositiveZReliable() {
        return this.positiveZChannel.reliable();
    }

    public boolean isNegativeZReliable() {
        return this.negativeZChannel.reliable();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.positiveZChannel.load(tag, POSITIVE_Z_POWER_TAG, POSITIVE_Z_RELIABLE_TAG);
        this.negativeZChannel.load(tag, NEGATIVE_Z_POWER_TAG, NEGATIVE_Z_RELIABLE_TAG);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.positiveZChannel.save(tag, POSITIVE_Z_POWER_TAG, POSITIVE_Z_RELIABLE_TAG);
        this.negativeZChannel.save(tag, NEGATIVE_Z_POWER_TAG, NEGATIVE_Z_RELIABLE_TAG);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        this.positiveZChannel.save(tag, POSITIVE_Z_POWER_TAG, POSITIVE_Z_RELIABLE_TAG);
        this.negativeZChannel.save(tag, NEGATIVE_Z_POWER_TAG, NEGATIVE_Z_RELIABLE_TAG);
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

        setChanged();
        BlockState state = this.level.getBlockState(this.worldPosition);
        this.level.sendBlockUpdated(this.worldPosition, state, state, 3);
    }

    private static boolean closeEnough(double left, double right) {
        return Math.abs(left - right) <= Math.max(POWER_EPSILON, Math.max(Math.abs(left), Math.abs(right)) * 1.0E-4);
    }

    private static final class StablePowerChannel {
        private long lastReceivedGameTime = Long.MIN_VALUE;
        private long lastReceivedSampleStep = Long.MIN_VALUE;
        private long lastObservedSampleStep = Long.MIN_VALUE;
        private double receivedPowerThisStep = 0.0;
        private boolean receivedReliableThisStep = false;
        private double committedPower = 0.0;
        private boolean reliable = true;

        void receive(long gameTime, ReadoutSample sample) {
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

        boolean tick(Level level) {
            if (level.getGameTime() - this.lastReceivedGameTime > SAMPLE_HOLD_TICKS) {
                return commitReliablePower(0.0);
            }

            if (this.lastReceivedSampleStep == this.lastObservedSampleStep) {
                return false;
            }

            this.lastObservedSampleStep = this.lastReceivedSampleStep;

            if (!this.receivedReliableThisStep) {
                return markUnreliable();
            }

            double observedPower = this.receivedPowerThisStep;

            return commitReliablePower(observedPower);
        }

        private boolean commitReliablePower(double observedPower) {
            boolean changed = !this.reliable || !closeEnough(this.committedPower, observedPower);
            this.committedPower = observedPower;
            this.reliable = true;
            return changed;
        }

        private boolean markUnreliable() {
            if (!this.reliable) {
                return false;
            }

            this.reliable = false;
            return true;
        }

        double committedPower() {
            return this.committedPower;
        }

        boolean reliable() {
            return this.reliable;
        }

        void load(CompoundTag tag, String powerTag, String reliableTag) {
            this.committedPower = tag.getDouble(powerTag);
            this.reliable = !tag.contains(reliableTag) || tag.getBoolean(reliableTag);
        }

        void save(CompoundTag tag, String powerTag, String reliableTag) {
            tag.putDouble(powerTag, this.committedPower);
            tag.putBoolean(reliableTag, this.reliable);
        }
    }
}
