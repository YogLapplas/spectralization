package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.PassThroughSensorBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class PassThroughSensorBlock extends Block implements EntityBlock, OpticalElement {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public PassThroughSensorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PassThroughSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.PASS_THROUGH_SENSOR.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                PassThroughSensorBlockEntity.tick(tickerLevel, pos, (PassThroughSensorBlockEntity) blockEntity);
    }

    @Override
    public OpticalResult interact(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        if (input.isEmpty()) {
            return OpticalResult.empty();
        }

        Direction outgoingDirection = incomingDirection.getOpposite();
        Direction positiveZDirection = state.getValue(FACING);

        if (outgoingDirection != positiveZDirection && outgoingDirection != positiveZDirection.getOpposite()) {
            return OpticalResult.absorbed(input.totalPower());
        }

        if (level.getBlockEntity(pos) instanceof PassThroughSensorBlockEntity sensor) {
            sensor.receivePower(outgoingDirection == positiveZDirection, input.totalPower());
        }

        return OpticalResult.single(
                new OutputBeam(outgoingDirection, input.withDirection(outgoingDirection)),
                0.0,
                0.0
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
