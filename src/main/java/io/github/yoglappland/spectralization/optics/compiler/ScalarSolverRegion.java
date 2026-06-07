package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ScalarSolverRegion(
        int id,
        int graphSccId,
        Set<PortGraphNode> nodes,
        List<Integer> edgeIds,
        int beta1,
        boolean feedback,
        ScalarSolverKind preferredSolverKind,
        ScalarSolverKind executionSolverKind
) {
    public ScalarSolverRegion {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edgeIds, "edgeIds");
        Objects.requireNonNull(preferredSolverKind, "preferredSolverKind");
        Objects.requireNonNull(executionSolverKind, "executionSolverKind");

        if (id < 0) {
            throw new IllegalArgumentException("Solver region id must be non-negative");
        }

        if (graphSccId < -1) {
            throw new IllegalArgumentException("Solver region graph SCC id must be -1 or non-negative");
        }

        if (beta1 < 0) {
            throw new IllegalArgumentException("Solver region beta1 must be non-negative");
        }

        nodes = Set.copyOf(nodes);
        edgeIds = List.copyOf(edgeIds);
    }

    public boolean fallback() {
        return preferredSolverKind != executionSolverKind;
    }
}
