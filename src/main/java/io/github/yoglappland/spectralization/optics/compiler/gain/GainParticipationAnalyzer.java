package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphChord;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GainParticipationAnalyzer {
    private static final int MAX_ANALYZED_CHORDS_PER_SCC = 128;

    List<GainSource> weightedSources(
            CompiledPortGraph graph,
            GainGraphIndex index,
            Map<Integer, GainSource> gainSourcesByEdgeId
    ) {
        Map<Integer, Double> graphWeightsByEdgeId = graphWeightsByEdgeId(graph, index, gainSourcesByEdgeId);
        return weightedGainSources(gainSourcesByEdgeId, graphWeightsByEdgeId);
    }

    private static Map<Integer, Double> graphWeightsByEdgeId(
            CompiledPortGraph graph,
            GainGraphIndex index,
            Map<Integer, GainSource> gainSourcesByEdgeId
    ) {
        Map<Integer, Double> graphWeightsByEdgeId = new HashMap<>();

        for (Integer edgeId : gainSourcesByEdgeId.keySet()) {
            graphWeightsByEdgeId.put(edgeId, GainSourceCollector.MIN_PARTICIPATION);
        }

        Map<Integer, List<PortGraphChord>> chordsBySccId = chordsBySccId(graph);

        for (Map.Entry<Integer, PortGraphScc> entry : index.feedbackSccsById().entrySet()) {
            int sccId = entry.getKey();
            PortGraphScc scc = entry.getValue();
            List<PortGraphChord> chords = chordsBySccId.getOrDefault(sccId, List.of());

            if (chords.isEmpty() || chords.size() > MAX_ANALYZED_CHORDS_PER_SCC) {
                addFallbackGraphWeights(scc, gainSourcesByEdgeId, graphWeightsByEdgeId);
                continue;
            }

            List<PortGraphEdge> internalEdges = index.internalEdges(scc);
            Map<PortGraphNode, List<PortGraphEdge>> outgoingEdges = outgoingEdges(internalEdges);

            for (PortGraphChord chord : chords) {
                PortGraphEdge chordEdge = index.edgesById().get(chord.edgeId());

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
        double maxTotalWeight = GainSourceCollector.MIN_PARTICIPATION;
        List<GainSource> weightedSources = new ArrayList<>(gainSourcesByEdgeId.size());

        for (GainSource source : gainSourcesByEdgeId.values()) {
            double graphWeight = Math.max(GainSourceCollector.MIN_PARTICIPATION, graphWeightsByEdgeId.getOrDefault(
                    source.edgeId(),
                    GainSourceCollector.MIN_PARTICIPATION
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
}
