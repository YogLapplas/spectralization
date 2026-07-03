package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.Level;

final class MaterialGainScheduler implements GainScheduler {
    private final GainSourceCollector sourceCollector = new GainSourceCollector();

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

        Map<Integer, Double> effectiveGainsByEdgeId = new HashMap<>();

        for (GainSource source : gainSourcesByEdgeId.values()) {
            effectiveGainsByEdgeId.put(source.edgeId(), source.baseGain());
        }

        CompiledPortGraph scheduledGraph = GainGraphRewriter.applyEffectiveGains(
                graph,
                gainSourcesByEdgeId,
                effectiveGainsByEdgeId
        );

        return new GainSchedule(
                scheduledGraph,
                hasActiveScheduledGain(effectiveGainsByEdgeId),
                "material_gain",
                gainSourcesByEdgeId.size(),
                totalGainHeadroom(gainSourcesByEdgeId),
                maxBaseGain(gainSourcesByEdgeId),
                maxEffectiveGain(effectiveGainsByEdgeId),
                gainSourceDebugInfo(gainSourcesByEdgeId)
        );
    }

    private static List<GainSchedule.GainSourceDebugInfo> gainSourceDebugInfo(
            Map<Integer, GainSource> gainSourcesByEdgeId
    ) {
        List<GainSource> sources = new ArrayList<>(gainSourcesByEdgeId.values());
        sources.sort(Comparator.comparingInt(GainSource::edgeId));
        List<GainSchedule.GainSourceDebugInfo> debugInfo = new ArrayList<>(sources.size());

        for (GainSource source : sources) {
            debugInfo.add(new GainSchedule.GainSourceDebugInfo(
                    source.edgeId(),
                    source.sccId(),
                    source.pos().getX(),
                    source.pos().getY(),
                    source.pos().getZ(),
                    source.materialId(),
                    source.spectralScope().name(),
                    source.materialWeight(),
                    source.baseGain(),
                    source.saturatedExtraOutput(),
                    source.baseGainByFrequency().size(),
                    source.saturatedExtraOutputByFrequency().size()
            ));
        }

        return debugInfo;
    }

    private static boolean hasActiveScheduledGain(Map<Integer, Double> effectiveGainsByEdgeId) {
        for (double gain : effectiveGainsByEdgeId.values()) {
            if (gain > 1.0 + GainSourceCollector.MIN_GAIN_DELTA) {
                return true;
            }
        }

        return false;
    }

    private static double totalGainHeadroom(Map<Integer, GainSource> gainSourcesByEdgeId) {
        double headroom = 0.0;

        for (GainSource source : gainSourcesByEdgeId.values()) {
            headroom += Math.max(0.0, source.baseGain() - 1.0);
        }

        return headroom;
    }

    private static double maxBaseGain(Map<Integer, GainSource> gainSourcesByEdgeId) {
        double maxGain = 1.0;

        for (GainSource source : gainSourcesByEdgeId.values()) {
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
}
