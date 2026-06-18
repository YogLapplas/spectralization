package io.github.yoglappland.spectralization.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.ClientHudState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class SpectralHudEntityOverlayEvents {
    private static final double RANGE = 48.0D;
    private static final double RANGE_SQUARED = RANGE * RANGE;
    private static final int MAX_BOXES = 96;
    private static final int BUTTON_WIDTH = 72;
    private static final int BUTTON_HEIGHT = 18;
    private static final int PRIMARY = 0xFF66CCFF;
    private static final int PRIMARY_DIM = 0xAA66CCFF;
    private static final int TRACKED = 0xFFFF4D5A;
    private static final int TEXT = 0xFFE8FBFF;
    private static final float TRACK_RESPONSE = 0.42F;
    private static final float TRACK_MAX_TICK_STEP = 14.0F;
    private static int nearbyEntityCount;
    private static int highlightedEntityCount;
    private static Integer trackedEntityId;
    private static boolean aimInitialized;
    private static float previousAimYaw;
    private static float previousAimPitch;
    private static float currentAimYaw;
    private static float currentAimPitch;

    public static int nearbyEntityCount() {
        refreshCounts();
        return nearbyEntityCount;
    }

    public static int highlightedEntityCount() {
        refreshCounts();
        return highlightedEntityCount;
    }

    public static boolean hasTrackingAction() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null && (trackedEntity() != null || targetUnderCrosshair() != null);
    }

    public static void renderTrackingButton(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || !hasTrackingAction()) {
            return;
        }

        int x = buttonX();
        int y = buttonY();
        boolean tracked = trackedEntity() != null;
        int border = tracked ? TRACKED : PRIMARY_DIM;
        Component label = Component.translatable(tracked
                ? "hud.spectralization.tracking.release"
                : "hud.spectralization.tracking.track");

        graphics.fill(x + 6, y, x + BUTTON_WIDTH - 6, y + 1, border);
        graphics.fill(x + 6, y + BUTTON_HEIGHT - 1, x + BUTTON_WIDTH - 6, y + BUTTON_HEIGHT, border);
        graphics.fill(x, y + 6, x + 1, y + BUTTON_HEIGHT - 6, border);
        graphics.fill(x + BUTTON_WIDTH - 1, y + 6, x + BUTTON_WIDTH, y + BUTTON_HEIGHT - 6, border);
        graphics.fill(x + 1, y + 5, x + 6, y + 6, border);
        graphics.fill(x + BUTTON_WIDTH - 6, y + 5, x + BUTTON_WIDTH - 1, y + 6, border);
        graphics.fill(x + 1, y + BUTTON_HEIGHT - 6, x + 6, y + BUTTON_HEIGHT - 5, border);
        graphics.fill(x + BUTTON_WIDTH - 6, y + BUTTON_HEIGHT - 6, x + BUTTON_WIDTH - 1, y + BUTTON_HEIGHT - 5, border);

        int textWidth = minecraft.font.width(label);
        graphics.drawString(minecraft.font, label, x + (BUTTON_WIDTH - textWidth) / 2, y + 5, tracked ? TRACKED : TEXT, false);
    }

    public static boolean mouseClickedTrackingButton(double mouseX, double mouseY, int button) {
        if (button != 0 || !hasTrackingAction()) {
            return false;
        }

        int x = buttonX();
        int y = buttonY();

        if (mouseX < x || mouseY < y || mouseX >= x + BUTTON_WIDTH || mouseY >= y + BUTTON_HEIGHT) {
            return false;
        }

        Entity tracked = trackedEntity();

        if (tracked != null) {
            clearTracking();
            return true;
        }

        Entity target = targetUnderCrosshair();
        if (target != null) {
            trackedEntityId = target.getId();
            initializeAim(Minecraft.getInstance());
        }

        return true;
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || trackedEntityId == null) {
            clearTracking();
            return;
        }

        Entity target = trackedEntity();

        if (target == null) {
            clearTracking();
            return;
        }

        advanceAim(minecraft, target);
    }

    @SubscribeEvent
    public static void computeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null || trackedEntityId == null || !aimInitialized) {
            return;
        }

        if (trackedEntity() == null) {
            clearTracking();
            return;
        }

        float partialTick = Mth.clamp((float) event.getPartialTick(), 0.0F, 1.0F);
        float yaw = lerpDegrees(partialTick, previousAimYaw, currentAimYaw);
        float pitch = Mth.lerp(partialTick, previousAimPitch, currentAimPitch);
        event.setYaw(yaw);
        event.setPitch(pitch);
        applyPlayerAngles(minecraft, yaw, pitch);
    }

    @SubscribeEvent
    public static void renderEntityBoxes(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || minecraft.level == null
                || minecraft.player == null
                || !ClientHudState.visible()) {
            nearbyEntityCount = 0;
            highlightedEntityCount = 0;
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        int nearby = 0;
        int highlighted = 0;
        Entity target = trackedEntity();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == minecraft.player || entity.distanceToSqr(minecraft.player) > RANGE_SQUARED) {
                continue;
            }

            nearby++;

            if (highlighted >= MAX_BOXES) {
                continue;
            }

            AABB bounds = entity.getBoundingBox().inflate(0.04D);
            if (entity == target) {
                renderTrackedBox(poseStack, consumer, bounds);
            } else {
                LevelRenderer.renderLineBox(poseStack, consumer, bounds, 0.40F, 0.80F, 1.00F, 0.72F);
            }
            highlighted++;
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
        nearbyEntityCount = nearby;
        highlightedEntityCount = highlighted;
    }

    private static void renderTrackedBox(PoseStack poseStack, VertexConsumer consumer, AABB bounds) {
        LevelRenderer.renderLineBox(poseStack, consumer, bounds.inflate(0.00D), 1.00F, 0.18F, 0.24F, 0.98F);
        LevelRenderer.renderLineBox(poseStack, consumer, bounds.inflate(0.035D), 1.00F, 0.08F, 0.12F, 0.82F);
        LevelRenderer.renderLineBox(poseStack, consumer, bounds.inflate(0.070D), 1.00F, 0.00F, 0.06F, 0.58F);
    }

    private static Entity targetUnderCrosshair() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }

        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        double bestDistance = RANGE_SQUARED;
        Entity best = null;

        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == minecraft.player || entity.distanceToSqr(minecraft.player) > RANGE_SQUARED) {
                continue;
            }

            AABB bounds = entity.getBoundingBox().inflate(0.18D);
            var hit = bounds.clip(eye, end);

            if (hit.isEmpty()) {
                continue;
            }

            double distance = eye.distanceToSqr(hit.get());

            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }

        return best;
    }

    private static Entity trackedEntity() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || trackedEntityId == null) {
            return null;
        }

        Entity entity = minecraft.level.getEntity(trackedEntityId);

        if (entity == null || !entity.isAlive()) {
            return null;
        }

        return entity;
    }

    private static void initializeAim(Minecraft minecraft) {
        if (minecraft.player == null) {
            aimInitialized = false;
            return;
        }

        currentAimYaw = minecraft.player.getYRot();
        currentAimPitch = minecraft.player.getXRot();
        previousAimYaw = currentAimYaw;
        previousAimPitch = currentAimPitch;
        aimInitialized = true;
    }

    private static void advanceAim(Minecraft minecraft, Entity target) {
        if (!aimInitialized) {
            initializeAim(minecraft);
        }

        if (minecraft.player == null) {
            return;
        }

        AimAngles desired = desiredAim(minecraft.player.getEyePosition(), target.getEyePosition());
        previousAimYaw = currentAimYaw;
        previousAimPitch = currentAimPitch;
        currentAimYaw = advanceAngle(currentAimYaw, desired.yaw());
        currentAimPitch = advancePitch(currentAimPitch, desired.pitch());
        applyPlayerAngles(minecraft, currentAimYaw, currentAimPitch);
    }

    private static AimAngles desiredAim(Vec3 eye, Vec3 targetEye) {
        Vec3 delta = targetEye.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        return new AimAngles(yaw, Mth.clamp(pitch, -90.0F, 90.0F));
    }

    private static float advanceAngle(float current, float target) {
        float delta = Mth.wrapDegrees(target - current);
        float step = Mth.clamp(delta * TRACK_RESPONSE, -TRACK_MAX_TICK_STEP, TRACK_MAX_TICK_STEP);
        return current + step;
    }

    private static float advancePitch(float current, float target) {
        float delta = Mth.clamp(target - current, -90.0F, 90.0F);
        float step = Mth.clamp(delta * TRACK_RESPONSE, -TRACK_MAX_TICK_STEP, TRACK_MAX_TICK_STEP);
        return Mth.clamp(current + step, -90.0F, 90.0F);
    }

    private static float lerpDegrees(float partialTick, float previous, float current) {
        return previous + Mth.wrapDegrees(current - previous) * partialTick;
    }

    private static void applyPlayerAngles(Minecraft minecraft, float yaw, float pitch) {
        if (minecraft.player == null) {
            return;
        }

        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(pitch);
        minecraft.player.yHeadRot = yaw;
    }

    private static void clearTracking() {
        trackedEntityId = null;
        aimInitialized = false;
        previousAimYaw = 0.0F;
        previousAimPitch = 0.0F;
        currentAimYaw = 0.0F;
        currentAimPitch = 0.0F;
    }

    private static int buttonX() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow().getGuiScaledWidth() / 2 - BUTTON_WIDTH / 2;
    }

    private static int buttonY() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getWindow().getGuiScaledHeight() / 2 + 22;
    }

    private static void refreshCounts() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null || minecraft.player == null) {
            nearbyEntityCount = 0;
            highlightedEntityCount = 0;
        }
    }

    private record AimAngles(float yaw, float pitch) {
    }

    private SpectralHudEntityOverlayEvents() {
    }
}
