package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalPathExposure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

public class PhosphorTubeItem extends Item {
    private static final CustomModelData LIGHTENED_MODEL = new CustomModelData(1);

    public PhosphorTubeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide) {
            boolean lightened = OpticalPathExposure.isEntityExposedTo(level, entity, FrequencyKey.DEBUG_VISIBLE);
            updateModelData(stack, lightened);
        }
    }

    private static void updateModelData(ItemStack stack, boolean lightened) {
        CustomModelData modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA);

        if (lightened) {
            if (modelData == null || modelData.value() != LIGHTENED_MODEL.value()) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, LIGHTENED_MODEL);
            }

            return;
        }

        if (modelData != null && modelData.value() == LIGHTENED_MODEL.value()) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }
}
