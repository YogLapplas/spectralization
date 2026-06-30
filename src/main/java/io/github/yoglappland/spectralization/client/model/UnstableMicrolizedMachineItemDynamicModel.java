package io.github.yoglappland.spectralization.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public final class UnstableMicrolizedMachineItemDynamicModel extends BakedModelWrapper<BakedModel> {
    private static final ModelResourceLocation UNSTABLE_MICROLIZED_MACHINE = ModelResourceLocation.inventory(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "unstable_microlized_machine")
    );

    private UnstableMicrolizedMachineItemDynamicModel(BakedModel originalModel) {
        super(originalModel);
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel originalModel = event.getModels().get(UNSTABLE_MICROLIZED_MACHINE);
        if (originalModel == null) {
            Spectralization.LOGGER.warn(
                    "Could not wrap unstable microlized machine item model: missing {}",
                    UNSTABLE_MICROLIZED_MACHINE
            );
            return;
        }

        event.getModels().put(UNSTABLE_MICROLIZED_MACHINE, new UnstableMicrolizedMachineItemDynamicModel(originalModel));
    }

    @Override
    public boolean isCustomRenderer() {
        return true;
    }

    @Override
    public BakedModel applyTransform(
            ItemDisplayContext cameraTransformType,
            PoseStack poseStack,
            boolean applyLeftHandTransform
    ) {
        originalModel.applyTransform(cameraTransformType, poseStack, applyLeftHandTransform);
        return this;
    }
}
