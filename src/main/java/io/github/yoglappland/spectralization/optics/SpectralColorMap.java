package io.github.yoglappland.spectralization.optics;

import java.util.ArrayList;
import java.util.List;

public final class SpectralColorMap {
    public static final int VISIBLE_BINS = SpectralRegion.VISIBLE.defaultBins();
    public static final int VISIBLE_RED_BIN = 2;
    public static final int VISIBLE_ORANGE_BIN = 5;
    public static final int VISIBLE_YELLOW_BIN = 8;
    public static final int VISIBLE_GREEN_BIN = 13;
    public static final int VISIBLE_LIME_BIN = 14;
    public static final int VISIBLE_CYAN_BIN = 18;
    public static final int VISIBLE_LIGHT_BLUE_BIN = 20;
    public static final int VISIBLE_BLUE_BIN = 23;
    public static final int VISIBLE_PURPLE_BIN = 26;
    public static final int VISIBLE_MAGENTA_BIN = 29;
    public static final int VISIBLE_PINK_BIN = 30;
    private static final int INFRARED_LOW_RGB = 0x3D1515;
    private static final int INFRARED_HIGH_RGB = 0xE24F3F;
    private static final int ULTRAVIOLET_LOW_RGB = 0x8367FF;
    private static final int ULTRAVIOLET_HIGH_RGB = 0x2C1E67;
    private static final int NONVISIBLE_FALLBACK_RGB = 0x8992A3;

    // Visible bins follow spectral order: red is adjacent to infrared, violet is adjacent to ultraviolet.
    private static final int[] VISIBLE_SRGB = {
            0x7A0000, 0xB00000, 0xE60000, 0xFF1F00, 0xFF4A00, 0xFF7A00, 0xFFA300, 0xFFD400,
            0xFFF200, 0xD6FF00, 0xA8FF00, 0x72FF00, 0x38FF00, 0x00E83A, 0x00FF55, 0x00FF82,
            0x00FFB0, 0x00FFE0, 0x00EFFF, 0x00C8FF, 0x009DFF, 0x006EFF, 0x0040FF, 0x2020FF,
            0x3C12F4, 0x5A00E0, 0x7800D0, 0x8B00C4, 0xA000C0, 0xB000B8, 0xC645C6, 0x7A00FF
    };
    private static final double[] VISIBLE_WHITE_BALANCE = visibleWhiteBalance();

    public static int rgbFor(FrequencyKey frequency) {
        if (frequency.region() != SpectralRegion.VISIBLE) {
            return 0xFFFFFF;
        }

        return visibleRgbForBin(frequency.bin());
    }

    public static int displayRgbFor(FrequencyKey frequency) {
        return switch (frequency.region()) {
            case INFRARED -> interpolateRegionRgb(INFRARED_LOW_RGB, INFRARED_HIGH_RGB, frequency);
            case VISIBLE -> visibleRgbForBin(frequency.bin());
            case ULTRAVIOLET -> interpolateRegionRgb(ULTRAVIOLET_LOW_RGB, ULTRAVIOLET_HIGH_RGB, frequency);
            default -> NONVISIBLE_FALLBACK_RGB;
        };
    }

    public static int visibleRgbForBin(int bin) {
        return VISIBLE_SRGB[clampVisibleBin(bin)];
    }

    public static int mixVisibleRgb(Iterable<WeightedFrequency> frequencies, int fallbackRgb) {
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;
        double totalWeight = 0.0;

        for (WeightedFrequency weightedFrequency : frequencies) {
            if (weightedFrequency.frequency().region() != SpectralRegion.VISIBLE || weightedFrequency.weight() <= 0.0) {
                continue;
            }

            int rgb = rgbFor(weightedFrequency.frequency());
            double weight = weightedFrequency.weight();
            red += red(rgb) * weight;
            green += green(rgb) * weight;
            blue += blue(rgb) * weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) {
            return fallbackRgb;
        }

        red *= VISIBLE_WHITE_BALANCE[0];
        green *= VISIBLE_WHITE_BALANCE[1];
        blue *= VISIBLE_WHITE_BALANCE[2];

        double max = Math.max(red, Math.max(green, blue));

        if (max <= 0.0) {
            return fallbackRgb;
        }

        return (clampChannel(red / max * 255.0) << 16)
                | (clampChannel(green / max * 255.0) << 8)
                | clampChannel(blue / max * 255.0);
    }

    public static int mixVisibleRgbForComponents(
            Iterable<PlaneWaveComponent> components,
            CoherenceKind coherence,
            int fallbackRgb
    ) {
        List<WeightedFrequency> frequencies = new ArrayList<>();

        for (PlaneWaveComponent component : components) {
            if (coherence != null && component.coherence() != coherence) {
                continue;
            }

            frequencies.add(new WeightedFrequency(component.frequency(), component.power()));
        }

        return mixVisibleRgb(frequencies, fallbackRgb);
    }

    public static int red(int rgb) {
        return (rgb >> 16) & 255;
    }

    public static int green(int rgb) {
        return (rgb >> 8) & 255;
    }

    public static int blue(int rgb) {
        return rgb & 255;
    }

    private static int clampChannel(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private static int interpolateRegionRgb(int lowRgb, int highRgb, FrequencyKey frequency) {
        int maxBin = Math.max(1, frequency.region().defaultBins() - 1);
        double t = Math.max(0.0D, Math.min(1.0D, frequency.bin() / (double) maxBin));
        int red = clampChannel(red(lowRgb) + (red(highRgb) - red(lowRgb)) * t);
        int green = clampChannel(green(lowRgb) + (green(highRgb) - green(lowRgb)) * t);
        int blue = clampChannel(blue(lowRgb) + (blue(highRgb) - blue(lowRgb)) * t);
        return (red << 16) | (green << 8) | blue;
    }

    private static int clampVisibleBin(int bin) {
        if (bin < 0) {
            return 0;
        }

        return Math.min(bin, VISIBLE_SRGB.length - 1);
    }

    private static double[] visibleWhiteBalance() {
        double red = 0.0;
        double green = 0.0;
        double blue = 0.0;

        for (int rgb : VISIBLE_SRGB) {
            red += red(rgb);
            green += green(rgb);
            blue += blue(rgb);
        }

        double max = Math.max(red, Math.max(green, blue));

        return new double[]{
                red <= 0.0 ? 1.0 : max / red,
                green <= 0.0 ? 1.0 : max / green,
                blue <= 0.0 ? 1.0 : max / blue
        };
    }

    private SpectralColorMap() {
    }

    public record WeightedFrequency(FrequencyKey frequency, double weight) {
    }
}
