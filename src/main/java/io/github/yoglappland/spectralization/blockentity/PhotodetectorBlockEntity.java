package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.block.PhotodetectorBlock;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PhotodetectorBlockEntity extends BlockEntity {
    private static final long SIGNAL_HOLD_TICKS = 1L;

    private long lastReceivedGameTime = Long.MIN_VALUE;
    private double receivedPowerThisTick = 0.0;

    public PhotodetectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.PHOTODETECTOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PhotodetectorBlockEntity photodetector) {
        if (level.isClientSide || state.getValue(PhotodetectorBlock.POWER) == 0) {
            return;
        }

        if (level.getGameTime() - photodetector.lastReceivedGameTime > SIGNAL_HOLD_TICKS) {
            PhotodetectorBlock.setSignalFromPower(level, pos, state, 0.0);
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

        PhotodetectorBlock.setSignalFromPower(
                this.level,
                this.worldPosition,
                this.level.getBlockState(this.worldPosition),
                this.receivedPowerThisTick
        );
        this.setChanged();
    }

    public double getPowerForSignal() {
        if (this.level == null || this.level.getGameTime() - this.lastReceivedGameTime > SIGNAL_HOLD_TICKS) {
            return 0.0;
        }

        return this.receivedPowerThisTick;
    }
}
