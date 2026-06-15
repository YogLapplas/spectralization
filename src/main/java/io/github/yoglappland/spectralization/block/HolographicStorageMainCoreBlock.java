package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.HolographicStorageMainCoreBlockEntity;
import io.github.yoglappland.spectralization.menu.HolographicStorageCoreMenu;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

public class HolographicStorageMainCoreBlock extends HolographicStorageCrystalBlock implements EntityBlock {
    public HolographicStorageMainCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        return defaultBlockState()
                .setValue(ERROR, HolographicStorageMultiblock.isCoreTooClose(level, pos));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshErrorState(level, pos);
        refreshNearbyStorageVisuals(level, pos);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HolographicStorageMainCoreBlockEntity core) {
            core.loadFromStack(stack);
        }
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
            if (level.getBlockEntity(pos) instanceof HolographicStorageMainCoreBlockEntity core) {
                player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) ->
                                new HolographicStorageCoreMenu(containerId, inventory, core),
                        Component.translatable("container.spectralization.holographic_storage_core")
                ));
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HolographicStorageMainCoreBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof HolographicStorageMainCoreBlockEntity core) {
            ItemStack stack = new ItemStack(this);
            core.saveToStack(stack);
            return stack;
        }

        return super.getCloneItemStack(level, pos, state);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);

        if (blockEntity instanceof HolographicStorageMainCoreBlockEntity core) {
            for (ItemStack drop : drops) {
                if (drop.getItem() == asItem()) {
                    core.saveToStack(drop);
                }
            }
        }

        return drops;
    }

    private static void refreshErrorState(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HolographicStorageMainCoreBlock) || state.getValue(ERROR)) {
            return;
        }

        if (HolographicStorageMultiblock.isCoreTooClose(level, pos)) {
            level.setBlock(pos, state.setValue(ERROR, true), Block.UPDATE_CLIENTS);
        }
    }
}
