package io.github.yoglappland.spectralization.optics;

public final class SpectralColorMap {
    public static final int VISIBLE_BINS = 64;

    private static final int[] VISIBLE_SRGB = {
            0x990099, 0x9900A9, 0x9600B8, 0x8F00C7, 0x8500D6, 0x7700E5, 0x001DF3, 0x0039FF,
            0x0051FF, 0x0069FF, 0x007FFF, 0x0095FF, 0x00AAFF, 0x00BFFF, 0x00D4FF, 0x00E8FF,
            0x00FCFF, 0x00FFEF, 0x00FFDB, 0x00FFC7, 0x00FFB2, 0x00FF9D, 0x00FF87, 0x00FF71,
            0x00FF5A, 0x00FF42, 0x00FF28, 0x00FF0A, 0x21FF00, 0x43FF00, 0x62FF00, 0x7FFF00,
            0x9BFF00, 0xB7FF00, 0xD2FF00, 0xECFF00, 0xFFFA00, 0xFFE400, 0xFFCF00, 0xFFB800,
            0xFFA200, 0xFF8A00, 0xFF7200, 0xFF5900, 0xFF3F00, 0xFF2300, 0xFF0000, 0xFF0000,
            0xFF0000, 0xFF0000, 0xFF0000, 0xFF0000, 0xFF0000, 0xFF0000, 0xFF0000, 0xF90000,
            0xEE0000, 0xE20000, 0xD60000, 0xCA0000, 0xBE0000, 0xB20000, 0xA60000, 0x990000
    };

    public static int rgbFor(FrequencyKey frequency) {
        if (frequency.region() != SpectralRegion.VISIBLE) {
            return 0xFFFFFF;
        }

        return visibleRgbForBin(frequency.bin());
    }

    public static int visibleRgbForBin(int bin) {
        return VISIBLE_SRGB[clampVisibleBin(bin)];
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

    private static int clampVisibleBin(int bin) {
        if (bin < 0) {
            return 0;
        }

        return Math.min(bin, VISIBLE_SRGB.length - 1);
    }

    private SpectralColorMap() {
    }
}
