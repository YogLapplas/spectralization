package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortWaveKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BeamPathOverlayTracker {
    private static final double SEND_RADIUS_SQUARED = 96.0D * 96.0D;
    private static final int MAX_SEGMENTS = 512;
    private static final int TERMINAL_RAY_BLOCKS = 64;
    private static final int TOPOLOGY_COLOR_BIN = 63;
    private static final int TOPOLOGY_WIDTH_LEVEL = 1;
    private static final int TOPOLOGY_VISUAL_LEVEL = 5;

    public static boolean hasHudViewerNear(ServerLevel level, BlockPos pos) {
        double sx = pos.getX() + 0.5D;
        double sy = pos.getY() + 0.5D;
        double sz = pos.getZ() + 0.5D;

        for (ServerPlayer player : level.players()) {
            if (hasHudHelmet(player) && player.distanceToSqr(sx, sy, sz) <= SEND_RADIUS_SQUARED) {
                return true;
            }
        }

        return false;
    }

    public static int terminalRayBlocks() {
        return TERMINAL_RAY_BLOCKS;
    }

    public static int publish(ServerLevel level, CompiledOpticalTrace trace) {
        if (trace.steps().isEmpty()) {
            return 0;
        }

        List<BeamPathOverlayPayload.Segment> segments = BeamVisualSegments.fromTrace(trace, BeamVisibilityKind.HUD)
                .stream()
                .limit(MAX_SEGMENTS)
                .map(BeamPathOverlayTracker::toPayloadSegment)
                .toList();

        if (segments.isEmpty()) {
            return 0;
        }

        return publish(level, trace.sourcePos(), segments);
    }

    public static List<BeamPathOverlayPayload.Segment> topologySegments(CompiledPortGraph graph) {
        if (graph.nodes().isEmpty()) {
            return List.of();
        }

        List<BeamPathOverlayPayload.Segment> segments = new ArrayList<>();
        Set<PortGraphNode> outgoingNodesWithPropagation = new HashSet<>();

        for (PortGraphEdge edge : graph.edges()) {
            if (edge.kind() != PortGraphEdgeKind.PROPAGATION || samePos(edge.from(), edge.to())) {
                continue;
            }

            outgoingNodesWithPropagation.add(edge.from());
            segments.add(toTopologyPayloadSegment(edge.from(), edge.to(), edge.from().side()));

            if (segments.size() >= MAX_SEGMENTS) {
                return List.copyOf(segments);
            }
        }

        for (PortGraphNode node : graph.nodes()) {
            if (node.waveKind() != PortWaveKind.OUTGOING || outgoingNodesWithPropagation.contains(node)) {
                continue;
            }

            BlockPos to = node.pos().relative(node.side(), TERMINAL_RAY_BLOCKS);
            segments.add(toTopologyPayloadSegment(node, new PortGraphNode(to, node.side().getOpposite(), PortWaveKind.INCOMING), node.side()));

            if (segments.size() >= MAX_SEGMENTS) {
                break;
            }
        }

        return List.copyOf(segments);
    }

    public static int publish(
            ServerLevel level,
            BlockPos sourcePos,
            List<BeamPathOverlayPayload.Segment> segments
    ) {
        if (segments.isEmpty()) {
            return 0;
        }

        BeamPathOverlayPayload payload = new BeamPathOverlayPayload(
                segments.size() > MAX_SEGMENTS ? segments.subList(0, MAX_SEGMENTS) : segments
        );
        double sx = sourcePos.getX() + 0.5D;
        double sy = sourcePos.getY() + 0.5D;
        double sz = sourcePos.getZ() + 0.5D;
        int sentPlayers = 0;

        for (ServerPlayer player : level.players()) {
            if (hasHudHelmet(player) && player.distanceToSqr(sx, sy, sz) <= SEND_RADIUS_SQUARED) {
                PacketDistributor.sendToPlayer(player, payload);
                sentPlayers++;
            }
        }

        return sentPlayers;
    }

    private static BeamPathOverlayPayload.Segment toPayloadSegment(BeamVisualSegment segment) {
        return new BeamPathOverlayPayload.Segment(
                segment.from(),
                segment.to(),
                segment.direction(),
                segment.coherence() == CoherenceKind.COHERENT,
                Math.max(0, Math.min(63, segment.colorBin())),
                Math.max(1, Math.min(8, BeamGeometryOps.widthLevel(segment.geometry().envelope()))),
                Math.max(1, Math.min(8, segment.geometry().visualLevel()))
        );
    }

    private static BeamPathOverlayPayload.Segment toTopologyPayloadSegment(
            PortGraphNode from,
            PortGraphNode to,
            Direction direction
    ) {
        return new BeamPathOverlayPayload.Segment(
                from.pos(),
                to.pos(),
                direction,
                true,
                TOPOLOGY_COLOR_BIN,
                TOPOLOGY_WIDTH_LEVEL,
                TOPOLOGY_VISUAL_LEVEL
        );
    }

    private static boolean samePos(PortGraphNode from, PortGraphNode to) {
        return from.pos().equals(to.pos());
    }

    private static boolean hasHudHelmet(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.LEATHER_HELMET);
    }

    private BeamPathOverlayTracker() {
    }
}
