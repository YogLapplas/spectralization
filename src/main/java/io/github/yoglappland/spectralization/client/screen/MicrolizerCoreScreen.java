package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.microlizer.ClientMicrolizerWorkAreaOverlayCache;
import io.github.yoglappland.spectralization.client.microlizer.MicrolizedMachinePreviewRenderer;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineItemData;
import io.github.yoglappland.spectralization.menu.MicrolizerCoreMenu;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class MicrolizerCoreScreen extends AbstractContainerScreen<MicrolizerCoreMenu> {
    private static final int MACHINE_PANEL_X = 16;
    private static final int MACHINE_PANEL_Y = 8;
    private static final int MACHINE_PANEL_WIDTH = 224;
    private static final int MACHINE_PANEL_HEIGHT = 118;
    private static final int MACHINE_INVENTORY_DIVIDER_Y = 128;
    private static final int CONTENT_PANEL_X = 24;
    private static final int CONTENT_PANEL_Y = 31;
    private static final int CONTENT_PANEL_WIDTH = 208;
    private static final int CONTENT_PANEL_HEIGHT = 88;
    private static final int LEFT_DIVIDER_X = 60;
    private static final int RIGHT_DIVIDER_X = 192;
    private static final int FE_BAR_X = 37;
    private static final int FE_BAR_Y = 42;
    private static final int FE_BAR_WIDTH = 11;
    private static final int FE_BAR_HEIGHT = 66;
    private static final int PREVIEW_X = 66;
    private static final int PREVIEW_Y = 39;
    private static final int PREVIEW_WIDTH = 124;
    private static final int PREVIEW_HEIGHT = 74;
    private static final int WORK_AREA_TAG_X = -17;
    private static final int WORK_AREA_TAG_Y = 67;
    private static final int WORK_AREA_TAG_WIDTH = 18;
    private static final int WORK_AREA_TAG_HEIGHT = 18;
    private static final int RIGHT_PANEL_X = 196;
    private static final int RIGHT_PANEL_Y = 39;
    private static final int RIGHT_PANEL_WIDTH = 32;
    private static final int RIGHT_PANEL_HEIGHT = 76;
    private static final int MICROLIZE_BUTTON_X = 197;
    private static final int MICROLIZE_BUTTON_Y = 82;
    private static final int MICROLIZE_BUTTON_WIDTH = 30;
    private static final int MICROLIZE_BUTTON_HEIGHT = 16;
    private static final int REDSTONE_TAG_X = -17;
    private static final int REDSTONE_TAG_Y = 43;
    private static final int REDSTONE_TAG_WIDTH = 18;
    private static final int REDSTONE_TAG_HEIGHT = 18;
    private static final int PREVIEW_SCAN_LIMIT = 4096;
    private static final int REDSTONE_COLOR = 0xFFE65F45;
    private static final int PREVIEW_BG = 0xFFD0C7B9;
    private static final int NO_SIGNAL_BG = 0xFF242424;
    private static final int NO_SIGNAL_TEXT = 0xFF8C8982;

    private float previewYaw = -35.0F;
    private float previewPitch = 28.0F;
    private float previewZoom = 1.0F;
    private boolean draggingPreview;

    public MicrolizerCoreScreen(MicrolizerCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = MicrolizerCoreMenu.IMAGE_WIDTH;
        imageHeight = MicrolizerCoreMenu.IMAGE_HEIGHT;
        inventoryLabelX = MicrolizerCoreMenu.PLAYER_INVENTORY_X;
        inventoryLabelY = 110;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMachineTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawCoreShell(graphics);
        drawPanel(graphics, leftPos + MACHINE_PANEL_X, topPos + MACHINE_PANEL_Y, MACHINE_PANEL_WIDTH, MACHINE_PANEL_HEIGHT);
        drawPanel(graphics, leftPos + CONTENT_PANEL_X, topPos + CONTENT_PANEL_Y, CONTENT_PANEL_WIDTH, CONTENT_PANEL_HEIGHT);
        drawContentDividers(graphics);
        drawRedstoneTag(graphics, mouseX, mouseY);
        drawWorkAreaTag(graphics, mouseX, mouseY);
        drawFeBar(graphics);
        drawInternalPreview(graphics);
        drawOutputAndAction(graphics, mouseX, mouseY);
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCenteredText(graphics, font, title.getString(), MACHINE_PANEL_X + MACHINE_PANEL_WIDTH / 2, MACHINE_PANEL_Y + 9,
                0.82F, ThermalSmelterUiSkin.TEXT);
        drawCenteredTextInRect(graphics, font, label("screen.spectralization.microlizer_core.start_microlizing"),
                MICROLIZE_BUTTON_X, MICROLIZE_BUTTON_Y, MICROLIZE_BUTTON_WIDTH, MICROLIZE_BUTTON_HEIGHT,
                0.64F, canStartMicrolizing() ? ThermalSmelterUiSkin.TEXT : ThermalSmelterUiSkin.TEXT_SUB);
        drawCenteredTextInRect(graphics, font, menu.redstonePulseMode() ? "P" : "R",
                REDSTONE_TAG_X, REDSTONE_TAG_Y, REDSTONE_TAG_WIDTH, REDSTONE_TAG_HEIGHT,
                0.68F, menu.redstonePulseMode() ? REDSTONE_COLOR : ThermalSmelterUiSkin.TEXT_SUB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideLocal(mouseX, mouseY, REDSTONE_TAG_X, REDSTONE_TAG_Y,
                REDSTONE_TAG_WIDTH, REDSTONE_TAG_HEIGHT)) {
            click(MicrolizerCoreMenu.BUTTON_TOGGLE_REDSTONE_MODE);
            return true;
        }

        if (button == 0 && insideLocal(mouseX, mouseY, WORK_AREA_TAG_X, WORK_AREA_TAG_Y,
                WORK_AREA_TAG_WIDTH, WORK_AREA_TAG_HEIGHT)) {
            toggleWorkArea();
            return true;
        }

        if (insidePreview(mouseX, mouseY) && button == 0) {
            draggingPreview = true;
            return true;
        }

        if (insidePreview(mouseX, mouseY) && button == 1) {
            previewYaw = -35.0F;
            previewPitch = 28.0F;
            previewZoom = 1.0F;
            return true;
        }

        if (button == 0 && insideLocal(mouseX, mouseY, MICROLIZE_BUTTON_X, MICROLIZE_BUTTON_Y,
                MICROLIZE_BUTTON_WIDTH, MICROLIZE_BUTTON_HEIGHT)) {
            if (canStartMicrolizing()) {
                click(MicrolizerCoreMenu.BUTTON_START_MICROLIZING);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingPreview && button == 0) {
            previewYaw += (float) dragX * 0.7F;
            previewPitch = MicrolizedMachinePreviewRenderer.clampPitch(previewPitch + (float) dragY * 0.7F);
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
            previewZoom = MicrolizedMachinePreviewRenderer.clampZoom(previewZoom + (float) scrollY * 0.08F);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawRedstoneTag(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + REDSTONE_TAG_X;
        int y = topPos + REDSTONE_TAG_Y;
        boolean hovered = insideLocal(mouseX, mouseY, REDSTONE_TAG_X, REDSTONE_TAG_Y,
                REDSTONE_TAG_WIDTH, REDSTONE_TAG_HEIGHT);
        int color = menu.redstonePulseMode() ? REDSTONE_COLOR : ThermalSmelterUiSkin.EMPTY;
        int fill = menu.redstonePulseMode() ? ThermalSmelterUiSkin.withAlpha(REDSTONE_COLOR, hovered ? 74 : 48)
                : ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, hovered ? 68 : 42);

        graphics.fill(x, y, x + REDSTONE_TAG_WIDTH, y + REDSTONE_TAG_HEIGHT, fill);
        outline(graphics, x, y, REDSTONE_TAG_WIDTH, REDSTONE_TAG_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(color, menu.redstonePulseMode() ? 170 : 96));
        graphics.fill(x + REDSTONE_TAG_WIDTH - 1, y + 2,
                x + REDSTONE_TAG_WIDTH + 2, y + REDSTONE_TAG_HEIGHT - 2,
                ThermalSmelterUiSkin.withAlpha(color, menu.redstonePulseMode() ? 118 : 54));
    }

    private void drawWorkAreaTag(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + WORK_AREA_TAG_X;
        int y = topPos + WORK_AREA_TAG_Y;
        boolean hovered = insideLocal(mouseX, mouseY, WORK_AREA_TAG_X, WORK_AREA_TAG_Y,
                WORK_AREA_TAG_WIDTH, WORK_AREA_TAG_HEIGHT);
        boolean active = menu.hasWorkArea() && workAreaOverlayActive();
        int color = menu.hasWorkArea() ? ThermalSmelterUiSkin.OPTICAL : ThermalSmelterUiSkin.EMPTY;
        int fill = ThermalSmelterUiSkin.withAlpha(
                active ? ThermalSmelterUiSkin.OPTICAL : ThermalSmelterUiSkin.PANEL_SHADOW,
                hovered ? 74 : active ? 52 : 36
        );

        graphics.fill(x, y, x + WORK_AREA_TAG_WIDTH, y + WORK_AREA_TAG_HEIGHT, fill);
        outline(graphics, x, y, WORK_AREA_TAG_WIDTH, WORK_AREA_TAG_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(color, menu.hasWorkArea() ? 150 : 78));
        graphics.fill(x + WORK_AREA_TAG_WIDTH - 1, y + 2,
                x + WORK_AREA_TAG_WIDTH + 2, y + WORK_AREA_TAG_HEIGHT - 2,
                ThermalSmelterUiSkin.withAlpha(color, menu.hasWorkArea() ? 108 : 46));

        int iconColor = ThermalSmelterUiSkin.withAlpha(color, menu.hasWorkArea() ? 172 : 82);
        int left = x + 5;
        int top = y + 5;
        int cell = 3;
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                int cellX = left + column * 5;
                int cellY = top + row * 5;
                graphics.fill(cellX, cellY, cellX + cell, cellY + cell, iconColor);
            }
        }
    }

    private void drawInternalPreview(GuiGraphics graphics) {
        int x = leftPos + PREVIEW_X;
        int y = topPos + PREVIEW_Y;
        if (!menu.valid()) {
            insetPanel(graphics, x, y, PREVIEW_WIDTH, PREVIEW_HEIGHT, NO_SIGNAL_BG);
            drawNoSignalPreview(graphics, x, y);
            return;
        }

        insetPanel(graphics, x, y, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_BG);
        PreviewData preview = previewData();

        if (preview.empty()) {
            drawEmptyPreview(graphics, x, y);
            return;
        }

        MicrolizedMachinePreviewRenderer.render(
                graphics,
                x,
                y,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                preview.blocks(),
                preview.ioPorts(),
                preview.forcedVolume(),
                preview.workAreaVolume(),
                previewYaw,
                previewPitch,
                previewZoom,
                7.0D,
                true
        );
    }

    private void drawFeBar(GuiGraphics graphics) {
        int x = leftPos + FE_BAR_X;
        int y = topPos + FE_BAR_Y;
        insetPanel(graphics, x, y, FE_BAR_WIDTH, FE_BAR_HEIGHT, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_BG, 255));
        graphics.fill(x + 3, y + 4, x + FE_BAR_WIDTH - 3, y + FE_BAR_HEIGHT - 4,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 88));
        graphics.fill(x + 2, y + 2, x + FE_BAR_WIDTH - 2, y + 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 78));
    }

    private void drawEmptyPreview(GuiGraphics graphics, int x, int y) {
        int color = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 120);
        graphics.fill(x + 23, y + 18, x + PREVIEW_WIDTH - 23, y + 20, color);
        graphics.fill(x + PREVIEW_WIDTH / 2 - 1, y + 10, x + PREVIEW_WIDTH / 2 + 1, y + PREVIEW_HEIGHT - 9, color);
    }

    private void drawNoSignalPreview(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + 6, y + PREVIEW_HEIGHT / 2 - 1, x + PREVIEW_WIDTH - 6, y + PREVIEW_HEIGHT / 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 54));
        graphics.fill(x + PREVIEW_WIDTH / 2 - 1, y + 6, x + PREVIEW_WIDTH / 2, y + PREVIEW_HEIGHT - 6,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 38));
        drawCenteredText(
                graphics,
                font,
                label("screen.spectralization.microlizer_core.no_signal"),
                x + PREVIEW_WIDTH / 2,
                y + PREVIEW_HEIGHT / 2 - Math.round(font.lineHeight * 0.78F / 2.0F),
                0.78F,
                NO_SIGNAL_TEXT
        );
    }

    private PreviewData previewData() {
        if (workAreaOverlayActive()) {
            PreviewData workArea = livePreviewData(true);
            if (!workArea.empty()) {
                return workArea;
            }
        }

        PreviewData live = livePreviewData(false);
        if (!live.empty()) {
            return live;
        }

        return outputPreviewData();
    }

    private PreviewData livePreviewData(boolean showWorkArea) {
        Minecraft client = Minecraft.getInstance();
        Level level = client.level;
        BlockPos workMin = menu.workMin();
        BlockPos workMax = menu.workMax();
        if (level == null || !validBox(workMin, workMax) || volume(workMin, workMax) > PREVIEW_SCAN_LIMIT) {
            return PreviewData.EMPTY;
        }

        List<MicrolizedMachineItemData.BlockEntry> blocks = new ArrayList<>();
        blocks.addAll(currentFrameBlocks(level));
        blocks.addAll(currentWorkAreaBlocks(level));
        List<MicrolizedMachineItemData.IoPortEntry> ioPorts = currentIoPorts(level);
        MicrolizedMachinePreviewRenderer.PreviewVolume workAreaVolume = showWorkArea
                ? new MicrolizedMachinePreviewRenderer.PreviewVolume(
                        0,
                        0,
                        0,
                        workMax.getX() - workMin.getX(),
                        workMax.getY() - workMin.getY(),
                        workMax.getZ() - workMin.getZ()
                )
                : null;
        return new PreviewData(List.copyOf(blocks), ioPorts, null, workAreaVolume);
    }

    private PreviewData outputPreviewData() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return PreviewData.EMPTY;
        }

        ItemStack output = menu.getSlot(MicrolizerCoreMenu.OUTPUT_SLOT_INDEX).getItem();
        if (!MicrolizedMachineItemData.hasData(output)) {
            return PreviewData.EMPTY;
        }

        var root = MicrolizedMachineItemData.copyRoot(output);
        return new PreviewData(
                MicrolizedMachineItemData.blockEntries(root, client.level.registryAccess()),
                MicrolizedMachineItemData.ioPortEntries(root, client.level.registryAccess()),
                null,
                null
        );
    }

    private List<MicrolizedMachineItemData.BlockEntry> currentFrameBlocks(Level level) {
        BlockPos frameMin = menu.min();
        BlockPos frameMax = menu.max();
        BlockPos workMin = menu.workMin();
        if (!validBox(frameMin, frameMax) || volume(frameMin, frameMax) > PREVIEW_SCAN_LIMIT) {
            return List.of();
        }

        List<MicrolizedMachineItemData.BlockEntry> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(frameMin, frameMax)) {
            BlockPos immutable = pos.immutable();
            if (!onFrameShell(immutable, frameMin, frameMax)) {
                continue;
            }

            BlockState state = level.getBlockState(immutable);
            if (!state.is(SpectralBlockTags.MICROLIZER)
                    || state.is(Spectralization.MICROLIZER_LIGHT_IO_PORT.get())) {
                continue;
            }

            blocks.add(new MicrolizedMachineItemData.BlockEntry(
                    immutable.getX() - workMin.getX(),
                    immutable.getY() - workMin.getY(),
                    immutable.getZ() - workMin.getZ(),
                    state
            ));
        }

        return List.copyOf(blocks);
    }

    private List<MicrolizedMachineItemData.BlockEntry> currentWorkAreaBlocks(Level level) {
        BlockPos workMin = menu.workMin();
        BlockPos workMax = menu.workMax();
        if (!validBox(workMin, workMax) || volume(workMin, workMax) > PREVIEW_SCAN_LIMIT) {
            return List.of();
        }

        List<MicrolizedMachineItemData.BlockEntry> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(workMin, workMax)) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (state.isAir()) {
                continue;
            }

            blocks.add(new MicrolizedMachineItemData.BlockEntry(
                    immutable.getX() - workMin.getX(),
                    immutable.getY() - workMin.getY(),
                    immutable.getZ() - workMin.getZ(),
                    state
            ));
        }

        return List.copyOf(blocks);
    }

    private List<MicrolizedMachineItemData.IoPortEntry> currentIoPorts(Level level) {
        BlockPos frameMin = menu.min();
        BlockPos frameMax = menu.max();
        BlockPos workMin = menu.workMin();
        if (!validBox(frameMin, frameMax) || volume(frameMin, frameMax) > PREVIEW_SCAN_LIMIT) {
            return List.of();
        }

        List<MicrolizedMachineItemData.IoPortEntry> ports = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(frameMin, frameMax)) {
            BlockPos immutable = pos.immutable();
            BlockState state = level.getBlockState(immutable);
            if (!state.is(Spectralization.MICROLIZER_LIGHT_IO_PORT.get())) {
                continue;
            }

            Direction face = singleSurfaceFace(immutable, frameMin, frameMax);
            if (face == null) {
                continue;
            }

            ports.add(new MicrolizedMachineItemData.IoPortEntry(
                    face,
                    immutable.getX() - workMin.getX(),
                    immutable.getY() - workMin.getY(),
                    immutable.getZ() - workMin.getZ(),
                    state
            ));
        }

        return List.copyOf(ports);
    }

    private static Direction singleSurfaceFace(BlockPos pos, BlockPos frameMin, BlockPos frameMax) {
        Direction face = null;
        int faceCount = 0;

        if (pos.getX() == frameMin.getX()) {
            face = Direction.WEST;
            faceCount++;
        }
        if (pos.getX() == frameMax.getX()) {
            face = Direction.EAST;
            faceCount++;
        }
        if (pos.getY() == frameMin.getY()) {
            face = Direction.DOWN;
            faceCount++;
        }
        if (pos.getY() == frameMax.getY()) {
            face = Direction.UP;
            faceCount++;
        }
        if (pos.getZ() == frameMin.getZ()) {
            face = Direction.NORTH;
            faceCount++;
        }
        if (pos.getZ() == frameMax.getZ()) {
            face = Direction.SOUTH;
            faceCount++;
        }

        return faceCount == 1 ? face : null;
    }

    private static boolean onFrameShell(BlockPos pos, BlockPos frameMin, BlockPos frameMax) {
        return pos.getX() == frameMin.getX()
                || pos.getX() == frameMax.getX()
                || pos.getY() == frameMin.getY()
                || pos.getY() == frameMax.getY()
                || pos.getZ() == frameMin.getZ()
                || pos.getZ() == frameMax.getZ();
    }

    private static boolean validBox(BlockPos min, BlockPos max) {
        return max.getX() >= min.getX()
                && max.getY() >= min.getY()
                && max.getZ() >= min.getZ();
    }

    private static long volume(BlockPos min, BlockPos max) {
        if (!validBox(min, max)) {
            return 0L;
        }

        return (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
    }

    private void drawOutputAndAction(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(
                leftPos + RIGHT_PANEL_X,
                topPos + RIGHT_PANEL_Y,
                leftPos + RIGHT_PANEL_X + RIGHT_PANEL_WIDTH,
                topPos + RIGHT_PANEL_Y + RIGHT_PANEL_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 28)
        );
        slotFrame(
                graphics,
                leftPos + MicrolizerCoreMenu.OUTPUT_SLOT_FRAME_X,
                topPos + MicrolizerCoreMenu.OUTPUT_SLOT_FRAME_Y,
                ThermalSmelterUiSkin.PROGRESS,
                !menu.outputEmpty()
        );
        drawActionButton(
                graphics,
                MICROLIZE_BUTTON_X,
                MICROLIZE_BUTTON_Y,
                MICROLIZE_BUTTON_WIDTH,
                MICROLIZE_BUTTON_HEIGHT,
                canStartMicrolizing(),
                insideLocal(mouseX, mouseY, MICROLIZE_BUTTON_X, MICROLIZE_BUTTON_Y,
                        MICROLIZE_BUTTON_WIDTH, MICROLIZE_BUTTON_HEIGHT)
        );

    }

    private void drawCoreShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + MACHINE_INVENTORY_DIVIDER_Y,
                ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 170));
        graphics.fill(leftPos + 2, topPos + 2, leftPos + 3, topPos + imageHeight - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 170));
        graphics.fill(leftPos + 8, topPos + MACHINE_INVENTORY_DIVIDER_Y,
                leftPos + imageWidth - 8, topPos + MACHINE_INVENTORY_DIVIDER_Y + 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 128));
        graphics.fill(leftPos + 8, topPos + MACHINE_INVENTORY_DIVIDER_Y + 1,
                leftPos + imageWidth - 8, topPos + MACHINE_INVENTORY_DIVIDER_Y + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 96));
    }

    private void drawContentDividers(GuiGraphics graphics) {
        int top = topPos + CONTENT_PANEL_Y + 7;
        int bottom = topPos + CONTENT_PANEL_Y + CONTENT_PANEL_HEIGHT - 7;
        thinVertical(graphics, leftPos + LEFT_DIVIDER_X, top, bottom);
        thinVertical(graphics, leftPos + RIGHT_DIVIDER_X, top, bottom);
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        int panelX = leftPos + MicrolizerCoreMenu.PLAYER_INVENTORY_X - 5;
        int panelY = topPos + MicrolizerCoreMenu.PLAYER_INVENTORY_Y - 8;
        drawPanel(graphics, panelX, panelY, 172, 90);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        leftPos + MicrolizerCoreMenu.PLAYER_INVENTORY_X + column * MicrolizerCoreMenu.SLOT_SIZE,
                        topPos + MicrolizerCoreMenu.PLAYER_INVENTORY_Y + row * MicrolizerCoreMenu.SLOT_SIZE
                );
            }
        }

        int hotbarY = MicrolizerCoreMenu.PLAYER_INVENTORY_Y + 58;
        graphics.fill(
                leftPos + MicrolizerCoreMenu.PLAYER_INVENTORY_X,
                topPos + hotbarY - 4,
                leftPos + MicrolizerCoreMenu.PLAYER_INVENTORY_X + 9 * MicrolizerCoreMenu.SLOT_SIZE,
                topPos + hotbarY - 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 88)
        );
        for (int column = 0; column < 9; column++) {
            ceramicSlot(
                    graphics,
                    leftPos + MicrolizerCoreMenu.PLAYER_INVENTORY_X + column * MicrolizerCoreMenu.SLOT_SIZE,
                    topPos + hotbarY
            );
        }
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int width, int height, boolean active, boolean hovered) {
        int left = leftPos + x;
        int top = topPos + y;
        int border = active ? ThermalSmelterUiSkin.STRONG_BORDER : ThermalSmelterUiSkin.EMPTY;
        int fill = hovered && active ? ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 112)
                : ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, active ? 58 : 28);
        graphics.fill(left, top, left + width, top + height, fill);
        outline(graphics, left, top, width, height, ThermalSmelterUiSkin.withAlpha(border, active ? 162 : 78));
        graphics.fill(left + 1, top + 1, left + width - 1, top + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, active ? 92 : 32));
        graphics.fill(left + 1, top + height - 2, left + width - 1, top + height - 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, active ? 108 : 48));
    }

    private void toggleWorkArea() {
        if (!menu.hasWorkArea()) {
            return;
        }

        ClientMicrolizerWorkAreaOverlayCache.toggle(menu.corePos(), menu.workMin(), menu.workMax());
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void renderMachineTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            return;
        }

        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        if (insideLocal(mouseX, mouseY, REDSTONE_TAG_X, REDSTONE_TAG_Y, REDSTONE_TAG_WIDTH, REDSTONE_TAG_HEIGHT)) {
            return List.of(Component.translatable(menu.redstonePulseMode()
                    ? "screen.spectralization.microlizer_core.redstone_pulse"
                    : "screen.spectralization.microlizer_core.redstone_ignored"));
        }

        if (insideLocal(mouseX, mouseY, WORK_AREA_TAG_X, WORK_AREA_TAG_Y,
                WORK_AREA_TAG_WIDTH, WORK_AREA_TAG_HEIGHT)) {
            return List.of(Component.translatable("screen.spectralization.microlizer_core.show_work_area"));
        }

        if (insideLocal(mouseX, mouseY, FE_BAR_X, FE_BAR_Y, FE_BAR_WIDTH, FE_BAR_HEIGHT)) {
            return List.of(Component.translatable("screen.spectralization.microlizer_core.fe_zero"));
        }

        if (insideLocal(mouseX, mouseY, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT)) {
            return List.of(
                    statusText(),
                    Component.translatable("screen.spectralization.microlizer_core.work_area")
                            .append(": ")
                            .append(dimensions(menu.workSizeX(), menu.workSizeY(), menu.workSizeZ())),
                    Component.translatable("screen.spectralization.microlizer_core.payload")
                            .append(": ")
                            .append(countAndTypes(menu.payloadBlocks(), menu.payloadTypes())),
                    Component.translatable("screen.spectralization.microlizer_core.io_ports")
                            .append(": ")
                            .append(menu.ioPorts() + "/6")
            );
        }

        if (insideLocal(mouseX, mouseY, MICROLIZE_BUTTON_X, MICROLIZE_BUTTON_Y,
                MICROLIZE_BUTTON_WIDTH, MICROLIZE_BUTTON_HEIGHT)) {
            return List.of(Component.translatable("screen.spectralization.microlizer_core.start_microlizing"));
        }

        return List.of();
    }

    private boolean canStartMicrolizing() {
        return menu.valid() && menu.outputEmpty() && !menu.microlizing();
    }

    private boolean insideLocal(double mouseX, double mouseY, int x, int y, int width, int height) {
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        return insideLocal(mouseX, mouseY, PREVIEW_X, PREVIEW_Y, PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    private boolean workAreaOverlayActive() {
        return ClientMicrolizerWorkAreaOverlayCache.activeWorkArea()
                .map(area -> area.corePos().equals(menu.corePos()))
                .orElse(false);
    }

    private Component statusText() {
        if (!menu.present()) {
            return Component.translatable("screen.spectralization.microlizer_core.status_missing");
        }

        if (!menu.valid()) {
            return Component.translatable("screen.spectralization.microlizer_core.status_error");
        }

        return Component.translatable("screen.spectralization.microlizer_core.status_ready");
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    private static String dimensions(int x, int y, int z) {
        return x + "x" + y + "x" + z;
    }

    private static String countAndTypes(int count, int types) {
        return Component.translatable("screen.spectralization.microlizer_core.count_types", count, types).getString();
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.PANEL);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, int color, boolean active) {
        ceramicSlot(graphics, x, y);
        if (active) {
            graphics.fill(x + MicrolizerCoreMenu.SLOT_SIZE - 2, y + 2,
                    x + MicrolizerCoreMenu.SLOT_SIZE - 1, y + MicrolizerCoreMenu.SLOT_SIZE - 2,
                    ThermalSmelterUiSkin.withAlpha(color, 155));
            graphics.fill(x + 2, y + MicrolizerCoreMenu.SLOT_SIZE - 2,
                    x + MicrolizerCoreMenu.SLOT_SIZE - 2, y + MicrolizerCoreMenu.SLOT_SIZE - 1,
                    ThermalSmelterUiSkin.withAlpha(color, 90));
        }
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + MicrolizerCoreMenu.SLOT_SIZE, y + MicrolizerCoreMenu.SLOT_SIZE,
                ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, MicrolizerCoreMenu.SLOT_SIZE, MicrolizerCoreMenu.SLOT_SIZE,
                ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + MicrolizerCoreMenu.SLOT_SIZE - 1, y + 2,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + MicrolizerCoreMenu.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + MicrolizerCoreMenu.SLOT_SIZE - 2,
                x + MicrolizerCoreMenu.SLOT_SIZE - 1, y + MicrolizerCoreMenu.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + MicrolizerCoreMenu.SLOT_SIZE - 2, y + 1,
                x + MicrolizerCoreMenu.SLOT_SIZE - 1, y + MicrolizerCoreMenu.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + MicrolizerCoreMenu.SLOT_SIZE - 2,
                y + MicrolizerCoreMenu.SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
    }

    private static void drawCenteredText(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        int textWidth = font.width(text);
        int x = Math.round(centerX - textWidth * scale / 2.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawCenteredTextInRect(
            GuiGraphics graphics,
            Font font,
            String text,
            int x,
            int y,
            int width,
            int height,
            float scale,
            int color
    ) {
        int textWidth = font.width(text);
        int textX = Math.round(x + (width - textWidth * scale) / 2.0F);
        int textY = Math.round(y + (height - font.lineHeight * scale) / 2.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void thinVertical(GuiGraphics graphics, int x, int y1, int y2) {
        graphics.fill(x, y1, x + 1, y2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 128));
        graphics.fill(x + 1, y1, x + 2, y2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 54));
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private record PreviewData(
            List<MicrolizedMachineItemData.BlockEntry> blocks,
            List<MicrolizedMachineItemData.IoPortEntry> ioPorts,
            MicrolizedMachinePreviewRenderer.PreviewVolume forcedVolume,
            MicrolizedMachinePreviewRenderer.PreviewVolume workAreaVolume
    ) {
        private static final PreviewData EMPTY = new PreviewData(List.of(), List.of(), null, null);

        private boolean empty() {
            return blocks.isEmpty() && ioPorts.isEmpty() && forcedVolume == null && workAreaVolume == null;
        }
    }
}
