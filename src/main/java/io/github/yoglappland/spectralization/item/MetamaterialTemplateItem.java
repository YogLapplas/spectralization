package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialTemplateData;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialVector;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class MetamaterialTemplateItem extends Item {
    public MetamaterialTemplateItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        MetamaterialTemplateData data = MetamaterialTemplateData.fromStack(stack);
        MetamaterialVector vector = data.vector();

        tooltipComponents.add(Component.translatable(
                data.standard()
                        ? "item.spectralization.metamaterial_template.tooltip.standard"
                        : "item.spectralization.metamaterial_template.tooltip.custom",
                Component.translatable(data.presetTranslationKey())
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.metamaterial_template.tooltip.vector",
                vector.x(),
                vector.y(),
                vector.z()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.metamaterial_template.tooltip.channel",
                vector.channelIndex()
        ).withStyle(ChatFormatting.DARK_AQUA));
    }
}
