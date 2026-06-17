package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.MetamaterialDesignTableBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralGui;
import io.github.yoglappland.spectralization.client.gui.SpectralGuiTheme;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.SpectralSlotKind;
import io.github.yoglappland.spectralization.menu.MetamaterialDesignTableMenu;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialVector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MetamaterialDesignTableScreen extends SpectralMachineScreen<MetamaterialDesignTableMenu> {
    private static final int RANGE_LEFT = 132;
    private static final int RANGE_TOP = 78;
    private static final int RANGE_WIDTH = 86;

    private Button modeButton;
    private Button designButton;

    public MetamaterialDesignTableScreen(MetamaterialDesignTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "metamaterial_design_table", 256, 232, MetamaterialDesignTableMenu.INVENTORY_X, 126);
    }

    @Override
    protected void init() {
        super.init();
        modeButton = Button.builder(Component.empty(), button -> click(MetamaterialDesignTableMenu.BUTTON_TOGGLE_MODE))
                .bounds(leftPos + 132, topPos + 32, 58, 18)
                .build();
        addRenderableWidget(modeButton);
        addSmallButton(196, 34, MetamaterialDesignTableMenu.BUTTON_STANDARD_PREV, Component.literal("<"));
        addSmallButton(216, 34, MetamaterialDesignTableMenu.BUTTON_STANDARD_NEXT, Component.literal(">"));
        designButton = Button.builder(Component.translatable("screen.spectralization.metamaterial_design_table.design"),
                        button -> click(MetamaterialDesignTableMenu.BUTTON_DESIGN))
                .bounds(leftPos + 72, topPos + 94, 54, 18)
                .build();
        addRenderableWidget(designButton);
        updateButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawMachineBackground(graphics);
        panel(graphics, "machine_panel", 18, 22, 220, 104);
        panel(graphics, "inventory_panel", 42, 132, 172, 90);
        slot(graphics, "slot_x_budget", MetamaterialDesignTableMenu.X_SLOT_X, MetamaterialDesignTableMenu.X_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_y_budget", MetamaterialDesignTableMenu.Y_SLOT_X, MetamaterialDesignTableMenu.Y_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_z_budget", MetamaterialDesignTableMenu.Z_SLOT_X, MetamaterialDesignTableMenu.Z_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_output", MetamaterialDesignTableMenu.OUTPUT_SLOT_X, MetamaterialDesignTableMenu.OUTPUT_SLOT_Y, SpectralSlotKind.OUTPUT);
        playerInventorySlots(graphics, MetamaterialDesignTableMenu.INVENTORY_X, MetamaterialDesignTableMenu.INVENTORY_Y);
        renderGhostItems(graphics);
        SpectralGui.drawRightArrow(graphics, leftPos + 58, topPos + 72, 28, SpectralGuiTheme.OPTICAL);
        renderRangeBars(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);
        clippedText(graphics, label(modeKey()), 132, 56, 92, SpectralGuiTheme.TEXT_PRIMARY);
        clippedText(graphics, Component.translatable(menu.selectedStandard().translationKey()).getString(), 132, 66, 92, SpectralGuiTheme.TEXT_MUTED);
        clippedText(graphics, "X " + range(MetamaterialDesignTableBlockEntity.DATA_MIN_X, MetamaterialDesignTableBlockEntity.DATA_MAX_X),
                132, 92, 84, SpectralGuiTheme.TEXT_PRIMARY);
        clippedText(graphics, "Y " + range(MetamaterialDesignTableBlockEntity.DATA_MIN_Y, MetamaterialDesignTableBlockEntity.DATA_MAX_Y),
                132, 106, 84, SpectralGuiTheme.TEXT_PRIMARY);
        clippedText(graphics, "Z " + range(MetamaterialDesignTableBlockEntity.DATA_MIN_Z, MetamaterialDesignTableBlockEntity.DATA_MAX_Z),
                132, 120, 84, SpectralGuiTheme.TEXT_PRIMARY);

        if (!menu.customMode()) {
            clippedText(graphics, targetText(), 24, 116, 98,
                    data(MetamaterialDesignTableBlockEntity.DATA_TARGET_IN_RANGE) != 0
                            ? SpectralGuiTheme.TEXT_MUTED
                            : 0xFFFF8FA3);
        }
    }

    private void renderGhostItems(GuiGraphics graphics) {
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_X_BUDGET,
                new ItemStack(Spectralization.RAW_SILVER.get()), MetamaterialDesignTableMenu.X_SLOT_X, MetamaterialDesignTableMenu.X_SLOT_Y);
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_Y_BUDGET,
                new ItemStack(Spectralization.CORUNDUM.get()), MetamaterialDesignTableMenu.Y_SLOT_X, MetamaterialDesignTableMenu.Y_SLOT_Y);
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_Z_BUDGET,
                new ItemStack(Spectralization.FLUORITE.get()), MetamaterialDesignTableMenu.Z_SLOT_X, MetamaterialDesignTableMenu.Z_SLOT_Y);
    }

    private void renderGhostItem(GuiGraphics graphics, int slot, ItemStack stack, int x, int y) {
        if (menu.getSlot(slot).hasItem()) {
            return;
        }

        int left = leftPos + x;
        int top = topPos + y;
        graphics.renderItem(stack, left, top);
        graphics.fill(left, top, left + 16, top + 16, 0xAA080A09);
    }

    private void renderRangeBars(GuiGraphics graphics) {
        drawRange(graphics, RANGE_LEFT, RANGE_TOP,
                data(MetamaterialDesignTableBlockEntity.DATA_MIN_X),
                data(MetamaterialDesignTableBlockEntity.DATA_MAX_X),
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_X));
        drawRange(graphics, RANGE_LEFT, RANGE_TOP + 14,
                data(MetamaterialDesignTableBlockEntity.DATA_MIN_Y),
                data(MetamaterialDesignTableBlockEntity.DATA_MAX_Y),
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_Y));
        drawRange(graphics, RANGE_LEFT, RANGE_TOP + 28,
                data(MetamaterialDesignTableBlockEntity.DATA_MIN_Z),
                data(MetamaterialDesignTableBlockEntity.DATA_MAX_Z),
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_Z));
    }

    private void drawRange(GuiGraphics graphics, int x, int y, int min, int max, int target) {
        SpectralGui.drawInset(graphics, leftPos + x, topPos + y, RANGE_WIDTH, 6);
        if (min <= max) {
            int left = positionFor(min);
            int right = positionFor(max) + 1;
            graphics.fill(leftPos + x + left, topPos + y + 1, leftPos + x + right, topPos + y + 5, 0xAA7CEAD9);
        }

        if (!menu.customMode()) {
            int marker = positionFor(target);
            graphics.fill(leftPos + x + marker, topPos + y - 1, leftPos + x + marker + 1, topPos + y + 7, 0xFFFFE28A);
        }
    }

    private int positionFor(int value) {
        int clamped = MetamaterialVector.clamp(value);
        double ratio = (clamped - MetamaterialVector.MIN_VALUE) / (double) (MetamaterialVector.VALUE_COUNT - 1);
        return 1 + (int) Math.round((RANGE_WIDTH - 3) * ratio);
    }

    private void updateButtons() {
        if (modeButton != null) {
            modeButton.setMessage(Component.translatable(modeKey()));
        }

        if (designButton != null) {
            designButton.active = menu.ready();
        }
    }

    private void addSmallButton(int x, int y, int id, Component text) {
        addRenderableWidget(Button.builder(text, button -> click(id))
                .bounds(leftPos + x, topPos + y, 16, 14)
                .build());
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private String modeKey() {
        return menu.customMode()
                ? "screen.spectralization.metamaterial_design_table.mode_custom"
                : "screen.spectralization.metamaterial_design_table.mode_standard";
    }

    private String range(int minIndex, int maxIndex) {
        int min = data(minIndex);
        int max = data(maxIndex);
        return min <= max ? min + ".." + max : "--";
    }

    private String targetText() {
        return "T "
                + data(MetamaterialDesignTableBlockEntity.DATA_TARGET_X)
                + ","
                + data(MetamaterialDesignTableBlockEntity.DATA_TARGET_Y)
                + ","
                + data(MetamaterialDesignTableBlockEntity.DATA_TARGET_Z);
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private void clippedText(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        String clipped = font.plainSubstrByWidth(text, width);
        graphics.drawString(font, clipped, x, y, color, false);
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }
}
