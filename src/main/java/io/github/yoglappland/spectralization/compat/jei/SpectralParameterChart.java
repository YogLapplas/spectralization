package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class SpectralParameterChart {
    private static final int PLOT_LEFT = 4;
    private static final int PLOT_TOP = 4;
    private static final int PLOT_RIGHT = 4;
    private static final int PLOT_BOTTOM = 4;
    private static final int REGION_COUNT = 3;
    private static final int CURVE_SHADOW = 0x66000000;

    private SpectralParameterChart() {
    }

    static void draw(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            Curve curve,
            double maxValue,
            int curveColor,
            double mouseX,
            double mouseY
    ) {
        Bounds plot = plotBounds(x, y, width, height);
        drawFrame(graphics, x, y, width, height, plot);

        if (!Double.isFinite(maxValue) || maxValue <= 0.0D) {
            return;
        }

        int previousX = plot.x();
        int previousY = valueY(interpolatedValueAt(curve, pointAtPlotX(previousX, plot)), maxValue, plot);

        for (int currentX = plot.x() + 1; currentX < plot.x() + plot.width(); currentX++) {
            int currentY = valueY(interpolatedValueAt(curve, pointAtPlotX(currentX, plot)), maxValue, plot);
            drawLine(graphics, previousX, previousY + 1, currentX, currentY + 1, CURVE_SHADOW);
            drawLine(graphics, previousX, previousY, currentX, currentY, curveColor);
            previousX = currentX;
            previousY = currentY;
        }

        if (inside(mouseX, mouseY, x, y, width, height)) {
            Hover hover = hoverAt(mouseX, x, y, width, height, curve);
            int hoverX = hover.x();
            int hoverY = valueY(hover.value(), maxValue, plot);
            graphics.fill(hoverX, plot.y(), hoverX + 1, plot.y() + plot.height(),
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 135));
            graphics.fill(hoverX - 2, hoverY - 2, hoverX + 3, hoverY + 3,
                    ThermalSmelterUiSkin.withAlpha(curveColor, 220));
            graphics.fill(hoverX - 1, hoverY - 1, hoverX + 2, hoverY + 2, 0xFFFFFFFF);
        }
    }

    static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX, mouseY, x, y, width, height);
    }

    static Hover hoverAt(double mouseX, int x, int y, int width, int height, Curve curve) {
        Bounds plot = plotBounds(x, y, width, height);
        int hoverX = Math.max(plot.x(), Math.min(plot.x() + plot.width() - 1, (int) Math.round(mouseX)));
        SpectrumPoint point = pointAtPlotX(hoverX, plot);
        return new Hover(point.nearestFrequency(), hoverX, interpolatedValueAt(curve, point));
    }

    static Component frequencyText(FrequencyKey frequency) {
        double t = normalizedBin(frequency);

        if (frequency.region() == SpectralRegion.INFRARED) {
            if (t < 0.34D) {
                return Component.translatable("jei.spectralization.chart.frequency.infrared.low");
            }

            if (t < 0.67D) {
                return Component.translatable("jei.spectralization.chart.frequency.infrared.middle");
            }

            return Component.translatable("jei.spectralization.chart.frequency.infrared.high");
        }

        if (frequency.region() == SpectralRegion.VISIBLE) {
            if (t < 0.18D) {
                return Component.translatable("jei.spectralization.chart.frequency.visible.red");
            }

            if (t < 0.38D) {
                return Component.translatable("jei.spectralization.chart.frequency.visible.yellow");
            }

            if (t < 0.62D) {
                return Component.translatable("jei.spectralization.chart.frequency.visible.green");
            }

            if (t < 0.82D) {
                return Component.translatable("jei.spectralization.chart.frequency.visible.blue");
            }

            return Component.translatable("jei.spectralization.chart.frequency.visible.violet");
        }

        if (t < 0.34D) {
            return Component.translatable("jei.spectralization.chart.frequency.ultraviolet.near");
        }

        if (t < 0.67D) {
            return Component.translatable("jei.spectralization.chart.frequency.ultraviolet.middle");
        }

        return Component.translatable("jei.spectralization.chart.frequency.ultraviolet.deep");
    }

    static String compactValue(double value) {
        if (!Double.isFinite(value)) {
            return "-";
        }

        return String.format(Locale.ROOT, "%.4f", Math.max(0.0D, value));
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, Bounds plot) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 150));
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.CHAMBER_BG);

        drawSpectrumBands(graphics, plot);
        drawGrid(graphics, plot);

        graphics.fill(plot.x() - 1, plot.y(), plot.x(), plot.y() + plot.height(),
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 175));
        graphics.fill(plot.x() - 1, plot.y() + plot.height(), plot.x() + plot.width(), plot.y() + plot.height() + 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 175));
    }

    private static void drawSpectrumBands(GuiGraphics graphics, Bounds plot) {
        for (int x = plot.x(); x < plot.x() + plot.width(); x++) {
            FrequencyKey frequency = frequencyAtPlotX(x, plot);
            graphics.fill(
                    x,
                    plot.y(),
                    x + 1,
                    plot.y() + plot.height(),
                    ThermalSmelterUiSkin.withAlpha(SpectralColorMap.displayRgbFor(frequency) | 0xFF000000, 72)
            );
        }

        int visibleLeft = regionBoundary(plot, 1);
        int visibleRight = regionBoundary(plot, 2);
        int firstSplit = visibleLeft;
        int secondSplit = visibleRight;
        int divider = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 150);
        graphics.fill(firstSplit, plot.y(), firstSplit + 1, plot.y() + plot.height(), divider);
        graphics.fill(secondSplit, plot.y(), secondSplit + 1, plot.y() + plot.height(), divider);
    }

    private static int regionBoundary(Bounds plot, int region) {
        return plot.x() + region * plot.width() / 3;
    }

    private static void drawGrid(GuiGraphics graphics, Bounds plot) {
        int grid = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 56);
        for (int i = 1; i < 4; i++) {
            int y = plot.y() + i * plot.height() / 4;
            graphics.fill(plot.x(), y, plot.x() + plot.width(), y + 1, grid);
        }
    }

    private static int valueY(double value, double maxValue, Bounds plot) {
        double clamped = Double.isFinite(value) ? Math.max(0.0D, Math.min(maxValue, value)) : 0.0D;
        double t = clamped / maxValue;
        return plot.y() + plot.height() - 1 - (int) Math.round(t * Math.max(1, plot.height() - 1));
    }

    private static SpectrumPoint pointAtPlotX(int x, Bounds plot) {
        double t = (x - plot.x()) / (double) Math.max(1, plot.width() - 1);
        double scaled = Math.max(0.0D, Math.min(1.0D, t)) * REGION_COUNT;
        int regionIndex = Math.min(REGION_COUNT - 1, (int) Math.floor(scaled));
        double regionT = regionIndex == REGION_COUNT - 1 && scaled >= REGION_COUNT
                ? 1.0D
                : scaled - regionIndex;
        SpectralRegion region = regionAt(regionIndex);
        int maxBin = Math.max(0, region.defaultBins() - 1);
        double bin = regionT * maxBin;

        return new SpectrumPoint(region, Math.max(0.0D, Math.min(maxBin, bin)));
    }

    private static FrequencyKey frequencyAtPlotX(int x, Bounds plot) {
        return pointAtPlotX(x, plot).nearestFrequency();
    }

    private static SpectralRegion regionAt(int regionIndex) {
        return switch (regionIndex) {
            case 0 -> SpectralRegion.INFRARED;
            case 1 -> SpectralRegion.VISIBLE;
            default -> SpectralRegion.ULTRAVIOLET;
        };
    }

    private static double interpolatedValueAt(Curve curve, SpectrumPoint point) {
        int bin = (int) Math.floor(point.bin());
        int maxBin = Math.max(0, point.region().defaultBins() - 1);
        double t = point.bin() - bin;

        if (bin >= maxBin) {
            return valueAtBin(curve, point.region(), maxBin);
        }

        double y0 = valueAtBin(curve, point.region(), bin - 1);
        double y1 = valueAtBin(curve, point.region(), bin);
        double y2 = valueAtBin(curve, point.region(), bin + 1);
        double y3 = valueAtBin(curve, point.region(), bin + 2);
        double t2 = t * t;
        double t3 = t2 * t;
        double interpolated = 0.5D * (
                2.0D * y1
                        + (-y0 + y2) * t
                        + (2.0D * y0 - 5.0D * y1 + 4.0D * y2 - y3) * t2
                        + (-y0 + 3.0D * y1 - 3.0D * y2 + y3) * t3
        );
        double low = Math.min(y1, y2);
        double high = Math.max(y1, y2);
        return Math.max(low, Math.min(high, interpolated));
    }

    private static double valueAtBin(Curve curve, SpectralRegion region, int bin) {
        int clampedBin = Math.max(0, Math.min(region.defaultBins() - 1, bin));
        double value = curve.valueAt(new FrequencyKey(region, clampedBin));
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    private static double normalizedBin(FrequencyKey frequency) {
        int maxBin = Math.max(1, frequency.region().defaultBins() - 1);
        return Math.max(0.0D, Math.min(1.0D, frequency.bin() / (double) maxBin));
    }

    private static Bounds plotBounds(int x, int y, int width, int height) {
        return new Bounds(
                x + PLOT_LEFT,
                y + PLOT_TOP,
                Math.max(1, width - PLOT_LEFT - PLOT_RIGHT),
                Math.max(1, height - PLOT_TOP - PLOT_BOTTOM)
        );
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;

        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);

            if (x0 == x1 && y0 == y1) {
                break;
            }

            int doubleError = error * 2;
            if (doubleError >= dy) {
                error += dy;
                x0 += sx;
            }

            if (doubleError <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }

    interface Curve {
        double valueAt(FrequencyKey frequency);
    }

    record Hover(FrequencyKey frequency, int x, double value) {
    }

    private record Bounds(int x, int y, int width, int height) {
    }

    private record SpectrumPoint(SpectralRegion region, double bin) {
        private FrequencyKey nearestFrequency() {
            return FrequencyKey.clamped(region, (int) Math.round(bin));
        }
    }
}
