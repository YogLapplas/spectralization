package io.github.yoglappland.spectralization.client.compact;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.network.CompactMachineOverlayPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class CompactMachineOverlayRenderEvents {
    private static final int COLOR_R = 0x66;
    private static final int COLOR_G = 0xCC;
    private static final int COLOR_B = 0xFF;
    private static final int COLOR_A = 176;
    private static final double BEAM_HALF_WIDTH = 0.12D;

    @SubscribeEvent
    public static void renderCompactMachineConnections(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (minecraft.level == null || minecraft.player == null) {
            ClientCompactMachineOverlayCache.clear();
            return;
        }

        var segments = ClientCompactMachineOverlayCache.activeSegments();
        if (segments.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        Vec3 cameraPosition = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        PoseStack.Pose pose = poseStack.last();

        for (CompactMachineOverlayPayload.Segment segment : segments) {
            renderSegment(consumer, pose, segment);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static void renderSegment(VertexConsumer consumer, PoseStack.Pose pose, CompactMachineOverlayPayload.Segment segment) {
        Direction direction = segment.direction();
        double sx = segment.from().getX() + 0.5D;
        double sy = segment.from().getY() + 0.5D;
        double sz = segment.from().getZ() + 0.5D;
        double ex = segment.to().getX() + 0.5D;
        double ey = segment.to().getY() + 0.5D;
        double ez = segment.to().getZ() + 0.5D;
        double width = BEAM_HALF_WIDTH;

        switch (direction.getAxis()) {
            case X -> {
                addQuad(consumer, pose, sx, sy - width, sz, ex, ey - width, ez, ex, ey + width, ez, sx, sy + width, sz);
                addQuad(consumer, pose, sx, sy, sz - width, ex, ey, ez - width, ex, ey, ez + width, sx, sy, sz + width);
            }
            case Y -> {
                addQuad(consumer, pose, sx - width, sy, sz, ex - width, ey, ez, ex + width, ey, ez, sx + width, sy, sz);
                addQuad(consumer, pose, sx, sy, sz - width, ex, ey, ez - width, ex, ey, ez + width, sx, sy, sz + width);
            }
            case Z -> {
                addQuad(consumer, pose, sx - width, sy, sz, ex - width, ey, ez, ex + width, ey, ez, sx + width, sy, sz);
                addQuad(consumer, pose, sx, sy - width, sz, ex, ey - width, ez, ex, ey + width, ez, sx, sy + width, sz);
            }
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
            double z4
    ) {
        addVertex(consumer, pose, x1, y1, z1);
        addVertex(consumer, pose, x2, y2, z2);
        addVertex(consumer, pose, x3, y3, z3);
        addVertex(consumer, pose, x4, y4, z4);
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, double z) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(COLOR_R, COLOR_G, COLOR_B, COLOR_A);
    }

    private CompactMachineOverlayRenderEvents() {
    }
}
