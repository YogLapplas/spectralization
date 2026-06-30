package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.unstable.UnstableMicrolizedMachineFaceRenderer;
import io.github.yoglappland.spectralization.client.unstable.UnstableMicrolizedMachineVariantModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class UnstableMicrolizedMachineItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ModelResourceLocation SHADOW_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "block/unstable_microlized_machine")
    );
    private static UnstableMicrolizedMachineItemRenderer instance;

    private UnstableMicrolizedMachineItemRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    public static UnstableMicrolizedMachineItemRenderer instance() {
        if (instance == null) {
            instance = new UnstableMicrolizedMachineItemRenderer(Minecraft.getInstance());
        }
        return instance;
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(SHADOW_MODEL);
        UnstableMicrolizedMachineVariantModels.registerAdditionalModels(event);
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        float time = renderTime();
        int seed = stack.getItem().hashCode() ^ displayContext.ordinal() * 0x119de1f3;
        poseStack.pushPose();
        renderShadow(poseStack, bufferSource, displayContext);
        renderBlock(poseStack, bufferSource, packedLight, packedOverlay, time, seed);
        poseStack.popPose();
    }

    private static void renderShadow(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            ItemDisplayContext displayContext
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(SHADOW_MODEL);
        VertexConsumer consumer = bufferSource.getBuffer(Sheets.translucentCullBlockSheet());

        poseStack.pushPose();
        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.translate(0.060F, -0.050F, -0.080F);
            poseStack.scale(1.095F, 1.095F, 1.095F);
        } else {
            poseStack.translate(0.045F, -0.035F, 0.035F);
            poseStack.scale(1.070F, 1.070F, 1.070F);
        }
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                null,
                model,
                0.015F,
                0.012F,
                0.020F,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    private static void renderBlock(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            float time,
            int seed
    ) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                Spectralization.UNSTABLE_MICROLIZED_MACHINE.get().defaultBlockState(),
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );
        UnstableMicrolizedMachineFaceRenderer.renderVariantFaces(bufferSource, poseStack.last(), seed, time, 0.0D, 0.0D, 0.0D);
    }

    private static float renderTime() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.getGameTime();
        }

        return System.currentTimeMillis() / 50.0F;
    }
}
