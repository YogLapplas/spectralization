package io.github.yoglappland.spectralization.menu;

public final class ThermalSmelterLayout {
    public static final int IMAGE_WIDTH = 256;
    public static final int IMAGE_HEIGHT = 216;

    public static final int MACHINE_HEIGHT = 108;

    public static final int PROCESS_X = 0;
    public static final int PROCESS_Y = 0;
    public static final int PROCESS_WIDTH = IMAGE_WIDTH;
    public static final int PROCESS_HEIGHT = MACHINE_HEIGHT;

    public static final int PROCESS_SIDE_PANEL_WIDTH = 44;
    public static final int PROCESS_LEFT_PANEL_X = 9;
    public static final int PROCESS_RIGHT_PANEL_X = IMAGE_WIDTH - PROCESS_LEFT_PANEL_X - PROCESS_SIDE_PANEL_WIDTH;
    public static final int PROCESS_SIDE_PANEL_Y = 8;
    public static final int PROCESS_SIDE_PANEL_HEIGHT = 88;

    public static final int PROCESS_INPUT_X = 22;
    public static final int PROCESS_INPUT_Y = 30;
    public static final int PROCESS_ADDITIVE_X = 22;
    public static final int PROCESS_ADDITIVE_Y = 56;
    public static final int PROCESS_OUTPUT_X = 216;
    public static final int PROCESS_OUTPUT_Y = 30;
    public static final int PROCESS_OUTPUT_SECOND_X = 216;
    public static final int PROCESS_OUTPUT_SECOND_Y = 56;

    public static final int PROCESS_SP_BAR_X = 76;
    public static final int PROCESS_SP_BAR_Y = 32;
    public static final int PROCESS_SP_BAR_WIDTH = 14;
    public static final int PROCESS_SP_BAR_HEIGHT = 45;

    public static final int PROCESS_CHAMBER_WIDTH = 132;
    public static final int PROCESS_CHAMBER_X = (IMAGE_WIDTH - PROCESS_CHAMBER_WIDTH) / 2;
    public static final int PROCESS_CHAMBER_Y = 8;
    public static final int PROCESS_CHAMBER_HEIGHT = 88;

    public static final int PROCESS_INNER_X = PROCESS_CHAMBER_X + 4;
    public static final int PROCESS_INNER_Y = PROCESS_CHAMBER_Y + 4;
    public static final int PROCESS_INNER_WIDTH = PROCESS_CHAMBER_WIDTH - 8;
    public static final int PROCESS_INNER_HEIGHT = PROCESS_CHAMBER_HEIGHT - 8;

    public static final int PROCESS_WORK_X1 = PROCESS_INNER_X + 4;
    public static final int PROCESS_WORK_Y1 = PROCESS_INNER_Y + 4;
    public static final int PROCESS_WORK_X2 = PROCESS_INNER_X + PROCESS_INNER_WIDTH - 4;
    public static final int PROCESS_WORK_Y2 = PROCESS_INNER_Y + PROCESS_INNER_HEIGHT - 6;
    public static final int PROCESS_BEAM_Y = 42;

    public static final int PROCESS_DIVIDER_LEFT_X = 96;
    public static final int PROCESS_DIVIDER_RIGHT_X = 160;
    public static final int PROCESS_DIVIDER_TOP_Y = 16;
    public static final int PROCESS_DIVIDER_BOTTOM_Y = 82;
    public static final int PROCESS_HORIZONTAL_DIVIDER_Y = 37;

    public static final int PROCESS_STAGE_Y = 20;
    public static final int PROCESS_STAGE_SIZE = 9;
    public static final int PROCESS_STAGE_X1 = 104;
    public static final int PROCESS_STAGE_X2 = 124;
    public static final int PROCESS_STAGE_X3 = 144;

