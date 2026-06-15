package io.github.yoglappland.spectralization.compact;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.CompactMachinePartBlock;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

public final class CompactMachineNetworkData extends SavedData {
    private static final String DATA_NAME = "spectralization_compact_machine_networks";
    private static final int MAX_CONNECTION_LENGTH = 64;
    private static final SavedData.Factory<CompactMachineNetworkData> FACTORY = new SavedData.Factory<>(
            CompactMachineNetworkData::new,
            CompactMachineNetworkData::load,
            null
    );
    private static final Map<ResourceKey<Level>, List<PendingRefresh>> PENDING_REFRESHES = new LinkedHashMap<>();

    private final Map<CompactMachineConnectionKey, CompactMachineConnection> connectionsByKey = new LinkedHashMap<>();

    public static Optional<CompactMachineNetworkData> maybeGet(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return Optional.of(serverLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME));
        }

        return Optional.empty();
    }

    public static List<CompactMachineConnection> connections(Level level) {
        return maybeGet(level)
                .map(CompactMachineNetworkData::connections)
                .orElse(List.of());
    }

    public static CompactMachineFrameInfo frameInfoAt(ServerLevel level, BlockPos corePos) {
        return maybeGet(level)
                .map(data -> CompactMachineValidator.inspectAt(level, data.connections(), corePos))
                .orElseGet(CompactMachineFrameInfo::missing);
    }

    public static boolean isRelevantPlacement(ServerLevel level, BlockPos pos, BlockState placedState) {
        if (placedState.getBlock() instanceof CompactMachinePartBlock) {
            return true;
        }

        Optional<CompactMachineNetworkData> maybeData = maybeGet(level);
        return maybeData.isPresent()
                && (maybeData.get().hasConnectionCrossing(pos)
                || CompactMachineValidator.hasCandidateShellContaining(maybeData.get().connections(), pos));
    }

    public static boolean isRelevantRemoval(ServerLevel level, BlockPos pos, BlockState removedState) {
        if (removedState.getBlock() instanceof CompactMachinePartBlock) {
            return true;
        }

        Optional<CompactMachineNetworkData> maybeData = maybeGet(level);
        if (maybeData.isPresent() && maybeData.get().hasConnectionCrossing(pos)) {
            return true;
        }

        if (maybeData.isPresent() && CompactMachineValidator.hasCandidateShellContaining(maybeData.get().connections(), pos)) {
            return true;
        }

        return hasEndpointAcrossPotentialGap(level, pos);
    }

    public static void refreshNear(ServerLevel level, BlockPos changedPos, String reason) {
        Optional<CompactMachineNetworkData> maybeData = maybeGet(level);
        if (maybeData.isEmpty()) {
            return;
        }

        CompactMachineNetworkData data = maybeData.get();
        BlockPos immutableChangedPos = changedPos.immutable();
        int before = data.connectionsByKey.size();
        Set<BlockPos> validationSeeds = new HashSet<>();
        validationSeeds.add(immutableChangedPos);

        ConnectionMutation removed = data.removeInvalidConnectionsNear(level, immutableChangedPos, reason);
        boolean changed = removed.changed();
        validationSeeds.addAll(removed.affectedPositions());

        if (isConnectionEndpoint(level.getBlockState(immutableChangedPos))) {
            ConnectionMutation addedFromEndpoint = data.tryConnectFrom(level, immutableChangedPos);
            changed |= addedFromEndpoint.changed();
            validationSeeds.addAll(addedFromEndpoint.affectedPositions());
        }

        if (isPassageState(level.getBlockState(immutableChangedPos))) {
            ConnectionMutation addedAcrossGap = data.tryConnectAcrossChangedPosition(level, immutableChangedPos);
            changed |= addedAcrossGap.changed();
            validationSeeds.addAll(addedAcrossGap.affectedPositions());
        }

        boolean includeContainingFrames = level.getBlockState(immutableChangedPos).getBlock() instanceof CompactMachinePartBlock
                || reason.startsWith("compact part")
                || CompactMachineValidator.hasCandidateShellContaining(data.connections(), immutableChangedPos);
        CompactMachineValidationResult validation = CompactMachineValidator.refreshAffected(
                level,
                data.connections(),
                validationSeeds,
                immutableChangedPos,
                includeContainingFrames
        );
        changed |= validation.changedErrorStates();

        if (changed) {
            data.setDirty();
            CompactMachineOverlayPublisher.publishNow(level);
        }

        if (changed || validation.validFrames() > 0 || validation.invalidFrames() > 0) {
            Spectralization.LOGGER.info(
                    "Compact machine network refreshed in {} at {} by {}: connections {} -> {}, seed(s) {}, valid frame(s) {}, invalid frame(s) {}, error states changed {}",
                    level.dimension().location(),
                    formatPos(immutableChangedPos),
                    reason,
                    before,
                    data.connectionsByKey.size(),
                    validationSeeds.size(),
                    validation.validFrames(),
                    validation.invalidFrames(),
                    validation.changedErrorStates()
            );
            SpectralDiagnostics.event(level, "compact_machine", "network_refresh")
                    .pos("changed_pos", immutableChangedPos)
                    .field("reason", reason)
                    .field("connections_before", before)
                    .field("connections_after", data.connectionsByKey.size())
                    .field("validation_seeds", validationSeeds.size())
                    .field("valid_frames", validation.validFrames())
                    .field("invalid_frames", validation.invalidFrames())
                    .field("error_states_changed", validation.changedErrorStates())
                    .write();
        }
    }

    public static void scheduleRefresh(ServerLevel level, BlockPos changedPos, String reason) {
        PENDING_REFRESHES.computeIfAbsent(level.dimension(), ignored -> new ArrayList<>())
                .add(new PendingRefresh(changedPos.immutable(), reason));
    }

    public static void processPendingRefreshes(MinecraftServer server) {
        if (PENDING_REFRESHES.isEmpty()) {
            return;
        }

        Map<ResourceKey<Level>, List<PendingRefresh>> pendingByLevel = new LinkedHashMap<>(PENDING_REFRESHES);
        PENDING_REFRESHES.clear();

        for (Map.Entry<ResourceKey<Level>, List<PendingRefresh>> entry : pendingByLevel.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }

            Map<BlockPos, String> reasonsByPos = new LinkedHashMap<>();
            for (PendingRefresh refresh : entry.getValue()) {
                reasonsByPos.putIfAbsent(refresh.pos(), refresh.reason());
            }

            for (Map.Entry<BlockPos, String> refresh : reasonsByPos.entrySet()) {
                refreshNear(level, refresh.getKey(), refresh.getValue() + " (deferred)");
            }
        }
    }

    public List<CompactMachineConnection> connections() {
        return List.copyOf(connectionsByKey.values());
    }

    private ConnectionMutation tryConnectAcrossChangedPosition(ServerLevel level, BlockPos changedPos) {
        Set<BlockPos> affectedPositions = new HashSet<>();

        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            BlockPos positiveEndpoint = nearestEndpoint(level, changedPos, positive);
            BlockPos negativeEndpoint = nearestEndpoint(level, changedPos, negative);

            if (positiveEndpoint != null && negativeEndpoint != null) {
                CompactMachineConnection connection = tryAddConnection(level, negativeEndpoint, positiveEndpoint, positive);
                if (connection != null) {
                    affectedPositions.add(connection.from());
                    affectedPositions.add(connection.to());
                }
            }
        }

        return new ConnectionMutation(!affectedPositions.isEmpty(), affectedPositions);
    }

    private ConnectionMutation tryConnectFrom(ServerLevel level, BlockPos endpoint) {
        Set<BlockPos> affectedPositions = new HashSet<>();

        for (Direction.Axis axis : Direction.Axis.values()) {
            if (hasAxisConnection(endpoint, axis)) {
                continue;
            }

            Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            BlockPos positiveTarget = nearestEndpoint(level, endpoint, positive);
            BlockPos negativeTarget = nearestEndpoint(level, endpoint, negative);
            Direction direction = nearestDirection(endpoint, positiveTarget, positive, negativeTarget, negative);
            BlockPos target = direction == positive ? positiveTarget : negativeTarget;
            if (target != null) {
                CompactMachineConnection connection = tryAddConnection(level, endpoint, target, direction);
                if (connection != null) {
                    affectedPositions.add(connection.from());
                    affectedPositions.add(connection.to());
                }
            }
        }

        return new ConnectionMutation(!affectedPositions.isEmpty(), affectedPositions);
    }

    private CompactMachineConnection tryAddConnection(ServerLevel level, BlockPos from, BlockPos to, Direction direction) {
        if (from.equals(to) || direction.getAxis() != axisBetween(from, to)) {
            return null;
        }

        CompactMachineConnectionKey key = CompactMachineConnectionKey.of(from, to);
        if (connectionsByKey.containsKey(key)) {
            return null;
        }

        if (hasAxisConnection(from, direction.getAxis()) || hasAxisConnection(to, direction.getAxis())) {
            return null;
        }

        if (!isConnectionEndpoint(level.getBlockState(from))
                || !isConnectionEndpoint(level.getBlockState(to))
                || !pathClear(level, from, to)) {
            return null;
        }

        CompactMachineConnection connection = new CompactMachineConnection(from, to, direction, level.getGameTime());
        connectionsByKey.put(key, connection);
        Spectralization.LOGGER.info(
                "Compact machine anchor connection added in {}: {} -> {} ({})",
                level.dimension().location(),
                formatPos(from),
                formatPos(to),
                direction.getAxis()
        );
        SpectralDiagnostics.event(level, "compact_machine", "anchor_connection_added")
                .pos("from", from)
                .pos("to", to)
                .field("axis", direction.getAxis())
                .field("geometry_changed", true)
                .field("topology_changed", true)
                .write();
        return connection;
    }

    private ConnectionMutation removeInvalidConnectionsNear(ServerLevel level, BlockPos changedPos, String reason) {
        List<CompactMachineConnectionKey> removed = new ArrayList<>();
        Set<BlockPos> affectedPositions = new HashSet<>();

        for (Map.Entry<CompactMachineConnectionKey, CompactMachineConnection> entry : connectionsByKey.entrySet()) {
            CompactMachineConnection connection = entry.getValue();
            if (!connection.touches(changedPos) && !crosses(connection, changedPos)) {
                continue;
            }

            if (!isConnectionEndpoint(level.getBlockState(connection.from()))
                    || !isConnectionEndpoint(level.getBlockState(connection.to()))
                    || !pathClear(level, connection.from(), connection.to())) {
                removed.add(entry.getKey());
                affectedPositions.add(connection.from());
                affectedPositions.add(connection.to());
                Spectralization.LOGGER.info(
                        "Compact machine anchor connection removed in {} by {}: {} -> {}",
                        level.dimension().location(),
                        reason,
                        formatPos(connection.from()),
                        formatPos(connection.to())
                );
                SpectralDiagnostics.event(level, "compact_machine", "anchor_connection_removed")
                        .field("reason", reason)
                        .pos("from", connection.from())
                        .pos("to", connection.to())
                        .field("geometry_changed", true)
                        .field("topology_changed", true)
                        .write();
            }
        }

        for (CompactMachineConnectionKey key : removed) {
            connectionsByKey.remove(key);
        }

        return new ConnectionMutation(!removed.isEmpty(), affectedPositions);
    }

    private boolean hasAxisConnection(BlockPos endpoint, Direction.Axis axis) {
        for (CompactMachineConnection connection : connectionsByKey.values()) {
            if (connection.touches(endpoint) && connection.direction().getAxis() == axis) {
                return true;
            }
        }

        return false;
    }

    private static Direction nearestDirection(
            BlockPos origin,
            BlockPos positiveTarget,
            Direction positive,
            BlockPos negativeTarget,
            Direction negative
    ) {
        if (positiveTarget == null) {
            return negativeTarget == null ? positive : negative;
        }

        if (negativeTarget == null) {
            return positive;
        }

        int positiveDistance = distanceBetween(origin, positiveTarget);
        int negativeDistance = distanceBetween(origin, negativeTarget);
        return positiveDistance <= negativeDistance ? positive : negative;
    }

    private boolean hasConnectionCrossing(BlockPos pos) {
        for (CompactMachineConnection connection : connectionsByKey.values()) {
            if (connection.touches(pos) || crosses(connection, pos)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasEndpointAcrossPotentialGap(ServerLevel level, BlockPos pos) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            Direction negative = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
            if (nearestEndpoint(level, pos, positive) != null && nearestEndpoint(level, pos, negative) != null) {
                return true;
            }
        }

        return false;
    }

    private static BlockPos nearestEndpoint(ServerLevel level, BlockPos origin, Direction direction) {
        for (int distance = 1; distance <= MAX_CONNECTION_LENGTH; distance++) {
            BlockPos current = origin.relative(direction, distance);
            if (!level.isLoaded(current)) {
                return null;
            }

            BlockState state = level.getBlockState(current);
            if (isConnectionEndpoint(state)) {
                return current.immutable();
            }

            if (!isPassageState(state)) {
                return null;
            }
        }

        return null;
    }

    private static boolean pathClear(ServerLevel level, BlockPos from, BlockPos to) {
        Direction direction = directionBetween(from, to);
        if (direction == null) {
            return false;
        }

        int distance = distanceBetween(from, to);
        for (int step = 1; step < distance; step++) {
            BlockPos current = from.relative(direction, step);
            if (!isPassageState(level.getBlockState(current))) {
                return false;
            }
        }

        return true;
    }

    private static boolean isConnectionEndpoint(BlockState state) {
        return state.getBlock() instanceof CompactMachinePartBlock part && part.kind().isConnectionEndpoint();
    }

    private static boolean isPassageState(BlockState state) {
        return state.isAir() || state.is(SpectralBlockTags.COMPACT_MACHINE);
    }

    private static Direction.Axis axisBetween(BlockPos from, BlockPos to) {
        Direction direction = directionBetween(from, to);
        return direction == null ? null : direction.getAxis();
    }

    private static Direction directionBetween(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        if (dy == 0 && dz == 0 && dx != 0) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        }

        if (dx == 0 && dz == 0 && dy != 0) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        }

        if (dx == 0 && dy == 0 && dz != 0) {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        return null;
    }

    private static int distanceBetween(BlockPos from, BlockPos to) {
        return Math.abs(to.getX() - from.getX())
                + Math.abs(to.getY() - from.getY())
                + Math.abs(to.getZ() - from.getZ());
    }

    private static boolean crosses(CompactMachineConnection connection, BlockPos pos) {
        Direction direction = directionBetween(connection.from(), connection.to());
        if (direction == null) {
            return false;
        }

        return switch (direction.getAxis()) {
            case X -> pos.getY() == connection.from().getY()
                    && pos.getZ() == connection.from().getZ()
                    && isStrictlyBetween(pos.getX(), connection.from().getX(), connection.to().getX());
            case Y -> pos.getX() == connection.from().getX()
                    && pos.getZ() == connection.from().getZ()
                    && isStrictlyBetween(pos.getY(), connection.from().getY(), connection.to().getY());
            case Z -> pos.getX() == connection.from().getX()
                    && pos.getY() == connection.from().getY()
                    && isStrictlyBetween(pos.getZ(), connection.from().getZ(), connection.to().getZ());
        };
    }

    private static boolean isStrictlyBetween(int value, int first, int second) {
        return value > Math.min(first, second) && value < Math.max(first, second);
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag connections = new ListTag();

        for (CompactMachineConnection connection : connectionsByKey.values()) {
            CompoundTag connectionTag = new CompoundTag();
            connectionTag.putLong("from", connection.from().asLong());
            connectionTag.putLong("to", connection.to().asLong());
            connectionTag.putString("direction", connection.direction().getName());
            connectionTag.putLong("created", connection.createdGameTime());
            connections.add(connectionTag);
        }

        tag.put("connections", connections);
        return tag;
    }

    private static CompactMachineNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        CompactMachineNetworkData data = new CompactMachineNetworkData();
        ListTag connections = tag.getList("connections", Tag.TAG_COMPOUND);

        for (int index = 0; index < connections.size(); index++) {
            CompactMachineConnection connection = readConnection(connections.getCompound(index));
            if (connection != null) {
                data.connectionsByKey.put(CompactMachineConnectionKey.of(connection.from(), connection.to()), connection);
            }
        }

        return data;
    }

    private static CompactMachineConnection readConnection(CompoundTag tag) {
        Direction direction = Direction.byName(tag.getString("direction"));
        if (direction == null) {
            return null;
        }

        return new CompactMachineConnection(
                BlockPos.of(tag.getLong("from")),
                BlockPos.of(tag.getLong("to")),
                direction,
                tag.getLong("created")
        );
    }

    private record ConnectionMutation(boolean changed, Set<BlockPos> affectedPositions) {
        private ConnectionMutation {
            affectedPositions = Set.copyOf(affectedPositions);
        }
    }

    private record PendingRefresh(BlockPos pos, String reason) {
        private PendingRefresh {
            pos = pos.immutable();
        }
    }
}
