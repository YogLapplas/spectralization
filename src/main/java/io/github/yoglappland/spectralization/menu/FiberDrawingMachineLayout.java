package io.github.yoglappland.spectralization.menu;

public final class FiberDrawingMachineLayout {
    public static final int IMAGE_WIDTH = 278;
    public static final int IMAGE_HEIGHT = 252;
    public static final int MACHINE_HEIGHT = 146;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;

    public static final int MACHINE_X = 0;
    public static final int MACHINE_Y = 0;
    public static final int MACHINE_WIDTH = IMAGE_WIDTH;
    public static final int MACHINE_PANEL_HEIGHT = MACHINE_HEIGHT;

    public static final int SIDE_PANEL_WIDTH = 52;
    public static final int SIDE_PANEL_Y = 10;
    public static final int SIDE_PANEL_HEIGHT = 126;
    public static final int LEFT_PANEL_X = 18;
    public static final int RIGHT_PANEL_X = IMAGE_WIDTH - LEFT_PANEL_X - SIDE_PANEL_WIDTH;

    public static final int MAIN_PANEL_WIDTH = 122;
    public static final int MAIN_PANEL_X = (IMAGE_WIDTH - MAIN_PANEL_WIDTH) / 2;
    public static final int MAIN_PANEL_Y = SIDE_PANEL_Y;
    public static final int MAIN_PANEL_HEIGHT = SIDE_PANEL_HEIGHT;
    public static final int MAIN_INNER_X = MAIN_PANEL_X + 4;
    public static final int MAIN_INNER_Y = MAIN_PANEL_Y + 4;
    public static final int MAIN_INNER_WIDTH = MAIN_PANEL_WIDTH - 8;
    public static final int MAIN_INNER_HEIGHT = MAIN_PANEL_HEIGHT - 8;

    public static final int MAIN_BOX_GAP = 4;
    public static final int ENERGY_REGION_WIDTH = 28;
    public static final int ENERGY_REGION_X = MAIN_INNER_X;
    public static final int ENERGY_REGION_Y = MAIN_INNER_Y;
    public static final int ENERGY_REGION_HEIGHT = MAIN_INNER_HEIGHT;

    public static final int WORK_REGION_X = ENERGY_REGION_X + ENERGY_REGION_WIDTH + MAIN_BOX_GAP;
    public static final int WORK_REGION_Y = MAIN_INNER_Y;
    public static final int WORK_REGION_WIDTH = MAIN_INNER_X + MAIN_INNER_WIDTH - WORK_REGION_X;
    public static final int WORK_REGION_HEIGHT = MAIN_INNER_HEIGHT;

    public static final int MOLD_INPUT_REGION_HEIGHT = 21;
    public static final int MOLD_REGION_X = WORK_REGION_X;
    public static final int MOLD_REGION_Y = WORK_REGION_Y;
    public static final int MOLD_REGION_WIDTH = WORK_REGION_WIDTH;
    public static final int MOLD_REGION_HEIGHT = 84;

    public static final int DRAW_REGION_X = WORK_REGION_X;
    public static final int DRAW_REGION_Y = MOLD_REGION_Y + MOLD_INPUT_REGION_HEIGHT + MAIN_BOX_GAP;
    public static final int DRAW_REGION_WIDTH = WORK_REGION_WIDTH;
    public static final int DRAW_REGION_HEIGHT = 36;

    public static final int MOLD_OUTPUT_REGION_X = WORK_REGION_X;
    public static final int MOLD_OUTPUT_REGION_Y = DRAW_REGION_Y + DRAW_REGION_HEIGHT + 2;
    public static final int MOLD_OUTPUT_REGION_WIDTH = WORK_REGION_WIDTH;
    public static final int MOLD_OUTPUT_REGION_HEIGHT = 21;

    public static final int PROPERTY_REGION_X = WORK_REGION_X;
    public static final int PROPERTY_REGION_Y = MOLD_REGION_Y + MOLD_REGION_HEIGHT + MAIN_BOX_GAP;
    public static final int PROPERTY_REGION_WIDTH = WORK_REGION_WIDTH;
    public static final int PROPERTY_REGION_HEIGHT = WORK_REGION_Y + WORK_REGION_HEIGHT - PROPERTY_REGION_Y;

    public static final int ENERGY_DIVIDER_X = ENERGY_REGION_X + ENERGY_REGION_WIDTH;

    public static final int MATERIAL_SLOT_X = LEFT_PANEL_X + (SIDE_PANEL_WIDTH - SLOT_SIZE) / 2;
    public static final int MATERIAL_SLOT_Y = SIDE_PANEL_Y + (SIDE_PANEL_HEIGHT - SLOT_SIZE) / 2;
    public static final int OUTPUT_SLOT_X = RIGHT_PANEL_X + (SIDE_PANEL_WIDTH - SLOT_SIZE) / 2;
    public static final int OUTPUT_SLOT_Y = MATERIAL_SLOT_Y;

