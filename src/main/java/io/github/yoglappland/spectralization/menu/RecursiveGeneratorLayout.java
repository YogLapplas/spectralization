package io.github.yoglappland.spectralization.menu;

public final class RecursiveGeneratorLayout {
    public static final int IMAGE_WIDTH = 264;
    public static final int IMAGE_HEIGHT = 232;
    public static final int MACHINE_HEIGHT = 126;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;

    public static final int MAIN_PANEL_X = 28;
    public static final int MAIN_PANEL_Y = 12;
    public static final int MAIN_PANEL_WIDTH = 208;
    public static final int MAIN_PANEL_HEIGHT = 108;

    public static final int LEFT_REGION_X = MAIN_PANEL_X + 8;
    public static final int LEFT_REGION_Y = MAIN_PANEL_Y + 8;
    public static final int LEFT_REGION_WIDTH = 52;
    public static final int LEFT_REGION_HEIGHT = MAIN_PANEL_HEIGHT - 16;

    public static final int CORE_REGION_X = MAIN_PANEL_X + 68;
    public static final int CORE_REGION_Y = MAIN_PANEL_Y + 8;
    public static final int CORE_REGION_WIDTH = 54;
    public static final int CORE_REGION_HEIGHT = MAIN_PANEL_HEIGHT - 16;

    public static final int TIME_REGION_X = MAIN_PANEL_X + 130;
    public static final int TIME_REGION_Y = MAIN_PANEL_Y + 8;
    public static final int TIME_REGION_WIDTH = 70;
    public static final int TIME_REGION_HEIGHT = MAIN_PANEL_HEIGHT - 16;

    public static final int ENERGY_BAR_WIDTH = 12;
    public static final int ENERGY_BAR_HEIGHT = 62;
    public static final int ENERGY_BAR_X = LEFT_REGION_X + 8;
    public static final int ENERGY_BAR_Y = LEFT_REGION_Y + 15;

    public static final int INPUT_SLOT_X = LEFT_REGION_X + 28;
    public static final int INPUT_SLOT_Y = LEFT_REGION_Y + 37;
    public static final int ITEM_INPUT_X = INPUT_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_INPUT_Y = INPUT_SLOT_Y + ITEM_SLOT_INSET;

    public static final int CORE_CENTER_X = CORE_REGION_X + CORE_REGION_WIDTH / 2;
    public static final int CORE_CENTER_Y = CORE_REGION_Y + CORE_REGION_HEIGHT / 2;
    public static final int CORE_SIZE = 42;

    public static final int TIME_CHART_X = TIME_REGION_X + 4;
    public static final int TIME_CHART_Y = TIME_REGION_Y + 8;
    public static final int TIME_CHART_WIDTH = 62;
    public static final int TIME_CHART_HEIGHT = 76;
    public static final int TIME_CHART_AXIS_INSET_X = 5;
    public static final int TIME_CHART_AXIS_INSET_BOTTOM = 6;
    public static final int TIME_CHART_COLUMN_WIDTH = 3;
    public static final int TIME_CHART_COLUMN_GAP = 0;

    public static final int PLAYER_INVENTORY_X = (IMAGE_WIDTH - 9 * SLOT_SIZE) / 2;
    public static final int PLAYER_INVENTORY_Y = 142;
    public static final int PLAYER_INVENTORY_PANEL_X = PLAYER_INVENTORY_X - 6;
    public static final int PLAYER_INVENTORY_PANEL_Y = PLAYER_INVENTORY_Y - 6;
    public static final int PLAYER_INVENTORY_PANEL_WIDTH = 9 * SLOT_SIZE + 12;
    public static final int PLAYER_INVENTORY_PANEL_HEIGHT = 88;
    public static final int PLAYER_INVENTORY_ITEM_X = PLAYER_INVENTORY_X + ITEM_SLOT_INSET;
    public static final int PLAYER_INVENTORY_ITEM_Y = PLAYER_INVENTORY_Y + ITEM_SLOT_INSET;
    public static final int INVENTORY_LABEL_X = PLAYER_INVENTORY_X;
    public static final int INVENTORY_LABEL_Y = 128;

    private RecursiveGeneratorLayout() {
    }
}
