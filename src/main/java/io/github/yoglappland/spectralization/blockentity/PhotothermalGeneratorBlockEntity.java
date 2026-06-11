package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.optics.cache.ReadoutSample;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PhotothermalGeneratorBlockEntity extends BlockEntity {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_BURN_REMAINING = 2;
    public static final int DATA_BURN_DURATION = 3;
    public static final int DATA_OUTPUT = 4;
    public static final int DATA_FUEL_COUNT = 5;
    public static final int DATA_COUNT = 6;
    public static final int CAPACITY = 10_000;

    private static final int BASE_FE_PER_TICK = 10;
    private static final int MAX_OUTPUT = 256;
    private static final long SAMPLE_HOLD_TICKS = 1L;
    private static final double FULL_OPTICAL_BOOST_POWER = 20.0;
    private static final double MAX_OUTPUT_MULTIPLIER = 5.0;
    private static final double MIN_BURN_DURATION_MULTIPLIER = 0.5;
    private static final String ENERGY_TAG = "energy";
    private static final String FUEL_TAG = "fuel";
    private static final String BURN_REMAINING_TAG = "burn_remaining";
    private static final String BURN_DURATION_TAG = "burn_duration";
    private static final String GENERATION_REMAINDER_TAG = "generation_remainder";
    private static final String OPTICAL_POWER_TAG = "optical_power";

    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(CAPACITY, 0, MAX_OUTPUT, this::setChanged);
    private final ItemStackHandler fuelItems = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isFuel(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return getData(index);
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private long lastReceivedGameTime = Long.MIN_VALUE;
    private long lastReceivedSampleStep = Long.MIN_VALUE;
    private long lastObservedSampleStep = Long.MIN_VALUE;
    private double receivedPowerThisStep = 0.0;
    private boolean receivedReliableThisStep = false;
    private double committedOpticalPower = 0.0;
    private double burnTicksRemaining = 0.0;
    private int baseBurnDuration = 0;
    private double generationRemainder = 0.0;

    public PhotothermalGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.PHOTOTHERMAL_GENERATOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, PhotothermalGeneratorBlockEntity generator) {
        if (level.isClientSide) {
            return;
        }

        generator.tickOpticalSample(level);
        generator.tickFuelCycle();
        generator.pushEnergy(level, pos);
    }

    public static boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && stack.getBurnTime(null) > 0;
    }

    public void receiveOpticalSample(ReadoutSample sample) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        long gameTime = this.level.getGameTime();
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

    public ItemStackHandler fuelItems() {
        return fuelItems;
    }

    public ContainerData createDataAccess() {
        return data;
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return side == null || side == Direction.UP ? energy : null;
    }

    @Nullable
    public ItemStackHandler getFuelItems(@Nullable Direction side) {
        return side == null || side == Direction.SOUTH ? fuelItems : null;
    }

    private void tickOpticalSample(Level level) {
        if (level.getGameTime() - this.lastReceivedGameTime > SAMPLE_HOLD_TICKS) {
            commitOpticalPower(0.0);
            return;
        }

        if (this.lastReceivedSampleStep == this.lastObservedSampleStep) {
            return;
        }

        this.lastObservedSampleStep = this.lastReceivedSampleStep;
        commitOpticalPower(receivedReliableThisStep ? receivedPowerThisStep : 0.0);
    }

    private void commitOpticalPower(double power) {
        double clampedPower = Double.isFinite(power) && power > 0.0 ? power : 0.0;

        if (Math.abs(committedOpticalPower - clampedPower) > 1.0E-6) {
            committedOpticalPower = clampedPower;
            setChanged();
        }
    }

    private void tickFuelCycle() {
        if (energy.getEnergyStored() >= energy.getMaxEnergyStored()) {
            return;
        }

        if (burnTicksRemaining <= 0.0) {
            tryStartFuel();
        }

        if (burnTicksRemaining <= 0.0) {
            return;
        }

        burnTicksRemaining = Math.max(0.0, burnTicksRemaining - 1.0 / currentBurnDurationMultiplier());
        generationRemainder += BASE_FE_PER_TICK * currentOutputMultiplier();

        int generated = (int) Math.floor(generationRemainder);

        if (generated > 0) {
            int accepted = energy.addEnergy(generated, false);
            generationRemainder -= accepted;

            if (accepted == 0 && energy.getEnergyStored() >= energy.getMaxEnergyStored()) {
                generationRemainder = Math.min(generationRemainder, 1.0);
            }
        }

        setChanged();
    }

    private void tryStartFuel() {
        ItemStack fuel = fuelItems.getStackInSlot(0);

        if (!isFuel(fuel)) {
            baseBurnDuration = 0;
            return;
        }

        int burnTime = fuel.getBurnTime(null);

        if (burnTime <= 0) {
            baseBurnDuration = 0;
            return;
        }

        burnTicksRemaining = burnTime;
        baseBurnDuration = burnTime;
        consumeOneFuel(fuel);
        setChanged();
    }

    private void consumeOneFuel(ItemStack fuel) {
        if (fuel.is(Items.LAVA_BUCKET) && fuel.getCount() == 1) {
            fuelItems.setStackInSlot(0, new ItemStack(Items.BUCKET));
            return;
        }

        fuel.shrink(1);
        fuelItems.setStackInSlot(0, fuel);
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

    private int getData(int index) {
        double burnDurationMultiplier = currentBurnDurationMultiplier();

        return switch (index) {
            case DATA_ENERGY -> energy.getEnergyStored();
            case DATA_CAPACITY -> energy.getMaxEnergyStored();
            case DATA_BURN_REMAINING -> (int) Math.ceil(burnTicksRemaining * burnDurationMultiplier);
            case DATA_BURN_DURATION -> (int) Math.ceil(baseBurnDuration * burnDurationMultiplier);
            case DATA_OUTPUT -> burnTicksRemaining > 0.0 ? (int) Math.round(BASE_FE_PER_TICK * currentOutputMultiplier()) : 0;
            case DATA_FUEL_COUNT -> fuelItems.getStackInSlot(0).getCount();
            default -> 0;
        };
    }

    private double currentOutputMultiplier() {
        double t = opticalBoostProgress();
        return 1.0 + t * (MAX_OUTPUT_MULTIPLIER - 1.0);
    }

    private double currentBurnDurationMultiplier() {
        double t = opticalBoostProgress();
        return 1.0 - t * (1.0 - MIN_BURN_DURATION_MULTIPLIER);
    }

    private double opticalBoostProgress() {
        return Math.max(0.0, Math.min(1.0, committedOpticalPower / FULL_OPTICAL_BOOST_POWER));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        burnTicksRemaining = tag.getDouble(BURN_REMAINING_TAG);
        baseBurnDuration = tag.getInt(BURN_DURATION_TAG);
        generationRemainder = tag.getDouble(GENERATION_REMAINDER_TAG);
        committedOpticalPower = tag.getDouble(OPTICAL_POWER_TAG);

        if (tag.contains(FUEL_TAG)) {
            fuelItems.deserializeNBT(registries, tag.getCompound(FUEL_TAG));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.put(FUEL_TAG, fuelItems.serializeNBT(registries));
        tag.putDouble(BURN_REMAINING_TAG, burnTicksRemaining);
        tag.putInt(BURN_DURATION_TAG, baseBurnDuration);
        tag.putDouble(GENERATION_REMAINDER_TAG, generationRemainder);
        tag.putDouble(OPTICAL_POWER_TAG, committedOpticalPower);
    }
}
