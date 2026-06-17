package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.HorizontalOpticalOrientation;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

public class MirrorBlock extends Block implements OpticalElement {
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 7);
    private static final double REFLECTANCE = 0.9;

    private static final double[][] BASE_BOXES = {
            {0.0, 0.0, 5.0, 16.0, 1.0, 11.0},
            {1.0, 0.0, 3.0, 15.0, 1.0, 5.0},
            {2.0, 0.0, 2.0, 14.0, 1.0, 3.0},
            {3.0, 0.0, 1.0, 13.0, 1.0, 2.0},
            {1.0, 0.0, 11.0, 15.0, 1.0, 13.0},
            {2.0, 0.0, 13.0, 14.0, 1.0, 14.0},
            {5.0, 0.0, 0.0, 11.0, 1.0, 1.0},
            {3.0, 0.0, 14.0, 13.0, 1.0, 15.0},
            {5.0, 0.0, 15.0, 11.0, 1.0, 16.0}
    };

    private static final double[][] OPTIC_BOXES = {
            {7.0, 1.0, 1.0, 9.0, 3.0, 15.0},
            {7.0, 3.0, 13.0, 9.0, 13.0, 15.0},
            {7.0, 3.0, 1.0, 9.0, 13.0, 3.0},
            {7.0, 13.0, 1.0, 9.0, 15.0, 15.0},
            {4.0, 1.0, 3.0, 7.0, 2.0, 13.0},
            {9.0, 1.0, 3.0, 12.0, 2.0, 13.0},
            {7.5, 3.0, 3.0, 8.5, 13.0, 13.0},
            {5.0, 2.0, 6.0, 7.0, 3.0, 10.0},
            {9.0, 2.0, 6.0, 11.0, 3.0, 10.0}
    };

    private static final double[][] DIAGONAL_BOXES = {
            {0.0, 0.0, 0.0, 16.0, 1.0, 16.0},
            {3.0, 1.0, 3.0, 13.0, 15.0, 13.0}
    };

    private static final VoxelShape[] SHAPES = buildShapes();

    public MirrorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0));
    }

    public static Set<Direction> getConnectedDirections(BlockState state) {
        return HorizontalOpticalOrientation.mirrorActiveSides(state.getValue(ROTATION));
    }

    public static Direction getReflectedDirection(BlockState state, Direction incoming) {
        return HorizontalOpticalOrientation.reflectMirror(state.getValue(ROTATION), incoming);
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        Set<Direction> connectedDirections = getConnectedDirections(state);
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();

        for (Direction incomingDirection : Direction.values()) {
            if (connectedDirections.contains(incomingDirection)) {
                builder.addRule(
                        incomingDirection,
                        getReflectedDirection(state, incomingDirection),
                        CompiledOpticalNetwork.scale(REFLECTANCE)
                );
            } else {
                builder.addRule(
                        incomingDirection,
                        incomingDirection.getOpposite(),
                        CompiledOpticalNetwork.passThrough()
                );
            }
        }

        return builder.build();
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

    protected static void rotateMirror(BlockState state, Level level, BlockPos pos) {
        if (!level.isClientSide) {
            level.setBlock(pos, rotateOnce(state), 3);
            OpticalTraceCache.markChanged(level, pos, OpticalDirtyKind.TOPOLOGY);
        }
    }

    protected static BlockState rotateOnce(BlockState state) {
        return state.setValue(ROTATION, (state.getValue(ROTATION) + 1) & 7);
    }

    private static int getPlacementRotation(Direction direction) {
        return HorizontalOpticalOrientation.rotationForHorizontalFacing(direction);
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape[] shapes = new VoxelShape[8];

        for (int rotation = 0; rotation < shapes.length; rotation++) {
            VoxelShape shape = Shapes.empty();

            if ((rotation & 1) == 1) {
                for (double[] box : DIAGONAL_BOXES) {
                    shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
                }

                shapes[rotation] = shape.optimize();
                continue;
            }

            double baseDegrees = baseModelRotationDegrees(rotation);
            double opticDegrees = opticModelRotationDegrees(rotation);

            for (double[] box : BASE_BOXES) {
                shape = Shapes.or(shape, rotateBox(box, baseDegrees));
            }

            for (double[] box : OPTIC_BOXES) {
                shape = Shapes.or(shape, rotateBox(box, opticDegrees));
            }

            shapes[rotation] = shape.optimize();
        }

        return shapes;
    }

    private static double baseModelRotationDegrees(int rotation) {
        return 90.0 * (((rotation >> 1) + 1) & 3);
    }

    private static double opticModelRotationDegrees(int rotation) {
        return baseModelRotationDegrees(rotation) - (rotation % 2 == 0 ? 0.0 : 45.0);
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
