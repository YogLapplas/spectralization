package io.github.yoglappland.spectralization.menu;

public final class BasicLithographyMachineLayout {
    public static final int IMAGE_WIDTH = 278;
    public static final int IMAGE_HEIGHT = 252;
    public static final int MACHINE_HEIGHT = 146;

    public static final int PROCESS_X = 0;
    public static final int PROCESS_Y = 0;
    public static final int PROCESS_WIDTH = IMAGE_WIDTH;
    public static final int PROCESS_HEIGHT = MACHINE_HEIGHT;

    public static final int SIDE_PANEL_WIDTH = 52;
    public static final int LEFT_PANEL_X = 18;
    public static final int RIGHT_PANEL_X = IMAGE_WIDTH - LEFT_PANEL_X - SIDE_PANEL_WIDTH;
    public static final int SIDE_PANEL_Y = 10;
    public static final int SIDE_PANEL_HEIGHT = 126;

    public static final int CHAMBER_WIDTH = 134;
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
    public static final int MAIN_SIDE_REGION_WIDTH = 24;
    public static final int MAIN_LEFT_DIVIDER_X = MAIN_PANEL_X + MAIN_SIDE_REGION_WIDTH;
    public static final int MAIN_RIGHT_DIVIDER_X = MAIN_PANEL_X + MAIN_PANEL_WIDTH - MAIN_SIDE_REGION_WIDTH;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;
    public static final int SIDE_SLOT_GAP = 4;
    public static final int SIDE_GRID_X_INSET = 6;
    public static final int SIDE_GRID_TOP_Y = 47;
    public static final int LEFT_ITEM_INPUT_0_X = LEFT_PANEL_X + SIDE_GRID_X_INSET;
    public static final int LEFT_ITEM_INPUT_1_X = LEFT_ITEM_INPUT_0_X + SLOT_SIZE + SIDE_SLOT_GAP;
    public static final int LEFT_ITEM_INPUT_TOP_Y = SIDE_GRID_TOP_Y;
    public static final int LEFT_ITEM_INPUT_BOTTOM_Y = LEFT_ITEM_INPUT_TOP_Y + SLOT_SIZE + SIDE_SLOT_GAP;
    public static final int RIGHT_ITEM_OUTPUT_0_X = RIGHT_PANEL_X + SIDE_GRID_X_INSET;
    public static final int RIGHT_ITEM_OUTPUT_1_X = RIGHT_ITEM_OUTPUT_0_X + SLOT_SIZE + SIDE_SLOT_GAP;
    public static final int RIGHT_ITEM_OUTPUT_TOP_Y = SIDE_GRID_TOP_Y;
    public static final int RIGHT_ITEM_OUTPUT_BOTTOM_Y = RIGHT_ITEM_OUTPUT_TOP_Y + SLOT_SIZE + SIDE_SLOT_GAP;

    public static final int TEMPLATE_SLOT_GAP = 8;
    public static final int TEMPLATE_PAIR_WIDTH = SLOT_SIZE * 2 + TEMPLATE_SLOT_GAP;
    public static final int TEMPLATE_INPUT_Y = 38;
    public static final int TEMPLATE_OUTPUT_Y = 83;
    public static final int TEMPLATE_LEFT_X = IMAGE_WIDTH / 2 - TEMPLATE_PAIR_WIDTH / 2;
    public static final int TEMPLATE_RIGHT_X = TEMPLATE_LEFT_X + SLOT_SIZE + TEMPLATE_SLOT_GAP;
    public static final int TEMPLATE_CONNECTOR_WIDTH = 46;
    public static final int TEMPLATE_CONNECTOR_X = IMAGE_WIDTH / 2 - TEMPLATE_CONNECTOR_WIDTH / 2;
    public static final int TEMPLATE_INPUT_LINE_Y = TEMPLATE_INPUT_Y + SLOT_SIZE + 4;
    public static final int TEMPLATE_OUTPUT_LINE_Y = TEMPLATE_OUTPUT_Y - 5;

    public static final int BAR_WIDTH = 10;
    public static final int BAR_HEIGHT = 62;
    public static final int ENERGY_BAR_X = MAIN_PANEL_X + (MAIN_SIDE_REGION_WIDTH - BAR_WIDTH) / 2;
    public static final int ENERGY_BAR_Y = MAIN_PANEL_Y + 23;

