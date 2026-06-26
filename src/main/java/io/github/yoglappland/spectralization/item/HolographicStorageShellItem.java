package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.blockentity.HolographicStorageShellBlockEntity;
import io.github.yoglappland.spectralization.storage.HolographicStorageEntry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class HolographicStorageShellItem extends BlockItem {
    public HolographicStorageShellItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide && !HolographicStorageShellBlockEntity.hasSavedStorage(stack)) {
            HolographicStorageShellBlockEntity.clearSavedStorage(stack);
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        HolographicStorageShellBlockEntity.entryFromStack(stack, context.registries())
                .ifPresent(entry -> appendStorageTooltip(tooltipComponents, entry));
    }

    private static void appendStorageTooltip(List<Component> tooltipComponents, HolographicStorageEntry entry) {
        tooltipComponents.add(Component.translatable(
                "item.spectralization.holographic_storage_shell.tooltip.stored",
                entry.stack().getHoverName(),
                entry.count(),
                HolographicStorageShellBlockEntity.CAPACITY
        ).withStyle(ChatFormatting.AQUA));
    }
}
