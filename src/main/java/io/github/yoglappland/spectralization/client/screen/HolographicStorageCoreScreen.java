package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.menu.HolographicStorageCoreMenu;
import io.github.yoglappland.spectralization.storage.HolographicStorageBlockEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class HolographicStorageCoreScreen extends SpectralMachineScreen<HolographicStorageCoreMenu> {
    private static final int SCREEN_WIDTH = 256;
    private static final int SCREEN_HEIGHT = 170;
    private static final int CORE_PANEL_X = 16;
    private static final int CORE_PANEL_Y = 18;
    private static final int CORE_PANEL_WIDTH = 224;
    private static final int CORE_PANEL_HEIGHT = 134;
    private static final int GAUGE_PANEL_X = 26;
    private static final int GAUGE_PANEL_Y = 30;
    private static final int GAUGE_PANEL_WIDTH = 70;
    private static final int GAUGE_PANEL_HEIGHT = 110;
    private static final int STATUS_PANEL_X = 106;
    private static final int STATUS_PANEL_Y = 30;
    private static final int STATUS_PANEL_WIDTH = 122;
    private static final int STATUS_PANEL_HEIGHT = 110;
    private static final int LOCKED_COLOR = 0xFF7A1E2B;
    private static final int TYPE_ACCENT = 0xFF78DCCB;
    private static final int ITEM_ACCENT = 0xFFFFB35C;
    private static final int CHANNEL_ACCENT = 0xFFC49BFF;

    public HolographicStorageCoreScreen(HolographicStorageCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "holographic_storage_core", SCREEN_WIDTH, SCREEN_HEIGHT, 0, 160);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCoreTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        drawCeramicPanel(graphics, leftPos + CORE_PANEL_X, topPos + CORE_PANEL_Y, CORE_PANEL_WIDTH, CORE_PANEL_HEIGHT);
        subtleRegion(graphics, leftPos + GAUGE_PANEL_X, topPos + GAUGE_PANEL_Y,
                GAUGE_PANEL_WIDTH, GAUGE_PANEL_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_BG, 146));
        subtleRegion(graphics, leftPos + STATUS_PANEL_X, topPos + STATUS_PANEL_Y,
                STATUS_PANEL_WIDTH, STATUS_PANEL_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 26));
        drawGauges(graphics);
        drawStatusPanel(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawGauges(GuiGraphics graphics) {
        int top = topPos + GAUGE_PANEL_Y + 18;
        drawVerticalGauge(graphics, "T", leftPos + GAUGE_PANEL_X + 13, top,
                menu.storedTypes(), menu.maxTypes(), TYPE_ACCENT);
        drawVerticalGauge(graphics, "I", leftPos + GAUGE_PANEL_X + 31, top,
                menu.storedItems(), menu.maxItems(), ITEM_ACCENT);
        drawChannelGauge(graphics, leftPos + GAUGE_PANEL_X + 49, top);
        drawStatusStrip(graphics, leftPos + GAUGE_PANEL_X + 12, topPos + GAUGE_PANEL_Y + 92);
    }

    private void drawVerticalGauge(
            GuiGraphics graphics,
            String label,
            int x,
            int y,
            long value,
            long max,
            int color
    ) {
        int height = 58;
        int fill = max <= 0 ? 0 : Math.round((height - 4) * Math.max(0L, Math.min(value, max)) / (float) max);
        drawCenteredText(graphics, font, label, x + 5, y - 11, 0.48F, ThermalSmelterUiSkin.TEXT_SUB);
        insetPanel(graphics, x, y, 10, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 90));
        if (fill > 0) {
            int fillTop = y + height - 2 - fill;
            graphics.fill(x + 2, fillTop, x + 8, y + height - 2, ThermalSmelterUiSkin.withAlpha(color, 190));
            graphics.fill(x + 2, fillTop, x + 7, Math.min(y + height - 2, fillTop + 2),
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 62));
        }
    }

    private void drawChannelGauge(GuiGraphics graphics, int x, int y) {
        drawCenteredText(graphics, font, "C", x + 5, y - 11, 0.48F, ThermalSmelterUiSkin.TEXT_SUB);
        int active = Math.max(1, Math.min(4, menu.channelMultiplier()));
        for (int index = 0; index < 4; index++) {
            int cellY = y + 42 - index * 14;
            int color = index < active ? CHANNEL_ACCENT : ThermalSmelterUiSkin.EMPTY;
            graphics.fill(x + 1, cellY, x + 9, cellY + 8,
                    ThermalSmelterUiSkin.withAlpha(color, index < active ? 172 : 58));
            outline(graphics, x + 1, cellY, 8, 8,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 116));
            if (index < active) {
                graphics.fill(x + 2, cellY + 1, x + 8, cellY + 2,
                        ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 76));
            }
        }
    }

    private void drawStatusStrip(GuiGraphics graphics, int x, int y) {
        int color = statusColor();
        graphics.fill(x, y, x + 46, y + 5, ThermalSmelterUiSkin.withAlpha(color, 140));
        outline(graphics, x, y, 46, 5, ThermalSmelterUiSkin.withAlpha(color, 205));
    }

    private void drawStatusPanel(GuiGraphics graphics) {
        int panelX = leftPos + STATUS_PANEL_X;
        int panelY = topPos + STATUS_PANEL_Y;
        int centerX = panelX + STATUS_PANEL_WIDTH / 2;
        drawStatusHeader(graphics, panelX + 10, panelY + 10, STATUS_PANEL_WIDTH - 20);
        drawStructureChips(graphics, panelX + 12, panelY + 34);
        drawBlockEntries(graphics, panelX + 12, panelY + 71);
        drawCenteredText(graphics, font, "CORE", centerX, panelY + STATUS_PANEL_HEIGHT - 15, 0.52F,
                ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawStatusHeader(GuiGraphics graphics, int x, int y, int width) {
        int color = statusColor();
        insetPanel(graphics, x, y, width, 14, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_BG, 116));
        graphics.fill(x + 2, y + 2, x + width - 2, y + 5, ThermalSmelterUiSkin.withAlpha(color, 74));
        drawCenteredText(graphics, font, statusCode(), x + width / 2, y + 4, 0.48F, color);
    }

    private void drawStructureChips(GuiGraphics graphics, int x, int y) {
        drawMetricChip(graphics, "S", Integer.toString(menu.crystals()), x, y, 31, TYPE_ACCENT);
        drawMetricChip(graphics, "F", Integer.toString(menu.exposedFaces()), x + 37, y, 31, ITEM_ACCENT);
        drawMetricChip(graphics, "x", Integer.toString(menu.channelMultiplier()), x + 74, y, 31, CHANNEL_ACCENT);
        graphics.fill(x + 1, y + 24, x + 104, y + 25,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 92));
    }

    private void drawMetricChip(GuiGraphics graphics, String label, String value, int x, int y, int width, int color) {
        insetPanel(graphics, x, y, width, 18, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_BG, 122));
        drawText(graphics, font, label, x + 4, y + 5, 0.44F, ThermalSmelterUiSkin.TEXT_SUB);
        drawRightText(graphics, font, value, x + width - 4, y + 5, 0.50F, color);
    }

    private void drawBlockEntries(GuiGraphics graphics, int x, int y) {
        List<HolographicStorageBlockEntry> entries = menu.blockEntries();
        int visibleEntries = Math.min(4, entries.size());
        if (visibleEntries == 0) {
            drawCenteredText(graphics, font, "-", x + 52, y + 15, 0.62F, ThermalSmelterUiSkin.TEXT_SUB);
            return;
        }

        for (int index = 0; index < visibleEntries; index++) {
            HolographicStorageBlockEntry entry = entries.get(index);
            int rowY = y + index * 9;
            graphics.fill(x, rowY + 1, x + 104, rowY + 7,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 24));
            drawText(graphics, font, shortBlockName(entry.descriptionId()), x + 3, rowY, 0.42F,
                    ThermalSmelterUiSkin.TEXT_SUB);
            drawRightText(graphics, font, formatCount(entry.count()), x + 101, rowY, 0.42F,
                    ThermalSmelterUiSkin.TEXT);
        }
    }

    private void renderCoreTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        if (inside(localX, localY, GAUGE_PANEL_X + 6, GAUGE_PANEL_Y + 8, 58, 78)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(label("screen.spectralization.holographic_storage.types")
                    + ": " + menu.storedTypes() + "/" + menu.maxTypes()));
            tooltip.add(Component.literal(label("screen.spectralization.holographic_storage.items")
                    + ": " + formatCount(menu.storedItems()) + "/" + formatCount(menu.maxItems())));
            tooltip.add(Component.literal(label("screen.spectralization.holographic_storage.channels")
                    + ": x" + menu.channelMultiplier()));
            return tooltip;
        }

        if (inside(localX, localY, STATUS_PANEL_X + 8, STATUS_PANEL_Y + 8, STATUS_PANEL_WIDTH - 16, 18)) {
            return List.of(statusText());
        }

        if (inside(localX, localY, STATUS_PANEL_X + 10, STATUS_PANEL_Y + 64, STATUS_PANEL_WIDTH - 20, 42)) {
            List<HolographicStorageBlockEntry> entries = menu.blockEntries();
            if (entries.isEmpty()) {
                return List.of(Component.translatable("screen.spectralization.holographic_storage.blocks"));
            }

            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("screen.spectralization.holographic_storage.blocks"));
            for (HolographicStorageBlockEntry entry : entries) {
                tooltip.add(Component.literal(Component.translatable(entry.descriptionId()).getString()
                        + ": " + entry.count()));
            }
            return tooltip;
        }

        return List.of();
    }

    private Component statusText() {
        if (menu.structureError()) {
            return Component.translatable("screen.spectralization.holographic_storage.status_error");
        }

        if (menu.overCapacity()) {
            return Component.translatable("screen.spectralization.holographic_storage.status_over_capacity");
        }

        return Component.translatable("screen.spectralization.holographic_storage.status_normal");
    }

    private String statusCode() {
        if (menu.structureError()) {
            return "ERR";
        }

        if (menu.overCapacity()) {
            return "FULL";
        }

        if (menu.interactionLocked()) {
            return "LOCK";
        }

        return "OK";
    }

    private int statusColor() {
        if (menu.structureError() || menu.overCapacity() || menu.interactionLocked()) {
            return LOCKED_COLOR;
        }

        return ThermalSmelterUiSkin.STATUS_READY;
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 112, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 12, topPos + 111, leftPos + imageWidth - 12, topPos + 112,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 96));
    }

    private static void drawCeramicPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.PANEL);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 4, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 4, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 4, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 4, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 4, y + height - 5, x + width - 4, y + height - 4,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 36));
    }

    private static void subtleRegion(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 96));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 42));
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 56));
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
    }

    private static void drawCenteredText(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        int textWidth = font.width(text);
        int x = Math.round(centerX - textWidth * scale / 2.0F);
        drawText(graphics, font, text, x, y, scale, color);
    }

    private static void drawRightText(GuiGraphics graphics, Font font, String text, int rightX, int y, float scale, int color) {
        int textWidth = font.width(text);
        int x = Math.round(rightX - textWidth * scale);
        drawText(graphics, font, text, x, y, scale, color);
    }

    private static void drawText(GuiGraphics graphics, Font font, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static boolean inside(int localX, int localY, int x, int y, int width, int height) {
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static String shortBlockName(String descriptionId) {
        String text = Component.translatable(descriptionId).getString();
        return text.length() <= 16 ? text : text.substring(0, 15) + ".";
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    private static String formatCount(long count) {
        if (count < 1000) {
            return Long.toString(count);
        }

        if (count < 1_000_000) {
            return count / 1000 + "k";
        }

        return count / 1_000_000 + "m";
    }
}
