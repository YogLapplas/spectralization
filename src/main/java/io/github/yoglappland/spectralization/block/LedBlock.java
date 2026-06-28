package io.github.yoglappland.spectralization.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LedBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5.0, 0.0, 4.0, 11.0, 1.0, 12.0),
            Block.box(6.0, 1.0, 5.0, 10.0, 1.5, 11.0),
            Block.box(6.5, 1.5, 5.5, 9.5, 2.0, 10.5)
    ).optimize();

    public LedBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
