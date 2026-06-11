package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.PhotonicGradientGeneratorBlockEntity;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PhotonicGradientGeneratorBlock extends Block implements EntityBlock {
    public PhotonicGradientGeneratorBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhotonicGradientGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != SpectralBlockEntities.PHOTONIC_GRADIENT_GENERATOR.get()) {
            return null;
        }

        return (tickerLevel, pos, tickerState, blockEntity) ->
                PhotonicGradientGeneratorBlockEntity.tick(tickerLevel, pos, (PhotonicGradientGeneratorBlockEntity) blockEntity);
    }
}
