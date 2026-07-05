package io.github.yoglappland.spectralization.block;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DirectionalFacingBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public DirectionalFacingBlock(Properties properties) {
        this(properties, Direction.UP);
    }

    public DirectionalFacingBlock(Properties properties, Direction defaultFacing) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, defaultFacing));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
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

    protected static VoxelShape buildLocalUpShape(Direction facing, double[][] boxes) {
        VoxelShape shape = Shapes.empty();

        for (double[] box : boxes) {
            shape = Shapes.or(shape, orientLocalUpBox(facing, box));
        }

        return shape.optimize();
    }

    private static VoxelShape orientLocalUpBox(Direction facing, double[] box) {
        double minX = box[0];
        double minY = box[1];
        double minZ = box[2];
        double maxX = box[3];
        double maxY = box[4];
        double maxZ = box[5];

        return switch (facing) {
            case DOWN -> Block.box(minX, 16.0D - maxY, minZ, maxX, 16.0D - minY, maxZ);
            case NORTH -> Block.box(minX, minZ, 16.0D - maxY, maxX, maxZ, 16.0D - minY);
            case EAST -> Block.box(minY, minZ, minX, maxY, maxZ, maxX);
            case SOUTH -> Block.box(minX, minZ, minY, maxX, maxZ, maxY);
            case WEST -> Block.box(16.0D - maxY, minZ, minX, 16.0D - minY, maxZ, maxX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }
}
