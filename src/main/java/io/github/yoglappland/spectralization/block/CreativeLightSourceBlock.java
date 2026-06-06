package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.menu.CreativeLightSourceMenu;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class CreativeLightSourceBlock extends Block implements EntityBlock, OpticalSource {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public CreativeLightSourceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof CreativeLightSourceBlockEntity source) {
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) ->
                                new CreativeLightSourceMenu(containerId, inventory, source),
                        Component.translatable("container.spectralization.creative_light_source")
                ));
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreativeLightSourceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.CREATIVE_LIGHT_SOURCE.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                CreativeLightSourceBlockEntity.tick(tickerLevel, pos, tickerState, (CreativeLightSourceBlockEntity) blockEntity);
    }

    @Override
    public List<OutputBeam> getOutputBeams(BlockState state, Level level, BlockPos pos) {
        Direction direction = state.getValue(FACING);

        if (level.getBlockEntity(pos) instanceof CreativeLightSourceBlockEntity source) {
            return List.of(source.createOutputBeam(direction));
        }

        return List.of();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

}
