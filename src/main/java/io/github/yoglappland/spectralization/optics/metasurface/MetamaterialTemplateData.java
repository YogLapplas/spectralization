package io.github.yoglappland.spectralization.optics.metasurface;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record MetamaterialTemplateData(
        MetamaterialVector vector,
        boolean standard,
        String presetId
) {
    private static final String X_KEY = "spectralization_metamaterial_x";
    private static final String Y_KEY = "spectralization_metamaterial_y";
    private static final String Z_KEY = "spectralization_metamaterial_z";
    private static final String STANDARD_KEY = "spectralization_metamaterial_standard";
    private static final String PRESET_KEY = "spectralization_metamaterial_preset";

    public static final MetamaterialTemplateData DEFAULT_CUSTOM =
            new MetamaterialTemplateData(new MetamaterialVector(0, 0, 0), false, "");

    public MetamaterialTemplateData {
        Objects.requireNonNull(vector, "vector");
        presetId = presetId == null ? "" : presetId;
    }

    public static MetamaterialTemplateData standard(MetamaterialStandardTemplate template) {
        Objects.requireNonNull(template, "template");
        return new MetamaterialTemplateData(template.vector(), true, template.idString());
    }

    public static MetamaterialTemplateData custom(MetamaterialVector vector) {
        return new MetamaterialTemplateData(vector, false, "");
    }

    public static MetamaterialTemplateData fromStack(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");

        boolean stackIsStandard = stack.is(Spectralization.STANDARD_METAMATERIAL_TEMPLATE.get());
        boolean stackIsCustom = stack.is(Spectralization.CUSTOM_METAMATERIAL_TEMPLATE.get());
        if (!stackIsStandard && !stackIsCustom) {
            return DEFAULT_CUSTOM;
        }

        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        boolean standard = tag.contains(STANDARD_KEY) ? tag.getBoolean(STANDARD_KEY) : stackIsStandard;
        String presetId = tag.getString(PRESET_KEY);
        MetamaterialVector vector;

        if (!tag.contains(X_KEY) || !tag.contains(Y_KEY) || !tag.contains(Z_KEY)) {
            vector = standard
                    ? MetamaterialStandardTemplate.byId(presetId).vector()
                    : DEFAULT_CUSTOM.vector();
        } else {
            vector = new MetamaterialVector(tag.getInt(X_KEY), tag.getInt(Y_KEY), tag.getInt(Z_KEY));
        }

        return new MetamaterialTemplateData(vector, standard, presetId);
    }

    public ItemStack createStack() {
        ItemStack stack = new ItemStack(standard
                ? Spectralization.STANDARD_METAMATERIAL_TEMPLATE.get()
                : Spectralization.CUSTOM_METAMATERIAL_TEMPLATE.get());
        applyTo(stack);
        return stack;
    }

    public void applyTo(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putInt(X_KEY, vector.x());
            tag.putInt(Y_KEY, vector.y());
            tag.putInt(Z_KEY, vector.z());
            tag.putBoolean(STANDARD_KEY, standard);
            tag.putString(PRESET_KEY, presetId);
        });
    }

    public String presetTranslationKey() {
        if (!standard) {
            return "item.spectralization.metamaterial_template.custom";
        }

        return MetamaterialStandardTemplate.byId(presetId).translationKey();
    }
}
