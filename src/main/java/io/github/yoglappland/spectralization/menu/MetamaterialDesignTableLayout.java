package io.github.yoglappland.spectralization.menu;

public final class MetamaterialDesignTableLayout {
    public static final int IMAGE_WIDTH = 256;
    public static final int IMAGE_HEIGHT = 252;
    public static final int MACHINE_HEIGHT = 146;

    public static final int PROCESS_X = 0;
    public static final int PROCESS_Y = 0;
    public static final int PROCESS_WIDTH = IMAGE_WIDTH;
    public static final int PROCESS_HEIGHT = MACHINE_HEIGHT;

    public static final int SIDE_PANEL_WIDTH = 44;
    public static final int LEFT_PANEL_X = 9;
    public static final int RIGHT_PANEL_X = IMAGE_WIDTH - LEFT_PANEL_X - SIDE_PANEL_WIDTH;
    public static final int SIDE_PANEL_Y = 10;
    public static final int SIDE_PANEL_HEIGHT = 126;
    public static final int LEFT_PANEL_CENTER_X = LEFT_PANEL_X + SIDE_PANEL_WIDTH / 2;
    public static final int RIGHT_PANEL_CENTER_X = RIGHT_PANEL_X + SIDE_PANEL_WIDTH / 2;

    public static final int CHAMBER_WIDTH = 132;
    public static final int CHAMBER_X = (IMAGE_WIDTH - CHAMBER_WIDTH) / 2;
    public static final int CHAMBER_Y = SIDE_PANEL_Y;
    public static final int CHAMBER_HEIGHT = SIDE_PANEL_HEIGHT;
    public static final int CHAMBER_INNER_X = CHAMBER_X + 4;
    public static final int CHAMBER_INNER_Y = CHAMBER_Y + 4;
    public static final int CHAMBER_INNER_WIDTH = CHAMBER_WIDTH - 8;
    public static final int CHAMBER_INNER_HEIGHT = CHAMBER_HEIGHT - 8;

    public static final int MAIN_PANEL_X = CHAMBER_INNER_X + 4;
    public static final int MAIN_PANEL_Y = CHAMBER_INNER_Y + 4;
    public static final int MAIN_PANEL_WIDTH = CHAMBER_INNER_WIDTH - 8;
    public static final int MAIN_PANEL_HEIGHT = CHAMBER_INNER_HEIGHT - 8;
    public static final int MAIN_PANEL_GAP = 5;

    public static final int OUTPUT_ZONE_X = MAIN_PANEL_X;
    public static final int OUTPUT_ZONE_Y = MAIN_PANEL_Y;
    public static final int OUTPUT_ZONE_WIDTH = MAIN_PANEL_WIDTH;
    public static final int OUTPUT_ZONE_HEIGHT = 45;
    public static final int BAR_ZONE_X = MAIN_PANEL_X;
    public static final int BAR_ZONE_Y = OUTPUT_ZONE_Y + OUTPUT_ZONE_HEIGHT + MAIN_PANEL_GAP;
    public static final int BAR_ZONE_WIDTH = MAIN_PANEL_WIDTH;
    public static final int BAR_ZONE_HEIGHT = MAIN_PANEL_HEIGHT - OUTPUT_ZONE_HEIGHT - MAIN_PANEL_GAP;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;
    public static final int LEFT_SLOT_X = LEFT_PANEL_CENTER_X - SLOT_SIZE / 2;
    public static final int RIGHT_SLOT_X = RIGHT_PANEL_CENTER_X - SLOT_SIZE / 2;
    public static final int TOP_SLOT_Y = SIDE_PANEL_Y + 17;
    public static final int MIDDLE_SLOT_Y = SIDE_PANEL_Y + 54;
    public static final int BOTTOM_SLOT_Y = SIDE_PANEL_Y + 91;
    public static final int SLOT_X_BUDGET_X = LEFT_SLOT_X;
    public static final int SLOT_X_BUDGET_Y = TOP_SLOT_Y;
    public static final int SLOT_Y_BUDGET_X = LEFT_SLOT_X;
    public static final int SLOT_Y_BUDGET_Y = MIDDLE_SLOT_Y;
    public static final int SLOT_Z_BUDGET_X = LEFT_SLOT_X;
    public static final int SLOT_Z_BUDGET_Y = BOTTOM_SLOT_Y;
    public static final int SLOT_RIGHT_TOP_X = RIGHT_SLOT_X;
    public static final int SLOT_RIGHT_TOP_Y = TOP_SLOT_Y;
    public static final int SLOT_RIGHT_MIDDLE_X = RIGHT_SLOT_X;
    public static final int SLOT_RIGHT_MIDDLE_Y = MIDDLE_SLOT_Y;
    public static final int SLOT_RIGHT_BOTTOM_X = RIGHT_SLOT_X;
    public static final int SLOT_RIGHT_BOTTOM_Y = BOTTOM_SLOT_Y;
    public static final int SLOT_OUTPUT_X = IMAGE_WIDTH / 2 - SLOT_SIZE / 2;
    public static final int SLOT_OUTPUT_Y = OUTPUT_ZONE_Y + 12;

