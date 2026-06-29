package io.github.yoglappland.spectralization.client.microlizer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineItemData;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class MicrolizedMachinePreviewRenderer {
    private static final float NORTH_MARKER_Y_OFFSET = 0.025F;
    private static final int NORTH_MARKER_COLOR = 0xFFD8D8D8;
    private static final int GRID_COLOR = 0x557E8580;
    private static final int AXIS_X_COLOR = 0xDDE25555;
    private static final int AXIS_Y_COLOR = 0xDD5FCF72;
    private static final int AXIS_Z_COLOR = 0xDD5B8CFF;
    private static final float WORK_AREA_VOLUME_EXPANSION = 0.01F;
    private static final int WORK_AREA_EDGE_COLOR = 0x888FE8FF;

    public static void render(
            GuiGraphics graphics,
            int previewLeft,
            int previewTop,
            int previewWidth,
            int previewHeight,
            List<MicrolizedMachineItemData.BlockEntry> blocks,
            List<MicrolizedMachineItemData.IoPortEntry> ioPorts,
            float previewYaw,
            float previewPitch,
            float previewZoom,
            double yOffset,
            boolean northMarker
    ) {
        render(
                graphics,
                previewLeft,
                previewTop,
                previewWidth,
                previewHeight,
                blocks,
                ioPorts,
                null,
                previewYaw,
                previewPitch,
                previewZoom,
                yOffset,
                northMarker
        );
    }

    public static void render(
            GuiGraphics graphics,
            int previewLeft,
            int previewTop,
            int previewWidth,
            int previewHeight,
            List<MicrolizedMachineItemData.BlockEntry> blocks,
            List<MicrolizedMachineItemData.IoPortEntry> ioPorts,
            PreviewVolume forcedVolume,
            float previewYaw,
            float previewPitch,
            float previewZoom,
            double yOffset,
            boolean northMarker
    ) {
        render(
                graphics,
                previewLeft,
                previewTop,
                previewWidth,
                previewHeight,
                blocks,
                ioPorts,
                forcedVolume,
                null,
                previewYaw,
                previewPitch,
                previewZoom,
                yOffset,
                northMarker
        );
    }

    public static void render(
            GuiGraphics graphics,
            int previewLeft,
            int previewTop,
            int previewWidth,
            int previewHeight,
            List<MicrolizedMachineItemData.BlockEntry> blocks,
            List<MicrolizedMachineItemData.IoPortEntry> ioPorts,
            PreviewVolume forcedVolume,
            PreviewVolume workAreaVolume,
            float previewYaw,
            float previewPitch,
            float previewZoom,
            double yOffset,
            boolean northMarker
    ) {
        if (blocks.isEmpty() && ioPorts.isEmpty() && forcedVolume == null && workAreaVolume == null) {
            return;
        }

        PreviewBounds bounds = PreviewBounds.of(blocks, ioPorts, forcedVolume, workAreaVolume);

        graphics.flush();
        graphics.enableScissor(previewLeft + 1, previewTop + 1, previewLeft + previewWidth - 1, previewTop + previewHeight - 1);
        RenderSystem.enableDepthTest();

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        try {
            poseStack.translate(previewLeft + previewWidth / 2.0D, previewTop + previewHeight / 2.0D + yOffset, 180.0D);
            float scale = previewScale(bounds, previewWidth, previewHeight, previewZoom);
            poseStack.scale(scale, -scale, scale);
            poseStack.mulPose(Axis.XP.rotationDegrees(previewPitch));
            poseStack.mulPose(Axis.YP.rotationDegrees(previewYaw));
            poseStack.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());

            Minecraft client = Minecraft.getInstance();
            BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
            MultiBufferSource.BufferSource bufferSource = graphics.bufferSource();

            if (workAreaVolume != null) {
                renderWorkAreaVolume(bufferSource, poseStack, workAreaVolume);
            }
            renderGridAndAxes(bufferSource, poseStack, bounds);

            if (!blocks.isEmpty()) {
                blocks.stream()
                        .sorted(Comparator.comparingInt(MicrolizedMachineItemData.BlockEntry::y)
                                .thenComparingInt(MicrolizedMachineItemData.BlockEntry::z)
                                .thenComparingInt(MicrolizedMachineItemData.BlockEntry::x))
                        .forEach(entry -> renderPreviewBlock(blockRenderer, bufferSource, poseStack, entry));
            }

            if (!ioPorts.isEmpty()) {
                ioPorts.stream()
                        .sorted(Comparator.comparingInt(MicrolizedMachineItemData.IoPortEntry::y)
                                .thenComparingInt(MicrolizedMachineItemData.IoPortEntry::z)
                                .thenComparingInt(MicrolizedMachineItemData.IoPortEntry::x))
                        .forEach(entry -> renderPreviewBlock(blockRenderer, bufferSource, poseStack, entry));
            }

            if (northMarker) {
                renderNorthMarker(bufferSource, poseStack, bounds);
            }
            graphics.flush();
        } finally {
            poseStack.popPose();
            RenderSystem.disableDepthTest();
            graphics.disableScissor();
        }
    }

    private static void renderWorkAreaVolume(
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            PreviewVolume volume
    ) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.gui());
        float minX = volume.minX() - WORK_AREA_VOLUME_EXPANSION;
        float minY = volume.minY() - WORK_AREA_VOLUME_EXPANSION;
        float minZ = volume.minZ() - WORK_AREA_VOLUME_EXPANSION;
        float maxX = volume.maxX() + 1.0F + WORK_AREA_VOLUME_EXPANSION;
        float maxY = volume.maxY() + 1.0F + WORK_AREA_VOLUME_EXPANSION;
        float maxZ = volume.maxZ() + 1.0F + WORK_AREA_VOLUME_EXPANSION;

        float stroke = 0.028F;
        cuboid(consumer, poseStack, minX, minY - stroke, minZ - stroke, maxX, minY + stroke, minZ + stroke, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, minX, minY - stroke, maxZ - stroke, maxX, minY + stroke, maxZ + stroke, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, minX, maxY - stroke, minZ - stroke, maxX, maxY + stroke, minZ + stroke, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, minX, maxY - stroke, maxZ - stroke, maxX, maxY + stroke, maxZ + stroke, WORK_AREA_EDGE_COLOR);

        cuboid(consumer, poseStack, minX - stroke, minY, minZ - stroke, minX + stroke, maxY, minZ + stroke, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, minX - stroke, minY, maxZ - stroke, minX + stroke, maxY, maxZ + stroke, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, maxX - stroke, minY, minZ - stroke, maxX + stroke, maxY, minZ + stroke, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, maxX - stroke, minY, maxZ - stroke, maxX + stroke, maxY, maxZ + stroke, WORK_AREA_EDGE_COLOR);

        cuboid(consumer, poseStack, minX - stroke, minY - stroke, minZ, minX + stroke, minY + stroke, maxZ, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, minX - stroke, maxY - stroke, minZ, minX + stroke, maxY + stroke, maxZ, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, maxX - stroke, minY - stroke, minZ, maxX + stroke, minY + stroke, maxZ, WORK_AREA_EDGE_COLOR);
        cuboid(consumer, poseStack, maxX - stroke, maxY - stroke, minZ, maxX + stroke, maxY + stroke, maxZ, WORK_AREA_EDGE_COLOR);
    }

    private static void renderGridAndAxes(MultiBufferSource bufferSource, PoseStack poseStack, PreviewBounds bounds) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.gui());
        float y = 0.018F;
        float minX = bounds.minX() - 0.5F;
        float maxX = bounds.maxX() + 1.5F;
        float minZ = bounds.minZ() - 0.5F;
        float maxZ = bounds.maxZ() + 1.5F;
        float stroke = 0.018F;

        for (int x = bounds.minX(); x <= bounds.maxX() + 1; x++) {
            rectOnGround(consumer, poseStack, x - stroke, minZ, x + stroke, maxZ, y, GRID_COLOR);
        }

        for (int z = bounds.minZ(); z <= bounds.maxZ() + 1; z++) {
            rectOnGround(consumer, poseStack, minX, z - stroke, maxX, z + stroke, y, GRID_COLOR);
        }

        float axisStroke = 0.045F;
        rectOnGround(consumer, poseStack, bounds.minX(), minZ - 0.20F, bounds.maxX() + 1.0F, minZ - 0.20F + axisStroke, y + 0.006F, AXIS_X_COLOR);
        rectOnGround(consumer, poseStack, minX - 0.20F, bounds.minZ(), minX - 0.20F + axisStroke, bounds.maxZ() + 1.0F, y + 0.012F, AXIS_Z_COLOR);
        verticalAxis(consumer, poseStack, minX - 0.20F, minZ - 0.20F, bounds.minY(), bounds.maxY() + 1.0F, AXIS_Y_COLOR);
    }

    public static float clampZoom(float value) {
        return clamp(value, 0.55F, 2.2F);
    }

    public static float clampPitch(float value) {
        return clamp(value, -80.0F, 80.0F);
    }

    private static void renderPreviewBlock(
            BlockRenderDispatcher blockRenderer,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            MicrolizedMachineItemData.BlockEntry entry
    ) {
        BlockState state = entry.state();
        if (state.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(entry.x(), entry.y(), entry.z());
        blockRenderer.renderSingleBlock(state, poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void renderPreviewBlock(
            BlockRenderDispatcher blockRenderer,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            MicrolizedMachineItemData.IoPortEntry entry
    ) {
        BlockState state = entry.state();
        if (state.getRenderShape() != RenderShape.MODEL) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(entry.x(), entry.y(), entry.z());
        blockRenderer.renderSingleBlock(state, poseStack, bufferSource, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void renderNorthMarker(MultiBufferSource bufferSource, PoseStack poseStack, PreviewBounds bounds) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.gui());
        float size = 0.78F;
        float cx = bounds.maxX() + 0.5F;
        float cz = Math.round((bounds.minZ() + bounds.maxZ()) * 0.5F) + 0.5F;
        float y = NORTH_MARKER_Y_OFFSET;
        float south = cx - size * 0.5F;
        float north = cx + size * 0.5F;
        float west = cz - size * 0.5F;
        float east = cz + size * 0.5F;
        float stroke = size * 0.17F;

        rectOnGround(consumer, poseStack, south, west, north, west + stroke, y, NORTH_MARKER_COLOR);
        rectOnGround(consumer, poseStack, south, east - stroke, north, east, y, NORTH_MARKER_COLOR);
        quadOnGround(
                consumer,
                poseStack,
                north,
                west,
                north,
                west + stroke,
                south,
                east - stroke,
                south,
                east,
                y,
                NORTH_MARKER_COLOR
        );
    }

    private static void rectOnGround(
            VertexConsumer consumer,
            PoseStack poseStack,
            float south,
            float west,
            float north,
            float east,
            float y,
            int color
    ) {
        quadOnGround(consumer, poseStack, south, west, north, west, north, east, south, east, y, color);
    }

    private static void quadOnGround(
            VertexConsumer consumer,
            PoseStack poseStack,
            float x0,
            float z0,
            float x1,
            float z1,
            float x2,
            float z2,
            float x3,
            float z3,
            float y,
            int color
    ) {
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, x0, y, z0).setColor(color);
        consumer.addVertex(pose, x1, y, z1).setColor(color);
        consumer.addVertex(pose, x2, y, z2).setColor(color);
        consumer.addVertex(pose, x3, y, z3).setColor(color);
        consumer.addVertex(pose, x3, y, z3).setColor(color);
        consumer.addVertex(pose, x2, y, z2).setColor(color);
        consumer.addVertex(pose, x1, y, z1).setColor(color);
        consumer.addVertex(pose, x0, y, z0).setColor(color);
    }

    private static void cuboid(
            VertexConsumer consumer,
            PoseStack poseStack,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            int color
    ) {
        quad(consumer, poseStack, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, color);
        quad(consumer, poseStack, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, color);
        quad(consumer, poseStack, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, color);
        quad(consumer, poseStack, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, color);
        quad(consumer, poseStack, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, color);
        quad(consumer, poseStack, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, color);
    }

    private static void quad(
            VertexConsumer consumer,
            PoseStack poseStack,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            int color
    ) {
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x2, y2, z2).setColor(color);
        consumer.addVertex(pose, x3, y3, z3).setColor(color);
        consumer.addVertex(pose, x3, y3, z3).setColor(color);
        consumer.addVertex(pose, x2, y2, z2).setColor(color);
        consumer.addVertex(pose, x1, y1, z1).setColor(color);
        consumer.addVertex(pose, x0, y0, z0).setColor(color);
    }

    private static void verticalAxis(
            VertexConsumer consumer,
            PoseStack poseStack,
            float x,
            float z,
            float minY,
            float maxY,
            int color
    ) {
        float stroke = 0.045F;
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, x - stroke, minY, z).setColor(color);
        consumer.addVertex(pose, x + stroke, minY, z).setColor(color);
        consumer.addVertex(pose, x + stroke, maxY, z).setColor(color);
        consumer.addVertex(pose, x - stroke, maxY, z).setColor(color);
        consumer.addVertex(pose, x - stroke, maxY, z).setColor(color);
        consumer.addVertex(pose, x + stroke, maxY, z).setColor(color);
        consumer.addVertex(pose, x + stroke, minY, z).setColor(color);
        consumer.addVertex(pose, x - stroke, minY, z).setColor(color);
    }

    private static float previewScale(PreviewBounds bounds, int previewWidth, int previewHeight, float previewZoom) {
        int maxSize = Math.max(1, Math.max(bounds.sizeX(), Math.max(bounds.sizeY(), bounds.sizeZ())));
        return Math.max(4.0F, (Math.min(previewWidth, previewHeight) - 10.0F) / maxSize) * previewZoom;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record PreviewVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public PreviewVolume {
            if (maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("Preview volume max must be greater than or equal to min");
            }
        }
    }

    private record PreviewBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static PreviewBounds of(
                List<MicrolizedMachineItemData.BlockEntry> blocks,
                List<MicrolizedMachineItemData.IoPortEntry> ioPorts,
                PreviewVolume forcedVolume,
                PreviewVolume workAreaVolume
        ) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;

            if (forcedVolume != null) {
                minX = forcedVolume.minX();
                minY = forcedVolume.minY();
                minZ = forcedVolume.minZ();
                maxX = forcedVolume.maxX();
                maxY = forcedVolume.maxY();
                maxZ = forcedVolume.maxZ();
            }

            if (workAreaVolume != null) {
                minX = Math.min(minX, workAreaVolume.minX());
                minY = Math.min(minY, workAreaVolume.minY());
                minZ = Math.min(minZ, workAreaVolume.minZ());
                maxX = Math.max(maxX, workAreaVolume.maxX());
                maxY = Math.max(maxY, workAreaVolume.maxY());
                maxZ = Math.max(maxZ, workAreaVolume.maxZ());
            }

            for (MicrolizedMachineItemData.BlockEntry block : blocks) {
                minX = Math.min(minX, block.x());
                minY = Math.min(minY, block.y());
                minZ = Math.min(minZ, block.z());
                maxX = Math.max(maxX, block.x());
                maxY = Math.max(maxY, block.y());
                maxZ = Math.max(maxZ, block.z());
            }

            for (MicrolizedMachineItemData.IoPortEntry port : ioPorts) {
                minX = Math.min(minX, port.x());
                minY = Math.min(minY, port.y());
                minZ = Math.min(minZ, port.z());
                maxX = Math.max(maxX, port.x());
                maxY = Math.max(maxY, port.y());
                maxZ = Math.max(maxZ, port.z());
            }

            if (minX == Integer.MAX_VALUE) {
                return new PreviewBounds(0, 0, 0, 0, 0, 0);
            }

            return new PreviewBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private int sizeX() {
            return maxX - minX + 1;
        }

        private int sizeY() {
            return maxY - minY + 1;
        }

        private int sizeZ() {
            return maxZ - minZ + 1;
        }

        private double centerX() {
            return (minX + maxX + 1) / 2.0D;
        }

        private double centerY() {
            return (minY + maxY + 1) / 2.0D;
        }

        private double centerZ() {
            return (minZ + maxZ + 1) / 2.0D;
        }
    }

    private MicrolizedMachinePreviewRenderer() {
    }
}
