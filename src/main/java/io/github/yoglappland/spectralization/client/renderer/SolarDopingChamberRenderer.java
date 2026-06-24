package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.yoglappland.spectralization.blockentity.SolarDopingChamberBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SolarDopingChamberRenderer implements BlockEntityRenderer<SolarDopingChamberBlockEntity> {
    private final ItemRenderer itemRenderer;

    public SolarDopingChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            SolarDopingChamberBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        ItemStack stack = blockEntity.getStackForDisplay();
        if (stack.isEmpty()) {
            return;
        }

        Level level = blockEntity.getLevel();
        float time = level == null ? partialTick : level.getGameTime() + partialTick;

        poseStack.pushPose();
        poseStack.translate(0.5F, 8.05F / 16.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.scale(0.34F, 0.34F, 0.34F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, null, 0);
        poseStack.popPose();
    }
}
