package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.HolographicStorageMainCoreBlockEntity;
import io.github.yoglappland.spectralization.menu.HolographicStorageCoreMenu;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class HolographicStorageMainCoreBlock extends HolographicStorageCrystalBlock implements EntityBlock {
    public HolographicStorageMainCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return defaultBlockState()
                .setValue(ERROR, HolographicStorageMultiblock.isCoreTooClose(level, pos));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshErrorState(level, pos);
        refreshNearbyStorageVisuals(level, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof HolographicStorageMainCoreBlockEntity core) {
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) ->
                                new HolographicStorageCoreMenu(containerId, inventory, core),
                        Component.translatable("container.spectralization.holographic_storage_core")
                ));
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HolographicStorageMainCoreBlockEntity(pos, state);
    }

    private static void refreshErrorState(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HolographicStorageMainCoreBlock) || state.getValue(ERROR)) {
            return;
        }

        if (HolographicStorageMultiblock.isCoreTooClose(level, pos)) {
            level.setBlock(pos, state.setValue(ERROR, true), Block.UPDATE_CLIENTS);
        }
    }
}
