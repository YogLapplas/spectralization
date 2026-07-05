package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GainMediumBlockEntity extends BlockEntity {
    private static final double PARAMETER_EPSILON = 1.0E-6;

    private double lastScheduledGain = Double.NaN;
    private double lastSeedPowerPerDirection = Double.NaN;

    public GainMediumBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.GAIN_MEDIUM.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshOutput();

        if (this.level != null && !this.level.isClientSide) {
            OpticalTraceCache.rememberSourceState(this.level, this.worldPosition);
            OpticalTraceCache.requestIntrinsicSourceAt(this.level, this.worldPosition);
        }
    }

    public static boolean refreshNear(LevelAccessor accessor, BlockPos center) {
        if (!(accessor instanceof Level level) || level.isClientSide) {
            return false;
        }

        boolean changed = false;
        changed |= refreshAt(level, center);

        for (Direction direction : Direction.values()) {
            changed |= refreshAt(level, center.relative(direction));
        }

        return changed;
    }

    private static boolean refreshAt(Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof RubyBlockEntity ruby) {
            return ruby.refreshOutput();
        }

        if (blockEntity instanceof GainMediumBlockEntity medium) {
            return medium.refreshOutput();
        }

        return false;
    }

    public boolean refreshOutput() {
        if (this.level == null || this.level.isClientSide) {
            return false;
        }

        BlockState state = this.getBlockState();
        FrequencyKey line = OpticalMaterialProfiles.gainMediumEmissionLine(state);

        if (line == null) {
            return false;
        }

        double scheduledGain = OpticalMaterialProfiles.scheduledCoherentBaseGainFor(
                this.level,
                this.worldPosition,
                state,
                line
        );
        double seedPowerPerDirection = OpticalMaterialProfiles.excitedCoherentSeedPowerPerDirection(
                this.level,
                this.worldPosition,
                state
        );

        if (!closeEnough(scheduledGain, this.lastScheduledGain)
                || !closeEnough(seedPowerPerDirection, this.lastSeedPowerPerDirection)) {
            this.lastScheduledGain = scheduledGain;
            this.lastSeedPowerPerDirection = seedPowerPerDirection;
            this.setChanged();
            OpticalTraceCache.markChanged(this.level, this.worldPosition, OpticalDirtyKind.PARAMETER);
            OpticalTraceCache.markChanged(this.level, this.worldPosition, OpticalDirtyKind.SOURCE);
            OpticalTraceCache.rememberSourceState(this.level, this.worldPosition);
            OpticalTraceCache.requestIntrinsicSourceAt(this.level, this.worldPosition);
            return true;
        }

        return false;
    }

    private static boolean closeEnough(double left, double right) {
        return Double.isFinite(left)
                && Double.isFinite(right)
                && Math.abs(left - right) <= PARAMETER_EPSILON;
    }
}
