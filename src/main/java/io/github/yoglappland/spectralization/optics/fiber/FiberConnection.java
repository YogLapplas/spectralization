package io.github.yoglappland.spectralization.optics.fiber;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record FiberConnection(
        UUID id,
        BlockPos endpointA,
        BlockPos endpointB,
        List<BlockPos> nodes,
        double totalLength,
        FiberMaterialProfile profile,
        long createdGameTime
) {
    public FiberConnection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(endpointA, "endpointA");
        Objects.requireNonNull(endpointB, "endpointB");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(profile, "profile");
        endpointA = endpointA.immutable();
        endpointB = endpointB.immutable();
        nodes = nodes.stream().map(BlockPos::immutable).toList();

        if (nodes.size() < 2 || !nodes.getFirst().equals(endpointA) || !nodes.getLast().equals(endpointB)) {
            throw new IllegalArgumentException("Fiber connection route must start and end at its endpoints");
        }

        if (!Double.isFinite(totalLength) || totalLength <= 0.0) {
            throw new IllegalArgumentException("Fiber connection length must be finite and positive");
        }
    }

    public static FiberConnection fromRoute(
            UUID id,
            FiberRoute route,
            FiberMaterialProfile profile,
            long createdGameTime
    ) {
        return new FiberConnection(
                id,
                route.start(),
                route.end(),
                route.nodes(),
                route.totalLength(),
                profile,
                createdGameTime
        );
    }

    public FiberRoute route() {
        return FiberRoute.fromNodes(nodes);
    }

    public boolean touches(BlockPos pos) {
        return nodes.contains(pos);
    }

    public boolean connects(BlockPos left, BlockPos right) {
        return (endpointA.equals(left) && endpointB.equals(right))
                || (endpointA.equals(right) && endpointB.equals(left));
    }
}
