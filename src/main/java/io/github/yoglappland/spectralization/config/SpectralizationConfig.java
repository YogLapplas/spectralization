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
    private static final ModConfigSpec.IntValue OPTICAL_SOLVER_MAX_REQUESTS_PER_TICK;
    private static final ModConfigSpec.IntValue OPTICAL_SOLVER_BUDGET_MICROS;
    private static final ModConfigSpec.BooleanValue OPTICAL_COMPILER_DEBUG_LOG;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DEBUG_MAX_EDGES;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_MAX_DIRECT_OUTGOING_NODES;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DEBUG_SAMPLE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DEBUG_MAX_RUNS_PER_TICK;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DEBUG_FULL_NETWORK_MAX_NODES;

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

        builder.push("optical_solver");
        OPTICAL_SOLVER_MAX_REQUESTS_PER_TICK = builder
                .comment("Maximum dirty optical trace requests processed per server tick.")
                .defineInRange("max_requests_per_tick", 2, 1, 128);
        OPTICAL_SOLVER_BUDGET_MICROS = builder
                .comment("Soft server-tick budget for dirty optical trace processing, in microseconds.")
                .defineInRange("budget_micros", 2000, 100, 50_000);
        builder.pop();

        builder.push("optical_compiler");
        OPTICAL_COMPILER_DEBUG_LOG = builder
                .comment("Whether every optical compiler run writes a debug entry to logs/spectralization/optical_compiler.log.")
                .define("debug_log", false);
        OPTICAL_COMPILER_DEBUG_MAX_EDGES = builder
                .comment("Maximum edge lines written for each optical compiler debug entry.")
                .defineInRange("debug_log_max_edges", 128, 0, 4096);
        OPTICAL_COMPILER_MAX_DIRECT_OUTGOING_NODES = builder
                .comment("Maximum outgoing port nodes explored by one direct optical compiler run.")
                .defineInRange("max_direct_outgoing_nodes", 1024, 64, 16384);
        OPTICAL_COMPILER_DEBUG_SAMPLE_INTERVAL_TICKS = builder
                .comment("Minimum ticks between full optical compiler debug samples for the same source.")
                .defineInRange("debug_sample_interval_ticks", 20, 1, 1200);
        OPTICAL_COMPILER_DEBUG_MAX_RUNS_PER_TICK = builder
                .comment("Maximum optical compiler debug samples processed per server tick.")
                .defineInRange("debug_max_runs_per_tick", 1, 1, 16);
        OPTICAL_COMPILER_DEBUG_FULL_NETWORK_MAX_NODES = builder
                .comment("Maximum direct graph nodes allowed before network-level debug expansion is skipped.")
                .defineInRange("debug_full_network_max_nodes", 2048, 64, 65536);
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

    public static int opticalSolverMaxRequestsPerTick() {
        return OPTICAL_SOLVER_MAX_REQUESTS_PER_TICK.get();
    }

    public static int opticalSolverBudgetMicros() {
        return OPTICAL_SOLVER_BUDGET_MICROS.get();
    }

    public static boolean opticalCompilerDebugLog() {
        return OPTICAL_COMPILER_DEBUG_LOG.get();
    }

    public static void setOpticalCompilerDebugLog(boolean enabled) {
        OPTICAL_COMPILER_DEBUG_LOG.set(enabled);
        SPEC.save();
    }

    public static int opticalCompilerDebugMaxEdges() {
        return OPTICAL_COMPILER_DEBUG_MAX_EDGES.get();
    }

    public static int opticalCompilerMaxDirectOutgoingNodes() {
        return OPTICAL_COMPILER_MAX_DIRECT_OUTGOING_NODES.get();
    }

    public static int opticalCompilerDebugSampleIntervalTicks() {
        return OPTICAL_COMPILER_DEBUG_SAMPLE_INTERVAL_TICKS.get();
    }

    public static int opticalCompilerDebugMaxRunsPerTick() {
        return OPTICAL_COMPILER_DEBUG_MAX_RUNS_PER_TICK.get();
    }

    public static int opticalCompilerDebugFullNetworkMaxNodes() {
        return OPTICAL_COMPILER_DEBUG_FULL_NETWORK_MAX_NODES.get();
    }

    private SpectralizationConfig() {
    }
}
