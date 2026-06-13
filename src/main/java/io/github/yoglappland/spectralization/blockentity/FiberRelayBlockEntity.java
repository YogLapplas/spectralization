package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class FiberRelayBlockEntity extends FiberNodeBlockEntity {
    public FiberRelayBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.FIBER_RELAY.get(), pos, blockState);
    }
}
