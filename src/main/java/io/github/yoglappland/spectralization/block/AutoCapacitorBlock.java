package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.AutoCapacitorBlockEntity;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class AutoCapacitorBlock extends HorizontalFacingEntityBlock {
    private static final VoxelShape SHAPE = shapeFromModelBoxes();

    public AutoCapacitorBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoCapacitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.AUTO_CAPACITOR.get()) {
            return null;
        }

        return (tickLevel, pos, tickState, blockEntity) ->
                AutoCapacitorBlockEntity.tick(tickLevel, pos, (AutoCapacitorBlockEntity) blockEntity);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    private static VoxelShape shapeFromModelBoxes() {
        VoxelShape shape = Shapes.empty();
        double[][] boxes = {
                {0, 11, 0, 16, 12, 16},
                {0, 13, 0, 16, 15, 16},
                {0, 0, 0, 16, 2, 16},
                {0, 9, 0, 16, 10, 16},
                {0, 5, 0, 16, 6, 16},
                {0, 7, 0, 16, 8, 16},
                {0, 3, 0, 16, 4, 16},
                {3.5, 15, 3.5, 5.5, 16, 5.5},
                {10.5, 15, 10.5, 12.5, 16, 12.5},
                {3, 1, 3, 6, 15.5, 6},
                {10, 1, 10, 13, 15.5, 13}
        };

        for (double[] box : boxes) {
            shape = Shapes.or(shape, Block.box(box[0], box[1], box[2], box[3], box[4], box[5]));
        }

        return shape.optimize();
    }
}
