package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.SeedLightBlock;
import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
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

public class SeedLightBlockEntity extends BlockEntity {
    private static final int CYCLE_TICKS = 5;
    private static final SeedTier GLOWSTONE_TIER = new SeedTier(800, 40, 0.06D, 1.0D);
    private static final SeedTier HIGH_DENSITY_GLOWSTONE_TIER = new SeedTier(1_600, 100, 0.10D, 2.5D);
    private static final SeedTier DIODE_TIER = new SeedTier(2_400, 160, 0.0D, 7.0D);
    private static final double SEED_EPSILON = 1.0E-6D;
    private static final String ENERGY_TAG = "energy";
    private static final String TICKS_UNTIL_CYCLE_TAG = "ticks_until_cycle";
    private static final String ACTIVE_TICKS_TAG = "active_ticks";

    private final SpectralEnergyStorage energy;
    private int ticksUntilCycle = 0;
    private int activeTicks = 0;

    public SeedLightBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.SEED_LIGHT_SOURCE.get(), pos, blockState);

        SeedTier tier = tierFor(blockState);
        this.energy = new SpectralEnergyStorage(
                tier.capacity(),
                tier.capacity(),
                0,
                this::setChanged
        );
    }

    public static void tick(Level level, BlockPos pos, SeedLightBlockEntity source) {
        if (level.isClientSide) {
            return;
        }

        source.tickSeedCycle(level, pos);
    }

    public static double seedStrengthFor(Level level, BlockPos pos, BlockState state) {
        if (level != null
                && pos != null
                && level.getBlockEntity(pos) instanceof SeedLightBlockEntity source) {
            return source.seedStrength();
        }

        return passiveSeedStrengthFor(state);
    }

    public static double passiveSeedStrengthFor(BlockState state) {
        return tierFor(state).passiveStrength();
    }

    public double seedStrength() {
        SeedTier tier = tierFor(getBlockState());
        return activeTicks > 0 ? tier.activeStrength() : tier.passiveStrength();
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energy;
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (this.level != null && !this.level.isClientSide) {
            setActive(this.level, this.worldPosition, activeTicks > 0);
            GainMediumBlockEntity.refreshNear(this.level, this.worldPosition);
        }
    }

    private void tickSeedCycle(Level level, BlockPos pos) {
        double previousStrength = seedStrength();
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

        notifySeedStrengthChanged(level, pos, previousStrength);
    }

    private void runCycle(Level level, BlockPos pos) {
        SeedTier tier = tierFor(getBlockState());

        if (hasAdjacentGainMedium(level, pos) && energy.getEnergyStored() >= tier.fePerCycle()) {
            energy.extractEnergy(tier.fePerCycle(), false);
            activeTicks = CYCLE_TICKS;
            setActive(level, pos, true);
            return;
        }

        activeTicks = 0;
        setActive(level, pos, false);
    }

    private void notifySeedStrengthChanged(Level level, BlockPos pos, double previousStrength) {
        if (Math.abs(seedStrength() - previousStrength) > SEED_EPSILON) {
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

        if (state.getBlock() instanceof SeedLightBlock
                && state.hasProperty(SeedLightBlock.ACTIVE)
                && state.getValue(SeedLightBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(SeedLightBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        ticksUntilCycle = Math.max(0, tag.getInt(TICKS_UNTIL_CYCLE_TAG));
        activeTicks = Math.max(0, tag.getInt(ACTIVE_TICKS_TAG));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.putInt(TICKS_UNTIL_CYCLE_TAG, ticksUntilCycle);
        tag.putInt(ACTIVE_TICKS_TAG, activeTicks);
    }

    private static SeedTier tierFor(BlockState state) {
        Block block = state.getBlock();

        if (block == Spectralization.HIGH_DENSITY_LIGHT_SEED_GLOWSTONE_BLOCK.get()) {
            return HIGH_DENSITY_GLOWSTONE_TIER;
        }

        if (block == Spectralization.DIODE_LIGHT_SEED.get()) {
            return DIODE_TIER;
        }

        return GLOWSTONE_TIER;
    }

    private record SeedTier(
            int capacity,
            int fePerCycle,
            double passiveStrength,
            double activeStrength
    ) {
    }
}
