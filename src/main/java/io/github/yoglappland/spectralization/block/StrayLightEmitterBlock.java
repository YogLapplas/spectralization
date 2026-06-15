package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.StrayLightEmitterBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.EnvironmentLightSpectra;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StrayLightEmitterBlock extends Block implements EntityBlock, OpticalSource {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final double[][] MODEL_BOXES = {
            {0.0, 0.0, 0.0, 16.0, 8.0, 16.0},
            {1.0, 10.0, 15.0, 15.0, 11.0, 16.0},
            {15.0, 10.0, 1.0, 16.0, 11.0, 15.0},
            {1.0, 10.0, 0.0, 15.0, 11.0, 1.0},
            {0.0, 10.0, 1.0, 1.0, 11.0, 15.0},
            {1.0, 9.0, 14.0, 14.0, 10.0, 15.0},
            {1.0, 9.0, 1.0, 2.0, 10.0, 14.0},
            {2.0, 9.0, 1.0, 15.0, 10.0, 2.0},
            {14.0, 9.0, 2.0, 15.0, 10.0, 15.0},
            {2.0, 8.0, 13.0, 13.0, 9.0, 14.0},
            {2.0, 8.0, 2.0, 3.0, 9.0, 13.0},
            {3.0, 8.0, 2.0, 14.0, 9.0, 3.0},
            {13.0, 8.0, 3.0, 14.0, 9.0, 14.0}
    };
    private static final VoxelShape DOWN_SHAPE = buildShape(Direction.DOWN);
    private static final VoxelShape UP_SHAPE = buildShape(Direction.UP);
    private static final VoxelShape NORTH_SHAPE = buildShape(Direction.NORTH);
    private static final VoxelShape EAST_SHAPE = buildShape(Direction.EAST);
    private static final VoxelShape SOUTH_SHAPE = buildShape(Direction.SOUTH);
    private static final VoxelShape WEST_SHAPE = buildShape(Direction.WEST);

    private final int sampleRadius;
    private final double efficiency;

    public StrayLightEmitterBlock(Properties properties, int sampleRadius, double efficiency) {
        super(properties);
        this.sampleRadius = sampleRadius;
        this.efficiency = efficiency;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StrayLightEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.STRAY_LIGHT_EMITTER.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                StrayLightEmitterBlockEntity.tick(tickerLevel, pos, tickerState, (StrayLightEmitterBlockEntity) blockEntity);
    }

    @Override
    public List<OutputBeam> getOutputBeams(BlockState state, Level level, BlockPos pos) {
        Direction outputDirection = state.getValue(FACING);
        BeamPacket packet = EnvironmentLightSpectra.collect(level, pos, outputDirection, sampleRadius, efficiency);

        if (packet.isEmpty()) {
            return List.of();
        }

        return List.of(new OutputBeam(outputDirection, packet));
    }

    public long sampleSignature(BlockState state, Level level, BlockPos pos) {
        return EnvironmentLightSpectra.sampleSignature(level, pos, state.getValue(FACING), sampleRadius);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static VoxelShape getShapeForFacing(Direction facing) {
        return switch (facing) {
            case UP -> UP_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> DOWN_SHAPE;
        };
    }

    private static VoxelShape buildShape(Direction facing) {
        VoxelShape shape = Shapes.empty();
        for (double[] box : MODEL_BOXES) {
            shape = Shapes.or(shape, orientBox(facing, box));
        }
        return shape.optimize();
    }

    private static VoxelShape orientBox(Direction facing, double[] box) {
        double minX = box[0];
        double minY = box[1];
        double minZ = box[2];
        double maxX = box[3];
        double maxY = box[4];
        double maxZ = box[5];

        return switch (facing) {
            case UP -> Block.box(minX, 16.0 - maxY, minZ, maxX, 16.0 - minY, maxZ);
            case NORTH -> Block.box(minX, minZ, minY, maxX, maxZ, maxY);
            case SOUTH -> Block.box(minX, minZ, 16.0 - maxY, maxX, maxZ, 16.0 - minY);
            case WEST -> Block.box(minY, minZ, minX, maxY, maxZ, maxX);
            case EAST -> Block.box(16.0 - maxY, minZ, minX, 16.0 - minY, maxZ, maxX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }
}
