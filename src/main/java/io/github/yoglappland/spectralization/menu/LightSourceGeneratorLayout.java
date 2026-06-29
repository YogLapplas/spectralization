package io.github.yoglappland.spectralization.menu;

public final class LightSourceGeneratorLayout {
    public static final int IMAGE_WIDTH = 244;
    public static final int IMAGE_HEIGHT = 232;
    public static final int MACHINE_HEIGHT = 126;

    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;

    public static final int MAIN_PANEL_X = 34;
    public static final int MAIN_PANEL_Y = 12;
    public static final int MAIN_PANEL_WIDTH = 176;
    public static final int MAIN_PANEL_HEIGHT = 108;

    public static final int ENERGY_BAR_WIDTH = 12;
    public static final int ENERGY_BAR_HEIGHT = 64;
    public static final int ENERGY_BAR_X = MAIN_PANEL_X + 45;
    public static final int ENERGY_BAR_Y = MAIN_PANEL_Y + 22;

    public static final int LIGHTNING_CENTER_X = MAIN_PANEL_X + MAIN_PANEL_WIDTH / 2;
    public static final int LIGHTNING_CENTER_Y = MAIN_PANEL_Y + MAIN_PANEL_HEIGHT / 2;
    public static final int LIGHTNING_WIDTH = 28;
    public static final int LIGHTNING_HEIGHT = 28;
    public static final int RECIPE_CLICK_X = LIGHTNING_CENTER_X - LIGHTNING_WIDTH / 2 - 4;
    public static final int RECIPE_CLICK_Y = LIGHTNING_CENTER_Y - LIGHTNING_HEIGHT / 2 - 4;
    public static final int RECIPE_CLICK_WIDTH = LIGHTNING_WIDTH + 8;
    public static final int RECIPE_CLICK_HEIGHT = LIGHTNING_HEIGHT + 8;

    public static final int SOURCE_SLOT_X = MAIN_PANEL_X + MAIN_PANEL_WIDTH - SLOT_SIZE - 45;
    public static final int SOURCE_SLOT_Y = MAIN_PANEL_Y + (MAIN_PANEL_HEIGHT - SLOT_SIZE) / 2;
    public static final int ITEM_SOURCE_X = SOURCE_SLOT_X + ITEM_SLOT_INSET;
    public static final int ITEM_SOURCE_Y = SOURCE_SLOT_Y + ITEM_SLOT_INSET;

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

    private LightSourceGeneratorLayout() {
    }
}
