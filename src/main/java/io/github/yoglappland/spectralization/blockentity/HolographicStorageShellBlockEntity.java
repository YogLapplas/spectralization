package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.storage.HolographicStorageEntry;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public class HolographicStorageShellBlockEntity extends BlockEntity {
    public static final int CAPACITY = 4096;

    private static final String STORAGE_TAG = "Storage";
    private static final String STACK_STORAGE_TAG = "spectralization_holographic_storage_shell";
    private static final String TEMPLATE_TAG = "Template";
    private static final String COUNT_TAG = "Count";
    private static final String EMPTY_TAG = "Empty";

    private ItemStack template = ItemStack.EMPTY;
    private int count;
    private final IItemHandler itemHandler = new ShellItemHandler();

    public HolographicStorageShellBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.HOLOGRAPHIC_STORAGE_SHELL.get(), pos, blockState);
    }

    public boolean isEmpty() {
        return template.isEmpty() || count <= 0;
    }

    public boolean hasStoredItem() {
        return !isEmpty();
    }

    public ItemStack templateStack() {
        return isEmpty() ? ItemStack.EMPTY : template.copyWithCount(1);
    }

    public ItemStack getStackForDisplay() {
        return templateStack();
    }

    public int storedCount() {
        return isEmpty() ? 0 : count;
    }

    public HolographicStorageEntry entry() {
        return new HolographicStorageEntry(templateStack(), storedCount());
    }

    public boolean canAccept(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        return isEmpty() || ItemStack.isSameItemSameComponents(template, stack);
    }

    public int insert(ItemStack stack, int maxAmount, boolean simulate) {
        if (stack.isEmpty() || maxAmount <= 0 || !canAccept(stack)) {
            return 0;
        }

        int inserted = Math.min(Math.min(stack.getCount(), maxAmount), CAPACITY - storedCount());
        if (inserted <= 0) {
            return 0;
        }

        if (!simulate) {
            if (isEmpty()) {
                template = stack.copyWithCount(1);
            }
            count += inserted;
            setChangedAndSync();
        }

        return inserted;
    }

    public ItemStack extract(int maxAmount, boolean simulate) {
        if (isEmpty() || maxAmount <= 0) {
            return ItemStack.EMPTY;
        }

        int extractedCount = Math.min(Math.min(maxAmount, template.getMaxStackSize()), count);
        if (extractedCount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = template.copyWithCount(extractedCount);
        if (!simulate) {
            count -= extractedCount;
            if (count <= 0) {
                clearStorage();
            }
            setChangedAndSync();
        }

        return extracted;
    }

    public IItemHandler getItems(@Nullable net.minecraft.core.Direction side) {
        return itemHandler;
    }

    public void loadFromStack(ItemStack stack, HolderLookup.Provider registries) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(STACK_STORAGE_TAG, Tag.TAG_COMPOUND)) {
            clearStorage();
            return;
        }

        readStorage(root.getCompound(STACK_STORAGE_TAG), registries);
        setChanged();
    }

    public void saveToStack(ItemStack stack, HolderLookup.Provider registries) {
        if (isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);
            stack.remove(DataComponents.MAX_STACK_SIZE);
            return;
        }

        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.remove(DataComponents.BLOCK_ENTITY_DATA);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag storageTag = new CompoundTag();
            writeStorage(storageTag, registries);
            root.put(STACK_STORAGE_TAG, storageTag);
        });
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.getBoolean(EMPTY_TAG)) {
            clearStorage();
        } else if (tag.contains(STORAGE_TAG, Tag.TAG_COMPOUND)) {
            readStorage(tag.getCompound(STORAGE_TAG), registries);
        } else {
            clearStorage();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (isEmpty()) {
            tag.putBoolean(EMPTY_TAG, true);
            tag.remove(STORAGE_TAG);
            return;
        }

        tag.putBoolean(EMPTY_TAG, false);
        CompoundTag storageTag = new CompoundTag();
        writeStorage(storageTag, registries);
        tag.put(STORAGE_TAG, storageTag);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    private void readStorage(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack parsedTemplate = ItemStack.parseOptional(registries, tag.getCompound(TEMPLATE_TAG));
        int parsedCount = tag.getInt(COUNT_TAG);

        if (parsedTemplate.isEmpty() || parsedCount <= 0) {
            clearStorage();
            return;
        }

        template = parsedTemplate.copyWithCount(1);
        count = Math.min(parsedCount, CAPACITY);
    }

    private void writeStorage(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TEMPLATE_TAG, template.copyWithCount(1).saveOptional(registries));
        tag.putInt(COUNT_TAG, storedCount());
    }

    public static boolean hasSavedStorage(ItemStack stack) {
        return storageTagFromStack(stack).isPresent();
    }

    public static Optional<HolographicStorageEntry> entryFromStack(
            ItemStack stack,
            HolderLookup.Provider registries
    ) {
        Optional<CompoundTag> maybeStorageTag = storageTagFromStack(stack);
        if (maybeStorageTag.isEmpty()) {
            return Optional.empty();
        }

        CompoundTag storageTag = maybeStorageTag.get();
        ItemStack template = ItemStack.parseOptional(registries, storageTag.getCompound(TEMPLATE_TAG));
        int count = storageTag.getInt(COUNT_TAG);

        if (template.isEmpty() || count <= 0) {
            return Optional.empty();
        }

        return Optional.of(new HolographicStorageEntry(template.copyWithCount(1), Math.min(count, CAPACITY)));
    }

    private static Optional<CompoundTag> storageTagFromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(STACK_STORAGE_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        CompoundTag storageTag = root.getCompound(STACK_STORAGE_TAG);
        if (storageTag.getInt(COUNT_TAG) <= 0 || !storageTag.contains(TEMPLATE_TAG, Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }

        return Optional.of(storageTag);
    }

    private void clearStorage() {
        template = ItemStack.EMPTY;
        count = 0;
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }

    private final class ShellItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot != 0 || isEmpty()) {
                return ItemStack.EMPTY;
            }

            return template.copyWithCount(Math.min(count, template.getMaxStackSize()));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) {
                return stack;
            }

            int inserted = insert(stack, stack.getCount(), simulate);
            if (inserted <= 0) {
                return stack;
            }

            ItemStack remainder = stack.copy();
            remainder.shrink(inserted);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }

            return extract(amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? CAPACITY : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && canAccept(stack);
        }
    }
}
