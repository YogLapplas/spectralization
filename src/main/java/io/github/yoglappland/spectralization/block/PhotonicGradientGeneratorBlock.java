package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.MachineContentsDropper;
import io.github.yoglappland.spectralization.blockentity.PhotonicGradientGeneratorBlockEntity;
import io.github.yoglappland.spectralization.menu.LightSourceGeneratorMenu;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class PhotonicGradientGeneratorBlock extends HorizontalFacingEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public PhotonicGradientGeneratorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof PhotonicGradientGeneratorBlockEntity generator) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new LightSourceGeneratorMenu(containerId, inventory, generator),
                    Component.translatable("container.spectralization.light_source_generator")
            ));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonicGradientGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.LIGHT_SOURCE_GENERATOR.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                PhotonicGradientGeneratorBlockEntity.tick(tickerLevel, pos, (PhotonicGradientGeneratorBlockEntity) blockEntity);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        MachineContentsDropper.dropFromBlockEntity(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }
}