    public static final int ITEM_X_BUDGET_X = SLOT_X_BUDGET_X + ITEM_SLOT_INSET;
    public static final int ITEM_X_BUDGET_Y = SLOT_X_BUDGET_Y + ITEM_SLOT_INSET;
    public static final int ITEM_Y_BUDGET_X = SLOT_Y_BUDGET_X + ITEM_SLOT_INSET;
    public static final int ITEM_Y_BUDGET_Y = SLOT_Y_BUDGET_Y + ITEM_SLOT_INSET;
    public static final int ITEM_Z_BUDGET_X = SLOT_Z_BUDGET_X + ITEM_SLOT_INSET;
    public static final int ITEM_Z_BUDGET_Y = SLOT_Z_BUDGET_Y + ITEM_SLOT_INSET;
    public static final int ITEM_RIGHT_TOP_X = SLOT_RIGHT_TOP_X + ITEM_SLOT_INSET;
    public static final int ITEM_RIGHT_TOP_Y = SLOT_RIGHT_TOP_Y + ITEM_SLOT_INSET;
    public static final int ITEM_RIGHT_MIDDLE_X = SLOT_RIGHT_MIDDLE_X + ITEM_SLOT_INSET;
    public static final int ITEM_RIGHT_MIDDLE_Y = SLOT_RIGHT_MIDDLE_Y + ITEM_SLOT_INSET;
    public static final int ITEM_RIGHT_BOTTOM_X = SLOT_RIGHT_BOTTOM_X + ITEM_SLOT_INSET;
    public static final int ITEM_RIGHT_BOTTOM_Y = SLOT_RIGHT_BOTTOM_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_X = SLOT_OUTPUT_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_Y = SLOT_OUTPUT_Y + ITEM_SLOT_INSET;

    public static final int BUTTON_SIZE = 12;
    public static final int BUTTON_ARROW_INSET = 3;
    public static final int BUTTON_ARROW_SIZE = 6;
    public static final int MODE_BUTTON_X = OUTPUT_ZONE_X + 8;
    public static final int MODE_BUTTON_Y = OUTPUT_ZONE_Y + 5;
    public static final int STANDARD_PREV_X = SLOT_OUTPUT_X - 22;
    public static final int STANDARD_NEXT_X = SLOT_OUTPUT_X + SLOT_SIZE + 10;
    public static final int STANDARD_BUTTON_Y = SLOT_OUTPUT_Y + 3;
    public static final int DESIGN_BUTTON_WIDTH = 22;
    public static final int DESIGN_BUTTON_HEIGHT = 7;
    public static final int DESIGN_BUTTON_X = IMAGE_WIDTH / 2 - DESIGN_BUTTON_WIDTH / 2;
    public static final int DESIGN_BUTTON_Y = SLOT_OUTPUT_Y + SLOT_SIZE + 6;

    public static final int AXIS_LABEL_X = BAR_ZONE_X + 7;
    public static final int AXIS_BAR_X = BAR_ZONE_X + 20;
    public static final int AXIS_BAR_WIDTH = 82;
    public static final int AXIS_BAR_HEIGHT = 8;
    public static final int AXIS_VALUE_X = AXIS_BAR_X + AXIS_BAR_WIDTH + 4;
    public static final int AXIS_ROW_X_Y = BAR_ZONE_Y + 8;
    public static final int AXIS_ROW_Y_Y = BAR_ZONE_Y + 25;
    public static final int AXIS_ROW_Z_Y = BAR_ZONE_Y + 42;

    public static final int PLAYER_INVENTORY_X = 47;
    public static final int PLAYER_INVENTORY_Y = 160;
    public static final int PLAYER_INVENTORY_ITEM_X = PLAYER_INVENTORY_X;
    public static final int PLAYER_INVENTORY_ITEM_Y = PLAYER_INVENTORY_Y;
    public static final int INVENTORY_LABEL_X = 47;
    public static final int INVENTORY_LABEL_Y = 146;

    private MetamaterialDesignTableLayout() {
    }
}
