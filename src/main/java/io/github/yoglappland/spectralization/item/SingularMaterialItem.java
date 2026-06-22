package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.singular.SingularMaterialData;
import io.github.yoglappland.spectralization.optics.singular.SingularMaterialGenerator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class SingularMaterialItem extends Item {
    public SingularMaterialItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            SingularMaterialData.resolveInWorld(stack, serverLevel.getSeed());
        }
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            SingularMaterialData.resolveInWorld(stack, serverLevel.getSeed());
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        SingularMaterialData data = SingularMaterialData.fromStack(stack);
        SingularMaterialGenerator.Traits traits = SingularMaterialGenerator.traits(data.renderSeed());

        tooltipComponents.add(Component.translatable(
                "item.spectralization.singular_material.tooltip.source",
                data.sourceId()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.singular_material.tooltip.family",
                Component.translatable(traits.family().translationKey())
        ).withStyle(ChatFormatting.DARK_AQUA));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.singular_material.tooltip.traits",
                traits.resonance(),
                traits.entropy(),
                traits.phase(),
                traits.chroma()
        ).withStyle(ChatFormatting.GRAY));
        if (!data.resolved()) {
            tooltipComponents.add(Component.translatable(
                    "item.spectralization.singular_material.tooltip.unresolved"
            ).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
