package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.item.BrushPaintSelection;
import io.github.yoglappland.spectralization.menu.CoatingBrushMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class CoatingBrushScreen extends AbstractContainerScreen<CoatingBrushMenu> {
    private static final int CARD_WIDTH = 74;
    private static final int CARD_HEIGHT = 76;

    private final List<Button> paintButtons = new ArrayList<>();

    public CoatingBrushScreen(CoatingBrushMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 190;
        imageHeight = 140;
        inventoryLabelY = imageHeight + 100;
    }

    @Override
    protected void init() {
        super.init();
        paintButtons.clear();

        for (int index = 0; index < BrushPaintSelection.SELECTABLE_TREATMENTS.size(); index++) {
            int x = leftPos + 18 + index * 82;
            int y = topPos + 92;
            int buttonId = index;
            Button button = Button.builder(Component.translatable(CoatingBrushMenu.labelFor(index).key()), ignored -> click(buttonId))
                    .bounds(x, y, CARD_WIDTH, 18)
                    .build();
            paintButtons.add(button);
            addRenderableWidget(button);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        updateButtonVisibility();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xDD101216);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xDD1B1F27);

        for (int index = 0; index < BrushPaintSelection.SELECTABLE_TREATMENTS.size(); index++) {
            if (menu.isPaintVisible(index)) {
                renderPaintCard(graphics, index);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EEF8, false);

        Component mode = menu.getData(CoatingBrushMenu.DATA_CREATIVE) == 1
                ? Component.translatable("screen.spectralization.coating_brush.creative")
                : Component.translatable("screen.spectralization.coating_brush.stored");
        graphics.drawString(font, mode, 10, 24, 0xBFC7D5, false);

        if (menu.getData(CoatingBrushMenu.DATA_CREATIVE) == 0
                && !menu.isPaintVisible(0)
                && !menu.isPaintVisible(1)) {
            graphics.drawString(font, Component.translatable("screen.spectralization.coating_brush.empty"), 10, 54, 0x8992A3, false);
        }
    }

    private void renderPaintCard(GuiGraphics graphics, int index) {
        int x = leftPos + 18 + index * 82;
        int y = topPos + 42;
        boolean selected = menu.getData(CoatingBrushMenu.DATA_SELECTED_INDEX) == index;
        int border = selected ? 0xFF9DF7FF : 0xFF3A4352;
        int fill = selected ? 0x552B6370 : 0x55262B36;

        graphics.fill(x - 4, y - 4, x + CARD_WIDTH + 4, y + CARD_HEIGHT, border);
        graphics.fill(x - 3, y - 3, x + CARD_WIDTH + 3, y + CARD_HEIGHT - 1, fill);

        ItemStack icon = menu.iconFor(index);

        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + 29, y + 8);
        }

        if (menu.getData(CoatingBrushMenu.DATA_CREATIVE) == 0) {
            graphics.drawString(font, Component.translatable("screen.spectralization.coating_brush.uses", menu.usesFor(index)), x + 16, y + 32, 0xBFC7D5, false);
        }
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void updateButtonVisibility() {
        for (int index = 0; index < paintButtons.size(); index++) {
            Button button = paintButtons.get(index);
            boolean visible = menu.isPaintVisible(index);
            button.visible = visible;
            button.active = visible;
        }
    }
}
