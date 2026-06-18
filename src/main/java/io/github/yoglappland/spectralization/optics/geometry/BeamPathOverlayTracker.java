package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.compiler.CompiledBeamProfileLayer;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortWaveKind;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.optics.compiler.SpectralPowerLane;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final int TOPOLOGY_STRAY_COLOR_RGB = 0xB0DCD2;
    private static final int TOPOLOGY_WIDTH_LEVEL = 1;
    private static final int TOPOLOGY_VISUAL_LEVEL = 5;
    private static final double TOPOLOGY_RADIUS = 1.0D / 16.0D;
    private static final double HUD_POWER_THRESHOLD = 1.0E-6D;

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

    public static boolean hasHudViewerNear(
            ServerLevel level,
            BlockPos sourcePos,
            List<BeamPathOverlayPayload.Segment> segments
    ) {
        for (ServerPlayer player : level.players()) {
            if (hasHudHelmet(player) && isNearOverlayPath(player, sourcePos, segments)) {
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
        return topologySegments(graph, hasHudSignal(solution), solution, CompiledBeamProfileLayer.EMPTY);
    }

    public static List<BeamPathOverlayPayload.Segment> topologySegments(
            CompiledPortGraph graph,
            boolean hasHudIntent,
            ScalarPowerSolution solution
    ) {
        return topologySegments(graph, hasHudIntent, solution, CompiledBeamProfileLayer.EMPTY);
    }

    public static List<BeamPathOverlayPayload.Segment> topologySegments(
            CompiledPortGraph graph,
            boolean hasHudIntent,
            ScalarPowerSolution solution,
            CompiledBeamProfileLayer beamProfileLayer
    ) {
        if (graph.nodes().isEmpty() || (!hasHudIntent && !hasHudSignal(solution))) {
            return List.of();
        }

        List<BeamPathOverlayPayload.Segment> segments = new ArrayList<>();
        addTopologySegments(graph, solution, beamProfileLayer, CoherenceKind.COHERENT, segments);
        addTopologySegments(graph, solution, beamProfileLayer, CoherenceKind.INCOHERENT, segments);

        return List.copyOf(segments.size() > MAX_SEGMENTS ? segments.subList(0, MAX_SEGMENTS) : segments);
    }

    private static void addTopologySegments(
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            CompiledBeamProfileLayer beamProfileLayer,
            CoherenceKind coherence,
            List<BeamPathOverlayPayload.Segment> segments
    ) {
        Set<PortGraphNode> outgoingNodesWithPropagation = new HashSet<>();

        for (PortGraphEdge edge : graph.edges()) {
            if (edge.kind() != PortGraphEdgeKind.PROPAGATION || samePos(edge.from(), edge.to())) {
                continue;
            }

            double power = topologyPower(solution, edge.from(), coherence);

            if (power <= HUD_POWER_THRESHOLD) {
                continue;
            }

            outgoingNodesWithPropagation.add(edge.from());
            segments.add(toTopologyPayloadSegment(
                    edge.from(),
                    edge.to(),
                    edge.from().side(),
                    coherence,
                    power,
                    solution,
                    beamProfileLayer,
                    Math.max(0.0D, edge.distance())
            ));

            if (segments.size() >= MAX_SEGMENTS) {
                return;
            }
        }

        for (PortGraphNode node : graph.nodes()) {
            if (node.waveKind() != PortWaveKind.OUTGOING || outgoingNodesWithPropagation.contains(node)) {
                continue;
            }

            double power = topologyPower(solution, node, coherence);

            if (power <= HUD_POWER_THRESHOLD) {
                continue;
            }

            BlockPos to = node.pos().relative(node.side(), TERMINAL_RAY_BLOCKS);
            segments.add(toTopologyPayloadSegment(
                    node,
                    new PortGraphNode(to, node.side().getOpposite(), PortWaveKind.INCOMING),
                    node.side(),
                    coherence,
                    power,
                    solution,
                    beamProfileLayer,
                    TERMINAL_RAY_BLOCKS
            ));

            if (segments.size() >= MAX_SEGMENTS) {
                break;
            }
        }
    }

    public static boolean hasCoherentSignal(ScalarPowerSolution solution) {
        return hasSignal(solution, CoherenceKind.COHERENT);
    }

    public static boolean hasHudSignal(ScalarPowerSolution solution) {
        return hasSignal(solution, CoherenceKind.COHERENT) || hasSignal(solution, CoherenceKind.INCOHERENT);
    }

    private static boolean hasSignal(ScalarPowerSolution solution, CoherenceKind coherence) {
        if (solution == null) {
            return false;
        }

        if (coherence == CoherenceKind.COHERENT) {
            for (double power : solution.coherentPowerByNode().values()) {
                if (power > HUD_POWER_THRESHOLD) {
                    return true;
                }
            }
        }

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : solution.powerByLane().entrySet()) {
            if (entry.getKey().coherence() != coherence) {
                continue;
            }

            for (double power : entry.getValue().values()) {
                if (power > HUD_POWER_THRESHOLD) {
                    return true;
                }
            }
        }

        return false;
    }

    private static double topologyPower(ScalarPowerSolution solution, PortGraphNode node, CoherenceKind coherence) {
        if (solution == null) {
            return coherence == CoherenceKind.COHERENT ? 1.0D : 0.0D;
        }

        return Math.max(powerAt(solution, node, coherence), 0.0D);
    }

    private static double powerAt(ScalarPowerSolution solution, PortGraphNode node, CoherenceKind coherence) {
        if (coherence == CoherenceKind.COHERENT) {
            return solution.coherentPowerAt(node);
        }

        double power = 0.0D;

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : solution.powerByLane().entrySet()) {
            if (entry.getKey().coherence() == CoherenceKind.INCOHERENT) {
                power += entry.getValue().getOrDefault(node, 0.0D);
            }
        }

        return power;
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
            if (hasHudHelmet(player) && isNearOverlayPath(player, sourcePos, segments)) {
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
        if (!hasHudHelmet(player) || !isNearOverlayPath(player, sourcePos, segments)) {
            return false;
        }

        PacketDistributor.sendToPlayer(player, payload(ownerId, segments));
        return true;
    }

    public static int clearForHudPlayers(ServerLevel level, int ownerId) {
        BeamPathOverlayPayload payload = payload(ownerId, List.of());
        int sentPlayers = 0;

        for (ServerPlayer player : level.players()) {
            if (!hasHudHelmet(player)) {
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
            CoherenceKind coherence,
            double power,
            ScalarPowerSolution solution,
            CompiledBeamProfileLayer beamProfileLayer,
            double distance
    ) {
        BeamEnvelope startEnvelope = profileEnvelopeAt(beamProfileLayer, from, solution, coherence);
        BeamEnvelope endEnvelope = profileEnvelopeAt(beamProfileLayer, to, solution, coherence);

        if (startEnvelope != null && endEnvelope == null) {
            endEnvelope = BeamGeometryOps.propagate(startEnvelope, Math.max(0.0D, distance));
        }

        double startRadius = startEnvelope == null ? TOPOLOGY_RADIUS : startEnvelope.radius();
        double endRadius = endEnvelope == null ? TOPOLOGY_RADIUS : endEnvelope.radius();
        int widthLevel = widthLevelForRadius(Math.max(startRadius, endRadius));

        return new BeamPathOverlayPayload.Segment(
                from.pos(),
                to.pos(),
                direction,
                coherence == CoherenceKind.COHERENT,
                solutionColorRgb(solution, from, coherence),
                widthLevel,
                topologyVisualLevel(power),
                Math.max(0.0D, startRadius),
                Math.max(0.0D, endRadius)
        );
    }

    private static BeamEnvelope profileEnvelopeAt(
            CompiledBeamProfileLayer beamProfileLayer,
            PortGraphNode node,
            ScalarPowerSolution solution,
            CoherenceKind coherence
    ) {
        if (beamProfileLayer == null || beamProfileLayer.envelopesByLane().isEmpty() || solution == null) {
            return null;
        }

        return beamProfileLayer.envelopeAtOrNull(node, solution, coherence);
    }

    private static int widthLevelForRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return TOPOLOGY_WIDTH_LEVEL;
        }

        double clampedRadius = Math.max(BeamGeometryOps.MIN_RADIUS, radius);
        return Math.max(1, Math.min(8, (int) Math.ceil(clampedRadius * 8.0D)));
    }

    private static int solutionColorRgb(ScalarPowerSolution solution, PortGraphNode node, CoherenceKind coherence) {
        if (solution == null) {
            return coherence == CoherenceKind.COHERENT ? TOPOLOGY_COLOR_RGB : TOPOLOGY_STRAY_COLOR_RGB;
        }

        return solution.mixedVisibleRgbAt(
                node,
                coherence,
                coherence == CoherenceKind.COHERENT ? TOPOLOGY_COLOR_RGB : TOPOLOGY_STRAY_COLOR_RGB
        );
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
