package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.SpectrometerBlockEntity;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.menu.SpectrometerMenu;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class SpectrometerScreen extends AbstractContainerScreen<SpectrometerMenu> {
    private static final int CHART_LEFT = 15;
    private static final int CHART_TOP = 42;
    private static final int CHART_WIDTH = 224;
    private static final int CHART_HEIGHT = 106;
    private static final int BUTTON_Y = 164;
    private static final int TEXT = ThermalSmelterUiSkin.TEXT;
    private static final int TEXT_SUB = ThermalSmelterUiSkin.TEXT_SUB;
    private static final int CHART_BG = ThermalSmelterUiSkin.CHAMBER_BG;
    private static final int GRID_MAJOR = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 92);
    private static final int GRID_MINOR = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 42);

    public SpectrometerScreen(SpectrometerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 192;
        inventoryLabelY = imageHeight + 100;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> click(SpectrometerMenu.BUTTON_REGION_DOWN))
                .bounds(leftPos + 16, topPos + BUTTON_Y, 24, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> click(SpectrometerMenu.BUTTON_REGION_UP))
                .bounds(leftPos + 216, topPos + BUTTON_Y, 24, 16)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderSpectrumTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        renderSpectrumChart(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, Component.translatable(
                "screen.spectralization.spectrometer.region",
                regionText(activeRegion())
        ), 12, 22, TEXT, false);
        graphics.drawString(font, Component.translatable(
                "screen.spectralization.spectrometer.total",
                powerText(totalPower())
        ), 122, 22, TEXT, false);
        graphics.drawString(font, Component.translatable(
                "screen.spectralization.spectrometer.peak",
                frequencyCode(new FrequencyKey(activeRegion(), data(SpectrometerBlockEntity.DATA_PEAK_BIN))),
                powerText(maxBinPower())
        ), 52, 168, TEXT_SUB, false);
        graphics.drawString(font, reliabilityText(), 156, 168, reliabilityColor(), false);
    }

    private void renderSpectrumChart(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = leftPos + CHART_LEFT;
        int top = topPos + CHART_TOP;
        int right = left + CHART_WIDTH;
        int bottom = top + CHART_HEIGHT;
        int bins = activeRegion().defaultBins();
        double maxPower = maxBinPower();

        drawChartFrame(graphics, left, top, right, bottom);
        graphics.fill(left, top, right, bottom, CHART_BG);
        drawSpectrumBackground(graphics, left, top, right, bottom);
        drawGrid(graphics, left, top, right, bottom);

        for (int bin = 0; bin < bins; bin++) {
            int slotLeft = left + bin * CHART_WIDTH / bins;
            int slotRight = left + (bin + 1) * CHART_WIDTH / bins;
            int barWidth = Math.max(1, slotRight - slotLeft - 1);
            double power = binPower(bin);
            int barHeight = maxPower <= 0.0D ? 0 : (int) Math.round((CHART_HEIGHT - 4) * power / maxPower);

            if (barHeight <= 0) {
                graphics.fill(slotLeft, bottom - 1, slotLeft + barWidth, bottom,
                        ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 116));
                continue;
            }

            int color = 0xFF000000 | barColor(bin);
            int barTop = bottom - barHeight;
            graphics.fill(slotLeft, bottom - barHeight, slotLeft + barWidth, bottom,
                    ThermalSmelterUiSkin.withAlpha(color, 190));
            outline(graphics, slotLeft, barTop, barWidth, barHeight,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 92));
            graphics.fill(slotLeft, bottom - barHeight, slotLeft + barWidth, bottom - barHeight + 1,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 112));
        }

        drawHoverLine(graphics, left, top, bottom, bins, mouseX, mouseY);

        if (!reliable()) {
            graphics.fill(left, top, right, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.HEAT, 42));
        }
    }

    private void drawSpectrumBackground(GuiGraphics graphics, int left, int top, int right, int bottom) {
        for (int x = left; x < right; x++) {
            int bin = binAtX(x, left, activeRegion().defaultBins());
            int color = 0xFF000000 | barColor(bin);
            graphics.fill(x, top, x + 1, bottom, ThermalSmelterUiSkin.withAlpha(color, 34));
        }
    }

    private static void drawGrid(GuiGraphics graphics, int left, int top, int right, int bottom) {
        for (int i = 1; i < 4; i++) {
            int y = top + i * (bottom - top) / 4;
            graphics.fill(left, y, right, y + 1, GRID_MAJOR);
        }

        for (int i = 1; i < 8; i++) {
            int x = left + i * (right - left) / 8;
            graphics.fill(x, top, x + 1, bottom, GRID_MINOR);
        }
    }

    private void drawHoverLine(GuiGraphics graphics, int left, int top, int bottom, int bins, int mouseX, int mouseY) {
        if (!insideChart(mouseX, mouseY)) {
            return;
        }

        int bin = binAtMouse(mouseX, bins);
        int x = binCenterX(left, bin, bins);
        graphics.fill(x, top, x + 1, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 160));
        graphics.fill(x + 1, top, x + 2, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 104));
        drawHoverTag(graphics, frequencyCode(new FrequencyKey(activeRegion(), bin)), x, top + 4);
    }

    private void renderSpectrumTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!insideChart(mouseX, mouseY)) {
            return;
        }

        int bin = binAtMouse(mouseX, activeRegion().defaultBins());
        double power = binPower(bin);
        double regionPower = regionPower();
        double totalPower = totalPower();
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable(
                "screen.spectralization.spectrometer.tooltip.frequency",
                frequencyCode(new FrequencyKey(activeRegion(), bin))
        ));
        tooltip.add(Component.translatable(
                "screen.spectralization.spectrometer.tooltip.power",
                powerText(power)
        ));
        tooltip.add(Component.translatable(
                "screen.spectralization.spectrometer.tooltip.share",
                percentText(power, regionPower),
                percentText(power, totalPower)
        ));
        tooltip.add(reliabilityText());
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private double totalPower() {
        return scaledPower(data(SpectrometerBlockEntity.DATA_TOTAL_POWER));
    }

    private double regionPower() {
        return scaledPower(data(SpectrometerBlockEntity.DATA_REGION_POWER));
    }

    private double binPower(int bin) {
        return scaledPower(data(SpectrometerBlockEntity.DATA_SPECTRUM_START + bin));
    }

    private double maxBinPower() {
        double maxPower = 0.0D;
        int bins = activeRegion().defaultBins();

        for (int bin = 0; bin < bins; bin++) {
            maxPower = Math.max(maxPower, binPower(bin));
        }

        return maxPower;
    }

    private static double scaledPower(int value) {
        return Math.max(0.0D, value / (double) SpectrometerBlockEntity.POWER_SCALE);
    }

    private SpectralRegion activeRegion() {
        SpectralRegion[] regions = SpectralRegion.values();
        int index = Math.max(0, Math.min(data(SpectrometerBlockEntity.DATA_REGION), regions.length - 1));
        return regions[index];
    }

    private int barColor(int bin) {
        return SpectralColorMap.displayRgbFor(new FrequencyKey(activeRegion(), bin));
    }

    private Component reliabilityText() {
        return Component.translatable(reliable()
                ? "screen.spectralization.spectrometer.reliable"
                : "screen.spectralization.spectrometer.unreliable");
    }

    private int reliabilityColor() {
        return reliable() ? ThermalSmelterUiSkin.STATUS_READY : ThermalSmelterUiSkin.HEAT_DARK;
    }

    private boolean reliable() {
        return data(SpectrometerBlockEntity.DATA_RELIABLE) == 1;
    }

    private static Component regionText(SpectralRegion region) {
        return Component.translatable("jei.spectralization.gain_material.region." + region.id());
    }

    private static String frequencyCode(FrequencyKey frequency) {
        return regionCode(frequency.region()) + "-" + Mth.clamp(frequency.bin(), 0, frequency.region().defaultBins() - 1);
    }

    private static String regionCode(SpectralRegion region) {
        return switch (region) {
            case RADIO -> "RF";
            case MICROWAVE -> "MW";
            case THZ -> "THz";
            case INFRARED -> "IR";
            case VISIBLE -> "V";
            case ULTRAVIOLET -> "UV";
            case FAR_ULTRAVIOLET -> "FUV";
            case XRAY -> "X";
            case GAMMA -> "G";
        };
    }

    private static String powerText(double power) {
        if (!Double.isFinite(power) || power <= 0.0D) {
            return "0.00 SP";
        }

        if (power >= 10000.0D) {
            return String.format(Locale.ROOT, "%.0f SP", power);
        }

        if (power >= 100.0D) {
            return String.format(Locale.ROOT, "%.1f SP", power);
        }

        return String.format(Locale.ROOT, "%.2f SP", power);
    }

    private static String percentText(double part, double total) {
        if (!Double.isFinite(part) || !Double.isFinite(total) || part <= 0.0D || total <= 0.0D) {
            return "0.0%";
        }

        return String.format(Locale.ROOT, "%.1f%%", Mth.clamp(part / total * 100.0D, 0.0D, 999.9D));
    }

    private boolean insideChart(double mouseX, double mouseY) {
        return ThermalSmelterUiSkin.inside(
                mouseX,
                mouseY,
                leftPos + CHART_LEFT,
                topPos + CHART_TOP,
                CHART_WIDTH,
                CHART_HEIGHT
        );
    }

    private int binAtMouse(double mouseX, int bins) {
        return binAtX((int) Math.floor(mouseX), leftPos + CHART_LEFT, bins);
    }

    private static int binAtX(int x, int left, int bins) {
        return Mth.clamp((x - left) * bins / CHART_WIDTH, 0, bins - 1);
    }

    private static int binCenterX(int left, int bin, int bins) {
        return left + (bin * CHART_WIDTH + CHART_WIDTH / 2) / bins;
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + BUTTON_Y - 6, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 8, topPos + BUTTON_Y - 7, leftPos + imageWidth - 8, topPos + BUTTON_Y - 6,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 120));
    }

    private static void drawChartFrame(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left - 4, top - 4, right + 4, bottom + 4, ThermalSmelterUiSkin.PANEL);
        outline(graphics, left - 4, top - 4, right - left + 8, bottom - top + 8, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(left - 3, top - 3, right + 3, top - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(left - 3, top - 3, left - 1, bottom + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(left - 3, bottom + 1, right + 3, bottom + 3, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(right + 1, top - 3, right + 3, bottom + 3, ThermalSmelterUiSkin.PANEL_SHADOW);
        outline(graphics, left - 1, top - 1, right - left + 2, bottom - top + 2, ThermalSmelterUiSkin.BORDER);
    }

    private void drawHoverTag(GuiGraphics graphics, String label, int centerX, int y) {
        int width = font.width(label) + 6;
        int x = Mth.clamp(centerX - width / 2, leftPos + CHART_LEFT + 2, leftPos + CHART_LEFT + CHART_WIDTH - width - 2);
        graphics.fill(x, y, x + width, y + 11, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 232));
        outline(graphics, x, y, width, 11, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 160));
        graphics.drawString(font, label, x + 3, y + 2, TEXT, false);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
