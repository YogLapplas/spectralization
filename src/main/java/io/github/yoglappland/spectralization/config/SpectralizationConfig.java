package io.github.yoglappland.spectralization.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SpectralizationConfig {
    public static final ModConfigSpec SPEC;
    private static final int LEGACY_INTERACTIVE_QUIET_TICKS = 8;
    private static final int DEFAULT_INTERACTIVE_QUIET_TICKS = 1;

    private static final ModConfigSpec.BooleanValue LIGHT_PATHS_VISIBLE;
    private static final ModConfigSpec.BooleanValue SURFACE_SPOTS_VISIBLE;
    private static final ModConfigSpec.BooleanValue LASER_DAMAGE;
    private static final ModConfigSpec.BooleanValue LASER_BLINDNESS;
    private static final ModConfigSpec.BooleanValue UI_DEBUG;
    private static final ModConfigSpec.BooleanValue UI_DEBUG_LABELS;
    private static final ModConfigSpec.BooleanValue DIAGNOSTICS_EVENT_LOG;
    private static final ModConfigSpec.BooleanValue SCATTERING_FIELD_ENABLED;
    private static final ModConfigSpec.IntValue SCATTERING_FIELD_RADIUS;
    private static final ModConfigSpec.DoubleValue SCATTERING_FIELD_PROPAGATION_FACTOR;
    private static final ModConfigSpec.IntValue OPTICAL_SOLVER_MAX_REQUESTS_PER_TICK;
    private static final ModConfigSpec.IntValue OPTICAL_SOLVER_BUDGET_MICROS;
    private static final ModConfigSpec.IntValue OPTICAL_EFFECT_TRACE_MAX_STATES;
    private static final ModConfigSpec.BooleanValue OPTICAL_COMPILER_DEBUG_LOG;
    private static final ModConfigSpec.BooleanValue OPTICAL_COMPILER_DEBUG_VERBOSE;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DEBUG_MAX_EDGES;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_MAX_DIRECT_OUTGOING_NODES;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DEBUG_SAMPLE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DEBUG_READOUT_APPLY_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DEBUG_MAX_RUNS_PER_TICK;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_FULL_NETWORK_MAX_NODES;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_SYSTEM_REBUILD_QUIET_TICKS;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_LARGE_DIRECT_GRAPH_NODES;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_DIRECT_RECOMPILE_QUIET_TICKS;
    private static final ModConfigSpec.BooleanValue OPTICAL_COMPILER_LEGACY_DEBUG_ORACLE;
    private static final ModConfigSpec.IntValue OPTICAL_COMPILER_SYSTEM_CACHE_MAX_ENTRIES;
    private static final ModConfigSpec.IntValue SPOT_PROJECTION_OCCLUSION_PLANES;

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

        builder.push("client");
        UI_DEBUG = builder
                .comment("Whether Spectralization machine screens render layout debug boxes, labels, and mouse coordinates.")
                .define("ui_debug", false);
        UI_DEBUG_LABELS = builder
                .comment("Whether UI debug boxes render their component names and rectangles.")
                .define("ui_debug_labels", true);
        builder.pop();

        builder.push("diagnostics");
        DIAGNOSTICS_EVENT_LOG = builder
                .comment("Whether sparse subsystem event summaries are written to logs/spectralization/diagnostics_*.log.")
                .define("event_log", true);
        builder.pop();

        builder.push("optical_field_sources");
        builder.push("scattering");
        SCATTERING_FIELD_ENABLED = builder
                .comment("Whether scattering field source blocks affect nearby optical paths.")
                .define("enabled", true);
        SCATTERING_FIELD_RADIUS = builder
                .comment("Radius in blocks around a scattering field source. Default 1 means a 3x3x3 field.")
                .defineInRange("radius", 1, 0, 16);
        SCATTERING_FIELD_PROPAGATION_FACTOR = builder
                .comment("Propagation multiplier applied to beams inside a scattering field.")
                .defineInRange("propagation_factor", 0.82D, 0.0D, 1.0D);
        builder.pop();
        builder.pop();

        builder.push("optical_solver");
        OPTICAL_SOLVER_MAX_REQUESTS_PER_TICK = builder
                .comment("Maximum dirty optical trace requests processed per server tick.")
                .defineInRange("max_requests_per_tick", 16, 1, 128);
        OPTICAL_SOLVER_BUDGET_MICROS = builder
                .comment("Soft server-tick budget for dirty optical trace processing, in microseconds.")
                .defineInRange("budget_micros", 8000, 100, 50_000);
        OPTICAL_EFFECT_TRACE_MAX_STATES = builder
                .comment("Maximum old recursive trace states when the explicit compiler debug oracle is enabled. The compiler solver is not limited by this.")
                .defineInRange("effect_trace_max_states", 384, 0, 4096);
        builder.pop();

        builder.push("optical_compiler");
        OPTICAL_COMPILER_DEBUG_LOG = builder
                .comment("Whether every optical compiler run writes a debug entry to logs/spectralization/optical_compiler.log.")
                .define("debug_log", false);
        OPTICAL_COMPILER_DEBUG_VERBOSE = builder
                .comment("Whether optical compiler debug entries include heavy detail sections such as lanes, solver regions, SCCs, chords, edges, HUD segments, and readout output lists.")
                .define("debug_log_verbose", false);
        OPTICAL_COMPILER_DEBUG_MAX_EDGES = builder
                .comment("Maximum edge lines written for each optical compiler debug entry.")
                .defineInRange("debug_log_max_edges", 128, 0, 4096);
        OPTICAL_COMPILER_MAX_DIRECT_OUTGOING_NODES = builder
                .comment("Maximum outgoing port nodes explored by one direct optical compiler run.")
                .defineInRange("max_direct_outgoing_nodes", 1024, 64, 16384);
        OPTICAL_COMPILER_DEBUG_SAMPLE_INTERVAL_TICKS = builder
                .comment("Minimum ticks between full optical compiler debug samples for the same source.")
                .defineInRange("debug_sample_interval_ticks", 20, 1, 1200);
        OPTICAL_COMPILER_DEBUG_READOUT_APPLY_INTERVAL_TICKS = builder
                .comment("Minimum ticks between repeated readout-apply debug samples for the same stable state. State changes are still logged immediately.")
                .defineInRange("debug_readout_apply_interval_ticks", 100, 1, 6000);
        OPTICAL_COMPILER_DEBUG_MAX_RUNS_PER_TICK = builder
                .comment("Maximum optical compiler debug samples processed per server tick.")
                .defineInRange("debug_max_runs_per_tick", 1, 1, 16);
        OPTICAL_COMPILER_FULL_NETWORK_MAX_NODES = builder
                .comment("Maximum direct graph nodes allowed before network-level gameplay compilation is skipped for the current rebuild.")
                .defineInRange("full_network_max_nodes", 16384, 64, 262144);
        OPTICAL_COMPILER_SYSTEM_REBUILD_QUIET_TICKS = builder
                .comment("Ticks without direct optical work required before queued topology/data rebuilds are allowed to run.")
                .defineInRange("system_rebuild_quiet_ticks", 0, 0, 200);
        OPTICAL_COMPILER_LARGE_DIRECT_GRAPH_NODES = builder
                .comment("Direct graph node count above which dirty recompilation is deferred until the network is quiet.")
                .defineInRange("large_direct_graph_nodes", 1024, 64, 262144);
        OPTICAL_COMPILER_DIRECT_RECOMPILE_QUIET_TICKS = builder
                .comment("Ticks a large dirty direct graph must remain quiet before it is recompiled.")
                .defineInRange("direct_recompile_quiet_ticks", 1, 0, 200);
        OPTICAL_COMPILER_LEGACY_DEBUG_ORACLE = builder
                .comment("Whether compiler debug logging may run the old recursive tracer as an observed oracle. Keep this off for performance tests.")
                .define("legacy_debug_oracle", false);
        OPTICAL_COMPILER_SYSTEM_CACHE_MAX_ENTRIES = builder
                .comment("Maximum cached compiled optical systems keyed by repeated source geometry states.")
                .defineInRange("system_cache_max_entries", 256, 0, 8192);
        SPOT_PROJECTION_OCCLUSION_PLANES = builder
                .comment("Number of parallel planes used by projected spot occlusion. Default 5 means front, 25%, 50%, 75%, and back.")
                .defineInRange("spot_projection_occlusion_planes", 5, 2, 33);
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

    public static boolean uiDebug() {
        return UI_DEBUG.get();
    }

    public static void setUiDebug(boolean enabled) {
        UI_DEBUG.set(enabled);
        SPEC.save();
    }

    public static boolean uiDebugLabels() {
        return UI_DEBUG_LABELS.get();
    }

    public static void setUiDebugLabels(boolean enabled) {
        UI_DEBUG_LABELS.set(enabled);
        SPEC.save();
    }

    public static boolean diagnosticsEventLog() {
        return DIAGNOSTICS_EVENT_LOG.get();
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

    public static int opticalEffectTraceMaxStates() {
        return OPTICAL_EFFECT_TRACE_MAX_STATES.get();
    }

    public static boolean opticalCompilerDebugLog() {
        return OPTICAL_COMPILER_DEBUG_LOG.get();
    }

    public static boolean opticalCompilerDebugVerbose() {
        return OPTICAL_COMPILER_DEBUG_VERBOSE.get();
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

    public static int opticalCompilerDebugReadoutApplyIntervalTicks() {
        return OPTICAL_COMPILER_DEBUG_READOUT_APPLY_INTERVAL_TICKS.get();
    }

    public static int opticalCompilerDebugMaxRunsPerTick() {
        return OPTICAL_COMPILER_DEBUG_MAX_RUNS_PER_TICK.get();
    }

    public static int opticalCompilerFullNetworkMaxNodes() {
        return OPTICAL_COMPILER_FULL_NETWORK_MAX_NODES.get();
    }

    public static int opticalCompilerSystemRebuildQuietTicks() {
        return interactiveQuietTicks(OPTICAL_COMPILER_SYSTEM_REBUILD_QUIET_TICKS);
    }

    public static int opticalCompilerLargeDirectGraphNodes() {
        return OPTICAL_COMPILER_LARGE_DIRECT_GRAPH_NODES.get();
    }

    public static int opticalCompilerDirectRecompileQuietTicks() {
        return interactiveQuietTicks(OPTICAL_COMPILER_DIRECT_RECOMPILE_QUIET_TICKS);
    }

    public static boolean opticalCompilerLegacyDebugOracle() {
        return OPTICAL_COMPILER_LEGACY_DEBUG_ORACLE.get();
    }

    public static int opticalCompilerSystemCacheMaxEntries() {
        return OPTICAL_COMPILER_SYSTEM_CACHE_MAX_ENTRIES.get();
    }

    public static int spotProjectionOcclusionPlanes() {
        return SPOT_PROJECTION_OCCLUSION_PLANES.get();
    }

    public static void setSpotProjectionOcclusionPlanes(int planes) {
        SPOT_PROJECTION_OCCLUSION_PLANES.set(Math.max(2, Math.min(33, planes)));
        SPEC.save();
    }

    private static int interactiveQuietTicks(ModConfigSpec.IntValue value) {
        int ticks = value.get();
        return ticks == LEGACY_INTERACTIVE_QUIET_TICKS ? DEFAULT_INTERACTIVE_QUIET_TICKS : ticks;
    }

    private SpectralizationConfig() {
    }
}
