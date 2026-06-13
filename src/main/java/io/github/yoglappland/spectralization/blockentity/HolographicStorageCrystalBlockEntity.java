package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HolographicStorageCrystalBlockEntity extends BlockEntity {
    private static final int DEFAULT_CORE_RENDER_COLOR = 0x80DAF8FF;

    public HolographicStorageCrystalBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.HOLOGRAPHIC_STORAGE_CRYSTAL.get(), pos, blockState);
    }

    public int getCoreRenderColor() {
        return DEFAULT_CORE_RENDER_COLOR;
    }
}
