package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.singular.SingularMaterialData;
import io.github.yoglappland.spectralization.optics.singular.SingularMaterialGenerator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class SingularMaterialItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation WHITE_PIXEL = ResourceLocation.fromNamespaceAndPath(
            Spectralization.MODID,
            "textures/item/singular_material_pixel.png"
    );
    private static final float BACK_Z = 7.55F / 16.0F;
    private static final float FRONT_Z = 8.45F / 16.0F;
    private static final long TICKS_PER_FRAME = 3L;

    private static SingularMaterialItemRenderer instance;
    private final Map<Long, SingularMaterialGenerator.VisualStrip> visualCache = new HashMap<>();

    private SingularMaterialItemRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    public static SingularMaterialItemRenderer instance() {
        if (instance == null) {
            instance = new SingularMaterialItemRenderer(Minecraft.getInstance());
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
        long seed = SingularMaterialData.fromStack(stack).renderSeed();
        SingularMaterialGenerator.VisualStrip strip = visualCache.computeIfAbsent(seed, SingularMaterialGenerator::generateStrip);
        long gameTime = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.getGameTime()
                : System.currentTimeMillis() / 50L;
        int frame = (int) ((gameTime / TICKS_PER_FRAME) % SingularMaterialGenerator.FRAME_COUNT);
        SingularMaterialGenerator.Visual visual = strip.frame(frame);
        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        renderSolidPixels(bufferSource.getBuffer(RenderType.entityCutoutNoCull(WHITE_PIXEL)), pose, visual, packedLight);
        renderHaloPixels(bufferSource.getBuffer(RenderType.entityTranslucent(WHITE_PIXEL)), pose, visual, packedLight);
        poseStack.popPose();
    }

    private static void renderSolidPixels(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            SingularMaterialGenerator.Visual visual,
            int light
    ) {
        for (int y = 0; y < SingularMaterialGenerator.SIZE; y++) {
            for (int x = 0; x < SingularMaterialGenerator.SIZE; x++) {
                if (!visual.visible(x, y)) {
                    continue;
                }
                if (!visual.solid(x, y)) {
                    continue;
                }

                int color = visual.color(x, y);
                float x0 = (float) x / SingularMaterialGenerator.SIZE;
                float x1 = (float) (x + 1) / SingularMaterialGenerator.SIZE;
                float y0 = 1.0F - (float) (y + 1) / SingularMaterialGenerator.SIZE;
                float y1 = 1.0F - (float) y / SingularMaterialGenerator.SIZE;

                int opaqueColor = opaque(color);
                renderFront(consumer, pose, x0, y0, x1, y1, opaqueColor, light);
                renderBack(consumer, pose, x0, y0, x1, y1, shade(opaqueColor, 0.52F), light);

                if (!visual.solid(x - 1, y)) {
                    renderEdge(consumer, pose, x0, y0, x0, y1, opaqueColor, 0.52F, light, -1.0F, 0.0F, 0.0F);
                }
                if (!visual.solid(x + 1, y)) {
                    renderEdge(consumer, pose, x1, y1, x1, y0, opaqueColor, 0.78F, light, 1.0F, 0.0F, 0.0F);
                }
                if (!visual.solid(x, y - 1)) {
                    renderEdge(consumer, pose, x1, y1, x0, y1, opaqueColor, 1.04F, light, 0.0F, 1.0F, 0.0F);
                }
                if (!visual.solid(x, y + 1)) {
                    renderEdge(consumer, pose, x0, y0, x1, y0, opaqueColor, 0.44F, light, 0.0F, -1.0F, 0.0F);
                }
            }
        }
    }

    private static void renderHaloPixels(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            SingularMaterialGenerator.Visual visual,
            int light
    ) {
        for (int y = 0; y < SingularMaterialGenerator.SIZE; y++) {
            for (int x = 0; x < SingularMaterialGenerator.SIZE; x++) {
                if (!visual.visible(x, y) || visual.solid(x, y)) {
                    continue;
                }

                int color = visual.color(x, y);
                float x0 = (float) x / SingularMaterialGenerator.SIZE;
                float x1 = (float) (x + 1) / SingularMaterialGenerator.SIZE;
                float y0 = 1.0F - (float) (y + 1) / SingularMaterialGenerator.SIZE;
                float y1 = 1.0F - (float) y / SingularMaterialGenerator.SIZE;
                renderFront(consumer, pose, x0, y0, x1, y1, color, light);
                renderBack(consumer, pose, x0, y0, x1, y1, shade(color, 0.58F), light);

                if (!visual.visible(x - 1, y)) {
                    renderEdge(consumer, pose, x0, y0, x0, y1, color, 0.58F, light, -1.0F, 0.0F, 0.0F);
                }
                if (!visual.visible(x + 1, y)) {
                    renderEdge(consumer, pose, x1, y1, x1, y0, color, 0.78F, light, 1.0F, 0.0F, 0.0F);
                }
                if (!visual.visible(x, y - 1)) {
                    renderEdge(consumer, pose, x1, y1, x0, y1, color, 1.02F, light, 0.0F, 1.0F, 0.0F);
                }
                if (!visual.visible(x, y + 1)) {
                    renderEdge(consumer, pose, x0, y0, x1, y0, color, 0.46F, light, 0.0F, -1.0F, 0.0F);
                }
            }
        }
    }

    private static void renderFront(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x0,
            float y0,
            float x1,
            float y1,
            int color,
            int light
    ) {
        addVertex(consumer, pose, x0, y1, FRONT_Z, color, light, 0.0F, 0.0F, 1.0F);
        addVertex(consumer, pose, x1, y1, FRONT_Z, color, light, 0.0F, 0.0F, 1.0F);
        addVertex(consumer, pose, x1, y0, FRONT_Z, color, light, 0.0F, 0.0F, 1.0F);
        addVertex(consumer, pose, x0, y0, FRONT_Z, color, light, 0.0F, 0.0F, 1.0F);
    }

    private static void renderBack(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x0,
            float y0,
            float x1,
            float y1,
            int color,
            int light
    ) {
        addVertex(consumer, pose, x0, y0, BACK_Z, color, light, 0.0F, 0.0F, -1.0F);
        addVertex(consumer, pose, x1, y0, BACK_Z, color, light, 0.0F, 0.0F, -1.0F);
        addVertex(consumer, pose, x1, y1, BACK_Z, color, light, 0.0F, 0.0F, -1.0F);
        addVertex(consumer, pose, x0, y1, BACK_Z, color, light, 0.0F, 0.0F, -1.0F);
    }

    private static void renderEdge(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x0,
            float y0,
            float x1,
            float y1,
            int color,
            float shade,
            int light,
            float normalX,
            float normalY,
            float normalZ
    ) {
        int shaded = shade(color, shade);
        addVertex(consumer, pose, x0, y0, FRONT_Z, shaded, light, normalX, normalY, normalZ);
        addVertex(consumer, pose, x1, y1, FRONT_Z, shaded, light, normalX, normalY, normalZ);
        addVertex(consumer, pose, x1, y1, BACK_Z, shaded, light, normalX, normalY, normalZ);
        addVertex(consumer, pose, x0, y0, BACK_Z, shaded, light, normalX, normalY, normalZ);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            int color,
            int light,
            float normalX,
            float normalY,
            float normalZ
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(
                        SingularMaterialGenerator.red(color),
                        SingularMaterialGenerator.green(color),
                        SingularMaterialGenerator.blue(color),
                        SingularMaterialGenerator.alpha(color)
                )
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static int shade(int color, float factor) {
        int alpha = SingularMaterialGenerator.alpha(color);
        int red = Math.round(SingularMaterialGenerator.red(color) * factor);
        int green = Math.round(SingularMaterialGenerator.green(color) * factor);
        int blue = Math.round(SingularMaterialGenerator.blue(color) * factor);
        return (alpha << 24)
                | (clamp(red) << 16)
                | (clamp(green) << 8)
                | clamp(blue);
    }

    private static int opaque(int color) {
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
