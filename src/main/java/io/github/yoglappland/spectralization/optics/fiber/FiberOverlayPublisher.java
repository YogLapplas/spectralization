package io.github.yoglappland.spectralization.optics.fiber;

import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class FiberOverlayPublisher {
    private static final int OWNER_ID = -0x0B0DCD2;
    private static final int MAX_SEGMENTS = 512;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final double FIBER_RENDER_RADIUS = 1.0D / 16.0D;
    private static final Comparator<Map.Entry<FiberSegmentKey, Integer>> SEGMENT_ORDER = Comparator
            .comparingLong((Map.Entry<FiberSegmentKey, Integer> entry) -> entry.getKey().first())
            .thenComparingLong(entry -> entry.getKey().second());

    public static void publishToPlayers(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            publishToPlayers(level, false);
        }
    }

    public static void publishNow(ServerLevel level) {
        publishToPlayers(level, true);
    }

    public static void publishToPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        PacketDistributor.sendToPlayer(player, payload(FiberNetworkIndex.snapshot(level)));
    }

    private static void publishToPlayers(ServerLevel level, boolean force) {
        if (!force && level.getGameTime() % REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }

        BeamPathOverlayPayload payload = payload(FiberNetworkIndex.snapshot(level));

        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static BeamPathOverlayPayload payload(FiberNetworkSnapshot snapshot) {
        return new BeamPathOverlayPayload(OWNER_ID, segments(snapshot));
    }

    private static List<BeamPathOverlayPayload.Segment> segments(FiberNetworkSnapshot snapshot) {
        if (snapshot.segmentUsage().isEmpty()) {
            return List.of();
        }

        List<Map.Entry<FiberSegmentKey, Integer>> orderedSegments = new ArrayList<>(snapshot.segmentUsage().entrySet());
        orderedSegments.sort(SEGMENT_ORDER);
        List<BeamPathOverlayPayload.Segment> segments = new ArrayList<>();

        for (Map.Entry<FiberSegmentKey, Integer> entry : orderedSegments) {
            FiberSegmentKey key = entry.getKey();
            BlockPos from = key.firstPos();
            BlockPos to = key.secondPos();
            int usage = Math.max(1, entry.getValue());
            int visualLevel = Math.max(1, Math.min(8, 1 + usage * 2));

            segments.add(new BeamPathOverlayPayload.Segment(
                    from,
                    to,
                    dominantDirection(from, to),
                    false,
                    FiberRenderStyle.COLOR_RGB,
                    1,
                    visualLevel,
                    FIBER_RENDER_RADIUS,
                    FIBER_RENDER_RADIUS
            ));

            if (segments.size() >= MAX_SEGMENTS) {
                break;
            }
        }

        return List.copyOf(segments);
    }

    private static Direction dominantDirection(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int az = Math.abs(dz);

        if (ay >= ax && ay >= az) {
            return dy >= 0 ? Direction.UP : Direction.DOWN;
        }

        if (ax >= az) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }

        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private FiberOverlayPublisher() {
    }
}
