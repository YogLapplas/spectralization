package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.Level;

final class StableFeedbackGainScheduler implements GainScheduler {
    private static final int MAX_SCHEDULED_NODES = 2048;
    private static final int MAX_SCHEDULED_EDGES = 8192;
    private static final int MAX_GAIN_SOURCES = 256;

    private final GainSourceCollector sourceCollector = new GainSourceCollector();
    private final GainParticipationAnalyzer participationAnalyzer = new GainParticipationAnalyzer();
    private final FeedbackGainBoundEstimator boundEstimator = new FeedbackGainBoundEstimator();
    private final EffectiveGainSoftcap softcap = new EffectiveGainSoftcap(boundEstimator);

    @Override
    public GainSchedule schedule(Level level, CompiledPortGraph graph) {
        Objects.requireNonNull(graph, "graph");

        if (level == null) {
            return GainSchedule.none(graph);
        }

        GainGraphIndex index = GainGraphIndex.from(graph);
        Map<Integer, GainSource> gainSourcesByEdgeId = sourceCollector.collect(level, graph, index);

        if (gainSourcesByEdgeId.isEmpty()) {
            return GainSchedule.none(graph);
        }

        Map<Integer, GainSource> feedbackGainSourcesByEdgeId = new HashMap<>();
        Map<Integer, Double> directEffectiveGainsByEdgeId = new HashMap<>();

        for (GainSource source : gainSourcesByEdgeId.values()) {
            if (source.sccId() >= 0 && index.feedbackSccsById().containsKey(source.sccId())) {
                feedbackGainSourcesByEdgeId.put(source.edgeId(), source);
            } else {
                directEffectiveGainsByEdgeId.put(source.edgeId(), source.baseGain());
            }
        }

        boolean analyzeParticipation = graph.nodes().size() <= MAX_SCHEDULED_NODES
                && graph.edges().size() <= MAX_SCHEDULED_EDGES
                && feedbackGainSourcesByEdgeId.size() <= MAX_GAIN_SOURCES;
        String feedbackMode = analyzeParticipation ? "chord_participation" : "fallback_material_weight";
        List<GainSource> feedbackGainSources = feedbackGainSourcesByEdgeId.isEmpty()
                ? List.of()
                : analyzeParticipation
                ? participationAnalyzer.weightedSources(graph, index, feedbackGainSourcesByEdgeId)
                : fallbackWeightedSources(feedbackGainSourcesByEdgeId);
        double rhoBefore = boundEstimator.estimate(
                graph,
                index,
                edge -> gainSourcesByEdgeId.getOrDefault(edge.id(), GainSource.passive()).baseGain()
        );
        ScheduledGains scheduledGains = feedbackGainSources.isEmpty()
                ? new ScheduledGains(Map.of(), 0.0, 0.0, 0.0, 1.0)
                : softcap.schedule(index, feedbackGainSources);
        Map<Integer, Double> feedbackEffectiveGainsByEdgeId = scheduledGains.effectiveGainsByEdgeId();
        double rhoAfter = softcap.estimateWithGains(graph, index, feedbackEffectiveGainsByEdgeId);

        if (rhoAfter >= EffectiveGainSoftcap.SOLVER_TARGET_RHO
                && softcap.hasActiveScheduledGain(feedbackEffectiveGainsByEdgeId)) {
            feedbackEffectiveGainsByEdgeId = softcap.shrinkExtraGainsToTarget(
                    graph,
                    index,
                    feedbackEffectiveGainsByEdgeId,
                    EffectiveGainSoftcap.SOLVER_TARGET_RHO
            );
            rhoAfter = softcap.estimateWithGains(graph, index, feedbackEffectiveGainsByEdgeId);
        }

        Map<Integer, Double> effectiveGainsByEdgeId = new HashMap<>(directEffectiveGainsByEdgeId);
        effectiveGainsByEdgeId.putAll(feedbackEffectiveGainsByEdgeId);
        CompiledPortGraph scheduledGraph = GainGraphRewriter.applyEffectiveGains(graph, effectiveGainsByEdgeId);
        String schedulerMode = schedulerMode(feedbackMode, directEffectiveGainsByEdgeId, feedbackGainSourcesByEdgeId);

        return new GainSchedule(
                scheduledGraph,
                softcap.hasActiveScheduledGain(effectiveGainsByEdgeId),
                rhoAfter < EffectiveGainSoftcap.HARD_RHO,
                schedulerMode,
                scheduledGains.passiveRho(),
                EffectiveGainSoftcap.SOLVER_TARGET_RHO,
                EffectiveGainSoftcap.HARD_RHO,
                rhoBefore,
                rhoAfter,
                gainSourcesByEdgeId.size(),
                scheduledGains.totalGainHeadroom() + directGainHeadroom(directEffectiveGainsByEdgeId),
                Math.max(scheduledGains.maxModeWeight(), directEffectiveGainsByEdgeId.isEmpty() ? 0.0 : 1.0),
                Math.max(scheduledGains.maxSourceCap(), maxDirectGain(directEffectiveGainsByEdgeId)),
                maxBaseGain(new ArrayList<>(gainSourcesByEdgeId.values())),
                softcap.maxEffectiveGain(effectiveGainsByEdgeId)
        );
    }

