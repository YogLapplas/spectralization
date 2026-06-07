package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CmosSensorBlockEntity extends BlockEntity {
    private static final long SIGNAL_HOLD_TICKS = 1L;
    private static final double POWER_EPSILON = 1.0E-6;

    private long lastReceivedGameTime = Long.MIN_VALUE;
    private double receivedPowerThisTick = 0.0;
    private double lastAppliedPower = 0.0;

    public CmosSensorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.CMOS_SENSOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CmosSensorBlockEntity cmosSensor) {
        if (level.isClientSide || state.getValue(CmosSensorBlock.POWER) == 0) {
            return;
        }

        if (level.getGameTime() - cmosSensor.lastReceivedGameTime > SIGNAL_HOLD_TICKS) {
            CmosSensorBlock.setSignalFromPower(level, pos, state, 0.0);
        }
    }

    public void receivePower(double power) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        long gameTime = this.level.getGameTime();
        if (this.lastReceivedGameTime == gameTime) {
            this.receivedPowerThisTick += power;
        } else {
            this.lastReceivedGameTime = gameTime;
            this.receivedPowerThisTick = power;
        }

        BlockState state = this.level.getBlockState(this.worldPosition);
        int currentSignal = state.getValue(CmosSensorBlock.POWER);
        int nextSignal = CmosSensorBlock.calculateSignal(
                this.receivedPowerThisTick,
                state.getValue(CmosSensorBlock.LOGARITHMIC)
        );

        if (currentSignal != nextSignal) {
            CmosSensorBlock.setSignalFromPower(
                    this.level,
                    this.worldPosition,
                    state,
                    this.receivedPowerThisTick
            );
            this.setChanged();
            this.lastAppliedPower = this.receivedPowerThisTick;
        } else if (Math.abs(this.lastAppliedPower - this.receivedPowerThisTick) > POWER_EPSILON) {
            this.setChanged();
            this.lastAppliedPower = this.receivedPowerThisTick;
        }
    }

    public double getPowerForSignal() {
        if (this.level == null || this.level.getGameTime() - this.lastReceivedGameTime > SIGNAL_HOLD_TICKS) {
            return 0.0;
        }

        return this.receivedPowerThisTick;
    }
}
