package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.HolographicStorageShellBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ModelEvent;

public class HolographicStorageShellRenderer implements BlockEntityRenderer<HolographicStorageShellBlockEntity> {
    private static final ModelResourceLocation CHANNEL_GREEN_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(
                    Spectralization.MODID,
                    "block/stable_holographic_storage_shell_green"
            )
    );
    private static final ModelResourceLocation CHANNEL_RED_MODEL = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(
                    Spectralization.MODID,
                    "block/stable_holographic_storage_shell_red"
            )
    );
    private static final ResourceLocation PARTICLE_CORE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_core.png");
    private static final ResourceLocation PARTICLE_HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_halo.png");
    private static final RenderType PARTICLE_CORE_RENDER_TYPE = RenderType.entityTranslucent(PARTICLE_CORE_TEXTURE);
    private static final RenderType PARTICLE_HALO_RENDER_TYPE = RenderType.entityTranslucent(PARTICLE_HALO_TEXTURE);
    private static final int PARTICLE_COUNT = 12;
    private static final float PARTICLE_CENTER_HIDE_RADIUS = 0.12F;
    private static final float CHANNEL_OVERLAY_SCALE = 1.002F;

    private final ItemRenderer itemRenderer;

    public HolographicStorageShellRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(CHANNEL_GREEN_MODEL);
        event.register(CHANNEL_RED_MODEL);
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
        Level level = blockEntity.getLevel();
        float time = level == null ? partialTick : level.getGameTime() + partialTick;
        ItemStack stack = blockEntity.getStackForDisplay();
        if (!stack.isEmpty()) {
            float bob = (float) Math.sin(time * 0.08F) * 0.015F;

            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F + bob, 0.5F);
            poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.25F));
            poseStack.scale(0.44F, 0.44F, 0.44F);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, null, 0);
            poseStack.popPose();
        }

        if (blockEntity.hasChannelGlow()) {
            renderChannelOverlay(poseStack, bufferSource, blockEntity.hasRedChannelGlow());
        }

        if (blockEntity.isPhotoinducedActive()) {
            renderPhotoinducedParticles(time, poseStack, bufferSource);
        }
    }

    private static void renderChannelOverlay(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            boolean red
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(red ? CHANNEL_RED_MODEL : CHANNEL_GREEN_MODEL);
        VertexConsumer consumer = bufferSource.getBuffer(Sheets.translucentCullBlockSheet());

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(CHANNEL_OVERLAY_SCALE, CHANNEL_OVERLAY_SCALE, CHANNEL_OVERLAY_SCALE);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                consumer,
                null,
                model,
                1.0F,
                1.0F,
                1.0F,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    private static void renderPhotoinducedParticles(
            float time,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer haloConsumer = bufferSource.getBuffer(PARTICLE_HALO_RENDER_TYPE);
        renderPhotoinducedParticlePass(time, haloConsumer, pose, true);
        VertexConsumer coreConsumer = bufferSource.getBuffer(PARTICLE_CORE_RENDER_TYPE);
        renderPhotoinducedParticlePass(time, coreConsumer, pose, false);
    }

    private static void renderPhotoinducedParticlePass(
            float time,
            VertexConsumer consumer,
            PoseStack.Pose pose,
            boolean halo
    ) {
        for (int index = 0; index < PARTICLE_COUNT; index++) {
            float phase = fract(time * 0.052F + index * 0.137F);
            float eased = phase * phase * (3.0F - 2.0F * phase);
            float orbit = index * 2.399963F + time * 0.018F;
            float radius = 0.48F * (1.0F - eased) + 0.035F;
            if (radius <= PARTICLE_CENTER_HIDE_RADIUS) {
                continue;
            }

            float wobble = (float) Math.sin(time * 0.11F + index * 1.7F) * 0.055F * (1.0F - phase);
            float x = 0.5F + (float) Math.cos(orbit) * radius;
            float z = 0.5F + (float) Math.sin(orbit) * radius;
            float y = 0.5F + wobble + (0.16F - 0.32F * ((index % 3) / 2.0F)) * (1.0F - eased);
            float size = 0.030F - 0.010F * eased;
            int alpha = Math.max(0, Math.min(255, Math.round((0.48F + 0.52F * phase) * 240.0F)));

            if ((index & 1) == 0) {
                renderSoftParticleLayer(consumer, pose, x, y, z, size, 185, 252, 255, alpha, halo);
            } else {
                renderSoftParticleLayer(consumer, pose, x, y, z, size, 255, 224, 138, alpha, halo);
            }
        }
    }

    private static void renderSoftParticleLayer(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float size,
            int red,
            int green,
            int blue,
            int alpha,
            boolean halo
    ) {
        if (halo) {
            int haloAlpha = Math.max(24, Math.min(150, Math.round(alpha * 0.50F)));
            renderParticlePlanes(consumer, pose, x, y, z, size * 2.20F, red, green, blue, haloAlpha);
        } else {
            renderParticlePlanes(consumer, pose, x, y, z, size * 0.92F, red, green, blue, Math.min(255, alpha + 34));
        }
    }

    private static void renderParticlePlanes(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float size,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addQuad(
                consumer,
                pose,
                x - size, y - size, z,
                x - size, y + size, z,
                x + size, y + size, z,
                x + size, y - size, z,
                red,
                green,
                blue,
                alpha,
                0.0F,
                0.0F,
                1.0F
        );
        addQuad(
                consumer,
                pose,
                x, y - size, z - size,
                x, y + size, z - size,
                x, y + size, z + size,
                x, y - size, z + size,
                red,
                green,
                blue,
                alpha,
                1.0F,
                0.0F,
                0.0F
        );
        addQuad(
                consumer,
                pose,
                x - size, y, z - size,
                x + size, y, z - size,
                x + size, y, z + size,
                x - size, y, z + size,
                red,
                green,
                blue,
                alpha,
                0.0F,
                1.0F,
                0.0F
        );
    }

    private static void addQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            int red,
            int green,
            int blue,
            int alpha,
            float normalX,
            float normalY,
            float normalZ
    ) {
        addVertex(consumer, pose, x1, y1, z1, 0.0F, 1.0F, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x2, y2, z2, 0.0F, 0.0F, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x3, y3, z3, 1.0F, 0.0F, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x4, y4, z4, 1.0F, 1.0F, red, green, blue, alpha, normalX, normalY, normalZ);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            float normalX,
            float normalY,
            float normalZ
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }
}
