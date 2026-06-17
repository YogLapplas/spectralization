package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.geometry.SpatialModeCoupling;
import io.github.yoglappland.spectralization.optics.geometry.SpatialProfileElement;
import io.github.yoglappland.spectralization.optics.geometry.SpatialTransformContext;
import io.github.yoglappland.spectralization.tag.SpectralItemTags;
import javax.annotation.Nullable;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LensHolderBlock extends Block implements EntityBlock, OpticalElement, SpatialProfileElement {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final double LENS_TRANSMITTANCE = 0.96;
    private static final double LENS_REFLECTANCE = 0.02;

    private static final double[][] BASE_BOXES = {
            {0.0, 0.0, 5.0, 16.0, 1.0, 11.0},
            {1.0, 0.0, 3.0, 15.0, 1.0, 13.0},
            {2.0, 0.0, 2.0, 14.0, 1.0, 14.0},
            {3.0, 0.0, 1.0, 13.0, 1.0, 15.0},
            {5.0, 0.0, 0.0, 11.0, 1.0, 16.0},
            {4.0, 1.0, 3.0, 12.0, 2.0, 13.0},
            {5.0, 2.0, 6.0, 11.0, 3.0, 10.0},
            {7.0, 1.0, 1.0, 9.0, 3.0, 15.0},
            {7.0, 13.0, 1.0, 9.0, 15.0, 15.0},
            {7.0, 3.0, 1.0, 9.0, 13.0, 3.0},
            {7.0, 3.0, 13.0, 9.0, 13.0, 15.0},
            {7.0, 3.0, 3.0, 9.0, 5.0, 5.0},
            {7.0, 11.0, 3.0, 9.0, 13.0, 5.0},
            {7.0, 3.0, 11.0, 9.0, 5.0, 13.0},
            {7.0, 11.0, 11.0, 9.0, 13.0, 13.0}
    };

    private static final VoxelShape NORTH_SHAPE = buildShape(90.0);
    private static final VoxelShape EAST_SHAPE = buildShape(180.0);
    private static final VoxelShape SOUTH_SHAPE = buildShape(270.0);
    private static final VoxelShape WEST_SHAPE = buildShape(0.0);

    public LensHolderBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LensHolderBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        Direction positiveDirection = state.getValue(FACING);
        Direction negativeDirection = positiveDirection.getOpposite();
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();

        for (Direction incomingDirection : Direction.values()) {
            Direction transmittedDirection = incomingDirection.getOpposite();

            if (transmittedDirection != positiveDirection && transmittedDirection != negativeDirection) {
                continue;
            }

            builder.addRule(incomingDirection, transmittedDirection, CompiledOpticalNetwork.scale(LENS_TRANSMITTANCE));
            builder.addRule(incomingDirection, incomingDirection, CompiledOpticalNetwork.scale(LENS_REFLECTANCE));
        }

        return builder.build();
    }

    @Override
    public SpatialModeCoupling transformSpatialProfile(
            BeamEnvelope inputEnvelope,
            SpatialTransformContext context
    ) {
        if (!(context.level().getBlockEntity(context.pos()) instanceof LensHolderBlockEntity lensHolder)
                || !lensHolder.hasLens()
                || context.outgoingDirection() != context.incomingDirection().getOpposite()) {
            return SpatialModeCoupling.ordered(inputEnvelope);
        }

        return SpatialModeCoupling.ordered(lensHolder.lensProfile().transformTransmittedEnvelope(inputEnvelope));
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
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder && lensHolder.hasLens()) {
            removeLens(level, pos, player, lensHolder);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!stack.is(SpectralItemTags.LENS)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            lensHolder.setLens(stack);

            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder && lensHolder.hasLens()) {
            removeLens(level, pos, player, lensHolder);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder && lensHolder.hasLens()) {
            Block.popResource(level, pos, lensHolder.getLens().copy());
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static void removeLens(Level level, BlockPos pos, Player player, LensHolderBlockEntity lensHolder) {
        if (level.isClientSide) {
            return;
        }

        ItemStack removedLens = lensHolder.removeLens();

        if (!player.addItem(removedLens)) {
            Block.popResource(level, pos, removedLens);
        }
    }

    private static VoxelShape getShapeForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    private static VoxelShape buildShape(double degrees) {
        VoxelShape shape = Shapes.empty();

        for (double[] box : BASE_BOXES) {
            shape = Shapes.or(shape, rotateBox(box, degrees));
        }

        return shape.optimize();
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
