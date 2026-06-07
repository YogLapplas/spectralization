package io.github.yoglappland.spectralization.client.spot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.SpotRecord;
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
import org.joml.Vector3f;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class SpotRenderEvents {
    private static final double SURFACE_OFFSET = 0.003D;

    @SubscribeEvent
    public static void renderSpots(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || Minecraft.getInstance().level == null) {
            return;
        }

        var spots = ClientSpotCache.activeSpots();

        if (spots.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        Vec3 cameraPosition = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        PoseStack.Pose pose = poseStack.last();

        for (SpotRecord spot : spots) {
            renderSpot(consumer, pose, spot);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static void renderSpot(VertexConsumer consumer, PoseStack.Pose pose, SpotRecord spot) {
        double radius = renderRadius(spot.radiusLevel());
        int alpha = 34 + spot.brightnessLevel() * 24;
        Vector3f color = colorFor(spot.colorBin());
        BlockPos pos = spot.pos();
        Direction face = spot.face();
        double x = pos.getX() + 0.5D + face.getStepX() * (0.5D + SURFACE_OFFSET);
        double y = pos.getY() + 0.5D + face.getStepY() * (0.5D + SURFACE_OFFSET);
        double z = pos.getZ() + 0.5D + face.getStepZ() * (0.5D + SURFACE_OFFSET);

        switch (face.getAxis()) {
            case X -> addQuad(
                    consumer,
                    pose,
                    x,
                    y - radius,
                    z - radius,
                    x,
                    y + radius,
                    z - radius,
                    x,
                    y + radius,
                    z + radius,
                    x,
                    y - radius,
                    z + radius,
                    color,
                    alpha
            );
            case Y -> addQuad(
                    consumer,
                    pose,
                    x - radius,
                    y,
                    z - radius,
                    x - radius,
                    y,
                    z + radius,
                    x + radius,
                    y,
                    z + radius,
                    x + radius,
                    y,
                    z - radius,
                    color,
                    alpha
            );
            case Z -> addQuad(
                    consumer,
                    pose,
                    x - radius,
                    y - radius,
                    z,
                    x + radius,
                    y - radius,
                    z,
                    x + radius,
                    y + radius,
                    z,
                    x - radius,
                    y + radius,
                    z,
                    color,
                    alpha
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
            Vector3f color,
            int alpha
    ) {
        addVertex(consumer, pose, x1, y1, z1, color, alpha);
        addVertex(consumer, pose, x2, y2, z2, color, alpha);
        addVertex(consumer, pose, x3, y3, z3, color, alpha);
        addVertex(consumer, pose, x4, y4, z4, color, alpha);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            Vector3f color,
            int alpha
    ) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor((int) (color.x() * 255.0F), (int) (color.y() * 255.0F), (int) (color.z() * 255.0F), alpha);
    }

    private static double renderRadius(int radiusLevel) {
        return 0.06D + radiusLevel * 0.055D;
    }

    private static Vector3f colorFor(int colorBin) {
        double t = Math.max(0.0D, Math.min(1.0D, colorBin / 63.0D));
        double hue = 0.72D - t * 0.72D;

        return hsvToRgb(hue, 0.92D, 1.0D);
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

    private SpotRenderEvents() {
    }
}
