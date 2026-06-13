package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kept so old test worlds with crystal block entity data can still load.
 * New storage logic lives on HolographicStorageMainCoreBlockEntity.
 */
@Deprecated(forRemoval = false)
public class HolographicStorageCrystalBlockEntity extends BlockEntity {
    public HolographicStorageCrystalBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.HOLOGRAPHIC_STORAGE_CRYSTAL.get(), pos, blockState);
    }
}
