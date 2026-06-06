package io.github.yoglappland.spectralization.block;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MirrorBlock extends Block {
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 7);

    private static final double[][] BASE_BOXES = {
            {5.0, 0.0, 4.0, 11.0, 2.0, 12.0},
            {4.0, 0.0, 5.0, 12.0, 2.0, 11.0},
            {7.0, 2.0, 7.0, 9.0, 8.0, 9.0},
            {5.0, 8.0, 7.0, 11.0, 16.0, 9.0}
    };

    private static final VoxelShape[] SHAPES = buildShapes();

    public MirrorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0));
    }

    public static Set<Direction> getConnectedDirections(BlockState state) {
        return switch (state.getValue(ROTATION)) {
            case 2, 6 -> EnumSet.of(Direction.EAST, Direction.WEST);
            case 1, 3, 5, 7 -> EnumSet.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
            default -> EnumSet.of(Direction.NORTH, Direction.SOUTH);
        };
    }

    public static Direction getReflectedDirection(BlockState state, Direction incoming) {
        return switch (state.getValue(ROTATION)) {
            case 1, 5 -> switch (incoming) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.NORTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.SOUTH;
                default -> incoming;
            };
            case 2, 6 -> switch (incoming) {
                case EAST -> Direction.WEST;
                case WEST -> Direction.EAST;
                default -> incoming;
            };
            case 3, 7 -> switch (incoming) {
                case NORTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                case SOUTH -> Direction.EAST;
                case EAST -> Direction.SOUTH;
                default -> incoming;
            };
            default -> switch (incoming) {
                case NORTH -> Direction.SOUTH;
                case SOUTH -> Direction.NORTH;
                default -> incoming;
            };
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(ROTATION, getPlacementRotation(context.getHorizontalDirection()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(ROTATION)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(ROTATION)];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        rotateMirror(state, level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        rotateMirror(state, level, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 8));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    private static void rotateMirror(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(ROTATION, (state.getValue(ROTATION) + 1) & 7), 3);
        }
    }

    private static int getPlacementRotation(Direction direction) {
        return switch (direction) {
            case EAST -> 2;
            case SOUTH -> 4;
            case WEST -> 6;
            default -> 0;
        };
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[8];

        for (int rotation = 0; rotation < shapes.length; rotation++) {
            VoxelShape shape = Shapes.empty();

            for (double[] box : BASE_BOXES) {
                shape = Shapes.or(shape, rotateBox(box, -45.0 * rotation));
            }

            shapes[rotation] = shape.optimize();
        }

        return shapes;
    }

    private static VoxelShape rotateBox(double[] box, double degrees) {
        double radians = Math.toRadians(degrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double minX = 16.0;
        double minZ = 16.0;
        double maxX = 0.0;
        double maxZ = 0.0;

        for (double x : new double[]{box[0], box[3]}) {
            for (double z : new double[]{box[2], box[5]}) {
                double centeredX = x - 8.0;
                double centeredZ = z - 8.0;
                double rotatedX = centeredX * cos - centeredZ * sin + 8.0;
                double rotatedZ = centeredX * sin + centeredZ * cos + 8.0;

                minX = Math.min(minX, rotatedX);
                minZ = Math.min(minZ, rotatedZ);
                maxX = Math.max(maxX, rotatedX);
                maxZ = Math.max(maxZ, rotatedZ);
            }
        }

        return Block.box(minX, box[1], minZ, maxX, box[4], maxZ);
    }
}
