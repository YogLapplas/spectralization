package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.menu.HolographicStorageMenu;
import io.github.yoglappland.spectralization.network.HolographicStorageActionPayload;
import io.github.yoglappland.spectralization.storage.HolographicStorageEntry;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class HolographicStorageScreen extends AbstractContainerScreen<HolographicStorageMenu> {
    private static final int GRID_LEFT = 24;
    private static final int GRID_TOP = 24;
    private static final int GRID_COLUMNS = 10;
    private static final int GRID_ROWS = 4;
    private static final int GRID_SLOT_SIZE = 18;
    private static final int STORAGE_PANEL_LEFT = 18;
    private static final int STORAGE_PANEL_TOP = 12;
    private static final int STORAGE_PANEL_WIDTH = 250;
    private static final int STORAGE_PANEL_HEIGHT = 92;
    private static final int MATRIX_CELL_AREA_LEFT = 22;
    private static final int MATRIX_CELL_AREA_TOP = 20;
    private static final int MATRIX_CELL_AREA_WIDTH = GRID_COLUMNS * GRID_SLOT_SIZE + 4;
    private static final int MATRIX_CELL_AREA_HEIGHT = GRID_ROWS * GRID_SLOT_SIZE + 4;
    private static final int METER_PANEL_LEFT = 220;
    private static final int METER_PANEL_TOP = 20;
    private static final int METER_PANEL_WIDTH = 40;
    private static final int METER_PANEL_HEIGHT = 76;
    private static final int CRAFTING_PANEL_LEFT = 57;
    private static final int CRAFTING_PANEL_TOP = 110;
    private static final int CRAFTING_PANEL_WIDTH = 172;
    private static final int CRAFTING_PANEL_HEIGHT = 78;
    private static final int CRAFTING_PANEL_INSET = 3;
    private static final int CRAFTING_ARROW_OFFSET_X = 68;
    private static final int CRAFTING_ARROW_OFFSET_Y = 4;
    private static final int CRAFTING_ARROW_WIDTH = 30;
    private static final int INVENTORY_PANEL_LEFT = 57;
    private static final int INVENTORY_PANEL_TOP = 202;
    private static final int INVENTORY_PANEL_WIDTH = 172;
    private static final int INVENTORY_PANEL_HEIGHT = 90;
    private static final int INVENTORY_X = HolographicStorageMenu.playerInventoryX();
    private static final int INVENTORY_Y = HolographicStorageMenu.playerInventoryY();
    private static final int SCROLLBAR_X = 208;
    private static final int SCROLLBAR_Y = GRID_TOP;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_HEIGHT = GRID_ROWS * GRID_SLOT_SIZE;
    private static final int VISIBLE_ENTRIES = GRID_COLUMNS * GRID_ROWS;
    private static final int LOCKED_COLOR = 0xFF7A1E2B;
    private static final int STORAGE_ACCENT = 0xFF9FE7DF;
    private static final int TYPE_ACCENT = 0xFF78DCCB;
    private static final int ITEM_ACCENT = 0xFFFFB35C;
    private static final int CHANNEL_ACCENT = 0xFFC49BFF;
    private static final int[] CHANNEL_CODE_COLORS = {
            0xFF8E7CC3,
            0xFF6FA8DC,
            0xFF76A5AF,
            0xFF93C47D,
            0xFFF6B26B,
            0xFFE06666,
            0xFFC27BA0,
            0xFFB7B7B7
    };

    private int scrollOffset;
    private boolean draggingScrollbar;

    public HolographicStorageScreen(HolographicStorageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 286;
        imageHeight = 302;
        inventoryLabelX = INVENTORY_X;
        inventoryLabelY = INVENTORY_Y - 12;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderStorageEntries(graphics);
        boolean storageTooltip = renderStorageTooltip(graphics, mouseX, mouseY);
        if (!storageTooltip) {
            renderMatrixTooltip(graphics, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        drawStorageMatrix(graphics, mouseX, mouseY);
        drawCraftingPanel(graphics);
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        if ((button == 0 || button == 1) && isInsideStorageGrid(mouseX, mouseY)) {
            if (menu.interactionLocked()) {
                return true;
            }

            int entryIndex = storageEntryIndex(mouseX, mouseY);
            ItemStack carried = menu.getCarried();
            if (button == 0 && Screen.hasShiftDown() && carried.isEmpty()) {
                if (entryIndex >= 0 && entryIndex < menu.entries().size()) {
                    PacketDistributor.sendToServer(new HolographicStorageActionPayload(
                            menu.containerId,
                            HolographicStorageActionPayload.ACTION_EXTRACT_TO_INVENTORY,
                            entryIndex,
                            64
                    ));
                }

                return true;
            }

            if (!carried.isEmpty()) {
                PacketDistributor.sendToServer(new HolographicStorageActionPayload(
                        menu.containerId,
                        HolographicStorageActionPayload.ACTION_INSERT_CARRIED,
                        -1,
                        button == 1 ? 1 : carried.getCount()
                ));
                return true;
            }

            if (entryIndex < 0 || entryIndex >= menu.entries().size()) {
                return true;
            }

            int amount = button == 1 ? rightClickExtractAmount(menu.entries().get(entryIndex)) : 64;
            PacketDistributor.sendToServer(new HolographicStorageActionPayload(
                    menu.containerId,
                    HolographicStorageActionPayload.ACTION_EXTRACT_TO_CARRIED,
                    entryIndex,
                    amount
            ));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (storageEntryIndex(mouseX, mouseY) >= 0 || isInsideStorageGrid(mouseX, mouseY)) {
            int maxOffset = Math.max(0, menu.entries().size() - VISIBLE_ENTRIES);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset - (int) Math.signum(scrollY) * GRID_COLUMNS));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            updateScrollFromMouse(mouseY);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawStorageMatrix(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCeramicPanel(graphics, leftPos + STORAGE_PANEL_LEFT, topPos + STORAGE_PANEL_TOP,
                STORAGE_PANEL_WIDTH, STORAGE_PANEL_HEIGHT);
        subtleRegion(graphics, leftPos + MATRIX_CELL_AREA_LEFT, topPos + MATRIX_CELL_AREA_TOP,
                MATRIX_CELL_AREA_WIDTH, MATRIX_CELL_AREA_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_BG, 140));
        subtleRegion(graphics, leftPos + METER_PANEL_LEFT, topPos + METER_PANEL_TOP,
                METER_PANEL_WIDTH, METER_PANEL_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 26));

        drawMatrixCells(graphics, mouseX, mouseY);
        drawScrollbar(graphics);
        drawMatrixMeters(graphics);

        if (menu.interactionLocked()) {
            graphics.fill(
                    leftPos + MATRIX_CELL_AREA_LEFT,
                    topPos + MATRIX_CELL_AREA_TOP,
                    leftPos + MATRIX_CELL_AREA_LEFT + MATRIX_CELL_AREA_WIDTH,
                    topPos + MATRIX_CELL_AREA_TOP + MATRIX_CELL_AREA_HEIGHT,
                    0x667A1E2B
            );
        }
    }

    private void drawMatrixCells(GuiGraphics graphics, int mouseX, int mouseY) {
        List<HolographicStorageEntry> entries = menu.entries();
        int maxOffset = Math.max(0, entries.size() - VISIBLE_ENTRIES);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
        int visibleEntries = Math.min(entries.size() - scrollOffset, VISIBLE_ENTRIES);

        for (int index = 0; index < VISIBLE_ENTRIES; index++) {
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int x = leftPos + GRID_LEFT + column * GRID_SLOT_SIZE;
            int y = topPos + GRID_TOP + row * GRID_SLOT_SIZE;
            boolean active = index < visibleEntries;
            boolean hovered = active
                    && mouseX >= x
                    && mouseX < x + GRID_SLOT_SIZE
                    && mouseY >= y
                    && mouseY < y + GRID_SLOT_SIZE;
            matrixCell(graphics, x, y, active, hovered);
        }
    }

    private void drawMatrixMeters(GuiGraphics graphics) {
        int meterLeft = leftPos + METER_PANEL_LEFT + 6;
        int meterTop = topPos + METER_PANEL_TOP + 8;
        drawMiniGauge(graphics, "T", meterLeft, meterTop, menu.storedTypes(), menu.maxTypes(), TYPE_ACCENT);
        drawMiniGauge(graphics, "I", meterLeft, meterTop + 20, menu.storedItems(), menu.maxItems(), ITEM_ACCENT);
        drawChannelCells(graphics, meterLeft, meterTop + 40);
        drawStatusStrip(graphics);
    }

    private void drawMiniGauge(
            GuiGraphics graphics,
            String label,
            int x,
            int y,
            long value,
            long max,
            int color
    ) {
        drawCenteredText(graphics, font, label, x + 3, y - 1, 0.44F, ThermalSmelterUiSkin.TEXT_SUB);
        int barX = x + 8;
        int barWidth = 21;
        int fill = max <= 0 ? 0 : Math.round((barWidth - 2) * Math.max(0L, Math.min(value, max)) / (float) max);
        insetPanel(graphics, barX, y, barWidth, 6, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 82));
        if (fill > 0) {
            graphics.fill(barX + 1, y + 1, barX + 1 + fill, y + 5,
                    ThermalSmelterUiSkin.withAlpha(color, 178));
            graphics.fill(barX + 1, y + 1, barX + 1 + fill, y + 2,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 58));
        }
    }

    private void drawChannelCells(GuiGraphics graphics, int x, int y) {
        drawCenteredText(graphics, font, "C", x + 3, y - 1, 0.44F, ThermalSmelterUiSkin.TEXT_SUB);
        int encodedChannels = Math.max(0, Math.min(4095, menu.channelMultiplier()));
        for (int index = 0; index < 4; index++) {
            int cellX = x + 8 + index * 5;
            int cellY = y + 1;
            int digit = (encodedChannels >> ((3 - index) * 3)) & 7;
            int color = CHANNEL_CODE_COLORS[digit];
            graphics.fill(cellX, cellY, cellX + 4, cellY + 5,
                    ThermalSmelterUiSkin.withAlpha(color, 174));
            outline(graphics, cellX, cellY, 4, 5,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 108));
            graphics.fill(cellX + 1, cellY + 1, cellX + 3, cellY + 2,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 62));
        }
    }

    private void drawStatusStrip(GuiGraphics graphics) {
        int color = menu.interactionLocked() ? LOCKED_COLOR : ThermalSmelterUiSkin.STATUS_READY;
        int x = leftPos + METER_PANEL_LEFT + 8;
        int y = topPos + METER_PANEL_TOP + METER_PANEL_HEIGHT - 8;
        graphics.fill(x, y, x + 24, y + 3, ThermalSmelterUiSkin.withAlpha(color, 150));
        outline(graphics, x, y, 24, 3, ThermalSmelterUiSkin.withAlpha(color, 190));
    }

    private void drawCraftingPanel(GuiGraphics graphics) {
        drawCeramicPanel(graphics, leftPos + CRAFTING_PANEL_LEFT, topPos + CRAFTING_PANEL_TOP,
                CRAFTING_PANEL_WIDTH, CRAFTING_PANEL_HEIGHT);
        subtleRegion(graphics,
                leftPos + CRAFTING_PANEL_LEFT + CRAFTING_PANEL_INSET,
                topPos + CRAFTING_PANEL_TOP + CRAFTING_PANEL_INSET,
                CRAFTING_PANEL_WIDTH - CRAFTING_PANEL_INSET * 2,
                CRAFTING_PANEL_HEIGHT - CRAFTING_PANEL_INSET * 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 24));
        drawCraftingSlots(graphics);
    }

    private void drawCraftingSlots(GuiGraphics graphics) {
        int gridX = HolographicStorageMenu.craftingGridX();
        int gridY = HolographicStorageMenu.craftingGridY();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int slotIndex = 1 + column + row * 3;
                slotFrame(
                        graphics,
                        leftPos + gridX + column * GRID_SLOT_SIZE - 1,
                        topPos + gridY + row * GRID_SLOT_SIZE - 1,
                        STORAGE_ACCENT,
                        menu.getSlot(slotIndex).hasItem()
                );
            }
        }

        int resultX = HolographicStorageMenu.craftingResultX();
        int resultY = HolographicStorageMenu.craftingResultY();
        slotFrame(
                graphics,
                leftPos + resultX - 1,
                topPos + resultY - 1,
                ThermalSmelterUiSkin.PROGRESS,
                menu.getSlot(HolographicStorageMenu.RESULT_SLOT).hasItem()
        );
        pixelArrowRight(
                graphics,
                leftPos + gridX + CRAFTING_ARROW_OFFSET_X,
                topPos + resultY + CRAFTING_ARROW_OFFSET_Y,
                CRAFTING_ARROW_WIDTH,
                8,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 168)
        );
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        drawCeramicPanel(graphics,
                leftPos + INVENTORY_PANEL_LEFT,
                topPos + INVENTORY_PANEL_TOP,
                INVENTORY_PANEL_WIDTH,
                INVENTORY_PANEL_HEIGHT);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        leftPos + INVENTORY_X + column * GRID_SLOT_SIZE - 1,
                        topPos + INVENTORY_Y + row * GRID_SLOT_SIZE - 1
                );
            }
        }

        int hotbarY = INVENTORY_Y + 58;
        graphics.fill(leftPos + INVENTORY_X - 1, topPos + hotbarY - 5,
                leftPos + INVENTORY_X + 9 * GRID_SLOT_SIZE - 1, topPos + hotbarY - 4,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 88));
        for (int column = 0; column < 9; column++) {
            ceramicSlot(graphics, leftPos + INVENTORY_X + column * GRID_SLOT_SIZE - 1, topPos + hotbarY - 1);
        }
    }

    private void renderStorageEntries(GuiGraphics graphics) {
        List<HolographicStorageEntry> entries = menu.entries();
        int maxOffset = Math.max(0, entries.size() - VISIBLE_ENTRIES);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
        int visibleEntries = Math.min(entries.size() - scrollOffset, VISIBLE_ENTRIES);

        for (int index = 0; index < visibleEntries; index++) {
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int x = leftPos + GRID_LEFT + column * GRID_SLOT_SIZE;
            int y = topPos + GRID_TOP + row * GRID_SLOT_SIZE;
            HolographicStorageEntry entry = entries.get(index + scrollOffset);
            ItemStack stack = entry.stack();

            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y, formatCount(entry.count()));
        }
    }

    private boolean renderStorageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int entryIndex = storageEntryIndex(mouseX, mouseY);
        if (entryIndex < 0 || entryIndex >= menu.entries().size()) {
            return false;
        }

        HolographicStorageEntry entry = menu.entries().get(entryIndex);
        ItemStack stack = entry.stack().copy();
        graphics.renderTooltip(font, stack, mouseX, mouseY);
        return true;
    }

    private void renderMatrixTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        if (insideLocal(mouseX, mouseY, METER_PANEL_LEFT + 6, METER_PANEL_TOP + 6, 30, 18)) {
            return List.of(Component.literal(label("screen.spectralization.holographic_storage.types")
                    + ": " + menu.storedTypes() + "/" + menu.maxTypes()));
        }

        if (insideLocal(mouseX, mouseY, METER_PANEL_LEFT + 6, METER_PANEL_TOP + 26, 30, 18)) {
            return List.of(Component.literal(label("screen.spectralization.holographic_storage.items")
                    + ": " + formatCount(menu.storedItems()) + "/" + formatCount(menu.maxItems())));
        }

        if (insideLocal(mouseX, mouseY, METER_PANEL_LEFT + 6, METER_PANEL_TOP + 46, 30, 20)) {
            return List.of(Component.literal(label("screen.spectralization.holographic_storage.channels")
                    + ": x" + menu.channelMultiplier()));
        }

        if (insideLocal(mouseX, mouseY, METER_PANEL_LEFT + 8, METER_PANEL_TOP + METER_PANEL_HEIGHT - 10, 24, 8)) {
            return List.of(statusText());
        }

        if (menu.interactionLocked()
                && insideLocal(mouseX, mouseY, MATRIX_CELL_AREA_LEFT, MATRIX_CELL_AREA_TOP,
                MATRIX_CELL_AREA_WIDTH, MATRIX_CELL_AREA_HEIGHT)) {
            return List.of(statusText());
        }

        return List.of();
    }

    private int storageEntryIndex(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos - GRID_LEFT;
        int localY = (int) mouseY - topPos - GRID_TOP;
        if (localX < 0 || localY < 0) {
            return -1;
        }

        int column = localX / GRID_SLOT_SIZE;
        int row = localY / GRID_SLOT_SIZE;
        if (column < 0 || column >= GRID_COLUMNS || row < 0 || row >= GRID_ROWS) {
            return -1;
        }

        return scrollOffset + row * GRID_COLUMNS + column;
    }

    private boolean isInsideStorageGrid(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos - GRID_LEFT;
        int localY = (int) mouseY - topPos - GRID_TOP;
        return localX >= 0
                && localY >= 0
                && localX < GRID_COLUMNS * GRID_SLOT_SIZE
                && localY < GRID_ROWS * GRID_SLOT_SIZE;
    }

    private boolean isInsideScrollbar(double mouseX, double mouseY) {
        int localX = (int) mouseX - leftPos - SCROLLBAR_X;
        int localY = (int) mouseY - topPos - SCROLLBAR_Y;
        return localX >= 0 && localY >= 0 && localX < SCROLLBAR_WIDTH && localY < SCROLLBAR_HEIGHT;
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int left = leftPos + SCROLLBAR_X;
        int top = topPos + SCROLLBAR_Y;
        insetPanel(graphics, left, top, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 88));

        int maxOffset = Math.max(0, menu.entries().size() - VISIBLE_ENTRIES);
        int thumbHeight = maxOffset <= 0 ? SCROLLBAR_HEIGHT - 2
                : Math.max(10, (SCROLLBAR_HEIGHT - 2) * VISIBLE_ENTRIES / menu.entries().size());
        int travel = Math.max(0, SCROLLBAR_HEIGHT - 2 - thumbHeight);
        int thumbY = maxOffset <= 0 ? 1 : 1 + (int) Math.round((double) scrollOffset / maxOffset * travel);
        int color = menu.interactionLocked() ? LOCKED_COLOR : ThermalSmelterUiSkin.BORDER;
        graphics.fill(left + 1, top + thumbY, left + SCROLLBAR_WIDTH - 1, top + thumbY + thumbHeight,
                ThermalSmelterUiSkin.withAlpha(color, 190));
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxOffset = Math.max(0, menu.entries().size() - VISIBLE_ENTRIES);
        if (maxOffset <= 0) {
            scrollOffset = 0;
            return;
        }

        int thumbHeight = Math.max(10, (SCROLLBAR_HEIGHT - 2) * VISIBLE_ENTRIES / menu.entries().size());
        int travel = Math.max(1, SCROLLBAR_HEIGHT - 2 - thumbHeight);
        int localY = (int) Math.round(mouseY - topPos - SCROLLBAR_Y) - 1 - thumbHeight / 2;
        double ratio = Math.max(0.0, Math.min(1.0, (double) localY / travel));
        scrollOffset = (int) Math.round(ratio * maxOffset);
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

    private boolean insideLocal(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    private static int rightClickExtractAmount(HolographicStorageEntry entry) {
        long half = Math.max(1L, (entry.count() + 1L) / 2L);
        return (int) Math.min(32L, half);
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

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 196, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 8, topPos + 197, leftPos + imageWidth - 8, topPos + 198,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 120));
    }

    private static void drawCeramicPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.PANEL);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 40));
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 90));
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 62));
    }

    private static void subtleRegion(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 58));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 54));
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 48));
    }

    private static void matrixCell(GuiGraphics graphics, int x, int y, boolean active, boolean hovered) {
        int fill = active ? ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_BG, 182)
                : ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 62);
        int border = hovered ? STORAGE_ACCENT
                : active ? ThermalSmelterUiSkin.withAlpha(STORAGE_ACCENT, 122)
                : ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 76);
        graphics.fill(x - 1, y - 1, x + 17, y + 17, border);
        graphics.fill(x, y, x + 16, y + 16, fill);
        graphics.fill(x, y, x + 16, y + 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 48));
        graphics.fill(x, y + 15, x + 16, y + 16, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 86));
        if (active) {
            graphics.fill(x + 14, y + 2, x + 15, y + 14, ThermalSmelterUiSkin.withAlpha(STORAGE_ACCENT, 80));
        }
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, int color, boolean active) {
        ceramicSlot(graphics, x, y);
        if (active) {
            graphics.fill(x + GRID_SLOT_SIZE - 2, y + 2, x + GRID_SLOT_SIZE - 1, y + GRID_SLOT_SIZE - 2,
                    ThermalSmelterUiSkin.withAlpha(color, 155));
            graphics.fill(x + 2, y + GRID_SLOT_SIZE - 2, x + GRID_SLOT_SIZE - 2, y + GRID_SLOT_SIZE - 1,
                    ThermalSmelterUiSkin.withAlpha(color, 90));
        } else {
            outline(graphics, x, y, GRID_SLOT_SIZE, GRID_SLOT_SIZE,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 95));
        }
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + GRID_SLOT_SIZE, y + GRID_SLOT_SIZE, ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, GRID_SLOT_SIZE, GRID_SLOT_SIZE, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + GRID_SLOT_SIZE - 1, y + 2, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + GRID_SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + GRID_SLOT_SIZE - 2, x + GRID_SLOT_SIZE - 1, y + GRID_SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + GRID_SLOT_SIZE - 2, y + 1, x + GRID_SLOT_SIZE - 1, y + GRID_SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + GRID_SLOT_SIZE - 2, y + GRID_SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
    }

    private static void pixelArrowRight(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int centerY = y + height / 2;
        int maxHalfHeight = Math.max(1, height / 2);
        int shaftHeight = Math.max(1, height / 3);

        if (shaftHeight % 2 == 0) {
            shaftHeight++;
        }

        int shaftTop = centerY - shaftHeight / 2;
        int shaftBottom = shaftTop + shaftHeight;
        int headWidth = Math.min(width, Math.min(9, Math.max(4, width / 5)));
        int shaftEnd = width - headWidth;
        if (shaftEnd > 0) {
            graphics.fill(x, shaftTop, x + shaftEnd, shaftBottom, color);
        }

        for (int column = 0; column < headWidth; column++) {
            int drawX = x + width - headWidth + column;
            int distanceToTip = headWidth - 1 - column;
            int halfHeight = Math.round(distanceToTip * maxHalfHeight / (float) Math.max(1, headWidth - 1));
            graphics.fill(drawX, centerY - halfHeight, drawX + 1, centerY + halfHeight + 1, color);
        }
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

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
