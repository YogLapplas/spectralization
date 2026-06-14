package io.github.yoglappland.spectralization.compact;

import io.github.yoglappland.spectralization.network.CompactMachineOverlayPayload;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CompactMachineOverlayPublisher {
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

        CompactMachineOverlayPayload payload = payload(level);
        for (var player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static CompactMachineOverlayPayload payload(ServerLevel level) {
        List<CompactMachineOverlayPayload.Segment> segments = CompactMachineNetworkData.connections(level)
                .stream()
                .map(CompactMachineOverlayPublisher::segment)
                .toList();
        return new CompactMachineOverlayPayload(segments);
    }

    private static CompactMachineOverlayPayload.Segment segment(CompactMachineConnection connection) {
        return new CompactMachineOverlayPayload.Segment(
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

    private CompactMachineOverlayPublisher() {
    }
}
