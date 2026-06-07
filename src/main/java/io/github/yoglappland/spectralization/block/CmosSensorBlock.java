package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.CmosSensorBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalReceiver;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CmosSensorBlock extends Block implements EntityBlock, OpticalReceiver {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty LOGARITHMIC = BooleanProperty.create("logarithmic");
    private static final double FULL_SIGNAL_POWER = 100.0;

    private static final VoxelShape UP_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0);
    private static final VoxelShape DOWN_SHAPE = Block.box(0.0, 13.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 13.0, 16.0, 16.0, 16.0);
    private static final VoxelShape EAST_SHAPE = Block.box(0.0, 0.0, 0.0, 3.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 3.0);
    private static final VoxelShape WEST_SHAPE = Block.box(13.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public CmosSensorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.DOWN)
                .setValue(POWER, 0)
                .setValue(LOGARITHMIC, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            boolean logarithmic = !state.getValue(LOGARITHMIC);
            BlockState nextState = state.setValue(LOGARITHMIC, logarithmic);
            double currentPower = 0.0;

            if (level.getBlockEntity(pos) instanceof CmosSensorBlockEntity cmosSensor) {
                currentPower = cmosSensor.getPowerForSignal();
            }

            setSignalFromPower(level, pos, nextState, currentPower);
            player.displayClientMessage(
                    Component.literal("CMOS sensor scale: " + (logarithmic ? "logarithmic" : "linear")),
                    true
            );
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CmosSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.CMOS_SENSOR.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                CmosSensorBlockEntity.tick(tickerLevel, pos, tickerState, (CmosSensorBlockEntity) blockEntity);
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        Direction receivingSide = getReceivingSide(state);
        Direction blockedBackSide = state.getValue(FACING);
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();

        for (Direction incomingDirection : Direction.values()) {
            if (incomingDirection == receivingSide || incomingDirection == blockedBackSide) {
                continue;
            }

            builder.addRule(
                    incomingDirection,
                    incomingDirection.getOpposite(),
                    CompiledOpticalNetwork.passThrough()
            );
        }

        return builder
                .interactionEffect((input, incomingDirection, result) -> {
                    if (incomingDirection != receivingSide) {
                        return;
                    }

                    double detectedPower = input.totalPower();

                    if (level.getBlockEntity(pos) instanceof CmosSensorBlockEntity cmosSensor) {
                        cmosSensor.receivePower(detectedPower);
                    } else {
                        setSignalFromPower(level, pos, state, detectedPower);
                    }
                })
                .build();
    }

    @Override
    public OpticalResult receiveBeam(
            BeamPacket input,
            Direction incomingDirection,
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return compileOpticalNetwork(state, level, pos).interact(input, incomingDirection);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing);

        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, facing.getOpposite());
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (direction == state.getValue(FACING) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFront(getReceivingSide(state));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFront(getReceivingSide(state));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWER, LOGARITHMIC);
    }

    public static void setSignalFromPower(Level level, BlockPos pos, BlockState state, double power) {
        if (level.isClientSide) {
            return;
        }

        int signal = calculateSignal(power, state.getValue(LOGARITHMIC));
        BlockState updatedState = state.setValue(POWER, signal);

        if (level.getBlockState(pos).equals(updatedState)) {
            return;
        }

        level.setBlock(pos, updatedState, 3);
        level.updateNeighborsAt(pos, state.getBlock());
    }

    private static int calculateSignal(double power, boolean logarithmic) {
        if (power <= 0.0) {
            return 0;
        }

        double normalizedPower = logarithmic
                ? Math.log1p(power) / Math.log1p(FULL_SIGNAL_POWER)
                : power / FULL_SIGNAL_POWER;

        return Mth.clamp((int) Math.ceil(normalizedPower * 15.0), 0, 15);
    }

    private static Direction getReceivingSide(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    private static VoxelShape getShapeForFront(Direction front) {
        return switch (front) {
            case DOWN -> DOWN_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> UP_SHAPE;
        };
    }
}
