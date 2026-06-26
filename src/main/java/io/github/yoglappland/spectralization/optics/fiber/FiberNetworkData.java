package io.github.yoglappland.spectralization.optics.fiber;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class FiberNetworkData extends SavedData {
    private static final String DATA_NAME = "spectralization_fiber_networks";
    private static final SavedData.Factory<FiberNetworkData> FACTORY = new SavedData.Factory<>(
            FiberNetworkData::new,
            FiberNetworkData::load,
            null
    );

    private final Map<UUID, FiberConnection> connectionsById = new LinkedHashMap<>();

    public static Optional<FiberNetworkData> maybeGet(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return Optional.of(serverLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME));
        }

        return Optional.empty();
    }

    public static List<FiberConnection> connections(Level level) {
        return maybeGet(level)
                .map(FiberNetworkData::connections)
                .orElse(List.of());
    }

    public static Optional<FiberConnection> addConnection(ServerLevel level, FiberRoute route) {
        return addConnection(level, route, FiberMaterialProfile.DEFAULT_NORMAL);
    }

    public static Optional<FiberConnection> addConnection(
            ServerLevel level,
            FiberRoute route,
            FiberMaterialProfile profile
    ) {
        Optional<FiberNetworkData> maybeData = maybeGet(level);

        if (maybeData.isEmpty()) {
            return Optional.empty();
        }

        FiberNetworkData data = maybeData.get();
        Set<BlockPos> dirtyEndpoints = new HashSet<>();
        FiberConnection connection = FiberConnection.fromRoute(UUID.randomUUID(), route, profile, level.getGameTime());
        data.connectionsById.put(connection.id(), connection);
        dirtyEndpoints.add(connection.endpointA());
        dirtyEndpoints.add(connection.endpointB());
        data.setDirty();
        FiberNetworkIndex.markDataChanged(level);
        markEndpointsDirty(level, dirtyEndpoints);
        int parallelCount = data.connectionCountBetween(connection.endpointA(), connection.endpointB());
        Spectralization.LOGGER.info(
                "Fiber connection added in {}: {} -> {}, {} node(s), length {}, parallel {}, profile {}/{} core {} cap {}",
                level.dimension().location(),
                formatPos(connection.endpointA()),
                formatPos(connection.endpointB()),
                connection.nodes().size(),
                String.format(java.util.Locale.ROOT, "%.3f", connection.totalLength()),
                parallelCount,
                connection.profile().material().id(),
                connection.profile().singleMode() ? "single" : "normal",
                connection.profile().coreDiameterText(),
                connection.profile().maxPowerText()
        );
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.FIBER, "connection_added")
                .pos("endpoint_a", connection.endpointA())
                .pos("endpoint_b", connection.endpointB())
                .field("nodes", connection.nodes().size())
                .field("length", connection.totalLength())
                .field("parallel", parallelCount)
                .field("material", connection.profile().material().id())
                .field("single_mode", connection.profile().singleMode())
                .field("core_diameter", connection.profile().coreDiameter())
                .field("max_power", connection.profile().maxPower())
                .field("optical_dirty", true)
                .write();
        return Optional.of(connection);
    }

    public static Optional<FiberConnection> addOrReplaceConnection(ServerLevel level, FiberRoute route) {
        return addConnection(level, route);
    }

    public static Optional<FiberConnection> removeOneConnectionBetween(ServerLevel level, BlockPos left, BlockPos right) {
        Optional<FiberNetworkData> maybeData = maybeGet(level);

        if (maybeData.isEmpty() || left.equals(right)) {
            return Optional.empty();
        }

        FiberNetworkData data = maybeData.get();
        UUID removedId = null;
        FiberConnection removedConnection = null;

        for (Map.Entry<UUID, FiberConnection> entry : data.connectionsById.entrySet()) {
            if (entry.getValue().connects(left, right)) {
                removedId = entry.getKey();
                removedConnection = entry.getValue();
            }
        }

        if (removedId == null || removedConnection == null) {
            return Optional.empty();
        }

        data.connectionsById.remove(removedId);
        data.setDirty();
        FiberNetworkIndex.markDataChanged(level);
        markEndpointsDirty(level, Set.of(removedConnection.endpointA(), removedConnection.endpointB()));
        FiberOverlayPublisher.publishNow(level);
        Spectralization.LOGGER.info(
                "Fiber connection removed in {} by shears: {} -> {}, remaining parallel {}",
                level.dimension().location(),
                formatPos(removedConnection.endpointA()),
                formatPos(removedConnection.endpointB()),
                data.connectionCountBetween(removedConnection.endpointA(), removedConnection.endpointB())
        );
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.FIBER, "connection_removed")
                .field("reason", "shears")
                .pos("endpoint_a", removedConnection.endpointA())
                .pos("endpoint_b", removedConnection.endpointB())
                .field("remaining_parallel", data.connectionCountBetween(removedConnection.endpointA(), removedConnection.endpointB()))
                .field("optical_dirty", true)
                .write();
        return Optional.of(removedConnection);
    }

    public static int connectionCountBetween(Level level, BlockPos left, BlockPos right) {
        return maybeGet(level)
                .map(data -> data.connectionCountBetween(left, right))
                .orElse(0);
    }

    public static boolean removeConnectionsTouching(Level level, BlockPos pos) {
        Optional<FiberNetworkData> maybeData = maybeGet(level);

        if (maybeData.isEmpty()) {
            return false;
        }

        FiberNetworkData data = maybeData.get();
        Set<BlockPos> dirtyEndpoints = new HashSet<>();
        int previousSize = data.connectionsById.size();
        boolean changed = data.connectionsById.entrySet().removeIf(entry -> {
            FiberConnection connection = entry.getValue();

            if (!connection.touches(pos)) {
                return false;
            }

            dirtyEndpoints.add(connection.endpointA());
            dirtyEndpoints.add(connection.endpointB());
            return true;
        });

        if (changed) {
            data.setDirty();
            FiberNetworkIndex.markDataChanged(level);

            if (level instanceof ServerLevel serverLevel) {
                markEndpointsDirty(serverLevel, dirtyEndpoints);
                FiberOverlayPublisher.publishNow(serverLevel);
                Spectralization.LOGGER.info(
                        "Fiber connection(s) removed in {} touching {}",
                        serverLevel.dimension().location(),
                        formatPos(pos)
                );
                SpectralDiagnostics.event(serverLevel, SpectralDiagnostics.Subsystem.FIBER, "connections_removed")
                        .field("reason", "touching_block")
                        .pos("pos", pos)
                        .field("removed", previousSize - data.connectionsById.size())
                        .field("dirty_endpoints", dirtyEndpoints.size())
                        .field("optical_dirty", true)
                        .write();
            }
        }

        return changed;
    }

    public static boolean removeConnectionsTouchingAny(ServerLevel level, Set<BlockPos> positions, String reason) {
        Optional<FiberNetworkData> maybeData = maybeGet(level);

        if (maybeData.isEmpty() || positions.isEmpty()) {
            return false;
        }

        FiberNetworkData data = maybeData.get();
        Set<BlockPos> immutablePositions = positions.stream().map(BlockPos::immutable).collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> dirtyEndpoints = new HashSet<>();
        int previousSize = data.connectionsById.size();
        boolean changed = data.connectionsById.entrySet().removeIf(entry -> {
            FiberConnection connection = entry.getValue();

            for (BlockPos position : immutablePositions) {
                if (connection.touches(position)) {
                    dirtyEndpoints.add(connection.endpointA());
                    dirtyEndpoints.add(connection.endpointB());
                    return true;
                }
            }

            return false;
        });

        if (changed) {
            data.setDirty();
            FiberNetworkIndex.markDataChanged(level);
            markEndpointsDirty(level, dirtyEndpoints);
            FiberOverlayPublisher.publishNow(level);
            Spectralization.LOGGER.info(
                    "Fiber connection(s) removed in {} by {} at {}: {} removed",
                    level.dimension().location(),
                    reason,
                    immutablePositions.stream().map(FiberNetworkData::formatPos).sorted().toList(),
                    previousSize - data.connectionsById.size()
            );
            SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.FIBER, "connections_removed")
                    .field("reason", reason)
                    .field("positions", immutablePositions.size())
                    .field("removed", previousSize - data.connectionsById.size())
                    .field("dirty_endpoints", dirtyEndpoints.size())
                    .field("optical_dirty", true)
                    .write();
        }

        return changed;
    }

    public static boolean removeConnectionsUsingAnySegment(ServerLevel level, Set<FiberSegmentKey> segments, String reason) {
        Optional<FiberNetworkData> maybeData = maybeGet(level);

        if (maybeData.isEmpty() || segments.isEmpty()) {
            return false;
        }

        FiberNetworkData data = maybeData.get();
        Set<FiberSegmentKey> immutableSegments = Set.copyOf(segments);
        Set<BlockPos> dirtyEndpoints = new HashSet<>();
        int previousSize = data.connectionsById.size();
        boolean changed = data.connectionsById.entrySet().removeIf(entry -> {
            FiberConnection connection = entry.getValue();

            for (FiberSegment segment : connection.route().segments()) {
                if (immutableSegments.contains(FiberSegmentKey.of(segment.from(), segment.to()))) {
                    dirtyEndpoints.add(connection.endpointA());
                    dirtyEndpoints.add(connection.endpointB());
                    return true;
                }
            }

            return false;
        });

        if (changed) {
            data.setDirty();
            FiberNetworkIndex.markDataChanged(level);
            markEndpointsDirty(level, dirtyEndpoints);
            FiberOverlayPublisher.publishNow(level);
            Spectralization.LOGGER.info(
                    "Fiber connection(s) removed in {} by {} on {} segment(s): {} removed",
                    level.dimension().location(),
                    reason,
                    immutableSegments.size(),
                    previousSize - data.connectionsById.size()
            );
            SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.FIBER, "connections_removed")
                    .field("reason", reason)
                    .field("segments", immutableSegments.size())
                    .field("removed", previousSize - data.connectionsById.size())
                    .field("dirty_endpoints", dirtyEndpoints.size())
                    .field("optical_dirty", true)
                    .write();
        }

        return changed;
    }

    public static boolean removeBlockedConnections(ServerLevel level) {
        Optional<FiberNetworkData> maybeData = maybeGet(level);

        if (maybeData.isEmpty()) {
            return false;
        }

        FiberNetworkData data = maybeData.get();
        Set<BlockPos> dirtyEndpoints = new HashSet<>();
        int previousSize = data.connectionsById.size();
        boolean changed = data.connectionsById.entrySet().removeIf(entry -> {
            FiberConnection connection = entry.getValue();

            if (!isBlocked(level, connection)) {
                return false;
            }

            dirtyEndpoints.add(connection.endpointA());
            dirtyEndpoints.add(connection.endpointB());
            return true;
        });

        if (changed) {
            data.setDirty();
            FiberNetworkIndex.markDataChanged(level);
            markEndpointsDirty(level, dirtyEndpoints);
            FiberOverlayPublisher.publishNow(level);
            Spectralization.LOGGER.info(
                    "Fiber blocked connection(s) removed in {}: {} removed",
                    level.dimension().location(),
                    previousSize - data.connectionsById.size()
            );
            SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.FIBER, "connections_removed")
                    .field("reason", "blocked")
                    .field("removed", previousSize - data.connectionsById.size())
                    .field("dirty_endpoints", dirtyEndpoints.size())
                    .field("optical_dirty", true)
                    .write();
        }

        return changed;
    }

    private static boolean isBlocked(ServerLevel level, FiberConnection connection) {
        List<BlockPos> nodes = connection.nodes();

        for (int index = 0; index < nodes.size() - 1; index++) {
            if (!FiberLineOfSight.clearBetween(level, nodes.get(index), nodes.get(index + 1))) {
                return true;
            }
        }

        return false;
    }

    private static void markEndpointsDirty(ServerLevel level, Set<BlockPos> endpoints) {
        if (endpoints.isEmpty()) {
            return;
        }

        OpticalNetworkIndex.markDirty(level);

        for (BlockPos endpoint : endpoints) {
            OpticalTraceCache.markChanged(level, endpoint, OpticalDirtyKind.TOPOLOGY);
            OpticalTraceCache.requestIntrinsicSourcesNear(level, endpoint);
        }
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public List<FiberConnection> connections() {
        return List.copyOf(connectionsById.values());
    }

    private int connectionCountBetween(BlockPos left, BlockPos right) {
        int count = 0;

        for (FiberConnection connection : connectionsById.values()) {
            if (connection.connects(left, right)) {
                count++;
            }
        }

        return count;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag connections = new ListTag();

        for (FiberConnection connection : connectionsById.values()) {
            CompoundTag connectionTag = new CompoundTag();
            connectionTag.putString("id", connection.id().toString());
            connectionTag.putLong("endpoint_a", connection.endpointA().asLong());
            connectionTag.putLong("endpoint_b", connection.endpointB().asLong());
            connectionTag.putDouble("length", connection.totalLength());
            connectionTag.putLong("created", connection.createdGameTime());
            connectionTag.put(FiberMaterialProfile.TAG_KEY, connection.profile().write());

            ListTag nodes = new ListTag();
            for (BlockPos node : connection.nodes()) {
                CompoundTag nodeTag = new CompoundTag();
                nodeTag.putLong("pos", node.asLong());
                nodes.add(nodeTag);
            }

            connectionTag.put("nodes", nodes);
            connections.add(connectionTag);
        }

        tag.put("connections", connections);
        return tag;
    }

    private static FiberNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        FiberNetworkData data = new FiberNetworkData();
        ListTag connections = tag.getList("connections", Tag.TAG_COMPOUND);

        for (int index = 0; index < connections.size(); index++) {
            FiberConnection connection = readConnection(connections.getCompound(index));

            if (connection != null) {
                data.connectionsById.put(connection.id(), connection);
            }
        }

        return data;
    }

    private static FiberConnection readConnection(CompoundTag tag) {
        UUID id;

        try {
            id = UUID.fromString(tag.getString("id"));
        } catch (IllegalArgumentException exception) {
            return null;
        }

        ListTag nodesTag = tag.getList("nodes", Tag.TAG_COMPOUND);
        List<BlockPos> nodes = new ArrayList<>();

        for (int index = 0; index < nodesTag.size(); index++) {
            nodes.add(BlockPos.of(nodesTag.getCompound(index).getLong("pos")));
        }

        if (nodes.size() < 2) {
            return null;
        }

        try {
            FiberMaterialProfile profile = tag.contains(FiberMaterialProfile.TAG_KEY)
                    ? FiberMaterialProfile.read(tag.getCompound(FiberMaterialProfile.TAG_KEY))
                    .orElse(FiberMaterialProfile.DEFAULT_NORMAL)
                    : FiberMaterialProfile.DEFAULT_NORMAL;
            return new FiberConnection(
                    id,
                    BlockPos.of(tag.getLong("endpoint_a")),
                    BlockPos.of(tag.getLong("endpoint_b")),
                    nodes,
                    tag.getDouble("length"),
                    profile,
                    tag.getLong("created")
            );
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
