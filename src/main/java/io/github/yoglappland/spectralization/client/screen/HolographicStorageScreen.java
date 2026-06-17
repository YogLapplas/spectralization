package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.client.gui.SpectralGui;
import io.github.yoglappland.spectralization.client.gui.SpectralGuiTheme;
import io.github.yoglappland.spectralization.client.gui.SpectralSlotKind;
import io.github.yoglappland.spectralization.menu.HolographicStorageMenu;
import io.github.yoglappland.spectralization.network.HolographicStorageActionPayload;
import io.github.yoglappland.spectralization.storage.HolographicStorageEntry;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class HolographicStorageScreen extends AbstractContainerScreen<HolographicStorageMenu> {
    private static final int GRID_LEFT = 24;
    private static final int GRID_TOP = 30;
    private static final int GRID_COLUMNS = 12;
    private static final int GRID_ROWS = 4;
    private static final int GRID_SLOT_SIZE = 18;
    private static final int STORAGE_PANEL_LEFT = 18;
    private static final int STORAGE_PANEL_TOP = 22;
    private static final int STORAGE_PANEL_WIDTH = 250;
    private static final int STORAGE_PANEL_HEIGHT = 92;
    private static final int CRAFTING_PANEL_LEFT = 42;
    private static final int CRAFTING_PANEL_TOP = 122;
    private static final int CRAFTING_PANEL_WIDTH = 202;
    private static final int CRAFTING_PANEL_HEIGHT = 66;
    private static final int INVENTORY_PANEL_LEFT = 56;
    private static final int INVENTORY_PANEL_TOP = 202;
    private static final int INVENTORY_PANEL_WIDTH = 172;
    private static final int INVENTORY_PANEL_HEIGHT = 90;
    private static final int INVENTORY_X = HolographicStorageMenu.playerInventoryX();
    private static final int INVENTORY_Y = HolographicStorageMenu.playerInventoryY();
    private static final int SCROLLBAR_X = 254;
    private static final int SCROLLBAR_Y = GRID_TOP;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_HEIGHT = GRID_ROWS * GRID_SLOT_SIZE;
    private static final int VISIBLE_ENTRIES = GRID_COLUMNS * GRID_ROWS;
    private static final int LOCKED_COLOR = 0xFF7A1E2B;

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
        renderStorageTooltip(graphics, mouseX, mouseY);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        SpectralGui.drawScreen(graphics, leftPos, topPos, imageWidth, imageHeight);
        SpectralGui.drawPanel(graphics, leftPos + STORAGE_PANEL_LEFT, topPos + STORAGE_PANEL_TOP, STORAGE_PANEL_WIDTH, STORAGE_PANEL_HEIGHT);
        SpectralGui.drawPanel(graphics, leftPos + CRAFTING_PANEL_LEFT, topPos + CRAFTING_PANEL_TOP, CRAFTING_PANEL_WIDTH, CRAFTING_PANEL_HEIGHT);
        SpectralGui.drawPanel(graphics, leftPos + INVENTORY_PANEL_LEFT, topPos + INVENTORY_PANEL_TOP, INVENTORY_PANEL_WIDTH, INVENTORY_PANEL_HEIGHT);

        for (int row = 0; row < GRID_ROWS; row++) {
            for (int column = 0; column < GRID_COLUMNS; column++) {
                int x = leftPos + GRID_LEFT + column * GRID_SLOT_SIZE;
                int y = topPos + GRID_TOP + row * GRID_SLOT_SIZE;
                SpectralGui.drawSlot(graphics, x, y, SpectralSlotKind.OPTICAL);
            }
        }

        drawScrollbar(graphics);
        drawCraftingSlots(graphics);
        SpectralGui.drawPlayerInventorySlots(graphics, leftPos + INVENTORY_X, topPos + INVENTORY_Y);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, SpectralGuiTheme.TEXT_TITLE, false);
        if (menu.interactionLocked()) {
            Component status = menu.structureError()
                    ? Component.translatable("screen.spectralization.holographic_storage.status_error")
                    : Component.translatable("screen.spectralization.holographic_storage.status_over_capacity");
            graphics.drawString(font, status, 8, 16, LOCKED_COLOR, false);
        }
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

    private void renderStorageEntries(GuiGraphics graphics) {
        List<HolographicStorageEntry> entries = menu.entries();
        int maxOffset = Math.max(0, entries.size() - VISIBLE_ENTRIES);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
        int visibleEntries = Math.min(entries.size() - scrollOffset, VISIBLE_ENTRIES);

        for (int index = 0; index < visibleEntries; index++) {
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int x = leftPos + GRID_LEFT + column * GRID_SLOT_SIZE + 1;
            int y = topPos + GRID_TOP + row * GRID_SLOT_SIZE + 1;
            HolographicStorageEntry entry = entries.get(index + scrollOffset);
            ItemStack stack = entry.stack();

            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y, formatCount(entry.count()));
        }

        if (menu.interactionLocked()) {
            graphics.fill(
                    leftPos + GRID_LEFT - 1,
                    topPos + GRID_TOP - 1,
                    leftPos + GRID_LEFT + GRID_COLUMNS * GRID_SLOT_SIZE,
                    topPos + GRID_TOP + GRID_ROWS * GRID_SLOT_SIZE,
                    0x667A1E2B
            );
        }
    }

    private void renderStorageTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int entryIndex = storageEntryIndex(mouseX, mouseY);
        if (entryIndex < 0 || entryIndex >= menu.entries().size()) {
            return;
        }

        HolographicStorageEntry entry = menu.entries().get(entryIndex);
        ItemStack stack = entry.stack().copy();
        graphics.renderTooltip(font, stack, mouseX, mouseY);
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
        SpectralGui.drawInset(graphics, left, top, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT);

        int maxOffset = Math.max(0, menu.entries().size() - VISIBLE_ENTRIES);
        int thumbHeight = maxOffset <= 0 ? SCROLLBAR_HEIGHT - 2 : Math.max(10, (SCROLLBAR_HEIGHT - 2) * VISIBLE_ENTRIES / menu.entries().size());
        int travel = Math.max(0, SCROLLBAR_HEIGHT - 2 - thumbHeight);
        int thumbY = maxOffset <= 0 ? 1 : 1 + (int) Math.round((double) scrollOffset / maxOffset * travel);
        int color = menu.interactionLocked() ? LOCKED_COLOR : SpectralGuiTheme.OPTICAL;
        graphics.fill(left + 1, top + thumbY, left + SCROLLBAR_WIDTH - 1, top + thumbY + thumbHeight, color);
    }

    private void drawCraftingSlots(GuiGraphics graphics) {
        int gridX = HolographicStorageMenu.craftingGridX();
        int gridY = HolographicStorageMenu.craftingGridY();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                SpectralGui.drawSlot(
                        graphics,
                        leftPos + gridX + column * GRID_SLOT_SIZE,
                        topPos + gridY + row * GRID_SLOT_SIZE,
                        SpectralSlotKind.INPUT
                );
            }
        }

        int resultX = HolographicStorageMenu.craftingResultX();
        int resultY = HolographicStorageMenu.craftingResultY();
        SpectralGui.drawSlot(graphics, leftPos + resultX, topPos + resultY, SpectralSlotKind.OUTPUT);
        int arrowY = topPos + resultY + 8;
        int arrowLeft = leftPos + gridX + 64;
        SpectralGui.drawRightArrow(graphics, arrowLeft, arrowY, 38, SpectralGuiTheme.OPTICAL);
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
}
