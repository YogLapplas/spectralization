package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.client.microlizer.MicrolizedMachinePreviewRenderer;
import io.github.yoglappland.spectralization.client.gui.SpectralGui;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineFaceColor;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineItemData;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.menu.MicrolizedMachineMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MicrolizedMachineScreen extends SpectralMachineScreen<MicrolizedMachineMenu> {
    private static final int PREVIEW_X = 26;
    private static final int PREVIEW_Y = 46;
    private static final int PREVIEW_WIDTH = 72;
    private static final int PREVIEW_HEIGHT = 86;
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

    public MicrolizedMachineScreen(MicrolizedMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "microlized_machine", 256, 170, 0, 160);
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> click(MicrolizedMachineMenu.BUTTON_ROTATE_COUNTERCLOCKWISE)
                )
                .bounds(leftPos + 136, topPos + 84, 22, 18)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> click(MicrolizedMachineMenu.BUTTON_ROTATE_CLOCKWISE)
                )
                .bounds(leftPos + 162, topPos + 84, 22, 18)
                .build());

        for (int index = 0; index < FACE_ORDER.length; index++) {
            Direction face = FACE_ORDER[index];
            Button button = Button.builder(
                            colorButtonText(face),
                            clicked -> click(MicrolizedMachineMenu.BUTTON_FACE_COLOR_BASE + face.ordinal())
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

        text(graphics, "size_text", label("screen.spectralization.microlized_machine.size")
                + ": " + dimensions(menu.sizeX(), menu.sizeY(), menu.sizeZ()), 124, 34, 104);
        text(graphics, "blocks_text", label("screen.spectralization.microlized_machine.blocks")
                + ": " + countAndTypes(menu.blockCount(), menu.typeCount()), 124, 48, 104);
        text(graphics, "io_text", label("screen.spectralization.microlized_machine.io")
                + ": " + menu.ioFaceCount(), 124, 62, 104);
        text(graphics, "mapping_text", label("screen.spectralization.microlized_machine.mapping")
                + ": " + menu.transferCount() + "/" + menu.sourceCount(), 124, 76, 104);
        text(graphics, "facing_text", label("screen.spectralization.microlized_machine.facing")
                + ": " + directionLabel(menu.facing()), 190, 88, 38);

        mutedText(graphics, "rotation_title", label("screen.spectralization.microlized_machine.rotation"), 124, 88, 44);
        mutedText(graphics, "face_colors_title", label("screen.spectralization.microlized_machine.face_colors"), 124, 110, 98);
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

    private void renderSnapshotPreview(GuiGraphics graphics) {
        int previewLeft = leftPos + PREVIEW_X;
        int previewTop = topPos + PREVIEW_Y;
        SpectralGui.drawInset(graphics, previewLeft, previewTop, PREVIEW_WIDTH, PREVIEW_HEIGHT);

        List<MicrolizedMachineItemData.BlockEntry> blocks = menu.snapshotBlocks();
        List<MicrolizedMachineItemData.IoPortEntry> ioPorts = menu.snapshotIoPorts();
        if (blocks.isEmpty() && ioPorts.isEmpty()) {
            return;
        }
        MicrolizedMachinePreviewRenderer.render(
                graphics,
                previewLeft,
                previewTop,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                blocks,
                ioPorts,
                previewYaw,
                previewPitch,
                previewZoom,
                10.0D,
                true
        );
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
            MicrolizedMachineFaceColor color = menu.faceColor(FACE_ORDER[index]);
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
        return Component.translatable("screen.spectralization.microlizer_core.count_types", count, types).getString();
    }

}