    public static final int OPTICAL_BAR_X = MAIN_RIGHT_DIVIDER_X + (MAIN_SIDE_REGION_WIDTH - BAR_WIDTH) / 2;
    public static final int OPTICAL_BAR_Y = ENERGY_BAR_Y;

    public static final int RECIPE_ARROW_X = IMAGE_WIDTH / 2 - 30;
    public static final int RECIPE_ARROW_Y = MAIN_PANEL_Y + 45;
    public static final int RECIPE_ARROW_WIDTH = 60;
    public static final int RECIPE_ARROW_HEIGHT = 13;
    public static final int CENTER_PROGRESS_AREA_Y = MAIN_PANEL_Y + MAIN_PANEL_HEIGHT - 22;
    public static final int PROGRESS_BAR_WIDTH = 48;
    public static final int PROGRESS_BAR_HEIGHT = 5;
    public static final int PROGRESS_BAR_X = IMAGE_WIDTH / 2 - PROGRESS_BAR_WIDTH / 2;
    public static final int PROGRESS_BAR_Y = CENTER_PROGRESS_AREA_Y + 10;
    public static final int PROGRESS_SEGMENTS = 6;

    public static final int RULE_BUTTON_X = -17;
    public static final int RULE_BUTTON_Y = SIDE_PANEL_Y + 12;
    public static final int RULE_BUTTON_WIDTH = 18;
    public static final int RULE_BUTTON_HEIGHT = 18;

    public static final int PLAYER_INVENTORY_X = (IMAGE_WIDTH - 9 * SLOT_SIZE) / 2;
    public static final int PLAYER_INVENTORY_Y = 160;
    public static final int PLAYER_INVENTORY_ITEM_X = PLAYER_INVENTORY_X;
    public static final int PLAYER_INVENTORY_ITEM_Y = PLAYER_INVENTORY_Y;
    public static final int INVENTORY_LABEL_X = PLAYER_INVENTORY_X;
    public static final int INVENTORY_LABEL_Y = 146;

    public static final int ITEM_TEMPLATE_INPUT_A_X = TEMPLATE_LEFT_X + ITEM_SLOT_INSET;
    public static final int ITEM_TEMPLATE_INPUT_A_Y = TEMPLATE_INPUT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_TEMPLATE_INPUT_B_X = TEMPLATE_RIGHT_X + ITEM_SLOT_INSET;
    public static final int ITEM_TEMPLATE_INPUT_B_Y = TEMPLATE_INPUT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_TEMPLATE_OUTPUT_A_X = TEMPLATE_LEFT_X + ITEM_SLOT_INSET;
    public static final int ITEM_TEMPLATE_OUTPUT_A_Y = TEMPLATE_OUTPUT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_TEMPLATE_OUTPUT_B_X = TEMPLATE_RIGHT_X + ITEM_SLOT_INSET;
    public static final int ITEM_TEMPLATE_OUTPUT_B_Y = TEMPLATE_OUTPUT_Y + ITEM_SLOT_INSET;

    public static final int ITEM_INPUT_0_X = LEFT_ITEM_INPUT_0_X + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_0_Y = LEFT_ITEM_INPUT_TOP_Y + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_1_X = LEFT_ITEM_INPUT_1_X + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_1_Y = LEFT_ITEM_INPUT_TOP_Y + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_2_X = LEFT_ITEM_INPUT_0_X + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_2_Y = LEFT_ITEM_INPUT_BOTTOM_Y + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_3_X = LEFT_ITEM_INPUT_1_X + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_3_Y = LEFT_ITEM_INPUT_BOTTOM_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_0_X = RIGHT_ITEM_OUTPUT_0_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_0_Y = RIGHT_ITEM_OUTPUT_TOP_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_1_X = RIGHT_ITEM_OUTPUT_1_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_1_Y = RIGHT_ITEM_OUTPUT_TOP_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_2_X = RIGHT_ITEM_OUTPUT_0_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_2_Y = RIGHT_ITEM_OUTPUT_BOTTOM_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_3_X = RIGHT_ITEM_OUTPUT_1_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_3_Y = RIGHT_ITEM_OUTPUT_BOTTOM_Y + ITEM_SLOT_INSET;

    private BasicLithographyMachineLayout() {
    }
}
