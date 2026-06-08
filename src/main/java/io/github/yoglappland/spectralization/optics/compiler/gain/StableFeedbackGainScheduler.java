package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
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
    private final SpectralRadiusEstimator spectralRadiusEstimator = new SpectralRadiusEstimator();
    private final EffectiveGainSoftcap softcap = new EffectiveGainSoftcap(spectralRadiusEstimator);

    @Override
    public GainSchedule schedule(Level level, CompiledPortGraph graph) {
        Objects.requireNonNull(graph, "graph");

        if (level == null
                || graph.feedbackSccCount() <= 0
                || graph.nodes().size() > MAX_SCHEDULED_NODES
                || graph.edges().size() > MAX_SCHEDULED_EDGES) {
            return GainSchedule.none(graph);
        }

        GainGraphIndex index = GainGraphIndex.from(graph);
        Map<Integer, GainSource> gainSourcesByEdgeId = sourceCollector.collect(level, graph, index);

        if (gainSourcesByEdgeId.isEmpty() || gainSourcesByEdgeId.size() > MAX_GAIN_SOURCES) {
            return GainSchedule.none(graph);
        }

        List<GainSource> gainSources = participationAnalyzer.weightedSources(graph, index, gainSourcesByEdgeId);
        double rhoBefore = spectralRadiusEstimator.estimate(
                graph,
                index,
                edge -> gainSourcesByEdgeId.getOrDefault(edge.id(), GainSource.passive()).baseGain()
        );
        ScheduledGains scheduledGains = softcap.schedule(index, gainSources);
        Map<Integer, Double> effectiveGainsByEdgeId = scheduledGains.effectiveGainsByEdgeId();
        double rhoAfter = softcap.estimateWithGains(graph, index, effectiveGainsByEdgeId);

        if (rhoAfter >= EffectiveGainSoftcap.HARD_RHO && softcap.hasActiveScheduledGain(effectiveGainsByEdgeId)) {
            effectiveGainsByEdgeId = softcap.shrinkExtraLogGainsToStable(graph, index, effectiveGainsByEdgeId);
            rhoAfter = softcap.estimateWithGains(graph, index, effectiveGainsByEdgeId);
        }

        CompiledPortGraph scheduledGraph = GainGraphRewriter.applyEffectiveGains(graph, effectiveGainsByEdgeId);

        return new GainSchedule(
                scheduledGraph,
                softcap.hasActiveScheduledGain(effectiveGainsByEdgeId),
                rhoAfter < EffectiveGainSoftcap.HARD_RHO,
                scheduledGains.passiveRho(),
                EffectiveGainSoftcap.HARD_RHO,
                rhoBefore,
                rhoAfter,
                gainSources.size(),
                scheduledGains.totalGainHeadroom(),
                scheduledGains.maxModeWeight(),
                scheduledGains.maxSourceCap(),
                maxBaseGain(gainSources),
                softcap.maxEffectiveGain(effectiveGainsByEdgeId)
        );
    }

    private static double maxBaseGain(List<GainSource> gainSources) {
        double maxGain = 1.0;

        for (GainSource source : gainSources) {
            maxGain = Math.max(maxGain, source.baseGain());
        }

        return maxGain;
    }
}
