package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.machine.RecursiveGeneratorState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class RecursiveGeneratorBlockItem extends BlockItem {
    public RecursiveGeneratorBlockItem(Block block, Properties properties) {
        super(block, properties.stacksTo(64));
    }

    public static void normalizeEmptyStack(ItemStack stack) {
        if (!RecursiveGeneratorState.fromStack(stack).hasState()) {
            RecursiveGeneratorState.clearStack(stack);
        }
    }

    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        super.verifyComponentsAfterLoad(stack);
        normalizeEmptyStack(stack);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return RecursiveGeneratorState.fromStack(stack).hasState() ? 1 : getDefaultMaxStackSize();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide) {
            normalizeEmptyStack(stack);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        RecursiveGeneratorState state = RecursiveGeneratorState.fromStack(stack);
        if (!state.hasState()) {
            return;
        }
        tooltip.add(Component.translatable("item.spectralization.recursive_generator.tooltip.depth", state.activeLayerCount()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.spectralization.recursive_generator.tooltip.energy", state.energy()).withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < RecursiveGeneratorState.MAX_LAYERS; i++) {
            int remaining = state.remainingAt(i);
            if (remaining > 0) {
                tooltip.add(Component.translatable("item.spectralization.recursive_generator.tooltip.remaining", i + 1, remaining / 20.0F).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
