package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.machine.RecursiveGeneratorState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class RecursiveGeneratorItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static RecursiveGeneratorItemRenderer instance;

    private RecursiveGeneratorItemRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    public static RecursiveGeneratorItemRenderer instance() {
        if (instance == null) {
            instance = new RecursiveGeneratorItemRenderer(Minecraft.getInstance());
        }
        return instance;
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
        RecursiveGeneratorState state = RecursiveGeneratorState.fromStack(stack);
        boolean animateInGui = displayContext == ItemDisplayContext.GUI && state.hasRemaining();
        poseStack.pushPose();
        if (animateInGui) {
            applyJitter(poseStack, state);
        }

        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                Spectralization.RECURSIVE_GENERATOR.get().defaultBlockState(),
                poseStack,
                bufferSource,
                packedLight,
                packedOverlay
        );
        poseStack.popPose();
    }

    private static void applyJitter(PoseStack poseStack, RecursiveGeneratorState state) {
        long gameTime = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime()
                : System.currentTimeMillis() / 50L;
        int depth = state.recursionDepth();
        double phase = gameTime * (0.46D + depth * 0.28D);
        float amplitude = 0.004F + depth * 0.0018F;
        poseStack.translate(
                Math.sin(phase) * amplitude,
                Math.cos(phase * 1.37D) * amplitude,
                0.0F
        );
    }
}
