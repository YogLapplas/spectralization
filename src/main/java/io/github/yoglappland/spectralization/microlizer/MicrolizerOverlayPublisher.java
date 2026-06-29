package io.github.yoglappland.spectralization.microlizer;

import io.github.yoglappland.spectralization.network.MicrolizerOverlayPayload;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

public final class MicrolizerOverlayPublisher {
    private static final int REFRESH_INTERVAL_TICKS = 20;

    public static void publishToPlayers(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            publishToPlayers(level, false);
        }
    }

    public static void publishNow(ServerLevel level) {
        publishToPlayers(level, true);
    }

    private static void publishToPlayers(ServerLevel level, boolean force) {
        if (!force && level.getGameTime() % REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }

        MicrolizerOverlayPayload payload = payload(level);
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static MicrolizerOverlayPayload payload(ServerLevel level) {
        List<MicrolizerOverlayPayload.Segment> segments = MicrolizerNetworkData.connections(level)
                .stream()
                .map(MicrolizerOverlayPublisher::segment)
                .toList();
        return new MicrolizerOverlayPayload(segments);
    }

    private static MicrolizerOverlayPayload.Segment segment(MicrolizerConnection connection) {
        return new MicrolizerOverlayPayload.Segment(
                connection.from(),
                connection.to(),
                positiveDirection(connection.direction())
        );
    }

    private static Direction positiveDirection(Direction direction) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? direction
                : direction.getOpposite();
    }

    private MicrolizerOverlayPublisher() {
    }
}
