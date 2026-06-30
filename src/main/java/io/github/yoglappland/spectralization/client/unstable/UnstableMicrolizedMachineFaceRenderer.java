package io.github.yoglappland.spectralization.client.unstable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

public final class UnstableMicrolizedMachineFaceRenderer {
    private static final double FACE_EPSILON = 0.003D;

    public static void renderVariantFaces(
            MultiBufferSource bufferSource,
            PoseStack.Pose pose,
            int seed,
            float time,
            double x,
            double y,
            double z
    ) {
        for (Direction face : Direction.values()) {
            RenderType renderType = UnstableMicrolizedMachineVariantModels.activeFaceRenderType(time, seed, face);
            if (renderType != null) {
                renderFace(bufferSource.getBuffer(renderType), pose, x, y, z, face);
            }
        }
    }

    private static void renderFace(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            Direction face
    ) {
        double x1 = x + 1.0D;
        double y1 = y + 1.0D;
        double z1 = z + 1.0D;

        switch (face) {
            case NORTH -> addQuad(
                    consumer, pose,
                    x1, y, z - FACE_EPSILON,
                    x1, y1, z - FACE_EPSILON,
                    x, y1, z - FACE_EPSILON,
                    x, y, z - FACE_EPSILON,
                    0.0F, 0.0F, -1.0F
            );
            case SOUTH -> addQuad(
                    consumer, pose,
                    x, y, z1 + FACE_EPSILON,
                    x, y1, z1 + FACE_EPSILON,
                    x1, y1, z1 + FACE_EPSILON,
                    x1, y, z1 + FACE_EPSILON,
                    0.0F, 0.0F, 1.0F
            );
            case EAST -> addQuad(
                    consumer, pose,
                    x1 + FACE_EPSILON, y, z1,
                    x1 + FACE_EPSILON, y1, z1,
                    x1 + FACE_EPSILON, y1, z,
                    x1 + FACE_EPSILON, y, z,
                    1.0F, 0.0F, 0.0F
            );
            case WEST -> addQuad(
                    consumer, pose,
                    x - FACE_EPSILON, y, z,
                    x - FACE_EPSILON, y1, z,
                    x - FACE_EPSILON, y1, z1,
                    x - FACE_EPSILON, y, z1,
                    -1.0F, 0.0F, 0.0F
            );
            case UP -> addQuad(
                    consumer, pose,
                    x, y1 + FACE_EPSILON, z1,
                    x, y1 + FACE_EPSILON, z,
                    x1, y1 + FACE_EPSILON, z,
                    x1, y1 + FACE_EPSILON, z1,
                    0.0F, 1.0F, 0.0F
            );
            case DOWN -> addQuad(
                    consumer, pose,
                    x, y - FACE_EPSILON, z,
                    x, y - FACE_EPSILON, z1,
                    x1, y - FACE_EPSILON, z1,
                    x1, y - FACE_EPSILON, z,
                    0.0F, -1.0F, 0.0F
            );
        }
    }

    private static void addQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double x3,
            double y3,
            double z3,
            double x4,
            double y4,
            double z4,
            float normalX,
            float normalY,
            float normalZ
    ) {
        addVertex(consumer, pose, x1, y1, z1, 0.0F, 1.0F, normalX, normalY, normalZ);
        addVertex(consumer, pose, x2, y2, z2, 0.0F, 0.0F, normalX, normalY, normalZ);
        addVertex(consumer, pose, x3, y3, z3, 1.0F, 0.0F, normalX, normalY, normalZ);
        addVertex(consumer, pose, x4, y4, z4, 1.0F, 1.0F, normalX, normalY, normalZ);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ
    ) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private UnstableMicrolizedMachineFaceRenderer() {
    }
}
