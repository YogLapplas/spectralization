package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
    private static final Direction[] ROTATION_ORDER = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
            Direction.UP,
            Direction.DOWN
    };
    public static final double OUTPUT_POWER = 100.0;

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
            Direction nextFacing = getNextFacing(state.getValue(FACING));
            level.setBlock(pos, state.setValue(FACING, nextFacing), 3);
            player.displayClientMessage(
                    Component.literal("Creative light source facing: " + nextFacing.getSerializedName()),
                    true
            );
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
        PlaneWaveComponent component = new PlaneWaveComponent(
                FrequencyKey.DEBUG_VISIBLE,
                OUTPUT_POWER,
                direction,
                CoherenceKind.COHERENT
        );
        BeamPacket beam = BeamPacket.single(component, BeamEnvelope.DEFAULT_COLLIMATED);

        return List.of(new OutputBeam(direction, beam));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static Direction getNextFacing(Direction facing) {
        for (int i = 0; i < ROTATION_ORDER.length; i++) {
            if (ROTATION_ORDER[i] == facing) {
                return ROTATION_ORDER[(i + 1) % ROTATION_ORDER.length];
            }
        }

        return Direction.NORTH;
    }
}
