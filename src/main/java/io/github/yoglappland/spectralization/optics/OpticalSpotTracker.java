package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.network.SpotUpdatePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

public final class OpticalSpotTracker {
    private static final int SEND_INTERVAL_TICKS = 5;
    private static final double SEND_RADIUS = 64.0;
    private static final double MIN_VISIBLE_IRRADIANCE = 0.75;
    private static final double MAX_VISIBLE_IRRADIANCE = 512.0;
    private static final double MIN_SPOT_RADIUS = 0.08;
    private static final double DEFAULT_OPTICAL_DIFFUSE_YIELD = 0.65;

    private static final Map<Level, Map<SpotKey, LastSentSpot>> LAST_SENT = new WeakHashMap<>();

    public static void markMaterialSpot(
            Level level,
            BlockPos pos,
            Direction face,
            BeamPacket beam,
            BlockState state
    ) {
        if (!state.isCollisionShapeFullBlock(level, pos)) {
            return;
        }

        OpticalMaterialProfile profile = OpticalMaterialProfiles.profileFor(state);
        double spotPower = 0.0;
        int colorBin = 63;

        for (PlaneWaveComponent component : beam.components()) {
            if (component.frequency().region() != SpectralRegion.VISIBLE) {
                continue;
            }

            OpticalMaterialResponse response = profile.responseAt(component.frequency());
            double unmodeledLoss = Math.max(0.0, 1.0 - response.transmittance() - response.reflectance() - response.absorption());
            double diffuseYield = Mth.clamp(response.absorption() * 0.60 + unmodeledLoss * 0.90, 0.0, 0.85);

            spotPower += component.power() * diffuseYield;
            colorBin = component.frequency().bin();
        }

        mark(level, pos, face, beam, spotPower, colorBin);
    }

    public static void markAbsorbedSpot(
            Level level,
            BlockPos pos,
            Direction face,
            BlockState state,
            BeamPacket beam,
            double absorbedPower
    ) {
        if (absorbedPower <= 0.0 || !state.isCollisionShapeFullBlock(level, pos)) {
            return;
        }

        int colorBin = visibleColorBin(beam);
        mark(level, pos, face, beam, absorbedPower * DEFAULT_OPTICAL_DIFFUSE_YIELD, colorBin);
    }

    private static void mark(
            Level level,
            BlockPos pos,
            Direction face,
            BeamPacket beam,
            double spotPower,
            int colorBin
    ) {
        if (!SpectralizationConfig.surfaceSpotsVisible() || !(level instanceof ServerLevel serverLevel) || spotPower <= 0.0) {
            return;
        }

        int brightnessLevel = brightnessLevel(spotPower, beam.envelope());

        if (brightnessLevel <= 0) {
            return;
        }

        SpotRecord spot = new SpotRecord(
                pos.immutable(),
                face,
                brightnessLevel,
                radiusLevel(beam.envelope()),
                Mth.clamp(colorBin, 0, 63)
        );

        if (!shouldSend(level, spot)) {
            return;
        }

        PacketDistributor.sendToPlayersNear(
                serverLevel,
                null,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                SEND_RADIUS,
                SpotUpdatePayload.fromSpot(spot)
        );
    }

    private static boolean shouldSend(Level level, SpotRecord spot) {
        long gameTime = level.getGameTime();
        Map<SpotKey, LastSentSpot> levelSpots = LAST_SENT.computeIfAbsent(level, ignored -> new HashMap<>());
        SpotKey key = new SpotKey(spot.pos(), spot.face());
        LastSentSpot lastSent = levelSpots.get(key);

        if (lastSent != null
                && gameTime - lastSent.gameTime < SEND_INTERVAL_TICKS
                && lastSent.brightnessLevel == spot.brightnessLevel()
                && lastSent.radiusLevel == spot.radiusLevel()
                && lastSent.colorBin == spot.colorBin()) {
            return false;
        }

        levelSpots.put(key, new LastSentSpot(gameTime, spot.brightnessLevel(), spot.radiusLevel(), spot.colorBin()));
        return true;
    }

    private static int brightnessLevel(double spotPower, BeamEnvelope envelope) {
        double radius = Math.max(envelope.radius(), MIN_SPOT_RADIUS);
        double area = Math.PI * radius * radius;
        double irradiance = spotPower / area;

        if (irradiance < MIN_VISIBLE_IRRADIANCE) {
            return 0;
        }

        double normalized = Math.log1p(irradiance / MIN_VISIBLE_IRRADIANCE)
                / Math.log1p(MAX_VISIBLE_IRRADIANCE / MIN_VISIBLE_IRRADIANCE);

        return Mth.clamp((int) Math.ceil(normalized * 7.0), 1, 7);
    }

    private static int radiusLevel(BeamEnvelope envelope) {
        double radius = Math.max(envelope.radius(), MIN_SPOT_RADIUS);
        return Mth.clamp((int) Math.ceil(radius * 8.0), 1, 7);
    }

    private static int visibleColorBin(BeamPacket beam) {
        double strongestPower = -1.0;
        int colorBin = 63;

        for (PlaneWaveComponent component : beam.components()) {
            if (component.frequency().region() == SpectralRegion.VISIBLE && component.power() > strongestPower) {
                strongestPower = component.power();
                colorBin = component.frequency().bin();
            }
        }

        return colorBin;
    }

    private record SpotKey(BlockPos pos, Direction face) {
    }

    private record LastSentSpot(long gameTime, int brightnessLevel, int radiusLevel, int colorBin) {
    }

    private OpticalSpotTracker() {
    }
}
