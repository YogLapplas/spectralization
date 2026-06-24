package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.FiberLaserBlockEntity;
import io.github.yoglappland.spectralization.menu.FiberLaserMenu;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.fiber.FiberLikeFaceBlock;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileTransfer;
import io.github.yoglappland.spectralization.optics.geometry.SpatialModeCoupling;
import io.github.yoglappland.spectralization.optics.geometry.SpatialProfileElement;
import io.github.yoglappland.spectralization.optics.geometry.SpatialTransformContext;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FiberLaserBlock extends Block implements EntityBlock, OpticalElement, SpatialProfileElement, FiberLikeFaceBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final double[][] MODEL_BOXES_EAST = {
            {6, 2, 0, 10, 3, 2},
            {5, 3, 0, 6, 4, 2},
            {4, 4, 0, 5, 5, 2},
            {3, 5, 0, 4, 6, 2},
            {2, 6, 0, 3, 10, 2},
            {10, 3, 0, 11, 4, 2},
            {11, 4, 0, 12, 5, 2},
            {12, 5, 0, 13, 6, 2},
            {12, 10, 0, 13, 11, 2},
            {13, 6, 0, 14, 10, 2},
            {11, 11, 0, 12, 12, 2},
            {4, 11, 0, 5, 12, 2},
            {10, 12, 0, 11, 13, 2},
            {6, 13, 0, 10, 14, 2},
            {5, 12, 0, 6, 13, 2},
            {3, 10, 0, 4, 11, 2},
            {6, 2, 14, 10, 3, 16},
            {5, 3, 14, 6, 4, 16},
            {4, 4, 14, 5, 5, 16},
            {3, 5, 14, 4, 6, 16},
            {2, 6, 14, 3, 10, 16},
            {10, 3, 14, 11, 4, 16},
            {11, 4, 14, 12, 5, 16},
            {12, 5, 14, 13, 6, 16},
            {12, 10, 14, 13, 11, 16},
            {13, 6, 14, 14, 10, 16},
            {11, 11, 14, 12, 12, 16},
            {4, 11, 14, 5, 12, 16},
            {10, 12, 14, 11, 13, 16},
            {6, 13, 14, 10, 14, 16},
            {5, 12, 14, 6, 13, 16},
            {3, 10, 14, 4, 11, 16},
            {6, 3, 2, 10, 4, 14},
            {5, 4, 2, 6, 5, 14},
            {4, 4, 2, 5, 6, 14},
            {12, 6, 11, 13, 10, 14},
            {11, 4, 2, 12, 6, 14},
            {10, 4, 2, 11, 5, 14},
            {6, 12, 2, 10, 13, 14},
            {10, 11, 2, 11, 12, 14},
            {11, 10, 2, 12, 12, 14},
            {5, 11, 2, 6, 12, 14},
            {4, 10, 2, 5, 12, 14},
            {12, 10, 4, 13, 11, 12},
            {12, 5, 4, 13, 6, 12},
            {12, 6, 2, 13, 10, 5},
            {3, 10, 4, 4, 11, 12},
            {3, 6, 2, 4, 10, 5},
            {3, 6, 11, 4, 10, 14},
            {3, 5, 4, 4, 6, 12},
            {12, 6, 5, 12.5, 10, 11},
            {3.5, 6, 5, 4, 10, 11},
            {0, 0, 0, 16, 1, 1},
            {0, 15, 0, 16, 16, 1},
            {0, 1, 0, 1, 15, 1},
            {15, 1, 0, 16, 15, 1},
            {0, 0, 15, 16, 1, 16},
            {0, 15, 15, 16, 16, 16},
            {0, 1, 15, 1, 15, 16},
            {15, 1, 15, 16, 15, 16},
            {7, 1, 0, 9, 2, 1},
            {0, 0, 0, 2, 1, 1},
            {7, 14, 0, 9, 15, 1},
            {7, 14, 15, 9, 15, 16},
            {7, 1, 15, 9, 2, 16},
            {1, 7, 0, 2, 9, 1},
            {14, 7, 0, 15, 9, 1},
            {14, 7, 15, 15, 9, 16},
            {1, 7, 15, 2, 9, 16},
            {6, 4, 2, 10, 6, 14},
            {6, 10, 2, 10, 12, 14},
            {10, 6, 2, 12, 10, 14},
            {4, 6, 2, 6, 10, 14},
            {5, 10, 2, 6, 11, 14},
            {5, 5, 2, 6, 6, 14},
            {10, 5, 2, 11, 6, 14},
            {10, 10, 2, 11, 11, 14},
            {6, 6, 2, 7, 7, 14},
            {6, 9, 2, 7, 10, 14},
            {9, 9, 2, 10, 10, 14},
            {9, 6, 2, 10, 7, 14},
            {7, 9, 3, 9, 10, 13},
            {6, 7, 3, 10, 9, 13},
            {7, 6, 3, 9, 7, 13}
    };
    private static final VoxelShape SHAPE_EAST = shapeFromModelBoxes();
    private static final VoxelShape SHAPE_SOUTH = rotateClockwise(SHAPE_EAST);
    private static final VoxelShape SHAPE_WEST = rotateClockwise(SHAPE_SOUTH);
    private static final VoxelShape SHAPE_NORTH = rotateClockwise(SHAPE_WEST);

    public FiberLaserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FiberLaserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.FIBER_LASER.get()) {
            return null;
        }

        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof FiberLaserBlockEntity laser) {
                FiberLaserBlockEntity.tick(tickLevel, pos, laser);
            }
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        Direction positiveDirection = opticalAxisPositive(state);
        Direction negativeDirection = positiveDirection.getOpposite();
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();

        builder.addRule(positiveDirection, negativeDirection, CompiledOpticalNetwork.passThrough());
        builder.addRule(negativeDirection, positiveDirection, CompiledOpticalNetwork.passThrough());
        return builder.build();
    }

    @Override
    public SpatialModeCoupling transformSpatialProfile(BeamEnvelope inputEnvelope, SpatialTransformContext context) {
        return SpatialModeCoupling.ordered(inputEnvelope);
    }

    @Override
    public BeamProfileTransfer transformProfileState(BeamProfileKey inputProfile, SpatialTransformContext context) {
        return BeamProfileTransfer.of(inputProfile, 1.0D);
    }

    @Override
    public boolean isFiberLikeFace(BlockState state, LevelAccessor level, BlockPos pos, Direction face) {
        Direction positiveDirection = opticalAxisPositive(state);
        return face == positiveDirection || face == positiveDirection.getOpposite();
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FiberLaserBlockEntity laser) {
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, menuPlayer) -> new FiberLaserMenu(containerId, inventory, laser),
                    Component.translatable("container.spectralization.fiber_laser")
            ));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForFacing(state.getValue(FACING));
    }

    private static Direction opticalAxisPositive(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForFacing(state.getValue(FACING));
    }

    private static VoxelShape shapeForFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_EAST;
        };
    }

    private static VoxelShape shapeFromModelBoxes() {
        VoxelShape shape = Shapes.empty();

        for (double[] box : MODEL_BOXES_EAST) {
            shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
        }

        return shape.optimize();
    }

    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape rotated = Shapes.empty();
        List<AABB> boxes = shape.toAabbs();

        for (AABB box : boxes) {
            rotated = Shapes.or(
                    rotated,
                    Shapes.box(
                            1.0 - box.maxZ,
                            box.minY,
                            box.minX,
                            1.0 - box.minZ,
                            box.maxY,
                            box.maxX
                    )
            );
        }

        return rotated.optimize();
    }
}
