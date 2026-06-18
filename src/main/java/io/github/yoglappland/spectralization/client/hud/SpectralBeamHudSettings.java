package io.github.yoglappland.spectralization.client.hud;

public final class SpectralBeamHudSettings {
    private static boolean coherentVisible = true;
    private static boolean strayVisible;

    public static boolean coherentVisible() {
        return coherentVisible;
    }

    public static boolean strayVisible() {
        return strayVisible;
    }

    public static void toggleCoherentVisible() {
        coherentVisible = !coherentVisible;
    }

    public static void toggleStrayVisible() {
        strayVisible = !strayVisible;
    }

    private SpectralBeamHudSettings() {
    }
}
