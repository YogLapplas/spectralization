package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.machine.FiberDrawingRecipe;
import io.github.yoglappland.spectralization.optics.fiber.FiberMaterialProfile;
import io.github.yoglappland.spectralization.optics.lens.LensMaterial;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.tag.SpectralItemTags;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class FiberDrawingMachineBlockEntity extends BlockEntity implements DropsContentsOnRemove {
    public static final int SLOT_MATERIAL_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int SLOT_MOLD_INPUT = 2;
    public static final int SLOT_MOLD_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int STATE_EMPTY = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_NO_POWER = 2;
    public static final int STATE_INVALID = 3;
    public static final int STATE_OUTPUT_BLOCKED = 4;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_MAX = 1;
    public static final int DATA_STATE = 2;
    public static final int DATA_MOLD_KIND = 3;
    public static final int DATA_PROGRESS = 4;
    public static final int DATA_PROGRESS_REQUIRED = 5;
    public static final int DATA_MOLD_RULE = 6;
    public static final int DATA_COUNT = 7;

    public static final int MOLD_RULE_KEEP = 0;
    public static final int MOLD_RULE_EJECT = 1;

    private static final int ENERGY_CAPACITY = 32_000;
    private static final int MAX_ENERGY_INPUT = 1_024;
    private static final int ENERGY_PER_TICK = 64;
    private static final int PROCESS_TICKS = 100;
    private static final String ITEMS_TAG = "items";
    private static final String ENERGY_TAG = "energy";
    private static final String PROGRESS_TAG = "progress";
    private static final String MOLD_RULE_TAG = "mold_rule";

    private int progress = 0;
    private int moldRule = MOLD_RULE_KEEP;

    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(
            ENERGY_CAPACITY,
            MAX_ENERGY_INPUT,
            0,
            this::syncChanged
    );

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_MATERIAL_INPUT -> isDrawableMaterial(stack);
                case SLOT_MOLD_INPUT -> isFiberMold(stack);
                default -> false;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_MOLD_INPUT || slot == SLOT_MOLD_OUTPUT ? 1 : super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            syncChanged();
        }
    };

    private final IItemHandler sidedItems = new SidedItems();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return getData(index);
        }

        @Override
        public void set(int index, int value) {
            setData(index, value);
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public FiberDrawingMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.FIBER_DRAWING_MACHINE.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, FiberDrawingMachineBlockEntity machine) {
        if (level.isClientSide) {
            return;
        }

        machine.tickRecipe();
    }

    public ItemStackHandler items() {
        return items;
    }

    @Nullable
    public IItemHandler getItems(@Nullable Direction side) {
        return side == null ? items : sidedItems;
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energy;
    }

    public ContainerData createDataAccess() {
        return data;
    }

    public void toggleMoldRule() {
        moldRule = moldRule == MOLD_RULE_KEEP ? MOLD_RULE_EJECT : MOLD_RULE_KEEP;
        syncChanged();
    }

    public void dropContents(Level level, BlockPos pos) {
        MachineContentsDropper.dropItemHandler(level, pos, items);
    }

    public static boolean isDrawableMaterial(ItemStack stack) {
        return LensMaterial.fromBlank(stack).isPresent();
    }

    public static boolean isFiberMold(ItemStack stack) {
        return stack.is(SpectralItemTags.FIBER_MOLDS) || stack.is(SpectralItemTags.SINGLE_MODE_FIBER_MOLDS);
    }

    private int getData(int index) {
        return switch (index) {
            case DATA_ENERGY -> energy.getEnergyStored();
            case DATA_ENERGY_MAX -> energy.getMaxEnergyStored();
            case DATA_STATE -> stateCode();
            case DATA_MOLD_KIND -> moldKind();
            case DATA_PROGRESS -> progress;
            case DATA_PROGRESS_REQUIRED -> PROCESS_TICKS;
            case DATA_MOLD_RULE -> moldRule;
            default -> 0;
        };
    }

    private void setData(int index, int value) {
        switch (index) {
            case DATA_ENERGY -> energy.setEnergyStored(value);
            case DATA_MOLD_RULE -> {
                moldRule = value == MOLD_RULE_EJECT ? MOLD_RULE_EJECT : MOLD_RULE_KEEP;
                setChanged();
            }
            default -> {
            }
        }
    }

    private int stateCode() {
        ItemStack material = items.getStackInSlot(SLOT_MATERIAL_INPUT);
        ItemStack mold = items.getStackInSlot(SLOT_MOLD_INPUT);

        if (material.isEmpty() && mold.isEmpty()) {
            return STATE_EMPTY;
        }

        Optional<FiberDrawingRecipe> recipe = activeRecipe();
        if (recipe.isEmpty()) {
            return STATE_INVALID;
        }

        if (outputBlocked(recipe.get())) {
            return STATE_OUTPUT_BLOCKED;
        }

        return energy.getEnergyStored() >= ENERGY_PER_TICK ? STATE_READY : STATE_NO_POWER;
    }

    private int moldKind() {
        ItemStack mold = items.getStackInSlot(SLOT_MOLD_INPUT);

        if (mold.is(SpectralItemTags.SINGLE_MODE_FIBER_MOLDS)) {
            return 2;
        }

        return mold.is(SpectralItemTags.FIBER_MOLDS) ? 1 : 0;
    }

    private void tickRecipe() {
        Optional<FiberDrawingRecipe> match = activeRecipe();

        if (match.isEmpty()) {
            resetProgress();
            return;
        }

        FiberDrawingRecipe recipe = match.get();
        if (outputBlocked(recipe) || !consumeRecipeEnergy(true)) {
            setChanged();
            return;
        }

        consumeRecipeEnergy(false);
        progress++;

        if (progress >= PROCESS_TICKS) {
            completeRecipe(recipe);
            progress = 0;
        }

        setChanged();
    }

    private Optional<FiberDrawingRecipe> activeRecipe() {
        ItemStack materialStack = items.getStackInSlot(SLOT_MATERIAL_INPUT);
        ItemStack moldStack = items.getStackInSlot(SLOT_MOLD_INPUT);
        Optional<LensMaterial> material = LensMaterial.fromBlank(materialStack);

        if (material.isEmpty() || !isFiberMold(moldStack)) {
            return Optional.empty();
        }

        boolean singleMode = moldStack.is(SpectralItemTags.SINGLE_MODE_FIBER_MOLDS);
        return FiberDrawingRecipe.recipes().stream()
                .filter(recipe -> recipe.material() == material.get()
                        && recipe.singleMode() == singleMode
                        && moldStack.is(recipe.moldItem().getItem())
                        && recipe.profile().isPresent())
                .findFirst();
    }

    private boolean outputBlocked(FiberDrawingRecipe recipe) {
        ItemStack result = recipe.resultItem();

        if (acceptedCount(result, SLOT_OUTPUT, SLOT_OUTPUT) < result.getCount()) {
            return true;
        }

        if (moldRule == MOLD_RULE_EJECT) {
            ItemStack mold = items.getStackInSlot(SLOT_MOLD_INPUT);
            ItemStack returned = mold.copy();
            returned.setCount(1);
            return acceptedCount(returned, SLOT_MOLD_OUTPUT, SLOT_MOLD_OUTPUT) < 1;
        }

        return false;
    }

    private int acceptedCount(ItemStack result, int firstSlot, int lastSlot) {
        if (result.isEmpty()) {
            return 0;
        }

        int accepted = 0;

        for (int slot = firstSlot; slot <= lastSlot; slot++) {
            accepted += outputCapacityFor(items.getStackInSlot(slot), result, slot);
        }

        return accepted;
    }

    private int outputCapacityFor(ItemStack output, ItemStack result, int slot) {
        int slotLimit = Math.min(result.getMaxStackSize(), items.getSlotLimit(slot));

        if (output.isEmpty()) {
            return slotLimit;
        }

        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return 0;
        }

        return Math.max(0, slotLimit - output.getCount());
    }

    private void completeRecipe(FiberDrawingRecipe recipe) {
        items.extractItem(SLOT_MATERIAL_INPUT, 1, false);
        insertResult(recipe.resultItem(), SLOT_OUTPUT, SLOT_OUTPUT);

        if (moldRule == MOLD_RULE_EJECT) {
            moveMoldToOutput();
        }
    }

    private void moveMoldToOutput() {
        ItemStack mold = items.extractItem(SLOT_MOLD_INPUT, 1, false);
        if (mold.isEmpty()) {
            return;
        }

        insertResult(mold, SLOT_MOLD_OUTPUT, SLOT_MOLD_OUTPUT);
    }

    private void insertResult(ItemStack result, int firstSlot, int lastSlot) {
        int remaining = result.getCount();

        for (int slot = firstSlot; slot <= lastSlot && remaining > 0; slot++) {
            ItemStack output = items.getStackInSlot(slot);
            int slotLimit = Math.min(result.getMaxStackSize(), items.getSlotLimit(slot));

            if (output.isEmpty()) {
                ItemStack inserted = result.copy();
                inserted.setCount(Math.min(remaining, slotLimit));
                items.setStackInSlot(slot, inserted);
                remaining -= inserted.getCount();
            } else if (ItemStack.isSameItemSameComponents(output, result)) {
                int moved = Math.min(remaining, slotLimit - output.getCount());

                if (moved > 0) {
                    output.grow(moved);
                    items.setStackInSlot(slot, output);
                    remaining -= moved;
                }
            }
        }
    }

    private boolean consumeRecipeEnergy(boolean simulate) {
        if (energy.getEnergyStored() < ENERGY_PER_TICK) {
            return false;
        }

        if (!simulate) {
            energy.setEnergyStored(energy.getEnergyStored() - ENERGY_PER_TICK);
        }

        return true;
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    private void syncChanged() {
        setChanged();

        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        progress = Math.max(0, tag.getInt(PROGRESS_TAG));
        moldRule = tag.getInt(MOLD_RULE_TAG) == MOLD_RULE_EJECT ? MOLD_RULE_EJECT : MOLD_RULE_KEEP;

        if (tag.contains(ITEMS_TAG)) {
            items.deserializeNBT(registries, tag.getCompound(ITEMS_TAG));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ITEMS_TAG, items.serializeNBT(registries));
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.putInt(PROGRESS_TAG, progress);
        tag.putInt(MOLD_RULE_TAG, moldRule);
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    private final class SidedItems implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < SLOT_COUNT ? items.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != SLOT_MATERIAL_INPUT && slot != SLOT_MOLD_INPUT) {
                return stack;
            }

            if (!items.isItemValid(slot, stack)) {
                return stack;
            }

            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != SLOT_OUTPUT && slot != SLOT_MOLD_OUTPUT) {
                return ItemStack.EMPTY;
            }

            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < SLOT_COUNT ? items.getSlotLimit(slot) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < SLOT_COUNT && items.isItemValid(slot, stack);
        }
    }
}
