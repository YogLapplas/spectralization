package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record PortGraphScc(
        int id,
        Set<PortGraphNode> nodes,
        List<Integer> edgeIds,
        int beta1,
        boolean feedback
) {
    public PortGraphScc {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edgeIds, "edgeIds");

        if (id < 0) {
            throw new IllegalArgumentException("SCC id must be non-negative");
        }

        if (beta1 < 0) {
            throw new IllegalArgumentException("SCC beta1 must be non-negative");
        }

        nodes = Set.copyOf(nodes);
        edgeIds = List.copyOf(edgeIds);
    }
}
