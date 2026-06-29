package io.github.yoglappland.spectralization.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

public final class MachineContentsDropper {
    private MachineContentsDropper() {
    }

    public static void dropFromBlockEntity(BlockState state, Level level, BlockPos pos, BlockState newState) {
        if (level.isClientSide || state.is(newState.getBlock())) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof DropsContentsOnRemove drops) {
            drops.dropContents(level, pos);
        }
    }

    public static void dropItemHandler(Level level, BlockPos pos, IItemHandler items) {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);

            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
            }
        }
    }
}
