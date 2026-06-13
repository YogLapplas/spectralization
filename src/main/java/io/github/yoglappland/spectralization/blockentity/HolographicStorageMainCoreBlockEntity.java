package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public class HolographicStorageMainCoreBlockEntity extends BlockEntity {
    private static final String STORAGE_ID_TAG = "StorageId";

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
