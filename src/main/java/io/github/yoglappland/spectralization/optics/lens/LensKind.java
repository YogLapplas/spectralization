package io.github.yoglappland.spectralization.optics.lens;

import java.util.Locale;

public enum LensKind {
    CONVEX("convex", "lens_kind.spectralization.convex", "lens_parameter.spectralization.focal_length"),
    CONCAVE("concave", "lens_kind.spectralization.concave", "lens_parameter.spectralization.spread_distance"),
    ENDER("ender", "lens_kind.spectralization.ender", "lens_parameter.spectralization.binding_distance"),
    MAGMA("magma", "lens_kind.spectralization.magma", "lens_parameter.spectralization.heat_strength"),
    ECHO("echo", "lens_kind.spectralization.echo", "lens_parameter.spectralization.resonance_strength");

    private final String id;
    private final String translationKey;
    private final String parameterKey;

    LensKind(String id, String translationKey, String parameterKey) {
        this.id = id;
        this.translationKey = translationKey;
        this.parameterKey = parameterKey;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }

    public String parameterKey() {
        return parameterKey;
    }

    public static LensKind byIndex(int index) {
        LensKind[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    public static LensKind byId(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);

        for (LensKind kind : values()) {
            if (kind.id.equals(normalized)) {
                return kind;
            }
        }

        return CONVEX;
    }
}
