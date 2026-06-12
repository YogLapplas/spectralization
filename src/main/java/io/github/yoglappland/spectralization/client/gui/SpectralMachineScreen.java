package io.github.yoglappland.spectralization.client.gui;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class SpectralMachineScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private final List<DebugBox> debugBoxes = new ArrayList<>();
    private final String layoutKey;
    private DragState dragState;

    protected SpectralMachineScreen(
            T menu,
            Inventory playerInventory,
            Component title,
            String layoutKey,
            int imageWidth,
            int imageHeight,
            int inventoryLabelX,
            int inventoryLabelY
    ) {
        super(menu, playerInventory, title);
        this.layoutKey = layoutKey;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.inventoryLabelX = inventoryLabelX;
        this.inventoryLabelY = inventoryLabelY;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        debugBoxes.clear();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderDebugOverlay(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (SpectralizationConfig.uiDebug() && button == 0) {
            DebugBox box = debugBoxAt(mouseX, mouseY);

            if (box != null && isEditable(box)) {
                int localX = (int) Math.round(mouseX - leftPos);
                int localY = (int) Math.round(mouseY - topPos);
                boolean resizing = localX >= box.x() + box.width() - 6 && localY >= box.y() + box.height() - 6;
                dragState = new DragState(
                        box,
                        resizing,
                        localX - box.x(),
                        localY - box.y(),
                        box.width(),
                        box.height()
                );
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragState != null && button == 0) {
            int localX = snap((int) Math.round(mouseX - leftPos));
            int localY = snap((int) Math.round(mouseY - topPos));
            DebugBox box = dragState.box();
            int x = box.x();
            int y = box.y();
            int width = box.width();
            int height = box.height();

            if (dragState.resizing()) {
                width = clamp(localX - box.x(), 4, imageWidth - box.x());
                height = clamp(localY - box.y(), 4, imageHeight - box.y());
            } else {
                x = clamp(localX - dragState.offsetX(), 0, imageWidth - box.width());
                y = clamp(localY - dragState.offsetY(), 0, imageHeight - box.height());
            }

            SpectralUiLayout.updateRect(layoutKey, box.id(), box.type(), box.label(), x, y, width, height, imageWidth, imageHeight, false);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragState != null && button == 0) {
            SpectralUiLayout.save(layoutKey);
            dragState = null;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected void drawMachineBackground(GuiGraphics graphics) {
        SpectralGui.drawScreen(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    protected void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        panel(graphics, "panel", x, y, width, height);
    }

    protected void panel(GuiGraphics graphics, String name, int x, int y, int width, int height) {
        SpectralUiRect rect = layout(name, "panel", name, x, y, width, height);
        SpectralGui.drawPanel(graphics, leftPos + rect.x(), topPos + rect.y(), rect.width(), rect.height());
        addDebugBox(name, "panel", name, rect);
    }

    protected void slot(GuiGraphics graphics, int x, int y, SpectralSlotKind kind) {
        slot(graphics, kind.name().toLowerCase() + "_slot", x, y, kind);
    }

    protected void slot(GuiGraphics graphics, String name, int x, int y, SpectralSlotKind kind) {
        SpectralUiRect rect = layout(name, "slot", name, x - 1, y - 1, SpectralGui.SLOT_SIZE, SpectralGui.SLOT_SIZE);
        SpectralGui.drawSlot(graphics, leftPos + rect.x() + 1, topPos + rect.y() + 1, kind);
        addDebugBox(name, "slot", name, rect);
    }

    protected void playerInventorySlots(GuiGraphics graphics, int x, int y) {
        SpectralUiRect rect = layout("player_inventory", "custom", "player_inventory", x - 1, y - 1, SpectralGui.SLOT_SIZE * 9, 76);
        SpectralGui.drawPlayerInventorySlots(graphics, leftPos + rect.x() + 1, topPos + rect.y() + 1);
        addDebugBox("player_inventory", "custom", "player_inventory", rect);
    }

    protected void horizontalBar(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            double value,
            double max,
            SpectralBarKind kind
    ) {
        horizontalBar(graphics, kind.name().toLowerCase() + "_bar", x, y, width, height, value, max, kind);
    }

    protected void horizontalBar(
            GuiGraphics graphics,
            String name,
            int x,
            int y,
            int width,
            int height,
            double value,
            double max,
            SpectralBarKind kind
    ) {
        SpectralUiRect rect = layout(name, "readout", name, x, y, width, height);
        SpectralGui.drawHorizontalBar(graphics, leftPos + rect.x(), topPos + rect.y(), rect.width(), rect.height(), value, max, kind);
        addDebugBox(name, "readout", name, rect);
    }

    protected void verticalBar(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            double value,
            double max,
            SpectralBarKind kind
    ) {
        verticalBar(graphics, kind.name().toLowerCase() + "_bar", x, y, width, height, value, max, kind);
    }

    protected void verticalBar(
            GuiGraphics graphics,
            String name,
            int x,
            int y,
            int width,
            int height,
            double value,
            double max,
            SpectralBarKind kind
    ) {
        SpectralUiRect rect = layout(name, "readout", name, x, y, width, height);
        SpectralGui.drawVerticalBar(graphics, leftPos + rect.x(), topPos + rect.y(), rect.width(), rect.height(), value, max, kind);
        addDebugBox(name, "readout", name, rect);
    }

    protected void title(GuiGraphics graphics) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, SpectralGuiTheme.TEXT_TITLE, false);
    }

    protected void text(GuiGraphics graphics, String text, int x, int y) {
        graphics.drawString(font, text, x, y, SpectralGuiTheme.TEXT_PRIMARY, false);
    }

    protected void text(GuiGraphics graphics, String name, String text, int x, int y, int width) {
        SpectralUiRect rect = layout(name, "label", name, x, y, width, 9);
        if (!isHiddenTextRect(rect)) {
            graphics.drawString(font, text, rect.x(), rect.y(), SpectralGuiTheme.TEXT_PRIMARY, false);
        }
        addDebugBox(name, "label", name, rect);
    }

    protected void mutedText(GuiGraphics graphics, String text, int x, int y) {
        graphics.drawString(font, text, x, y, SpectralGuiTheme.TEXT_MUTED, false);
    }

    protected void mutedText(GuiGraphics graphics, String name, String text, int x, int y, int width) {
        SpectralUiRect rect = layout(name, "label", name, x, y, width, 9);
        if (!isHiddenTextRect(rect)) {
            graphics.drawString(font, text, rect.x(), rect.y(), SpectralGuiTheme.TEXT_MUTED, false);
        }
        addDebugBox(name, "label", name, rect);
    }

    private SpectralUiRect layout(String id, String type, String label, int x, int y, int width, int height) {
        return SpectralUiLayout.rect(layoutKey, id, type, label, x, y, width, height, imageWidth, imageHeight);
    }

    private void addDebugBox(String id, String type, String label, SpectralUiRect rect) {
        if (SpectralizationConfig.uiDebug()) {
            debugBoxes.add(new DebugBox(id, type, label, rect.x(), rect.y(), rect.width(), rect.height()));
        }
    }

    private void renderDebugOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!SpectralizationConfig.uiDebug()) {
            return;
        }

        for (DebugBox box : debugBoxes) {
            drawDebugBox(graphics, box);
        }

        int relativeMouseX = mouseX - leftPos;
        int relativeMouseY = mouseY - topPos;
        String label = "ui " + relativeMouseX + ", " + relativeMouseY;
        int labelWidth = font.width(label) + 6;
        int labelLeft = Math.min(mouseX + 8, leftPos + imageWidth - labelWidth);
        int labelTop = Math.min(mouseY + 8, topPos + imageHeight - 12);
        graphics.fill(labelLeft, labelTop, labelLeft + labelWidth, labelTop + 11, 0xDD050705);
        graphics.drawString(font, label, labelLeft + 3, labelTop + 2, 0xFFE8FFE2, false);
    }

    private DebugBox debugBoxAt(double mouseX, double mouseY) {
        int localX = (int) Math.round(mouseX - leftPos);
        int localY = (int) Math.round(mouseY - topPos);

        for (int i = debugBoxes.size() - 1; i >= 0; i--) {
            DebugBox box = debugBoxes.get(i);

            if (localX >= box.x()
                    && localX < box.x() + box.width()
                    && localY >= box.y()
                    && localY < box.y() + box.height()) {
                return box;
            }
        }

        return null;
    }

    private int snap(int value) {
        int grid = hasShiftDown() ? 8 : 1;
        return Math.round((float) value / grid) * grid;
    }

    private static boolean isEditable(DebugBox box) {
        return !"slot".equals(box.type()) && !"player_inventory".equals(box.id());
    }

    private boolean isHiddenTextRect(SpectralUiRect rect) {
        return rect.width() <= 4
                || rect.height() <= 4
                || (rect.x() <= 8 && rect.y() + rect.height() >= imageHeight - 8);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void drawDebugBox(GuiGraphics graphics, DebugBox box) {
        int left = leftPos + box.x();
        int top = topPos + box.y();
        int right = left + box.width();
        int bottom = top + box.height();
        int color = isEditable(box) ? 0xFFFF3355 : 0xFF8A98A0;
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);

        if (SpectralizationConfig.uiDebugLabels()) {
            String label = box.id() + " " + box.x() + "," + box.y() + " " + box.width() + "x" + box.height();
            int labelWidth = font.width(label) + 4;
            int labelTop = Math.max(top - 10, topPos);
            graphics.fill(left, labelTop, left + labelWidth, labelTop + 9, 0xCC050705);
            graphics.drawString(font, label, left + 2, labelTop + 1, 0xFFFFD7DE, false);
        }

        if (isEditable(box)) {
            graphics.fill(right - 5, bottom - 5, right - 1, bottom - 1, 0xAAFFE775);
        }
    }

    private record DebugBox(String id, String type, String label, int x, int y, int width, int height) {
    }

    private record DragState(DebugBox box, boolean resizing, int offsetX, int offsetY, int originalWidth, int originalHeight) {
    }
}
