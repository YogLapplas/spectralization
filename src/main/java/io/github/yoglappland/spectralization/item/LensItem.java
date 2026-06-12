package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.lens.LensProfile;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class LensItem extends Item {
    public LensItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        LensProfile profile = LensProfile.fromStack(stack);

        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.tag",
                profile.tag()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.focal_length",
                profile.focalLength()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.aperture",
                profile.aperture()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.quality",
                Component.translatable(profile.qualityNameKey())
        ).withStyle(ChatFormatting.GRAY));
    }
}
