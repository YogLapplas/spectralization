package io.github.yoglappland.spectralization.blockentity;

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
    public static final int CAPACITY = 16_000;
    private static final int CYCLE_TICKS = 5;
    private static final int FE_PER_PUMP_UNIT = 8_000;
    private static final int MAX_PUMP_AMOUNT = 1;
    private static final int ACTIVATION_THRESHOLD_FE = 8_000;
    private static final String ENERGY_TAG = "energy";
    private static final String TICKS_UNTIL_CYCLE_TAG = "ticks_until_cycle";
    private static final String ACTIVE_TICKS_TAG = "active_ticks";
    private static final String PUMP_AMOUNT_TAG = "pump_amount";

    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(
            CAPACITY,
            CAPACITY,
            0,
            this::setChanged
    );
    private int ticksUntilCycle = 0;
    private int activeTicks = 0;
    private int pumpAmount = 0;

    public PumpMagmaBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.PUMP_MAGMA_BLOCK.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, PumpMagmaBlockEntity pump) {
        if (level.isClientSide) {
            return;
        }

        pump.tickPumpCycle(level, pos);
    }

    @Override
    public int pumpAmount() {
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
        int previousPumpAmount = pumpAmount;
        int consumed = 0;

        if (hasAdjacentGainMedium(level, pos)) {
            int pumpUnits = Math.min(MAX_PUMP_AMOUNT, energy.getEnergyStored() / FE_PER_PUMP_UNIT);
            consumed = pumpUnits * FE_PER_PUMP_UNIT;

            if (consumed > 0) {
                energy.setEnergyStored(energy.getEnergyStored() - consumed);
            }

            pumpAmount = pumpUnits;
        } else {
            pumpAmount = 0;
        }

        if (consumed >= ACTIVATION_THRESHOLD_FE) {
            activeTicks = CYCLE_TICKS;
            setActive(level, pos, true);
        } else {
            activeTicks = 0;
            setActive(level, pos, false);
        }

        if (pumpAmount != previousPumpAmount) {
            RubyBlockEntity.refreshNear(level, pos);
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
        pumpAmount = Math.max(0, tag.getInt(PUMP_AMOUNT_TAG));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.putInt(TICKS_UNTIL_CYCLE_TAG, ticksUntilCycle);
        tag.putInt(ACTIVE_TICKS_TAG, activeTicks);
        tag.putInt(PUMP_AMOUNT_TAG, pumpAmount);
    }
}
