package io.github.yoglappland.spectralization.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public final class RecursiveGeneratorItemDynamicModel extends BakedModelWrapper<BakedModel> {
    private static final ModelResourceLocation RECURSIVE_GENERATOR = ModelResourceLocation.inventory(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "recursive_generator")
    );

    private RecursiveGeneratorItemDynamicModel(BakedModel originalModel) {
        super(originalModel);
    }

    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel originalModel = event.getModels().get(RECURSIVE_GENERATOR);
        if (originalModel == null) {
            Spectralization.LOGGER.warn("Could not wrap recursive generator item model: missing {}", RECURSIVE_GENERATOR);
            return;
        }

        event.getModels().put(RECURSIVE_GENERATOR, new RecursiveGeneratorItemDynamicModel(originalModel));
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