    public static final int MOLD_SLOT_Y = DRAW_REGION_Y - SLOT_SIZE - 2;
    public static final int MOLD_OUTPUT_SLOT_Y = DRAW_REGION_Y + DRAW_REGION_HEIGHT + 2;
    public static final int MOLD_INPUT_SLOT_X = MOLD_REGION_X + (MOLD_REGION_WIDTH - SLOT_SIZE) / 2;
    public static final int MOLD_OUTPUT_SLOT_X = MOLD_OUTPUT_REGION_X + (MOLD_OUTPUT_REGION_WIDTH - SLOT_SIZE) / 2;

    public static final int ENERGY_BAR_WIDTH = 10;
    public static final int ENERGY_BAR_HEIGHT = 78;
    public static final int ENERGY_BAR_X = ENERGY_REGION_X + (ENERGY_REGION_WIDTH - ENERGY_BAR_WIDTH) / 2;
    public static final int ENERGY_BAR_Y = ENERGY_REGION_Y + 26;

    public static final int DRAW_LINE_HEIGHT = 24;
    public static final int DRAW_LINE_X = DRAW_REGION_X + 9;
    public static final int DRAW_LINE_Y = DRAW_REGION_Y + (DRAW_REGION_HEIGHT - DRAW_LINE_HEIGHT) / 2;
    public static final int DRAW_LINE_WIDTH = DRAW_REGION_WIDTH - 18;

    public static final int RECIPE_CLICK_X = DRAW_LINE_X - 4;
    public static final int RECIPE_CLICK_Y = DRAW_LINE_Y - 4;
    public static final int RECIPE_CLICK_WIDTH = DRAW_LINE_WIDTH + 8;
    public static final int RECIPE_CLICK_HEIGHT = DRAW_LINE_HEIGHT + 8;

    public static final int PROPERTY_BAR_COUNT = 3;
    public static final int PROPERTY_BAR_WIDTH = 56;
    public static final int PROPERTY_BAR_HEIGHT = 4;
    public static final int PROPERTY_BAR_GAP = 5;
    public static final int PROPERTY_BARS_HEIGHT = PROPERTY_BAR_COUNT * PROPERTY_BAR_HEIGHT + (PROPERTY_BAR_COUNT - 1) * PROPERTY_BAR_GAP;
    public static final int PROPERTY_BARS_X = PROPERTY_REGION_X + (PROPERTY_REGION_WIDTH - PROPERTY_BAR_WIDTH) / 2;
    public static final int PROPERTY_BARS_Y = PROPERTY_REGION_Y + (PROPERTY_REGION_HEIGHT - PROPERTY_BARS_HEIGHT) / 2;

    public static final int RULE_BUTTON_X = -17;
    public static final int RULE_BUTTON_Y = SIDE_PANEL_Y + 12;
    public static final int RULE_BUTTON_WIDTH = 18;
    public static final int RULE_BUTTON_HEIGHT = 18;

    public static final int PLAYER_INVENTORY_X = (IMAGE_WIDTH - 9 * SLOT_SIZE) / 2;
    public static final int PLAYER_INVENTORY_Y = 160;
    public static final int PLAYER_INVENTORY_PANEL_X = PLAYER_INVENTORY_X - 6;
    public static final int PLAYER_INVENTORY_PANEL_Y = PLAYER_INVENTORY_Y - 6;
    public static final int PLAYER_INVENTORY_PANEL_WIDTH = 9 * SLOT_SIZE + 12;
    public static final int PLAYER_INVENTORY_PANEL_HEIGHT = 88;
    public static final int PLAYER_INVENTORY_ITEM_X = PLAYER_INVENTORY_X + ITEM_SLOT_INSET;
    public static final int PLAYER_INVENTORY_ITEM_Y = PLAYER_INVENTORY_Y + ITEM_SLOT_INSET;
    public static final int INVENTORY_LABEL_X = PLAYER_INVENTORY_X;
    public static final int INVENTORY_LABEL_Y = 146;

    public static final int ITEM_MATERIAL_X = MATERIAL_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_MATERIAL_Y = MATERIAL_SLOT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_X = OUTPUT_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_Y = OUTPUT_SLOT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_MOLD_INPUT_X = MOLD_INPUT_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_MOLD_INPUT_Y = MOLD_SLOT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_MOLD_OUTPUT_X = MOLD_OUTPUT_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_MOLD_OUTPUT_Y = MOLD_OUTPUT_SLOT_Y + ITEM_SLOT_INSET;

    private FiberDrawingMachineLayout() {
    }
}
