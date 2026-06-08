package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphScc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record GainGraphIndex(
        Map<Integer, PortGraphEdge> edgesById,
        Map<Integer, PortGraphScc> feedbackSccsById
) {
    static GainGraphIndex from(CompiledPortGraph graph) {
        Map<Integer, PortGraphEdge> edgesById = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            edgesById.put(edge.id(), edge);
        }

        Map<Integer, PortGraphScc> feedbackSccsById = new HashMap<>();

        for (PortGraphScc scc : graph.sccs()) {
            if (scc.feedback()) {
                feedbackSccsById.put(scc.id(), scc);
            }
        }

        return new GainGraphIndex(edgesById, feedbackSccsById);
    }

    List<PortGraphEdge> internalEdges(PortGraphScc scc) {
        List<PortGraphEdge> edges = new ArrayList<>();

        for (int edgeId : scc.edgeIds()) {
            PortGraphEdge edge = edgesById.get(edgeId);

            if (edge != null) {
                edges.add(edge);
            }
        }

        return edges;
    }
}
