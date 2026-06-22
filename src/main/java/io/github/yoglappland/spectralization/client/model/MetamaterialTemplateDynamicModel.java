package io.github.yoglappland.spectralization.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public final class MetamaterialTemplateDynamicModel extends BakedModelWrapper<BakedModel> {
    private static final ModelResourceLocation STANDARD_TEMPLATE = ModelResourceLocation.inventory(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "standard_metamaterial_template")
    );
    private static final ModelResourceLocation CUSTOM_TEMPLATE = ModelResourceLocation.inventory(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "custom_metamaterial_template")
    );

    private MetamaterialTemplateDynamicModel(BakedModel originalModel) {
        super(originalModel);
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        wrap(event, STANDARD_TEMPLATE);
        wrap(event, CUSTOM_TEMPLATE);
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, ModelResourceLocation location) {
        BakedModel originalModel = event.getModels().get(location);
        if (originalModel == null) {
            Spectralization.LOGGER.warn("Could not wrap metamaterial template model: missing {}", location);
            return;
        }

        event.getModels().put(location, new MetamaterialTemplateDynamicModel(originalModel));
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
