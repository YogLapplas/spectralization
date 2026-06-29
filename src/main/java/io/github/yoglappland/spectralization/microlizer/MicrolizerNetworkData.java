package io.github.yoglappland.spectralization.microlizer;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.MicrolizerPartBlock;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

public final class MicrolizerNetworkData extends SavedData {
    private static final String DATA_NAME = "spectralization_microlizer_networks";
    private static final int MAX_CONNECTION_LENGTH = 64;
    private static final SavedData.Factory<MicrolizerNetworkData> FACTORY = new SavedData.Factory<>(
            MicrolizerNetworkData::new,
            MicrolizerNetworkData::load,
            null
    );
    private static final Map<ResourceKey<Level>, List<PendingRefresh>> PENDING_REFRESHES = new LinkedHashMap<>();

    private final Map<MicrolizerConnectionKey, MicrolizerConnection> connectionsByKey = new LinkedHashMap<>();

    public static Optional<MicrolizerNetworkData> maybeGet(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return Optional.of(serverLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME));
        }

        return Optional.empty();
    }

    public static List<MicrolizerConnection> connections(Level level) {
        return maybeGet(level)
                .map(MicrolizerNetworkData::connections)
                .orElse(List.of());
    }

    public static MicrolizerFrameInfo frameInfoAt(ServerLevel level, BlockPos corePos) {
        return maybeGet(level)
                .map(data -> MicrolizerValidator.inspectAt(level, data.connections(), corePos))
                .orElseGet(MicrolizerFrameInfo::missing);
    }

    public static boolean isRelevantPlacement(ServerLevel level, BlockPos pos, BlockState placedState) {
        if (placedState.getBlock() instanceof MicrolizerPartBlock) {
            return true;
        }

        Optional<MicrolizerNetworkData> maybeData = maybeGet(level);
        return maybeData.isPresent()
                && (maybeData.get().hasConnectionCrossing(pos)
                || MicrolizerValidator.hasCandidateShellContaining(maybeData.get().connections(), pos));
    }

    public static boolean isRelevantRemoval(ServerLevel level, BlockPos pos, BlockState removedState) {
        if (removedState.getBlock() instanceof MicrolizerPartBlock) {
            return true;
        }

        Optional<MicrolizerNetworkData> maybeData = maybeGet(level);
        if (maybeData.isPresent() && maybeData.get().hasConnectionCrossing(pos)) {
            return true;
        }

        if (maybeData.isPresent() && MicrolizerValidator.hasCandidateShellContaining(maybeData.get().connections(), pos)) {
            return true;
        }

        return hasEndpointAcrossPotentialGap(level, pos);
    }

    public static void refreshNear(ServerLevel level, BlockPos changedPos, String reason) {
        refreshBatch(level, Map.of(changedPos.immutable(), reason));
    }

    private static void refreshBatch(ServerLevel level, Map<BlockPos, String> reasonsByPos) {
        Optional<MicrolizerNetworkData> maybeData = maybeGet(level);
        if (maybeData.isEmpty()) {
            return;
        }

        MicrolizerNetworkData data = maybeData.get();
        int before = data.connectionsByKey.size();
        Set<BlockPos> validationSeeds = new HashSet<>();
        Set<BlockPos> reconnectSeeds = new LinkedHashSet<>();
        Set<BlockPos> passageSeeds = new LinkedHashSet<>();
        Set<BlockPos> changedPositions = new LinkedHashSet<>(reasonsByPos.keySet());
        boolean includeContainingFrames = false;
        String reasonSummary = summarizeReasons(reasonsByPos);

        for (BlockPos changedPos : changedPositions) {
            BlockPos immutableChangedPos = changedPos.immutable();
            validationSeeds.add(immutableChangedPos);
            BlockState currentState = level.getBlockState(immutableChangedPos);

            if (isConnectionEndpoint(currentState)) {
                reconnectSeeds.add(immutableChangedPos);
            } else if (isConnectionPassageState(currentState)) {
                passageSeeds.add(immutableChangedPos);
            }

            String reason = reasonsByPos.getOrDefault(immutableChangedPos, reasonSummary);
            includeContainingFrames |= currentState.getBlock() instanceof MicrolizerPartBlock
                    || reason.startsWith("microlizer part")
                    || MicrolizerValidator.hasCandidateShellContaining(data.connections(), immutableChangedPos);
        }

        boolean changed = false;
        for (BlockPos changedPos : changedPositions) {
            String reason = reasonsByPos.getOrDefault(changedPos, reasonSummary);
            ConnectionMutation removed = data.removeInvalidConnectionsNear(level, changedPos, reason);
            changed |= removed.changed();
            validationSeeds.addAll(removed.affectedPositions());
            addCurrentEndpointSeeds(level, reconnectSeeds, removed.affectedPositions());
        }

        for (BlockPos passageSeed : passageSeeds) {
            ConnectionMutation addedAcrossGap = data.tryConnectAcrossChangedPosition(level, passageSeed);
            changed |= addedAcrossGap.changed();
            validationSeeds.addAll(addedAcrossGap.affectedPositions());
            addCurrentEndpointSeeds(level, reconnectSeeds, addedAcrossGap.affectedPositions());
        }

        ConnectionMutation addedFromEndpoints = data.tryConnectFromSeeds(level, reconnectSeeds);
        changed |= addedFromEndpoints.changed();
        validationSeeds.addAll(addedFromEndpoints.affectedPositions());

        MicrolizerValidationResult validation = MicrolizerValidator.refreshAffected(
                level,
                data.connections(),
                validationSeeds,
                changedPositions.stream().findFirst().orElse(BlockPos.ZERO),
                includeContainingFrames
        );
        changed |= validation.changedErrorStates();

        if (changed) {
            data.setDirty();
            MicrolizerOverlayPublisher.publishNow(level);
        }

        if (changed || validation.validFrames() > 0 || validation.invalidFrames() > 0) {
            Spectralization.LOGGER.info(
                    "Microlizer network refreshed in {} at {} by {}: changed position(s) {}, connections {} -> {}, seed(s) {}, valid frame(s) {}, invalid frame(s) {}, error states changed {}",
                    level.dimension().location(),
                    formatPos(changedPositions.stream().findFirst().orElse(BlockPos.ZERO)),
                    reasonSummary,
                    changedPositions.size(),
                    before,
                    data.connectionsByKey.size(),
                    validationSeeds.size(),
                    validation.validFrames(),
                    validation.invalidFrames(),
                    validation.changedErrorStates()
            );
            SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.MICROLIZER, "network_refresh")
                    .pos("changed_pos", changedPositions.stream().findFirst().orElse(BlockPos.ZERO))
                    .field("reason", reasonSummary)
                    .field("changed_positions", changedPositions.size())
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

            Map<BlockPos, String> deferredReasonsByPos = new LinkedHashMap<>();
            reasonsByPos.forEach((pos, reason) -> deferredReasonsByPos.put(pos, reason + " (deferred)"));
            refreshBatch(level, deferredReasonsByPos);
        }
    }

    public List<MicrolizerConnection> connections() {
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
                MicrolizerConnection connection = tryAddConnection(level, negativeEndpoint, positiveEndpoint, positive);
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
                MicrolizerConnection connection = tryAddConnection(level, endpoint, target, direction);
                if (connection != null) {
                    affectedPositions.add(connection.from());
                    affectedPositions.add(connection.to());
                }
            }
        }

        return new ConnectionMutation(!affectedPositions.isEmpty(), affectedPositions);
    }

    private ConnectionMutation tryConnectFromSeeds(ServerLevel level, Set<BlockPos> endpointSeeds) {
        Set<BlockPos> queued = new LinkedHashSet<>(endpointSeeds);
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> affectedPositions = new HashSet<>();
        boolean changed = false;

        while (!queued.isEmpty()) {
            BlockPos endpoint = queued.iterator().next();
            queued.remove(endpoint);
            if (!visited.add(endpoint) || !isConnectionEndpoint(level.getBlockState(endpoint))) {
                continue;
            }

            ConnectionMutation added = tryConnectFrom(level, endpoint);
            if (!added.changed()) {
                continue;
            }

            changed = true;
            affectedPositions.addAll(added.affectedPositions());
            for (BlockPos affected : added.affectedPositions()) {
                if (!visited.contains(affected) && isConnectionEndpoint(level.getBlockState(affected))) {
                    queued.add(affected);
                }
            }
        }

        return new ConnectionMutation(changed, affectedPositions);
    }

    private MicrolizerConnection tryAddConnection(ServerLevel level, BlockPos from, BlockPos to, Direction direction) {
        if (from.equals(to) || direction.getAxis() != axisBetween(from, to)) {
            return null;
        }

        MicrolizerConnectionKey key = MicrolizerConnectionKey.of(from, to);
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

        MicrolizerConnection connection = new MicrolizerConnection(from, to, direction, level.getGameTime());
        connectionsByKey.put(key, connection);
        Spectralization.LOGGER.info(
                "Microlizer anchor connection added in {}: {} -> {} ({})",
                level.dimension().location(),
                formatPos(from),
                formatPos(to),
                direction.getAxis()
        );
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.MICROLIZER, "anchor_connection_added")
                .pos("from", from)
                .pos("to", to)
                .field("axis", direction.getAxis())
                .field("geometry_changed", true)
                .field("topology_changed", true)
                .write();
        return connection;
    }

    private ConnectionMutation removeInvalidConnectionsNear(ServerLevel level, BlockPos changedPos, String reason) {
        List<MicrolizerConnectionKey> removed = new ArrayList<>();
        Set<BlockPos> affectedPositions = new HashSet<>();

        for (Map.Entry<MicrolizerConnectionKey, MicrolizerConnection> entry : connectionsByKey.entrySet()) {
            MicrolizerConnection connection = entry.getValue();
            if (!connection.touches(changedPos) && !crosses(connection, changedPos)) {
                continue;
            }

            boolean endpointInsertedInPath = crosses(connection, changedPos)
                    && isConnectionEndpoint(level.getBlockState(changedPos));
            if (endpointInsertedInPath
                    || !isConnectionEndpoint(level.getBlockState(connection.from()))
                    || !isConnectionEndpoint(level.getBlockState(connection.to()))
                    || !pathClear(level, connection.from(), connection.to())) {
                removed.add(entry.getKey());
                affectedPositions.add(connection.from());
                affectedPositions.add(connection.to());
                Spectralization.LOGGER.info(
                        "Microlizer anchor connection removed in {} by {}: {} -> {}",
                        level.dimension().location(),
                        reason,
                        formatPos(connection.from()),
                        formatPos(connection.to())
                );
                SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.MICROLIZER, "anchor_connection_removed")
                        .field("reason", reason)
                        .pos("from", connection.from())
                        .pos("to", connection.to())
                        .field("geometry_changed", true)
                        .field("topology_changed", true)
                        .write();
            }
        }

        for (MicrolizerConnectionKey key : removed) {
            connectionsByKey.remove(key);
        }

        return new ConnectionMutation(!removed.isEmpty(), affectedPositions);
    }

    private boolean hasAxisConnection(BlockPos endpoint, Direction.Axis axis) {
        for (MicrolizerConnection connection : connectionsByKey.values()) {
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
        for (MicrolizerConnection connection : connectionsByKey.values()) {
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
            BlockState state = level.getBlockState(current);
            if (isConnectionEndpoint(state) || !isPassageState(state)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isConnectionEndpoint(BlockState state) {
        return state.getBlock() instanceof MicrolizerPartBlock part && part.kind().isConnectionEndpoint();
    }

    private static boolean isPassageState(BlockState state) {
        return state.isAir() || state.is(SpectralBlockTags.MICROLIZER);
    }

    private static boolean isConnectionPassageState(BlockState state) {
        return !isConnectionEndpoint(state) && isPassageState(state);
    }

    private static void addCurrentEndpointSeeds(ServerLevel level, Set<BlockPos> seeds, Iterable<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (isConnectionEndpoint(level.getBlockState(pos))) {
                seeds.add(pos.immutable());
            }
        }
    }

    private static String summarizeReasons(Map<BlockPos, String> reasonsByPos) {
        String firstReason = reasonsByPos.values().stream()
                .findFirst()
                .orElse("unknown");
        if (reasonsByPos.size() <= 1) {
            return firstReason;
        }

        return firstReason + " +" + (reasonsByPos.size() - 1) + " more";
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

    private static boolean crosses(MicrolizerConnection connection, BlockPos pos) {
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

        for (MicrolizerConnection connection : connectionsByKey.values()) {
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

    private static MicrolizerNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        MicrolizerNetworkData data = new MicrolizerNetworkData();
        ListTag connections = tag.getList("connections", Tag.TAG_COMPOUND);

        for (int index = 0; index < connections.size(); index++) {
            MicrolizerConnection connection = readConnection(connections.getCompound(index));
            if (connection != null) {
                data.connectionsByKey.put(MicrolizerConnectionKey.of(connection.from(), connection.to()), connection);
            }
        }

        return data;
    }

    private static MicrolizerConnection readConnection(CompoundTag tag) {
        Direction direction = Direction.byName(tag.getString("direction"));
        if (direction == null) {
            return null;
        }

        return new MicrolizerConnection(
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
