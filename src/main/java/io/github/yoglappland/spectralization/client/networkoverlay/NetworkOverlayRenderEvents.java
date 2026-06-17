package io.github.yoglappland.spectralization.client.networkoverlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.ClientHudState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class NetworkOverlayRenderEvents {
    private static final double INFLATE = 0.004D;
    private static final int RED = 96;
    private static final int GREEN = 7;
    private static final int BLUE = 10;
    private static final int ALPHA = 44;

    @SubscribeEvent
    public static void renderNetworkOverlay(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || Minecraft.getInstance().level == null
                || !ClientHudState.visible()
                || !ClientNetworkOverlayCache.isVisible()) {
            return;
        }

        var positions = ClientNetworkOverlayCache.positions();

        if (positions.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        Vec3 cameraPosition = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        PoseStack.Pose pose = poseStack.last();

        for (BlockPos pos : positions) {
            renderBlockFilter(consumer, pose, pos);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static void renderBlockFilter(VertexConsumer consumer, PoseStack.Pose pose, BlockPos pos) {
        double minX = pos.getX() - INFLATE;
        double minY = pos.getY() - INFLATE;
        double minZ = pos.getZ() - INFLATE;
        double maxX = pos.getX() + 1.0D + INFLATE;
        double maxY = pos.getY() + 1.0D + INFLATE;
        double maxZ = pos.getZ() + 1.0D + INFLATE;

        addQuad(consumer, pose, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ);
        addQuad(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, maxX, minY, minZ);
        addQuad(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, minY, maxZ);
        addQuad(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ, minX, minY, minZ);
        addQuad(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        addQuad(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ);
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
                .setColor(RED, GREEN, BLUE, ALPHA);
    }

    private NetworkOverlayRenderEvents() {
    }
}
