package io.github.yoglappland.spectralization.client.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.ClientHudState;
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
    private static final int FIBER_OWNER_ID = -0x0B0DCD2;
    private static final int FIBER_SHELL_COLOR = 0x203B3A;
    private static final int FIBER_SHADOW_COLOR = 0x0B171A;
    private static final int FIBER_HIGHLIGHT_COLOR = 0x7FA49D;
    private static final int FIBER_CORE_COLOR = 0x9FFFF2;
    private static final double EDGE_OFFSET = 0.0D;
    private static final double MIN_RENDER_RADIUS = 0.75D / 16.0D;
    private static final double MAX_RENDER_RADIUS = 3.0D;
    private static final double RING_THICKNESS = 0.5D / 16.0D;
    private static final double FIBER_CABLE_RADIUS = 1.45D / 16.0D;
    private static final double FIBER_STRAND_RADIUS = 0.26D / 16.0D;
    private static final double FIBER_MAX_SAG = 0.42D;
    private static final double FIBER_SAG_PER_HORIZONTAL_BLOCK = 0.026D;
    private static final int CYLINDER_SIDES = 12;
    private static final int FIBER_CABLE_SIDES = 9;
    private static final int FIBER_CURVE_STEPS = 8;

    @SubscribeEvent
    public static void renderBeamPaths(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (minecraft.level == null || minecraft.player == null) {
            ClientBeamPathCache.clear();
            return;
        }

        boolean diagnosticOverlayVisible = ClientHudState.visible() && hasBeamPathViewer(minecraft);
        var segments = ClientBeamPathCache.activeRenderedSegments();

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

        for (ClientBeamPathCache.RenderedSegment renderedSegment : segments) {
            if (renderedSegment.ownerId() == FIBER_OWNER_ID) {
                renderFiberCable(consumer, pose, renderedSegment.segment());
            } else if (diagnosticOverlayVisible) {
                renderSegment(consumer, pose, renderedSegment.segment());
            }
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

    private static void renderFiberCable(VertexConsumer consumer, PoseStack.Pose pose, BeamPathOverlayPayload.Segment segment) {
        Vec3 start = segmentStart(segment);
        Vec3 end = segmentEnd(segment);
        Vec3 axis = end.subtract(start);

        if (axis.lengthSqr() < 1.0E-8D) {
            return;
        }

        Vec3 side = stableSide(axis);
        double sag = cableSag(axis);
        Vec3 previous = cablePoint(start, end, 0.0D, sag);

        for (int step = 1; step <= FIBER_CURVE_STEPS; step++) {
            double t = step / (double) FIBER_CURVE_STEPS;
            Vec3 current = cablePoint(start, end, t, sag);
            renderCableOuterVolume(consumer, pose, previous, current, FIBER_CABLE_RADIUS, 238);
            renderCableStrand(
                    consumer,
                    pose,
                    previous,
                    current,
                    side.scale(FIBER_CABLE_RADIUS * 0.68D),
                    FIBER_STRAND_RADIUS,
                    FIBER_HIGHLIGHT_COLOR,
                    185
            );
            renderCableStrand(
                    consumer,
                    pose,
                    previous,
                    current,
                    side.scale(-FIBER_CABLE_RADIUS * 0.74D).add(0.0D, -FIBER_CABLE_RADIUS * 0.18D, 0.0D),
                    FIBER_STRAND_RADIUS * 0.8D,
                    FIBER_SHADOW_COLOR,
                    210
            );
            renderCableStrand(
                    consumer,
                    pose,
                    previous,
                    current,
                    side.scale(FIBER_CABLE_RADIUS * 0.18D).add(0.0D, FIBER_CABLE_RADIUS * 0.24D, 0.0D),
                    FIBER_STRAND_RADIUS * 0.72D,
                    FIBER_CORE_COLOR,
                    Math.min(170, 62 + Math.max(1, segment.visualLevel()) * 12)
            );
            previous = current;
        }

        renderFiberClamp(consumer, pose, start, axis.normalize(), FIBER_CABLE_RADIUS, 220);
        renderFiberClamp(consumer, pose, end, axis.normalize().scale(-1.0D), FIBER_CABLE_RADIUS, 220);
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

        for (int index = 0; index < CYLINDER_SIDES; index++) {
            double a0 = angle(index);
            double a1 = angle(index + 1);
            Vec3 start0 = start.add(radial(basisA, basisB, a0, startRadius));
            Vec3 start1 = start.add(radial(basisA, basisB, a1, startRadius));
            Vec3 end1 = end.add(radial(basisA, basisB, a1, endRadius));
            Vec3 end0 = end.add(radial(basisA, basisB, a0, endRadius));

            addQuad(consumer, pose, start0, end0, end1, start1, color, shellAlpha);
            renderRingSegment(consumer, pose, start, basisA, basisB, a0, a1, startRadius, color, ringAlpha);
            renderRingSegment(consumer, pose, end, basisA, basisB, a0, a1, endRadius, color, ringAlpha);
        }
    }

    private static void renderCableOuterVolume(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            double radius,
            int alpha
    ) {
        Vec3 axis = end.subtract(start);

        if (axis.lengthSqr() < 1.0E-8D) {
            return;
        }

        Vec3 forward = axis.normalize();
        Vec3 basisA = perpendicular(forward);
        Vec3 basisB = forward.cross(basisA).normalize();
        Vec3 lightDirection = new Vec3(-0.35D, 0.85D, -0.28D).normalize();

        for (int index = 0; index < FIBER_CABLE_SIDES; index++) {
            double a0 = fiberAngle(index);
            double a1 = fiberAngle(index + 1);
            double midAngle = (a0 + a1) * 0.5D;
            Vec3 normal = radial(basisA, basisB, midAngle, 1.0D).normalize();
            double light = 0.62D
                    + 0.20D * Math.max(0.0D, normal.y)
                    + 0.18D * Math.max(0.0D, normal.dot(lightDirection));
            int color = shadedColor(FIBER_SHELL_COLOR, light);
            Vec3 start0 = start.add(radial(basisA, basisB, a0, radius));
            Vec3 start1 = start.add(radial(basisA, basisB, a1, radius));
            Vec3 end1 = end.add(radial(basisA, basisB, a1, radius));
            Vec3 end0 = end.add(radial(basisA, basisB, a0, radius));

            addQuad(consumer, pose, start0, end0, end1, start1, color, alpha);
        }
    }

    private static void renderCableStrand(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 offset,
            double radius,
            int color,
            int alpha
    ) {
        renderSimpleCylinder(consumer, pose, start.add(offset), end.add(offset), radius, color, alpha, 6);
    }

    private static void renderFiberClamp(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 center,
            Vec3 direction,
            double radius,
            int alpha
    ) {
        Vec3 start = center.subtract(direction.scale(0.035D));
        Vec3 end = center.add(direction.scale(0.035D));
        renderSimpleCylinder(consumer, pose, start, end, radius * 1.18D, 0x606E6A, alpha, FIBER_CABLE_SIDES);
    }

    private static void renderSimpleCylinder(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            double radius,
            int color,
            int alpha,
            int sides
    ) {
        Vec3 axis = end.subtract(start);

        if (axis.lengthSqr() < 1.0E-8D) {
            return;
        }

        Vec3 forward = axis.normalize();
        Vec3 basisA = perpendicular(forward);
        Vec3 basisB = forward.cross(basisA).normalize();

        for (int index = 0; index < sides; index++) {
            double a0 = Math.PI * 2.0D * index / sides;
            double a1 = Math.PI * 2.0D * (index + 1) / sides;
            Vec3 start0 = start.add(radial(basisA, basisB, a0, radius));
            Vec3 start1 = start.add(radial(basisA, basisB, a1, radius));
            Vec3 end1 = end.add(radial(basisA, basisB, a1, radius));
            Vec3 end0 = end.add(radial(basisA, basisB, a0, radius));

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
        return Math.PI * 2.0D * index / CYLINDER_SIDES;
    }

    private static double fiberAngle(int index) {
        return Math.PI * 2.0D * index / FIBER_CABLE_SIDES;
    }

    private static Vec3 segmentStart(BeamPathOverlayPayload.Segment segment) {
        Direction direction = segment.direction();
        return new Vec3(
                segment.from().getX() + 0.5D + direction.getStepX() * EDGE_OFFSET,
                segment.from().getY() + 0.5D + direction.getStepY() * EDGE_OFFSET,
                segment.from().getZ() + 0.5D + direction.getStepZ() * EDGE_OFFSET
        );
    }

    private static Vec3 segmentEnd(BeamPathOverlayPayload.Segment segment) {
        Direction direction = segment.direction();
        return new Vec3(
                segment.to().getX() + 0.5D - direction.getStepX() * EDGE_OFFSET,
                segment.to().getY() + 0.5D - direction.getStepY() * EDGE_OFFSET,
                segment.to().getZ() + 0.5D - direction.getStepZ() * EDGE_OFFSET
        );
    }

    private static Vec3 cablePoint(Vec3 start, Vec3 end, double t, double sag) {
        Vec3 point = start.lerp(end, t);
        return point.subtract(0.0D, Math.sin(Math.PI * t) * sag, 0.0D);
    }

    private static double cableSag(Vec3 axis) {
        double horizontalLength = Math.sqrt(axis.x * axis.x + axis.z * axis.z);
        return Math.min(FIBER_MAX_SAG, horizontalLength * FIBER_SAG_PER_HORIZONTAL_BLOCK);
    }

    private static Vec3 stableSide(Vec3 axis) {
        Vec3 horizontal = new Vec3(axis.x, 0.0D, axis.z);

        if (horizontal.lengthSqr() > 1.0E-8D) {
            Vec3 forward = horizontal.normalize();
            return new Vec3(-forward.z, 0.0D, forward.x);
        }

        return new Vec3(1.0D, 0.0D, 0.0D);
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

    private static int shadedColor(int color, double shade) {
        int red = clampColor(Math.round(SpectralColorMap.red(color) * (float) shade));
        int green = clampColor(Math.round(SpectralColorMap.green(color) * (float) shade));
        int blue = clampColor(Math.round(SpectralColorMap.blue(color) * (float) shade));
        return red << 16 | green << 8 | blue;
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
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
