package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
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

public class PhotonicGradientGeneratorBlockEntity extends BlockEntity {
    public static final int CAPACITY = 1_000;
    private static final int MAX_OUTPUT = 64;
    private static final double FE_PER_LIGHT_LEVEL = 0.1;
    private static final String ENERGY_TAG = "energy";
    private static final String REMAINDER_TAG = "generation_remainder";

    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(CAPACITY, 0, MAX_OUTPUT, this::setChanged);
    private double generationRemainder = 0.0;

    public PhotonicGradientGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.PHOTONIC_GRADIENT_GENERATOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, PhotonicGradientGeneratorBlockEntity generator) {
        if (level.isClientSide) {
            return;
        }

        generator.generate(level, pos);
        generator.pushEnergy(level, pos);
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return side == null || side == Direction.UP ? energy : null;
    }

    private void generate(Level level, BlockPos pos) {
        int north = level.getMaxLocalRawBrightness(pos.relative(Direction.NORTH));
        int south = level.getMaxLocalRawBrightness(pos.relative(Direction.SOUTH));
        int east = level.getMaxLocalRawBrightness(pos.relative(Direction.EAST));
        int west = level.getMaxLocalRawBrightness(pos.relative(Direction.WEST));
        int zGradient = Math.abs(north - south);
        int xGradient = Math.abs(east - west);
        int effectiveGradient = Math.min(zGradient, xGradient);

        if (effectiveGradient <= 0) {
            return;
        }

        generationRemainder += effectiveGradient * FE_PER_LIGHT_LEVEL;
        int generated = (int) Math.floor(generationRemainder);

        if (generated <= 0) {
            return;
        }

        int accepted = energy.addEnergy(generated, false);
        generationRemainder -= accepted;

        if (accepted == 0 && energy.getEnergyStored() >= energy.getMaxEnergyStored()) {
            generationRemainder = Math.min(generationRemainder, 1.0);
        }
    }

    private void pushEnergy(Level level, BlockPos pos) {
        IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos.above(), Direction.DOWN);

        if (target == null || energy.getEnergyStored() <= 0) {
            return;
        }

        int extracted = energy.extractEnergy(MAX_OUTPUT, true);
        int accepted = target.receiveEnergy(extracted, false);

        if (accepted > 0) {
            energy.extractEnergy(accepted, false);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        generationRemainder = tag.getDouble(REMAINDER_TAG);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.putDouble(REMAINDER_TAG, generationRemainder);
    }
}
