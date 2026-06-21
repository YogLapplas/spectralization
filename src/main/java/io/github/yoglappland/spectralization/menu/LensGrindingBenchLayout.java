package io.github.yoglappland.spectralization.menu;

public final class LensGrindingBenchLayout {
    public static final int IMAGE_WIDTH = 256;
    public static final int IMAGE_HEIGHT = 216;
    public static final int MACHINE_HEIGHT = 108;

    public static final int PROCESS_X = 0;
    public static final int PROCESS_Y = 0;
    public static final int PROCESS_WIDTH = IMAGE_WIDTH;
    public static final int PROCESS_HEIGHT = MACHINE_HEIGHT;

    public static final int SIDE_PANEL_WIDTH = 44;
    public static final int LEFT_PANEL_X = 9;
    public static final int RIGHT_PANEL_X = IMAGE_WIDTH - LEFT_PANEL_X - SIDE_PANEL_WIDTH;
    public static final int SIDE_PANEL_Y = 8;
    public static final int SIDE_PANEL_HEIGHT = 88;
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
    public static final int MAIN_PANEL_GAP = 4;
    public static final int RIGHT_ZONE_WIDTH = 52;
    public static final int LEFT_ZONE_WIDTH = MAIN_PANEL_WIDTH - RIGHT_ZONE_WIDTH - MAIN_PANEL_GAP;
    public static final int LEFT_TOP_ZONE_X = MAIN_PANEL_X;
    public static final int LEFT_TOP_ZONE_Y = MAIN_PANEL_Y;
    public static final int LEFT_TOP_ZONE_WIDTH = LEFT_ZONE_WIDTH;
    public static final int LEFT_TOP_ZONE_HEIGHT = (MAIN_PANEL_HEIGHT - MAIN_PANEL_GAP) / 2;
    public static final int LEFT_BOTTOM_ZONE_X = MAIN_PANEL_X;
    public static final int LEFT_BOTTOM_ZONE_Y = LEFT_TOP_ZONE_Y + LEFT_TOP_ZONE_HEIGHT + MAIN_PANEL_GAP;
    public static final int LEFT_BOTTOM_ZONE_WIDTH = LEFT_ZONE_WIDTH;
    public static final int LEFT_BOTTOM_ZONE_HEIGHT = LEFT_TOP_ZONE_HEIGHT;
    public static final int RIGHT_ZONE_X = MAIN_PANEL_X + LEFT_ZONE_WIDTH + MAIN_PANEL_GAP;
    public static final int RIGHT_ZONE_Y = MAIN_PANEL_Y;
    public static final int RIGHT_ZONE_HEIGHT = MAIN_PANEL_HEIGHT;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;
    public static final int SLOT_BLANK_X = LEFT_PANEL_CENTER_X - SLOT_SIZE / 2;
    public static final int SLOT_BLANK_Y = 17;
    public static final int SLOT_TOOL_X = SLOT_BLANK_X;
    public static final int SLOT_TOOL_Y = 44;
    public static final int SLOT_REFERENCE_X = SLOT_BLANK_X;
    public static final int SLOT_REFERENCE_Y = 71;
    public static final int SLOT_OUTPUT_X = RIGHT_PANEL_CENTER_X - SLOT_SIZE / 2;
    public static final int SLOT_OUTPUT_Y = 42;

    public static final int ITEM_BLANK_X = SLOT_BLANK_X + ITEM_SLOT_INSET;
    public static final int ITEM_BLANK_Y = SLOT_BLANK_Y + ITEM_SLOT_INSET;
    public static final int ITEM_TOOL_X = SLOT_TOOL_X + ITEM_SLOT_INSET;
    public static final int ITEM_TOOL_Y = SLOT_TOOL_Y + ITEM_SLOT_INSET;
    public static final int ITEM_REFERENCE_X = SLOT_REFERENCE_X + ITEM_SLOT_INSET;
    public static final int ITEM_REFERENCE_Y = SLOT_REFERENCE_Y + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_X = SLOT_OUTPUT_X + ITEM_SLOT_INSET;
    public static final int ITEM_OUTPUT_Y = SLOT_OUTPUT_Y + ITEM_SLOT_INSET;

