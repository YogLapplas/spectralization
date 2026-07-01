package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.FiberDrawingMachineBlockEntity;
import io.github.yoglappland.spectralization.blockentity.MachineContentsDropper;
import io.github.yoglappland.spectralization.menu.FiberDrawingMachineMenu;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FiberDrawingMachineBlock extends HorizontalFacingEntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public FiberDrawingMachineBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FiberDrawingMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.FIBER_DRAWING_MACHINE.get()) {
            return null;
        }

        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof FiberDrawingMachineBlockEntity machine) {
                FiberDrawingMachineBlockEntity.tick(tickLevel, pos, machine);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FiberDrawingMachineBlockEntity machine) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new FiberDrawingMachineMenu(containerId, inventory, machine),
                    Component.translatable("container.spectralization.fiber_drawing_machine")
            ));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        MachineContentsDropper.dropFromBlockEntity(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
