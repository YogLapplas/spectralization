package io.github.yoglappland.spectralization.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpectralizationConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue LIGHT_PATHS_VISIBLE;
    private static final ModConfigSpec.BooleanValue LASER_DAMAGE;
    private static final ModConfigSpec.BooleanValue LASER_BLINDNESS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("optics");
        LIGHT_PATHS_VISIBLE = builder
                .comment("Whether debug light path particles are visible near scattering blocks.")
                .define("light_paths_visible", true);
        LASER_DAMAGE = builder
                .comment("Whether hazardous beams can damage entities looking into them.")
                .define("laser_damage", true);
        LASER_BLINDNESS = builder
                .comment("Whether hazardous beams can blind entities looking into them.")
                .define("laser_blindness", true);
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

    private SpectralizationConfig() {
    }
}
