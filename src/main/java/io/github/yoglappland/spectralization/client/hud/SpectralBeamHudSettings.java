package io.github.yoglappland.spectralization.client.hud;

public final class SpectralBeamHudSettings {
    public static boolean coherentVisible() {
        return true;
    }

    public static boolean strayVisible() {
        return true;
    }

    public static void toggleCoherentVisible() {
        // The split coherent/stray renderer is temporarily disabled.
    }

    public static void toggleStrayVisible() {
        // The split coherent/stray renderer is temporarily disabled.
    }

    private SpectralBeamHudSettings() {
    }
}
