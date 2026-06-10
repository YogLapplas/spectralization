package io.github.yoglappland.spectralization.optics;

public final class SpectralColorMap {
    public static final int VISIBLE_BINS = 32;

    private static final int[] VISIBLE_SRGB = {
            0x990099, 0x9600B8, 0x8500D6, 0x001DF3, 0x0051FF, 0x007FFF, 0x00AAFF, 0x00D4FF,
            0x00FCFF, 0x00FFDB, 0x00FFB2, 0x00FF87, 0x00FF5A, 0x00FF28, 0x21FF00, 0x62FF00,
            0xB7FF00, 0xECFF00, 0xFFE400, 0xFFB800, 0xFF8A00, 0xFF5900, 0xFF2300, 0xFF0000,
            0xFF0000, 0xFF0000, 0xFF0000, 0xF90000, 0xE20000, 0xCA0000, 0xB20000, 0x990000
    };
    private static final double[] VISIBLE_WHITE_BALANCE = visibleWhiteBalance();

    public static int rgbFor(FrequencyKey frequency) {
        if (frequency.region() != SpectralRegion.VISIBLE) {
            return 0xFFFFFF;
        }

        return visibleRgbForBin(frequency.bin());
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
