package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.yoglappland.spectralization.block.PhotonicGradientGeneratorBlock;
import io.github.yoglappland.spectralization.blockentity.PhotonicGradientGeneratorBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class LightSourceGeneratorRenderer implements BlockEntityRenderer<PhotonicGradientGeneratorBlockEntity> {
    private final ItemRenderer itemRenderer;

    public LightSourceGeneratorRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            PhotonicGradientGeneratorBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();
        if (!state.hasProperty(PhotonicGradientGeneratorBlock.ACTIVE)
                || !state.getValue(PhotonicGradientGeneratorBlock.ACTIVE)) {
            return;
        }

        ItemStack stack = blockEntity.displaySourceItem();
        if (stack.isEmpty()) {
            return;
        }

        Level level = blockEntity.getLevel();
        float time = level == null ? partialTick : level.getGameTime() + partialTick;
        float bob = (float) Math.sin(time * 0.12F) * 0.025F;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.54F + bob, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 2.4F));
        poseStack.mulPose(Axis.XP.rotationDegrees(8.0F));
        poseStack.scale(0.36F, 0.36F, 0.36F);
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT,
                packedOverlay,
                poseStack,
                bufferSource,
                null,
                0
        );
        poseStack.popPose();
    }
}
