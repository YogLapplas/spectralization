package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.lens.LensKind;
import io.github.yoglappland.spectralization.optics.lens.LensProfile;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class LensItem extends EnchantableItem {
    public LensItem(Properties properties) {
        this(properties, 0);
    }

    public LensItem(Properties properties, int enchantmentValue) {
        super(properties, enchantmentValue);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        LensProfile profile = LensProfile.fromStack(stack);
        LensKind kind = LensKind.byId(profile.tag());

        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.kind",
                Component.translatable(kind.translationKey())
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.parameter",
                Component.translatable(kind.parameterKey()),
                profile.focalLengthText()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.quality",
                Component.translatable(profile.qualityNameKey())
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.material",
                Component.translatable(profile.materialProfile().translationKey())
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.lens.tooltip.transmittance",
                profile.transmittancePercent()
        ).withStyle(ChatFormatting.DARK_GRAY));
    }
}