    private static String schedulerMode(
            String feedbackMode,
            Map<Integer, Double> directEffectiveGainsByEdgeId,
            Map<Integer, GainSource> feedbackGainSourcesByEdgeId
    ) {
        boolean hasDirect = !directEffectiveGainsByEdgeId.isEmpty();
        boolean hasFeedback = !feedbackGainSourcesByEdgeId.isEmpty();

        if (hasDirect && hasFeedback) {
            return feedbackMode + "+direct_gain";
        }

        if (hasDirect) {
            return "direct_gain";
        }

        return feedbackMode;
    }

    private static double directGainHeadroom(Map<Integer, Double> directEffectiveGainsByEdgeId) {
        double headroom = 0.0;

        for (double gain : directEffectiveGainsByEdgeId.values()) {
            headroom += Math.max(0.0, gain - 1.0);
        }

        return headroom;
    }

    private static double maxDirectGain(Map<Integer, Double> directEffectiveGainsByEdgeId) {
        double maxGain = 1.0;

        for (double gain : directEffectiveGainsByEdgeId.values()) {
            maxGain = Math.max(maxGain, gain);
        }

        return maxGain;
    }

    private static double maxBaseGain(List<GainSource> gainSources) {
        double maxGain = 1.0;

        for (GainSource source : gainSources) {
            maxGain = Math.max(maxGain, source.baseGain());
        }

        return maxGain;
    }

    private static List<GainSource> fallbackWeightedSources(Map<Integer, GainSource> gainSourcesByEdgeId) {
        List<GainSource> sources = new ArrayList<>(gainSourcesByEdgeId.values());
        sources.sort(Comparator.comparingInt(GainSource::edgeId));

        double maxTotalWeight = GainSourceCollector.MIN_PARTICIPATION;
        List<GainSource> weightedSources = new ArrayList<>(sources.size());

        for (GainSource source : sources) {
            double graphWeight = 1.0;
            double totalWeight = Math.max(GainSourceCollector.MIN_PARTICIPATION, source.materialWeight());
            maxTotalWeight = Math.max(maxTotalWeight, totalWeight);
            weightedSources.add(source.withWeights(graphWeight, totalWeight));
        }

        List<GainSource> normalizedSources = new ArrayList<>(weightedSources.size());

        for (GainSource source : weightedSources) {
            normalizedSources.add(source.withModeWeight(Math.min(1.0, source.totalWeight() / maxTotalWeight)));
        }

        return normalizedSources;
    }
}
