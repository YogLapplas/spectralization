package io.github.yoglappland.spectralization.client.compact;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.network.CompactMachineOverlayPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
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
    private static final int WORK_FILL_R = 0x7C;
    private static final int WORK_FILL_G = 0xEA;
    private static final int WORK_FILL_B = 0xD9;
    private static final int WORK_FILL_A = 64;
    private static final double BEAM_HALF_WIDTH = 1.0D / 16.0D;
    private static final double WORK_AREA_EXPANSION = 0.08D / 16.0D;

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
        var workArea = ClientCompactMachineWorkAreaOverlayCache.activeWorkArea();
        if (segments.isEmpty() && workArea.isEmpty()) {
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

        workArea.ifPresent(area -> renderWorkArea(consumer, pose, area));

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
                addQuad(consumer, pose, sx, sy, sz - width, sx, sy, sz + width, ex, ey, ez + width, ex, ey, ez - width);
            }
            case Y -> {
                addQuad(consumer, pose, sx - width, sy, sz, sx + width, sy, sz, ex + width, ey, ez, ex - width, ey, ez);
                addQuad(consumer, pose, sx, sy, sz - width, ex, ey, ez - width, ex, ey, ez + width, sx, sy, sz + width);
            }
            case Z -> {
                addQuad(consumer, pose, sx - width, sy, sz, ex - width, ey, ez, ex + width, ey, ez, sx + width, sy, sz);
                addQuad(consumer, pose, sx, sy - width, sz, sx, sy + width, sz, ex, ey + width, ez, ex, ey - width, ez);
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
        addVertex(consumer, pose, x, y, z, COLOR_R, COLOR_G, COLOR_B, COLOR_A);
    }

    private static void renderWorkArea(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientCompactMachineWorkAreaOverlayCache.WorkArea area
    ) {
        BlockPos min = area.min();
        BlockPos max = area.max();
        double minX = min.getX() - WORK_AREA_EXPANSION;
        double minY = min.getY() - WORK_AREA_EXPANSION;
        double minZ = min.getZ() - WORK_AREA_EXPANSION;
        double maxX = max.getX() + 1.0D + WORK_AREA_EXPANSION;
        double maxY = max.getY() + 1.0D + WORK_AREA_EXPANSION;
        double maxZ = max.getZ() + 1.0D + WORK_AREA_EXPANSION;

        addWorkFillQuad(consumer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
        addWorkFillQuad(consumer, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ);
        addWorkFillQuad(consumer, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ);
        addWorkFillQuad(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ);
        addWorkFillQuad(consumer, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ);
        addWorkFillQuad(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
    }

    private static void addWorkFillQuad(
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
        addVertex(consumer, pose, x1, y1, z1, WORK_FILL_R, WORK_FILL_G, WORK_FILL_B, WORK_FILL_A);
        addVertex(consumer, pose, x2, y2, z2, WORK_FILL_R, WORK_FILL_G, WORK_FILL_B, WORK_FILL_A);
        addVertex(consumer, pose, x3, y3, z3, WORK_FILL_R, WORK_FILL_G, WORK_FILL_B, WORK_FILL_A);
        addVertex(consumer, pose, x4, y4, z4, WORK_FILL_R, WORK_FILL_G, WORK_FILL_B, WORK_FILL_A);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha);
    }

    private CompactMachineOverlayRenderEvents() {
    }
}
