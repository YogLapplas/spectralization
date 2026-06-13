package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.network.SpotOverlayPayload;
import io.github.yoglappland.spectralization.network.SpotUpdatePayload;
import io.github.yoglappland.spectralization.optics.compiler.OpticalCompilerDebugLogger;
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
    private static final double DEFAULT_OPTICAL_DIFFUSE_YIELD = 0.85;
    private static final int DEFAULT_SPOT_RGB = 0xFF4630;
    private static final Comparator<SpotRecord> SPOT_COMPARATOR = Comparator
            .comparingInt((SpotRecord spot) -> spot.pos().getX())
            .thenComparingInt(spot -> spot.pos().getY())
            .thenComparingInt(spot -> spot.pos().getZ())
            .thenComparingInt(spot -> spot.face().ordinal());

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

    public static void publishCompiledSpots(ServerLevel level, int ownerId, List<SpotRecord> spots) {
        if (!SpectralizationConfig.surfaceSpotsVisible()) {
            clear(level);
            return;
        }

        Map<Integer, SpotOwnerSnapshot> ownerSnapshots = SPOTS_BY_OWNER.computeIfAbsent(level, ignored -> new HashMap<>());
        long gameTime = level.getGameTime();
        List<SpotRecord> visibleSpots = visibleSnapshot(spots);

        if (visibleSpots.isEmpty()) {
            SpotOwnerSnapshot removed = ownerSnapshots.remove(ownerId);
            if (removed != null) {
                sendClearSnapshot(level, ownerId);
            }
            return;
        }

        long signature = spotSnapshotSignature(visibleSpots);
        SpotOwnerSnapshot previous = ownerSnapshots.get(ownerId);

        if (previous != null && previous.signature() == signature && previous.spots().equals(visibleSpots)) {
            return;
        }

        SpotOwnerSnapshot snapshot = new SpotOwnerSnapshot(ownerId, visibleSpots, signature, gameTime);
        ownerSnapshots.put(ownerId, snapshot);
        sendSnapshotToNearby(level, snapshot, true);
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

    public static void clearAll() {
        SPOTS_BY_OWNER.clear();
        SENT_SNAPSHOTS_BY_PLAYER.clear();
        LEGACY_ACTIVE_SPOTS.clear();
        LAST_REFRESH.clear();
        LAST_LOG.clear();
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

        for (SpotOwnerSnapshot snapshot : ownerSnapshots.values()) {
            SnapshotSendResult result = sendSnapshotToNearby(level, snapshot, false);
            sentSpots += result.sentSpots();
            sentPlayers += result.sentPlayers();
            activeSpotRecords.addAll(snapshot.spots());
        }

        long lastLog = LAST_LOG.getOrDefault(level, (long) -LOG_INTERVAL_TICKS);
        if (gameTime - lastLog >= LOG_INTERVAL_TICKS) {
            LAST_LOG.put(level, gameTime);
            OpticalCompilerDebugLogger.logSpotOverlay(level, activeSpotRecords, sentSpots, sentPlayers, gameTime);
        }
    }

    private static boolean shouldSend(Level level, SpotRecord spot) {
        long gameTime = level.getGameTime();
        Map<SpotKey, ActiveSpot> levelSpots = LEGACY_ACTIVE_SPOTS.computeIfAbsent(level, ignored -> new HashMap<>());
        SpotKey key = new SpotKey(spot.pos(), spot.face());
        ActiveSpot lastSent = levelSpots.get(key);

        if (lastSent != null && gameTime - lastSent.lastSentGameTime() < SEND_INTERVAL_TICKS && lastSent.spot().equals(spot)) {
            return false;
        }

        levelSpots.put(key, new ActiveSpot(gameTime, spot));
        return true;
    }

    private static SnapshotSendResult sendSnapshotToNearby(ServerLevel level, SpotOwnerSnapshot snapshot, boolean force) {
        SpotOverlayPayload payload = new SpotOverlayPayload(snapshot.ownerId(), snapshot.spots());
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

            PacketDistributor.sendToPlayer(player, payload);
            rememberSnapshotSent(level, player, snapshot, gameTime);
            sentPlayers++;
            sentSpots += snapshot.spots().size();
        }

        return new SnapshotSendResult(sentSpots, sentPlayers);
    }

    private static void sendClearSnapshot(ServerLevel level, int ownerId) {
        SpotOverlayPayload payload = new SpotOverlayPayload(ownerId, List.of());
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

    private static int alphaLevel(double spotPower, BeamEnvelope envelope) {
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

    private record SpotKey(BlockPos pos, Direction face) {
    }

    private record ActiveSpot(long lastSentGameTime, SpotRecord spot) {
    }

    private record SpotOwnerSnapshot(int ownerId, List<SpotRecord> spots, long signature, long gameTime) {
        private SpotOwnerSnapshot {
            spots = List.copyOf(spots);
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
