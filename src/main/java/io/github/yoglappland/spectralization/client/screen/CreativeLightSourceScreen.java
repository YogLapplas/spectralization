package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.menu.CreativeLightSourceMenu;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CreativeLightSourceScreen extends AbstractContainerScreen<CreativeLightSourceMenu> {
    public CreativeLightSourceScreen(CreativeLightSourceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 210;
        inventoryLabelY = imageHeight + 100;
    }

    @Override
    protected void init() {
        super.init();

        int y = topPos + 24;
        addRow(y, CreativeLightSourceMenu.BUTTON_REGION_DOWN, CreativeLightSourceMenu.BUTTON_REGION_UP);
        addRow(y + 18, CreativeLightSourceMenu.BUTTON_BIN_DOWN, CreativeLightSourceMenu.BUTTON_BIN_UP);
        addRow(y + 36, CreativeLightSourceMenu.BUTTON_POWER_DOWN, CreativeLightSourceMenu.BUTTON_POWER_UP);
        addSingleButton(y + 54, CreativeLightSourceMenu.BUTTON_COHERENCE, "Toggle");
        addRow(y + 72, CreativeLightSourceMenu.BUTTON_MODEL_DOWN, CreativeLightSourceMenu.BUTTON_MODEL_UP);
        addRow(y + 90, CreativeLightSourceMenu.BUTTON_RADIUS_DOWN, CreativeLightSourceMenu.BUTTON_RADIUS_UP);
        addRow(y + 108, CreativeLightSourceMenu.BUTTON_DIVERGENCE_DOWN, CreativeLightSourceMenu.BUTTON_DIVERGENCE_UP);
        addRow(y + 126, CreativeLightSourceMenu.BUTTON_FOCUS_DOWN, CreativeLightSourceMenu.BUTTON_FOCUS_UP);
        addRow(y + 144, CreativeLightSourceMenu.BUTTON_MODE_M_DOWN, CreativeLightSourceMenu.BUTTON_MODE_M_UP);
        addRow(y + 162, CreativeLightSourceMenu.BUTTON_MODE_N_DOWN, CreativeLightSourceMenu.BUTTON_MODE_N_UP);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xDD101216);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xDD1B1F27);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EEF8, false);

        int y = 24;
        drawValue(graphics, y, "Region", regionName());
        drawValue(graphics, y + 18, "Bin", Integer.toString(data(CreativeLightSourceBlockEntity.DATA_BIN)));
        drawValue(graphics, y + 36, "Power", Integer.toString(data(CreativeLightSourceBlockEntity.DATA_POWER)));
        drawValue(graphics, y + 54, "Coherence", coherenceName());
        drawValue(graphics, y + 72, "Model", beamModelName());
        drawValue(graphics, y + 90, "Radius", milli(CreativeLightSourceBlockEntity.DATA_RADIUS_MILLI));
        drawValue(graphics, y + 108, "Divergence", milli(CreativeLightSourceBlockEntity.DATA_DIVERGENCE_MILLI));
        drawValue(graphics, y + 126, "Focus", milli(CreativeLightSourceBlockEntity.DATA_FOCUS_DISTANCE_MILLI));
        drawValue(graphics, y + 144, "Mode M", Integer.toString(data(CreativeLightSourceBlockEntity.DATA_MODE_M)));
        drawValue(graphics, y + 162, "Mode N", Integer.toString(data(CreativeLightSourceBlockEntity.DATA_MODE_N)));
    }

    private void addRow(int y, int downId, int upId) {
        addSingleButton(y, downId, "-");
        addRenderableWidget(Button.builder(Component.literal("+"), button -> click(upId))
                .bounds(leftPos + 215, y, 26, 16)
                .build());
    }

    private void addSingleButton(int y, int id, String text) {
        int width = text.equals("Toggle") ? 54 : 26;
        addRenderableWidget(Button.builder(Component.literal(text), button -> click(id))
                .bounds(leftPos + 184, y, width, 16)
                .build());
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void drawValue(GuiGraphics graphics, int y, String label, String value) {
        graphics.drawString(font, label + ": " + value, 12, y + 4, 0xD8DEE9, false);
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private String regionName() {
        SpectralRegion[] regions = SpectralRegion.values();
        int index = Math.max(0, Math.min(data(CreativeLightSourceBlockEntity.DATA_REGION), regions.length - 1));
        return regions[index].id();
    }

    private String coherenceName() {
        CoherenceKind[] kinds = CoherenceKind.values();
        int index = Math.max(0, Math.min(data(CreativeLightSourceBlockEntity.DATA_COHERENCE), kinds.length - 1));
        return kinds[index].name().toLowerCase();
    }

    private String beamModelName() {
        BeamModel[] models = BeamModel.values();
        int index = Math.max(0, Math.min(data(CreativeLightSourceBlockEntity.DATA_BEAM_MODEL), models.length - 1));
        return models[index].name().toLowerCase();
    }

    private String milli(int index) {
        return String.format("%.3f", data(index) / 1000.0);
    }
}
