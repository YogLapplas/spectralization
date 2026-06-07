package io.github.yoglappland.spectralization.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpectralizationConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue LIGHT_PATHS_VISIBLE;
    private static final ModConfigSpec.BooleanValue SURFACE_SPOTS_VISIBLE;
    private static final ModConfigSpec.BooleanValue LASER_DAMAGE;
    private static final ModConfigSpec.BooleanValue LASER_BLINDNESS;
    private static final ModConfigSpec.BooleanValue SCATTERING_FIELD_ENABLED;
    private static final ModConfigSpec.IntValue SCATTERING_FIELD_RADIUS;
    private static final ModConfigSpec.DoubleValue SCATTERING_FIELD_PROPAGATION_FACTOR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("optics");
        LIGHT_PATHS_VISIBLE = builder
                .comment("Whether debug light path particles are visible near scattering blocks.")
                .define("light_paths_visible", true);
        SURFACE_SPOTS_VISIBLE = builder
                .comment("Whether lossy optical interactions create client-side surface spot overlays.")
                .define("surface_spots_visible", true);
        LASER_DAMAGE = builder
                .comment("Whether hazardous beams can damage entities looking into them.")
                .define("laser_damage", true);
        LASER_BLINDNESS = builder
                .comment("Whether hazardous beams can blind entities looking into them.")
                .define("laser_blindness", true);
        builder.pop();

        builder.push("optical_field_sources");
        builder.push("scattering");
        SCATTERING_FIELD_ENABLED = builder
                .comment("Whether scattering field source blocks affect nearby optical paths.")
                .define("enabled", true);
        SCATTERING_FIELD_RADIUS = builder
                .comment("Radius in blocks around a scattering field source. Default 2 means a 5x5x5 field.")
                .defineInRange("radius", 2, 0, 16);
        SCATTERING_FIELD_PROPAGATION_FACTOR = builder
                .comment("Propagation multiplier applied to beams inside a scattering field.")
                .defineInRange("propagation_factor", 0.82D, 0.0D, 1.0D);
        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }

    public static boolean lightPathsVisible() {
        return LIGHT_PATHS_VISIBLE.get();
    }

    public static void setLightPathsVisible(boolean visible) {
        LIGHT_PATHS_VISIBLE.set(visible);
        SPEC.save();
    }

    public static boolean surfaceSpotsVisible() {
        return SURFACE_SPOTS_VISIBLE.get();
    }

    public static boolean laserDamage() {
        return LASER_DAMAGE.get();
    }

    public static void setLaserDamage(boolean enabled) {
        LASER_DAMAGE.set(enabled);
        SPEC.save();
    }

    public static boolean laserBlindness() {
        return LASER_BLINDNESS.get();
    }

    public static void setLaserBlindness(boolean enabled) {
        LASER_BLINDNESS.set(enabled);
        SPEC.save();
    }

    public static boolean scatteringFieldEnabled() {
        return SCATTERING_FIELD_ENABLED.get();
    }

    public static int scatteringFieldRadius() {
        return SCATTERING_FIELD_RADIUS.get();
    }

    public static double scatteringFieldPropagationFactor() {
        return SCATTERING_FIELD_PROPAGATION_FACTOR.get();
    }

    private SpectralizationConfig() {
    }
}
