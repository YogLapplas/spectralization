package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public class SilverGlassBlock extends Block implements OpticalElement {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final double SIDE_TRANSMITTANCE = 0.96;
    private static final double SIDE_REFLECTANCE = 0.03;
    private static final double COATING_TRANSMITTANCE = 0.09;
    private static final double COATING_REFLECTANCE = 0.90;

    public SilverGlassBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public CompiledOpticalNetwork compileOpticalNetwork(BlockState state, Level level, BlockPos pos) {
        Direction coatedFace = state.getValue(FACING);
        Direction glassBackFace = coatedFace.getOpposite();
        CompiledOpticalNetwork.Builder builder = CompiledOpticalNetwork.builder();

        for (Direction incomingDirection : Direction.values()) {
            Direction transmittedDirection = incomingDirection.getOpposite();
            boolean throughCoatingAxis = incomingDirection == coatedFace || incomingDirection == glassBackFace;

            if (throughCoatingAxis) {
                builder.addRule(
                        incomingDirection,
                        transmittedDirection,
                        CompiledOpticalNetwork.scale(COATING_TRANSMITTANCE)
                );
                builder.addRule(
                        incomingDirection,
                        incomingDirection,
                        CompiledOpticalNetwork.scale(COATING_REFLECTANCE)
                );
            } else {
                builder.addRule(
                        incomingDirection,
                        transmittedDirection,
                        CompiledOpticalNetwork.scale(SIDE_TRANSMITTANCE)
                );
                builder.addRule(
                        incomingDirection,
                        incomingDirection,
                        CompiledOpticalNetwork.scale(SIDE_REFLECTANCE)
                );
            }
        }

        return builder.build();
    }

    @Override
    public OpticalResult interact(BeamPacket input, Direction incomingDirection, BlockState state, Level level, BlockPos pos) {
        return compileOpticalNetwork(state, level, pos).interact(input, incomingDirection);
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
}
