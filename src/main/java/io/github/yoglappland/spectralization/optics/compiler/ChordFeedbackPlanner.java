package io.github.yoglappland.spectralization.optics.compiler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChordFeedbackPlanner {
    private static final Comparator<PortGraphNode> NODE_COMPARATOR = Comparator
            .comparingInt((PortGraphNode node) -> node.pos().getX())
            .thenComparingInt(node -> node.pos().getY())
            .thenComparingInt(node -> node.pos().getZ())
            .thenComparingInt(node -> node.side().ordinal())
            .thenComparingInt(node -> node.waveKind().ordinal());

    public static ChordFeedbackPlan plan(CompiledPortGraph graph) {
        if (graph.chords().isEmpty()) {
            return ChordFeedbackPlan.empty();
        }

        Map<PortGraphNode, Integer> graphNodeIds = ScalarPowerSolutions.nodeIds(graph.nodes());
        List<ChordFeedbackSystem> systems = new ArrayList<>();
        int variableCount = 0;
        int maxBeta1 = 0;
        boolean complete = true;

        for (PortGraphScc scc : graph.sccs()) {
            if (!scc.feedback() || scc.beta1() == 0) {
                continue;
            }

            List<PortGraphNode> localNodes = new ArrayList<>(scc.nodes());
            localNodes.sort(NODE_COMPARATOR);
            Map<PortGraphNode, Integer> localNodeIds = new HashMap<>();

            for (int index = 0; index < localNodes.size(); index++) {
                localNodeIds.put(localNodes.get(index), index);
            }

            List<ChordFeedbackVariable> variables = new ArrayList<>();

            for (PortGraphChord chord : graph.chords()) {
                if (chord.sccId() != scc.id()) {
                    continue;
                }

                if (chord.edgeId() < 0 || chord.edgeId() >= graph.edges().size()) {
                    complete = false;
                    continue;
                }

                PortGraphEdge edge = graph.edges().get(chord.edgeId());
                Integer fromGraphNodeId = graphNodeIds.get(chord.from());
                Integer toGraphNodeId = graphNodeIds.get(chord.to());
                Integer fromLocalNodeId = localNodeIds.get(chord.from());
                Integer toLocalNodeId = localNodeIds.get(chord.to());

                if (fromGraphNodeId == null
                        || toGraphNodeId == null
                        || fromLocalNodeId == null
                        || toLocalNodeId == null) {
                    complete = false;
                    continue;
                }

                variables.add(new ChordFeedbackVariable(
                        variables.size(),
                        scc.id(),
                        chord.id(),
                        chord.edgeId(),
                        fromGraphNodeId,
                        toGraphNodeId,
                        fromLocalNodeId,
                        toLocalNodeId,
                        chord.from(),
                        chord.to(),
                        edge.sampleGain()
                ));
            }

            boolean compilable = variables.size() == scc.beta1();
            complete &= compilable;
            variableCount += variables.size();
            maxBeta1 = Math.max(maxBeta1, scc.beta1());
            systems.add(new ChordFeedbackSystem(
                    scc.id(),
                    scc.nodes().size(),
                    scc.edgeIds().size(),
                    scc.beta1(),
                    variables,
                    compilable
            ));
        }

        return new ChordFeedbackPlan(systems, systems.size(), variableCount, maxBeta1, complete);
    }

    private ChordFeedbackPlanner() {
    }
}
