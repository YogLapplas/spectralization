package io.github.yoglappland.spectralization.compact;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.CompactMachinePartBlock;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class CompactMachineValidator {
    public static CompactMachineValidationResult refresh(
            ServerLevel level,
            List<CompactMachineConnection> connections,
            BlockPos origin,
            int radius
    ) {
        return refreshAffected(level, connections, Set.of(origin), origin, true);
    }

    public static CompactMachineValidationResult refreshAffected(
            ServerLevel level,
            List<CompactMachineConnection> connections,
            Set<BlockPos> seeds,
            BlockPos changedPos
    ) {
        return refreshAffected(level, connections, seeds, changedPos, true);
    }

    public static CompactMachineValidationResult refreshAffected(
            ServerLevel level,
            List<CompactMachineConnection> connections,
            Set<BlockPos> seeds,
            BlockPos changedPos,
            boolean includeContainingFrames
    ) {
        if (connections.isEmpty()) {
            return new CompactMachineValidationResult(0, 0, setError(level, compactSeedParts(level, seeds), true));
        }

        Map<BlockPos, List<CompactMachineConnection>> graph = graph(connections);
        List<ConnectionComponent> components = components(graph, connections);
        List<ConnectionComponent> affectedComponents = components.stream()
                .filter(component -> component.isAffectedBy(seeds, changedPos, includeContainingFrames))
                .toList();

        boolean changedErrorStates = false;
        int validFrames = 0;
        int invalidFrames = 0;
        Set<BlockPos> handledPartPositions = new HashSet<>();

        for (ConnectionComponent component : affectedComponents) {
            FrameCheck check = checkFrame(level, component.endpoints(), component.connections());
            handledPartPositions.addAll(check.partPositions());

            if (check.valid()) {
                validFrames++;
                changedErrorStates |= setError(level, check.partPositions(), false);
                Spectralization.LOGGER.info(
                        "Compact machine frame valid in {}: {} -> {}, parts {}, core {}, io {}",
                        level.dimension().location(),
                        formatPos(check.min()),
                        formatPos(check.max()),
                        check.partPositions().size(),
                        check.coreCount(),
                        check.ioCount()
                );
            } else {
                invalidFrames++;
                changedErrorStates |= setError(level, check.partPositions(), true);
                Spectralization.LOGGER.info(
                        "Compact machine frame invalid in {} near {}: {}",
                        level.dimension().location(),
                        formatPos(component.representative()),
                        check.reason()
                );
            }
        }

        Set<BlockPos> unhandledSeedParts = compactSeedParts(level, seeds);
        unhandledSeedParts.removeAll(handledPartPositions);
        changedErrorStates |= setError(level, unhandledSeedParts, true);

        return new CompactMachineValidationResult(validFrames, invalidFrames, changedErrorStates);
    }

    public static CompactMachineFrameInfo inspectAt(
            ServerLevel level,
            List<CompactMachineConnection> connections,
            BlockPos corePos
    ) {
        if (connections.isEmpty()) {
            return CompactMachineFrameInfo.missing();
        }

        Map<BlockPos, List<CompactMachineConnection>> graph = graph(connections);
        for (ConnectionComponent component : components(graph, connections)) {
            Bounds bounds = Bounds.of(component.endpoints());
            if (bounds == null || !bounds.contains(corePos)) {
                continue;
            }

            FrameCheck check = checkFrame(level, component.endpoints(), component.connections());
            Bounds workBounds = bounds.workBounds();
            return new CompactMachineFrameInfo(
                    true,
                    check.valid(),
                    bounds.min(),
                    bounds.max(),
                    bounds.sizeX(),
                    bounds.sizeY(),
                    bounds.sizeZ(),
                    workBounds == null ? BlockPos.ZERO : workBounds.min(),
                    workBounds == null ? BlockPos.ZERO : workBounds.max(),
                    workBounds == null ? 0 : workBounds.sizeX(),
                    workBounds == null ? 0 : workBounds.sizeY(),
                    workBounds == null ? 0 : workBounds.sizeZ(),
                    component.connections().size(),
                    check.partPositions().size(),
                    check.compactBlockCount(),
                    check.compactTypeCount(),
                    check.ioCount(),
                    check.payloadBlockCount(),
                    check.payloadTypeCount(),
                    check.reason()
            );
        }

        return CompactMachineFrameInfo.missing();
    }

    public static boolean hasCandidateShellContaining(List<CompactMachineConnection> connections, BlockPos pos) {
        if (connections.isEmpty()) {
            return false;
        }

        Map<BlockPos, List<CompactMachineConnection>> graph = graph(connections);
        for (ConnectionComponent component : components(graph, connections)) {
            Bounds bounds = Bounds.of(component.endpoints());
            if (bounds != null && bounds.hasVolume() && bounds.contains(pos) && bounds.isOnSurface(pos)) {
                return true;
            }
        }

        return false;
    }

    private static FrameCheck checkFrame(
            ServerLevel level,
            Set<BlockPos> endpoints,
            List<CompactMachineConnection> connections
    ) {
        Bounds bounds = Bounds.of(endpoints);
        if (bounds == null) {
            return FrameCheck.invalid("bounds are empty", compactParts(level, endpoints));
        }

        Set<BlockPos> corners = bounds.corners();
        MachineScan scan = scanMachineArea(level, bounds, corners);
        if (!bounds.hasVolume()) {
            return FrameCheck.invalid("bounds are not a 3D rectangular frame", scan);
        }

        if (!bounds.hasWorkArea()) {
            return FrameCheck.invalid("frame has no internal work area", scan);
        }

        if (!endpoints.equals(corners)) {
            return FrameCheck.invalid("connection endpoints are not exactly the 8 frame corners", scan);
        }

        Set<CompactMachineConnectionKey> edgeKeys = new HashSet<>();
        for (CompactMachineConnection connection : connections) {
            edgeKeys.add(CompactMachineConnectionKey.of(connection.from(), connection.to()));
        }

        for (CompactMachineConnectionKey requiredEdge : bounds.requiredEdges()) {
            if (!edgeKeys.contains(requiredEdge)) {
                return FrameCheck.invalid(
                        "missing frame edge " + formatPos(requiredEdge.first()) + " -> " + formatPos(requiredEdge.second()),
                        scan
                );
            }
        }

        if (!scan.validPlacement()) {
            return FrameCheck.invalid(scan.invalidReason(), scan);
        }

        if (scan.coreCount() != 1) {
            return FrameCheck.invalid("expected exactly one core, found " + scan.coreCount(), scan);
        }

        if (scan.ioCount() > 6) {
            return FrameCheck.invalid("expected at most 6 io ports, found " + scan.ioCount(), scan);
        }

        return FrameCheck.valid(bounds.min(), bounds.max(), scan);
    }

    private static Map<BlockPos, List<CompactMachineConnection>> graph(List<CompactMachineConnection> connections) {
        Map<BlockPos, List<CompactMachineConnection>> graph = new HashMap<>();

        for (CompactMachineConnection connection : connections) {
            graph.computeIfAbsent(connection.from(), ignored -> new ArrayList<>()).add(connection);
            graph.computeIfAbsent(connection.to(), ignored -> new ArrayList<>()).add(connection);
        }

        return graph;
    }

    private static List<ConnectionComponent> components(
            Map<BlockPos, List<CompactMachineConnection>> graph,
            List<CompactMachineConnection> connections
    ) {
        List<ConnectionComponent> components = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        for (BlockPos start : graph.keySet()) {
            if (!visited.add(start)) {
                continue;
            }

            Set<BlockPos> endpoints = component(start, graph, visited);
            components.add(new ConnectionComponent(endpoints, componentConnections(endpoints, connections)));
        }

        return components;
    }

    private static Set<BlockPos> component(
            BlockPos start,
            Map<BlockPos, List<CompactMachineConnection>> graph,
            Set<BlockPos> visited
    ) {
        Set<BlockPos> component = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        component.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (CompactMachineConnection connection : graph.getOrDefault(current, List.of())) {
                BlockPos next = connection.from().equals(current) ? connection.to() : connection.from();
                if (component.add(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }

        return component;
    }

    private static List<CompactMachineConnection> componentConnections(
            Set<BlockPos> component,
            List<CompactMachineConnection> connections
    ) {
        return connections.stream()
                .filter(connection -> component.contains(connection.from()) && component.contains(connection.to()))
                .toList();
    }

    private static boolean setError(ServerLevel level, Iterable<BlockPos> positions, boolean error) {
        boolean changed = false;

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CompactMachinePartBlock) || state.getValue(CompactMachinePartBlock.ERROR) == error) {
                continue;
            }

            level.setBlock(pos, state.setValue(CompactMachinePartBlock.ERROR, error), Block.UPDATE_ALL);
            changed = true;
        }

        return changed;
    }

    private static MachineScan scanMachineArea(ServerLevel level, Bounds bounds, Set<BlockPos> corners) {
        Set<BlockPos> partPositions = new HashSet<>();
        Set<Block> compactTypes = new HashSet<>();
        Set<Block> payloadTypes = new HashSet<>();
        int compactBlockCount = 0;
        int payloadBlockCount = 0;
        int coreCount = 0;
        int ioCount = 0;
        String invalidReason = null;

        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockState state = level.getBlockState(pos);
            BlockPos immutablePos = pos.immutable();
            boolean onSurface = bounds.isOnSurface(immutablePos);
            boolean compactTagged = state.is(SpectralBlockTags.COMPACT_MACHINE);

            if (compactTagged) {
                compactBlockCount++;
                compactTypes.add(state.getBlock());
            }

            if (onSurface) {
                if (!state.isAir() && !compactTagged && invalidReason == null) {
                    invalidReason = "non compact block on frame shell at " + formatPos(immutablePos);
                }
            } else if (compactTagged) {
                if (invalidReason == null) {
                    invalidReason = "compact machine block is inside work area at " + formatPos(immutablePos);
                }
            } else if (!state.isAir()) {
                payloadBlockCount++;
                payloadTypes.add(state.getBlock());
            }

            if (!(state.getBlock() instanceof CompactMachinePartBlock part)) {
                continue;
            }

            partPositions.add(immutablePos);

            if (part.kind() == CompactMachinePartKind.ANCHOR && !corners.contains(immutablePos) && invalidReason == null) {
                invalidReason = "anchor is not a frame corner at " + formatPos(immutablePos);
            }

            if (part.kind() == CompactMachinePartKind.CORE) {
                coreCount++;
                if (!bounds.isOnSurface(immutablePos) && invalidReason == null) {
                    invalidReason = "core is not on frame surface at " + formatPos(immutablePos);
                }
            }

            if (part.kind() == CompactMachinePartKind.IO_PORT) {
                ioCount++;
                if (!bounds.isOnSurface(immutablePos) && invalidReason == null) {
                    invalidReason = "io port is not on frame surface at " + formatPos(immutablePos);
                }
            }
        }

        return new MachineScan(
                Set.copyOf(partPositions),
                compactBlockCount,
                compactTypes.size(),
                coreCount,
                ioCount,
                payloadBlockCount,
                payloadTypes.size(),
                invalidReason
        );
    }

    private static Set<BlockPos> compactSeedParts(ServerLevel level, Set<BlockPos> seeds) {
        return compactParts(level, seeds);
    }

    private static Set<BlockPos> compactParts(ServerLevel level, Set<BlockPos> positions) {
        Set<BlockPos> parts = new HashSet<>();
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).getBlock() instanceof CompactMachinePartBlock) {
                parts.add(pos.immutable());
            }
        }

        return parts;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private record FrameCheck(
            boolean valid,
            String reason,
            BlockPos min,
            BlockPos max,
            Set<BlockPos> partPositions,
            int coreCount,
            int ioCount,
            int compactBlockCount,
            int compactTypeCount,
            int payloadBlockCount,
            int payloadTypeCount
    ) {
        private static FrameCheck valid(BlockPos min, BlockPos max, MachineScan scan) {
            return new FrameCheck(
                    true,
                    "",
                    min,
                    max,
                    scan.partPositions(),
                    scan.coreCount(),
                    scan.ioCount(),
                    scan.compactBlockCount(),
                    scan.compactTypeCount(),
                    scan.payloadBlockCount(),
                    scan.payloadTypeCount()
            );
        }

        private static FrameCheck invalid(String reason, Set<BlockPos> partPositions) {
            return new FrameCheck(false, reason, BlockPos.ZERO, BlockPos.ZERO, partPositions, 0, 0, 0, 0, 0, 0);
        }

        private static FrameCheck invalid(String reason, MachineScan scan) {
            return new FrameCheck(
                    false,
                    reason,
                    BlockPos.ZERO,
                    BlockPos.ZERO,
                    scan.partPositions(),
                    scan.coreCount(),
                    scan.ioCount(),
                    scan.compactBlockCount(),
                    scan.compactTypeCount(),
                    scan.payloadBlockCount(),
                    scan.payloadTypeCount()
            );
        }
    }

    private record MachineScan(
            Set<BlockPos> partPositions,
            int compactBlockCount,
            int compactTypeCount,
            int coreCount,
            int ioCount,
            int payloadBlockCount,
            int payloadTypeCount,
            String invalidReason
    ) {
        private boolean validPlacement() {
            return invalidReason == null;
        }
    }

    private record ConnectionComponent(Set<BlockPos> endpoints, List<CompactMachineConnection> connections) {
        private BlockPos representative() {
            return endpoints.stream()
                    .findFirst()
                    .orElse(BlockPos.ZERO);
        }

        private boolean isAffectedBy(Set<BlockPos> seeds, BlockPos changedPos, boolean includeContainingFrames) {
            for (BlockPos seed : seeds) {
                if (endpoints.contains(seed)) {
                    return true;
                }
            }

            if (!includeContainingFrames) {
                return false;
            }

            Bounds bounds = Bounds.of(endpoints);
            if (bounds == null) {
                return false;
            }

            if (bounds.contains(changedPos)) {
                return true;
            }

            for (BlockPos seed : seeds) {
                if (bounds.contains(seed)) {
                    return true;
                }
            }

            return false;
        }
    }

    private record Bounds(BlockPos min, BlockPos max) {
        private static Bounds of(Set<BlockPos> positions) {
            if (positions.isEmpty()) {
                return null;
            }

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;

            for (BlockPos pos : positions) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }

            return new Bounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
        }

        private boolean hasVolume() {
            return min.getX() < max.getX() && min.getY() < max.getY() && min.getZ() < max.getZ();
        }

        private boolean hasWorkArea() {
            return max.getX() - min.getX() > 1
                    && max.getY() - min.getY() > 1
                    && max.getZ() - min.getZ() > 1;
        }

        private Bounds workBounds() {
            if (!hasWorkArea()) {
                return null;
            }

            return new Bounds(
                    new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ() + 1),
                    new BlockPos(max.getX() - 1, max.getY() - 1, max.getZ() - 1)
            );
        }

        private Set<BlockPos> corners() {
            Set<BlockPos> corners = new HashSet<>();
            corners.add(new BlockPos(min.getX(), min.getY(), min.getZ()));
            corners.add(new BlockPos(max.getX(), min.getY(), min.getZ()));
            corners.add(new BlockPos(min.getX(), max.getY(), min.getZ()));
            corners.add(new BlockPos(max.getX(), max.getY(), min.getZ()));
            corners.add(new BlockPos(min.getX(), min.getY(), max.getZ()));
            corners.add(new BlockPos(max.getX(), min.getY(), max.getZ()));
            corners.add(new BlockPos(min.getX(), max.getY(), max.getZ()));
            corners.add(new BlockPos(max.getX(), max.getY(), max.getZ()));
            return Set.copyOf(corners);
        }

        private List<CompactMachineConnectionKey> requiredEdges() {
            List<CompactMachineConnectionKey> edges = new ArrayList<>();

            for (int y : List.of(min.getY(), max.getY())) {
                for (int z : List.of(min.getZ(), max.getZ())) {
                    edges.add(CompactMachineConnectionKey.of(
                            new BlockPos(min.getX(), y, z),
                            new BlockPos(max.getX(), y, z)
                    ));
                }
            }

            for (int x : List.of(min.getX(), max.getX())) {
                for (int z : List.of(min.getZ(), max.getZ())) {
                    edges.add(CompactMachineConnectionKey.of(
                            new BlockPos(x, min.getY(), z),
                            new BlockPos(x, max.getY(), z)
                    ));
                }
            }

            for (int x : List.of(min.getX(), max.getX())) {
                for (int y : List.of(min.getY(), max.getY())) {
                    edges.add(CompactMachineConnectionKey.of(
                            new BlockPos(x, y, min.getZ()),
                            new BlockPos(x, y, max.getZ())
                    ));
                }
            }

            return edges;
        }

        private boolean isOnSurface(BlockPos pos) {
            return pos.getX() == min.getX()
                    || pos.getX() == max.getX()
                    || pos.getY() == min.getY()
                    || pos.getY() == max.getY()
                    || pos.getZ() == min.getZ()
                    || pos.getZ() == max.getZ();
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX()
                    && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY()
                    && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ()
                    && pos.getZ() <= max.getZ();
        }

        private int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        private int sizeY() {
            return max.getY() - min.getY() + 1;
        }

        private int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }
    }

    private CompactMachineValidator() {
    }
}
