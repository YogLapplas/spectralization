package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.MachineContentsDropper;
import io.github.yoglappland.spectralization.blockentity.CompactMachineCoreBlockEntity;
import io.github.yoglappland.spectralization.compact.CompactMachinePartKind;
import io.github.yoglappland.spectralization.menu.CompactMachineCoreMenu;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CompactMachineCoreBlock extends CompactMachinePartBlock implements EntityBlock {
    public CompactMachineCoreBlock(BlockBehaviour.Properties properties) {
        super(properties, CompactMachinePartKind.CORE);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CompactMachineCoreBlockEntity core) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) ->
                            new CompactMachineCoreMenu(containerId, inventory, core),
                    Component.translatable("container.spectralization.compact_machine_core")
            ));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompactMachineCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.COMPACT_MACHINE_CORE.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                CompactMachineCoreBlockEntity.tick(tickerLevel, pos, (CompactMachineCoreBlockEntity) blockEntity);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        MachineContentsDropper.dropFromBlockEntity(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
