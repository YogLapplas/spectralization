package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class FiberOpticInterfaceBlockEntity extends FiberNodeBlockEntity {
    public FiberOpticInterfaceBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.FIBER_OPTIC_INTERFACE.get(), pos, blockState);
    }
}
