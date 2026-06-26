package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortWaveKind;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
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
    private static final int TOPOLOGY_COLOR_RGB = 0xFF2300;
    private static final int TOPOLOGY_WIDTH_LEVEL = 1;
    private static final int TOPOLOGY_VISUAL_LEVEL = 5;
    private static final double TOPOLOGY_RADIUS = 1.0D / 16.0D;
    private static final double HUD_COHERENT_POWER_THRESHOLD = 1.0E-6D;

    public static boolean hasHudViewerNear(ServerLevel level, BlockPos pos) {
        double sx = pos.getX() + 0.5D;
        double sy = pos.getY() + 0.5D;
        double sz = pos.getZ() + 0.5D;

        for (ServerPlayer player : level.players()) {
            if (hasHudViewer(player) && player.distanceToSqr(sx, sy, sz) <= SEND_RADIUS_SQUARED) {
                return true;
            }
        }

        return false;
    }

    public static boolean hasHudViewerNear(
            ServerLevel level,
            BlockPos sourcePos,
            List<BeamPathOverlayPayload.Segment> segments
    ) {
        for (ServerPlayer player : level.players()) {
            if (hasHudViewer(player) && isNearOverlayPath(player, sourcePos, segments)) {
                return true;
            }
        }

        return false;
    }

    public static int terminalRayBlocks() {
        return TERMINAL_RAY_BLOCKS;
    }

    public static long signature(List<BeamPathOverlayPayload.Segment> segments) {
        long signature = 0xCBF29CE484222325L;
        signature = mix(signature, segments.size());

        for (BeamPathOverlayPayload.Segment segment : segments) {
            signature = mix(signature, segment.from().asLong());
            signature = mix(signature, segment.to().asLong());
            signature = mix(signature, segment.direction().ordinal());
            signature = mix(signature, segment.coherent() ? 1 : 0);
            signature = mix(signature, segment.colorRgb());
            signature = mix(signature, segment.widthLevel());
            signature = mix(signature, segment.visualLevel());
            signature = mix(signature, Double.doubleToLongBits(segment.startRadius()));
            signature = mix(signature, Double.doubleToLongBits(segment.endRadius()));
        }

        return signature;
    }

    public static int publish(ServerLevel level, CompiledOpticalTrace trace) {
        if (trace.steps().isEmpty()) {
            return 0;
        }

        List<BeamPathOverlayPayload.Segment> segments = BeamVisualSegments.fromTrace(trace, BeamVisibilityKind.HUD)
                .stream()
                .filter(segment -> segment.coherence() == CoherenceKind.COHERENT)
                .limit(MAX_SEGMENTS)
                .map(BeamPathOverlayTracker::toPayloadSegment)
                .toList();

        if (segments.isEmpty()) {
            return 0;
        }

        return publish(level, trace.sourcePos(), trace.sourcePos().hashCode(), segments);
    }

    public static List<BeamPathOverlayPayload.Segment> topologySegments(
            CompiledPortGraph graph,
            ScalarPowerSolution solution
    ) {
        return topologySegments(graph, hasCoherentSignal(solution), solution);
    }

    public static List<BeamPathOverlayPayload.Segment> topologySegments(
            CompiledPortGraph graph,
            boolean hasCoherentIntensity,
            ScalarPowerSolution solution
    ) {
        if (graph.nodes().isEmpty() || !hasCoherentIntensity) {
            return List.of();
        }

        List<BeamPathOverlayPayload.Segment> segments = new ArrayList<>();
        Set<PortGraphNode> outgoingNodesWithPropagation = new HashSet<>();

        for (PortGraphEdge edge : graph.edges()) {
            if (edge.kind() != PortGraphEdgeKind.PROPAGATION || samePos(edge.from(), edge.to())) {
                continue;
            }

            double coherentPower = topologyPower(solution, edge.from());

            outgoingNodesWithPropagation.add(edge.from());
            segments.add(toTopologyPayloadSegment(edge.from(), edge.to(), edge.from().side(), coherentPower, solution));

            if (segments.size() >= MAX_SEGMENTS) {
                return List.copyOf(segments);
            }
        }

        for (PortGraphNode node : graph.nodes()) {
            if (node.waveKind() != PortWaveKind.OUTGOING || outgoingNodesWithPropagation.contains(node)) {
                continue;
            }

            double coherentPower = topologyPower(solution, node);

            BlockPos to = node.pos().relative(node.side(), TERMINAL_RAY_BLOCKS);
            segments.add(toTopologyPayloadSegment(
                    node,
                    new PortGraphNode(to, node.side().getOpposite(), PortWaveKind.INCOMING),
                    node.side(),
                    coherentPower,
                    solution
            ));

            if (segments.size() >= MAX_SEGMENTS) {
                break;
            }
        }

        return List.copyOf(segments);
    }

    public static boolean hasCoherentSignal(ScalarPowerSolution solution) {
        for (double power : solution.coherentPowerByNode().values()) {
            if (power > HUD_COHERENT_POWER_THRESHOLD) {
                return true;
            }
        }

        return false;
    }

    private static double topologyPower(ScalarPowerSolution solution, PortGraphNode node) {
        if (solution == null) {
            return 1.0D;
        }

        return Math.max(solution.coherentPowerAt(node), 1.0D);
    }

    public static int publish(
            ServerLevel level,
            BlockPos sourcePos,
            int ownerId,
            List<BeamPathOverlayPayload.Segment> segments
    ) {
        BeamPathOverlayPayload payload = new BeamPathOverlayPayload(
                ownerId,
                segments.size() > MAX_SEGMENTS ? segments.subList(0, MAX_SEGMENTS) : segments
        );
        int sentPlayers = 0;

        for (ServerPlayer player : level.players()) {
            if (hasHudViewer(player) && isNearOverlayPath(player, sourcePos, segments)) {
                PacketDistributor.sendToPlayer(player, payload);
                sentPlayers++;
            }
        }

        return sentPlayers;
    }

    public static boolean publishToPlayer(
            ServerPlayer player,
            BlockPos sourcePos,
            int ownerId,
            List<BeamPathOverlayPayload.Segment> segments
    ) {
        if (!hasHudViewer(player) || !isNearOverlayPath(player, sourcePos, segments)) {
            return false;
        }

        PacketDistributor.sendToPlayer(player, payload(ownerId, segments));
        return true;
    }

    public static int clearForHudPlayers(ServerLevel level, int ownerId) {
        BeamPathOverlayPayload payload = payload(ownerId, List.of());
        int sentPlayers = 0;

        for (ServerPlayer player : level.players()) {
            if (!hasHudViewer(player)) {
                continue;
            }

            PacketDistributor.sendToPlayer(player, payload);
            sentPlayers++;
        }

        return sentPlayers;
    }

    public static void clearForPlayer(ServerPlayer player, int ownerId) {
        PacketDistributor.sendToPlayer(player, payload(ownerId, List.of()));
    }

    public static boolean hasHudHelmet(ServerPlayer player) {
        var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        return helmet.is(Items.LEATHER_HELMET)
                || helmet.is(Spectralization.VERITY_HELM_OF_ALL_SEEING_INSIGHT.get());
    }

    public static boolean hasHudViewer(ServerPlayer player) {
        return hasHudHelmet(player)
                || player.getMainHandItem().is(Spectralization.OPTICAL_FIBER_COIL.get())
                || player.getOffhandItem().is(Spectralization.OPTICAL_FIBER_COIL.get())
                || player.getMainHandItem().is(Spectralization.SINGLE_MODE_FIBER_COIL.get())
                || player.getOffhandItem().is(Spectralization.SINGLE_MODE_FIBER_COIL.get())
                || player.getMainHandItem().is(Items.SHEARS)
                || player.getOffhandItem().is(Items.SHEARS);
    }

    private static BeamPathOverlayPayload payload(int ownerId, List<BeamPathOverlayPayload.Segment> segments) {
        return new BeamPathOverlayPayload(
                ownerId,
                segments.size() > MAX_SEGMENTS ? segments.subList(0, MAX_SEGMENTS) : segments
        );
    }

    private static BeamPathOverlayPayload.Segment toPayloadSegment(BeamVisualSegment segment) {
        return new BeamPathOverlayPayload.Segment(
                segment.from(),
                segment.to(),
                segment.direction(),
                segment.coherence() == CoherenceKind.COHERENT,
                segment.colorRgb(),
                Math.max(1, Math.min(8, BeamGeometryOps.widthLevel(segment.geometry().envelope()))),
                Math.max(1, Math.min(8, segment.geometry().visualLevel())),
                Math.max(0.0D, segment.startRadius()),
                Math.max(0.0D, segment.endRadius())
        );
    }

    private static BeamPathOverlayPayload.Segment toTopologyPayloadSegment(
            PortGraphNode from,
            PortGraphNode to,
            Direction direction,
            double coherentPower,
            ScalarPowerSolution solution
    ) {
        return new BeamPathOverlayPayload.Segment(
                from.pos(),
                to.pos(),
                direction,
                true,
                solutionColorRgb(solution, from),
                TOPOLOGY_WIDTH_LEVEL,
                topologyVisualLevel(coherentPower),
                TOPOLOGY_RADIUS,
                TOPOLOGY_RADIUS
        );
    }

    private static int solutionColorRgb(ScalarPowerSolution solution, PortGraphNode node) {
        if (solution == null) {
            return TOPOLOGY_COLOR_RGB;
        }

        return solution.mixedVisibleRgbAt(node, CoherenceKind.COHERENT, TOPOLOGY_COLOR_RGB);
    }

    private static int topologyVisualLevel(double coherentPower) {
        if (!Double.isFinite(coherentPower) || coherentPower <= 0.0D) {
            return 1;
        }

        int level = (int) Math.ceil(Math.log(coherentPower + 1.0D) / Math.log(2.0D)) + 2;

        return Math.max(1, Math.min(8, level));
    }

    private static boolean samePos(PortGraphNode from, PortGraphNode to) {
        return from.pos().equals(to.pos());
    }

    private static boolean isNearOverlayPath(
            ServerPlayer player,
            BlockPos sourcePos,
            List<BeamPathOverlayPayload.Segment> segments
    ) {
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        if (distanceToBlockCenterSqr(px, py, pz, sourcePos) <= SEND_RADIUS_SQUARED) {
            return true;
        }

        for (BeamPathOverlayPayload.Segment segment : segments) {
            if (distanceToSegmentSqr(px, py, pz, segment.from(), segment.to()) <= SEND_RADIUS_SQUARED) {
                return true;
            }
        }

        return false;
    }

    private static double distanceToBlockCenterSqr(double px, double py, double pz, BlockPos pos) {
        double dx = px - (pos.getX() + 0.5D);
        double dy = py - (pos.getY() + 0.5D);
        double dz = pz - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceToSegmentSqr(double px, double py, double pz, BlockPos from, BlockPos to) {
        double ax = from.getX() + 0.5D;
        double ay = from.getY() + 0.5D;
        double az = from.getZ() + 0.5D;
        double bx = to.getX() + 0.5D;
        double by = to.getY() + 0.5D;
        double bz = to.getZ() + 0.5D;
        double vx = bx - ax;
        double vy = by - ay;
        double vz = bz - az;
        double lengthSquared = vx * vx + vy * vy + vz * vz;
        double t = lengthSquared <= 0.0D
                ? 0.0D
                : ((px - ax) * vx + (py - ay) * vy + (pz - az) * vz) / lengthSquared;
        double clampedT = Math.max(0.0D, Math.min(1.0D, t));
        double cx = ax + vx * clampedT;
        double cy = ay + vy * clampedT;
        double cz = az + vz * clampedT;
        double dx = px - cx;
        double dy = py - cy;
        double dz = pz - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static long mix(long seed, long value) {
        long mixed = seed ^ value;
        mixed *= 0x100000001B3L;
        return mixed;
    }

    private BeamPathOverlayTracker() {
    }
}
