package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.yoglappland.spectralization.blockentity.MetamaterialDesignTableBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class MetamaterialDesignTableRenderer implements BlockEntityRenderer<MetamaterialDesignTableBlockEntity> {
    private static final SlotRenderInfo[] SLOT_RENDERS = {
            new SlotRenderInfo(MetamaterialDesignTableBlockEntity.SLOT_LEFT_TOP, 3.5F, 12.5F, 1.5F, 0.18F),
            new SlotRenderInfo(MetamaterialDesignTableBlockEntity.SLOT_LEFT_MIDDLE, 3.5F, 8.0F, 1.5F, 0.18F),
            new SlotRenderInfo(MetamaterialDesignTableBlockEntity.SLOT_LEFT_BOTTOM, 3.5F, 3.5F, 1.5F, 0.18F),
            new SlotRenderInfo(MetamaterialDesignTableBlockEntity.SLOT_RIGHT_TOP, 12.5F, 12.5F, 1.5F, 0.18F),
            new SlotRenderInfo(MetamaterialDesignTableBlockEntity.SLOT_RIGHT_MIDDLE, 12.5F, 8.0F, 1.5F, 0.18F),
            new SlotRenderInfo(MetamaterialDesignTableBlockEntity.SLOT_RIGHT_BOTTOM, 12.5F, 3.5F, 1.5F, 0.18F),
            new SlotRenderInfo(MetamaterialDesignTableBlockEntity.SLOT_OUTPUT, 8.0F, 8.0F, 8.0F, 0.34F)
    };

    private final ItemRenderer itemRenderer;

    public MetamaterialDesignTableRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            MetamaterialDesignTableBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        for (SlotRenderInfo renderInfo : SLOT_RENDERS) {
            ItemStack stack = blockEntity.getStackForDisplay(renderInfo.slot());

            if (!stack.isEmpty()) {
                renderStack(stack, renderInfo, poseStack, bufferSource, packedLight, packedOverlay);
            }
        }
    }

    private void renderStack(
            ItemStack stack,
            SlotRenderInfo renderInfo,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        poseStack.translate(renderInfo.x() / 16.0F, renderInfo.y() / 16.0F, renderInfo.z() / 16.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(renderInfo.scale(), renderInfo.scale(), renderInfo.scale());
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, null, 0);
        poseStack.popPose();
    }

    private record SlotRenderInfo(int slot, float x, float y, float z, float scale) {
    }
}
