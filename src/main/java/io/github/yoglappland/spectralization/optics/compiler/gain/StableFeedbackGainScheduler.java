package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphChord;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class StableFeedbackGainScheduler implements GainScheduler {
    private static final int MAX_SCHEDULED_NODES = 2048;
    private static final int MAX_SCHEDULED_EDGES = 8192;
    private static final int MAX_GAIN_SOURCES = 256;
    private static final int MAX_ANALYZED_CHORDS_PER_SCC = 128;
    private static final int POWER_ITERATIONS = 32;
    private static final int SAFETY_BISECTION_STEPS = 14;
    private static final double HARD_RHO = 0.985;
    private static final double SOFTCAP_SHARPNESS = 4.0;
    private static final double MIN_PARTICIPATION = 1.0E-6;
    private static final double MIN_GAIN_DELTA = 1.0E-6;

    @Override
    public GainSchedule schedule(Level level, CompiledPortGraph graph) {
        Objects.requireNonNull(graph, "graph");

        if (level == null
                || graph.feedbackSccCount() <= 0
                || graph.nodes().size() > MAX_SCHEDULED_NODES
                || graph.edges().size() > MAX_SCHEDULED_EDGES) {
            return GainSchedule.none(graph);
        }

        Map<Integer, PortGraphEdge> edgesById = edgesById(graph);
        Map<Integer, PortGraphScc> feedbackSccsById = feedbackSccsById(graph);
        Map<Integer, GainSource> gainSourcesByEdgeId = collectGainSources(level, graph, edgesById, feedbackSccsById);

        if (gainSourcesByEdgeId.isEmpty() || gainSourcesByEdgeId.size() > MAX_GAIN_SOURCES) {
            return GainSchedule.none(graph);
        }

        Map<Integer, Double> graphWeightsByEdgeId =
                graphWeightsByEdgeId(graph, edgesById, feedbackSccsById, gainSourcesByEdgeId);
        List<GainSource> gainSources = weightedGainSources(gainSourcesByEdgeId, graphWeightsByEdgeId);
        double rhoBefore = estimateSpectralRadius(graph, edge -> gainSourcesByEdgeId
                .getOrDefault(edge.id(), GainSource.passive())
                .baseGain());
        ScheduledGains scheduledGains = scheduledEffectiveGainsByEdgeId(
                graph,
                edgesById,
                feedbackSccsById,
                gainSources
        );
        Map<Integer, Double> effectiveGainsByEdgeId = scheduledGains.effectiveGainsByEdgeId();
        double rhoAfter = estimateSpectralRadiusWithGains(graph, effectiveGainsByEdgeId);

        if (rhoAfter >= HARD_RHO && hasActiveScheduledGain(effectiveGainsByEdgeId)) {
            effectiveGainsByEdgeId = shrinkExtraLogGainsToStable(graph, effectiveGainsByEdgeId);
            rhoAfter = estimateSpectralRadiusWithGains(graph, effectiveGainsByEdgeId);
        }

        CompiledPortGraph scheduledGraph = applyEffectiveGains(graph, effectiveGainsByEdgeId);

        return new GainSchedule(
                scheduledGraph,
                hasActiveScheduledGain(effectiveGainsByEdgeId),
                rhoAfter < HARD_RHO,
                scheduledGains.passiveRho(),
                HARD_RHO,
                rhoBefore,
                rhoAfter,
                gainSources.size(),
                scheduledGains.totalGainHeadroom(),
                scheduledGains.maxModeWeight(),
                scheduledGains.maxSourceCap(),
                maxBaseGain(gainSources),
                maxEffectiveGain(effectiveGainsByEdgeId)
        );
    }

    private static Map<Integer, PortGraphEdge> edgesById(CompiledPortGraph graph) {
        Map<Integer, PortGraphEdge> edgesById = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            edgesById.put(edge.id(), edge);
        }

        return edgesById;
    }

    private static Map<Integer, PortGraphScc> feedbackSccsById(CompiledPortGraph graph) {
        Map<Integer, PortGraphScc> sccsById = new HashMap<>();

        for (PortGraphScc scc : graph.sccs()) {
            if (scc.feedback()) {
                sccsById.put(scc.id(), scc);
            }
        }

        return sccsById;
    }

    private static Map<Integer, GainSource> collectGainSources(
            Level level,
            CompiledPortGraph graph,
            Map<Integer, PortGraphEdge> edgesById,
            Map<Integer, PortGraphScc> feedbackSccsById
    ) {
        Map<PortGraphNode, Integer> sccIdByNode = new HashMap<>();

        for (PortGraphScc scc : feedbackSccsById.values()) {
            for (PortGraphNode node : scc.nodes()) {
                sccIdByNode.put(node, scc.id());
            }
        }

        Map<Integer, GainSource> gainSourcesByEdgeId = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            Integer sccId = sccIdByNode.get(edge.from());

            if (sccId == null || !sccId.equals(sccIdByNode.get(edge.to())) || !isLocalGainCandidate(edge)) {
                continue;
            }

            BlockPos pos = edge.from().pos();

            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            double baseGain = OpticalMaterialProfiles.scheduledCoherentBaseGainFor(level, pos, state);
            double materialWeight = OpticalMaterialProfiles.gainMaterialWeightFor(state);

            if (baseGain <= 1.0 + MIN_GAIN_DELTA || materialWeight <= 0.0) {
                continue;
            }

            gainSourcesByEdgeId.put(edge.id(), new GainSource(
                    edge.id(),
                    sccId,
                    baseGain,
                    materialWeight,
                    MIN_PARTICIPATION,
                    MIN_PARTICIPATION
            ));
        }

        return gainSourcesByEdgeId;
    }

    private static boolean isLocalGainCandidate(PortGraphEdge edge) {
        return edge.kind() == PortGraphEdgeKind.LOCAL_SCATTERING
                && edge.sampleGain() > 0.0
                && edge.from().pos().equals(edge.to().pos())
                && edge.from().side().getOpposite() == edge.to().side();
    }

    private static Map<Integer, Double> graphWeightsByEdgeId(
            CompiledPortGraph graph,
            Map<Integer, PortGraphEdge> edgesById,
            Map<Integer, PortGraphScc> feedbackSccsById,
            Map<Integer, GainSource> gainSourcesByEdgeId
    ) {
        Map<Integer, Double> graphWeightsByEdgeId = new HashMap<>();

        for (Integer edgeId : gainSourcesByEdgeId.keySet()) {
            graphWeightsByEdgeId.put(edgeId, MIN_PARTICIPATION);
        }

        Map<Integer, List<PortGraphChord>> chordsBySccId = chordsBySccId(graph);

        for (Map.Entry<Integer, PortGraphScc> entry : feedbackSccsById.entrySet()) {
            int sccId = entry.getKey();
            PortGraphScc scc = entry.getValue();
            List<PortGraphChord> chords = chordsBySccId.getOrDefault(sccId, List.of());

            if (chords.isEmpty() || chords.size() > MAX_ANALYZED_CHORDS_PER_SCC) {
                addFallbackGraphWeights(scc, gainSourcesByEdgeId, graphWeightsByEdgeId);
                continue;
            }

            List<PortGraphEdge> internalEdges = internalEdges(scc, edgesById);
            Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges = outgoingEdges(internalEdges);

            for (PortGraphChord chord : chords) {
                PortGraphEdge chordEdge = edgesById.get(chord.edgeId());

                if (chordEdge == null) {
                    continue;
                }

                List<PortGraphEdge> cycleEdges = cycleEdges(chord, chordEdge, outgoingEdges);

                if (cycleEdges.isEmpty()) {
                    continue;
                }

                double loopStrength = loopStrength(cycleEdges);

                if (loopStrength <= 0.0) {
                    continue;
                }

                for (PortGraphEdge edge : cycleEdges) {
                    if (gainSourcesByEdgeId.containsKey(edge.id())) {
                        graphWeightsByEdgeId.merge(edge.id(), loopStrength, Double::sum);
                    }
                }
            }
        }

        return graphWeightsByEdgeId;
    }

    private static Map<Integer, List<PortGraphChord>> chordsBySccId(CompiledPortGraph graph) {
        Map<Integer, List<PortGraphChord>> chordsBySccId = new HashMap<>();

        for (PortGraphChord chord : graph.chords()) {
            chordsBySccId.computeIfAbsent(chord.sccId(), ignored -> new ArrayList<>()).add(chord);
        }

        return chordsBySccId;
    }

    private static List<PortGraphEdge> internalEdges(PortGraphScc scc, Map<Integer, PortGraphEdge> edgesById) {
        List<PortGraphEdge> edges = new ArrayList<>();

        for (int edgeId : scc.edgeIds()) {
            PortGraphEdge edge = edgesById.get(edgeId);

            if (edge != null) {
                edges.add(edge);
            }
        }

        return edges;
    }

    private static Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges(List<PortGraphEdge> edges) {
        Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges = new HashMap<>();

        for (PortGraphEdge edge : edges) {
            outgoingEdges.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }

        return outgoingEdges;
    }

    private static List<PortGraphEdge> cycleEdges(
            PortGraphChord chord,
            PortGraphEdge chordEdge,
            Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges
    ) {
        Map<PortGraphNode, PortGraphEdge> previousEdges = new HashMap<>();
        ArrayDeque<PortGraphNode> pending = new ArrayDeque<>();
        Set<PortGraphNode> visited = new HashSet<>();

        pending.addLast(chord.to());
        visited.add(chord.to());

        while (!pending.isEmpty()) {
            PortGraphNode node = pending.removeFirst();

            if (node.equals(chord.from())) {
                break;
            }

            for (PortGraphEdge edge : outgoingEdges.getOrDefault(node, List.of())) {
                if (edge.id() == chord.edgeId() || !visited.add(edge.to())) {
                    continue;
                }

                previousEdges.put(edge.to(), edge);
                pending.addLast(edge.to());
            }
        }

        if (!visited.contains(chord.from())) {
            return List.of();
        }

        List<PortGraphEdge> cycleEdges = new ArrayList<>();
        PortGraphNode cursor = chord.from();

        while (!cursor.equals(chord.to())) {
            PortGraphEdge edge = previousEdges.get(cursor);

            if (edge == null) {
                return List.of();
            }

            cycleEdges.add(edge);
            cursor = edge.from();
        }

        cycleEdges.add(chordEdge);
        return cycleEdges;
    }

    private static double loopStrength(List<PortGraphEdge> cycleEdges) {
        double strength = 1.0;

        for (PortGraphEdge edge : cycleEdges) {
            strength *= edge.sampleGain();

            if (!Double.isFinite(strength) || strength <= 0.0) {
                return 0.0;
            }
        }

        return strength;
    }

    private static void addFallbackGraphWeights(
            PortGraphScc scc,
            Map<Integer, GainSource> gainSourcesByEdgeId,
            Map<Integer, Double> graphWeightsByEdgeId
    ) {
        for (int edgeId : scc.edgeIds()) {
            if (gainSourcesByEdgeId.containsKey(edgeId)) {
                graphWeightsByEdgeId.merge(edgeId, 1.0, Double::sum);
            }
        }
    }

    private static List<GainSource> weightedGainSources(
            Map<Integer, GainSource> gainSourcesByEdgeId,
            Map<Integer, Double> graphWeightsByEdgeId
    ) {
        double maxTotalWeight = MIN_PARTICIPATION;
        List<GainSource> weightedSources = new ArrayList<>(gainSourcesByEdgeId.size());

        for (GainSource source : gainSourcesByEdgeId.values()) {
            double graphWeight = Math.max(MIN_PARTICIPATION, graphWeightsByEdgeId.getOrDefault(
                    source.edgeId(),
                    MIN_PARTICIPATION
            ));
            double totalWeight = graphWeight * source.materialWeight();
            maxTotalWeight = Math.max(maxTotalWeight, totalWeight);
            weightedSources.add(source.withWeights(graphWeight, totalWeight));
        }

        List<GainSource> normalizedSources = new ArrayList<>(weightedSources.size());

        for (GainSource source : weightedSources) {
            normalizedSources.add(source.withModeWeight(Math.min(1.0, source.totalWeight() / maxTotalWeight)));
        }

        return normalizedSources;
    }

    private static ScheduledGains scheduledEffectiveGainsByEdgeId(
            CompiledPortGraph graph,
            Map<Integer, PortGraphEdge> edgesById,
            Map<Integer, PortGraphScc> feedbackSccsById,
            List<GainSource> gainSources
    ) {
        Map<Integer, Double> effectiveGainsByEdgeId = new HashMap<>();
        Map<Integer, List<GainSource>> sourcesBySccId = sourcesBySccId(gainSources);
        double maxPassiveRho = 0.0;
        double totalGainHeadroom = 0.0;
        double maxModeWeight = 0.0;
        double maxSourceCap = 1.0;

        for (Map.Entry<Integer, List<GainSource>> entry : sourcesBySccId.entrySet()) {
            PortGraphScc scc = feedbackSccsById.get(entry.getKey());

            if (scc == null) {
                continue;
            }

            List<GainSource> sccSources = entry.getValue();
            double passiveRho = estimateSccSpectralRadius(scc, edgesById, edge -> 1.0);
            maxPassiveRho = Math.max(maxPassiveRho, passiveRho);

            for (GainSource source : sccSources) {
                double sourceCap = sourceCap(source);
                double effectiveGain = softCappedGain(source.baseGain(), sourceCap);
                effectiveGainsByEdgeId.put(source.edgeId(), effectiveGain);
                totalGainHeadroom += Math.max(0.0, sourceCap - 1.0);
                maxModeWeight = Math.max(maxModeWeight, source.modeWeight());
                maxSourceCap = Math.max(maxSourceCap, sourceCap);
            }
        }

        return new ScheduledGains(
                effectiveGainsByEdgeId,
                maxPassiveRho,
                totalGainHeadroom,
                maxModeWeight,
                maxSourceCap
        );
    }

    private static Map<Integer, List<GainSource>> sourcesBySccId(List<GainSource> gainSources) {
        Map<Integer, List<GainSource>> sourcesBySccId = new HashMap<>();

        for (GainSource source : gainSources) {
            sourcesBySccId.computeIfAbsent(source.sccId(), ignored -> new ArrayList<>()).add(source);
        }

        return sourcesBySccId;
    }

    private static double sourceCap(GainSource source) {
        double rawExtraGain = Math.max(0.0, source.baseGain() - 1.0);
        double coupledExtraGain = rawExtraGain * Math.max(0.0, Math.min(1.0, source.modeWeight()));
        return 1.0 + coupledExtraGain;
    }

    private static double softCappedGain(double baseGain, double sourceCap) {
        if (baseGain <= 1.0 + MIN_GAIN_DELTA || sourceCap <= 1.0 + MIN_GAIN_DELTA) {
            return 1.0;
        }

        double rawExtraGain = Math.max(0.0, baseGain - 1.0);
        double capExtraGain = Math.max(0.0, sourceCap - 1.0);

        if (rawExtraGain <= 0.0 || capExtraGain <= 0.0) {
            return 1.0;
        }

        double ratio = rawExtraGain / capExtraGain;
        double denominator = Math.pow(1.0 + Math.pow(ratio, SOFTCAP_SHARPNESS), 1.0 / SOFTCAP_SHARPNESS);
        double effectiveExtraGain = rawExtraGain / denominator;

        if (!Double.isFinite(effectiveExtraGain) || effectiveExtraGain <= 0.0) {
            return 1.0;
        }

        return 1.0 + effectiveExtraGain;
    }

    private static Map<Integer, Double> shrinkExtraLogGainsToStable(
            CompiledPortGraph graph,
            Map<Integer, Double> effectiveGainsByEdgeId
    ) {
        double low = 0.0;
        double high = 1.0;

        for (int step = 0; step < SAFETY_BISECTION_STEPS; step++) {
            double middle = (low + high) * 0.5;
            Map<Integer, Double> gains = scaledExtraLogGains(effectiveGainsByEdgeId, middle);
            double rho = estimateSpectralRadius(graph, edge -> gains.getOrDefault(edge.id(), 1.0));

            if (rho < HARD_RHO) {
                low = middle;
            } else {
                high = middle;
            }
        }

        return scaledExtraLogGains(effectiveGainsByEdgeId, low);
    }

    private static Map<Integer, Double> scaledExtraLogGains(
            Map<Integer, Double> effectiveGainsByEdgeId,
            double scale
    ) {
        Map<Integer, Double> scaledGains = new HashMap<>();

        for (Map.Entry<Integer, Double> entry : effectiveGainsByEdgeId.entrySet()) {
            double gain = entry.getValue();

            if (gain <= 1.0 + MIN_GAIN_DELTA) {
                scaledGains.put(entry.getKey(), 1.0);
                continue;
            }

            scaledGains.put(entry.getKey(), Math.exp(Math.log(gain) * scale));
        }

        return scaledGains;
    }

    private static double estimateSpectralRadiusWithGains(
            CompiledPortGraph graph,
            Map<Integer, Double> effectiveGainsByEdgeId
    ) {
        return estimateSpectralRadius(graph, edge -> effectiveGainsByEdgeId.getOrDefault(edge.id(), 1.0));
    }

    private static double estimateSpectralRadius(CompiledPortGraph graph, EdgeGain edgeGain) {
        double rho = 0.0;
        Map<Integer, PortGraphEdge> edgesById = edgesById(graph);

        for (PortGraphScc scc : graph.sccs()) {
            if (!scc.feedback()) {
                continue;
            }

            rho = Math.max(rho, estimateSccSpectralRadius(scc, edgesById, edgeGain));
        }

        return rho;
    }

    private static double estimateSccSpectralRadius(
            PortGraphScc scc,
            Map<Integer, PortGraphEdge> edgesById,
            EdgeGain edgeGain
    ) {
        List<PortGraphNode> nodes = new ArrayList<>(scc.nodes());
        Map<PortGraphNode, Integer> nodeIds = new HashMap<>();

        for (int index = 0; index < nodes.size(); index++) {
            nodeIds.put(nodes.get(index), index);
        }

        List<PortGraphEdge> internalEdges = internalEdges(scc, edgesById);

        if (nodes.isEmpty() || internalEdges.isEmpty()) {
            return 0.0;
        }

        double[] current = new double[nodes.size()];
        double seed = 1.0 / nodes.size();

        for (int index = 0; index < current.length; index++) {
            current[index] = seed;
        }

        double norm = 0.0;

        for (int iteration = 0; iteration < POWER_ITERATIONS; iteration++) {
            double[] next = new double[nodes.size()];

            for (PortGraphEdge edge : internalEdges) {
                Integer fromId = nodeIds.get(edge.from());
                Integer toId = nodeIds.get(edge.to());

                if (fromId == null || toId == null) {
                    continue;
                }

                next[toId] += current[fromId] * edge.sampleGain() * edgeGain.gainFor(edge);
            }

            norm = l1Norm(next);

            if (!Double.isFinite(norm) || norm <= 0.0) {
                return 0.0;
            }

            for (int index = 0; index < next.length; index++) {
                next[index] /= norm;
            }

            current = next;
        }

        return norm;
    }

    private static double l1Norm(double[] values) {
        double norm = 0.0;

        for (double value : values) {
            norm += Math.abs(value);
        }

        return norm;
    }

    private static CompiledPortGraph applyEffectiveGains(
            CompiledPortGraph graph,
            Map<Integer, Double> effectiveGainsByEdgeId
    ) {
        if (!hasActiveScheduledGain(effectiveGainsByEdgeId)) {
            return graph;
        }

        List<PortGraphEdge> edges = new ArrayList<>(graph.edges().size());

        for (PortGraphEdge edge : graph.edges()) {
            double gain = effectiveGainsByEdgeId.getOrDefault(edge.id(), 1.0);

            if (gain <= 1.0 + MIN_GAIN_DELTA) {
                edges.add(edge);
                continue;
            }

            edges.add(new PortGraphEdge(
                    edge.id(),
                    edge.kind(),
                    edge.from(),
                    edge.to(),
                    edge.distance(),
                    edge.sampleInputPower(),
                    edge.sampleOutputPower() * gain
            ));
        }

        return new CompiledPortGraph(
                graph.sourcePos(),
                graph.sourceDirection(),
                graph.sourceNode(),
                graph.nodes(),
                edges,
                graph.sccs(),
                graph.chords(),
                graph.terminationCount()
        );
    }

    private static boolean hasActiveScheduledGain(Map<Integer, Double> effectiveGainsByEdgeId) {
        for (double gain : effectiveGainsByEdgeId.values()) {
            if (gain > 1.0 + MIN_GAIN_DELTA) {
                return true;
            }
        }

        return false;
    }

    private static double maxBaseGain(List<GainSource> gainSources) {
        double maxGain = 1.0;

        for (GainSource source : gainSources) {
            maxGain = Math.max(maxGain, source.baseGain());
        }

        return maxGain;
    }

    private static double maxEffectiveGain(Map<Integer, Double> effectiveGainsByEdgeId) {
        double maxGain = 1.0;

        for (double gain : effectiveGainsByEdgeId.values()) {
            maxGain = Math.max(maxGain, gain);
        }

        return maxGain;
    }

    private record ScheduledGains(
            Map<Integer, Double> effectiveGainsByEdgeId,
            double passiveRho,
            double totalGainHeadroom,
            double maxModeWeight,
            double maxSourceCap
    ) {
    }

    private record GainSource(
            int edgeId,
            int sccId,
            double baseGain,
            double materialWeight,
            double graphWeight,
            double totalWeight,
            double modeWeight
    ) {
        private GainSource(
                int edgeId,
                int sccId,
                double baseGain,
                double materialWeight,
                double graphWeight,
                double totalWeight
        ) {
            this(edgeId, sccId, baseGain, materialWeight, graphWeight, totalWeight, 1.0);
        }

        private static GainSource passive() {
            return new GainSource(-1, -1, 1.0, 0.0, 0.0, 0.0, 0.0);
        }

        private GainSource withWeights(double graphWeight, double totalWeight) {
            return new GainSource(edgeId, sccId, baseGain, materialWeight, graphWeight, totalWeight, modeWeight);
        }

        private GainSource withModeWeight(double modeWeight) {
            return new GainSource(edgeId, sccId, baseGain, materialWeight, graphWeight, totalWeight, modeWeight);
        }
    }

    @FunctionalInterface
    private interface EdgeGain {
        double gainFor(PortGraphEdge edge);
    }
}
