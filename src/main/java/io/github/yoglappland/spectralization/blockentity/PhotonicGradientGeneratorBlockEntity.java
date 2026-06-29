package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.PhotonicGradientGeneratorBlock;
import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.tag.SpectralItemTags;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PhotonicGradientGeneratorBlockEntity extends BlockEntity implements DropsContentsOnRemove {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_BURN_REMAINING = 2;
    public static final int DATA_BURN_DURATION = 3;
    public static final int DATA_OUTPUT = 4;
    public static final int DATA_SOURCE_COUNT = 5;
    public static final int DATA_COUNT = 6;
    public static final int CAPACITY = 10_000;

    private static final int MAX_OUTPUT = 64;
    public static final int BURN_TICKS = 200;
    public static final int BASE_FE_PER_TICK = 10;
    private static final String ENERGY_TAG = "energy";
    private static final String SOURCE_TAG = "source";
    private static final String DISPLAY_SOURCE_TAG = "display_source";
    private static final String BURN_REMAINING_TAG = "burn_remaining";
    private static final String BURN_DURATION_TAG = "burn_duration";
    private static final String OUTPUT_TAG = "output";
    private static final String REMAINDER_TAG = "generation_remainder";

    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(CAPACITY, 0, MAX_OUTPUT, this::setChanged);
    private final ItemStackHandler sourceItems = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isLightSourceFuel(stack);
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

    private int burnTicksRemaining = 0;
    private int burnDuration = 0;
    private int currentOutput = 0;
    private double generationRemainder = 0.0;
    private ItemStack displaySource = ItemStack.EMPTY;

    public PhotonicGradientGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.LIGHT_SOURCE_GENERATOR.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, PhotonicGradientGeneratorBlockEntity generator) {
        if (level.isClientSide) {
            return;
        }

        generator.tickFuelCycle();
        generator.pushEnergy(level, pos);
        generator.setActive(level, pos, generator.isGenerating());
    }

    public static boolean isLightSourceFuel(ItemStack stack) {
        return outputFor(stack) > 0;
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energy;
    }

    @Nullable
    public ItemStackHandler getSourceItems(@Nullable Direction side) {
        return side == null || side == Direction.SOUTH ? sourceItems : null;
    }

    public ItemStackHandler sourceItems() {
        return sourceItems;
    }

    public ContainerData createDataAccess() {
        return data;
    }

    public void dropContents(Level level, BlockPos pos) {
        MachineContentsDropper.dropItemHandler(level, pos, sourceItems);
    }

    public ItemStack displaySourceItem() {
        return displaySource;
    }

    public boolean isGenerating() {
        return burnTicksRemaining > 0
                && currentOutput > 0
                && energy.getEnergyStored() < energy.getMaxEnergyStored();
    }

    private void tickFuelCycle() {
        if (energy.getEnergyStored() >= energy.getMaxEnergyStored()) {
            return;
        }

        if (burnTicksRemaining <= 0) {
            tryStartFuel();
        }

        if (burnTicksRemaining <= 0 || currentOutput <= 0) {
            if (burnTicksRemaining <= 0) {
                clearDisplaySource();
            }
            return;
        }

        burnTicksRemaining--;
        if (burnTicksRemaining <= 0) {
            clearDisplaySource();
        }
        generationRemainder += currentOutput;
        int generated = (int) Math.floor(generationRemainder);

        if (generated <= 0) {
            return;
        }

        int accepted = energy.addEnergy(generated, false);
        generationRemainder -= accepted;

        if (accepted == 0 && energy.getEnergyStored() >= energy.getMaxEnergyStored()) {
            generationRemainder = Math.min(generationRemainder, 1.0);
        }

        setChanged();
    }

    private void tryStartFuel() {
        ItemStack fuel = sourceItems.getStackInSlot(0);
        int output = outputFor(fuel);

        if (output <= 0) {
            burnDuration = 0;
            currentOutput = 0;
            clearDisplaySource();
            return;
        }

        displaySource = fuel.copyWithCount(1);
        fuel.shrink(1);
        sourceItems.setStackInSlot(0, fuel);
        burnTicksRemaining = BURN_TICKS;
        burnDuration = BURN_TICKS;
        currentOutput = output;
        setChanged();
    }

    private void pushEnergy(Level level, BlockPos pos) {
        if (energy.getEnergyStored() <= 0) {
            return;
        }

        int remaining = Math.min(MAX_OUTPUT, energy.getEnergyStored());
        for (Direction direction : Direction.values()) {
            if (remaining <= 0) {
                return;
            }

            IEnergyStorage target = level.getCapability(
                    Capabilities.EnergyStorage.BLOCK,
                    pos.relative(direction),
                    direction.getOpposite()
            );

            if (target == null) {
                continue;
            }

            int extracted = energy.extractEnergy(remaining, true);
            int accepted = target.receiveEnergy(extracted, false);

            if (accepted > 0) {
                energy.extractEnergy(accepted, false);
                remaining -= accepted;
            }
        }
    }

    private void clearDisplaySource() {
        if (!displaySource.isEmpty()) {
            displaySource = ItemStack.EMPTY;
            setChanged();
        }
    }

    private void setActive(Level level, BlockPos pos, boolean active) {
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof PhotonicGradientGeneratorBlock
                && state.getValue(PhotonicGradientGeneratorBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(PhotonicGradientGeneratorBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        burnTicksRemaining = tag.getInt(BURN_REMAINING_TAG);
        burnDuration = tag.getInt(BURN_DURATION_TAG);
        currentOutput = tag.getInt(OUTPUT_TAG);
        generationRemainder = tag.getDouble(REMAINDER_TAG);
        displaySource = tag.contains(DISPLAY_SOURCE_TAG)
                ? ItemStack.parseOptional(registries, tag.getCompound(DISPLAY_SOURCE_TAG))
                : ItemStack.EMPTY;

        if (tag.contains(SOURCE_TAG)) {
            sourceItems.deserializeNBT(registries, tag.getCompound(SOURCE_TAG));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.put(SOURCE_TAG, sourceItems.serializeNBT(registries));
        tag.putInt(BURN_REMAINING_TAG, burnTicksRemaining);
        tag.putInt(BURN_DURATION_TAG, burnDuration);
        tag.putInt(OUTPUT_TAG, currentOutput);
        tag.putDouble(REMAINDER_TAG, generationRemainder);
        if (!displaySource.isEmpty()) {
            tag.put(DISPLAY_SOURCE_TAG, displaySource.saveOptional(registries));
        }
    }

    private int getData(int index) {
        return switch (index) {
            case DATA_ENERGY -> energy.getEnergyStored();
            case DATA_CAPACITY -> energy.getMaxEnergyStored();
            case DATA_BURN_REMAINING -> burnTicksRemaining;
            case DATA_BURN_DURATION -> burnDuration;
            case DATA_OUTPUT -> burnTicksRemaining > 0 ? currentOutput : 0;
            case DATA_SOURCE_COUNT -> sourceItems.getStackInSlot(0).getCount();
            default -> 0;
        };
    }

    public static int outputFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        if (!stack.is(SpectralItemTags.LIGHT_SOURCE_GENERATOR_FUEL)) {
            return 0;
        }

        if (stack.is(Spectralization.BASIC_LED_ITEM.get())) {
            return 8;
        }

        if (stack.is(Spectralization.ADVANCED_LED_ITEM.get())) {
            return 14;
        }

        return BASE_FE_PER_TICK;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}
