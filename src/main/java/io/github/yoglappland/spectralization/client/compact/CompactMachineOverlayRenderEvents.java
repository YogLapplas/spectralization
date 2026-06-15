package io.github.yoglappland.spectralization.client.compact;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.network.CompactMachineAnimationPayload;
import io.github.yoglappland.spectralization.network.CompactMachineOverlayPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
    private static final int SPARK_R = 0xFF;
    private static final int SPARK_G = 0xE7;
    private static final int SPARK_B = 0x55;
    private static final int SPARK_CORE_R = 0xFF;
    private static final int SPARK_CORE_G = 0xFF;
    private static final int SPARK_CORE_B = 0xD1;
    private static final int PROJECTION_R = 0xFF;
    private static final int PROJECTION_G = 0xD4;
    private static final int PROJECTION_B = 0x64;
    private static final int TRACK_R = 0xEE;
    private static final int TRACK_G = 0xFF;
    private static final int TRACK_B = 0xD8;
    private static final double BEAM_HALF_WIDTH = 1.0D / 16.0D;
    private static final double WORK_AREA_EXPANSION = 0.08D / 16.0D;
    private static final int SPARK_COUNT = 56;
    private static final int TRACK_COUNT = 18;
    private static final double ORBITING_SPARK_SIZE_SCALE = 0.76D;
    private static final double INITIAL_ORBIT_SPEED = 0.42D;
    private static final double ACCELERATED_ORBIT_SPEED = 0.519D;
    private static final double COLLAPSE_ORBIT_SPEED = 0.5796D;
    private static final double GOLDEN_ANGLE = 2.399963229728653D;
    private static final ResourceLocation SPARK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_core.png");
    private static final ResourceLocation SPARK_HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_halo.png");
    private static final RenderType SPARK_RENDER_TYPE = RenderType.entityTranslucent(SPARK_TEXTURE);
    private static final RenderType SPARK_HALO_RENDER_TYPE = RenderType.entityTranslucent(SPARK_HALO_TEXTURE);

    @SubscribeEvent
    public static void renderCompactMachineConnections(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        if (minecraft.level == null || minecraft.player == null) {
            ClientCompactMachineOverlayCache.clear();
            ClientCompactMachineAnimationCache.clear();
            return;
        }

        var segments = ClientCompactMachineOverlayCache.activeSegments();
        var workArea = ClientCompactMachineWorkAreaOverlayCache.activeWorkArea();
        var animations = ClientCompactMachineAnimationCache.activeAnimations(
                minecraft.level.getGameTime(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false)
        );
        if (segments.isEmpty() && workArea.isEmpty() && animations.isEmpty()) {
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
        for (ClientCompactMachineAnimationCache.ActiveAnimation animation : animations) {
            renderCompressionProjection(consumer, pose, animation);
        }
        bufferSource.endBatch(RenderType.debugQuads());

        VertexConsumer haloConsumer = bufferSource.getBuffer(SPARK_HALO_RENDER_TYPE);
        for (ClientCompactMachineAnimationCache.ActiveAnimation animation : animations) {
            renderCompressionSparks(haloConsumer, pose, animation, true);
        }
        bufferSource.endBatch(SPARK_HALO_RENDER_TYPE);

        VertexConsumer sparkConsumer = bufferSource.getBuffer(SPARK_RENDER_TYPE);
        for (ClientCompactMachineAnimationCache.ActiveAnimation animation : animations) {
            renderCompressionSparks(sparkConsumer, pose, animation, false);
        }
        poseStack.popPose();
        bufferSource.endBatch(SPARK_RENDER_TYPE);
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

    private static void renderCompressionProjection(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientCompactMachineAnimationCache.ActiveAnimation animation
    ) {
        BlockPos min = animation.min();
        BlockPos max = animation.max();
        double age = animation.age();
        double minX = min.getX();
        double minY = min.getY();
        double minZ = min.getZ();
        double maxX = max.getX() + 1.0D;
        double maxY = max.getY() + 1.0D;
        double maxZ = max.getZ() + 1.0D;
        double centerX = (minX + maxX) * 0.5D;
        double centerY = (minY + maxY) * 0.5D;
        double centerZ = (minZ + maxZ) * 0.5D;
        double maxExtent = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));

        if (age >= CompactMachineAnimationPayload.CLEAR_WORK_AREA_AT_TICKS
                && age < CompactMachineAnimationPayload.MERGE_AT_TICKS) {
            renderProjectedBlocks(consumer, pose, animation, centerX, centerY, centerZ);
        }

        if (age >= CompactMachineAnimationPayload.MERGE_AT_TICKS) {
            renderFinalParticleTracks(consumer, pose, animation, centerX, centerY, centerZ, maxExtent);
        }
    }

    private static void renderCompressionSparks(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientCompactMachineAnimationCache.ActiveAnimation animation,
            boolean halo
    ) {
        BlockPos min = animation.min();
        BlockPos max = animation.max();
        double age = animation.age();
        double minX = min.getX();
        double minY = min.getY();
        double minZ = min.getZ();
        double maxX = max.getX() + 1.0D;
        double maxY = max.getY() + 1.0D;
        double maxZ = max.getZ() + 1.0D;
        double centerX = (minX + maxX) * 0.5D;
        double centerY = (minY + maxY) * 0.5D;
        double centerZ = (minZ + maxZ) * 0.5D;
        double maxExtent = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        double diagonal = diagonal(maxX - minX, maxY - minY, maxZ - minZ);

        if (age < CompactMachineAnimationPayload.MERGE_AT_TICKS) {
            renderOrbitingSparks(consumer, pose, animation, centerX, centerY, centerZ, maxExtent, diagonal, halo);
        } else {
            renderFinalSpark(consumer, pose, animation, centerX, centerY, centerZ, diagonal, halo);
        }
    }

    private static void renderProjectedBlocks(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientCompactMachineAnimationCache.ActiveAnimation animation,
            double originX,
            double originY,
            double originZ
    ) {
        double collapse = collapseProgress(animation.age());
        double scale = Math.max(0.0D, 1.0D - collapse);
        if (scale <= 0.02D) {
            return;
        }

        int alpha = (int) Math.round(112.0D * scale);
        double halfSize = 0.5D * scale;

        for (BlockPos block : animation.projectedBlocks()) {
            double blockCenterX = block.getX() + 0.5D;
            double blockCenterY = block.getY() + 0.5D;
            double blockCenterZ = block.getZ() + 0.5D;
            double x = lerp(blockCenterX, originX, collapse);
            double y = lerp(blockCenterY, originY, collapse);
            double z = lerp(blockCenterZ, originZ, collapse);
            renderCube(
                    consumer,
                    pose,
                    x - halfSize,
                    y - halfSize,
                    z - halfSize,
                    x + halfSize,
                    y + halfSize,
                    z + halfSize,
                    PROJECTION_R,
                    PROJECTION_G,
                    PROJECTION_B,
                    alpha
            );
        }
    }

    private static void renderOrbitingSparks(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientCompactMachineAnimationCache.ActiveAnimation animation,
            double centerX,
            double centerY,
            double centerZ,
            double maxExtent,
            double diagonal,
            boolean halo
    ) {
        double collapse = collapseProgress(animation.age());
        double radialScale = Math.max(0.0D, 1.0D - collapse);
        double baseRadius = Math.max(1.75D, diagonal);
        int alpha = (int) Math.round((halo ? 96.0D : 184.0D) * Math.max(0.0D, 1.0D - collapse * 0.24D));

        for (int index = 0; index < SPARK_COUNT; index++) {
            double seed = unitHash(animation.corePos().asLong() + index * 73471L);
            double seedB = unitHash(animation.corePos().asLong() + index * 91283L + 19L);
            double seedC = unitHash(animation.corePos().asLong() + index * 61843L + 47L);
            double seedD = unitHash(animation.corePos().asLong() + index * 24593L + 73L);
            double lineSpeed = 0.65D + seed * 0.70D;
            double accelerationScale = 0.55D + seedD * 0.90D;
            double orbitAngle = orbitAngle(animation.age(), accelerationScale);
            double ringRadius = baseRadius * (0.78D + (seedB - 0.5D) * 0.10D);
            double verticalOffset = (seedC - 0.5D) * Math.max(0.24D, maxExtent * 0.18D);
            double orbitRadius = Math.max(baseRadius * 0.45D, ringRadius);
            double angularSpeedScale = lineSpeed * baseRadius * 0.64D / orbitRadius;
            angularSpeedScale = Math.max(0.38D, Math.min(1.85D, angularSpeedScale));
            double theta = orbitAngle * angularSpeedScale + index * GOLDEN_ANGLE + seedC * 1.1D;
            double radius = ringRadius * radialScale;
            double x = centerX + Math.cos(theta) * radius;
            double y = centerY + verticalOffset * radialScale;
            double z = centerZ + Math.sin(theta) * radius;
            double size = (0.11D + seedD * 0.12D + maxExtent * 0.015D)
                    * (0.82D + 0.18D * radialScale)
                    * ORBITING_SPARK_SIZE_SCALE;
            int color = sparkColor(animation.corePos().asLong(), index, animation.age());

            if (halo) {
                renderSpark(consumer, pose, x, y, z, size * 2.35D, alpha, color);
            } else {
                renderSpark(consumer, pose, x, y, z, size, alpha, mixWithWhite(color, 0.24D));
            }
        }
    }

    private static void renderFinalSpark(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientCompactMachineAnimationCache.ActiveAnimation animation,
            double centerX,
            double centerY,
            double centerZ,
            double diagonal,
            boolean halo
    ) {
        double t = smoothRange(
                animation.age(),
                CompactMachineAnimationPayload.MERGE_AT_TICKS,
                animation.durationTicks()
        );
        double halfSize = lerp(Math.max(1.35D, diagonal * 0.46D) * 0.7D, 0.045D, easeInCubic(t));
        int alpha = (int) Math.round((halo ? 180.0D : 255.0D) * (1.0D - t));

        if (alpha > 0) {
            double size = halo ? halfSize * 1.85D : halfSize * 0.82D;
            renderSpark(consumer, pose, centerX, centerY, centerZ, size, alpha, 0xFFFFFF);
        }
    }

    private static void renderFinalParticleTracks(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientCompactMachineAnimationCache.ActiveAnimation animation,
            double centerX,
            double centerY,
            double centerZ,
            double maxExtent
    ) {
        double finalSpan = Math.max(1.0D, animation.durationTicks() - CompactMachineAnimationPayload.MERGE_AT_TICKS);
        double burst = easeOutCubic(smoothRange(
                animation.age(),
                CompactMachineAnimationPayload.MERGE_AT_TICKS,
                CompactMachineAnimationPayload.MERGE_AT_TICKS + finalSpan * 0.72D
        ));
        double fade = 1.0D - smoothRange(
                animation.age(),
                CompactMachineAnimationPayload.MERGE_AT_TICKS + finalSpan * 0.28D,
                animation.durationTicks()
        );

        if (burst <= 0.0D || fade <= 0.0D) {
            return;
        }

        long base = animation.corePos().asLong();
        Vec3 center = new Vec3(centerX, centerY, centerZ);

        for (int index = 0; index < TRACK_COUNT; index++) {
            double seed = unitHash(base + index * 15731L + 5L);
            double seedB = unitHash(base + index * 31469L + 11L);
            double seedC = unitHash(base + index * 52711L + 23L);
            Vec3 direction = trackDirection(base, index);
            Vec3 side = perpendicular(direction, seedB);
            Vec3 bend = side.scale((0.25D + seedC * 0.32D) * maxExtent * burst);
            double length = Math.max(1.2D, maxExtent * (0.82D + seed * 1.35D)) * burst;
            double halfWidth = 0.018D + seedB * 0.026D;
            int alpha = (int) Math.round((150.0D + seed * 70.0D) * fade);
            int segments = 4;

            Vec3 previous = center.add(direction.scale(Math.max(0.18D, maxExtent * 0.08D)));
            for (int segment = 1; segment <= segments; segment++) {
                double step = segment / (double) segments;
                double curve = Math.sin(step * Math.PI * (0.72D + seedB * 0.45D) + seedC * Math.PI);
                Vec3 next = center
                        .add(direction.scale(length * step))
                        .add(bend.scale(curve * step));
                renderTrackSegment(consumer, pose, previous, next, halfWidth, alpha);
                previous = next;
            }
        }
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

    private static void renderTrackSegment(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            double halfWidth,
            int alpha
    ) {
        if (alpha <= 0) {
            return;
        }

        Vec3 delta = end.subtract(start);
        if (delta.lengthSqr() < 1.0E-8D) {
            return;
        }

        Vec3 side = delta.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-8D) {
            side = delta.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }

        side = side.normalize().scale(halfWidth);
        Vec3 sideB = delta.cross(side).normalize().scale(halfWidth * 0.72D);
        addTrackQuad(consumer, pose, start, end, side, alpha);
        addTrackQuad(consumer, pose, start, end, sideB, Math.max(0, (int) Math.round(alpha * 0.72D)));
    }

    private static void addTrackQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            Vec3 start,
            Vec3 end,
            Vec3 side,
            int alpha
    ) {
        addColoredQuad(
                consumer,
                pose,
                start.x - side.x,
                start.y - side.y,
                start.z - side.z,
                start.x + side.x,
                start.y + side.y,
                start.z + side.z,
                end.x + side.x,
                end.y + side.y,
                end.z + side.z,
                end.x - side.x,
                end.y - side.y,
                end.z - side.z,
                TRACK_R,
                TRACK_G,
                TRACK_B,
                alpha
        );
    }

    private static void renderSpark(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            double halfSize,
            int alpha,
            int color
    ) {
        int red = red(color);
        int green = green(color);
        int blue = blue(color);
        addTexturedQuad(consumer, pose, x - halfSize, y, z - halfSize, x + halfSize, y, z - halfSize, x + halfSize, y, z + halfSize, x - halfSize, y, z + halfSize, red, green, blue, alpha);
        addTexturedQuad(consumer, pose, x - halfSize, y - halfSize, z, x + halfSize, y - halfSize, z, x + halfSize, y + halfSize, z, x - halfSize, y + halfSize, z, red, green, blue, alpha);
        addTexturedQuad(consumer, pose, x, y - halfSize, z - halfSize, x, y + halfSize, z - halfSize, x, y + halfSize, z + halfSize, x, y - halfSize, z + halfSize, red, green, blue, alpha);
    }

    private static void addTexturedQuad(
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
        addTexturedQuad(
                consumer,
                pose,
                x1,
                y1,
                z1,
                x2,
                y2,
                z2,
                x3,
                y3,
                z3,
                x4,
                y4,
                z4,
                0.0D,
                1.0D,
                1.0D,
                1.0D,
                1.0D,
                0.0D,
                0.0D,
                0.0D,
                red,
                green,
                blue,
                alpha
        );
    }

    private static void addTexturedQuad(
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
            double u1,
            double v1,
            double u2,
            double v2,
            double u3,
            double v3,
            double u4,
            double v4,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addTexturedVertex(consumer, pose, x1, y1, z1, (float) u1, (float) v1, red, green, blue, alpha);
        addTexturedVertex(consumer, pose, x2, y2, z2, (float) u2, (float) v2, red, green, blue, alpha);
        addTexturedVertex(consumer, pose, x3, y3, z3, (float) u3, (float) v3, red, green, blue, alpha);
        addTexturedVertex(consumer, pose, x4, y4, z4, (float) u4, (float) v4, red, green, blue, alpha);
    }

    private static void addTexturedVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static void renderCube(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addColoredQuad(consumer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        addColoredQuad(consumer, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, red, green, blue, alpha);
        addColoredQuad(consumer, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        addColoredQuad(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, red, green, blue, alpha);
        addColoredQuad(consumer, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        addColoredQuad(consumer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void addColoredQuad(
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

    private static double lerp(double from, double to, double progress) {
        return from + (to - from) * progress;
    }

    private static double diagonal(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double orbitAngle(double age, double accelerationScale) {
        double slowEnd = CompactMachineAnimationPayload.SLOW_ORBIT_TICKS;
        double collapseStart = CompactMachineAnimationPayload.CLEAR_WORK_AREA_AT_TICKS;
        double mergeStart = CompactMachineAnimationPayload.MERGE_AT_TICKS;
        double slowAge = Math.min(age, slowEnd);
        double angle = slowAge * INITIAL_ORBIT_SPEED;
        double acceleratedSpeed = INITIAL_ORBIT_SPEED
                + (ACCELERATED_ORBIT_SPEED - INITIAL_ORBIT_SPEED) * accelerationScale;
        double collapseSpeed = acceleratedSpeed
                + (COLLAPSE_ORBIT_SPEED - ACCELERATED_ORBIT_SPEED) * accelerationScale;

        if (age > slowEnd) {
            double accelAge = Math.min(
                    age - slowEnd,
                    collapseStart - slowEnd
            );
            angle += integratedSmoothSpeed(
                    accelAge,
                    collapseStart - slowEnd,
                    INITIAL_ORBIT_SPEED,
                    acceleratedSpeed
            );
        }

        if (age > collapseStart) {
            double collapseAge = Math.min(age - collapseStart, mergeStart - collapseStart);
            angle += integratedSmoothSpeed(
                    collapseAge,
                    mergeStart - collapseStart,
                    acceleratedSpeed,
                    collapseSpeed
            );
        }

        return angle;
    }

    private static double integratedSmoothSpeed(double age, double duration, double fromSpeed, double toSpeed) {
        if (duration <= 0.0D) {
            return age * toSpeed;
        }

        double t = clamp(age / duration);
        double smoothIntegral = t * t * t * t * (2.5D + t * (-3.0D + t));
        return fromSpeed * age + (toSpeed - fromSpeed) * duration * smoothIntegral;
    }

    private static double collapseProgress(double age) {
        return smootherStep(smoothRange(
                age,
                CompactMachineAnimationPayload.CLEAR_WORK_AREA_AT_TICKS,
                CompactMachineAnimationPayload.MERGE_AT_TICKS
        ));
    }

    private static double smoothRange(double value, double from, double to) {
        if (to <= from) {
            return value >= to ? 1.0D : 0.0D;
        }

        return clamp((value - from) / (to - from));
    }

    private static double easeOutCubic(double value) {
        double t = clamp(value);
        double inverse = 1.0D - t;
        return 1.0D - inverse * inverse * inverse;
    }

    private static double easeInCubic(double value) {
        double t = clamp(value);
        return t * t * t;
    }

    private static double smootherStep(double value) {
        double t = clamp(value);
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    private static Vec3 trackDirection(long base, int index) {
        double seed = unitHash(base + index * 43237L + 101L);
        double vertical = -0.88D + unitHash(base + index * 92821L + 17L) * 1.76D;
        double horizontal = Math.sqrt(Math.max(0.0D, 1.0D - vertical * vertical));
        double theta = index * GOLDEN_ANGLE + seed * Math.PI * 2.0D;
        return new Vec3(Math.cos(theta) * horizontal, vertical, Math.sin(theta) * horizontal).normalize();
    }

    private static Vec3 perpendicular(Vec3 direction, double seed) {
        Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-8D) {
            side = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }

        side = side.normalize();
        Vec3 sideB = direction.cross(side).normalize();
        double angle = seed * Math.PI * 2.0D;
        return side.scale(Math.cos(angle)).add(sideB.scale(Math.sin(angle))).normalize();
    }

    private static int sparkColor(long base, int index, double age) {
        double hue = unitHash(base + index * 80837L + 131L);
        hue = (hue + age * 0.0018D) % 1.0D;
        return hsvToRgb(hue, 0.72D, 1.0D);
    }

    private static int hsvToRgb(double hue, double saturation, double value) {
        double h = (hue - Math.floor(hue)) * 6.0D;
        int sector = (int) Math.floor(h);
        double f = h - sector;
        double p = value * (1.0D - saturation);
        double q = value * (1.0D - saturation * f);
        double t = value * (1.0D - saturation * (1.0D - f));

        double red;
        double green;
        double blue;

        switch (sector) {
            case 0 -> {
                red = value;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = value;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = value;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = value;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = value;
            }
            default -> {
                red = value;
                green = p;
                blue = q;
            }
        }

        return rgb(
                (int) Math.round(red * 255.0D),
                (int) Math.round(green * 255.0D),
                (int) Math.round(blue * 255.0D)
        );
    }

    private static int mixWithWhite(int color, double amount) {
        double t = clamp(amount);
        return rgb(
                (int) Math.round(lerp(red(color), 255.0D, t)),
                (int) Math.round(lerp(green(color), 255.0D, t)),
                (int) Math.round(lerp(blue(color), 255.0D, t))
        );
    }

    private static int rgb(int red, int green, int blue) {
        return (clampColor(red) << 16) | (clampColor(green) << 8) | clampColor(blue);
    }

    private static int red(int color) {
        return color >> 16 & 0xFF;
    }

    private static int green(int color) {
        return color >> 8 & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double unitHash(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (mixed & 0xFFFFFFL) / (double) 0x1000000L;
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static double clampSigned(double value) {
        return Math.max(-0.96D, Math.min(0.96D, value));
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
