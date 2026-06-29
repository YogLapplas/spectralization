package io.github.yoglappland.spectralization.microlizer;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.MicrolizerPartBlock;
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

public final class MicrolizerValidator {
    public static MicrolizerValidationResult refresh(
            ServerLevel level,
            List<MicrolizerConnection> connections,
            BlockPos origin,
            int radius
    ) {
        return refreshAffected(level, connections, Set.of(origin), origin, true);
    }

    public static MicrolizerValidationResult refreshAffected(
            ServerLevel level,
            List<MicrolizerConnection> connections,
            Set<BlockPos> seeds,
            BlockPos changedPos
    ) {
        return refreshAffected(level, connections, seeds, changedPos, true);
    }

    public static MicrolizerValidationResult refreshAffected(
            ServerLevel level,
            List<MicrolizerConnection> connections,
            Set<BlockPos> seeds,
            BlockPos changedPos,
            boolean includeContainingFrames
    ) {
        if (connections.isEmpty()) {
            return new MicrolizerValidationResult(0, 0, setError(level, microlizerSeedParts(level, seeds), true));
        }

        Map<BlockPos, List<MicrolizerConnection>> graph = graph(connections);
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
                        "Microlizer frame valid in {}: {} -> {}, parts {}, core {}, io {}",
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
                        "Microlizer frame invalid in {} near {}: {}",
                        level.dimension().location(),
                        formatPos(component.representative()),
                        check.reason()
                );
            }
        }

        Set<BlockPos> unhandledSeedParts = microlizerSeedParts(level, seeds);
        unhandledSeedParts.removeAll(handledPartPositions);
        changedErrorStates |= setError(level, unhandledSeedParts, true);

        return new MicrolizerValidationResult(validFrames, invalidFrames, changedErrorStates);
    }

    public static MicrolizerFrameInfo inspectAt(
            ServerLevel level,
            List<MicrolizerConnection> connections,
            BlockPos corePos
    ) {
        if (connections.isEmpty()) {
            return MicrolizerFrameInfo.missing();
        }

        Map<BlockPos, List<MicrolizerConnection>> graph = graph(connections);
        for (ConnectionComponent component : components(graph, connections)) {
            Bounds bounds = Bounds.of(component.endpoints());
            if (bounds == null || !bounds.contains(corePos)) {
                continue;
            }

            FrameCheck check = checkFrame(level, component.endpoints(), component.connections());
            Bounds workBounds = bounds.workBounds();
            return new MicrolizerFrameInfo(
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
                    check.microlizerBlockCount(),
                    check.microlizerTypeCount(),
                    check.ioCount(),
                    check.payloadBlockCount(),
                    check.payloadTypeCount(),
                    check.reason()
            );
        }

        return MicrolizerFrameInfo.missing();
    }

    public static boolean hasCandidateShellContaining(List<MicrolizerConnection> connections, BlockPos pos) {
        if (connections.isEmpty()) {
            return false;
        }

        Map<BlockPos, List<MicrolizerConnection>> graph = graph(connections);
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
            List<MicrolizerConnection> connections
    ) {
        Bounds bounds = Bounds.of(endpoints);
        if (bounds == null) {
            return FrameCheck.invalid("bounds are empty", microlizerParts(level, endpoints));
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

        Set<MicrolizerConnectionKey> edgeKeys = new HashSet<>();
        for (MicrolizerConnection connection : connections) {
            edgeKeys.add(MicrolizerConnectionKey.of(connection.from(), connection.to()));
        }

        for (MicrolizerConnectionKey requiredEdge : bounds.requiredEdges()) {
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

    private static Map<BlockPos, List<MicrolizerConnection>> graph(List<MicrolizerConnection> connections) {
        Map<BlockPos, List<MicrolizerConnection>> graph = new HashMap<>();

        for (MicrolizerConnection connection : connections) {
            graph.computeIfAbsent(connection.from(), ignored -> new ArrayList<>()).add(connection);
            graph.computeIfAbsent(connection.to(), ignored -> new ArrayList<>()).add(connection);
        }

        return graph;
    }

    private static List<ConnectionComponent> components(
            Map<BlockPos, List<MicrolizerConnection>> graph,
            List<MicrolizerConnection> connections
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
            Map<BlockPos, List<MicrolizerConnection>> graph,
            Set<BlockPos> visited
    ) {
        Set<BlockPos> component = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        component.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (MicrolizerConnection connection : graph.getOrDefault(current, List.of())) {
                BlockPos next = connection.from().equals(current) ? connection.to() : connection.from();
                if (component.add(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }

        return component;
    }

    private static List<MicrolizerConnection> componentConnections(
            Set<BlockPos> component,
            List<MicrolizerConnection> connections
    ) {
        return connections.stream()
                .filter(connection -> component.contains(connection.from()) && component.contains(connection.to()))
                .toList();
    }

    private static boolean setError(ServerLevel level, Iterable<BlockPos> positions, boolean error) {
        boolean changed = false;

        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof MicrolizerPartBlock) || state.getValue(MicrolizerPartBlock.ERROR) == error) {
                continue;
            }

            level.setBlock(pos, state.setValue(MicrolizerPartBlock.ERROR, error), Block.UPDATE_ALL);
            changed = true;
        }

        return changed;
    }

    private static MachineScan scanMachineArea(ServerLevel level, Bounds bounds, Set<BlockPos> corners) {
        Set<BlockPos> partPositions = new HashSet<>();
        Set<Block> microlizerTypes = new HashSet<>();
        Set<Block> payloadTypes = new HashSet<>();
        int microlizerBlockCount = 0;
        int payloadBlockCount = 0;
        int coreCount = 0;
        int ioCount = 0;
        String invalidReason = null;

        for (BlockPos pos : BlockPos.betweenClosed(bounds.min(), bounds.max())) {
            BlockState state = level.getBlockState(pos);
            BlockPos immutablePos = pos.immutable();
            boolean onSurface = bounds.isOnSurface(immutablePos);
            boolean microlizerTagged = state.is(SpectralBlockTags.MICROLIZER);

            if (microlizerTagged) {
                microlizerBlockCount++;
                microlizerTypes.add(state.getBlock());
            }

            if (onSurface) {
                if (!state.isAir() && !microlizerTagged && invalidReason == null) {
                    invalidReason = "non microlizer block on frame shell at " + formatPos(immutablePos);
                }
            } else if (microlizerTagged) {
                if (invalidReason == null) {
                    invalidReason = "microlizer block is inside work area at " + formatPos(immutablePos);
                }
            } else if (!state.isAir()) {
                payloadBlockCount++;
                payloadTypes.add(state.getBlock());
            }

            if (!(state.getBlock() instanceof MicrolizerPartBlock part)) {
                continue;
            }

            partPositions.add(immutablePos);

            if (part.kind() == MicrolizerPartKind.ANCHOR && !corners.contains(immutablePos) && invalidReason == null) {
                invalidReason = "anchor is not a frame corner at " + formatPos(immutablePos);
            }

            if (part.kind() == MicrolizerPartKind.CORE) {
                coreCount++;
                if (!bounds.isOnSurface(immutablePos) && invalidReason == null) {
                    invalidReason = "core is not on frame surface at " + formatPos(immutablePos);
                }
            }

            if (part.kind() == MicrolizerPartKind.IO_PORT) {
                ioCount++;
                if (!bounds.isOnSurface(immutablePos) && invalidReason == null) {
                    invalidReason = "io port is not on frame surface at " + formatPos(immutablePos);
                }
            }
        }

        return new MachineScan(
                Set.copyOf(partPositions),
                microlizerBlockCount,
                microlizerTypes.size(),
                coreCount,
                ioCount,
                payloadBlockCount,
                payloadTypes.size(),
                invalidReason
        );
    }

    private static Set<BlockPos> microlizerSeedParts(ServerLevel level, Set<BlockPos> seeds) {
        return microlizerParts(level, seeds);
    }

    private static Set<BlockPos> microlizerParts(ServerLevel level, Set<BlockPos> positions) {
        Set<BlockPos> parts = new HashSet<>();
        for (BlockPos pos : positions) {
            if (level.getBlockState(pos).getBlock() instanceof MicrolizerPartBlock) {
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
            int microlizerBlockCount,
            int microlizerTypeCount,
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
                    scan.microlizerBlockCount(),
                    scan.microlizerTypeCount(),
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
                    scan.microlizerBlockCount(),
                    scan.microlizerTypeCount(),
                    scan.payloadBlockCount(),
                    scan.payloadTypeCount()
            );
        }
    }

    private record MachineScan(
            Set<BlockPos> partPositions,
            int microlizerBlockCount,
            int microlizerTypeCount,
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

    private record ConnectionComponent(Set<BlockPos> endpoints, List<MicrolizerConnection> connections) {
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

        private List<MicrolizerConnectionKey> requiredEdges() {
            List<MicrolizerConnectionKey> edges = new ArrayList<>();

            for (int y : List.of(min.getY(), max.getY())) {
                for (int z : List.of(min.getZ(), max.getZ())) {
                    edges.add(MicrolizerConnectionKey.of(
                            new BlockPos(min.getX(), y, z),
                            new BlockPos(max.getX(), y, z)
                    ));
                }
            }

            for (int x : List.of(min.getX(), max.getX())) {
                for (int z : List.of(min.getZ(), max.getZ())) {
                    edges.add(MicrolizerConnectionKey.of(
                            new BlockPos(x, min.getY(), z),
                            new BlockPos(x, max.getY(), z)
                    ));
                }
            }

            for (int x : List.of(min.getX(), max.getX())) {
                for (int y : List.of(min.getY(), max.getY())) {
                    edges.add(MicrolizerConnectionKey.of(
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

    private MicrolizerValidator() {
    }
}
