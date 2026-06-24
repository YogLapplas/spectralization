package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
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
            double baseGain = OpticalMaterialProfiles.scheduledCoherentBaseGainFor(
                    level,
                    pos,
                    state,
                    edge.sampleFrequency()
            );
            double materialWeight = OpticalMaterialProfiles.gainMaterialWeightFor(state);

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
                    baseGain,
                    OpticalMaterialProfiles.scheduledCoherentGainAffectsAllPresentFrequencies(state)
                            ? GainSpectralScope.ALL_PRESENT_FREQUENCIES
                            : GainSpectralScope.SAMPLE_FREQUENCY,
                    materialWeight,
                    MIN_PARTICIPATION,
                    MIN_PARTICIPATION
            ));
        }

        return gainSourcesByEdgeId;
    }

    private static boolean isLocalMaterialGainCandidate(PortGraphEdge edge) {
        return edge.kind() == PortGraphEdgeKind.LOCAL_SCATTERING
                && edge.sampleGain() > 0.0
                && edge.from().pos().equals(edge.to().pos())
                && edge.from().side().getOpposite() == edge.to().side();
    }
}
