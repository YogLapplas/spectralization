package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalTraceTermination;
import io.github.yoglappland.spectralization.optics.OpticalTraceTerminationReason;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutput;
import io.github.yoglappland.spectralization.optics.compiler.gain.GainSchedule;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionAllocation;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionContinuity;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class OpticalCompilerDebugLogger {
    private static final DateTimeFormatter SESSION_LOG_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss_'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final String SESSION_LOG_FILE_NAME =
            "optical_compiler_" + SESSION_LOG_TIMESTAMP.format(Instant.now()) + ".log";
    private static final String LOG_RELATIVE_PATH = "logs/spectralization/" + SESSION_LOG_FILE_NAME;
    private static final String AUTH_GAMEPLAY = "gameplay";
    private static final String AUTH_REFERENCE = "reference";
    private static final String AUTH_DEBUG_ORACLE = "debug_oracle";
    private static final String AUTH_LEGACY_COMPARE = "legacy_compare";
    private static final double SPOT_TINY_TEXTURE_AREA_THRESHOLD = 1.0E-4D;
    private static final double SPOT_LARGE_STRETCH_RATIO_THRESHOLD = 200.0D;
    private static final Comparator<PortGraphNode> NODE_COMPARATOR = Comparator
            .comparingInt((PortGraphNode node) -> node.pos().getX())
            .thenComparingInt(node -> node.pos().getY())
            .thenComparingInt(node -> node.pos().getZ())
            .thenComparingInt(node -> node.side().ordinal())
            .thenComparingInt(node -> node.waveKind().ordinal());

    public static void logObservedTrace(Level level, CompiledOpticalTrace trace, CompiledPortGraph graph) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(4096);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=observed\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(AUTH_DEBUG_ORACLE).append('\n');
        builder.append("source=").append(formatPos(trace.sourcePos()))
                .append(" direction=").append(trace.sourceOutput().outgoingDirection())
                .append(" power=").append(formatPower(trace.sourceOutput().beam().totalPower()))
                .append('\n');
        builder.append("effect_trace_max_states=")
                .append(SpectralizationConfig.opticalEffectTraceMaxStates())
                .append('\n');
        builder.append("nodes=").append(graph.nodes().size())
                .append(" edges=").append(graph.edges().size())
                .append(" sccs=").append(graph.sccs().size())
                .append(" feedback_sccs=").append(graph.feedbackSccCount())
                .append(" beta1=").append(graph.beta1())
                .append(" chords=").append(graph.chords().size())
                .append(" trace_steps=").append(trace.steps().size())
                .append(" terminations=").append(trace.terminations().size())
                .append('\n');

        appendTerminationSummary(builder, trace);
        if (verboseLog()) {
            appendSccs(builder, graph);
            appendChords(builder, graph);
            appendEdges(builder, graph);
        } else {
            appendVerboseSectionsSkipped(builder);
        }
        builder.append('\n');
        write(builder.toString());
    }

    public static void logObservedAndDirect(
            Level level,
            CompiledOpticalTrace trace,
            CompiledPortGraph observedGraph,
            CompiledPortGraph directGraph,
            ScalarPowerSolution scalarPowerSolution
    ) {
        logObservedDirectAndNetwork(
                level,
                trace,
                observedGraph,
                directGraph,
                scalarPowerSolution,
                false,
                0,
                0,
                0,
                0,
                null,
                null,
                0,
                0,
                true,
                true,
                false,
                List.of(),
                List.of(),
                List.of(),
                isLegacyTraceComparable(trace),
                0,
                0,
                List.of(),
                true,
                isLegacyTraceComparable(trace)
        );
    }

    public static void logObservedDirectAndNetwork(
            Level level,
            CompiledOpticalTrace trace,
            CompiledPortGraph observedGraph,
            CompiledPortGraph directGraph,
            ScalarPowerSolution scalarPowerSolution,
            boolean directGeometryCacheHit,
            int directGeometrySignaturePositions,
            int directGeometryCacheEntries,
            int directGeometryCacheLimit,
            int heldReadoutCacheEntries,
            CompiledPortGraph networkGraph,
            ScalarPowerSolution networkSolution,
            int networkSourceCount,
            int networkSystemId,
            boolean networkStructurallyFresh,
            boolean networkParametricallyFresh,
            boolean networkUsableForGameplay,
            List<ReceiverOutput> legacyReceiverOutputs,
            List<ReceiverOutput> directReceiverOutputs,
            List<ReceiverOutput> networkReceiverOutputs,
            boolean legacyComparable,
            int directReadoutBindingCount,
            int networkReadoutBindingCount,
            List<ReceiverOutput> systemLegacyReceiverOutputs,
            boolean systemLegacyComplete,
            boolean systemLegacyComparable
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(8192);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=observed_vs_direct\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(AUTH_LEGACY_COMPARE).append('\n');
        builder.append("source=").append(formatPos(trace.sourcePos()))
                .append(" direction=").append(trace.sourceOutput().outgoingDirection())
                .append(" power=").append(formatPower(trace.sourceOutput().beam().totalPower()))
                .append('\n');
        builder.append("effect_trace_max_states=")
                .append(SpectralizationConfig.opticalEffectTraceMaxStates())
                .append('\n');
        appendGraphSummary(builder, "observed", observedGraph, trace.steps().size(), trace.terminations().size());
        appendGraphSummary(builder, "direct", directGraph, -1, directGraph.terminationCount());
        appendTemplateSummary(builder, "direct", level, directGraph);
        appendScalarSolution(builder, "direct_scalar", scalarPowerSolution, AUTH_REFERENCE);
        builder.append("direct_geometry_cache hit=").append(directGeometryCacheHit)
                .append(" signature_positions=").append(directGeometrySignaturePositions)
                .append(" entries=").append(directGeometryCacheEntries)
                .append(" limit=").append(directGeometryCacheLimit)
                .append('\n');
        builder.append("held_readout_cache entries=").append(heldReadoutCacheEntries).append('\n');

        if (networkGraph != null && networkSolution != null) {
            builder.append("network_at_direct_stage available=true\n");
            builder.append("network sources=").append(networkSourceCount).append(' ');
            appendGraphSummary(builder, "", networkGraph, -1, networkGraph.terminationCount());
            appendTemplateSummary(builder, "network", level, networkGraph);
            appendSourceVectorSummary(builder, level, networkGraph, networkSourceCount);
            builder.append("network_cache system_id=").append(networkSystemId)
                    .append(" structurally_fresh=").append(networkStructurallyFresh)
                    .append(" parametrically_fresh=").append(networkParametricallyFresh)
                    .append(" usable_for_gameplay=").append(networkUsableForGameplay)
                    .append('\n');
            appendScalarSolution(
                    builder,
                    "network_scalar",
                    networkSolution,
                    networkUsableForGameplay ? AUTH_GAMEPLAY : AUTH_REFERENCE
            );
        } else {
            builder.append("network_at_direct_stage available=false reason=not_installed_yet_or_rebuild_pending\n");
        }

        appendReceiverOutputs(
                builder,
                legacyReceiverOutputs,
                directReceiverOutputs,
                networkReceiverOutputs,
                legacyComparable,
                scalarPowerSolution.reliableForReadout(),
                networkSolution != null && networkSolution.reliableForReadout(),
                directReadoutBindingCount,
                networkReadoutBindingCount
        );
        appendSystemReceiverOutputs(
                builder,
                systemLegacyReceiverOutputs,
                networkReceiverOutputs,
                systemLegacyComplete,
                systemLegacyComparable,
                networkSolution != null && networkSolution.reliableForReadout()
        );

        builder.append("delta direct_minus_observed")
                .append(" nodes=").append(directGraph.nodes().size() - observedGraph.nodes().size())
                .append(" edges=").append(directGraph.edges().size() - observedGraph.edges().size())
                .append(" sccs=").append(directGraph.sccs().size() - observedGraph.sccs().size())
                .append(" feedback_sccs=").append(directGraph.feedbackSccCount() - observedGraph.feedbackSccCount())
                .append(" beta1=").append(directGraph.beta1() - observedGraph.beta1())
                .append(" chords=").append(directGraph.chords().size() - observedGraph.chords().size())
                .append('\n');

        appendTerminationSummary(builder, trace);
        if (verboseLog()) {
            appendSccs(builder, "observed", observedGraph);
            appendChords(builder, "observed", observedGraph);
            appendSccs(builder, "direct", directGraph);
            appendChords(builder, "direct", directGraph);
            appendStrongestNodes(builder, "direct_scalar", scalarPowerSolution);

            if (networkGraph != null && networkSolution != null) {
                appendSccs(builder, "network", networkGraph);
                appendChords(builder, "network", networkGraph);
                appendStrongestNodes(builder, "network_scalar", networkSolution);
            }

            appendEdges(builder, "direct", directGraph);
        } else {
            appendVerboseSectionsSkipped(builder);
        }
        builder.append('\n');
        write(builder.toString());
    }

    public static void logDirectOnly(
            Level level,
            BlockPos sourcePos,
            OutputBeam sourceOutput,
            CompiledPortGraph directGraph,
            ScalarPowerSolution scalarPowerSolution,
            boolean directGeometryCacheHit,
            int directGeometrySignaturePositions,
            int directGeometryCacheEntries,
            int directGeometryCacheLimit,
            int heldReadoutCacheEntries,
            CompiledPortGraph networkGraph,
            ScalarPowerSolution networkSolution,
            int networkSourceCount,
            int networkSystemId,
            boolean networkStructurallyFresh,
            boolean networkParametricallyFresh,
            boolean networkUsableForGameplay,
            List<ReceiverOutput> directReceiverOutputs,
            List<ReceiverOutput> networkReceiverOutputs,
            int directReadoutBindingCount,
            int networkReadoutBindingCount
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(8192);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=direct_only\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(networkUsableForGameplay ? AUTH_REFERENCE : AUTH_GAMEPLAY).append('\n');
        builder.append("source=").append(formatPos(sourcePos))
                .append(" direction=").append(sourceOutput.outgoingDirection())
                .append(" power=").append(formatPower(sourceOutput.beam().totalPower()))
                .append('\n');
        builder.append("legacy_observed_trace=skipped_feedback_or_disabled\n");
        appendGraphSummary(builder, "direct", directGraph, -1, directGraph.terminationCount());
        appendTemplateSummary(builder, "direct", level, directGraph);
        appendScalarSolution(
                builder,
                "direct_scalar",
                scalarPowerSolution,
                networkUsableForGameplay ? AUTH_REFERENCE : AUTH_GAMEPLAY
        );
        builder.append("direct_geometry_cache hit=").append(directGeometryCacheHit)
                .append(" signature_positions=").append(directGeometrySignaturePositions)
                .append(" entries=").append(directGeometryCacheEntries)
                .append(" limit=").append(directGeometryCacheLimit)
                .append('\n');
        builder.append("held_readout_cache entries=").append(heldReadoutCacheEntries).append('\n');

        if (networkGraph != null && networkSolution != null) {
            builder.append("network_at_direct_stage available=true\n");
            builder.append("network sources=").append(networkSourceCount).append(' ');
            appendGraphSummary(builder, "", networkGraph, -1, networkGraph.terminationCount());
            appendTemplateSummary(builder, "network", level, networkGraph);
            appendSourceVectorSummary(builder, level, networkGraph, networkSourceCount);
            builder.append("network_cache system_id=").append(networkSystemId)
                    .append(" structurally_fresh=").append(networkStructurallyFresh)
                    .append(" parametrically_fresh=").append(networkParametricallyFresh)
                    .append(" usable_for_gameplay=").append(networkUsableForGameplay)
                    .append('\n');
            appendScalarSolution(
                    builder,
                    "network_scalar",
                    networkSolution,
                    networkUsableForGameplay ? AUTH_GAMEPLAY : AUTH_REFERENCE
            );
        } else {
            builder.append("network_at_direct_stage available=false reason=not_installed_yet_or_rebuild_pending\n");
        }

        appendReceiverOutputs(
                builder,
                List.of(),
                directReceiverOutputs,
                networkReceiverOutputs,
                false,
                scalarPowerSolution.reliableForReadout(),
                networkSolution != null && networkSolution.reliableForReadout(),
                directReadoutBindingCount,
                networkReadoutBindingCount
        );
        if (verboseLog()) {
            appendSccs(builder, "direct", directGraph);
            appendChords(builder, "direct", directGraph);
            appendStrongestNodes(builder, "direct_scalar", scalarPowerSolution);

            if (networkGraph != null && networkSolution != null) {
                appendSccs(builder, "network", networkGraph);
                appendChords(builder, "network", networkGraph);
                appendStrongestNodes(builder, "network_scalar", networkSolution);
            }

            appendEdges(builder, "direct", directGraph);
        } else {
            appendVerboseSectionsSkipped(builder);
        }
        builder.append('\n');
        write(builder.toString());
    }

    public static void logExampleValidation(
            Level level,
            String testName,
            BlockPos sourcePos,
            Direction direction,
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            List<ReceiverOutput> receiverOutputs,
            boolean passed,
            String message,
            double actualPower,
            double expectedPower,
            double tolerance
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(8192);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=example_validation\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(AUTH_REFERENCE).append('\n');
        builder.append("test=").append(testName)
                .append(" passed=").append(passed)
                .append(" message=\"").append(message.replace("\"", "\\\"")).append('"')
                .append('\n');
        builder.append("source=").append(formatPos(sourcePos))
                .append(" direction=").append(direction)
                .append(" actual=").append(formatPower(actualPower))
                .append(" expected=").append(formatPower(expectedPower))
                .append(" delta=").append(formatPower(actualPower - expectedPower))
                .append(" tolerance=").append(formatPower(tolerance))
                .append(" profile_mode=").append(profileMode(solution))
                .append(" profile_overflow=").append(solution.profileOverflow())
                .append(" profile_fallback=").append(solution.profileCollapsedFallback())
                .append('\n');
        appendGraphSummary(builder, "example", graph, -1, graph.terminationCount());
        appendTemplateSummary(builder, "example", level, graph);
        appendScalarSolution(builder, "example_scalar", solution, AUTH_REFERENCE);
        appendReadoutOutputList(builder, "example", receiverOutputs);

        if (verboseLog() || !passed) {
            appendSccs(builder, "example", graph);
            appendChords(builder, "example", graph);
            appendStrongestNodes(builder, "example_scalar", solution);
            appendEdges(builder, "example", graph);
        } else {
            appendVerboseSectionsSkipped(builder);
        }

        builder.append('\n');
        write(builder.toString());
    }

    public static void logSystemRebuildDeferred(
            Level level,
            int requestedNetworkId,
            int componentNetworkCount,
            int staleSourceRecordCount,
            int queuedSourceRefreshCount,
            int pendingAfter,
            String reason
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(384);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=system_rebuild_deferred\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(AUTH_REFERENCE).append('\n');
        builder.append("requested_network=").append(requestedNetworkId)
                .append(" reason=").append(reason)
                .append(" component_networks=").append(componentNetworkCount)
                .append(" stale_source_records=").append(staleSourceRecordCount)
                .append(" queued_source_refreshes=").append(queuedSourceRefreshCount)
                .append(" pending_after=").append(pendingAfter)
                .append('\n');
        builder.append('\n');
        write(builder.toString());
    }

    public static void logSystemRebuild(
            Level level,
            int requestedNetworkId,
            int rebuiltNetworkCount,
            int pendingAfter,
            long quietTicks,
            boolean systemCacheHit,
            int systemCacheEntries,
            int systemCacheLimit,
            int systemId,
            int sourceCount,
            int readoutBindingCount,
            int receiverOutputCount,
            boolean usableForGameplay,
            boolean structurallyFresh,
            boolean parametricallyFresh,
            boolean gameplayCandidate,
            boolean solutionReliable,
            String solverKind,
            CompiledPortGraph baseCoherentGraph,
            CompiledPortGraph coherentGraph
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(512);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=system_rebuild\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(usableForGameplay ? AUTH_GAMEPLAY : AUTH_REFERENCE).append('\n');
        builder.append("requested_network=").append(requestedNetworkId)
                .append(" rebuilt_networks=").append(rebuiltNetworkCount)
                .append(" pending_after=").append(pendingAfter)
                .append(" quiet_ticks=").append(quietTicks)
                .append(" system_cache_hit=").append(systemCacheHit)
                .append(" system_cache_entries=").append(systemCacheEntries)
                .append(" system_cache_limit=").append(systemCacheLimit)
                .append(" system_id=").append(systemId)
                .append(" sources=").append(sourceCount)
                .append(" readout_bindings=").append(readoutBindingCount)
                .append(" receiver_outputs=").append(receiverOutputCount)
                .append(" usable_for_gameplay=").append(usableForGameplay)
                .append(" structurally_fresh=").append(structurallyFresh)
                .append(" parametrically_fresh=").append(parametricallyFresh)
                .append(" gameplay_candidate=").append(gameplayCandidate)
                .append(" solution_reliable=").append(solutionReliable)
                .append(" solver=").append(solverKind)
                .append('\n');
        appendSystemGraphFingerprint(builder, "base_coherent", baseCoherentGraph);
        appendSystemGraphFingerprint(builder, "coherent", coherentGraph);
        builder.append('\n');
        write(builder.toString());
    }

    private static void appendSystemGraphFingerprint(
            StringBuilder builder,
            String label,
            CompiledPortGraph graph
    ) {
        if (graph == null) {
            builder.append(label).append("_graph=none\n");
            return;
        }

        builder.append(label).append("_graph")
                .append(" nodes=").append(graph.nodes().size())
                .append(" edges=").append(graph.edges().size())
                .append(" beta1=").append(graph.beta1())
                .append(" hash=").append(Long.toUnsignedString(graphFingerprint(graph), 16))
                .append('\n');
    }

    private static long graphFingerprint(CompiledPortGraph graph) {
        long hash = 0x6A09E667F3BCC909L;
        List<PortGraphNode> nodes = new ArrayList<>(graph.nodes());
        nodes.sort(NODE_COMPARATOR);

        for (PortGraphNode node : nodes) {
            hash = mix(hash, node.pos().asLong());
            hash = mix(hash, node.side().ordinal());
            hash = mix(hash, node.waveKind().ordinal());
        }

        List<PortGraphEdge> edges = new ArrayList<>(graph.edges());
        edges.sort(Comparator
                .comparing((PortGraphEdge edge) -> edge.from(), NODE_COMPARATOR)
                .thenComparing(edge -> edge.to(), NODE_COMPARATOR)
                .thenComparing(edge -> edge.kind().ordinal())
                .thenComparingDouble(PortGraphEdge::sampleGain));

        for (PortGraphEdge edge : edges) {
            hash = mix(hash, edge.from().pos().asLong());
            hash = mix(hash, edge.from().side().ordinal());
            hash = mix(hash, edge.from().waveKind().ordinal());
            hash = mix(hash, edge.to().pos().asLong());
            hash = mix(hash, edge.to().side().ordinal());
            hash = mix(hash, edge.to().waveKind().ordinal());
            hash = mix(hash, edge.kind().ordinal());
            hash = mix(hash, Double.doubleToLongBits(edge.sampleGain()));
        }

        return hash;
    }

    private static long mix(long hash, long value) {
        long mixedValue = value + 0x9E3779B97F4A7C15L + (hash << 6) + (hash >>> 2);
        return hash ^ mixedValue;
    }

    public static void logReadoutApply(
            Level level,
            int networkId,
            BlockPos sourcePos,
            Direction sourceDirection,
            String mode,
            boolean reliable,
            long readoutStep,
            boolean systemRebuildPending,
            int directReadoutBindingCount,
            int directReceiverOutputCount,
            boolean directSolutionReliable,
            int directSourceOutputCount,
            boolean systemAvailable,
            int systemId,
            int systemSourceCount,
            int systemReadoutBindingCount,
            int systemReceiverOutputCount,
            boolean systemUsableForGameplay,
            boolean systemSolutionReliable,
            int heldReadoutCacheEntries
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(1024);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=readout_apply\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(reliable ? AUTH_GAMEPLAY : AUTH_REFERENCE).append('\n');
        builder.append("network_id=").append(networkId)
                .append(" source=").append(formatPos(sourcePos))
                .append(" direction=").append(sourceDirection)
                .append(" mode=").append(mode)
                .append(" reliable=").append(reliable)
                .append(" readout_step=").append(readoutStep)
                .append(" system_rebuild_pending=").append(systemRebuildPending)
                .append('\n');
        builder.append("direct_readout")
                .append(" bindings=").append(directReadoutBindingCount)
                .append(" receiver_outputs=").append(directReceiverOutputCount)
                .append(" source_outputs=").append(directSourceOutputCount)
                .append(" solution_reliable=").append(directSolutionReliable)
                .append('\n');
        builder.append("system_readout")
                .append(" available=").append(systemAvailable)
                .append(" system_id=").append(systemId)
                .append(" sources=").append(systemSourceCount)
                .append(" bindings=").append(systemReadoutBindingCount)
                .append(" receiver_outputs=").append(systemReceiverOutputCount)
                .append(" usable_for_gameplay=").append(systemUsableForGameplay)
                .append(" solution_reliable=").append(systemSolutionReliable)
                .append('\n');
        builder.append("held_readout_cache entries=").append(heldReadoutCacheEntries).append('\n');
        builder.append('\n');
        write(builder.toString());
    }

    public static void logReadoutApplyDetails(
            Level level,
            int networkId,
            BlockPos sourcePos,
            Direction sourceDirection,
            String mode,
            boolean reliable,
            long readoutStep,
            List<ReceiverOutput> directReceiverOutputs,
            List<ReceiverOutput> systemReceiverOutputs,
            List<ReceiverOutput> heldReceiverOutputs
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()
                || (!verboseLog() && reliable)) {
            return;
        }

        StringBuilder builder = new StringBuilder(2048);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=readout_apply_details\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(reliable ? AUTH_GAMEPLAY : AUTH_REFERENCE).append('\n');
        builder.append("network_id=").append(networkId)
                .append(" source=").append(formatPos(sourcePos))
                .append(" direction=").append(sourceDirection)
                .append(" mode=").append(mode)
                .append(" reliable=").append(reliable)
                .append(" readout_step=").append(readoutStep)
                .append('\n');
        appendReadoutOutputList(builder, "direct", directReceiverOutputs);
        appendReadoutOutputList(builder, "system", systemReceiverOutputs);
        appendReadoutOutputList(builder, "held", heldReceiverOutputs);
        builder.append('\n');
        write(builder.toString());
    }

    public static void logBeamHudOverlay(
            Level level,
            int networkId,
            BlockPos sourcePos,
            Direction sourceDirection,
            String mode,
            int segmentCount,
            List<BeamPathOverlayPayload.Segment> segments,
            int sentPlayers,
            int terminalRayBlocks,
            long gameTime
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(512);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=beam_hud_overlay\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("network_id=").append(networkId)
                .append(" source=").append(formatPos(sourcePos))
                .append(" direction=").append(sourceDirection)
                .append(" mode=").append(mode)
                .append(" layer=topology")
                .append(" segments=").append(segmentCount)
                .append(" sent_players=").append(sentPlayers)
                .append(" terminal_ray_blocks=").append(terminalRayBlocks)
                .append(" game_time=").append(gameTime)
                .append('\n');

        if (!verboseLog()) {
            builder.append("segment_details=skipped_non_verbose\n\n");
            write(builder.toString());
            return;
        }

        if (segments.isEmpty()) {
            builder.append("segment_details: none\n\n");
            write(builder.toString());
            return;
        }

        builder.append("segment_details:\n");
        int maxRows = Math.max(1, Math.min(32, SpectralizationConfig.opticalCompilerDebugMaxEdges()));
        int rowCount = 0;

        for (BeamPathOverlayPayload.Segment segment : segments) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(segments.size() - rowCount).append(" more segments\n");
                break;
            }

            builder.append("  ")
                    .append(formatPos(segment.from()))
                    .append(" -> ")
                    .append(formatPos(segment.to()))
                    .append(" direction=").append(segment.direction())
                    .append(" coherent=").append(segment.coherent())
                    .append(" rgb=").append(formatRgb(segment.colorRgb()))
                    .append(" width=").append(segment.widthLevel())
                    .append(" visual=").append(segment.visualLevel())
                    .append('\n');
            rowCount++;
        }

        builder.append('\n');
        write(builder.toString());
    }

    public static void logSpotOverlay(
            Level level,
            List<SpotRecord> activeSpots,
            List<SpotProjectionAllocation> allocations,
            int sentSpots,
            int sentPlayers,
            long gameTime
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(1024);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=spot_overlay\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("layer=surface")
                .append(" active_spots=").append(activeSpots.size())
                .append(" sent_spots=").append(sentSpots)
                .append(" sent_players=").append(sentPlayers)
                .append(" slice_quantization=").append(SpotRecord.SLICE_QUANTIZATION_LEVEL)
                .append(" quad_quantization=").append(SpotRecord.QUAD_QUANTIZATION_LEVEL)
                .append(" game_time=").append(gameTime)
                .append('\n');

        if (activeSpots.isEmpty()) {
            builder.append("spot_details: none\n\n");
            write(builder.toString());
            return;
        }

        EnumMap<SpotRecord.ProjectionMode, Integer> projectionCounts =
                new EnumMap<>(SpotRecord.ProjectionMode.class);
        EnumMap<Direction, Integer> quadFaceCounts = new EnumMap<>(Direction.class);
        for (SpotRecord spot : activeSpots) {
            projectionCounts.merge(spot.projectionMode(), 1, Integer::sum);
            if (spot.projectionMode() == SpotRecord.ProjectionMode.FOOTPRINT_QUAD) {
                quadFaceCounts.merge(spot.face(), 1, Integer::sum);
            }
        }

        builder.append("spot_projection_counts")
                .append(" centered=").append(projectionCounts.getOrDefault(SpotRecord.ProjectionMode.CENTERED_QUAD, 0))
                .append(" slice=").append(projectionCounts.getOrDefault(SpotRecord.ProjectionMode.FOOTPRINT_SLICE, 0))
                .append(" quad=").append(projectionCounts.getOrDefault(SpotRecord.ProjectionMode.FOOTPRINT_QUAD, 0))
                .append(" debug=").append(projectionCounts.getOrDefault(SpotRecord.ProjectionMode.DEBUG_FACE_CENTER, 0))
                .append(" quad_faces=").append(formatDirectionCounts(quadFaceCounts))
                .append('\n');
        appendDebugFaceMarkers(builder, activeSpots);

        SpotProjectionContinuity.Report continuityReport = SpotProjectionContinuity.inspect(activeSpots, 8);
        ProjectionMetricSummary metricSummary = summarizeProjectionMetrics(activeSpots);
        builder.append("spot_projection_continuity")
                .append(" surfaces=").append(continuityReport.surfaceCount())
                .append(" shared_edges=").append(continuityReport.sharedEdges())
                .append(" uv_mismatches=").append(continuityReport.mismatchCount())
                .append(" texture_gap_max=").append(continuityReport.maxTextureGap())
                .append('\n');
        builder.append("spot_projection_metrics")
                .append(" surfaces=").append(metricSummary.count())
                .append(" original_area_sum=").append(formatDouble(metricSummary.originalAreaSum()))
                .append(" world_area_sum=").append(formatDouble(metricSummary.worldAreaSum()))
                .append(" stretch_min=").append(formatDouble(metricSummary.stretchMin()))
                .append(" stretch_max=").append(formatDouble(metricSummary.stretchMax()))
                .append(" stretch_avg=").append(formatDouble(metricSummary.stretchAverage()))
                .append(" tiny_texture_patches=").append(metricSummary.tinyTexturePatches())
                .append(" large_stretch_patches=").append(metricSummary.largeStretchPatches())
                .append('\n');
        appendProjectionFaceMetrics(builder, activeSpots);
        if (verboseLog()) {
            appendProjectionAllocationMetrics(builder, allocations);
        } else {
            builder.append("spot_projection_allocation_summary=skipped_non_verbose\n");
            builder.append("spot_projection_allocation_metrics=skipped_non_verbose\n");
        }

        for (SpotProjectionContinuity.Mismatch mismatch : continuityReport.mismatches()) {
            builder.append("  continuity_mismatch ").append(mismatch.format()).append('\n');
        }

        if (!verboseLog()) {
            builder.append("spot_details=skipped_non_verbose\n\n");
            write(builder.toString());
            return;
        }

        builder.append("spot_details:\n");
        int maxRows = Math.max(1, Math.min(32, SpectralizationConfig.opticalCompilerDebugMaxEdges()));
        int rowCount = 0;

        for (SpotRecord spot : activeSpots) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(activeSpots.size() - rowCount).append(" more spots\n");
                break;
            }

            builder.append("  pos=").append(formatPos(spot.pos()))
                    .append(" face=").append(spot.face())
                    .append(" coherent_alpha=").append(spot.coherentAlphaLevel())
                    .append(" coherent_radius=").append(spot.coherentRadiusLevel())
                    .append(" coherent_rgb=").append(formatRgb(spot.coherentRed(), spot.coherentGreen(), spot.coherentBlue()))
                    .append(" stray_alpha=").append(spot.strayAlphaLevel())
                    .append(" stray_radius=").append(spot.strayRadiusLevel())
                    .append(" stray_rgb=").append(formatRgb(spot.strayRed(), spot.strayGreen(), spot.strayBlue()))
                    .append(" ring_alpha=").append(spot.ringAlphaLevel())
                    .append(" projection=").append(spot.projectionMode())
                    .append(" debug_marker=").append(formatDebugMarker(spot.debugMarker()))
                    .append(" clip=").append(spot.clipMinU()).append(',')
                    .append(spot.clipMinV()).append(',')
                    .append(spot.clipMaxU()).append(',')
                    .append(spot.clipMaxV())
                    .append(" texture=").append(spot.textureMinU()).append(',')
                    .append(spot.textureMinV()).append(',')
                    .append(spot.textureMaxU()).append(',')
                    .append(spot.textureMaxV());

            if (spot.projectionMode() == SpotRecord.ProjectionMode.FOOTPRINT_QUAD) {
                builder.append(" quad=")
                        .append(formatQuadVertex(spot.quadX0(), spot.quadY0(), spot.quadZ0(), spot.quadTextureU0(), spot.quadTextureV0()))
                        .append(';')
                        .append(formatQuadVertex(spot.quadX1(), spot.quadY1(), spot.quadZ1(), spot.quadTextureU1(), spot.quadTextureV1()))
                        .append(';')
                        .append(formatQuadVertex(spot.quadX2(), spot.quadY2(), spot.quadZ2(), spot.quadTextureU2(), spot.quadTextureV2()))
                        .append(';')
                        .append(formatQuadVertex(spot.quadX3(), spot.quadY3(), spot.quadZ3(), spot.quadTextureU3(), spot.quadTextureV3()));
            }

            SpotProjectionContinuity.Metrics metrics = SpotProjectionContinuity.metrics(spot);
            if (metrics.originalArea() > 0.0D) {
                builder.append(" projection_metric=")
                        .append("original_area=").append(formatDouble(metrics.originalArea()))
                        .append(",world_area=").append(formatDouble(metrics.worldArea()))
                        .append(",stretch_ratio=").append(formatDouble(metrics.stretchRatio()));
            }

            builder.append('\n');
            rowCount++;
        }

        builder.append('\n');
        write(builder.toString());
    }

    public static void logGainSchedule(Level level, CompiledPortGraph graph, GainSchedule schedule) {
        if (!SpectralizationConfig.opticalCompilerDebugLog() || schedule.gainSourceCount() <= 0) {
            return;
        }

        StringBuilder builder = new StringBuilder(1024);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=gain_schedule\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("source=").append(formatPos(graph.sourcePos()))
                .append(" direction=").append(graph.sourceDirection())
                .append(" nodes=").append(graph.nodes().size())
                .append(" edges=").append(graph.edges().size())
                .append(" feedback_sccs=").append(graph.feedbackSccCount())
                .append(" beta1=").append(graph.beta1())
                .append('\n');
        builder.append("gain_schedule")
                .append(" sources=").append(schedule.gainSourceCount())
                .append(" scheduled=").append(schedule.scheduled())
                .append(" mode=").append(schedule.schedulerMode())
                .append(" total_gain_headroom=").append(formatPower(schedule.totalGainHeadroom()))
                .append(" max_base_gain=").append(formatPower(schedule.maxBaseGain()))
                .append(" max_effective_gain=").append(formatPower(schedule.maxEffectiveGain()))
                .append('\n');
        appendGraphSaturationSummary(builder, "scheduled_graph_saturation", schedule.graph());
        appendGainSourceDebugInfo(builder, schedule);
        builder.append('\n');
        write(builder.toString());
    }

    private static void appendGainSourceDebugInfo(StringBuilder builder, GainSchedule schedule) {
        if (schedule.gainSources().isEmpty()) {
            return;
        }

        builder.append("gain_source_details:\n");
        int maxRows = verboseLog()
                ? Math.max(1, SpectralizationConfig.opticalCompilerDebugMaxEdges())
                : 8;
        int rowCount = 0;

        for (GainSchedule.GainSourceDebugInfo source : schedule.gainSources()) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(schedule.gainSources().size() - rowCount)
                        .append(" more gain sources\n");
                return;
            }

            builder.append("  edge=").append(source.edgeId())
                    .append(" scc=").append(source.sccId())
                    .append(" pos=").append(source.x()).append(',').append(source.y()).append(',').append(source.z())
                    .append(" material=").append(source.materialId())
                    .append(" scope=").append(source.spectralScope())
                    .append(" material_weight=").append(formatPower(source.materialWeight()))
                    .append(" base_gain=").append(formatPower(source.baseGain()))
                    .append(" saturated_extra_output=").append(formatPower(source.saturatedExtraOutput()))
                    .append(" base_frequencies=").append(source.baseFrequencyCount())
                    .append(" saturated_frequencies=").append(source.saturatedFrequencyCount())
                    .append('\n');
            rowCount++;
        }
    }

    public static void logRubySeedSynthesis(
            Level level,
            CompiledPortGraph graph,
            int rubySeedNodeCount,
            int rubyModeCount,
            double totalStraySeedPower,
            double totalConvertedPower,
            double maxConvertedPower,
            int maxPumpRate
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()
                || rubySeedNodeCount <= 0
                || totalConvertedPower <= 0.0) {
            return;
        }

        StringBuilder builder = new StringBuilder(768);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=ruby_seed_synthesis\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("source=").append(formatPos(graph.sourcePos()))
                .append(" direction=").append(graph.sourceDirection())
                .append(" nodes=").append(graph.nodes().size())
                .append(" edges=").append(graph.edges().size())
                .append(" feedback_sccs=").append(graph.feedbackSccCount())
                .append(" beta1=").append(graph.beta1())
                .append('\n');
        builder.append("ruby_seed")
                .append(" seed_nodes=").append(rubySeedNodeCount)
                .append(" modes=").append(rubyModeCount)
                .append(" max_pump_rate=").append(maxPumpRate)
                .append(" stray_seed_power=").append(formatPower(totalStraySeedPower))
                .append(" coherent_source_power=").append(formatPower(totalConvertedPower))
                .append(" max_node_source_power=").append(formatPower(maxConvertedPower))
                .append('\n');
        builder.append('\n');
        write(builder.toString());
    }

    public static void logPowerChannelSolve(
            Level level,
            CompiledPortGraph passiveGraph,
            CompiledPortGraph coherentGraph,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> incoherentSourcePowersByLane,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> directCoherentSourcePowersByLane,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> coherentSourcePowersByLane,
            int incoherentSourceCount,
            double incoherentSourcePower,
            int directCoherentSourceCount,
            double directCoherentSourcePower,
            int rubySeedNodeCount,
            int rubyModeCount,
            double rubyStraySeedPower,
            double rubyCoherentSourcePower,
            int coherentSourceCount,
            double coherentSourcePower,
            GainSchedule gainSchedule,
            ScalarPowerSolution incoherentSolution,
            ScalarPowerSolution coherentSolution,
            ScalarPowerSolution combinedSolution
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()
                || (coherentSourceCount <= 0
                && directCoherentSourceCount <= 0
                && rubySeedNodeCount <= 0
                && rubyCoherentSourcePower <= 0.0)) {
            return;
        }

        StringBuilder builder = new StringBuilder(4096);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=power_channel_solve\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(AUTH_GAMEPLAY).append('\n');
        builder.append("source=").append(formatPos(coherentGraph.sourcePos()))
                .append(" direction=").append(coherentGraph.sourceDirection())
                .append('\n');
        builder.append("passive_graph")
                .append(" nodes=").append(passiveGraph.nodes().size())
                .append(" edges=").append(passiveGraph.edges().size())
                .append(" feedback_sccs=").append(passiveGraph.feedbackSccCount())
                .append(" beta1=").append(passiveGraph.beta1())
                .append('\n');
        builder.append("coherent_graph")
                .append(" nodes=").append(coherentGraph.nodes().size())
                .append(" edges=").append(coherentGraph.edges().size())
                .append(" feedback_sccs=").append(coherentGraph.feedbackSccCount())
                .append(" beta1=").append(coherentGraph.beta1())
                .append('\n');
        appendGraphSaturationSummary(builder, "passive_graph_saturation", passiveGraph);
        appendGraphSaturationSummary(builder, "coherent_graph_saturation", coherentGraph);
        builder.append("source_vectors")
                .append(" incoherent_count=").append(incoherentSourceCount)
                .append(" incoherent_power=").append(formatPower(incoherentSourcePower))
                .append(" direct_coherent_count=").append(directCoherentSourceCount)
                .append(" direct_coherent_power=").append(formatPower(directCoherentSourcePower))
                .append(" ruby_seed_nodes=").append(rubySeedNodeCount)
                .append(" ruby_modes=").append(rubyModeCount)
                .append(" ruby_stray_seed_power=").append(formatPower(rubyStraySeedPower))
                .append(" ruby_coherent_power=").append(formatPower(rubyCoherentSourcePower))
                .append(" coherent_count=").append(coherentSourceCount)
                .append(" coherent_power=").append(formatPower(coherentSourcePower))
                .append('\n');
        appendLanePowerSummary(builder, "incoherent_source_lanes", incoherentSourcePowersByLane, 32);
        appendLanePowerSummary(builder, "direct_coherent_source_lanes", directCoherentSourcePowersByLane, 32);
        appendLanePowerSummary(builder, "coherent_source_lanes", coherentSourcePowersByLane, 32);
        builder.append("gain_schedule")
                .append(" scheduled=").append(gainSchedule.scheduled())
                .append(" mode=").append(gainSchedule.schedulerMode())
                .append(" total_gain_headroom=").append(formatPower(gainSchedule.totalGainHeadroom()))
                .append(" max_base_gain=").append(formatPower(gainSchedule.maxBaseGain()))
                .append(" max_effective_gain=").append(formatPower(gainSchedule.maxEffectiveGain()))
                .append('\n');
        appendScalarSolution(builder, "incoherent_channel", incoherentSolution, AUTH_GAMEPLAY);
        appendScalarSolution(builder, "coherent_channel", coherentSolution, AUTH_GAMEPLAY);
        appendScalarSolution(builder, "combined_channels", combinedSolution, AUTH_GAMEPLAY);
        if (verboseLog()) {
            appendStrongestNodes(builder, "coherent_channel", coherentSolution);
            appendStrongestNodes(builder, "combined_channels", combinedSolution);
        } else {
            appendVerboseSectionsSkipped(builder);
        }
        builder.append('\n');
        write(builder.toString());
    }

    public static void logSpectralLaneSolve(
            Level level,
            String channel,
            CompiledPortGraph graph,
            int requestedLaneCount,
            int executedLaneCount,
            boolean parallel,
            long elapsedNanos,
            ScalarPowerSolution solution
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        StringBuilder builder = new StringBuilder(2048);
        builder.append("=== spectralization optical compiler ===\n");
        builder.append("session_log=").append(SESSION_LOG_FILE_NAME).append('\n');
        builder.append("stage=spectral_lane_solve\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("authority=").append(AUTH_GAMEPLAY).append('\n');
        builder.append("channel=").append(channel)
                .append(" source=").append(formatPos(graph.sourcePos()))
                .append(" direction=").append(graph.sourceDirection())
                .append('\n');
        builder.append("lane_execution")
                .append(" requested_lanes=").append(requestedLaneCount)
                .append(" executed_lanes=").append(executedLaneCount)
                .append(" parallel=").append(parallel)
                .append(" elapsed_us=").append(String.format(Locale.ROOT, "%.3f", elapsedNanos / 1_000.0D))
                .append(" graph_nodes=").append(graph.nodes().size())
                .append(" graph_edges=").append(graph.edges().size())
                .append(" feedback_sccs=").append(graph.feedbackSccCount())
                .append(" beta1=").append(graph.beta1())
                .append(" profile_mode=").append(profileMode(solution))
                .append(" profile_overflow=").append(solution.profileOverflow())
                .append(" profile_fallback=").append(solution.profileCollapsedFallback())
                .append('\n');
        appendGraphSaturationSummary(builder, channel + "_graph_saturation", graph);
        appendScalarSolution(builder, channel + "_lane_solution", solution, AUTH_GAMEPLAY);
        builder.append('\n');
        write(builder.toString());
    }

    private static String profileMode(ScalarPowerSolution solution) {
        if (solution.profileOverflow()) {
            return "collapsed_due_to_overflow";
        }

        if (solution.profileCollapsedFallback()) {
            return "collapsed_fallback";
        }

        return switch (solution.solverKind()) {
            case PROFILE_COLLAPSED_EXACT -> "collapsed_equivalence";
            case PROFILE_STATE_EXACT -> "state_exact";
            case NONE -> "none";
            default -> "scalar";
        };
    }

    private static void appendTemplateSummary(
            StringBuilder builder,
            String label,
            Level level,
            CompiledPortGraph graph
    ) {
        Map<OpticalComponentTemplate, Integer> counts = new EnumMap<>(OpticalComponentTemplate.class);
        Set<BlockPos> positions = new HashSet<>();
        positions.add(graph.sourcePos());

        for (PortGraphNode node : graph.nodes()) {
            positions.add(node.pos());
        }

        for (BlockPos pos : positions) {
            OpticalComponentTemplate template = OpticalComponentTemplateClassifier.classify(level, pos);
            counts.merge(template, 1, Integer::sum);
        }

        builder.append(label).append("_templates=");

        if (counts.isEmpty()) {
            builder.append("none\n");
            return;
        }

        boolean first = true;

        for (OpticalComponentTemplate template : OpticalComponentTemplate.values()) {
            Integer count = counts.get(template);

            if (count == null || count <= 0) {
                continue;
            }

            if (!first) {
                builder.append(", ");
            }

            builder.append(template.name().toLowerCase(Locale.ROOT)).append(':').append(count);
            first = false;
        }

        builder.append('\n');
    }

    private static void appendSourceVectorSummary(
            StringBuilder builder,
            Level level,
            CompiledPortGraph graph,
            int activeSourceCount
    ) {
        int graphSourceCount = countTemplate(level, graph, OpticalComponentTemplate.SOURCE);

        builder.append("network_source_vector")
                .append(" active_sources=").append(activeSourceCount)
                .append(" graph_source_templates=").append(graphSourceCount)
                .append('\n');
    }

    private static int countTemplate(Level level, CompiledPortGraph graph, OpticalComponentTemplate targetTemplate) {
        Set<BlockPos> positions = new HashSet<>();
        int count = 0;
        positions.add(graph.sourcePos());

        for (PortGraphNode node : graph.nodes()) {
            positions.add(node.pos());
        }

        for (BlockPos pos : positions) {
            if (OpticalComponentTemplateClassifier.classify(level, pos) == targetTemplate) {
                count++;
            }
        }

        return count;
    }

    private static void appendReceiverOutputs(
            StringBuilder builder,
            List<ReceiverOutput> legacyReceiverOutputs,
            List<ReceiverOutput> directReceiverOutputs,
            List<ReceiverOutput> networkReceiverOutputs,
            boolean legacyComparable,
            boolean directReliable,
            boolean networkReliable,
            int directReadoutBindingCount,
            int networkReadoutBindingCount
    ) {
        Map<String, Double> legacyPowers = aggregateReceiverOutputs(legacyReceiverOutputs);
        Map<String, Double> directPowers = aggregateReceiverOutputs(directReceiverOutputs);
        Map<String, Double> networkPowers = aggregateReceiverOutputs(networkReceiverOutputs);

        builder.append("receiver_outputs")
                .append(" legacy_count=").append(legacyReceiverOutputs.size())
                .append(" legacy_comparable=").append(legacyComparable)
                .append(" direct_reliable=").append(directReliable)
                .append(" network_reliable=").append(networkReliable)
                .append(" direct_bindings=").append(directReadoutBindingCount)
                .append(" direct_count=").append(directReceiverOutputs.size())
                .append(" network_bindings=").append(networkReadoutBindingCount)
                .append(" network_count=").append(networkReceiverOutputs.size())
                .append(" legacy_total=").append(formatPower(total(legacyPowers)))
                .append(" direct_total=").append(formatPower(total(directPowers)))
                .append(" network_total=").append(formatPower(total(networkPowers)))
                .append(" max_abs_delta_direct=").append(formatPower(maxAbsDelta(legacyPowers, directPowers)))
                .append(" max_abs_delta_network=").append(formatPower(maxAbsDelta(legacyPowers, networkPowers)))
                .append('\n');

        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(legacyPowers.keySet());
        keys.addAll(directPowers.keySet());
        keys.addAll(networkPowers.keySet());
        builder.append("receiver_output_details:\n");

        if (keys.isEmpty()) {
            builder.append("  none\n");
            return;
        }

        int maxRows = Math.max(1, SpectralizationConfig.opticalCompilerDebugMaxEdges());
        int rowCount = 0;

        for (String key : keys) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(keys.size() - rowCount).append(" more receivers\n");
                return;
            }

            double legacyPower = legacyPowers.getOrDefault(key, 0.0);
            double directPower = directPowers.getOrDefault(key, 0.0);
            double networkPower = networkPowers.getOrDefault(key, 0.0);
            builder.append("  ")
                    .append(key)
                    .append(" legacy=").append(formatPower(legacyPower))
                    .append(" direct=").append(formatPower(directPower))
                    .append(" network=").append(formatPower(networkPower))
                    .append(" direct_delta=").append(formatPower(directPower - legacyPower))
                    .append(" network_delta=").append(formatPower(networkPower - legacyPower))
                    .append('\n');
            rowCount++;
        }
    }

    private static void appendSystemReceiverOutputs(
            StringBuilder builder,
            List<ReceiverOutput> systemLegacyReceiverOutputs,
            List<ReceiverOutput> networkReceiverOutputs,
            boolean systemLegacyComplete,
            boolean systemLegacyComparable,
            boolean networkReliable
    ) {
        Map<String, Double> systemLegacyPowers = aggregateReceiverOutputs(systemLegacyReceiverOutputs);
        Map<String, Double> networkPowers = aggregateReceiverOutputs(networkReceiverOutputs);

        builder.append("system_receiver_outputs")
                .append(" legacy_complete=").append(systemLegacyComplete)
                .append(" legacy_comparable=").append(systemLegacyComparable)
                .append(" network_reliable=").append(networkReliable)
                .append(" legacy_count=").append(systemLegacyReceiverOutputs.size())
                .append(" network_count=").append(networkReceiverOutputs.size())
                .append(" legacy_total=").append(formatPower(total(systemLegacyPowers)))
                .append(" network_total=").append(formatPower(total(networkPowers)))
                .append(" max_abs_delta_network=").append(formatPower(maxAbsDelta(systemLegacyPowers, networkPowers)))
                .append('\n');

        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(systemLegacyPowers.keySet());
        keys.addAll(networkPowers.keySet());
        builder.append("system_receiver_output_details:\n");

        if (keys.isEmpty()) {
            builder.append("  none\n");
            return;
        }

        int maxRows = Math.max(1, SpectralizationConfig.opticalCompilerDebugMaxEdges());
        int rowCount = 0;

        for (String key : keys) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(keys.size() - rowCount).append(" more receivers\n");
                return;
            }

            double legacyPower = systemLegacyPowers.getOrDefault(key, 0.0);
            double networkPower = networkPowers.getOrDefault(key, 0.0);
            builder.append("  ")
                    .append(key)
                    .append(" legacy=").append(formatPower(legacyPower))
                    .append(" network=").append(formatPower(networkPower))
                    .append(" network_delta=").append(formatPower(networkPower - legacyPower))
                    .append('\n');
            rowCount++;
        }
    }

    private static Map<String, Double> aggregateReceiverOutputs(List<ReceiverOutput> receiverOutputs) {
        Map<String, Double> powers = new HashMap<>();

        for (ReceiverOutput receiverOutput : receiverOutputs) {
            powers.merge(formatReceiverOutputKey(receiverOutput), receiverOutput.power(), Double::sum);
        }

        return powers;
    }

    private static void appendReadoutOutputList(
            StringBuilder builder,
            String label,
            List<ReceiverOutput> receiverOutputs
    ) {
        builder.append(label)
                .append("_outputs count=").append(receiverOutputs.size())
                .append(" total_power=").append(formatPower(totalReceiverPower(receiverOutputs)))
                .append(" coherent_power=").append(formatPower(totalReceiverCoherentPower(receiverOutputs)))
                .append(" stray_power=").append(formatPower(totalReceiverStrayPower(receiverOutputs)))
                .append('\n');

        if (receiverOutputs.isEmpty()) {
            builder.append(label).append("_output_details: none\n");
            return;
        }

        builder.append(label).append("_output_details:\n");
        int maxRows = Math.max(1, SpectralizationConfig.opticalCompilerDebugMaxEdges());
        int rowCount = 0;

        for (ReceiverOutput receiverOutput : receiverOutputs.stream()
                .sorted(Comparator.comparing(OpticalCompilerDebugLogger::formatReceiverOutputKey))
                .toList()) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(receiverOutputs.size() - rowCount).append(" more receivers\n");
                return;
            }

            builder.append("  ")
                    .append(formatReceiverOutputKey(receiverOutput))
                    .append(" power=").append(formatPower(receiverOutput.power()))
                    .append(" coherent=").append(formatPower(receiverOutput.coherentPower()))
                    .append(" stray=").append(formatPower(receiverOutput.strayPower()))
                    .append('\n');
            appendReceiverFrequencyPowers(builder, receiverOutput, "    ");
            rowCount++;
        }
    }

    private static void appendReceiverFrequencyPowers(
            StringBuilder builder,
            ReceiverOutput receiverOutput,
            String indent
    ) {
        if (receiverOutput.powerByFrequency().isEmpty()) {
            return;
        }

        List<Map.Entry<FrequencyKey, Double>> frequencies = new ArrayList<>(receiverOutput.powerByFrequency().entrySet());
        frequencies.sort(Map.Entry.comparingByKey(Comparator
                .comparing((FrequencyKey frequency) -> frequency.region().ordinal())
                .thenComparingInt(FrequencyKey::bin)));
        int maxRows = Math.max(1, Math.min(32, SpectralizationConfig.opticalCompilerDebugMaxEdges()));
        int rowCount = 0;

        for (Map.Entry<FrequencyKey, Double> entry : frequencies) {
            if (rowCount >= maxRows) {
                builder.append(indent).append("... ").append(frequencies.size() - rowCount).append(" more frequencies\n");
                return;
            }

            builder.append(indent)
                    .append(formatFrequency(entry.getKey()))
                    .append("=")
                    .append(formatPower(entry.getValue()))
                    .append('\n');
            rowCount++;
        }
    }

    private static double totalReceiverPower(List<ReceiverOutput> receiverOutputs) {
        double totalPower = 0.0;

        for (ReceiverOutput receiverOutput : receiverOutputs) {
            totalPower += receiverOutput.power();
        }

        return totalPower;
    }

    private static double totalReceiverCoherentPower(List<ReceiverOutput> receiverOutputs) {
        double totalPower = 0.0;

        for (ReceiverOutput receiverOutput : receiverOutputs) {
            totalPower += receiverOutput.coherentPower();
        }

        return totalPower;
    }

    private static double totalReceiverStrayPower(List<ReceiverOutput> receiverOutputs) {
        double totalPower = 0.0;

        for (ReceiverOutput receiverOutput : receiverOutputs) {
            totalPower += receiverOutput.strayPower();
        }

        return totalPower;
    }

    private static String formatReceiverOutputKey(ReceiverOutput receiverOutput) {
        String channel = receiverOutput.kind().name().toLowerCase(Locale.ROOT);

        if (receiverOutput.kind().name().equals("PASS_THROUGH_SENSOR")) {
            channel += receiverOutput.positiveZ() ? "/positive_z" : "/negative_z";
        }

        return channel + "@" + formatPos(receiverOutput.pos());
    }

    private static double total(Map<String, Double> powers) {
        double total = 0.0;

        for (double power : powers.values()) {
            total += power;
        }

        return total;
    }

    private static double maxAbsDelta(Map<String, Double> left, Map<String, Double> right) {
        TreeSet<String> keys = new TreeSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());
        double maxDelta = 0.0;

        for (String key : keys) {
            maxDelta = Math.max(maxDelta, Math.abs(right.getOrDefault(key, 0.0) - left.getOrDefault(key, 0.0)));
        }

        return maxDelta;
    }

    private static void appendScalarSolution(
            StringBuilder builder,
            String label,
            ScalarPowerSolution solution,
            String authority
    ) {
        builder.append(label)
                .append(" authority=").append(authority)
                .append(" solver=").append(solution.solverKind())
                .append(" profile_mode=").append(profileMode(solution))
                .append(" profile_overflow=").append(solution.profileOverflow())
                .append(" profile_fallback=").append(solution.profileCollapsedFallback())
                .append(" converged=").append(solution.converged())
                .append(" unstable=").append(solution.unstable())
                .append(" readout_reliable=").append(solution.reliableForReadout())
                .append(" iterations=").append(solution.iterations())
                .append(" residual=").append(formatPower(solution.residual()))
                .append(" max_node_power=").append(formatPower(solution.maxNodePower()))
                .append(" total_node_power=").append(formatPower(solution.totalNodePower()))
                .append(" lanes=").append(solution.powerByLane().size())
                .append('\n');
        appendSolverPlan(builder, label, solution.solverPlan());
        appendProfileDiagnostics(builder, label, solution.profileDiagnostics());
        if (verboseLog()) {
            appendPowerLanes(builder, label, solution);
            appendSolverRegionResults(builder, label, solution);
        }
    }

    private static void appendProfileDiagnostics(
            StringBuilder builder,
            String label,
            ProfileSolverDiagnostics diagnostics
    ) {
        if (!diagnostics.present()) {
            return;
        }

        builder.append(label)
                .append("_profile_diagnostics")
                .append(" states=").append(diagnostics.stateCount())
                .append(" transitions=").append(diagnostics.transitionCount())
                .append(" readout_projections=").append(diagnostics.readoutProjectionCount())
                .append(" saturating_transitions=").append(diagnostics.saturatingTransitionCount())
                .append(" internal_saturating_transitions=").append(diagnostics.internalSaturatingTransitionCount())
                .append(" saturating_sccs=").append(diagnostics.saturatingSccCount())
                .append(" max_saturating_scc_states=").append(diagnostics.maxSaturatingSccStates())
                .append('\n');
    }

    private static void appendPowerLanes(
            StringBuilder builder,
            String label,
            ScalarPowerSolution solution
    ) {
        if (solution.powerByLane().isEmpty()) {
            return;
        }

        builder.append(label).append("_lanes:\n");
        List<Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>>> lanes =
                new ArrayList<>(solution.powerByLane().entrySet());
        lanes.sort(Map.Entry.comparingByKey(SpectralPowerLane.COMPARATOR));
        int maxRows = Math.max(1, Math.min(8, SpectralizationConfig.opticalCompilerDebugMaxEdges()));
        int rowCount = 0;

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : lanes) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(lanes.size() - rowCount).append(" more lanes\n");
                return;
            }

            builder.append("  ")
                    .append(formatLane(entry.getKey()))
                    .append(" total=")
                    .append(formatPower(totalNodePower(entry.getValue())))
                    .append('\n');
            rowCount++;
        }
    }

    private static void appendLanePowerSummary(
            StringBuilder builder,
            String label,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> lanePowers,
            int maxRows
    ) {
        builder.append(label)
                .append(" count=").append(lanePowers.size())
                .append(" total_power=").append(formatPower(totalLanePower(lanePowers)))
                .append('\n');

        if (lanePowers.isEmpty()) {
            return;
        }

        List<Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>>> lanes =
                new ArrayList<>(lanePowers.entrySet());
        lanes.sort(Map.Entry.comparingByKey(SpectralPowerLane.COMPARATOR));
        int rowCount = 0;

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : lanes) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(lanes.size() - rowCount).append(" more lanes\n");
                return;
            }

            builder.append("  ")
                    .append(formatLane(entry.getKey()))
                    .append(" power=")
                    .append(formatPower(totalNodePower(entry.getValue())))
                    .append('\n');
            rowCount++;
        }
    }

    private static double totalLanePower(Map<SpectralPowerLane, Map<PortGraphNode, Double>> lanePowers) {
        double total = 0.0;

        for (Map<PortGraphNode, Double> nodePowers : lanePowers.values()) {
            total += totalNodePower(nodePowers);
        }

        return total;
    }

    private static double totalNodePower(Map<PortGraphNode, Double> powers) {
        double total = 0.0;

        for (double power : powers.values()) {
            total += power;
        }

        return total;
    }

    private static void appendSolverPlan(
            StringBuilder builder,
            String label,
            ScalarSolverPlan solverPlan
    ) {
        builder.append(label)
                .append("_solver_plan primary=").append(solverPlan.primarySolverKind())
                .append(" regions=").append(solverPlan.regions().size())
                .append(" dag_regions=").append(solverPlan.dagRegionCount())
                .append(" feedback_regions=").append(solverPlan.feedbackRegionCount())
                .append(" fallback_regions=").append(solverPlan.fallbackRegionCount())
                .append(" max_beta1=").append(solverPlan.maxBeta1())
                .append('\n');
        if (!verboseLog()) {
            return;
        }

        appendSolverCapabilities(builder, label);
        appendChordFeedbackPlan(builder, label, solverPlan.chordFeedbackPlan());

        if (solverPlan.regions().isEmpty()) {
            return;
        }

        builder.append(label).append("_solver_regions:\n");
        int maxRows = Math.max(1, Math.min(16, SpectralizationConfig.opticalCompilerDebugMaxEdges()));
        int rowCount = 0;

        for (ScalarSolverRegion region : solverPlan.regions()) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(solverPlan.regions().size() - rowCount).append(" more regions\n");
                return;
            }

            builder.append("  #").append(region.id())
                    .append(" scc=").append(region.graphSccId())
                    .append(" nodes=").append(region.nodes().size())
                    .append(" edges=").append(region.edgeIds().size())
                    .append(" beta1=").append(region.beta1())
                    .append(" feedback=").append(region.feedback())
                    .append(" preferred=").append(region.preferredSolverKind())
                    .append(" execution=").append(region.executionSolverKind())
                    .append(" fallback=").append(region.fallback())
                    .append('\n');
            rowCount++;
        }
    }

    private static void appendSolverRegionResults(
            StringBuilder builder,
            String label,
            ScalarPowerSolution solution
    ) {
        if (solution.regionResults().isEmpty()) {
            return;
        }

        builder.append(label).append("_solver_region_results:\n");
        int maxRows = Math.max(1, Math.min(16, SpectralizationConfig.opticalCompilerDebugMaxEdges()));
        int rowCount = 0;

        for (ScalarSolverRegionResult result : solution.regionResults()) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(solution.regionResults().size() - rowCount)
                        .append(" more region results\n");
                return;
            }

            builder.append("  #").append(result.regionId())
                    .append(" scc=").append(result.graphSccId())
                    .append(" solver=").append(result.solverKind())
                    .append(" converged=").append(result.converged())
                    .append(" unstable=").append(result.unstable())
                    .append(" iterations=").append(result.iterations())
                    .append(" residual=").append(formatPower(result.residual()))
                    .append(" max_node_power=").append(formatPower(result.maxNodePower()))
                    .append(" total_node_power=").append(formatPower(result.totalNodePower()))
                    .append('\n');
            rowCount++;
        }
    }

    private static void appendChordFeedbackPlan(
            StringBuilder builder,
            String label,
            ChordFeedbackPlan chordPlan
    ) {
        builder.append(label)
                .append("_chord_plan systems=").append(chordPlan.systemCount())
                .append(" variables=").append(chordPlan.variableCount())
                .append(" max_beta1=").append(chordPlan.maxBeta1())
                .append(" complete=").append(chordPlan.complete())
                .append('\n');

        if (chordPlan.systems().isEmpty()) {
            return;
        }

        builder.append(label).append("_chord_systems:\n");
        int maxRows = Math.max(1, Math.min(16, SpectralizationConfig.opticalCompilerDebugMaxEdges()));
        int rowCount = 0;

        for (ChordFeedbackSystem system : chordPlan.systems()) {
            if (rowCount >= maxRows) {
                builder.append("  ... ").append(chordPlan.systems().size() - rowCount)
                        .append(" more chord systems\n");
                return;
            }

            builder.append("  scc=").append(system.graphSccId())
                    .append(" nodes=").append(system.nodeCount())
                    .append(" edges=").append(system.edgeCount())
                    .append(" beta1=").append(system.beta1())
                    .append(" variables=").append(system.variableCount())
                    .append(" compilable=").append(system.compilable())
                    .append('\n');
            appendChordVariables(builder, system);
            rowCount++;
        }
    }

    private static void appendChordVariables(StringBuilder builder, ChordFeedbackSystem system) {
        if (system.variables().isEmpty()) {
            return;
        }

        int maxRows = Math.max(1, Math.min(8, SpectralizationConfig.opticalCompilerDebugMaxEdges()));
        int rowCount = 0;

        for (ChordFeedbackVariable variable : system.variables()) {
            if (rowCount >= maxRows) {
                builder.append("    ... ").append(system.variables().size() - rowCount)
                        .append(" more chord variables\n");
                return;
            }

            builder.append("    var#").append(variable.id())
                    .append(" chord=").append(variable.chordId())
                    .append(" edge=").append(variable.edgeId())
                    .append(" graph_from=").append(variable.fromGraphNodeId())
                    .append(" graph_to=").append(variable.toGraphNodeId())
                    .append(" local_from=").append(variable.fromLocalNodeId())
                    .append(" local_to=").append(variable.toLocalNodeId())
                    .append(" gain=").append(formatPower(variable.gain()))
                    .append(' ')
                    .append(formatNode(variable.from()))
                    .append(" -> ")
                    .append(formatNode(variable.to()))
                    .append('\n');
            rowCount++;
        }
    }

    private static void appendSolverCapabilities(StringBuilder builder, String label) {
        builder.append(label)
                .append("_solver_capabilities")
                .append(" topological=true")
                .append(" fixed_point=true")
                .append(" feedback_scc_exact=").append(FeedbackSccScalarSolver.implemented())
                .append(" chord_planning=").append(ChordFeedbackScalarSolver.planningImplemented())
                .append(" chord=").append(ChordFeedbackScalarSolver.implemented())
                .append(" mixed_region=").append(MixedRegionScalarSolver.implemented())
                .append(" weighted_bfs=").append(WeightedBfsAttentionScalarSolver.implemented())
                .append(" magnitude_bucket=").append(MagnitudeBucketScalarSolver.implemented())
                .append(" residual_correction=").append(ResidualCorrectionScalarSolver.implemented())
                .append(" loop_macro=").append(LoopMacroScalarSolver.implemented())
                .append(" symmetry=").append(SymmetryReductionScalarSolver.implemented())
                .append(" debug_oracle=").append(DebugOracleScalarSolver.implemented())
                .append('\n');
    }

    private static void appendStrongestNodes(
            StringBuilder builder,
            String label,
            ScalarPowerSolution solution
    ) {
        builder.append(label).append("_strongest_nodes:\n");
        List<Map.Entry<PortGraphNode, Double>> strongestNodes = solution.strongestNodes(8);

        if (strongestNodes.isEmpty()) {
            builder.append("  none\n");
            return;
        }

        for (Map.Entry<PortGraphNode, Double> entry : strongestNodes) {
            builder.append("  ")
                    .append(formatNode(entry.getKey()))
                    .append(" power=")
                    .append(formatPower(entry.getValue()))
                    .append('\n');
        }
    }

    private static void appendGraphSummary(
            StringBuilder builder,
            String label,
            CompiledPortGraph graph,
            int traceSteps,
            int terminations
    ) {
        builder.append(label)
                .append(" nodes=").append(graph.nodes().size())
                .append(" edges=").append(graph.edges().size())
                .append(" sccs=").append(graph.sccs().size())
                .append(" feedback_sccs=").append(graph.feedbackSccCount())
                .append(" beta1=").append(graph.beta1())
                .append(" chords=").append(graph.chords().size());

        if (traceSteps >= 0) {
            builder.append(" trace_steps=").append(traceSteps);
        }

        builder.append(" terminations=").append(terminations)
                .append('\n');
    }

    private static void appendGraphSaturationSummary(
            StringBuilder builder,
            String label,
            CompiledPortGraph graph
    ) {
        int saturatingEdges = 0;
        int saturatingFrequencies = 0;
        int sampleFrequencySaturatingEdges = 0;
        int aggregateGainEdges = 0;
        int aggregateSaturationEdges = 0;
        int frequencyGainEdges = 0;

        for (PortGraphEdge edge : graph.edges()) {
            if (edge.sampleGainByFrequency().isEmpty()) {
                if (edge.sampleGain() > 0.0D) {
                    aggregateGainEdges++;
                }
            } else {
                frequencyGainEdges++;
            }

            if (edge.saturatingGainByFrequency().isEmpty()) {
                continue;
            }

            saturatingEdges++;
            saturatingFrequencies += edge.saturatingGainByFrequency().size();

            if (edge.saturatingGainByFrequency().containsKey(edge.sampleFrequency())) {
                sampleFrequencySaturatingEdges++;
            }

            if (edge.sampleGainByFrequency().isEmpty()) {
                aggregateSaturationEdges++;
            }
        }

        builder.append(label)
                .append(" saturated_edges=").append(saturatingEdges)
                .append(" saturated_frequencies=").append(saturatingFrequencies)
                .append(" sample_frequency_saturated_edges=").append(sampleFrequencySaturatingEdges)
                .append(" aggregate_gain_edges=").append(aggregateGainEdges)
                .append(" aggregate_saturation_edges=").append(aggregateSaturationEdges)
                .append(" frequency_gain_edges=").append(frequencyGainEdges)
                .append('\n');
    }

    private static void appendTerminationSummary(StringBuilder builder, CompiledOpticalTrace trace) {
        Map<OpticalTraceTerminationReason, Integer> counts = new EnumMap<>(OpticalTraceTerminationReason.class);

        for (OpticalTraceTermination termination : trace.terminations()) {
            counts.merge(termination.reason(), 1, Integer::sum);
        }

        builder.append("termination_summary=");

        if (counts.isEmpty()) {
            builder.append("none\n");
            return;
        }

        boolean first = true;

        for (Map.Entry<OpticalTraceTerminationReason, Integer> entry : counts.entrySet()) {
            if (!first) {
                builder.append(", ");
            }

            builder.append(entry.getKey()).append(':').append(entry.getValue());
            first = false;
        }

        builder.append('\n');
    }

    private static boolean isLegacyTraceComparable(CompiledOpticalTrace trace) {
        for (OpticalTraceTermination termination : trace.terminations()) {
            if (termination.reason() == OpticalTraceTerminationReason.MAX_SEGMENTS
                    || termination.reason() == OpticalTraceTerminationReason.MAX_STATES) {
                return false;
            }
        }

        return true;
    }

    private static void appendSccs(StringBuilder builder, CompiledPortGraph graph) {
        appendSccs(builder, "", graph);
    }

    private static void appendSccs(StringBuilder builder, String label, CompiledPortGraph graph) {
        appendSectionHeader(builder, label, "sccs");

        for (PortGraphScc scc : graph.sccs()) {
            if (!scc.feedback() && scc.edgeIds().isEmpty()) {
                continue;
            }

            List<PortGraphNode> nodes = new ArrayList<>(scc.nodes());
            nodes.sort(NODE_COMPARATOR);
            builder.append("  #").append(scc.id())
                    .append(" nodes=").append(scc.nodes().size())
                    .append(" edges=").append(scc.edgeIds().size())
                    .append(" beta1=").append(scc.beta1())
                    .append(" feedback=").append(scc.feedback());

            if (!nodes.isEmpty()) {
                builder.append(" first_node=").append(formatNode(nodes.getFirst()));
            }

            builder.append('\n');
        }
    }

    private static void appendChords(StringBuilder builder, CompiledPortGraph graph) {
        appendChords(builder, "", graph);
    }

    private static void appendChords(StringBuilder builder, String label, CompiledPortGraph graph) {
        appendSectionHeader(builder, label, "chords");

        if (graph.chords().isEmpty()) {
            builder.append("  none\n");
            return;
        }

        for (PortGraphChord chord : graph.chords()) {
            builder.append("  #").append(chord.id())
                    .append(" scc=").append(chord.sccId())
                    .append(" edge=").append(chord.edgeId())
                    .append(' ')
                    .append(formatNode(chord.from()))
                    .append(" -> ")
                    .append(formatNode(chord.to()))
                    .append('\n');
        }
    }

    private static void appendEdges(StringBuilder builder, CompiledPortGraph graph) {
        appendEdges(builder, "", graph);
    }

    private static void appendEdges(StringBuilder builder, String label, CompiledPortGraph graph) {
        int maxEdges = SpectralizationConfig.opticalCompilerDebugMaxEdges();
        builder.append(label.isEmpty() ? "edges" : label + "_edges").append(':');

        if (graph.edges().isEmpty()) {
            builder.append(" none\n");
            return;
        }

        builder.append('\n');
        int edgeCount = Math.min(maxEdges, graph.edges().size());

        for (int index = 0; index < edgeCount; index++) {
            PortGraphEdge edge = graph.edges().get(index);
            builder.append("  #").append(edge.id())
                    .append(' ').append(edge.kind())
                    .append(' ')
                    .append(formatNode(edge.from()))
                    .append(" -> ")
                    .append(formatNode(edge.to()))
                    .append(" distance=").append(edge.distance())
                    .append(" input=").append(formatPower(edge.sampleInputPower()))
                    .append(" output=").append(formatPower(edge.sampleOutputPower()))
                    .append(" gain=").append(formatPower(edge.sampleGain()))
                    .append('\n');
        }

        if (edgeCount < graph.edges().size()) {
            builder.append("  ... ").append(graph.edges().size() - edgeCount).append(" more edges\n");
        }
    }

    private static void appendSectionHeader(StringBuilder builder, String label, String section) {
        builder.append(label.isEmpty() ? section : label + "_" + section).append(":\n");
    }

    private static void appendVerboseSectionsSkipped(StringBuilder builder) {
        builder.append("verbose_sections=skipped_non_verbose\n");
    }

    private static boolean verboseLog() {
        return SpectralizationConfig.opticalCompilerDebugVerbose();
    }

    private static String formatNode(PortGraphNode node) {
        return formatPos(node.pos()) + "/" + node.side().getSerializedName() + "/" + node.waveKind().name().toLowerCase(Locale.ROOT);
    }

    private static String formatLane(SpectralPowerLane lane) {
        return lane.coherence().name().toLowerCase(Locale.ROOT)
                + " "
                + formatFrequency(lane.frequency());
    }

    private static String formatFrequency(FrequencyKey frequency) {
        return frequency.region().name().toLowerCase(Locale.ROOT) + ":" + frequency.bin();
    }

    private static ProjectionMetricSummary summarizeProjectionMetrics(List<SpotRecord> spots) {
        int count = 0;
        double originalAreaSum = 0.0D;
        double worldAreaSum = 0.0D;
        double stretchSum = 0.0D;
        double stretchMin = Double.POSITIVE_INFINITY;
        double stretchMax = 0.0D;
        int tinyTexturePatches = 0;
        int largeStretchPatches = 0;

        for (SpotRecord spot : spots) {
            SpotProjectionContinuity.Metrics metrics = SpotProjectionContinuity.metrics(spot);

            if (metrics.originalArea() <= 0.0D) {
                continue;
            }

            count++;
            originalAreaSum += metrics.originalArea();
            worldAreaSum += metrics.worldArea();
            stretchSum += metrics.stretchRatio();
            stretchMin = Math.min(stretchMin, metrics.stretchRatio());
            stretchMax = Math.max(stretchMax, metrics.stretchRatio());

            if (metrics.originalArea() <= SPOT_TINY_TEXTURE_AREA_THRESHOLD) {
                tinyTexturePatches++;
            }

            if (metrics.stretchRatio() >= SPOT_LARGE_STRETCH_RATIO_THRESHOLD) {
                largeStretchPatches++;
            }
        }

        if (count == 0) {
            return new ProjectionMetricSummary(0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0, 0);
        }

        return new ProjectionMetricSummary(
                count,
                originalAreaSum,
                worldAreaSum,
                stretchMin,
                stretchMax,
                stretchSum / count,
                tinyTexturePatches,
                largeStretchPatches
        );
    }

    private static void appendProjectionFaceMetrics(StringBuilder builder, List<SpotRecord> spots) {
        builder.append("spot_projection_face_metrics:\n");
        int count = 0;

        for (SpotRecord spot : spots) {
            SpotProjectionContinuity.Metrics metrics = SpotProjectionContinuity.metrics(spot);

            if (metrics.originalArea() <= 0.0D) {
                continue;
            }

            builder.append("  pos=").append(formatPos(spot.pos()))
                    .append(" face=").append(spot.face().getSerializedName())
                    .append(" projection=").append(spot.projectionMode().name().toLowerCase(Locale.ROOT))
                    .append(" original_area=").append(formatDouble(metrics.originalArea()))
                    .append(" world_area=").append(formatDouble(metrics.worldArea()))
                    .append(" stretch_ratio=").append(formatDouble(metrics.stretchRatio()))
                    .append('\n');
            count++;
        }

        if (count == 0) {
            builder.append("  none\n");
        }
    }

    private static void appendDebugFaceMarkers(StringBuilder builder, List<SpotRecord> spots) {
        builder.append("spot_projection_debug_markers:\n");
        int count = 0;

        for (SpotRecord spot : spots) {
            if (spot.projectionMode() != SpotRecord.ProjectionMode.DEBUG_FACE_CENTER) {
                continue;
            }

            builder.append("  marker=").append(formatDebugMarker(spot.debugMarker()))
                    .append(" pos=").append(formatPos(spot.pos()))
                    .append(" face=").append(spot.face().getSerializedName())
                    .append('\n');
            count++;
        }

        if (count == 0) {
            builder.append("  none\n");
        }
    }

    private static void appendProjectionAllocationMetrics(
            StringBuilder builder,
            List<SpotProjectionAllocation> allocations
    ) {
        if (allocations.isEmpty()) {
            builder.append("spot_projection_allocation_summary allocations=0\n");
            builder.append("spot_projection_allocation_metrics: none\n");
            return;
        }

        double candidateAreaSum = 0.0D;
        double assignedAreaSum = 0.0D;
        double emittedAreaSum = 0.0D;
        double assignedPowerSum = 0.0D;
        double emittedPowerSum = 0.0D;
        int emittedQuads = 0;
        int assignedButNotEmitted = 0;
        int internalSideAllocations = 0;
        int otherSideAllocations = 0;
        int probeAllocations = 0;
        int otherAllocations = 0;
        Map<String, Integer> resultCounts = new HashMap<>();

        for (SpotProjectionAllocation allocation : allocations) {
            candidateAreaSum += allocation.candidateArea();
            assignedAreaSum += allocation.assignedArea();
            emittedAreaSum += allocation.emittedArea();
            assignedPowerSum += allocation.assignedPowerFraction();
            emittedPowerSum += allocation.emittedPowerFraction();
            emittedQuads += allocation.emittedQuads();
            resultCounts.merge(allocation.result(), 1, Integer::sum);

            if (allocation.assignedArea() > 0.0D && allocation.emittedQuads() == 0) {
                assignedButNotEmitted++;
            }

            if (allocation.internalProjectionSide()) {
                internalSideAllocations++;
            } else if (isSideAllocation(allocation)) {
                otherSideAllocations++;
            } else if (allocation.kind().endsWith("-probe")) {
                probeAllocations++;
            } else {
                otherAllocations++;
            }
        }

        builder.append("spot_projection_allocation_summary")
                .append(" allocations=").append(allocations.size())
                .append(" candidate_area_sum=").append(formatDouble(candidateAreaSum))
                .append(" assigned_area_sum=").append(formatDouble(assignedAreaSum))
                .append(" emitted_area_sum=").append(formatDouble(emittedAreaSum))
                .append(" missing_assigned_area=").append(formatDouble(Math.max(0.0D, assignedAreaSum - emittedAreaSum)))
                .append(" assigned_power_fraction_sum=").append(formatDouble(assignedPowerSum))
                .append(" emitted_power_fraction_sum=").append(formatDouble(emittedPowerSum))
                .append(" emitted_quads=").append(emittedQuads)
                .append(" assigned_but_not_emitted=").append(assignedButNotEmitted)
                .append(" internal_side_allocations=").append(internalSideAllocations)
                .append(" other_side_allocations=").append(otherSideAllocations)
                .append(" probe_allocations=").append(probeAllocations)
                .append(" other_allocations=").append(otherAllocations)
                .append(" results=").append(formatStringCounts(resultCounts))
                .append('\n');

        builder.append("spot_projection_allocation_metrics:\n");
        List<SpotProjectionAllocation> sortedAllocations = new ArrayList<>(allocations);
        sortedAllocations.sort(Comparator
                .comparingInt((SpotProjectionAllocation allocation) -> allocationPriority(allocation))
                .thenComparingInt(allocation -> allocation.pos().getX())
                .thenComparingInt(allocation -> allocation.pos().getY())
                .thenComparingInt(allocation -> allocation.pos().getZ())
                .thenComparing(allocation -> allocation.face().getSerializedName())
                .thenComparing(SpotProjectionAllocation::kind));

        int configuredRows = Math.max(96, Math.min(512, SpectralizationConfig.opticalCompilerDebugMaxEdges() * 4));
        int maxRows = Math.max(configuredRows, internalSideAllocations);
        builder.append("spot_projection_allocation_row_budget configured=")
                .append(configuredRows)
                .append(" effective=").append(maxRows)
                .append(" internal_side_reserved=").append(internalSideAllocations)
                .append('\n');
        int count = 0;

        for (SpotProjectionAllocation allocation : sortedAllocations) {
            if (count >= maxRows) {
                builder.append("  ... ").append(sortedAllocations.size() - count).append(" more allocations\n");
                break;
            }

            builder.append("  pos=").append(formatPos(allocation.pos()))
                    .append(" face=").append(allocation.face().getSerializedName())
                    .append(" kind=").append(allocation.kind())
                    .append(" candidate_area=").append(formatDouble(allocation.candidateArea()))
                    .append(" assigned_area=").append(formatDouble(allocation.assignedArea()))
                    .append(" emitted_area=").append(formatDouble(allocation.emittedArea()))
                    .append(" assigned_power_fraction=").append(formatDouble(allocation.assignedPowerFraction()))
                    .append(" emitted_power_fraction=").append(formatDouble(allocation.emittedPowerFraction()))
                    .append(" emitted_quads=").append(allocation.emittedQuads())
                    .append(" internal_side=").append(allocation.internalProjectionSide())
                    .append(" result=").append(allocation.result());

            if (!allocation.detail().isEmpty()) {
                builder.append(" detail=").append(allocation.detail());
            }

            builder.append('\n');
            count++;
        }
    }

    private static int allocationPriority(SpotProjectionAllocation allocation) {
        if (allocation.internalProjectionSide()) {
            return -3;
        }

        if (isSideAllocation(allocation)) {
            return -2;
        }

        if (allocation.assignedArea() > 0.0D && allocation.emittedQuads() == 0) {
            return -1;
        }

        if (allocation.kind().endsWith("-probe")) {
            return 3;
        }

        if ("occluded".equals(allocation.result())) {
            return 2;
        }

        if ("emitted".equals(allocation.result())) {
            return 3;
        }

        return 1;
    }

    private static boolean isSideAllocation(SpotProjectionAllocation allocation) {
        return "u-side".equals(allocation.kind()) || "v-side".equals(allocation.kind());
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatPower(double power) {
        if (!Double.isFinite(power)) {
            return "inf";
        }

        return String.format(Locale.ROOT, "%.6f", power);
    }

    private static String formatDouble(double value) {
        return formatPower(value);
    }

    private static String formatRgb(int red, int green, int blue) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    private static String formatRgb(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    private static String formatDebugMarker(int marker) {
        return marker < 0 ? "---" : String.format(Locale.ROOT, "%03X", marker & 0xFFF);
    }

    private static String formatStringCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (!builder.isEmpty()) {
                        builder.append(',');
                    }

                    builder.append(entry.getKey()).append(':').append(entry.getValue());
                });

        return builder.toString();
    }

    private static String formatDirectionCounts(EnumMap<Direction, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }

        StringBuilder builder = new StringBuilder();
        for (Direction direction : Direction.values()) {
            int count = counts.getOrDefault(direction, 0);
            if (count <= 0) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(',');
            }

            builder.append(direction.getSerializedName()).append(':').append(count);
        }

        return builder.toString();
    }

    private static String formatQuadVertex(int x, int y, int z, int u, int v) {
        return String.format(Locale.ROOT, "%d,%d,%d@%d,%d", x, y, z, u, v);
    }

    private static void write(String content) {
        SpectralDiagnostics.appendLog(LOG_RELATIVE_PATH, content, "optical compiler debug");
    }

    private record ProjectionMetricSummary(
            int count,
            double originalAreaSum,
            double worldAreaSum,
            double stretchMin,
            double stretchMax,
            double stretchAverage,
            int tinyTexturePatches,
            int largeStretchPatches
    ) {
    }

    private OpticalCompilerDebugLogger() {
    }
}
