package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class GainSourceCollector {
    static final double MIN_PARTICIPATION = 1.0E-6;
    static final double MIN_GAIN_DELTA = 1.0E-6;

    Map<Integer, GainSource> collect(Level level, CompiledPortGraph graph, GainGraphIndex index) {
        Map<PortGraphNode, Integer> feedbackSccIdByNode = new HashMap<>();

        for (PortGraphScc scc : index.feedbackSccsById().values()) {
            for (PortGraphNode node : scc.nodes()) {
                feedbackSccIdByNode.put(node, scc.id());
            }
        }

        Map<Integer, GainSource> gainSourcesByEdgeId = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            if (!isLocalMaterialGainCandidate(edge)) {
                continue;
            }

            BlockPos pos = edge.from().pos();

            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockState state = level.getBlockState(pos);
            double materialWeight = OpticalMaterialProfiles.gainMaterialWeightFor(state);
            Map<FrequencyKey, Double> baseGainByFrequency = new HashMap<>();
            Map<FrequencyKey, Double> saturatedExtraOutputByFrequency = new HashMap<>();
            double baseGain = 1.0D;
            double saturatedExtraOutput = 0.0D;

            for (FrequencyKey frequency : edgeFrequencies(edge)) {
                double frequencyBaseGain = OpticalMaterialProfiles.scheduledCoherentBaseGainFor(
                        level,
                        pos,
                        state,
                        frequency
                );

                if (frequencyBaseGain <= 1.0D + MIN_GAIN_DELTA) {
                    continue;
                }

                double frequencySaturatedExtraOutput = OpticalMaterialProfiles.saturatedCoherentExtraOutputFor(
                        level,
                        pos,
                        state,
                        frequency
                );
                baseGainByFrequency.put(frequency, frequencyBaseGain);
                baseGain = Math.max(baseGain, frequencyBaseGain);

                if (frequencySaturatedExtraOutput > 0.0D) {
                    saturatedExtraOutputByFrequency.put(frequency, frequencySaturatedExtraOutput);
                    saturatedExtraOutput = Math.max(saturatedExtraOutput, frequencySaturatedExtraOutput);
                }
            }

            if (baseGain <= 1.0 + MIN_GAIN_DELTA || materialWeight <= 0.0) {
                continue;
            }

            Integer fromFeedbackSccId = feedbackSccIdByNode.get(edge.from());
            Integer toFeedbackSccId = feedbackSccIdByNode.get(edge.to());
            int sccId = fromFeedbackSccId != null && fromFeedbackSccId.equals(toFeedbackSccId)
                    ? fromFeedbackSccId
                    : -1;

            gainSourcesByEdgeId.put(edge.id(), new GainSource(
                    edge.id(),
                    sccId,
                    pos,
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                    baseGain,
                    saturatedExtraOutput,
                    OpticalMaterialProfiles.scheduledCoherentGainAffectsAllPresentFrequencies(state)
                            ? GainSpectralScope.ALL_PRESENT_FREQUENCIES
                            : GainSpectralScope.SAMPLE_FREQUENCY,
                    materialWeight,
                    baseGainByFrequency,
                    saturatedExtraOutputByFrequency
            ));
        }

        return gainSourcesByEdgeId;
    }

    private static Set<FrequencyKey> edgeFrequencies(PortGraphEdge edge) {
        Set<FrequencyKey> frequencies = new HashSet<>(edge.sampleGainByFrequency().keySet());
        frequencies.add(edge.sampleFrequency());
        return frequencies;
    }

    private static boolean isLocalMaterialGainCandidate(PortGraphEdge edge) {
        return edge.kind() == PortGraphEdgeKind.LOCAL_SCATTERING
                && edge.sampleGain() > 0.0
                && edge.from().pos().equals(edge.to().pos())
                && edge.from().side().getOpposite() == edge.to().side();
    }
}
