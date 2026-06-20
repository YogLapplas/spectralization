package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.MetamaterialDesignTableBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralGui;
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
    private static final int MACHINE_PANEL_X = 18;
    private static final int MACHINE_PANEL_Y = 22;
    private static final int MACHINE_PANEL_WIDTH = 220;
    private static final int MACHINE_PANEL_HEIGHT = 132;
    private static final int OUTPUT_FRAME_X = 108;
    private static final int OUTPUT_FRAME_Y = 54;
    private static final int OUTPUT_FRAME_SIZE = 42;
    private static final int RANGE_LEFT = 56;
    private static final int RANGE_TOP = 122;
    private static final int RANGE_WIDTH = 148;
    private static final int AXIS_X_COLOR = 0xFFE64A4A;
    private static final int AXIS_Y_COLOR = 0xFFE8D45C;
    private static final int AXIS_Z_COLOR = 0xFF4E8CFF;

    private Button modeButton;
    private Button designButton;

    public MetamaterialDesignTableScreen(MetamaterialDesignTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "metamaterial_design_table", 256, 252, MetamaterialDesignTableMenu.INVENTORY_X, 146);
    }

    @Override
    protected void init() {
        super.init();
        modeButton = Button.builder(Component.empty(), button -> click(MetamaterialDesignTableMenu.BUTTON_TOGGLE_MODE))
                .bounds(leftPos + 117, topPos + 36, 24, 14)
                .build();
        addRenderableWidget(modeButton);
        addSmallButton(88, 68, MetamaterialDesignTableMenu.BUTTON_STANDARD_PREV, Component.literal("<"));
        addSmallButton(154, 68, MetamaterialDesignTableMenu.BUTTON_STANDARD_NEXT, Component.literal(">"));
        designButton = Button.builder(Component.literal(">"),
                        button -> click(MetamaterialDesignTableMenu.BUTTON_DESIGN))
                .bounds(leftPos + 117, topPos + 101, 24, 14)
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
        panel(graphics, "machine_panel", MACHINE_PANEL_X, MACHINE_PANEL_Y, MACHINE_PANEL_WIDTH, MACHINE_PANEL_HEIGHT);
        panel(graphics, "inventory_panel", 42, 154, 172, 88);
        renderOutputFrame(graphics);
        slot(graphics, "slot_left_top", MetamaterialDesignTableMenu.X_SLOT_X, MetamaterialDesignTableMenu.X_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_left_middle", MetamaterialDesignTableMenu.Y_SLOT_X, MetamaterialDesignTableMenu.Y_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_left_bottom", MetamaterialDesignTableMenu.Z_SLOT_X, MetamaterialDesignTableMenu.Z_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_right_top", MetamaterialDesignTableMenu.RIGHT_TOP_SLOT_X, MetamaterialDesignTableMenu.RIGHT_TOP_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_right_middle", MetamaterialDesignTableMenu.RIGHT_MIDDLE_SLOT_X, MetamaterialDesignTableMenu.RIGHT_MIDDLE_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_right_bottom", MetamaterialDesignTableMenu.RIGHT_BOTTOM_SLOT_X, MetamaterialDesignTableMenu.RIGHT_BOTTOM_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_output", MetamaterialDesignTableMenu.OUTPUT_SLOT_X, MetamaterialDesignTableMenu.OUTPUT_SLOT_Y, SpectralSlotKind.OUTPUT);
        playerInventorySlots(graphics, MetamaterialDesignTableMenu.INVENTORY_X, MetamaterialDesignTableMenu.INVENTORY_Y);
        renderGhostItems(graphics);
        renderRangeBars(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);
        drawAxisGlyph(graphics, "X", RANGE_LEFT - 16, RANGE_TOP - 1, AXIS_X_COLOR);
        drawAxisGlyph(graphics, "Y", RANGE_LEFT - 16, RANGE_TOP + 11, AXIS_Y_COLOR);
        drawAxisGlyph(graphics, "Z", RANGE_LEFT - 16, RANGE_TOP + 23, AXIS_Z_COLOR);
    }

    private void renderOutputFrame(GuiGraphics graphics) {
        SpectralGui.drawInset(
                graphics,
                leftPos + OUTPUT_FRAME_X,
                topPos + OUTPUT_FRAME_Y,
                OUTPUT_FRAME_SIZE,
                OUTPUT_FRAME_SIZE
        );
        graphics.fill(
                leftPos + OUTPUT_FRAME_X + 4,
                topPos + OUTPUT_FRAME_Y + 4,
                leftPos + OUTPUT_FRAME_X + OUTPUT_FRAME_SIZE - 4,
                topPos + OUTPUT_FRAME_Y + OUTPUT_FRAME_SIZE - 4,
                0x44141A18
        );
        graphics.fill(
                leftPos + OUTPUT_FRAME_X + 8,
                topPos + OUTPUT_FRAME_Y + 8,
                leftPos + OUTPUT_FRAME_X + OUTPUT_FRAME_SIZE - 8,
                topPos + OUTPUT_FRAME_Y + OUTPUT_FRAME_SIZE - 8,
                menu.ready() ? 0x227CEAD9 : 0x225B635A
        );
    }

    private void renderGhostItems(GuiGraphics graphics) {
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_X_BUDGET,
                new ItemStack(Spectralization.RAW_SILVER.get()), MetamaterialDesignTableMenu.X_SLOT_X, MetamaterialDesignTableMenu.X_SLOT_Y);
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_Y_BUDGET,
                new ItemStack(Spectralization.CORUNDUM.get()), MetamaterialDesignTableMenu.Y_SLOT_X, MetamaterialDesignTableMenu.Y_SLOT_Y);
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_Z_BUDGET,
                new ItemStack(Spectralization.FLUORITE.get()), MetamaterialDesignTableMenu.Z_SLOT_X, MetamaterialDesignTableMenu.Z_SLOT_Y);
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_RIGHT_TOP,
                new ItemStack(Items.QUARTZ), MetamaterialDesignTableMenu.RIGHT_TOP_SLOT_X, MetamaterialDesignTableMenu.RIGHT_TOP_SLOT_Y);
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_RIGHT_MIDDLE,
                new ItemStack(Items.GOLD_INGOT), MetamaterialDesignTableMenu.RIGHT_MIDDLE_SLOT_X, MetamaterialDesignTableMenu.RIGHT_MIDDLE_SLOT_Y);
        renderGhostItem(graphics, MetamaterialDesignTableBlockEntity.SLOT_RIGHT_BOTTOM,
                new ItemStack(Spectralization.RUBY.get()), MetamaterialDesignTableMenu.RIGHT_BOTTOM_SLOT_X, MetamaterialDesignTableMenu.RIGHT_BOTTOM_SLOT_Y);
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
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_X),
                AXIS_X_COLOR);
        drawRange(graphics, RANGE_LEFT, RANGE_TOP + 12,
                data(MetamaterialDesignTableBlockEntity.DATA_MIN_Y),
                data(MetamaterialDesignTableBlockEntity.DATA_MAX_Y),
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_Y),
                AXIS_Y_COLOR);
        drawRange(graphics, RANGE_LEFT, RANGE_TOP + 24,
                data(MetamaterialDesignTableBlockEntity.DATA_MIN_Z),
                data(MetamaterialDesignTableBlockEntity.DATA_MAX_Z),
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_Z),
                AXIS_Z_COLOR);
    }

    private void drawRange(GuiGraphics graphics, int x, int y, int min, int max, int target, int color) {
        SpectralGui.drawInset(graphics, leftPos + x, topPos + y, RANGE_WIDTH, 7);
        if (min <= max) {
            int left = positionFor(min);
            int right = positionFor(max) + 1;
            graphics.fill(
                    leftPos + x + left,
                    topPos + y + 1,
                    leftPos + x + right,
                    topPos + y + 6,
                    SpectralGui.tinted(color, 180)
            );
        }

        if (!menu.customMode()) {
            int marker = positionFor(target);
            graphics.fill(leftPos + x + marker, topPos + y - 1, leftPos + x + marker + 1, topPos + y + 8,
                    data(MetamaterialDesignTableBlockEntity.DATA_TARGET_IN_RANGE) != 0 ? 0xFFFFFFFF : 0xFFFF7066);
        }
    }

    private int positionFor(int value) {
        int clamped = MetamaterialVector.clamp(value);
        double ratio = (clamped - MetamaterialVector.MIN_VALUE) / (double) (MetamaterialVector.VALUE_COUNT - 1);
        return 1 + (int) Math.round((RANGE_WIDTH - 3) * ratio);
    }

    private void updateButtons() {
        if (modeButton != null) {
            modeButton.setMessage(Component.literal(menu.customMode() ? "C" : "S"));
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

    private int data(int index) {
        return menu.getData(index);
    }

    private void drawAxisGlyph(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.fill(leftPos + x - 2, topPos + y, leftPos + x + 10, topPos + y + 9, SpectralGui.tinted(color, 50));
        graphics.drawString(font, text, leftPos + x + 1, topPos + y + 1, color, false);
    }
}
