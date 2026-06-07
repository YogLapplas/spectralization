package io.github.yoglappland.spectralization.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.yoglappland.spectralization.network.NetworkOverlayPayload;
import io.github.yoglappland.spectralization.optics.OpticalEntityInteractions;
import io.github.yoglappland.spectralization.optics.OpticalPathVisualization;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SpectralCommands {
    private static final Set<UUID> NETWORK_OVERLAY_PLAYERS = new HashSet<>();
    private static final int MAX_OVERLAY_POSITIONS = 16_384;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spectralization")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("lightpaths")
                        .executes(context -> reportState(context.getSource()))
                        .then(Commands.literal("on")
                                .executes(context -> setLightPaths(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setLightPaths(context.getSource(), false)))
                        .then(Commands.literal("toggle")
                                .executes(context -> setLightPaths(
                                        context.getSource(),
                                        !OpticalPathVisualization.isEnabled()
                                ))))
                .then(Commands.literal("laserdamage")
                        .executes(context -> reportLaserDamageState(context.getSource()))
                        .then(Commands.literal("on")
                                .executes(context -> setLaserDamage(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setLaserDamage(context.getSource(), false)))
                        .then(Commands.literal("toggle")
                                .executes(context -> setLaserDamage(
                                        context.getSource(),
                                        !OpticalEntityInteractions.isLaserDamageEnabled()
                                ))))
                .then(Commands.literal("laserblindness")
                        .executes(context -> reportLaserBlindnessState(context.getSource()))
                        .then(Commands.literal("on")
                                .executes(context -> setLaserBlindness(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setLaserBlindness(context.getSource(), false)))
                        .then(Commands.literal("toggle")
                                .executes(context -> setLaserBlindness(
                                        context.getSource(),
                                        !OpticalEntityInteractions.isLaserBlindnessEnabled()
                                ))))
                .then(Commands.literal("networks")
                        .executes(context -> reportNetworkStatus(context.getSource()))
                        .then(Commands.literal("status")
                                .executes(context -> reportNetworkStatus(context.getSource())))
                        .then(Commands.literal("rebuild")
                                .executes(context -> rebuildNetworkIndex(
                                        context.getSource(),
                                        OpticalNetworkIndex.DEFAULT_REBUILD_RADIUS
                                ))
                                .then(Commands.argument(
                                                "radius",
                                                IntegerArgumentType.integer(
                                                        OpticalNetworkIndex.MIN_REBUILD_RADIUS,
                                                        OpticalNetworkIndex.MAX_REBUILD_RADIUS
                                                )
                                        )
                                        .executes(context -> rebuildNetworkIndex(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "radius")
                                        ))))
                        .then(Commands.literal("overlay")
                                .executes(context -> reportNetworkOverlayState(context.getSource()))
                                .then(Commands.literal("on")
                                        .executes(context -> setNetworkOverlay(
                                                context.getSource(),
                                                true,
                                                OpticalNetworkIndex.DEFAULT_REBUILD_RADIUS
                                        ))
                                        .then(Commands.argument(
                                                        "radius",
                                                        IntegerArgumentType.integer(
                                                                OpticalNetworkIndex.MIN_REBUILD_RADIUS,
                                                                OpticalNetworkIndex.MAX_REBUILD_RADIUS
                                                        )
                                                )
                                                .executes(context -> setNetworkOverlay(
                                                        context.getSource(),
                                                        true,
                                                        IntegerArgumentType.getInteger(context, "radius")
                                                ))))
                                .then(Commands.literal("off")
                                        .executes(context -> setNetworkOverlay(
                                                context.getSource(),
                                                false,
                                                OpticalNetworkIndex.DEFAULT_REBUILD_RADIUS
                                        )))
                                .then(Commands.literal("toggle")
                                        .executes(context -> toggleNetworkOverlay(
                                                context.getSource(),
                                                OpticalNetworkIndex.DEFAULT_REBUILD_RADIUS
                                        ))
                                        .then(Commands.argument(
                                                        "radius",
                                                        IntegerArgumentType.integer(
                                                                OpticalNetworkIndex.MIN_REBUILD_RADIUS,
                                                                OpticalNetworkIndex.MAX_REBUILD_RADIUS
                                                        )
                                                )
                                                .executes(context -> toggleNetworkOverlay(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "radius")
                                                )))))));
    }

    private static int setLightPaths(CommandSourceStack source, boolean enabled) {
        OpticalPathVisualization.setEnabled(enabled);
        return reportState(source);
    }

    private static int reportState(CommandSourceStack source) {
        String state = OpticalPathVisualization.isEnabled() ? "on" : "off";
        source.sendSuccess(() -> Component.literal("Spectralization light path visualization: " + state), true);
        return 1;
    }

    private static int setLaserDamage(CommandSourceStack source, boolean enabled) {
        OpticalEntityInteractions.setLaserDamageEnabled(enabled);
        return reportLaserDamageState(source);
    }

    private static int reportLaserDamageState(CommandSourceStack source) {
        String state = OpticalEntityInteractions.isLaserDamageEnabled() ? "on" : "off";
        source.sendSuccess(() -> Component.literal("Spectralization laser damage: " + state), true);
        return 1;
    }

    private static int setLaserBlindness(CommandSourceStack source, boolean enabled) {
        OpticalEntityInteractions.setLaserBlindnessEnabled(enabled);
        return reportLaserBlindnessState(source);
    }

    private static int reportLaserBlindnessState(CommandSourceStack source) {
        String state = OpticalEntityInteractions.isLaserBlindnessEnabled() ? "on" : "off";
        source.sendSuccess(() -> Component.literal("Spectralization laser blindness: " + state), true);
        return 1;
    }

    private static int rebuildNetworkIndex(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos center = BlockPos.containing(source.getPosition());
        OpticalNetworkSnapshot snapshot = OpticalNetworkIndex.rebuildAround(level, center, radius);

        if (source.getEntity() instanceof ServerPlayer player
                && NETWORK_OVERLAY_PLAYERS.contains(player.getUUID())) {
            sendNetworkOverlay(player, snapshot, true);
        }

        return reportNetworkSnapshot(source, snapshot, radius);
    }

    private static int reportNetworkStatus(CommandSourceStack source) {
        OpticalNetworkSnapshot snapshot = OpticalNetworkIndex.snapshot(source.getLevel());
        source.sendSuccess(() -> Component.literal(String.format(
                "Spectralization optical networks: %d networks, %d nodes, %d edges, %d overlay cells, dirty=%s",
                snapshot.networks().size(),
                snapshot.nodeCount(),
                snapshot.edgeCount(),
                snapshot.overlayPositions().size(),
                snapshot.dirty()
        )), false);
        return 1;
    }

    private static int reportNetworkSnapshot(CommandSourceStack source, OpticalNetworkSnapshot snapshot, int radius) {
        source.sendSuccess(() -> Component.literal(String.format(
                "Rebuilt optical networks in radius %d: %d networks, %d nodes, %d edges, %d overlay cells",
                radius,
                snapshot.networks().size(),
                snapshot.nodeCount(),
                snapshot.edgeCount(),
                snapshot.overlayPositions().size()
        )), true);
        return Math.max(1, snapshot.networks().size());
    }

    private static int setNetworkOverlay(CommandSourceStack source, boolean enabled, int radius) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Network overlay can only be used by a player."));
            return 0;
        }

        if (!enabled) {
            NETWORK_OVERLAY_PLAYERS.remove(player.getUUID());
            sendNetworkOverlay(player, OpticalNetworkSnapshot.EMPTY, false);
            source.sendSuccess(() -> Component.literal("Spectralization optical network overlay: off"), true);
            return 1;
        }

        NETWORK_OVERLAY_PLAYERS.add(player.getUUID());
        OpticalNetworkSnapshot snapshot = OpticalNetworkIndex.rebuildAround(
                player.serverLevel(),
                player.blockPosition(),
                radius
        );
        sendNetworkOverlay(player, snapshot, true);

        source.sendSuccess(() -> Component.literal(String.format(
                "Spectralization optical network overlay: on (%d cells)",
                Math.min(MAX_OVERLAY_POSITIONS, snapshot.overlayPositions().size())
        )), true);
        return Math.max(1, snapshot.overlayPositions().size());
    }

    private static int toggleNetworkOverlay(CommandSourceStack source, int radius) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Network overlay can only be used by a player."));
            return 0;
        }

        return setNetworkOverlay(source, !NETWORK_OVERLAY_PLAYERS.contains(player.getUUID()), radius);
    }

    private static int reportNetworkOverlayState(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Network overlay can only be used by a player."));
            return 0;
        }

        String state = NETWORK_OVERLAY_PLAYERS.contains(player.getUUID()) ? "on" : "off";
        source.sendSuccess(() -> Component.literal("Spectralization optical network overlay: " + state), false);
        return 1;
    }

    private static void sendNetworkOverlay(
            ServerPlayer player,
            OpticalNetworkSnapshot snapshot,
            boolean visible
    ) {
        List<BlockPos> positions = new ArrayList<>(snapshot.overlayPositions());

        if (positions.size() > MAX_OVERLAY_POSITIONS) {
            positions = positions.subList(0, MAX_OVERLAY_POSITIONS);
        }

        PacketDistributor.sendToPlayer(player, new NetworkOverlayPayload(visible, positions));
    }

    private SpectralCommands() {
    }
}
