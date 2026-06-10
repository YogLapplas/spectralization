package io.github.yoglappland.spectralization.client.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class BeamPathRenderEvents {
    private static final double EDGE_OFFSET = 0.0D;
    private static final double BEAM_WIDTH = 0.035D;

    @SubscribeEvent
    public static void renderBeamPaths(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (minecraft.level == null
                || minecraft.player == null
                || !hasBeamViewerHelmet(minecraft)) {
            ClientBeamPathCache.clear();
            return;
        }

        var segments = ClientBeamPathCache.activeSegments();

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

        for (BeamPathOverlayPayload.Segment segment : segments) {
            renderSegment(consumer, pose, segment);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static void renderSegment(VertexConsumer consumer, PoseStack.Pose pose, BeamPathOverlayPayload.Segment segment) {
        Direction direction = segment.direction();
        double sx = segment.from().getX() + 0.5D + direction.getStepX() * EDGE_OFFSET;
        double sy = segment.from().getY() + 0.5D + direction.getStepY() * EDGE_OFFSET;
        double sz = segment.from().getZ() + 0.5D + direction.getStepZ() * EDGE_OFFSET;
        double ex = segment.to().getX() + 0.5D - direction.getStepX() * EDGE_OFFSET;
        double ey = segment.to().getY() + 0.5D - direction.getStepY() * EDGE_OFFSET;
        double ez = segment.to().getZ() + 0.5D - direction.getStepZ() * EDGE_OFFSET;
        double width = BEAM_WIDTH;
        int color = SpectralColorMap.visibleRgbForBin(segment.colorBin());
        int alpha = Math.min(180, 28 + Math.max(1, segment.visualLevel()) * 18 + (segment.coherent() ? 22 : 0));

        switch (direction.getAxis()) {
            case X -> {
                addQuad(consumer, pose, sx, sy - width, sz, ex, ey - width, ez, ex, ey + width, ez, sx, sy + width, sz, color, alpha);
                addQuad(consumer, pose, sx, sy, sz - width, ex, ey, ez - width, ex, ey, ez + width, sx, sy, sz + width, color, alpha);
            }
            case Y -> {
                addQuad(consumer, pose, sx - width, sy, sz, ex - width, ey, ez, ex + width, ey, ez, sx + width, sy, sz, color, alpha);
                addQuad(consumer, pose, sx, sy, sz - width, ex, ey, ez - width, ex, ey, ez + width, sx, sy, sz + width, color, alpha);
            }
            case Z -> {
                addQuad(consumer, pose, sx - width, sy, sz, ex - width, ey, ez, ex + width, ey, ez, sx + width, sy, sz, color, alpha);
                addQuad(consumer, pose, sx, sy - width, sz, ex, ey - width, ez, ex, ey + width, ez, sx, sy + width, sz, color, alpha);
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
            double z4,
            int color,
            int alpha
    ) {
        addVertex(consumer, pose, x1, y1, z1, color, alpha);
        addVertex(consumer, pose, x2, y2, z2, color, alpha);
        addVertex(consumer, pose, x3, y3, z3, color, alpha);
        addVertex(consumer, pose, x4, y4, z4, color, alpha);
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, double z, int color, int alpha) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(SpectralColorMap.red(color), SpectralColorMap.green(color), SpectralColorMap.blue(color), alpha);
    }

    private static boolean hasBeamViewerHelmet(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }

        var helmet = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(Items.LEATHER_HELMET)
                || helmet.is(Spectralization.VERITY_HELM_OF_ALL_SEEING_INSIGHT.get());
    }

    private BeamPathRenderEvents() {
    }
}
