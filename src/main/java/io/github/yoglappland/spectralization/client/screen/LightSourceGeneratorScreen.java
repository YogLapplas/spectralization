package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.PhotonicGradientGeneratorBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.menu.LightSourceGeneratorLayout;
import io.github.yoglappland.spectralization.menu.LightSourceGeneratorMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class LightSourceGeneratorScreen extends SpectralMachineScreen<LightSourceGeneratorMenu> {
    private static final int SOURCE_COLOR = 0xFFF1B94A;
    private static final int SOURCE_GLOW = 0xFFFFE9A8;
    private static final ResourceLocation LIGHTNING_ICON =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/gui/lightening.png");

    public LightSourceGeneratorScreen(LightSourceGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(
                menu,
                playerInventory,
                title,
                "light_source_generator",
                LightSourceGeneratorLayout.IMAGE_WIDTH,
                LightSourceGeneratorLayout.IMAGE_HEIGHT,
                LightSourceGeneratorLayout.INVENTORY_LABEL_X,
                LightSourceGeneratorLayout.INVENTORY_LABEL_Y
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMachineTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        drawMainPanel(graphics);
        drawEnergyBar(graphics);
        drawLightning(graphics);
        slotFrame(
                graphics,
                leftPos + LightSourceGeneratorLayout.SOURCE_SLOT_X,
                topPos + LightSourceGeneratorLayout.SOURCE_SLOT_Y,
                SOURCE_COLOR,
                data(PhotonicGradientGeneratorBlockEntity.DATA_SOURCE_COUNT) > 0
        );
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void renderMachineTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            return;
        }

        List<Component> tooltip = tooltipAt(mouseX, mouseY);

        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        if (insideLocal(mouseX, mouseY,
                LightSourceGeneratorLayout.ENERGY_BAR_X - 2,
                LightSourceGeneratorLayout.ENERGY_BAR_Y - 2,
                LightSourceGeneratorLayout.ENERGY_BAR_WIDTH + 4,
                LightSourceGeneratorLayout.ENERGY_BAR_HEIGHT + 4)) {
            return energyTooltip();
        }

        if (insideLocal(mouseX, mouseY,
                LightSourceGeneratorLayout.RECIPE_CLICK_X,
                LightSourceGeneratorLayout.RECIPE_CLICK_Y,
                LightSourceGeneratorLayout.RECIPE_CLICK_WIDTH,
                LightSourceGeneratorLayout.RECIPE_CLICK_HEIGHT)) {
            return List.of(tt("tooltip.recipe"));
        }

        return List.of();
    }

    private List<Component> energyTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt(
                "tooltip.energy",
                data(PhotonicGradientGeneratorBlockEntity.DATA_ENERGY),
                data(PhotonicGradientGeneratorBlockEntity.DATA_CAPACITY)
        ));
        tooltip.add(tt("tooltip.output", data(PhotonicGradientGeneratorBlockEntity.DATA_OUTPUT)));
        tooltip.add(tt("tooltip.remaining", formatSeconds(data(PhotonicGradientGeneratorBlockEntity.DATA_BURN_REMAINING))));
        return tooltip;
    }

    private void drawMainPanel(GuiGraphics graphics) {
        drawPanel(
                graphics,
                leftPos + LightSourceGeneratorLayout.MAIN_PANEL_X,
                topPos + LightSourceGeneratorLayout.MAIN_PANEL_Y,
                LightSourceGeneratorLayout.MAIN_PANEL_WIDTH,
                LightSourceGeneratorLayout.MAIN_PANEL_HEIGHT
        );

        graphics.fill(
                leftPos + LightSourceGeneratorLayout.MAIN_PANEL_X + 4,
                topPos + LightSourceGeneratorLayout.MAIN_PANEL_Y + 4,
                leftPos + LightSourceGeneratorLayout.MAIN_PANEL_X + LightSourceGeneratorLayout.MAIN_PANEL_WIDTH - 4,
                topPos + LightSourceGeneratorLayout.MAIN_PANEL_Y + LightSourceGeneratorLayout.MAIN_PANEL_HEIGHT - 4,
                ThermalSmelterUiSkin.withAlpha(SOURCE_GLOW, 20)
        );
    }

    private void drawEnergyBar(GuiGraphics graphics) {
        drawVerticalGauge(
                graphics,
                leftPos + LightSourceGeneratorLayout.ENERGY_BAR_X,
                topPos + LightSourceGeneratorLayout.ENERGY_BAR_Y,
                LightSourceGeneratorLayout.ENERGY_BAR_WIDTH,
                LightSourceGeneratorLayout.ENERGY_BAR_HEIGHT,
                data(PhotonicGradientGeneratorBlockEntity.DATA_ENERGY),
                Math.max(1, data(PhotonicGradientGeneratorBlockEntity.DATA_CAPACITY)),
                ThermalSmelterUiSkin.STATUS_READY,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT
        );
    }

    private void drawLightning(GuiGraphics graphics) {
        int x = leftPos + LightSourceGeneratorLayout.LIGHTNING_CENTER_X - LightSourceGeneratorLayout.LIGHTNING_WIDTH / 2;
        int y = topPos + LightSourceGeneratorLayout.LIGHTNING_CENTER_Y - LightSourceGeneratorLayout.LIGHTNING_HEIGHT / 2;
        float scaleX = LightSourceGeneratorLayout.LIGHTNING_WIDTH / 16.0F;
        float scaleY = LightSourceGeneratorLayout.LIGHTNING_HEIGHT / 16.0F;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.blit(LIGHTNING_ICON, 0, 0, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        drawPanel(
                graphics,
                leftPos + LightSourceGeneratorLayout.PLAYER_INVENTORY_PANEL_X,
                topPos + LightSourceGeneratorLayout.PLAYER_INVENTORY_PANEL_Y,
                LightSourceGeneratorLayout.PLAYER_INVENTORY_PANEL_WIDTH,
                LightSourceGeneratorLayout.PLAYER_INVENTORY_PANEL_HEIGHT
        );

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        leftPos + LightSourceGeneratorLayout.PLAYER_INVENTORY_X
                                + column * LightSourceGeneratorLayout.SLOT_SIZE,
                        topPos + LightSourceGeneratorLayout.PLAYER_INVENTORY_Y
                                + row * LightSourceGeneratorLayout.SLOT_SIZE
                );
            }
        }

        int hotbarY = LightSourceGeneratorLayout.PLAYER_INVENTORY_Y + 58;
        graphics.fill(
                leftPos + LightSourceGeneratorLayout.PLAYER_INVENTORY_X,
                topPos + hotbarY - 4,
                leftPos + LightSourceGeneratorLayout.PLAYER_INVENTORY_X + 9 * LightSourceGeneratorLayout.SLOT_SIZE,
                topPos + hotbarY - 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 88)
        );

        for (int column = 0; column < 9; column++) {
            ceramicSlot(
                    graphics,
                    leftPos + LightSourceGeneratorLayout.PLAYER_INVENTORY_X
                            + column * LightSourceGeneratorLayout.SLOT_SIZE,
                    topPos + hotbarY
            );
        }
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth,
                topPos + LightSourceGeneratorLayout.MACHINE_HEIGHT, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1,
                topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1,
                topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 8, topPos + LightSourceGeneratorLayout.MACHINE_HEIGHT + 1,
                leftPos + imageWidth - 8, topPos + LightSourceGeneratorLayout.MACHINE_HEIGHT + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 128));
    }

    private static void drawVerticalGauge(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int value,
            int max,
            int color,
            int glintColor
    ) {
        int fillHeight = max <= 0 ? 0 : Math.round(height * Math.max(0, Math.min(value, max)) / (float) max);
        insetPanel(graphics, x - 1, y - 1, width + 2, height + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 92));
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 78));

        if (fillHeight > 0) {
            int fillTop = y + height - fillHeight;
            graphics.fill(x, fillTop, x + width, y + height,
                    ThermalSmelterUiSkin.withAlpha(color, 126 + Math.round(76 * fillHeight / (float) Math.max(1, height))));
            graphics.fill(x + 2, fillTop, x + width - 1, y + height,
                    ThermalSmelterUiSkin.withAlpha(glintColor, 46));
        }
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.PANEL);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 40));
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, int color, boolean active) {
        ceramicSlot(graphics, x, y);
        if (active) {
            graphics.fill(x + LightSourceGeneratorLayout.SLOT_SIZE - 2, y + 2,
                    x + LightSourceGeneratorLayout.SLOT_SIZE - 1, y + LightSourceGeneratorLayout.SLOT_SIZE - 2,
                    ThermalSmelterUiSkin.withAlpha(color, 155));
            graphics.fill(x + 2, y + LightSourceGeneratorLayout.SLOT_SIZE - 2,
                    x + LightSourceGeneratorLayout.SLOT_SIZE - 2, y + LightSourceGeneratorLayout.SLOT_SIZE - 1,
                    ThermalSmelterUiSkin.withAlpha(color, 90));
        } else {
            outline(graphics, x, y, LightSourceGeneratorLayout.SLOT_SIZE, LightSourceGeneratorLayout.SLOT_SIZE,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 95));
        }
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + LightSourceGeneratorLayout.SLOT_SIZE, y + LightSourceGeneratorLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, LightSourceGeneratorLayout.SLOT_SIZE, LightSourceGeneratorLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + LightSourceGeneratorLayout.SLOT_SIZE - 1, y + 2,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + LightSourceGeneratorLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + LightSourceGeneratorLayout.SLOT_SIZE - 2,
                x + LightSourceGeneratorLayout.SLOT_SIZE - 1, y + LightSourceGeneratorLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + LightSourceGeneratorLayout.SLOT_SIZE - 2, y + 1,
                x + LightSourceGeneratorLayout.SLOT_SIZE - 1, y + LightSourceGeneratorLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + LightSourceGeneratorLayout.SLOT_SIZE - 2,
                y + LightSourceGeneratorLayout.SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 36));
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private boolean insideLocal(int mouseX, int mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    private Component tt(String suffix, Object... args) {
        return Component.translatable("screen.spectralization.light_source_generator." + suffix, args);
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0, ticks) / 20.0);
    }
}
