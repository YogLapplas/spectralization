package io.github.yoglappland.spectralization.menu;

public final class SolarDopingChamberLayout {
    public static final int IMAGE_WIDTH = 278;
    public static final int IMAGE_HEIGHT = 252;
    public static final int MACHINE_HEIGHT = 146;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;

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
    public static final int HEIGHT_REGION_WIDTH = 26;
    public static final int HEIGHT_DIVIDER_X = MAIN_PANEL_X + MAIN_PANEL_WIDTH - HEIGHT_REGION_WIDTH;
    public static final int WORK_REGION_X = MAIN_PANEL_X;
    public static final int WORK_REGION_WIDTH = HEIGHT_DIVIDER_X - WORK_REGION_X;
    public static final int WORK_DIVIDER_Y = MAIN_PANEL_Y + MAIN_PANEL_HEIGHT / 2;
    public static final int LOWER_WORK_HEIGHT = MAIN_PANEL_Y + MAIN_PANEL_HEIGHT - WORK_DIVIDER_Y;

    public static final int FILTER_SLOT_X = WORK_REGION_X + (WORK_REGION_WIDTH - SLOT_SIZE) / 2;
    public static final int FILTER_SLOT_Y = MAIN_PANEL_Y + 15;
    public static final int PROCESS_SLOT_X = FILTER_SLOT_X;
    public static final int PROCESS_SLOT_Y = WORK_DIVIDER_Y + (LOWER_WORK_HEIGHT - SLOT_SIZE) / 2;

    public static final int CATALYST_CLICK_X = WORK_REGION_X + 5;
    public static final int CATALYST_CLICK_Y = MAIN_PANEL_Y + 7;
    public static final int CATALYST_CLICK_WIDTH = WORK_REGION_WIDTH - 10;
    public static final int CATALYST_CLICK_HEIGHT = WORK_DIVIDER_Y - CATALYST_CLICK_Y - 2;
    public static final int RECIPE_REGION_X = WORK_REGION_X + 5;
    public static final int RECIPE_REGION_Y = WORK_DIVIDER_Y + 2;
    public static final int RECIPE_REGION_WIDTH = WORK_REGION_WIDTH - 10;
    public static final int RECIPE_REGION_HEIGHT = MAIN_PANEL_Y + MAIN_PANEL_HEIGHT - RECIPE_REGION_Y - 7;
    public static final int RECIPE_CLICK_TOP_X = RECIPE_REGION_X;
    public static final int RECIPE_CLICK_TOP_Y = RECIPE_REGION_Y;
    public static final int RECIPE_CLICK_TOP_WIDTH = RECIPE_REGION_WIDTH;
    public static final int RECIPE_CLICK_TOP_HEIGHT = PROCESS_SLOT_Y - RECIPE_REGION_Y;
    public static final int RECIPE_CLICK_LEFT_X = RECIPE_REGION_X;
    public static final int RECIPE_CLICK_LEFT_Y = PROCESS_SLOT_Y;
    public static final int RECIPE_CLICK_LEFT_WIDTH = PROCESS_SLOT_X - RECIPE_REGION_X;
    public static final int RECIPE_CLICK_LEFT_HEIGHT = SLOT_SIZE;
    public static final int RECIPE_CLICK_RIGHT_X = PROCESS_SLOT_X + SLOT_SIZE;
    public static final int RECIPE_CLICK_RIGHT_Y = PROCESS_SLOT_Y;
    public static final int RECIPE_CLICK_RIGHT_WIDTH = RECIPE_REGION_X + RECIPE_REGION_WIDTH - RECIPE_CLICK_RIGHT_X;
    public static final int RECIPE_CLICK_RIGHT_HEIGHT = SLOT_SIZE;
    public static final int RECIPE_CLICK_BOTTOM_X = RECIPE_REGION_X;
    public static final int RECIPE_CLICK_BOTTOM_Y = PROCESS_SLOT_Y + SLOT_SIZE;
    public static final int RECIPE_CLICK_BOTTOM_WIDTH = RECIPE_REGION_WIDTH;
    public static final int RECIPE_CLICK_BOTTOM_HEIGHT = RECIPE_REGION_Y + RECIPE_REGION_HEIGHT - RECIPE_CLICK_BOTTOM_Y;

    public static final int PARTICLE_AREA_X = WORK_REGION_X + 5;
    public static final int PARTICLE_AREA_Y = MAIN_PANEL_Y + 7;
    public static final int PARTICLE_AREA_WIDTH = WORK_REGION_WIDTH - 10;
    public static final int PARTICLE_AREA_HEIGHT = MAIN_PANEL_HEIGHT - 14;

    public static final int BAR_WIDTH = 10;
    public static final int BAR_HEIGHT = 72;
    public static final int ENERGY_BAR_X = LEFT_PANEL_X + (SIDE_PANEL_WIDTH - BAR_WIDTH) / 2;
    public static final int ENERGY_BAR_Y = SIDE_PANEL_Y + 28;
    public static final int HEIGHT_BAR_X = HEIGHT_DIVIDER_X + (HEIGHT_REGION_WIDTH - BAR_WIDTH) / 2;
    public static final int HEIGHT_BAR_Y = MAIN_PANEL_Y + 24;

    public static final int PIE_CENTER_X = RIGHT_PANEL_X + SIDE_PANEL_WIDTH / 2;
    public static final int PIE_CENTER_Y = SIDE_PANEL_Y + 58;
    public static final int PIE_RADIUS = 18;
    public static final int PIE_INNER_RADIUS = 8;
    public static final int PIE_LABEL_Y = PIE_CENTER_Y + PIE_RADIUS + 12;

    public static final int STATUS_STRIP_X = RIGHT_PANEL_X + 10;
    public static final int STATUS_STRIP_Y = SIDE_PANEL_Y + 100;
    public static final int STATUS_STRIP_WIDTH = SIDE_PANEL_WIDTH - 20;
    public static final int STATUS_STRIP_HEIGHT = 6;

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

    public static final int ITEM_PROCESS_X = PROCESS_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_PROCESS_Y = PROCESS_SLOT_Y + ITEM_SLOT_INSET;
    public static final int ITEM_FILTER_X = FILTER_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_FILTER_Y = FILTER_SLOT_Y + ITEM_SLOT_INSET;

    private SolarDopingChamberLayout() {
    }
}
