package io.github.yoglappland.spectralization.optics.fiber;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortWaveKind;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.optics.compiler.SpectralPowerLane;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class FiberOverloadMonitor {
    private static final double POWER_CAPACITY_PER_FIBER = 64.0D;

    public static boolean burnOverloadedFibers(
            ServerLevel level,
            CompiledPortGraph graph,
            ScalarPowerSolution solution
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(solution, "solution");

        if (!solution.reliableForReadout()) {
            return false;
        }

        FiberNetworkSnapshot snapshot = FiberNetworkIndex.snapshot(level);

        if (snapshot.connections().isEmpty()) {
            return false;
        }

        Map<FiberSegmentKey, Double> loadBySegment = loadByFiberSegment(snapshot, graph, solution);

        if (loadBySegment.isEmpty()) {
            return false;
        }

        Set<FiberSegmentKey> overloadedSegments = new HashSet<>();
        List<String> overloadLogParts = new ArrayList<>();

        for (Map.Entry<FiberSegmentKey, Double> entry : loadBySegment.entrySet()) {
            int usage = snapshot.segmentUsage().getOrDefault(entry.getKey(), 0);
            double capacity = usage * POWER_CAPACITY_PER_FIBER;

            if (usage <= 0 || entry.getValue() <= capacity) {
                continue;
            }

            overloadedSegments.add(entry.getKey());
            overloadLogParts.add(formatOverload(entry.getKey(), entry.getValue(), capacity, usage));
        }

        if (overloadedSegments.isEmpty()) {
            return false;
        }

        Spectralization.LOGGER.info(
                "Fiber overload in {}: burning {} segment(s): {}",
                level.dimension().location(),
                overloadedSegments.size(),
                String.join(", ", overloadLogParts)
        );
        SpectralDiagnostics.anomaly(level, SpectralDiagnostics.Subsystem.FIBER, "overload")
                .field("segments", overloadedSegments.size())
                .field("graph_nodes", graph.nodes().size())
                .field("graph_edges", graph.edges().size())
                .field("solver", solution.solverKind())
                .field("reliable", solution.reliableForReadout())
                .field("capacity_per_fiber", POWER_CAPACITY_PER_FIBER)
                .write();
        return FiberNetworkData.removeConnectionsUsingAnySegment(level, overloadedSegments, "overload");
    }

    private static Map<FiberSegmentKey, Double> loadByFiberSegment(
            FiberNetworkSnapshot snapshot,
            CompiledPortGraph graph,
            ScalarPowerSolution solution
    ) {
        Map<FiberSegmentKey, Double> loadBySegment = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            if (!isFiberTransferEdge(snapshot, edge)) {
                continue;
            }

            double transferredPower = transferredPower(edge, solution);

            if (transferredPower <= 0.0D) {
                continue;
            }

            List<FiberRoute> routes = FiberOpticalTransfer.directEndpointRoutes(snapshot, edge.from().pos(), edge.to().pos());

            if (routes.isEmpty()) {
                continue;
            }

            double routePower = transferredPower / routes.size();

            for (FiberRoute route : routes) {
                for (FiberSegment segment : route.segments()) {
                    loadBySegment.merge(FiberSegmentKey.of(segment.from(), segment.to()), routePower, Double::sum);
                }
            }
        }

        return loadBySegment;
    }

    private static double transferredPower(PortGraphEdge edge, ScalarPowerSolution solution) {
        double power = 0.0D;

        for (SpectralPowerLane lane : solution.powerByLane().keySet()) {
            power += solution.powerAt(edge.from(), lane) * edge.sampleGainFor(lane.frequency());
        }

        if (power > 0.0D) {
            return power;
        }

        return solution.powerAt(edge.from()) * edge.sampleGain();
    }

    private static boolean isFiberTransferEdge(FiberNetworkSnapshot snapshot, PortGraphEdge edge) {
        if (edge.kind() != PortGraphEdgeKind.LOCAL_SCATTERING
                || edge.from().waveKind() != PortWaveKind.INCOMING
                || edge.to().waveKind() != PortWaveKind.OUTGOING
                || edge.from().pos().equals(edge.to().pos())) {
            return false;
        }

        FiberNode fromNode = snapshot.nodeAt(edge.from().pos()).orElse(null);
        FiberNode toNode = snapshot.nodeAt(edge.to().pos()).orElse(null);

        return fromNode != null
                && toNode != null
                && fromNode.kind() == FiberNodeKind.INTERFACE
                && toNode.kind() == FiberNodeKind.INTERFACE;
    }

    private static String formatOverload(FiberSegmentKey segment, double load, double capacity, int usage) {
        return formatPos(segment.firstPos()) + "<->" + formatPos(segment.secondPos())
                + " load=" + String.format(java.util.Locale.ROOT, "%.3f", load)
                + " cap=" + String.format(java.util.Locale.ROOT, "%.3f", capacity)
                + " fibers=" + usage;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private FiberOverloadMonitor() {
    }
}
