package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.HolographicStorageShellBlockEntity;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

public class HolographicStorageShellBlock extends Block implements EntityBlock {
    private final boolean stable;

    public HolographicStorageShellBlock(BlockBehaviour.Properties properties) {
        this(properties, false);
    }

    public HolographicStorageShellBlock(BlockBehaviour.Properties properties, boolean stable) {
        super(properties);
        this.stable = stable;
    }

    public boolean stable() {
        return stable;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HolographicStorageShellBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (!(level.getBlockEntity(pos) instanceof HolographicStorageShellBlockEntity shell)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            int inserted = shell.insert(stack, stack.getCount(), false);
            if (inserted > 0) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(inserted);
                }
                player.displayClientMessage(storageMessage(shell), true);
            } else {
                player.displayClientMessage(storageMessage(shell), true);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HolographicStorageShellBlockEntity shell) {
            player.displayClientMessage(storageMessage(shell), true);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
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
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HolographicStorageShellBlockEntity shell) {
            shell.loadFromStack(stack, level.registryAccess());
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level instanceof Level actualLevel
                && actualLevel.getBlockEntity(pos) instanceof HolographicStorageShellBlockEntity shell) {
            ItemStack stack = new ItemStack(this);
            shell.saveToStack(stack, actualLevel.registryAccess());
            return stack;
        }

        return super.getCloneItemStack(level, pos, state);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);

        if (blockEntity instanceof HolographicStorageShellBlockEntity shell && shell.getLevel() != null) {
            for (ItemStack drop : drops) {
                if (drop.getItem() == asItem()) {
                    shell.saveToStack(drop, shell.getLevel().registryAccess());
                }
            }
        }

        return drops;
    }

    private static Component storageMessage(HolographicStorageShellBlockEntity shell) {
        if (shell.isEmpty()) {
            return Component.literal("0/" + HolographicStorageShellBlockEntity.CAPACITY);
        }

        return Component.literal(shell.storedCount() + "/" + HolographicStorageShellBlockEntity.CAPACITY);
    }
}
