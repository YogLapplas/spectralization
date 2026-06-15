package io.github.yoglappland.spectralization.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.yoglappland.spectralization.client.gui.SpectralGui;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.compact.CompactedMachineFaceColor;
import io.github.yoglappland.spectralization.compact.CompactedMachineItemData;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.menu.CompactedMachineMenu;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class CompactedMachineScreen extends SpectralMachineScreen<CompactedMachineMenu> {
    private static final int PREVIEW_X = 26;
    private static final int PREVIEW_Y = 46;
    private static final int PREVIEW_WIDTH = 72;
    private static final int PREVIEW_HEIGHT = 86;
    private static final float NORTH_MARKER_Y_OFFSET = 0.025F;
    private static final int NORTH_MARKER_COLOR = 0xFFD8D8D8;
    private static final Direction[] FACE_ORDER = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.UP,
            Direction.DOWN
    };

    private final Button[] colorButtons = new Button[FACE_ORDER.length];
    private float previewYaw = -35.0F;
    private float previewPitch = 28.0F;
    private float previewZoom = 1.0F;
    private boolean draggingPreview;

    public CompactedMachineScreen(CompactedMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "compacted_machine", 256, 170, 0, 160);
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> click(CompactedMachineMenu.BUTTON_ROTATE_COUNTERCLOCKWISE)
                )
                .bounds(leftPos + 136, topPos + 84, 22, 18)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> click(CompactedMachineMenu.BUTTON_ROTATE_CLOCKWISE)
                )
                .bounds(leftPos + 162, topPos + 84, 22, 18)
                .build());

        for (int index = 0; index < FACE_ORDER.length; index++) {
            Direction face = FACE_ORDER[index];
            Button button = Button.builder(
                            colorButtonText(face),
                            clicked -> click(CompactedMachineMenu.BUTTON_FACE_COLOR_BASE + face.ordinal())
                    )
                    .bounds(leftPos + 124 + (index % 3) * 36, topPos + 122 + (index / 3) * 18, 34, 14)
                    .build();
            colorButtons[index] = button;
            addRenderableWidget(button);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int index = 0; index < FACE_ORDER.length; index++) {
            colorButtons[index].setMessage(colorButtonText(FACE_ORDER[index]));
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawMachineBackground(graphics);
        panel(graphics, "preview_panel", 18, 22, 88, 132);
        panel(graphics, "info_panel", 112, 22, 126, 132);
        renderSnapshotPreview(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);

        text(graphics, "size_text", label("screen.spectralization.compacted_machine.size")
                + ": " + dimensions(menu.sizeX(), menu.sizeY(), menu.sizeZ()), 124, 34, 104);
        text(graphics, "blocks_text", label("screen.spectralization.compacted_machine.blocks")
                + ": " + countAndTypes(menu.blockCount(), menu.typeCount()), 124, 48, 104);
        text(graphics, "io_text", label("screen.spectralization.compacted_machine.io")
                + ": " + menu.ioFaceCount(), 124, 62, 104);
        text(graphics, "mapping_text", label("screen.spectralization.compacted_machine.mapping")
                + ": " + menu.transferCount() + "/" + menu.sourceCount(), 124, 76, 104);
        text(graphics, "facing_text", label("screen.spectralization.compacted_machine.facing")
                + ": " + directionLabel(menu.facing()), 190, 88, 38);

        mutedText(graphics, "rotation_title", label("screen.spectralization.compacted_machine.rotation"), 124, 88, 44);
        mutedText(graphics, "face_colors_title", label("screen.spectralization.compacted_machine.face_colors"), 124, 110, 98);
        renderColorSwatches(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insidePreview(mouseX, mouseY) && button == 0 && !SpectralizationConfig.uiDebug()) {
            draggingPreview = true;
            return true;
        }

        if (insidePreview(mouseX, mouseY) && button == 1) {
            previewYaw = -35.0F;
            previewPitch = 28.0F;
            previewZoom = 1.0F;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPreview && button == 0) {
            previewYaw += (float) dragX * 0.7F;
            previewPitch = clamp(previewPitch + (float) dragY * 0.7F, -80.0F, 80.0F);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingPreview && button == 0) {
            draggingPreview = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insidePreview(mouseX, mouseY)) {
            previewZoom = clamp(previewZoom + (float) scrollY * 0.08F, 0.55F, 2.2F);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void renderSnapshotPreview(GuiGraphics graphics) {
        int previewLeft = leftPos + PREVIEW_X;
        int previewTop = topPos + PREVIEW_Y;
        SpectralGui.drawInset(graphics, previewLeft, previewTop, PREVIEW_WIDTH, PREVIEW_HEIGHT);

        List<CompactedMachineItemData.BlockEntry> blocks = menu.snapshotBlocks();
        List<CompactedMachineItemData.IoPortEntry> ioPorts = menu.snapshotIoPorts();
        if (blocks.isEmpty() && ioPorts.isEmpty()) {
            return;
        }
        PreviewBounds bounds = PreviewBounds.of(blocks, ioPorts);

        graphics.flush();
        graphics.enableScissor(previewLeft + 1, previewTop + 1, previewLeft + PREVIEW_WIDTH - 1, previewTop + PREVIEW_HEIGHT - 1);
        RenderSystem.enableDepthTest();

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        try {
            poseStack.translate(previewLeft + PREVIEW_WIDTH / 2.0D, previewTop + PREVIEW_HEIGHT / 2.0D + 10.0D, 180.0D);
            float scale = previewScale(bounds);
            poseStack.scale(scale, -scale, scale);
            poseStack.mulPose(Axis.XP.rotationDegrees(previewPitch));
            poseStack.mulPose(Axis.YP.rotationDegrees(previewYaw));
            poseStack.translate(-bounds.centerX(), -bounds.centerY(), -bounds.centerZ());

            Minecraft client = Minecraft.getInstance();
            BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
            MultiBufferSource.BufferSource bufferSource = graphics.bufferSource();

            if (!blocks.isEmpty()) {
                blocks.stream()
                        .sorted(Comparator.comparingInt(CompactedMachineItemData.BlockEntry::y)
                                .thenComparingInt(CompactedMachineItemData.BlockEntry::z)
                                .thenComparingInt(CompactedMachineItemData.BlockEntry::x))
                        .forEach(entry -> renderPreviewBlock(blockRenderer, bufferSource, poseStack, entry));
            }

            if (!ioPorts.isEmpty()) {
                ioPorts.stream()
                        .sorted(Comparator.comparingInt(CompactedMachineItemData.IoPortEntry::y)
                                .thenComparingInt(CompactedMachineItemData.IoPortEntry::z)
                                .thenComparingInt(CompactedMachineItemData.IoPortEntry::x))
                        .forEach(entry -> renderPreviewBlock(blockRenderer, bufferSource, poseStack, entry));
            }

            renderNorthMarker(bufferSource, poseStack, bounds);
            graphics.flush();
        } finally {
            poseStack.popPose();
            RenderSystem.disableDepthTest();
            graphics.disableScissor();
        }
    }

    private void renderPreviewBlock(
            BlockRenderDispatcher blockRenderer,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            CompactedMachineItemData.BlockEntry entry
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

    private void renderPreviewBlock(
            BlockRenderDispatcher blockRenderer,
            MultiBufferSource bufferSource,
            PoseStack poseStack,
            CompactedMachineItemData.IoPortEntry entry
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

    private void renderNorthMarker(MultiBufferSource bufferSource, PoseStack poseStack, PreviewBounds bounds) {
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

    private float previewScale(PreviewBounds bounds) {
        int maxSize = Math.max(1, Math.max(bounds.sizeX(), Math.max(bounds.sizeY(), bounds.sizeZ())));
        return Math.max(4.0F, (Math.min(PREVIEW_WIDTH, PREVIEW_HEIGHT) - 10.0F) / maxSize) * previewZoom;
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        int previewLeft = leftPos + PREVIEW_X;
        int previewTop = topPos + PREVIEW_Y;
        return mouseX >= previewLeft
                && mouseX < previewLeft + PREVIEW_WIDTH
                && mouseY >= previewTop
                && mouseY < previewTop + PREVIEW_HEIGHT;
    }

    private void renderColorSwatches(GuiGraphics graphics) {
        for (int index = 0; index < FACE_ORDER.length; index++) {
            int localX = 126 + (index % 3) * 36;
            int localY = 126 + (index / 3) * 18;
            CompactedMachineFaceColor color = menu.faceColor(FACE_ORDER[index]);
            graphics.fill(localX, localY, localX + 6, localY + 6, 0xFF050705);
            graphics.fill(localX + 1, localY + 1, localX + 5, localY + 5, color.argb());
        }
    }

    private Component colorButtonText(Direction face) {
        return Component.literal(directionLabel(face).substring(0, 1));
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    private static String directionLabel(Direction direction) {
        return Component.translatable("direction.spectralization." + direction.getName()).getString();
    }

    private static String dimensions(int x, int y, int z) {
        return x + "x" + y + "x" + z;
    }

    private static String countAndTypes(int count, int types) {
        return Component.translatable("screen.spectralization.compact_machine_core.count_types", count, types).getString();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record PreviewBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static PreviewBounds of(
                List<CompactedMachineItemData.BlockEntry> blocks,
                List<CompactedMachineItemData.IoPortEntry> ioPorts
        ) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (CompactedMachineItemData.BlockEntry block : blocks) {
                minX = Math.min(minX, block.x());
                minY = Math.min(minY, block.y());
                minZ = Math.min(minZ, block.z());
                maxX = Math.max(maxX, block.x());
                maxY = Math.max(maxY, block.y());
                maxZ = Math.max(maxZ, block.z());
            }

            for (CompactedMachineItemData.IoPortEntry port : ioPorts) {
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
}