    public static final int PROCESS_MAIN_CENTER_X = 128;
    public static final int PROCESS_TEMP_BOX_X = 98;
    public static final int PROCESS_TEMP_BOX_Y = 39;
    public static final int PROCESS_TEMP_BOX_WIDTH = 60;
    public static final int PROCESS_TEMP_BOX_HEIGHT = 30;
    public static final int PROCESS_TEMP_LABEL_Y = 46;
    public static final int PROCESS_TEMP_VALUE_Y = 59;

    public static final int PROCESS_PARALLEL_CENTER_X = 173;
    public static final int PROCESS_PARALLEL_LABEL_Y = 27;
    public static final int PROCESS_PARALLEL_GRID_X = 166;
    public static final int PROCESS_PARALLEL_GRID_Y = 34;
    public static final int PROCESS_PARALLEL_GRID_SIZE = 6;
    public static final int PROCESS_PARALLEL_GRID_GAP = 2;
    public static final int PROCESS_STATUS_INDICATOR_X = 166;
    public static final int PROCESS_STATUS_INDICATOR_Y = 60;
    public static final int PROCESS_STATUS_INDICATOR_WIDTH = 14;
    public static final int PROCESS_STATUS_INDICATOR_HEIGHT = 6;
    public static final int PROCESS_STATUS_LABEL_Y = 71;

    public static final int PROCESS_PROGRESS_X = 79;
    public static final int PROCESS_PROGRESS_Y = 85;
    public static final int PROCESS_PROGRESS_SEGMENTS = 10;
    public static final int PROCESS_PROGRESS_SEGMENT_WIDTH = 8;
    public static final int PROCESS_PROGRESS_SEGMENT_HEIGHT = 4;
    public static final int PROCESS_PROGRESS_SEGMENT_GAP = 2;

    public static final int PROCESS_RECIPE_CLICK_X = PROCESS_MAIN_CENTER_X - 25;
    public static final int PROCESS_RECIPE_CLICK_Y = 73;
    public static final int PROCESS_RECIPE_CLICK_WIDTH = 50;
    public static final int PROCESS_RECIPE_CLICK_HEIGHT = 9;

    public static final int SLOT_INPUT_X = PROCESS_X + PROCESS_INPUT_X;
    public static final int SLOT_INPUT_Y = PROCESS_Y + PROCESS_INPUT_Y;
    public static final int SLOT_ADDITIVE_X = PROCESS_X + PROCESS_ADDITIVE_X;
    public static final int SLOT_ADDITIVE_Y = PROCESS_Y + PROCESS_ADDITIVE_Y;
    public static final int SLOT_OUTPUT_X = PROCESS_X + PROCESS_OUTPUT_X;
    public static final int SLOT_OUTPUT_Y = PROCESS_Y + PROCESS_OUTPUT_Y;
    public static final int SLOT_OUTPUT_SECOND_X = PROCESS_X + PROCESS_OUTPUT_SECOND_X;
    public static final int SLOT_OUTPUT_SECOND_Y = PROCESS_Y + PROCESS_OUTPUT_SECOND_Y;

    public static final int PLAYER_INVENTORY_X = 48;
    public static final int PLAYER_INVENTORY_Y = 124;
    public static final int INVENTORY_LABEL_X = 48;
    public static final int INVENTORY_LABEL_Y = 110;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;

    public static final int ITEM_INPUT_X = SLOT_INPUT_X + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_Y = SLOT_INPUT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_ADDITIVE_X = SLOT_ADDITIVE_X + ITEM_SLOT_INSET;
    public static final int ITEM_ADDITIVE_Y = SLOT_ADDITIVE_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_X = SLOT_OUTPUT_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_Y = SLOT_OUTPUT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_SECOND_X = SLOT_OUTPUT_SECOND_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_SECOND_Y = SLOT_OUTPUT_SECOND_Y + ITEM_SLOT_INSET;

    public static final int PLAYER_INVENTORY_ITEM_X = PLAYER_INVENTORY_X + ITEM_SLOT_INSET;
    public static final int PLAYER_INVENTORY_ITEM_Y = PLAYER_INVENTORY_Y + ITEM_SLOT_INSET;

    private ThermalSmelterLayout() {
    }
}
