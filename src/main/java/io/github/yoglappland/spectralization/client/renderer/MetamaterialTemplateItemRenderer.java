package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialTemplateData;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialVector;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class MetamaterialTemplateItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float BACK_Z = 7.5F / 16.0F;
    private static final float FRONT_Z = 8.5F / 16.0F;
    private static final float LAYER_STEP = 0.002F;
    private static final int FRONT_SHADE = 255;
    private static final int BACK_SHADE = 170;
    private static final int LEFT_SHADE = 150;
    private static final int RIGHT_SHADE = 205;
    private static final int TOP_SHADE = 235;
    private static final int BOTTOM_SHADE = 125;

    private static MetamaterialTemplateItemRenderer instance;
    private final Map<ResourceLocation, AlphaMask> alphaMasks = new HashMap<>();

    private MetamaterialTemplateItemRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    public static MetamaterialTemplateItemRenderer instance() {
        if (instance == null) {
            instance = new MetamaterialTemplateItemRenderer(Minecraft.getInstance());
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
        MetamaterialTraits traits = MetamaterialTraits.from(MetamaterialTemplateData.fromStack(stack).vector());
        ResourceLocation substrateTexture = texture("substrate/substrate_" + traits.substrate());
        ResourceLocation cornerTexture = texture("corner/corner_" + traits.corner());
        ResourceLocation latticeTexture = texture("lattice_filter/substrate_"
                + traits.substrate()
                + "_lattice_"
                + traits.lattice()
                + "_filter_"
                + traits.filter());

        poseStack.pushPose();
        renderMaskedLayer(
                bufferSource,
                poseStack.last(),
                substrateTexture,
                alphaMask(substrateTexture),
                BACK_Z,
                FRONT_Z,
                packedLight
        );
        renderFrontLayer(bufferSource, poseStack.last(), latticeTexture, FRONT_Z + LAYER_STEP * 2.0F, packedLight, FRONT_SHADE);
        renderMaskedLayer(
                bufferSource,
                poseStack.last(),
                cornerTexture,
                alphaMask(cornerTexture),
                BACK_Z - LAYER_STEP,
                FRONT_Z + LAYER_STEP * 3.0F,
                packedLight
        );
        poseStack.popPose();
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                Spectralization.MODID,
                "textures/item/metamaterial/" + path + ".png"
        );
    }

    private AlphaMask alphaMask(ResourceLocation texture) {
        return alphaMasks.computeIfAbsent(texture, this::loadAlphaMask);
    }

    private AlphaMask loadAlphaMask(ResourceLocation location) {
        try {
            Resource resource = Minecraft.getInstance()
                    .getResourceManager()
                    .getResource(location)
                    .orElseThrow(() -> new IOException("Missing resource " + location));
            try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
                int width = image.getWidth();
                int height = image.getHeight();
                boolean[] solid = new boolean[width * height];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int abgr = image.getPixelRGBA(x, y);
                        solid[y * width + x] = ((abgr >>> 24) & 0xFF) > 8;
                    }
                }
                return new AlphaMask(width, height, solid);
            }
        } catch (IOException exception) {
            Spectralization.LOGGER.warn("Could not read metamaterial alpha mask {}", location, exception);
            return AlphaMask.full(32, 32);
        }
    }

    private static void renderMaskedLayer(
            MultiBufferSource bufferSource,
            PoseStack.Pose pose,
            ResourceLocation texture,
            AlphaMask mask,
            float backZ,
            float frontZ,
            int packedLight
    ) {
        renderFrontLayer(bufferSource, pose, texture, frontZ, packedLight, FRONT_SHADE);
        renderBackLayer(bufferSource, pose, texture, backZ, packedLight);
        renderMaskEdges(bufferSource, pose, texture, mask, backZ, frontZ, packedLight);
    }

    private static void renderFrontLayer(
            MultiBufferSource bufferSource,
            PoseStack.Pose pose,
            ResourceLocation texture,
            float z,
            int packedLight,
            int shade
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
        addVertex(consumer, pose, 0.0F, 1.0F, z, 0.0F, 0.0F, packedLight, shade, 0.0F, 0.0F, 1.0F);
        addVertex(consumer, pose, 1.0F, 1.0F, z, 1.0F, 0.0F, packedLight, shade, 0.0F, 0.0F, 1.0F);
        addVertex(consumer, pose, 1.0F, 0.0F, z, 1.0F, 1.0F, packedLight, shade, 0.0F, 0.0F, 1.0F);
        addVertex(consumer, pose, 0.0F, 0.0F, z, 0.0F, 1.0F, packedLight, shade, 0.0F, 0.0F, 1.0F);
    }

    private static void renderBackLayer(
            MultiBufferSource bufferSource,
            PoseStack.Pose pose,
            ResourceLocation texture,
            float z,
            int packedLight
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
        addVertex(consumer, pose, 0.0F, 0.0F, z, 0.0F, 1.0F, packedLight, BACK_SHADE, 0.0F, 0.0F, -1.0F);
        addVertex(consumer, pose, 1.0F, 0.0F, z, 1.0F, 1.0F, packedLight, BACK_SHADE, 0.0F, 0.0F, -1.0F);
        addVertex(consumer, pose, 1.0F, 1.0F, z, 1.0F, 0.0F, packedLight, BACK_SHADE, 0.0F, 0.0F, -1.0F);
        addVertex(consumer, pose, 0.0F, 1.0F, z, 0.0F, 0.0F, packedLight, BACK_SHADE, 0.0F, 0.0F, -1.0F);
    }

    private static void renderMaskEdges(
            MultiBufferSource bufferSource,
            PoseStack.Pose pose,
            ResourceLocation texture,
            AlphaMask mask,
            float backZ,
            float frontZ,
            int packedLight
    ) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));

        for (int y = 0; y < mask.height(); y++) {
            for (int x = 0; x < mask.width(); x++) {
                if (!mask.solid(x, y)) {
                    continue;
                }

                float x0 = (float) x / mask.width();
                float x1 = (float) (x + 1) / mask.width();
                float y0 = 1.0F - (float) (y + 1) / mask.height();
                float y1 = 1.0F - (float) y / mask.height();
                float u = ((float) x + 0.5F) / mask.width();
                float v = ((float) y + 0.5F) / mask.height();

                if (!mask.solid(x - 1, y)) {
                    renderEdge(consumer, pose, x0, y0, x0, y1, u, v, backZ, frontZ, LEFT_SHADE, packedLight, -1.0F, 0.0F, 0.0F);
                }
                if (!mask.solid(x + 1, y)) {
                    renderEdge(consumer, pose, x1, y1, x1, y0, u, v, backZ, frontZ, RIGHT_SHADE, packedLight, 1.0F, 0.0F, 0.0F);
                }
                if (!mask.solid(x, y - 1)) {
                    renderEdge(consumer, pose, x1, y1, x0, y1, u, v, backZ, frontZ, TOP_SHADE, packedLight, 0.0F, 1.0F, 0.0F);
                }
                if (!mask.solid(x, y + 1)) {
                    renderEdge(consumer, pose, x0, y0, x1, y0, u, v, backZ, frontZ, BOTTOM_SHADE, packedLight, 0.0F, -1.0F, 0.0F);
                }
            }
        }
    }

    private static void renderEdge(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x0,
            float y0,
            float x1,
            float y1,
            float u,
            float v,
            float backZ,
            float frontZ,
            int shade,
            int light,
            float normalX,
            float normalY,
            float normalZ
    ) {
        addVertex(consumer, pose, x0, y0, frontZ, u, v, light, shade, normalX, normalY, normalZ);
        addVertex(consumer, pose, x1, y1, frontZ, u, v, light, shade, normalX, normalY, normalZ);
        addVertex(consumer, pose, x1, y1, backZ, u, v, light, shade, normalX, normalY, normalZ);
        addVertex(consumer, pose, x0, y0, backZ, u, v, light, shade, normalX, normalY, normalZ);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int light,
            int shade,
            float normalX,
            float normalY,
            float normalZ
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(gradientShade(shade, x, y), gradientShade(shade, x, y), gradientShade(shade, x, y), 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static int gradientShade(int shade, float x, float y) {
        float light = ((1.0F - x) + y) * 0.5F;
        float factor = 0.46F + light * 0.62F;
        int value = Math.round(shade * factor);
        return Math.max(0, Math.min(255, value));
    }

    private record MetamaterialTraits(int substrate, int corner, int lattice, int filter) {
        private static MetamaterialTraits from(MetamaterialVector vector) {
            int x = vector.x() - MetamaterialVector.MIN_VALUE;
            int y = vector.y() - MetamaterialVector.MIN_VALUE;
            int z = vector.z() - MetamaterialVector.MIN_VALUE;
            return new MetamaterialTraits(
                    x / 2,
                    y / 2,
                    z / 2,
                    (x % 2) * 4 + (y % 2) * 2 + z % 2
            );
        }
    }

    private record AlphaMask(int width, int height, boolean[] solid) {
        private static AlphaMask full(int width, int height) {
            boolean[] solid = new boolean[width * height];
            for (int index = 0; index < solid.length; index++) {
                solid[index] = true;
            }
            return new AlphaMask(width, height, solid);
        }

        private boolean solid(int x, int y) {
            return x >= 0 && y >= 0 && x < width && y < height && solid[y * width + x];
        }
    }
}
