package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.HolographicStorageMainCoreBlockEntity;
import io.github.yoglappland.spectralization.menu.HolographicStorageMenu;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HolographicStorageScreenBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    private static final VoxelShape DOWN_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);
    private static final VoxelShape UP_SHAPE = Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0);
    private static final VoxelShape EAST_SHAPE = Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0);

    public HolographicStorageScreenBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.DOWN)
                .setValue(CONNECTED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction attachmentDirection = context.getClickedFace().getOpposite();
        return defaultBlockState()
                .setValue(FACING, attachmentDirection)
                .setValue(CONNECTED, isConnected(context.getLevel(), context.getClickedPos(), attachmentDirection));
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
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide) {
            Optional<HolographicStorageMainCoreBlockEntity> maybeCore = findCore(level, pos, state);
            if (maybeCore.isPresent()) {
                HolographicStorageMainCoreBlockEntity core = maybeCore.get();
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) ->
                                new HolographicStorageMenu(containerId, inventory, core),
                        Component.translatable("container.spectralization.holographic_storage")
                ));
            } else {
                player.displayClientMessage(Component.translatable(
                        "block.spectralization.holographic_storage_screen.message.unconnected"
                ), true);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshVisualState(level, pos);
        HolographicStorageCrystalBlock.refreshNearbyStorageVisuals(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock())) {
            HolographicStorageCrystalBlock.refreshNearbyStorageVisuals(level, pos);
        }
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        refreshVisualState(level, pos);
        HolographicStorageCrystalBlock.refreshNearbyStorageVisuals(level, pos);
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
        builder.add(FACING, CONNECTED);
    }

    public static void refreshVisualState(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HolographicStorageScreenBlock)) {
            return;
        }

        boolean connected = isConnected(level, pos, state.getValue(FACING));
        BlockState updatedState = state.setValue(CONNECTED, connected);
        if (!state.equals(updatedState)) {
            level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean isConnected(Level level, BlockPos pos, Direction attachmentDirection) {
        BlockPos attachedPos = pos.relative(attachmentDirection);
        BlockState attachedState = level.getBlockState(attachedPos);
        if (!HolographicStorageMultiblock.isScreenAttachableStorage(attachedState)) {
            return false;
        }

        if (attachedState.getBlock() instanceof HolographicStorageCrystalBlock) {
            return attachedState.getValue(HolographicStorageCrystalBlock.LINKED)
                    || HolographicStorageMultiblock.isRecognizedCrystal(level, attachedPos);
        }

        return HolographicStorageMultiblock.isRecognizedCrystal(level, attachedPos);
    }

    private static Optional<HolographicStorageMainCoreBlockEntity> findCore(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof HolographicStorageScreenBlock) || !state.getValue(CONNECTED)) {
            return Optional.empty();
        }

        BlockPos attachedCrystalPos = pos.relative(state.getValue(FACING));
        Optional<BlockPos> maybeCorePos = HolographicStorageMultiblock.findCoreForMember(level, attachedCrystalPos);
        if (maybeCorePos.isEmpty()) {
            return Optional.empty();
        }

        if (level.getBlockEntity(maybeCorePos.get()) instanceof HolographicStorageMainCoreBlockEntity core) {
            return Optional.of(core);
        }

        return Optional.empty();
    }

    private static VoxelShape getShapeForFacing(Direction facing) {
        return switch (facing) {
            case DOWN -> DOWN_SHAPE;
            case UP -> UP_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
        };
    }
}
