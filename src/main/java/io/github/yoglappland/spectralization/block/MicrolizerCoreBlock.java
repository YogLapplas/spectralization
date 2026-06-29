package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.MachineContentsDropper;
import io.github.yoglappland.spectralization.blockentity.MicrolizerCoreBlockEntity;
import io.github.yoglappland.spectralization.microlizer.MicrolizerPartKind;
import io.github.yoglappland.spectralization.menu.MicrolizerCoreMenu;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class MicrolizerCoreBlock extends MicrolizerPartBlock implements EntityBlock {
    public MicrolizerCoreBlock(BlockBehaviour.Properties properties) {
        super(properties, MicrolizerPartKind.CORE);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MicrolizerCoreBlockEntity core) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) ->
                            new MicrolizerCoreMenu(containerId, inventory, core),
                    Component.translatable("container.spectralization.microlizer_core")
            ));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MicrolizerCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.MICROLIZER_CORE.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                MicrolizerCoreBlockEntity.tick(tickerLevel, pos, (MicrolizerCoreBlockEntity) blockEntity);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MicrolizerCoreBlockEntity core) {
            core.updateRedstoneSignal(serverLevel);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        MachineContentsDropper.dropFromBlockEntity(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
