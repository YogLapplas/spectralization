package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

public class AutoCapacitorBlockEntity extends BlockEntity {
    public static final int CAPACITY = 16_000;
    private static final int RELEASE_COOLDOWN_TICKS = 4;
    private static final String ENERGY_TAG = "energy";
    private static final String COOLDOWN_TAG = "cooldown";

    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(
            CAPACITY,
            CAPACITY,
            CAPACITY,
            this::setChanged
    );
    private final IEnergyStorage inputOnlyEnergy = new SidedEnergyView(true, false);
    private final IEnergyStorage outputOnlyEnergy = new SidedEnergyView(false, true);
    private int cooldownTicks = 0;

    public AutoCapacitorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.AUTO_CAPACITOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, AutoCapacitorBlockEntity capacitor) {
        if (level.isClientSide) {
            return;
        }

        capacitor.tickRelease(level, pos);
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        if (side == Direction.UP || side == Direction.DOWN) {
            return inputOnlyEnergy;
        }

        if (side != null) {
            return outputOnlyEnergy;
        }

        return null;
    }

    private void tickRelease(Level level, BlockPos pos) {
        if (cooldownTicks > 0) {
            cooldownTicks--;
            setChanged();
            return;
        }

        if (energy.getEnergyStored() < CAPACITY) {
            return;
        }

        pushStoredEnergy(level, pos);
        cooldownTicks = RELEASE_COOLDOWN_TICKS;
        setChanged();
    }

    private void pushStoredEnergy(Level level, BlockPos pos) {
        List<IEnergyStorage> pumpTargets = new ArrayList<>();
        List<IEnergyStorage> otherTargets = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            if (energy.getEnergyStored() <= 0) {
                return;
            }

            if (direction.getAxis().isVertical()) {
                continue;
            }

            BlockPos targetPos = pos.relative(direction);
            IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    targetPos,
                    direction.getOpposite()
            );

            if (target == null) {
                continue;
            }

            if (target.receiveEnergy(energy.getEnergyStored(), true) <= 0) {
                continue;
            }

            BlockEntity targetBlockEntity = level.getBlockEntity(targetPos);

            if (targetBlockEntity instanceof PumpMagmaBlockEntity
                    || targetBlockEntity instanceof SeedLightBlockEntity) {
                pumpTargets.add(target);
            } else {
                otherTargets.add(target);
            }
        }

        pushToTargets(pumpTargets);
        pushToTargets(otherTargets);
    }

    private void pushToTargets(List<IEnergyStorage> targets) {
        List<IEnergyStorage> activeTargets = new ArrayList<>(targets);

        while (energy.getEnergyStored() > 0 && !activeTargets.isEmpty()) {
            int share = Math.max(1, (energy.getEnergyStored() + activeTargets.size() - 1) / activeTargets.size());
            List<IEnergyStorage> nextTargets = new ArrayList<>();
            int transferredThisRound = 0;

            for (IEnergyStorage target : activeTargets) {
                if (energy.getEnergyStored() <= 0) {
                    break;
                }

                int offered = energy.extractEnergy(Math.min(share, energy.getEnergyStored()), true);
                int accepted = target.receiveEnergy(offered, false);

                if (accepted > 0) {
                    energy.extractEnergy(accepted, false);
                    transferredThisRound += accepted;
                }

                if (energy.getEnergyStored() > 0 && target.receiveEnergy(1, true) > 0) {
                    nextTargets.add(target);
                }
            }

            if (transferredThisRound <= 0) {
                break;
            }

            activeTargets = nextTargets;
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        cooldownTicks = Math.max(0, tag.getInt(COOLDOWN_TAG));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.putInt(COOLDOWN_TAG, cooldownTicks);
    }

    private final class SidedEnergyView implements IEnergyStorage {
        private final boolean input;
        private final boolean output;

        private SidedEnergyView(boolean input, boolean output) {
            this.input = input;
            this.output = output;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return input ? energy.receiveEnergy(maxReceive, simulate) : 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return output ? energy.extractEnergy(maxExtract, simulate) : 0;
        }

        @Override
        public int getEnergyStored() {
            return energy.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return energy.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return output;
        }

        @Override
        public boolean canReceive() {
            return input;
        }
    }
}
