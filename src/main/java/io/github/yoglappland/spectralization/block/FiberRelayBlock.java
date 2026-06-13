package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.FiberRelayBlockEntity;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeBlock;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeKind;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeProfile;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FiberRelayBlock extends Block implements EntityBlock, FiberNodeBlock {
    private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);

    public FiberRelayBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FiberRelayBlockEntity(pos, state);
    }

    @Override
    public FiberNodeKind fiberNodeKind(BlockState state, LevelAccessor level, BlockPos pos) {
        return FiberNodeKind.RELAY;
    }

    @Override
    public FiberNodeProfile fiberNodeProfile(BlockState state, LevelAccessor level, BlockPos pos) {
        return FiberNodeProfile.BASIC_RELAY;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
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
