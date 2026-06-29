package io.github.yoglappland.spectralization.menu;

public final class PhotothermalGeneratorLayout {
    public static final int IMAGE_WIDTH = 278;
    public static final int IMAGE_HEIGHT = 252;
    public static final int MACHINE_HEIGHT = 146;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;

    public static final int MAIN_PANEL_X = 34;
    public static final int MAIN_PANEL_Y = 12;
    public static final int MAIN_PANEL_WIDTH = 210;
    public static final int MAIN_PANEL_HEIGHT = 126;
    public static final int MAIN_INNER_X = MAIN_PANEL_X + 5;
    public static final int MAIN_INNER_Y = MAIN_PANEL_Y + 5;
    public static final int MAIN_INNER_WIDTH = MAIN_PANEL_WIDTH - 10;
    public static final int MAIN_INNER_HEIGHT = MAIN_PANEL_HEIGHT - 10;

    public static final int LEFT_REGION_X = MAIN_INNER_X;
    public static final int LEFT_REGION_Y = MAIN_INNER_Y;
    public static final int LEFT_REGION_WIDTH = 145;
    public static final int LEFT_REGION_HEIGHT = MAIN_INNER_HEIGHT;
    public static final int REGION_DIVIDER_X = LEFT_REGION_X + LEFT_REGION_WIDTH;
    public static final int RIGHT_REGION_X = REGION_DIVIDER_X + 1;
    public static final int RIGHT_REGION_Y = MAIN_INNER_Y;
    public static final int RIGHT_REGION_WIDTH = MAIN_INNER_X + MAIN_INNER_WIDTH - RIGHT_REGION_X;
    public static final int RIGHT_REGION_HEIGHT = MAIN_INNER_HEIGHT;

    public static final int ENERGY_BAR_WIDTH = 12;
    public static final int ENERGY_BAR_HEIGHT = 72;
    public static final int ENERGY_BAR_X = LEFT_REGION_X + 15;
    public static final int ENERGY_BAR_Y = LEFT_REGION_Y + (LEFT_REGION_HEIGHT - ENERGY_BAR_HEIGHT) / 2;

    public static final int LIGHT_BAR_WIDTH = 12;
    public static final int LIGHT_BAR_HEIGHT = 72;
    public static final int LIGHT_BAR_X = RIGHT_REGION_X + (RIGHT_REGION_WIDTH - LIGHT_BAR_WIDTH) / 2;
    public static final int LIGHT_BAR_Y = RIGHT_REGION_Y + (RIGHT_REGION_HEIGHT - LIGHT_BAR_HEIGHT) / 2;

    public static final int BOLT_CENTER_X = LEFT_REGION_X + 71;
    public static final int BOLT_CENTER_Y = LEFT_REGION_Y + LEFT_REGION_HEIGHT / 2;
    public static final int BOLT_WIDTH = 28;
    public static final int BOLT_HEIGHT = 28;
    public static final int RECIPE_CLICK_X = BOLT_CENTER_X - BOLT_WIDTH / 2 - 4;
    public static final int RECIPE_CLICK_Y = BOLT_CENTER_Y - BOLT_HEIGHT / 2 - 4;
    public static final int RECIPE_CLICK_WIDTH = BOLT_WIDTH + 8;
    public static final int RECIPE_CLICK_HEIGHT = BOLT_HEIGHT + 8;

    public static final int FUEL_SLOT_X = LEFT_REGION_X + LEFT_REGION_WIDTH - SLOT_SIZE - 18;
    public static final int FUEL_SLOT_Y = LEFT_REGION_Y + (LEFT_REGION_HEIGHT - SLOT_SIZE) / 2;
    public static final int ITEM_FUEL_X = FUEL_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_FUEL_Y = FUEL_SLOT_Y + ITEM_SLOT_INSET;

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

    private PhotothermalGeneratorLayout() {
    }
}