    public static final int KIND_ZONE_X = LEFT_TOP_ZONE_X;
    public static final int KIND_ZONE_Y = LEFT_TOP_ZONE_Y;
    public static final int KIND_ZONE_WIDTH = LEFT_TOP_ZONE_WIDTH;
    public static final int KIND_ZONE_HEIGHT = LEFT_TOP_ZONE_HEIGHT;
    public static final int KIND_BUTTON_SIZE = 12;
    public static final int BUTTON_ARROW_INSET = 3;
    public static final int BUTTON_ARROW_SIZE = 6;
    public static final int KIND_CENTER_X = KIND_ZONE_X + KIND_ZONE_WIDTH / 2;
    public static final int KIND_CENTER_Y = KIND_ZONE_Y + KIND_ZONE_HEIGHT / 2;
    public static final int KIND_PREV_X = KIND_CENTER_X - 21;
    public static final int KIND_NEXT_X = KIND_CENTER_X + 9;
    public static final int KIND_BUTTON_Y = KIND_CENTER_Y - KIND_BUTTON_SIZE / 2;
    public static final int KIND_CODE_Y = KIND_ZONE_Y + 26;

    public static final int TARGET_ZONE_X = LEFT_BOTTOM_ZONE_X;
    public static final int TARGET_ZONE_Y = LEFT_BOTTOM_ZONE_Y;
    public static final int TARGET_ZONE_WIDTH = LEFT_BOTTOM_ZONE_WIDTH;
    public static final int TARGET_ZONE_HEIGHT = LEFT_BOTTOM_ZONE_HEIGHT;
    public static final int TARGET_BUTTON_SIZE = 12;
    public static final int TARGET_CENTER_X = TARGET_ZONE_X + TARGET_ZONE_WIDTH / 2;
    public static final int TARGET_CENTER_Y = TARGET_ZONE_Y + TARGET_ZONE_HEIGHT / 2;
    public static final int TARGET_PREV_X = TARGET_CENTER_X - 25;
    public static final int TARGET_NEXT_X = TARGET_CENTER_X + 13;
    public static final int TARGET_BUTTON_Y = TARGET_CENTER_Y - TARGET_BUTTON_SIZE / 2;
    public static final int TARGET_BOX_WIDTH = 24;
    public static final int TARGET_BOX_X = TARGET_CENTER_X - TARGET_BOX_WIDTH / 2;
    public static final int TARGET_BOX_HEIGHT = 8;
    public static final int TARGET_BOX_Y = TARGET_CENTER_Y - TARGET_BOX_HEIGHT / 2;

    public static final int METER_SCALE_LABEL_WIDTH = 10;
    public static final int METER_SCALE_LABEL_HEIGHT = 5;
    public static final int METER_SCALE_LABEL_GAP = 4;
    public static final int METER_SCALE_TICK_LONG = 3;
    public static final int METER_SCALE_TICK_SHORT = 2;
    public static final int QUALITY_BAR_WIDTH = 8;
    public static final int QUALITY_BAR_HEIGHT = 58;
    public static final int QUALITY_BAR_X = RIGHT_ZONE_X + METER_SCALE_LABEL_WIDTH + METER_SCALE_LABEL_GAP + 1;
    public static final int QUALITY_BAR_Y = RIGHT_ZONE_Y + 7;
    public static final int RANGE_WIDTH = QUALITY_BAR_WIDTH;
    public static final int RANGE_HEIGHT = QUALITY_BAR_HEIGHT;
    public static final int RANGE_X = RIGHT_ZONE_X + RIGHT_ZONE_WIDTH - METER_SCALE_LABEL_WIDTH - METER_SCALE_LABEL_GAP - RANGE_WIDTH - 1;
    public static final int RANGE_Y = QUALITY_BAR_Y;
    public static final int QUALITY_SCALE_LABEL_X = QUALITY_BAR_X - METER_SCALE_LABEL_GAP - METER_SCALE_LABEL_WIDTH;
    public static final int RANGE_SCALE_LABEL_X = RANGE_X + RANGE_WIDTH + METER_SCALE_LABEL_GAP;
    public static final int SCALE_TOP_LABEL_Y = QUALITY_BAR_Y - 2;
    public static final int SCALE_BOTTOM_LABEL_Y = QUALITY_BAR_Y + QUALITY_BAR_HEIGHT - 3;

    public static final int PLAYER_INVENTORY_X = 48;
    public static final int PLAYER_INVENTORY_Y = 124;
    public static final int PLAYER_INVENTORY_ITEM_X = PLAYER_INVENTORY_X + ITEM_SLOT_INSET;
    public static final int PLAYER_INVENTORY_ITEM_Y = PLAYER_INVENTORY_Y + ITEM_SLOT_INSET;
    public static final int INVENTORY_LABEL_X = 48;
    public static final int INVENTORY_LABEL_Y = 110;

    private LensGrindingBenchLayout() {
    }
}
