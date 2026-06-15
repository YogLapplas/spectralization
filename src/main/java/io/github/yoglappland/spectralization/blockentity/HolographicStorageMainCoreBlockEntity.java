package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HolographicStorageMainCoreBlockEntity extends BlockEntity {
    private static final String STORAGE_ID_TAG = "StorageId";
    private static final String STACK_STORAGE_ID_TAG = "spectralization_holographic_storage_id";

    private UUID storageId;

    public HolographicStorageMainCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.HOLOGRAPHIC_STORAGE_MAIN_CORE.get(), pos, blockState);
    }

    public UUID storageId() {
        if (storageId == null) {
            storageId = UUID.randomUUID();
            setChanged();
        }

        return storageId;
    }

    public int registeredChannelMultiplier() {
        return 1;
    }

    public void loadFromStack(ItemStack stack) {
        storageIdFromStack(stack).ifPresent(id -> {
            storageId = id;
            setChanged();
        });
    }

    public void saveToStack(ItemStack stack) {
        UUID id = storageId();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
                tag.putUUID(STACK_STORAGE_ID_TAG, id)
        );
    }

    public static Optional<UUID> storageIdFromStack(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        if (!tag.hasUUID(STACK_STORAGE_ID_TAG)) {
            return Optional.empty();
        }

        return Optional.of(tag.getUUID(STACK_STORAGE_ID_TAG));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(STORAGE_ID_TAG)) {
            storageId = tag.getUUID(STORAGE_ID_TAG);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (storageId != null) {
            tag.putUUID(STORAGE_ID_TAG, storageId);
        }
    }
}
