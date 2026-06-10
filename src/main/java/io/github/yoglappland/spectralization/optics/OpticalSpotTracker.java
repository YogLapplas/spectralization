package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.network.SpotUpdatePayload;
import io.github.yoglappland.spectralization.optics.compiler.OpticalCompilerDebugLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final int SEND_INTERVAL_TICKS = 5;
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final int LOG_INTERVAL_TICKS = 40;
    private static final double SEND_RADIUS = 64.0;
    private static final double SEND_RADIUS_SQUARED = SEND_RADIUS * SEND_RADIUS;
    private static final double MIN_SPOT_RADIUS = 0.08;
    private static final double MIN_VISIBLE_SPOT_POWER = 0.125;
    private static final double SPOT_POWER_PER_ALPHA_LEVEL = 8.0;
    private static final double DEFAULT_OPTICAL_DIFFUSE_YIELD = 0.85;
    private static final double VISIBLE_MIN_WAVELENGTH_NM = 380.0;
    private static final double VISIBLE_MAX_WAVELENGTH_NM = 750.0;

    private static final Map<Level, Map<SpotKey, ActiveSpot>> ACTIVE_SPOTS = new WeakHashMap<>();
    private static final Map<Level, Map<Integer, Set<SpotKey>>> SPOT_KEYS_BY_OWNER = new WeakHashMap<>();
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

        OpticalMaterialProfile profile = OpticalMaterialProfiles.profileFor(state);
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
        OpticalMaterialProfile profile = OpticalMaterialProfiles.profileFor(state);
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
            spotPower.accept(component.withCoherence(CoherenceKind.INCOHERENT), componentStrayPower * response.reflectance());
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

        Map<SpotKey, ActiveSpot> levelSpots = ACTIVE_SPOTS.computeIfAbsent(level, ignored -> new HashMap<>());
        Map<Integer, Set<SpotKey>> ownerKeysByLevel = SPOT_KEYS_BY_OWNER.computeIfAbsent(level, ignored -> new HashMap<>());
        Set<SpotKey> previousKeys = ownerKeysByLevel.getOrDefault(ownerId, Set.of());
        Set<SpotKey> nextKeys = new HashSet<>();
        long gameTime = level.getGameTime();

        for (SpotRecord spot : spots) {
            if (!spot.visible()) {
                continue;
            }

            SpotKey key = new SpotKey(spot.pos(), spot.face());
            nextKeys.add(key);
            ActiveSpot previous = levelSpots.get(key);
            levelSpots.put(key, new ActiveSpot(gameTime, spot));

            if (previous == null
                    || !previous.spot().equals(spot)
                    || gameTime - previous.lastSentGameTime() >= SEND_INTERVAL_TICKS) {
                sendSpotToNearby(level, spot);
            }
        }

        for (SpotKey previousKey : previousKeys) {
            if (nextKeys.contains(previousKey)) {
                continue;
            }

            ActiveSpot removed = levelSpots.remove(previousKey);

            if (removed != null) {
                sendSpotToNearby(level, clearedSpot(removed.spot()));
            }
        }

        if (nextKeys.isEmpty()) {
            ownerKeysByLevel.remove(ownerId);
        } else {
            ownerKeysByLevel.put(ownerId, Set.copyOf(nextKeys));
        }
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

        Map<SpotKey, ActiveSpot> levelSpots = ACTIVE_SPOTS.remove(serverLevel);
        SPOT_KEYS_BY_OWNER.remove(serverLevel);
        LAST_REFRESH.remove(serverLevel);
        LAST_LOG.remove(serverLevel);

        if (levelSpots == null || levelSpots.isEmpty()) {
            return;
        }

        for (ActiveSpot activeSpot : levelSpots.values()) {
            sendSpotToNearby(serverLevel, clearedSpot(activeSpot.spot()));
        }
    }

    public static void clearAll() {
        ACTIVE_SPOTS.clear();
        SPOT_KEYS_BY_OWNER.clear();
        LAST_REFRESH.clear();
        LAST_LOG.clear();
    }

    private static void refresh(ServerLevel level) {
        if (!SpectralizationConfig.surfaceSpotsVisible()) {
            clear(level);
            return;
        }

        Map<SpotKey, ActiveSpot> levelSpots = ACTIVE_SPOTS.get(level);

        if (levelSpots == null || levelSpots.isEmpty()) {
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
        List<SpotRecord> activeSpotRecords = new ArrayList<>(levelSpots.size());

        for (Map.Entry<SpotKey, ActiveSpot> entry : levelSpots.entrySet()) {
            SpotRecord spot = entry.getValue().spot();

            if (!spot.visible()) {
                continue;
            }

            int players = sendSpotToNearby(level, spot);
            if (players > 0) {
                sentSpots++;
                sentPlayers += players;
                entry.setValue(new ActiveSpot(gameTime, spot));
            }

            activeSpotRecords.add(spot);
        }

        long lastLog = LAST_LOG.getOrDefault(level, (long) -LOG_INTERVAL_TICKS);
        if (gameTime - lastLog >= LOG_INTERVAL_TICKS) {
            LAST_LOG.put(level, gameTime);
            OpticalCompilerDebugLogger.logSpotOverlay(level, activeSpotRecords, sentSpots, sentPlayers, gameTime);
        }
    }

    private static boolean shouldSend(Level level, SpotRecord spot) {
        long gameTime = level.getGameTime();
        Map<SpotKey, ActiveSpot> levelSpots = ACTIVE_SPOTS.computeIfAbsent(level, ignored -> new HashMap<>());
        SpotKey key = new SpotKey(spot.pos(), spot.face());
        ActiveSpot lastSent = levelSpots.get(key);

        if (lastSent != null && gameTime - lastSent.lastSentGameTime() < SEND_INTERVAL_TICKS && lastSent.spot().equals(spot)) {
            return false;
        }

        levelSpots.put(key, new ActiveSpot(gameTime, spot));
        return true;
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

    private static SpotRecord clearedSpot(SpotRecord spot) {
        return new SpotRecord(
                spot.pos(),
                spot.face(),
                0,
                0,
                255,
                70,
                48,
                0,
                0,
                255,
                70,
                48,
                0
        );
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
                : response.reflectance();
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

    private static int[] colorForAccumulator(double red, double green, double blue, double power) {
        if (power <= 0.0 || (red <= 0.0 && green <= 0.0 && blue <= 0.0)) {
            return new int[]{255, 70, 48};
        }

        double max = Math.max(red, Math.max(green, blue));

        if (max <= 0.0) {
            return new int[]{255, 70, 48};
        }

        return new int[]{
                Mth.clamp((int) Math.round(red / max * 255.0), 0, 255),
                Mth.clamp((int) Math.round(green / max * 255.0), 0, 255),
                Mth.clamp((int) Math.round(blue / max * 255.0), 0, 255)
        };
    }

    private static double wavelengthNm(PlaneWaveComponent component) {
        int bins = Math.max(1, component.frequency().region().defaultBins() - 1);
        double t = Mth.clamp(component.frequency().bin() / (double) bins, 0.0, 1.0);
        return VISIBLE_MIN_WAVELENGTH_NM + t * (VISIBLE_MAX_WAVELENGTH_NM - VISIBLE_MIN_WAVELENGTH_NM);
    }

    private static double[] wavelengthToRgb(double wavelengthNm) {
        double red;
        double green;
        double blue;

        if (wavelengthNm < 410.0) {
            red = 0.45 + (410.0 - wavelengthNm) / 30.0 * 0.55;
            green = 0.0;
            blue = 1.0;
        } else if (wavelengthNm < 475.0) {
            red = 0.0;
            green = (wavelengthNm - 410.0) / 65.0;
            blue = 1.0;
        } else if (wavelengthNm < 540.0) {
            red = 0.0;
            green = 1.0;
            blue = 1.0 - (wavelengthNm - 475.0) / 65.0;
        } else if (wavelengthNm < 590.0) {
            red = (wavelengthNm - 540.0) / 50.0;
            green = 1.0;
            blue = 0.0;
        } else if (wavelengthNm < 650.0) {
            red = 1.0;
            green = 1.0 - (wavelengthNm - 590.0) / 60.0;
            blue = 0.0;
        } else {
            red = 1.0;
            green = 0.0;
            blue = 0.0;
        }

        double edgeFactor;
        if (wavelengthNm < 420.0) {
            edgeFactor = 0.55 + (wavelengthNm - VISIBLE_MIN_WAVELENGTH_NM) / 40.0 * 0.45;
        } else if (wavelengthNm > 700.0) {
            edgeFactor = 0.55 + (VISIBLE_MAX_WAVELENGTH_NM - wavelengthNm) / 50.0 * 0.45;
        } else {
            edgeFactor = 1.0;
        }

        return new double[]{
                Math.pow(Mth.clamp(red * edgeFactor, 0.0, 1.0), 0.85),
                Math.pow(Mth.clamp(green * edgeFactor, 0.0, 1.0), 0.85),
                Math.pow(Mth.clamp(blue * edgeFactor, 0.0, 1.0), 0.85)
        };
    }

    private record SpotKey(BlockPos pos, Direction face) {
    }

    private record ActiveSpot(long lastSentGameTime, SpotRecord spot) {
    }

    private static final class SpotPowerAccumulator {
        private double coherentPower;
        private double coherentRed;
        private double coherentGreen;
        private double coherentBlue;
        private double strayPower;
        private double strayRed;
        private double strayGreen;
        private double strayBlue;

        void accept(PlaneWaveComponent component, double power) {
            if (power <= 0.0) {
                return;
            }

            double[] rgb = wavelengthToRgb(wavelengthNm(component));

            if (component.coherence() == CoherenceKind.COHERENT) {
                coherentPower += power;
                coherentRed += rgb[0] * power;
                coherentGreen += rgb[1] * power;
                coherentBlue += rgb[2] * power;
            } else {
                strayPower += power;
                strayRed += rgb[0] * power;
                strayGreen += rgb[1] * power;
                strayBlue += rgb[2] * power;
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
            return colorForAccumulator(coherentRed, coherentGreen, coherentBlue, coherentPower);
        }

        int[] strayColor() {
            return colorForAccumulator(strayRed, strayGreen, strayBlue, strayPower);
        }
    }

    private OpticalSpotTracker() {
    }
}
