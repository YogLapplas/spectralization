package io.github.yoglappland.spectralization.blockentity;

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
    private static final long SIGNAL_HOLD_TICKS = 1L;
    private static final String POSITIVE_Z_POWER_TAG = "positive_z_power";
    private static final String NEGATIVE_Z_POWER_TAG = "negative_z_power";

    private long lastPositiveZGameTime = Long.MIN_VALUE;
    private long lastNegativeZGameTime = Long.MIN_VALUE;
    private double positiveZPowerThisTick = 0.0;
    private double negativeZPowerThisTick = 0.0;

    public PassThroughSensorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.PASS_THROUGH_SENSOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, PassThroughSensorBlockEntity sensor) {
        boolean changed = false;

        if (sensor.positiveZPowerThisTick > 0.0
                && level.getGameTime() - sensor.lastPositiveZGameTime > SIGNAL_HOLD_TICKS) {
            sensor.positiveZPowerThisTick = 0.0;
            changed = true;
        }

        if (sensor.negativeZPowerThisTick > 0.0
                && level.getGameTime() - sensor.lastNegativeZGameTime > SIGNAL_HOLD_TICKS) {
            sensor.negativeZPowerThisTick = 0.0;
            changed = true;
        }

        if (changed) {
            sensor.syncToClient();
        }
    }

    public void receivePower(boolean positiveZ, double power) {
        if (this.level == null || this.level.isClientSide || power <= 0.0) {
            return;
        }

        long gameTime = this.level.getGameTime();

        if (positiveZ) {
            if (this.lastPositiveZGameTime == gameTime) {
                this.positiveZPowerThisTick += power;
            } else {
                this.lastPositiveZGameTime = gameTime;
                this.positiveZPowerThisTick = power;
            }
        } else {
            if (this.lastNegativeZGameTime == gameTime) {
                this.negativeZPowerThisTick += power;
            } else {
                this.lastNegativeZGameTime = gameTime;
                this.negativeZPowerThisTick = power;
            }
        }

        syncToClient();
    }

    public double getPositiveZPower() {
        return this.positiveZPowerThisTick;
    }

    public double getNegativeZPower() {
        return this.negativeZPowerThisTick;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.positiveZPowerThisTick = tag.getDouble(POSITIVE_Z_POWER_TAG);
        this.negativeZPowerThisTick = tag.getDouble(NEGATIVE_Z_POWER_TAG);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble(POSITIVE_Z_POWER_TAG, this.positiveZPowerThisTick);
        tag.putDouble(NEGATIVE_Z_POWER_TAG, this.negativeZPowerThisTick);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putDouble(POSITIVE_Z_POWER_TAG, this.positiveZPowerThisTick);
        tag.putDouble(NEGATIVE_Z_POWER_TAG, this.negativeZPowerThisTick);
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
}
