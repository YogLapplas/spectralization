package io.github.yoglappland.spectralization.optics.topology;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

public record OpticalNetworkSnapshot(
        List<OpticalNetworkRecord> networks,
        Set<BlockPos> overlayPositions,
        boolean dirty
) {
    public static final OpticalNetworkSnapshot EMPTY = new OpticalNetworkSnapshot(List.of(), Set.of(), true);

    public OpticalNetworkSnapshot {
        Objects.requireNonNull(networks, "networks");
        Objects.requireNonNull(overlayPositions, "overlayPositions");

        networks = List.copyOf(networks);
        overlayPositions = Set.copyOf(overlayPositions);
    }

    public int nodeCount() {
        return networks.stream()
                .mapToInt(network -> network.nodes().size())
                .sum();
    }

    public int edgeCount() {
        return networks.stream()
                .mapToInt(network -> network.edges().size())
                .sum();
    }
}
