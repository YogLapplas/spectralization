package io.github.yoglappland.spectralization.client.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.ClientHudState;
import io.github.yoglappland.spectralization.client.hud.SpectralBeamHudSettings;
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
    private static final double MIN_RENDER_RADIUS = 0.75D / 16.0D;
    private static final double MAX_RENDER_RADIUS = 3.0D;
    private static final double RING_THICKNESS = 0.5D / 16.0D;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 96.0D * 96.0D;
    private static final int MAX_RENDERED_SEGMENTS = 192;
    private static final int RING_SIDES = 8;
    private static final int SHELL_PLANES = 2;

    @SubscribeEvent
    public static void renderBeamPaths(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (minecraft.level == null
                || minecraft.player == null
                || !hasBeamPathViewer(minecraft)) {
            ClientBeamPathCache.clear();
            return;
        }

        if (!ClientHudState.visible()) {
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

        int renderedSegments = 0;

        for (BeamPathOverlayPayload.Segment segment : segments) {
            if (!shouldRender(segment)) {
                continue;
            }

            if (!isNearCamera(cameraPosition, segment)) {
                continue;
            }

            renderSegment(consumer, pose, segment);
            renderedSegments++;

            if (renderedSegments >= MAX_RENDERED_SEGMENTS) {
                break;
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static boolean shouldRender(BeamPathOverlayPayload.Segment segment) {
        return segment.coherent()
                ? SpectralBeamHudSettings.coherentVisible()
                : SpectralBeamHudSettings.strayVisible();
    }

    private static void renderSegment(VertexConsumer consumer, PoseStack.Pose pose, BeamPathOverlayPayload.Segment segment) {
        Direction direction = segment.direction();
        double sx = segment.from().getX() + 0.5D + direction.getStepX() * EDGE_OFFSET;
        double sy = segment.from().getY() + 0.5D + direction.getStepY() * EDGE_OFFSET;
        double sz = segment.from().getZ() + 0.5D + direction.getStepZ() * EDGE_OFFSET;
        double ex = segment.to().getX() + 0.5D - direction.getStepX() * EDGE_OFFSET;
        double ey = segment.to().getY() + 0.5D - direction.getStepY() * EDGE_OFFSET;
        double ez = segment.to().getZ() + 0.5D - direction.getStepZ() * EDGE_OFFSET;
        int color = segment.colorRgb();
        int shellAlpha = shellAlpha(segment);
        int ringAlpha = ringAlpha(segment, shellAlpha);
        renderBeamVolume(
                consumer,
                pose,
                new Vec3(sx, sy, sz),
                new Vec3(ex, ey, ez),
                renderRadius(segment.startRadius(), segment.widthLevel()),
                renderRadius(segment.endRadius(), segment.widthLevel()),
                color,
                shellAlpha,
                ringAlpha
        );
    }

    private static int shellAlpha(BeamPathOverlayPayload.Segment segment) {
        int level = Math.max(1, segment.visualLevel());
        return segment.coherent()
                ? Math.min(92, 18 + level * 7 + 12)
                : Math.min(96, 24 + level * 8);
    }

    private static int ringAlpha(BeamPathOverlayPayload.Segment segment, int shellAlpha) {
        return segment.coherent()
                ? Math.min(148, shellAlpha + 42)
                : Math.min(166, shellAlpha + 58);
    }

    private static void renderBeamVolume(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            double startRadius,
            double endRadius,
            int color,
            int shellAlpha,
            int ringAlpha
    ) {
        Vec3 axis = end.subtract(start);

        if (axis.lengthSqr() < 1.0E-8D) {
            return;
        }

        Vec3 forward = axis.normalize();
        Vec3 basisA = perpendicular(forward);
        Vec3 basisB = forward.cross(basisA).normalize();

        renderBeamShell(consumer, pose, start, end, basisA, basisB, startRadius, endRadius, color, shellAlpha);

        for (int index = 0; index < RING_SIDES; index++) {
            double a0 = angle(index);
            double a1 = angle(index + 1);
            renderRingSegment(consumer, pose, start, basisA, basisB, a0, a1, startRadius, color, ringAlpha);
            renderRingSegment(consumer, pose, end, basisA, basisB, a0, a1, endRadius, color, ringAlpha);
        }
    }

    private static void renderBeamShell(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 basisA,
            Vec3 basisB,
            double startRadius,
            double endRadius,
            int color,
            int alpha
    ) {
        for (int index = 0; index < SHELL_PLANES; index++) {
            double a0 = Math.PI * index / SHELL_PLANES;
            double a1 = a0 + Math.PI;
            Vec3 start0 = start.add(radial(basisA, basisB, a0, startRadius));
            Vec3 start1 = start.add(radial(basisA, basisB, a1, startRadius));
            Vec3 end1 = end.add(radial(basisA, basisB, a1, endRadius));
            Vec3 end0 = end.add(radial(basisA, basisB, a0, endRadius));
            addQuad(consumer, pose, start0, end0, end1, start1, color, alpha);
        }
    }

    private static void renderRingSegment(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 center,
            Vec3 basisA,
            Vec3 basisB,
            double a0,
            double a1,
            double radius,
            int color,
            int alpha
    ) {
        double innerRadius = Math.max(0.0D, radius - RING_THICKNESS);
        double outerRadius = radius + RING_THICKNESS;
        Vec3 outer0 = center.add(radial(basisA, basisB, a0, outerRadius));
        Vec3 outer1 = center.add(radial(basisA, basisB, a1, outerRadius));
        Vec3 inner1 = center.add(radial(basisA, basisB, a1, innerRadius));
        Vec3 inner0 = center.add(radial(basisA, basisB, a0, innerRadius));

        addQuad(consumer, pose, outer0, outer1, inner1, inner0, color, alpha);
    }

    private static Vec3 perpendicular(Vec3 forward) {
        Vec3 up = Math.abs(forward.y) < 0.95D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        return forward.cross(up).normalize();
    }

    private static Vec3 radial(Vec3 basisA, Vec3 basisB, double angle, double radius) {
        return basisA.scale(Math.cos(angle) * radius).add(basisB.scale(Math.sin(angle) * radius));
    }

    private static double angle(int index) {
        return Math.PI * 2.0D * index / RING_SIDES;
    }

    private static double renderRadius(double radius, int fallbackWidthLevel) {
        double fallbackRadius = Math.max(1, fallbackWidthLevel) / 16.0D;
        double value = Double.isFinite(radius) && radius > 0.0D ? radius : fallbackRadius;
        return Math.max(MIN_RENDER_RADIUS, Math.min(MAX_RENDER_RADIUS, value));
    }

    private static void addQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 p1,
            Vec3 p2,
            Vec3 p3,
            Vec3 p4,
            int color,
            int alpha
    ) {
        addQuad(consumer, pose, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, p4.x, p4.y, p4.z, color, alpha);
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

    private static boolean isNearCamera(Vec3 cameraPosition, BeamPathOverlayPayload.Segment segment) {
        double px = cameraPosition.x;
        double py = cameraPosition.y;
        double pz = cameraPosition.z;
        double ax = segment.from().getX() + 0.5D;
        double ay = segment.from().getY() + 0.5D;
        double az = segment.from().getZ() + 0.5D;
        double bx = segment.to().getX() + 0.5D;
        double by = segment.to().getY() + 0.5D;
        double bz = segment.to().getZ() + 0.5D;
        double vx = bx - ax;
        double vy = by - ay;
        double vz = bz - az;
        double lengthSquared = vx * vx + vy * vy + vz * vz;
        double t = lengthSquared <= 0.0D
                ? 0.0D
                : ((px - ax) * vx + (py - ay) * vy + (pz - az) * vz) / lengthSquared;
        double clampedT = Math.max(0.0D, Math.min(1.0D, t));
        double cx = ax + vx * clampedT;
        double cy = ay + vy * clampedT;
        double cz = az + vz * clampedT;
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQUARED;
    }

    private static boolean hasBeamPathViewer(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }

        var helmet = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(Items.LEATHER_HELMET)
                || helmet.is(Spectralization.VERITY_HELM_OF_ALL_SEEING_INSIGHT.get())
                || minecraft.player.getMainHandItem().is(Spectralization.OPTICAL_FIBER_COIL.get())
                || minecraft.player.getOffhandItem().is(Spectralization.OPTICAL_FIBER_COIL.get())
                || minecraft.player.getMainHandItem().is(Items.SHEARS)
                || minecraft.player.getOffhandItem().is(Items.SHEARS);
    }

    private BeamPathRenderEvents() {
    }
}
