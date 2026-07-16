package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.network.SpotOverlayPayload;
import io.github.yoglappland.spectralization.network.SpotUpdatePayload;
import io.github.yoglappland.spectralization.optics.compiler.OpticalCompilerDebugLogger;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionAllocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class OpticalSpotTracker {
    private static final int SEND_INTERVAL_TICKS = 40;
    private static final int REFRESH_INTERVAL_TICKS = 160;
    private static final int LOG_INTERVAL_TICKS = 160;
    private static final int PLAYER_SNAPSHOT_REFRESH_TICKS = 1200;
    private static final double SEND_RADIUS = 64.0;
    private static final double SEND_RADIUS_SQUARED = SEND_RADIUS * SEND_RADIUS;
    private static final double MIN_SPOT_RADIUS = 0.08;
    private static final double MIN_VISIBLE_SPOT_POWER = 0.125;
    private static final double SPOT_POWER_PER_ALPHA_LEVEL = 8.0;
    private static final int TEMPORARY_FIXED_SPOT_ALPHA_LEVEL =
            Integer.getInteger("spectralization.fixedSpotAlphaLevel", 0);
    private static final double DEFAULT_OPTICAL_DIFFUSE_YIELD = 0.85;
    private static final int DEFAULT_SPOT_RGB = 0xFF4630;
    private static final Comparator<SpotRecord> SPOT_COMPARATOR = Comparator
            .comparingInt((SpotRecord spot) -> spot.pos().getX())
            .thenComparingInt(spot -> spot.pos().getY())
            .thenComparingInt(spot -> spot.pos().getZ())
            .thenComparingInt(spot -> projectionSendPriority(spot.projectionMode()))
            .thenComparingInt(spot -> spot.face().ordinal())
            .thenComparingInt(SpotRecord::clipMinU)
            .thenComparingInt(SpotRecord::clipMinV)
            .thenComparingInt(SpotRecord::clipMaxU)
            .thenComparingInt(SpotRecord::clipMaxV)
            .thenComparingInt(SpotRecord::textureMinU)
            .thenComparingInt(SpotRecord::textureMinV)
            .thenComparingInt(SpotRecord::textureMaxU)
            .thenComparingInt(SpotRecord::textureMaxV)
            .thenComparingInt(SpotRecord::quadX0)
            .thenComparingInt(SpotRecord::quadY0)
            .thenComparingInt(SpotRecord::quadZ0)
            .thenComparingInt(SpotRecord::quadTextureU0)
            .thenComparingInt(SpotRecord::quadTextureV0)
            .thenComparingInt(SpotRecord::quadX1)
            .thenComparingInt(SpotRecord::quadY1)
            .thenComparingInt(SpotRecord::quadZ1)
            .thenComparingInt(SpotRecord::quadTextureU1)
            .thenComparingInt(SpotRecord::quadTextureV1)
            .thenComparingInt(SpotRecord::quadX2)
            .thenComparingInt(SpotRecord::quadY2)
            .thenComparingInt(SpotRecord::quadZ2)
            .thenComparingInt(SpotRecord::quadTextureU2)
            .thenComparingInt(SpotRecord::quadTextureV2)
            .thenComparingInt(SpotRecord::quadX3)
            .thenComparingInt(SpotRecord::quadY3)
            .thenComparingInt(SpotRecord::quadZ3)
            .thenComparingInt(SpotRecord::quadTextureU3)
            .thenComparingInt(SpotRecord::quadTextureV3)
            .thenComparingInt(SpotRecord::debugMarker);

    private static final Map<Level, Map<Integer, SpotOwnerSnapshot>> SPOTS_BY_OWNER = new WeakHashMap<>();
    private static final Map<Level, Map<UUID, Map<Integer, SentSpotSnapshot>>> SENT_SNAPSHOTS_BY_PLAYER = new WeakHashMap<>();
    private static final Map<Level, Map<SpotKey, ActiveSpot>> LEGACY_ACTIVE_SPOTS = new WeakHashMap<>();
    private static final Map<Level, Long> LAST_REFRESH = new WeakHashMap<>();
    private static final Map<Level, Long> LAST_LOG = new WeakHashMap<>();

    public static void markMaterialSpot(
            Level level,
            BlockPos pos,
            Direction face,
            BeamPacket beam,
            BlockState state
    ) {
        if (!isFullBlockSurface(level, pos, state)) {
            return;
        }

        OpticalMaterialProfile profile = OpticalMaterialProfiles.profileFor(level, pos, state);
        SpotPowerAccumulator spotPower = new SpotPowerAccumulator();

        for (PlaneWaveComponent component : beam.components()) {
            if (component.frequency().region() != SpectralRegion.VISIBLE) {
                continue;
            }

            OpticalMaterialResponse response = profile.responseAt(component.frequency());
            double diffuseYield = spotYieldFor(component, response);

            spotPower.accept(component, component.power() * diffuseYield);
        }

        mark(level, pos, face, beam, spotPower);
    }

    public static void markMaterialExitSpot(
            Level level,
            BlockPos pos,
            Direction face,
            BeamPacket outputBeam,
            BlockState state
    ) {
        markMaterialSpot(level, pos, face, outputBeam, state);
    }

    public static SpotRecord createCompiledSurfaceSpot(
            Level level,
            BlockPos pos,
            Direction face,
            BlockState state,
            BeamPacket profileTemplate,
            double beamPower,
            double coherentBeamPower
    ) {
        OpticalMaterialProfile profile = OpticalMaterialProfiles.profileFor(level, pos, state);
        return createCompiledSurfaceSpot(
                profile,
                pos,
                face,
                profileTemplate,
                beamPower,
                coherentBeamPower
        );
    }

    public static SpotRecord createCompiledSurfaceSpot(
            OpticalMaterialProfile profile,
            BlockPos pos,
            Direction face,
            BeamPacket profileTemplate,
            double beamPower,
            double coherentBeamPower
    ) {
        java.util.Objects.requireNonNull(profile, "profile");
        SpotPowerAccumulator spotPower = new SpotPowerAccumulator();
        double totalTemplatePower = Math.max(profileTemplate.totalPower(), 1.0E-9);

        for (PlaneWaveComponent component : profileTemplate.components()) {
            if (component.frequency().region() != SpectralRegion.VISIBLE || component.power() <= 0.0) {
                continue;
            }

            OpticalMaterialResponse response = profile.responseAt(component.frequency());
            double templateFraction = component.power() / totalTemplatePower;
            double componentBeamPower = beamPower * templateFraction;
            double componentCoherentPower = Math.min(componentBeamPower, coherentBeamPower * templateFraction);
            double componentStrayPower = Math.max(0.0, componentBeamPower - componentCoherentPower);

            spotPower.accept(component.withCoherence(CoherenceKind.COHERENT), componentCoherentPower * response.absorption());
            spotPower.accept(component.withCoherence(CoherenceKind.INCOHERENT), componentStrayPower * straySpotYieldFor(response));
        }

        return createSpot(pos, face, profileTemplate, spotPower);
    }

    public static SpotRecord createCompiledSpot(
            BlockPos pos,
            Direction face,
            BeamPacket profileTemplate,
            double spotPower,
            double coherentSpotPower
    ) {
        SpotPowerAccumulator accumulator = new SpotPowerAccumulator();
        double totalTemplatePower = Math.max(profileTemplate.totalPower(), 1.0E-9);

        for (PlaneWaveComponent component : profileTemplate.components()) {
            if (component.frequency().region() != SpectralRegion.VISIBLE || component.power() <= 0.0) {
                continue;
            }

            double templateFraction = component.power() / totalTemplatePower;
            double componentSpotPower = spotPower * templateFraction;
            double componentCoherentSpotPower = Math.min(componentSpotPower, coherentSpotPower * templateFraction);

            accumulator.accept(component.withCoherence(CoherenceKind.COHERENT), componentCoherentSpotPower);
            accumulator.accept(component.withCoherence(CoherenceKind.INCOHERENT), Math.max(0.0, componentSpotPower - componentCoherentSpotPower));
        }

        return createSpot(pos, face, profileTemplate, accumulator);
    }

    public static void markAbsorbedSpot(
            Level level,
            BlockPos pos,
            Direction face,
            BlockState state,
            BeamPacket beam,
            double absorbedPower
    ) {
        if (!isFullBlockSurface(level, pos, state)) {
            return;
        }

        if (absorbedPower <= 0.0) {
            return;
        }

        SpotPowerAccumulator spotPower = new SpotPowerAccumulator();
        double visiblePower = visiblePower(beam);

        if (visiblePower <= 0.0) {
            return;
        }

        for (PlaneWaveComponent component : beam.components()) {
            if (component.frequency().region() != SpectralRegion.VISIBLE) {
                continue;
            }

            double componentSpotPower = absorbedPower * DEFAULT_OPTICAL_DIFFUSE_YIELD * component.power() / visiblePower;
            spotPower.accept(component, componentSpotPower);
        }

        mark(level, pos, face, beam, spotPower);
    }

    public static CompiledPublishResult publishCompiledSpots(
            ServerLevel level,
            int ownerId,
            List<SpotRecord> spots,
            List<SpotProjectionAllocation> allocations
    ) {
        long startedNanos = System.nanoTime();
        if (!SpectralizationConfig.surfaceSpotsVisible()) {
            clear(level);
            return CompiledPublishResult.changed(
                    Math.max(0L, System.nanoTime() - startedNanos), 0L, 0L, 0L, 0L
            );
        }

        Map<Integer, SpotOwnerSnapshot> ownerSnapshots = SPOTS_BY_OWNER.computeIfAbsent(level, ignored -> new HashMap<>());
        long gameTime = level.getGameTime();
        List<SpotProjectionAllocation> visibleAllocations = List.copyOf(allocations);
        SpotOwnerSnapshot previous = ownerSnapshots.get(ownerId);
        boolean sameCompiledInput = previous != null
                && previous.sourceSpots().equals(spots)
                && previous.allocations().equals(visibleAllocations);
        long inputCompareNanos = Math.max(0L, System.nanoTime() - startedNanos);
        if (sameCompiledInput) {
            return CompiledPublishResult.reused(inputCompareNanos, 0L, 0L, 0L);
        }

        long snapshotStartNanos = System.nanoTime();
        List<SpotRecord> sourceSpots = List.copyOf(spots);
        List<SpotRecord> visibleSpots = visibleSnapshot(sourceSpots);
        long snapshotBuildNanos = Math.max(0L, System.nanoTime() - snapshotStartNanos);

        if (visibleSpots.isEmpty()) {
            SpotOwnerSnapshot removed = ownerSnapshots.remove(ownerId);
            long sendStartNanos = System.nanoTime();
            if (removed != null) {
                sendClearSnapshot(level, ownerId);
            }
            return new CompiledPublishResult(
                    removed == null,
                    inputCompareNanos,
                    snapshotBuildNanos,
                    0L,
                    0L,
                    Math.max(0L, System.nanoTime() - sendStartNanos)
            );
        }

        long signatureStartNanos = System.nanoTime();
        long signature = 31L * spotSnapshotSignature(visibleSpots) + allocationSnapshotSignature(visibleAllocations);
        long signatureNanos = Math.max(0L, System.nanoTime() - signatureStartNanos);

        long equalityStartNanos = System.nanoTime();
        if (previous != null
                && previous.signature() == signature
                && previous.spots().equals(visibleSpots)
                && previous.allocations().equals(visibleAllocations)) {
            ownerSnapshots.put(ownerId, new SpotOwnerSnapshot(
                    ownerId,
                    sourceSpots,
                    previous.spots(),
                    visibleAllocations,
                    signature,
                    previous.gameTime()
            ));
            return CompiledPublishResult.reused(
                    inputCompareNanos,
                    snapshotBuildNanos,
                    signatureNanos,
                    Math.max(0L, System.nanoTime() - equalityStartNanos)
            );
        }
        long equalityNanos = Math.max(0L, System.nanoTime() - equalityStartNanos);

        SpotOwnerSnapshot snapshot = new SpotOwnerSnapshot(
                ownerId, sourceSpots, visibleSpots, visibleAllocations, signature, gameTime
        );
        ownerSnapshots.put(ownerId, snapshot);
        long sendStartNanos = System.nanoTime();
        if (previous == null) {
            sendSnapshotToNearby(level, snapshot, true);
        } else {
            sendSnapshotToNearbyOrPrevious(level, snapshot, previous);
        }
        return CompiledPublishResult.changed(
                inputCompareNanos,
                snapshotBuildNanos,
                signatureNanos,
                equalityNanos,
                Math.max(0L, System.nanoTime() - sendStartNanos)
        );
    }

    public static void clearCompiledSpots(ServerLevel level, int ownerId) {
        Map<Integer, SpotOwnerSnapshot> ownerSnapshots = SPOTS_BY_OWNER.get(level);

        if (ownerSnapshots != null) {
            ownerSnapshots.remove(ownerId);

            if (ownerSnapshots.isEmpty()) {
                SPOTS_BY_OWNER.remove(level);
            }
        }

        sendClearSnapshot(level, ownerId);
    }

    private static void mark(
            Level level,
            BlockPos pos,
            Direction face,
            BeamPacket beam,
            SpotPowerAccumulator spotPower
    ) {
        if (!SpectralizationConfig.surfaceSpotsVisible() || !(level instanceof ServerLevel serverLevel) || spotPower.totalPower() <= 0.0) {
            return;
        }

        SpotRecord spot = createSpot(pos, face, beam, spotPower);

        if (!spot.visible() || !shouldSend(level, spot)) {
            return;
        }

        sendSpotToNearby(serverLevel, spot);
    }

    private static SpotRecord createSpot(
            BlockPos pos,
            Direction face,
            BeamPacket beam,
            SpotPowerAccumulator spotPower
    ) {
        int coherentAlpha = alphaLevel(spotPower.coherentPower(), beam.envelope());
        int strayAlpha = alphaLevel(spotPower.strayPower(), beam.envelope());
        int coherentRadius = coherentAlpha > 0 ? coherentRadiusLevel(beam.envelope()) : 0;
        int strayRadius = strayAlpha > 0 ? strayRadiusLevel(beam.envelope()) : 0;
        int[] coherentColor = spotPower.coherentColor();
        int[] strayColor = spotPower.strayColor();
        int ringAlpha = ringAlphaLevel(coherentAlpha, strayAlpha);

        return new SpotRecord(
                pos.immutable(),
                face,
                coherentAlpha,
                coherentRadius,
                coherentColor[0],
                coherentColor[1],
                coherentColor[2],
                strayAlpha,
                strayRadius,
                strayColor[0],
                strayColor[1],
                strayColor[2],
                ringAlpha
        );
    }

    public static void refresh(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            refresh(level);
        }
    }

    public static void clear(LevelAccessor level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        Map<Integer, SpotOwnerSnapshot> ownerSnapshots = SPOTS_BY_OWNER.remove(serverLevel);
        LEGACY_ACTIVE_SPOTS.remove(serverLevel);
        LAST_REFRESH.remove(serverLevel);
        LAST_LOG.remove(serverLevel);

        if (ownerSnapshots != null) {
            for (int ownerId : ownerSnapshots.keySet()) {
                sendClearSnapshot(serverLevel, ownerId);
            }
        }

        SENT_SNAPSHOTS_BY_PLAYER.remove(serverLevel);
    }

    /** Clears only legacy transient spot updates while preserving compiled owner snapshots. */
    public static void clearLegacySpots(LevelAccessor levelAccessor) {
        if (!(levelAccessor instanceof ServerLevel level)) {
            return;
        }
        Map<SpotKey, ActiveSpot> legacy = LEGACY_ACTIVE_SPOTS.remove(level);
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        for (ActiveSpot active : legacy.values()) {
            sendSpotToNearby(level, invisibleLike(active.spot()));
        }
    }

    public static void discardSpotsAt(LevelAccessor levelAccessor, BlockPos pos) {
        if (!(levelAccessor instanceof ServerLevel level)) {
            return;
        }

        BlockPos removedPos = pos.immutable();
        discardOwnerSpotsAt(level, removedPos);
        discardLegacySpotsAt(level, removedPos);
    }

    public static void clearAll() {
        SPOTS_BY_OWNER.clear();
        SENT_SNAPSHOTS_BY_PLAYER.clear();
        LEGACY_ACTIVE_SPOTS.clear();
        LAST_REFRESH.clear();
        LAST_LOG.clear();
    }

    private static void discardOwnerSpotsAt(ServerLevel level, BlockPos removedPos) {
        Map<Integer, SpotOwnerSnapshot> ownerSnapshots = SPOTS_BY_OWNER.get(level);

        if (ownerSnapshots == null || ownerSnapshots.isEmpty()) {
            return;
        }

        List<Integer> ownerIds = new ArrayList<>(ownerSnapshots.keySet());
        long gameTime = level.getGameTime();

        for (int ownerId : ownerIds) {
            SpotOwnerSnapshot snapshot = ownerSnapshots.get(ownerId);

            if (snapshot == null) {
                continue;
            }

            List<SpotRecord> filteredSpots = spotsWithoutPos(snapshot.spots(), removedPos);
            List<SpotProjectionAllocation> filteredAllocations =
                    allocationsWithoutPos(snapshot.allocations(), removedPos);

            if (filteredSpots.size() == snapshot.spots().size()
                    && filteredAllocations.size() == snapshot.allocations().size()) {
                continue;
            }

            if (filteredSpots.isEmpty()) {
                ownerSnapshots.remove(ownerId);
                sendClearSnapshot(level, ownerId);
                continue;
            }

            long signature = 31L * spotSnapshotSignature(filteredSpots)
                    + allocationSnapshotSignature(filteredAllocations);
            SpotOwnerSnapshot updated = new SpotOwnerSnapshot(
                    ownerId, filteredSpots, filteredSpots, filteredAllocations, signature, gameTime
            );
            ownerSnapshots.put(ownerId, updated);
            sendSnapshotToNearbyOrChangedPos(level, updated, removedPos);
        }

        if (ownerSnapshots.isEmpty()) {
            SPOTS_BY_OWNER.remove(level);
        }
    }

    private static List<SpotRecord> spotsWithoutPos(List<SpotRecord> spots, BlockPos removedPos) {
        List<SpotRecord> filtered = null;

        for (int index = 0; index < spots.size(); index++) {
            SpotRecord spot = spots.get(index);

            if (!spot.pos().equals(removedPos)) {
                if (filtered != null) {
                    filtered.add(spot);
                }
                continue;
            }

            if (filtered == null) {
                filtered = new ArrayList<>(spots.size() - 1);
                filtered.addAll(spots.subList(0, index));
            }
        }

        return filtered == null ? spots : List.copyOf(filtered);
    }

    private static List<SpotProjectionAllocation> allocationsWithoutPos(
            List<SpotProjectionAllocation> allocations,
            BlockPos removedPos
    ) {
        List<SpotProjectionAllocation> filtered = null;

        for (int index = 0; index < allocations.size(); index++) {
            SpotProjectionAllocation allocation = allocations.get(index);

            if (!allocation.pos().equals(removedPos)) {
                if (filtered != null) {
                    filtered.add(allocation);
                }
                continue;
            }

            if (filtered == null) {
                filtered = new ArrayList<>(allocations.size() - 1);
                filtered.addAll(allocations.subList(0, index));
            }
        }

        return filtered == null ? allocations : List.copyOf(filtered);
    }

    private static void discardLegacySpotsAt(ServerLevel level, BlockPos removedPos) {
        Map<SpotKey, ActiveSpot> levelSpots = LEGACY_ACTIVE_SPOTS.get(level);

        if (levelSpots == null || levelSpots.isEmpty()) {
            return;
        }

        List<SpotKey> removedKeys = new ArrayList<>();
        List<SpotRecord> removedSpots = new ArrayList<>();

        for (Map.Entry<SpotKey, ActiveSpot> entry : levelSpots.entrySet()) {
            SpotRecord spot = entry.getValue().spot();

            if (spot.pos().equals(removedPos)) {
                removedKeys.add(entry.getKey());
                removedSpots.add(spot);
            }
        }

        if (removedKeys.isEmpty()) {
            return;
        }

        for (SpotKey key : removedKeys) {
            levelSpots.remove(key);
        }

        if (levelSpots.isEmpty()) {
            LEGACY_ACTIVE_SPOTS.remove(level);
        }

        for (SpotRecord spot : removedSpots) {
            sendSpotToNearby(level, invisibleLike(spot));
        }
    }

    private static void refresh(ServerLevel level) {
        if (!SpectralizationConfig.surfaceSpotsVisible()) {
            clear(level);
            return;
        }

        Map<Integer, SpotOwnerSnapshot> ownerSnapshots = SPOTS_BY_OWNER.get(level);

        if (ownerSnapshots == null || ownerSnapshots.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        long lastRefresh = LAST_REFRESH.getOrDefault(level, (long) -REFRESH_INTERVAL_TICKS);

        if (gameTime - lastRefresh < REFRESH_INTERVAL_TICKS) {
            return;
        }

        LAST_REFRESH.put(level, gameTime);

        int sentSpots = 0;
        int sentPlayers = 0;
        List<SpotRecord> activeSpotRecords = new ArrayList<>();
        List<SpotProjectionAllocation> activeAllocations = new ArrayList<>();

        for (SpotOwnerSnapshot snapshot : ownerSnapshots.values()) {
            SnapshotSendResult result = sendSnapshotToNearby(level, snapshot, false);
            sentSpots += result.sentSpots();
            sentPlayers += result.sentPlayers();
            activeSpotRecords.addAll(snapshot.spots());
            activeAllocations.addAll(snapshot.allocations());
        }

        long lastLog = LAST_LOG.getOrDefault(level, (long) -LOG_INTERVAL_TICKS);
        if (gameTime - lastLog >= LOG_INTERVAL_TICKS) {
            LAST_LOG.put(level, gameTime);
            OpticalCompilerDebugLogger.logSpotOverlay(
                    level,
                    activeSpotRecords,
                    activeAllocations,
                    sentSpots,
                    sentPlayers,
                    gameTime
            );
        }
    }

    private static boolean shouldSend(Level level, SpotRecord spot) {
        long gameTime = level.getGameTime();
        Map<SpotKey, ActiveSpot> levelSpots = LEGACY_ACTIVE_SPOTS.computeIfAbsent(level, ignored -> new HashMap<>());
        SpotKey key = spotKey(spot);
        ActiveSpot lastSent = levelSpots.get(key);

        if (lastSent != null && gameTime - lastSent.lastSentGameTime() < SEND_INTERVAL_TICKS && lastSent.spot().equals(spot)) {
            return false;
        }

        levelSpots.put(key, new ActiveSpot(gameTime, spot));
        return true;
    }

    private static SnapshotSendResult sendSnapshotToNearby(ServerLevel level, SpotOwnerSnapshot snapshot, boolean force) {
        List<SpotOverlayPayload> payloads = snapshotPayloads(snapshot);
        long gameTime = level.getGameTime();
        int sentPlayers = 0;
        int sentSpots = 0;

        for (ServerPlayer player : level.players()) {
            if (!isNearSnapshot(player, snapshot)) {
                continue;
            }

            if (!force && !needsSnapshot(player, snapshot, gameTime)) {
                continue;
            }

            sendSnapshotPayloads(player, payloads);
            rememberSnapshotSent(level, player, snapshot, gameTime);
            sentPlayers++;
            sentSpots += snapshot.spots().size();
        }

        return new SnapshotSendResult(sentSpots, sentPlayers);
    }

    private static SnapshotSendResult sendSnapshotToNearbyOrChangedPos(
            ServerLevel level,
            SpotOwnerSnapshot snapshot,
            BlockPos changedPos
    ) {
        List<SpotOverlayPayload> payloads = snapshotPayloads(snapshot);
        long gameTime = level.getGameTime();
        int sentPlayers = 0;
        int sentSpots = 0;

        for (ServerPlayer player : level.players()) {
            if (!isNearSnapshot(player, snapshot) && !isNearBlock(player, changedPos)) {
                continue;
            }

            sendSnapshotPayloads(player, payloads);
            rememberSnapshotSent(level, player, snapshot, gameTime);
            sentPlayers++;
            sentSpots += snapshot.spots().size();
        }

        return new SnapshotSendResult(sentSpots, sentPlayers);
    }

    private static SnapshotSendResult sendSnapshotToNearbyOrPrevious(
            ServerLevel level,
            SpotOwnerSnapshot snapshot,
            SpotOwnerSnapshot previous
    ) {
        List<SpotOverlayPayload> payloads = snapshotPayloads(snapshot);
        long gameTime = level.getGameTime();
        int sentPlayers = 0;
        int sentSpots = 0;

        for (ServerPlayer player : level.players()) {
            if (!isNearSnapshot(player, snapshot) && !isNearSnapshot(player, previous)) {
                continue;
            }

            sendSnapshotPayloads(player, payloads);
            rememberSnapshotSent(level, player, snapshot, gameTime);
            sentPlayers++;
            sentSpots += snapshot.spots().size();
        }

        return new SnapshotSendResult(sentSpots, sentPlayers);
    }

    private static void sendClearSnapshot(ServerLevel level, int ownerId) {
        SpotOverlayPayload payload = SpotOverlayPayload.clear(ownerId, level.getGameTime());
        Map<UUID, Map<Integer, SentSpotSnapshot>> sentByPlayer = SENT_SNAPSHOTS_BY_PLAYER.get(level);

        if (sentByPlayer == null || sentByPlayer.isEmpty()) {
            return;
        }

        for (ServerPlayer player : level.players()) {
            Map<Integer, SentSpotSnapshot> playerSnapshots = sentByPlayer.get(player.getUUID());
            if (playerSnapshots == null || !playerSnapshots.containsKey(ownerId)) {
                continue;
            }

            PacketDistributor.sendToPlayer(player, payload);
            playerSnapshots.remove(ownerId);
        }
    }

    private static List<SpotOverlayPayload> snapshotPayloads(SpotOwnerSnapshot snapshot) {
        long snapshotToken = snapshot.signature() ^ Long.rotateLeft(snapshot.gameTime(), 23);
        return SpotOverlayPayload.chunks(snapshot.ownerId(), snapshotToken, snapshot.spots());
    }

    private static void sendSnapshotPayloads(ServerPlayer player, List<SpotOverlayPayload> payloads) {
        for (SpotOverlayPayload payload : payloads) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static boolean needsSnapshot(ServerPlayer player, SpotOwnerSnapshot snapshot, long gameTime) {
        Map<UUID, Map<Integer, SentSpotSnapshot>> sentByPlayer = SENT_SNAPSHOTS_BY_PLAYER.get(player.level());

        if (sentByPlayer == null) {
            return true;
        }

        Map<Integer, SentSpotSnapshot> playerSnapshots = sentByPlayer.get(player.getUUID());

        if (playerSnapshots == null) {
            return true;
        }

        SentSpotSnapshot sent = playerSnapshots.get(snapshot.ownerId());

        return sent == null
                || sent.signature() != snapshot.signature()
                || gameTime - sent.gameTime() >= PLAYER_SNAPSHOT_REFRESH_TICKS;
    }

    private static void rememberSnapshotSent(
            ServerLevel level,
            ServerPlayer player,
            SpotOwnerSnapshot snapshot,
            long gameTime
    ) {
        Map<UUID, Map<Integer, SentSpotSnapshot>> sentByPlayer =
                SENT_SNAPSHOTS_BY_PLAYER.computeIfAbsent(level, ignored -> new HashMap<>());
        Map<Integer, SentSpotSnapshot> playerSnapshots =
                sentByPlayer.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        playerSnapshots.put(snapshot.ownerId(), new SentSpotSnapshot(snapshot.signature(), gameTime));
    }

    private static boolean isNearSnapshot(ServerPlayer player, SpotOwnerSnapshot snapshot) {
        for (SpotRecord spot : snapshot.spots()) {
            double sx = spot.pos().getX() + 0.5D;
            double sy = spot.pos().getY() + 0.5D;
            double sz = spot.pos().getZ() + 0.5D;

            if (player.distanceToSqr(sx, sy, sz) <= SEND_RADIUS_SQUARED) {
                return true;
            }
        }

        return false;
    }

    private static boolean isNearBlock(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                <= SEND_RADIUS_SQUARED;
    }

    private static List<SpotRecord> visibleSnapshot(List<SpotRecord> spots) {
        if (spots.isEmpty()) {
            return List.of();
        }

        List<SpotRecord> visibleSpots = new ArrayList<>();
        for (SpotRecord spot : spots) {
            if (spot.visible()) {
                visibleSpots.add(spot);
            }
        }

        visibleSpots.sort(SPOT_COMPARATOR);
        return visibleSpots.isEmpty() ? List.of() : List.copyOf(visibleSpots);
    }

    private static long spotSnapshotSignature(List<SpotRecord> spots) {
        long signature = 0xCBF29CE484222325L;
        signature = mix(signature, spots.size());

        for (SpotRecord spot : spots) {
            signature = mix(signature, spot.pos().asLong());
            signature = mix(signature, spot.face().ordinal());
            signature = mix(signature, spot.coherentAlphaLevel());
            signature = mix(signature, spot.coherentRadiusLevel());
            signature = mix(signature, spot.coherentRed());
            signature = mix(signature, spot.coherentGreen());
            signature = mix(signature, spot.coherentBlue());
            signature = mix(signature, spot.strayAlphaLevel());
            signature = mix(signature, spot.strayRadiusLevel());
            signature = mix(signature, spot.strayRed());
            signature = mix(signature, spot.strayGreen());
            signature = mix(signature, spot.strayBlue());
            signature = mix(signature, spot.ringAlphaLevel());
            signature = mix(signature, spot.projectionMode().ordinal());
            signature = mix(signature, spot.clipMinU());
            signature = mix(signature, spot.clipMinV());
            signature = mix(signature, spot.clipMaxU());
            signature = mix(signature, spot.clipMaxV());
            signature = mix(signature, spot.textureMinU());
            signature = mix(signature, spot.textureMinV());
            signature = mix(signature, spot.textureMaxU());
            signature = mix(signature, spot.textureMaxV());
            signature = mix(signature, spot.quadX0());
            signature = mix(signature, spot.quadY0());
            signature = mix(signature, spot.quadZ0());
            signature = mix(signature, spot.quadTextureU0());
            signature = mix(signature, spot.quadTextureV0());
            signature = mix(signature, spot.quadX1());
            signature = mix(signature, spot.quadY1());
            signature = mix(signature, spot.quadZ1());
            signature = mix(signature, spot.quadTextureU1());
            signature = mix(signature, spot.quadTextureV1());
            signature = mix(signature, spot.quadX2());
            signature = mix(signature, spot.quadY2());
            signature = mix(signature, spot.quadZ2());
            signature = mix(signature, spot.quadTextureU2());
            signature = mix(signature, spot.quadTextureV2());
            signature = mix(signature, spot.quadX3());
            signature = mix(signature, spot.quadY3());
            signature = mix(signature, spot.quadZ3());
            signature = mix(signature, spot.quadTextureU3());
            signature = mix(signature, spot.quadTextureV3());
            signature = mix(signature, spot.debugMarker());
        }

        return signature;
    }

    private static SpotRecord invisibleLike(SpotRecord spot) {
        return new SpotRecord(
                spot.pos(),
                spot.face(),
                0,
                spot.coherentRadiusLevel(),
                spot.coherentRed(),
                spot.coherentGreen(),
                spot.coherentBlue(),
                0,
                spot.strayRadiusLevel(),
                spot.strayRed(),
                spot.strayGreen(),
                spot.strayBlue(),
                0,
                spot.projectionMode(),
                spot.clipMinU(),
                spot.clipMinV(),
                spot.clipMaxU(),
                spot.clipMaxV(),
                spot.textureMinU(),
                spot.textureMinV(),
                spot.textureMaxU(),
                spot.textureMaxV(),
                spot.quadX0(),
                spot.quadY0(),
                spot.quadZ0(),
                spot.quadTextureU0(),
                spot.quadTextureV0(),
                spot.quadX1(),
                spot.quadY1(),
                spot.quadZ1(),
                spot.quadTextureU1(),
                spot.quadTextureV1(),
                spot.quadX2(),
                spot.quadY2(),
                spot.quadZ2(),
                spot.quadTextureU2(),
                spot.quadTextureV2(),
                spot.quadX3(),
                spot.quadY3(),
                spot.quadZ3(),
                spot.quadTextureU3(),
                spot.quadTextureV3(),
                spot.debugMarker()
        );
    }

    private static long allocationSnapshotSignature(List<SpotProjectionAllocation> allocations) {
        long signature = 0x84222325CBF29CE4L;
        signature = mix(signature, allocations.size());

        for (SpotProjectionAllocation allocation : allocations) {
            signature = mix(signature, allocation.pos().asLong());
            signature = mix(signature, allocation.face().ordinal());
            signature = mix(signature, allocation.kind().hashCode());
            signature = mix(signature, Double.doubleToLongBits(allocation.candidateArea()));
            signature = mix(signature, Double.doubleToLongBits(allocation.assignedArea()));
            signature = mix(signature, Double.doubleToLongBits(allocation.emittedArea()));
            signature = mix(signature, Double.doubleToLongBits(allocation.assignedPowerFraction()));
            signature = mix(signature, Double.doubleToLongBits(allocation.emittedPowerFraction()));
            signature = mix(signature, allocation.emittedQuads());
            signature = mix(signature, allocation.result().hashCode());
            signature = mix(signature, allocation.detail().hashCode());
        }

        return signature;
    }

    private static long mix(long seed, long value) {
        long mixed = seed ^ value;
        mixed *= 0x100000001B3L;
        return mixed;
    }

    private static int sendSpotToNearby(ServerLevel level, SpotRecord spot) {
        SpotUpdatePayload payload = SpotUpdatePayload.fromSpot(spot);
        double sx = spot.pos().getX() + 0.5D;
        double sy = spot.pos().getY() + 0.5D;
        double sz = spot.pos().getZ() + 0.5D;
        int sentPlayers = 0;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(sx, sy, sz) <= SEND_RADIUS_SQUARED) {
                PacketDistributor.sendToPlayer(player, payload);
                sentPlayers++;
            }
        }

        return sentPlayers;
    }

    private static boolean isFullBlockSurface(Level level, BlockPos pos, BlockState state) {
        return state.isCollisionShapeFullBlock(level, pos);
    }

    private static int projectionSendPriority(SpotRecord.ProjectionMode projectionMode) {
        return projectionMode == SpotRecord.ProjectionMode.FOOTPRINT_QUAD ? 1 : 0;
    }

    private static int alphaLevel(double spotPower, BeamEnvelope envelope) {
        if (spotPower <= 0.0D) {
            return 0;
        }

        // Temporary projection-debug mode: geometry coverage must not disappear because of brightness.
        if (TEMPORARY_FIXED_SPOT_ALPHA_LEVEL > 0) {
            return TEMPORARY_FIXED_SPOT_ALPHA_LEVEL;
        }

        if (spotPower < MIN_VISIBLE_SPOT_POWER) {
            return 0;
        }

        double scaledPower = spotPower / MIN_VISIBLE_SPOT_POWER;
        int level = (int) Math.floor(Math.log(scaledPower) / Math.log(SPOT_POWER_PER_ALPHA_LEVEL)) + 1;
        return Mth.clamp(level, 1, 15);
    }

    private static int coherentRadiusLevel(BeamEnvelope envelope) {
        double radius = Math.max(envelope.radius(), MIN_SPOT_RADIUS);
        return Mth.clamp((int) Math.ceil(radius * 10.0), 1, 15);
    }

    private static int strayRadiusLevel(BeamEnvelope envelope) {
        int coherentRadius = coherentRadiusLevel(envelope);
        int scatterBonus = Mth.clamp((int) Math.ceil(envelope.scatter() * 6.0), 1, 6);
        int qualityBonus = Mth.clamp((int) Math.floor((envelope.beamQuality() - 1.0) * 1.5), 0, 4);
        return Mth.clamp(coherentRadius + scatterBonus + qualityBonus, 1, 15);
    }

    private static int ringAlphaLevel(int coherentAlpha, int strayAlpha) {
        int strongest = Math.max(coherentAlpha, strayAlpha);
        return strongest >= 13 ? Mth.clamp(strongest - 8, 1, 7) : 0;
    }

    private static double spotYieldFor(PlaneWaveComponent component, OpticalMaterialResponse response) {
        return component.coherence() == CoherenceKind.COHERENT
                ? response.absorption()
                : straySpotYieldFor(response);
    }

    private static double straySpotYieldFor(OpticalMaterialResponse response) {
        return Mth.clamp(1.0 - response.transmittance(), 0.0, 1.0);
    }

    private static double visiblePower(BeamPacket beam) {
        double power = 0.0;
        for (PlaneWaveComponent component : beam.components()) {
            if (component.frequency().region() == SpectralRegion.VISIBLE) {
                power += component.power();
            }
        }
        return power;
    }

    private static int[] colorForFrequencyPower(Map<FrequencyKey, Double> powerByFrequency, double power) {
        if (power <= 0.0 || powerByFrequency.isEmpty()) {
            return colorChannels(DEFAULT_SPOT_RGB);
        }

        int rgb = SpectralColorMap.mixVisibleRgb(weightedFrequencies(powerByFrequency), DEFAULT_SPOT_RGB);
        return colorChannels(rgb);
    }

    private static List<SpectralColorMap.WeightedFrequency> weightedFrequencies(Map<FrequencyKey, Double> powerByFrequency) {
        List<SpectralColorMap.WeightedFrequency> frequencies = new ArrayList<>();

        for (Map.Entry<FrequencyKey, Double> entry : powerByFrequency.entrySet()) {
            frequencies.add(new SpectralColorMap.WeightedFrequency(entry.getKey(), entry.getValue()));
        }

        return frequencies;
    }

    private static int[] colorChannels(int rgb) {
        return new int[]{
                SpectralColorMap.red(rgb),
                SpectralColorMap.green(rgb),
                SpectralColorMap.blue(rgb)
        };
    }

    private static SpotKey spotKey(SpotRecord spot) {
        return new SpotKey(
                spot.pos(),
                spot.face(),
                spot.projectionMode().ordinal(),
                spot.clipMinU(),
                spot.clipMinV(),
                spot.clipMaxU(),
                spot.clipMaxV(),
                spot.textureMinU(),
                spot.textureMinV(),
                spot.textureMaxU(),
                spot.textureMaxV(),
                spot.quadX0(),
                spot.quadY0(),
                spot.quadZ0(),
                spot.quadTextureU0(),
                spot.quadTextureV0(),
                spot.quadX1(),
                spot.quadY1(),
                spot.quadZ1(),
                spot.quadTextureU1(),
                spot.quadTextureV1(),
                spot.quadX2(),
                spot.quadY2(),
                spot.quadZ2(),
                spot.quadTextureU2(),
                spot.quadTextureV2(),
                spot.quadX3(),
                spot.quadY3(),
                spot.quadZ3(),
                spot.quadTextureU3(),
                spot.quadTextureV3()
        );
    }

    private record SpotKey(
            BlockPos pos,
            Direction face,
            int projectionMode,
            int clipMinU,
            int clipMinV,
            int clipMaxU,
            int clipMaxV,
            int textureMinU,
            int textureMinV,
            int textureMaxU,
            int textureMaxV,
            int quadX0,
            int quadY0,
            int quadZ0,
            int quadTextureU0,
            int quadTextureV0,
            int quadX1,
            int quadY1,
            int quadZ1,
            int quadTextureU1,
            int quadTextureV1,
            int quadX2,
            int quadY2,
            int quadZ2,
            int quadTextureU2,
            int quadTextureV2,
            int quadX3,
            int quadY3,
            int quadZ3,
            int quadTextureU3,
            int quadTextureV3
    ) {
    }

    private record ActiveSpot(long lastSentGameTime, SpotRecord spot) {
    }

    private record SpotOwnerSnapshot(
            int ownerId,
            List<SpotRecord> sourceSpots,
            List<SpotRecord> spots,
            List<SpotProjectionAllocation> allocations,
            long signature,
            long gameTime
    ) {
        private SpotOwnerSnapshot {
            sourceSpots = List.copyOf(sourceSpots);
            spots = List.copyOf(spots);
            allocations = List.copyOf(allocations);
        }
    }

    public record CompiledPublishResult(
            boolean reused,
            long inputCompareNanos,
            long snapshotBuildNanos,
            long signatureNanos,
            long equalityNanos,
            long sendNanos
    ) {
        private static CompiledPublishResult reused(
                long inputCompareNanos,
                long snapshotBuildNanos,
                long signatureNanos,
                long equalityNanos
        ) {
            return new CompiledPublishResult(
                    true, inputCompareNanos, snapshotBuildNanos, signatureNanos, equalityNanos, 0L
            );
        }

        private static CompiledPublishResult changed(
                long inputCompareNanos,
                long snapshotBuildNanos,
                long signatureNanos,
                long equalityNanos,
                long sendNanos
        ) {
            return new CompiledPublishResult(
                    false, inputCompareNanos, snapshotBuildNanos, signatureNanos, equalityNanos, sendNanos
            );
        }

        public static CompiledPublishResult noop() {
            return new CompiledPublishResult(true, 0L, 0L, 0L, 0L, 0L);
        }
    }

    private record SentSpotSnapshot(long signature, long gameTime) {
    }

    private record SnapshotSendResult(int sentSpots, int sentPlayers) {
    }

    private static final class SpotPowerAccumulator {
        private double coherentPower;
        private final Map<FrequencyKey, Double> coherentPowerByFrequency = new HashMap<>();
        private double strayPower;
        private final Map<FrequencyKey, Double> strayPowerByFrequency = new HashMap<>();

        void accept(PlaneWaveComponent component, double power) {
            if (power <= 0.0) {
                return;
            }

            if (component.frequency().region() != SpectralRegion.VISIBLE) {
                return;
            }

            if (component.coherence() == CoherenceKind.COHERENT) {
                coherentPower += power;
                coherentPowerByFrequency.merge(component.frequency(), power, Double::sum);
            } else {
                strayPower += power;
                strayPowerByFrequency.merge(component.frequency(), power, Double::sum);
            }
        }

        double coherentPower() {
            return coherentPower;
        }

        double strayPower() {
            return strayPower;
        }

        double totalPower() {
            return coherentPower + strayPower;
        }

        int[] coherentColor() {
            return colorForFrequencyPower(coherentPowerByFrequency, coherentPower);
        }

        int[] strayColor() {
            return colorForFrequencyPower(strayPowerByFrequency, strayPower);
        }
    }

    private OpticalSpotTracker() {
    }
}
