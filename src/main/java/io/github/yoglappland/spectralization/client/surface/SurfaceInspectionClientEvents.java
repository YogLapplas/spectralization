package io.github.yoglappland.spectralization.client.surface;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.network.SurfaceInspectionRequestPayload;
import io.github.yoglappland.spectralization.network.SurfaceInspectionResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class SurfaceInspectionClientEvents {
    private static final int QUERY_INTERVAL_TICKS = 5;
    private static final double SURFACE_OFFSET = 0.006D;

    private static long nextQueryGameTime;
    private static BlockPos lastRequestedPos;
    private static Direction lastRequestedSide;

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || !hasInspectionHelmet(minecraft)) {
            ClientSurfaceInspectionCache.clear();
            lastRequestedPos = null;
            lastRequestedSide = null;
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        BlockHitResult hit = minecraft.hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit
                : null;
        boolean hasTarget = hit != null;
        BlockPos requestPos = hasTarget ? hit.getBlockPos() : minecraft.player.blockPosition();
        Direction requestSide = hasTarget ? hit.getDirection() : Direction.UP;

        if (gameTime < nextQueryGameTime
                && requestPos.equals(lastRequestedPos)
                && requestSide == lastRequestedSide) {
            return;
        }

        lastRequestedPos = requestPos;
        lastRequestedSide = requestSide;
        nextQueryGameTime = gameTime + QUERY_INTERVAL_TICKS;
        PacketDistributor.sendToServer(new SurfaceInspectionRequestPayload(hasTarget, lastRequestedPos, lastRequestedSide));
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || !hasInspectionHelmet(minecraft)) {
            return;
        }

        SurfaceInspectionResponsePayload response = activeLookedAtResponse(minecraft);

        if (response == null || !response.present()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int x = minecraft.getWindow().getGuiScaledWidth() / 2 + 12;
        int y = minecraft.getWindow().getGuiScaledHeight() / 2 + 12;
        int width = 126;
        int height = 42;
        int color = colorFor(response);

        graphics.fill(x, y, x + width, y + height, 0xCC101216);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xCC1B1F27);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, color);
        graphics.drawString(minecraft.font, Component.translatable(treatmentKey(response)), x + 6, y + 8, 0xE7EEF8, false);
        graphics.drawString(minecraft.font, "T " + percent(response.transmittancePermille()), x + 6, y + 22, 0xBFC7D5, false);
        graphics.drawString(minecraft.font, "R " + percent(response.reflectancePermille()), x + 47, y + 22, 0xBFC7D5, false);
        graphics.drawString(minecraft.font, "A " + percent(response.absorptionPermille()), x + 88, y + 22, 0xBFC7D5, false);
    }

    @SubscribeEvent
    public static void renderSurfaceHighlight(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || minecraft.level == null
                || minecraft.player == null
                || !hasInspectionHelmet(minecraft)) {
            return;
        }

        var surfaces = ClientSurfaceInspectionCache.activeSurfaces();

        if (surfaces.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        Vec3 cameraPosition = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        for (SurfaceInspectionResponsePayload response : surfaces) {
            if (response.present()) {
                renderFace(consumer, poseStack.last(), response);
            }
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.debugQuads());
    }

    private static SurfaceInspectionResponsePayload activeLookedAtResponse(Minecraft minecraft) {
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return ClientSurfaceInspectionCache.active(hit.getBlockPos(), hit.getDirection());
    }

    private static void renderFace(VertexConsumer consumer, PoseStack.Pose pose, SurfaceInspectionResponsePayload response) {
        BlockPos pos = response.pos();
        Direction face = response.side();
        int color = colorFor(response);
        int red = (color >> 16) & 255;
        int green = (color >> 8) & 255;
        int blue = color & 255;
        int alpha = 72;
        double x = pos.getX() + 0.5D + face.getStepX() * (0.5D + SURFACE_OFFSET);
        double y = pos.getY() + 0.5D + face.getStepY() * (0.5D + SURFACE_OFFSET);
        double z = pos.getZ() + 0.5D + face.getStepZ() * (0.5D + SURFACE_OFFSET);

        switch (face.getAxis()) {
            case X -> addQuad(consumer, pose, x, y - 0.5D, z - 0.5D, x, y + 0.5D, z - 0.5D, x, y + 0.5D, z + 0.5D, x, y - 0.5D, z + 0.5D, red, green, blue, alpha);
            case Y -> addQuad(consumer, pose, x - 0.5D, y, z - 0.5D, x - 0.5D, y, z + 0.5D, x + 0.5D, y, z + 0.5D, x + 0.5D, y, z - 0.5D, red, green, blue, alpha);
            case Z -> addQuad(consumer, pose, x - 0.5D, y - 0.5D, z, x + 0.5D, y - 0.5D, z, x + 0.5D, y + 0.5D, z, x - 0.5D, y + 0.5D, z, red, green, blue, alpha);
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
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addVertex(consumer, pose, x1, y1, z1, red, green, blue, alpha);
        addVertex(consumer, pose, x2, y2, z2, red, green, blue, alpha);
        addVertex(consumer, pose, x3, y3, z3, red, green, blue, alpha);
        addVertex(consumer, pose, x4, y4, z4, red, green, blue, alpha);
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
        consumer.addVertex(pose, (float) x, (float) y, (float) z).setColor(red, green, blue, alpha);
    }

    private static boolean hasInspectionHelmet(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }

        var helmet = minecraft.player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(Items.LEATHER_HELMET)
                || helmet.is(Spectralization.VERITY_HELM_OF_ALL_SEEING_INSIGHT.get());
    }

    private static String treatmentKey(SurfaceInspectionResponsePayload response) {
        return switch (response.treatmentKind()) {
            case GOLDING -> "surface.spectralization.treatment.golding";
            case SILVERING -> "surface.spectralization.treatment.silvering";
            default -> "surface.spectralization.treatment.none";
        };
    }

    private static int colorFor(SurfaceInspectionResponsePayload response) {
        return switch (response.treatmentKind()) {
            case GOLDING -> 0xFFE2BC5A;
            case SILVERING -> 0xFF9DF7FF;
            default -> 0xFFBFC7D5;
        };
    }

    private static String percent(int permille) {
        return String.format("%.1f%%", permille / 10.0D);
    }

    private SurfaceInspectionClientEvents() {
    }
}
