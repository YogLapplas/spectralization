package io.github.yoglappland.spectralization.client.beam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
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
import org.joml.Vector3f;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class BeamPathRenderEvents {
    private static final double EDGE_OFFSET = 0.0D;
    private static final double HUD_WIDTH = 0.035D;

    @SubscribeEvent
    public static void renderBeamPaths(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || minecraft.level == null
                || minecraft.player == null
                || !minecraft.player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET)) {
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
        double width = HUD_WIDTH;
        Vector3f color = colorFor(segment.colorBin(), segment.coherent());
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
            Vector3f color,
            int alpha
    ) {
        addVertex(consumer, pose, x1, y1, z1, color, alpha);
        addVertex(consumer, pose, x2, y2, z2, color, alpha);
        addVertex(consumer, pose, x3, y3, z3, color, alpha);
        addVertex(consumer, pose, x4, y4, z4, color, alpha);
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, double z, Vector3f color, int alpha) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor((int) (color.x() * 255.0F), (int) (color.y() * 255.0F), (int) (color.z() * 255.0F), alpha);
    }

    private static Vector3f colorFor(int colorBin, boolean coherent) {
        double t = Math.max(0.0D, Math.min(1.0D, colorBin / 63.0D));
        double hue = 0.72D - t * 0.72D;
        Vector3f color = hsvToRgb(hue, coherent ? 0.96D : 0.42D, coherent ? 1.0D : 0.82D);
        return color;
    }

    private static Vector3f hsvToRgb(double hue, double saturation, double value) {
        double h = (hue - Math.floor(hue)) * 6.0D;
        int sector = (int) Math.floor(h);
        double f = h - sector;
        double p = value * (1.0D - saturation);
        double q = value * (1.0D - f * saturation);
        double tt = value * (1.0D - (1.0D - f) * saturation);

        return switch (sector) {
            case 0 -> new Vector3f((float) value, (float) tt, (float) p);
            case 1 -> new Vector3f((float) q, (float) value, (float) p);
            case 2 -> new Vector3f((float) p, (float) value, (float) tt);
            case 3 -> new Vector3f((float) p, (float) q, (float) value);
            case 4 -> new Vector3f((float) tt, (float) p, (float) value);
            default -> new Vector3f((float) value, (float) p, (float) q);
        };
    }

    private BeamPathRenderEvents() {
    }
}
