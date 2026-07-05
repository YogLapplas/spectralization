package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.PumpMagmaBlock;
import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.optics.pump.OpticalPumpSource;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class PumpMagmaBlockEntity extends BlockEntity implements OpticalPumpSource {
    public static final int CAPACITY = 480_000;
    private static final int CYCLE_TICKS = 5;
    private static final PumpTier MAGMA_TIER = new PumpTier(16_000, 8_000.0D, 1.0D);
    private static final PumpTier HIGH_DENSITY_MAGMA_TIER = new PumpTier(72_000, 16_000.0D, 4.5D);
    private static final PumpTier DIODE_TIER = new PumpTier(480_000, 34_286.0D, 14.0D);
    private static final double PUMP_EPSILON = 1.0E-6D;
    private static final String ENERGY_TAG = "energy";
    private static final String TICKS_UNTIL_CYCLE_TAG = "ticks_until_cycle";
    private static final String ACTIVE_TICKS_TAG = "active_ticks";
    private static final String PUMP_AMOUNT_TAG = "pump_amount";

    private final SpectralEnergyStorage energy;
    private int ticksUntilCycle = 0;
    private int activeTicks = 0;
    private double pumpAmount = 0.0D;

    public PumpMagmaBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.PUMP_MAGMA_BLOCK.get(), pos, blockState);

        PumpTier tier = tierFor(blockState);
        this.energy = new SpectralEnergyStorage(
                tier.capacity(),
                tier.capacity(),
                0,
                this::setChanged
        );
    }

    public static void tick(Level level, BlockPos pos, PumpMagmaBlockEntity pump) {
        if (level.isClientSide) {
            return;
        }

        pump.tickPumpCycle(level, pos);
    }

    @Override
    public double pumpAmount() {
        return pumpAmount;
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energy;
    }

    private void tickPumpCycle(Level level, BlockPos pos) {
        boolean ranCycle = false;

        if (ticksUntilCycle > 0) {
            ticksUntilCycle--;
            setChanged();
        }

        if (ticksUntilCycle <= 0) {
            runCycle(level, pos);
            ticksUntilCycle = CYCLE_TICKS;
            ranCycle = true;
            setChanged();
        }

        if (!ranCycle && activeTicks > 0) {
            activeTicks--;

            if (activeTicks <= 0) {
                setActive(level, pos, false);
            }

            setChanged();
        }
    }

    private void runCycle(Level level, BlockPos pos) {
        double previousPumpAmount = pumpAmount;
        int consumed = 0;

        if (hasAdjacentGainMedium(level, pos)) {
            PumpTier tier = tierFor(getBlockState());
            double pumpUnits = Math.min(tier.maxPumpAmount(), energy.getEnergyStored() / tier.fePerPumpUnit());
            consumed = Math.min(energy.getEnergyStored(), (int) Math.ceil(pumpUnits * tier.fePerPumpUnit()));

            if (consumed > 0) {
                energy.setEnergyStored(energy.getEnergyStored() - consumed);
            }

            pumpAmount = pumpUnits;
        } else {
            pumpAmount = 0.0D;
        }

        if (pumpAmount > 0.0D) {
            activeTicks = CYCLE_TICKS;
            setActive(level, pos, true);
        } else {
            activeTicks = 0;
            setActive(level, pos, false);
        }

        if (Math.abs(pumpAmount - previousPumpAmount) > PUMP_EPSILON) {
            GainMediumBlockEntity.refreshNear(level, pos);
        }
    }

    private static boolean hasAdjacentGainMedium(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);

            if (level.isLoaded(neighborPos) && level.getBlockState(neighborPos).is(SpectralBlockTags.LASER_GAIN_MEDIUM)) {
                return true;
            }
        }

        return false;
    }

    private void setActive(Level level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof PumpMagmaBlock && state.getValue(PumpMagmaBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(PumpMagmaBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        ticksUntilCycle = Math.max(0, tag.getInt(TICKS_UNTIL_CYCLE_TAG));
        activeTicks = Math.max(0, tag.getInt(ACTIVE_TICKS_TAG));
        pumpAmount = Math.max(0.0D, tag.getDouble(PUMP_AMOUNT_TAG));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.putInt(TICKS_UNTIL_CYCLE_TAG, ticksUntilCycle);
        tag.putInt(ACTIVE_TICKS_TAG, activeTicks);
        tag.putDouble(PUMP_AMOUNT_TAG, pumpAmount);
    }

    private static PumpTier tierFor(BlockState state) {
        Block block = state.getBlock();

        if (block == Spectralization.HIGH_DENSITY_PUMP_MAGMA_BLOCK.get()) {
            return HIGH_DENSITY_MAGMA_TIER;
        }

        if (block == Spectralization.DIODE_PUMP.get()) {
            return DIODE_TIER;
        }

        return MAGMA_TIER;
    }

    private record PumpTier(int capacity, double fePerPumpUnit, double maxPumpAmount) {
    }
}
