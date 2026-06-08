package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.OpticalTraceTermination;
import io.github.yoglappland.spectralization.optics.OpticalTraceTerminationReason;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import net.neoforged.fml.loading.FMLPaths;

public final class OpticalCompilerDebugLogger {
    private static final DateTimeFormatter SESSION_LOG_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss_'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final String SESSION_LOG_FILE_NAME =
            "optical_compiler_" + SESSION_LOG_TIMESTAMP.format(Instant.now()) + ".log";
    private static final String LOG_RELATIVE_PATH = "logs/spectralization/" + SESSION_LOG_FILE_NAME;
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
        appendSccs(builder, graph);
        appendChords(builder, graph);
        appendEdges(builder, graph);
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
        appendScalarSolution(builder, "direct_scalar", scalarPowerSolution);
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
            appendScalarSolution(builder, "network_scalar", networkSolution);
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
        builder.append("source=").append(formatPos(sourcePos))
                .append(" direction=").append(sourceOutput.outgoingDirection())
                .append(" power=").append(formatPower(sourceOutput.beam().totalPower()))
                .append('\n');
        builder.append("legacy_observed_trace=skipped_feedback_or_disabled\n");
        appendGraphSummary(builder, "direct", directGraph, -1, directGraph.terminationCount());
        appendTemplateSummary(builder, "direct", level, directGraph);
        appendScalarSolution(builder, "direct_scalar", scalarPowerSolution);
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
            appendScalarSolution(builder, "network_scalar", networkSolution);
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
        appendSccs(builder, "direct", directGraph);
        appendChords(builder, "direct", directGraph);
        appendStrongestNodes(builder, "direct_scalar", scalarPowerSolution);

        if (networkGraph != null && networkSolution != null) {
            appendSccs(builder, "network", networkGraph);
            appendChords(builder, "network", networkGraph);
            appendStrongestNodes(builder, "network_scalar", networkSolution);
        }

        appendEdges(builder, "direct", directGraph);
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
            String solverKind
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
                .append(" solver=").append(solverKind)
                .append('\n');
        builder.append('\n');
        write(builder.toString());
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
            ScalarPowerSolution solution
    ) {
        builder.append(label)
                .append(" solver=").append(solution.solverKind())
                .append(" converged=").append(solution.converged())
                .append(" unstable=").append(solution.unstable())
                .append(" readout_reliable=").append(solution.reliableForReadout())
                .append(" iterations=").append(solution.iterations())
                .append(" residual=").append(formatPower(solution.residual()))
                .append(" max_node_power=").append(formatPower(solution.maxNodePower()))
                .append(" total_node_power=").append(formatPower(solution.totalNodePower()))
                .append('\n');
        appendSolverPlan(builder, label, solution.solverPlan());
        appendSolverRegionResults(builder, label, solution);
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

    private static String formatNode(PortGraphNode node) {
        return formatPos(node.pos()) + "/" + node.side().getSerializedName() + "/" + node.waveKind().name().toLowerCase(Locale.ROOT);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String formatPower(double power) {
        return String.format(Locale.ROOT, "%.6f", power);
    }

    private static void write(String content) {
        Path logPath = FMLPaths.GAMEDIR.get().resolve(LOG_RELATIVE_PATH);

        try {
            Files.createDirectories(logPath.getParent());
            Files.writeString(
                    logPath,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            Spectralization.LOGGER.warn("Failed to write optical compiler debug log", exception);
        }
    }

    private OpticalCompilerDebugLogger() {
    }
}
