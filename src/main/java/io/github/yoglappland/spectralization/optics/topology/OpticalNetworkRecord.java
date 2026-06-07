package io.github.yoglappland.spectralization.optics.topology;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

public record OpticalNetworkRecord(
        int id,
        Set<BlockPos> nodes,
        List<PotentialEdge> edges,
        int possibleSourceCount,
        boolean allNodesLoaded
) {
    public OpticalNetworkRecord {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");

        nodes = Set.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
