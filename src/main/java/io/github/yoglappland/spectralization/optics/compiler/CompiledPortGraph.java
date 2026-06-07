package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record CompiledPortGraph(
        BlockPos sourcePos,
        Direction sourceDirection,
        PortGraphNode sourceNode,
        List<PortGraphNode> nodes,
        List<PortGraphEdge> edges,
        List<PortGraphScc> sccs,
        List<PortGraphChord> chords,
        int terminationCount
) {
    public CompiledPortGraph {
        Objects.requireNonNull(sourcePos, "sourcePos");
        Objects.requireNonNull(sourceDirection, "sourceDirection");
        Objects.requireNonNull(sourceNode, "sourceNode");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(sccs, "sccs");
        Objects.requireNonNull(chords, "chords");

        if (terminationCount < 0) {
            throw new IllegalArgumentException("Termination count must be non-negative");
        }

        sourcePos = sourcePos.immutable();
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        sccs = List.copyOf(sccs);
        chords = List.copyOf(chords);
    }

    public int feedbackSccCount() {
        int count = 0;

        for (PortGraphScc scc : sccs) {
            if (scc.feedback()) {
                count++;
            }
        }

        return count;
    }

    public int beta1() {
        int beta1 = 0;

        for (PortGraphScc scc : sccs) {
            beta1 += scc.beta1();
        }

        return beta1;
    }
}
