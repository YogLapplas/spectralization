package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeBlock;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeKind;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class FiberNodeBlockEntity extends BlockEntity {
    protected FiberNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        registerNode();
    }

    @Override
    public void setRemoved() {
        unregisterNode();
        super.setRemoved();
    }

    private void registerNode() {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof FiberNodeBlock fiberNodeBlock)) {
            return;
        }

        FiberNodeKind kind = fiberNodeBlock.fiberNodeKind(getBlockState(), level, worldPosition);
        FiberNodeProfile profile = fiberNodeBlock.fiberNodeProfile(getBlockState(), level, worldPosition);
        FiberNetworkIndex.registerNode(level, worldPosition, kind, profile);
    }

    private void unregisterNode() {
        if (level == null || level.isClientSide) {
            return;
        }

        FiberNetworkIndex.unregisterNode(level, worldPosition);
    }
}
