package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.FiberOpticInterfaceBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeBlock;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeKind;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeProfile;
import io.github.yoglappland.spectralization.optics.geometry.SpatialModeCoupling;
import io.github.yoglappland.spectralization.optics.geometry.SpatialProfileElement;
import io.github.yoglappland.spectralization.optics.geometry.SpatialTransformContext;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FiberOpticInterfaceBlock extends Block implements EntityBlock, FiberNodeBlock, OpticalElement, SpatialProfileElement {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final double[] BASE_BOX = {3.0D, 0.0D, 3.0D, 13.0D, 9.0D, 13.0D};
    private static final VoxelShape DOWN_SHAPE = orientBox(Direction.DOWN);
    private static final VoxelShape UP_SHAPE = orientBox(Direction.UP);
    private static final VoxelShape NORTH_SHAPE = orientBox(Direction.NORTH);
    private static final VoxelShape EAST_SHAPE = orientBox(Direction.EAST);
    private static final VoxelShape SOUTH_SHAPE = orientBox(Direction.SOUTH);
    private static final VoxelShape WEST_SHAPE = orientBox(Direction.WEST);

    public FiberOpticInterfaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FiberOpticInterfaceBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    public FiberNodeKind fiberNodeKind(BlockState state, LevelAccessor level, BlockPos pos) {
        return FiberNodeKind.INTERFACE;
    }

    @Override
    public FiberNodeProfile fiberNodeProfile(BlockState state, LevelAccessor level, BlockPos pos) {
        return FiberNodeProfile.BASIC_INTERFACE;
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        return CompiledOpticalNetwork.builder().build();
    }

    @Override
    public SpatialModeCoupling transformSpatialProfile(
            BeamEnvelope inputEnvelope,
            SpatialTransformContext context
    ) {
        FiberNodeProfile profile = fiberNodeProfile(context.state(), context.level(), context.pos());
        BeamEnvelope outputEnvelope = BeamEnvelope.collimated(profile.coreRadius())
                .withBeamQuality(inputEnvelope.beamQuality())
                .withScatter(inputEnvelope.scatter());
        return SpatialModeCoupling.ordered(outputEnvelope);
    }

    @Override
    public OpticalResult interact(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return compileOpticalNetwork(state, level, pos).interact(input, incomingDirection);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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

    private static VoxelShape orientBox(Direction facing) {
        double minX = BASE_BOX[0];
        double minY = BASE_BOX[1];
        double minZ = BASE_BOX[2];
        double maxX = BASE_BOX[3];
        double maxY = BASE_BOX[4];
        double maxZ = BASE_BOX[5];

        return switch (facing) {
            case UP -> Block.box(minX, 16.0D - maxY, minZ, maxX, 16.0D - minY, maxZ);
            case NORTH -> Block.box(minX, minZ, minY, maxX, maxZ, maxY);
            case SOUTH -> Block.box(minX, minZ, 16.0D - maxY, maxX, maxZ, 16.0D - minY);
            case WEST -> Block.box(minY, minZ, minX, maxY, maxZ, maxX);
            case EAST -> Block.box(16.0D - maxY, minZ, minX, 16.0D - minY, maxZ, maxX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }
}
