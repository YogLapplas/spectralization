package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.RecursiveGeneratorBlock;
import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.machine.RecursiveGeneratorState;
import io.github.yoglappland.spectralization.menu.RecursiveGeneratorMenu;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class RecursiveGeneratorBlockEntity extends BlockEntity implements MenuProvider, DropsContentsOnRemove {
    private static final String ENERGY_KEY = "Energy";
    private static final String REMAINING_KEY = "Remaining";
    private static final String INPUT_KEY = "Input";
    private static final String STACK_INPUT_KEY = "spectralization_recursive_generator_input";

    public static final int DATA_ENERGY = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_OUTPUT = 2;
    public static final int DATA_ACTIVE_DEPTH = 3;
    public static final int DATA_REMAINING_0 = 4;
    public static final int DATA_INPUT_LOCKED = DATA_REMAINING_0 + RecursiveGeneratorState.MAX_LAYERS;
    public static final int DATA_COUNT = DATA_INPUT_LOCKED + 1;

    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(
            RecursiveGeneratorState.CAPACITY,
            0,
            RecursiveGeneratorState.MAX_EXTRACT_PER_TICK,
            this::setChanged
    );
    private RecursiveGeneratorState state = RecursiveGeneratorState.empty();
    private final ItemStackHandler inputItems = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canAcceptUpgradeStack(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChangedAndSync();
        }
    };

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_CAPACITY -> energy.getMaxEnergyStored();
                case DATA_OUTPUT -> isGenerating() ? state.currentOutputPerTick() : 0;
                case DATA_ACTIVE_DEPTH -> state.hasRemaining() ? state.recursionDepth() : 0;
                default -> {
                    int layer = index - DATA_REMAINING_0;
                    if (layer >= 0 && layer < RecursiveGeneratorState.MAX_LAYERS) {
                        yield state.remainingAt(layer);
                    }
                    yield index == DATA_INPUT_LOCKED && isInputLocked() ? 1 : 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private final IEnergyStorage outputEnergy = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return energy.extractEnergy(Math.min(maxExtract, RecursiveGeneratorState.MAX_EXTRACT_PER_TICK), simulate);
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
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    private final BlockCapabilityCache<IEnergyStorage, Direction>[] energyTargets = new BlockCapabilityCache[Direction.values().length];

    public RecursiveGeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.RECURSIVE_GENERATOR.get(), pos, blockState);
    }

    public static boolean isUpgradeItem(ItemStack stack) {
        return stack.is(Spectralization.RECURSIVE_GENERATOR_ITEM.get());
    }

    public static void tick(Level level, BlockPos pos, BlockState blockState, RecursiveGeneratorBlockEntity generator) {
        if (level.isClientSide) {
            return;
        }

        boolean changed = false;
        if (generator.consumePendingUpgrade()) {
            changed = true;
        }

        boolean wasActive = generator.state.hasRemaining();
        if (wasActive) {
            int output = generator.state.currentOutputPerTick();
            if (output > 0 && generator.energy.getEnergyStored() < generator.energy.getMaxEnergyStored()) {
                int accepted = generator.energy.addEnergy(output, false);
                if (accepted > 0) {
                    generator.state = generator.state.withEnergy(generator.energy.getEnergyStored()).tickRemaining();
                    if (!generator.state.hasRemaining()) {
                        generator.inputItems.setStackInSlot(0, ItemStack.EMPTY);
                    } else {
                        generator.syncLockedInputTooltipState();
                    }
                    changed = true;
                } else {
                    generator.state = generator.state.withEnergy(generator.energy.getEnergyStored());
                    generator.syncLockedInputTooltipState();
                }
            }
        }

        if (generator.energy.getEnergyStored() > 0) {
            int before = generator.energy.getEnergyStored();
            generator.pushEnergy();
            if (generator.energy.getEnergyStored() != before) {
                generator.state = generator.state.withEnergy(generator.energy.getEnergyStored());
                generator.syncLockedInputTooltipState();
                changed = true;
            }
        }

        boolean active = generator.isGenerating();
        if (blockState.getValue(RecursiveGeneratorBlock.ACTIVE) != active) {
            level.setBlock(pos, blockState.setValue(RecursiveGeneratorBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
            changed = true;
        }
        if (changed) {
            generator.setChangedAndSync();
        }
    }

    public boolean canAcceptUpgradeStack(ItemStack stack) {
        return isUpgradeItem(stack) && !state.hasRemaining() && inputItems.getStackInSlot(0).isEmpty();
    }

    public boolean isInputLocked() {
        return state.hasRemaining() && !inputItems.getStackInSlot(0).isEmpty();
    }

    public boolean canPlayerUseInputSlot() {
        return !isInputLocked();
    }

    public boolean acceptUpgradeStack(ItemStack stack) {
        if (!isUpgradeItem(stack) || state.hasRemaining()) {
            return false;
        }
        return beginRecursion(stack);
    }

    public boolean insertUpgradeStack(ItemStack stack) {
        if (!canAcceptUpgradeStack(stack)) {
            return false;
        }
        ItemStack locked = stack.copyWithCount(1);
        inputItems.setStackInSlot(0, locked);
        return beginRecursion(locked);
    }

    public ItemStackHandler inputItems() {
        return inputItems;
    }

    public ContainerData data() {
        return data;
    }

    public @Nullable IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return outputEnergy;
    }

    public void loadFromStack(ItemStack stack, HolderLookup.Provider registries) {
        state = RecursiveGeneratorState.fromStack(stack);
        energy.setEnergyStored(state.energy());
        inputItems.setStackInSlot(0, savedInputFromStack(stack, registries));
        setChangedAndSync();
    }

    public void saveToStack(ItemStack stack, HolderLookup.Provider registries) {
        syncLockedInputTooltipState();
        RecursiveGeneratorState saved = state.withEnergy(energy.getEnergyStored());
        RecursiveGeneratorState.writeToStack(stack, saved);
        writeSavedInputToStack(stack, saved, registries);
    }

    public void updateActiveBlockState() {
        if (level == null) {
            return;
        }
        BlockState blockState = getBlockState();
        boolean active = isGenerating();
        if (blockState.hasProperty(RecursiveGeneratorBlock.ACTIVE) && blockState.getValue(RecursiveGeneratorBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, blockState.setValue(RecursiveGeneratorBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.spectralization.recursive_generator");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RecursiveGeneratorMenu(containerId, playerInventory, this);
    }

    @Override
    public void dropContents(Level level, BlockPos pos) {
        if (isInputLocked()) {
            return;
        }
        SimpleContainer container = new SimpleContainer(inputItems.getSlots());
        for (int slot = 0; slot < inputItems.getSlots(); slot++) {
            container.setItem(slot, inputItems.getStackInSlot(slot));
        }
        Containers.dropContents(level, pos, container);
        for (int slot = 0; slot < inputItems.getSlots(); slot++) {
            inputItems.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(ENERGY_KEY, energy.getEnergyStored());
        tag.putIntArray(REMAINING_KEY, state.remaining());
        tag.put(INPUT_KEY, inputItems.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        state = new RecursiveGeneratorState(tag.getIntArray(REMAINING_KEY), tag.getInt(ENERGY_KEY));
        energy.setEnergyStored(state.energy());
        if (tag.contains(INPUT_KEY)) {
            inputItems.deserializeNBT(registries, tag.getCompound(INPUT_KEY));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadAdditional(pkt.getTag(), lookupProvider);
    }

    private boolean consumePendingUpgrade() {
        ItemStack pending = inputItems.getStackInSlot(0);
        if (!isUpgradeItem(pending) || state.hasRemaining()) {
            return false;
        }
        return beginRecursion(pending);
    }

    private boolean beginRecursion(ItemStack internalGenerator) {
        RecursiveGeneratorState inner = RecursiveGeneratorState.fromStack(internalGenerator);
        RecursiveGeneratorState before = state;
        RecursiveGeneratorState promoted = RecursiveGeneratorState.promotedFrom(inner, energy.getEnergyStored());
        logPromotion(inner, before, promoted);
        state = promoted;
        energy.setEnergyStored(state.energy());
        syncLockedInputTooltipState();
        setChangedAndSync();
        return true;
    }

    private boolean isGenerating() {
        return state.hasRemaining() && energy.getEnergyStored() < energy.getMaxEnergyStored();
    }

    private void logPromotion(
            RecursiveGeneratorState inner,
            RecursiveGeneratorState before,
            RecursiveGeneratorState promoted
    ) {
        int expectedLayers = RecursiveGeneratorState.expectedPromotedActiveLayerCount(inner);
        boolean lostLayers = promoted.activeLayerCount() < expectedLayers;
        String message = "recursive_generator promote pos={} inner_layers={} before_layers={} after_layers={} expected_layers={} energy_before={} inner_energy={} energy_after={} inner_remaining={} before_remaining={} after_remaining={}";
        Object[] args = {
                worldPosition,
                inner.activeLayerCount(),
                before.activeLayerCount(),
                promoted.activeLayerCount(),
                expectedLayers,
                energy.getEnergyStored(),
                inner.energy(),
                promoted.energy(),
                Arrays.toString(inner.remaining()),
                Arrays.toString(before.remaining()),
                Arrays.toString(promoted.remaining())
        };
        if (lostLayers) {
            Spectralization.LOGGER.warn(message, args);
        } else {
            Spectralization.LOGGER.info(message, args);
        }
    }

    private void syncLockedInputTooltipState() {
        if (!state.hasRemaining()) {
            return;
        }
        ItemStack locked = inputItems.getStackInSlot(0);
        if (locked.isEmpty() || !isUpgradeItem(locked)) {
            return;
        }
        RecursiveGeneratorState.writeToStack(locked, state.withEnergy(energy.getEnergyStored()));
    }

    private static ItemStack savedInputFromStack(ItemStack stack, HolderLookup.Provider registries) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag root = data.copyTag();
        if (!root.contains(STACK_INPUT_KEY)) {
            return ItemStack.EMPTY;
        }
        ItemStack savedInput = ItemStack.parseOptional(registries, root.getCompound(STACK_INPUT_KEY));
        if (!isUpgradeItem(savedInput)) {
            return ItemStack.EMPTY;
        }
        return savedInput.copyWithCount(1);
    }

    private void writeSavedInputToStack(ItemStack stack, RecursiveGeneratorState saved, HolderLookup.Provider registries) {
        ItemStack input = inputItems.getStackInSlot(0);
        if (!saved.hasRemaining() || input.isEmpty()) {
            removeSavedInput(stack);
            return;
        }

        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        root.put(STACK_INPUT_KEY, input.copyWithCount(1).saveOptional(registries));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    private static void removeSavedInput(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        CompoundTag root = data.copyTag();
        if (!root.contains(STACK_INPUT_KEY)) {
            return;
        }
        root.remove(STACK_INPUT_KEY);
        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    private void pushEnergy() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        int remaining = Math.min(RecursiveGeneratorState.MAX_EXTRACT_PER_TICK, energy.getEnergyStored());
        if (remaining <= 0) {
            return;
        }
        for (Direction direction : Direction.values()) {
            IEnergyStorage target = targetFor(serverLevel, direction);
            if (target == null || !target.canReceive()) {
                continue;
            }
            int extracted = energy.extractEnergy(remaining, true);
            int accepted = target.receiveEnergy(extracted, false);
            if (accepted > 0) {
                energy.extractEnergy(accepted, false);
                remaining -= accepted;
                if (remaining <= 0) {
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private @Nullable IEnergyStorage targetFor(net.minecraft.server.level.ServerLevel serverLevel, Direction direction) {
        int index = direction.ordinal();
        BlockCapabilityCache<IEnergyStorage, Direction> cache = energyTargets[index];
        if (cache == null) {
            cache = BlockCapabilityCache.create(
                    Capabilities.EnergyStorage.BLOCK,
                    serverLevel,
                    worldPosition.relative(direction),
                    direction.getOpposite()
            );
            energyTargets[index] = cache;
        }
        return cache.getCapability();
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
