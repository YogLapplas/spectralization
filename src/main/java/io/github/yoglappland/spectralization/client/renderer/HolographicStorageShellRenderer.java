package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.yoglappland.spectralization.blockentity.HolographicStorageShellBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class HolographicStorageShellRenderer implements BlockEntityRenderer<HolographicStorageShellBlockEntity> {
    private final ItemRenderer itemRenderer;

    public HolographicStorageShellRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            HolographicStorageShellBlockEntity blockEntity,
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
        float bob = (float) Math.sin(time * 0.08F) * 0.015F;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F + bob, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.25F));
        poseStack.scale(0.44F, 0.44F, 0.44F);
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, null, 0);
        poseStack.popPose();
    }
}
