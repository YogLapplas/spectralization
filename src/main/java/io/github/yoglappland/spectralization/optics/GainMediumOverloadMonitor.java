package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdge;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphEdgeKind;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.PortWaveKind;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.optics.compiler.SpectralPowerLane;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class GainMediumOverloadMonitor {
    public static boolean burnOverloadedGainMedia(
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

        Map<BlockPos, MaterialLoad> loadsByPos = rubyMaterialLoads(level, graph, solution);
        boolean burned = false;

        for (Map.Entry<BlockPos, MaterialLoad> entry : loadsByPos.entrySet()) {
            BlockPos pos = entry.getKey();
            MaterialLoad load = entry.getValue();
            double limit = OpticalMaterialProfiles.rubyHandlingLimit();

            if (load.absorbedPower() <= limit || !level.getBlockState(pos).is(Spectralization.RUBY_BLOCK.get())) {
                continue;
            }

            Spectralization.LOGGER.info(
                    "Ruby overload in {} at {}: absorbed={} limit={} raw={} incoming={} outgoing={} absorption_max={}",
                    level.dimension().location(),
                    pos.toShortString(),
                    format(load.absorbedPower()),
                    format(limit),
                    format(load.rawPortLoad()),
                    format(load.incomingPower()),
                    format(load.outgoingPower()),
                    format(load.maxAbsorption())
            );
            SpectralDiagnostics.anomaly(level, "optics", "gain_medium_overload")
                    .pos("pos", pos)
                    .field("material", "ruby")
                    .field("load", load.absorbedPower())
                    .field("absorbed_load", load.absorbedPower())
                    .field("limit", limit)
                    .field("raw_port_load", load.rawPortLoad())
                    .field("incoming_power", load.incomingPower())
                    .field("outgoing_power", load.outgoingPower())
                    .field("max_absorption", load.maxAbsorption())
                    .field("solver", solution.solverKind())
                    .field("reliable", solution.reliableForReadout())
                    .write();
            level.destroyBlock(pos, false);
            burned = true;
        }

        return burned;
    }

    private static Map<BlockPos, MaterialLoad> rubyMaterialLoads(
            ServerLevel level,
            CompiledPortGraph graph,
            ScalarPowerSolution solution
    ) {
        Map<BlockPos, MaterialLoad> loadsByPos = rubyRawPortLoads(level, graph, solution);
        Map<PortGraphNode, BlockPos> rubyInputNodePositions = rubyInputNodePositions(level, graph);

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> laneEntry : solution.powerByLane().entrySet()) {
            SpectralPowerLane lane = laneEntry.getKey();

            for (Map.Entry<PortGraphNode, Double> powerEntry : laneEntry.getValue().entrySet()) {
                BlockPos pos = rubyInputNodePositions.get(powerEntry.getKey());

                if (pos == null || powerEntry.getValue() <= 0.0D) {
                    continue;
                }

                double absorption = rubyAbsorption(level, pos, lane.frequency());
                double absorbedPower = powerEntry.getValue() * absorption;
                MaterialLoad load = loadsByPos.getOrDefault(pos, MaterialLoad.ZERO);
                loadsByPos.put(pos, load.withAbsorbed(absorbedPower, absorption));
            }
        }

        if (!solution.powerByLane().isEmpty()) {
            return loadsByPos;
        }

        for (Map.Entry<PortGraphNode, BlockPos> entry : rubyInputNodePositions.entrySet()) {
            double power = solution.powerAt(entry.getKey());

            if (power <= 0.0D) {
                continue;
            }

            double absorption = rubyAbsorption(level, entry.getValue(), FrequencyKey.DEBUG_VISIBLE);
            double absorbedPower = power * absorption;
            MaterialLoad load = loadsByPos.getOrDefault(entry.getValue(), MaterialLoad.ZERO);
            loadsByPos.put(entry.getValue(), load.withAbsorbed(absorbedPower, absorption));
        }

        return loadsByPos;
    }

    private static Map<BlockPos, MaterialLoad> rubyRawPortLoads(
            ServerLevel level,
            CompiledPortGraph graph,
            ScalarPowerSolution solution
    ) {
        Map<BlockPos, MaterialLoad> loadsByPos = new HashMap<>();

        for (PortGraphNode node : graph.nodes()) {
            if (!isLoadedRuby(level, node.pos())) {
                continue;
            }

            double power = solution.powerAt(node);

            if (power <= 0.0D) {
                continue;
            }

            BlockPos pos = node.pos().immutable();
            MaterialLoad load = loadsByPos.getOrDefault(pos, MaterialLoad.ZERO);
            loadsByPos.put(pos, node.waveKind() == PortWaveKind.INCOMING
                    ? load.withIncoming(power)
                    : load.withOutgoing(power));
        }

        return loadsByPos;
    }

    private static Map<PortGraphNode, BlockPos> rubyInputNodePositions(ServerLevel level, CompiledPortGraph graph) {
        Map<PortGraphNode, BlockPos> inputNodePositions = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            if (!isRubyLocalMaterialEdge(level, edge)) {
                continue;
            }

            inputNodePositions.put(edge.from(), edge.from().pos().immutable());
        }

        return inputNodePositions;
    }

    private static boolean isRubyLocalMaterialEdge(ServerLevel level, PortGraphEdge edge) {
        return edge.kind() == PortGraphEdgeKind.LOCAL_SCATTERING
                && edge.from().waveKind() == PortWaveKind.INCOMING
                && edge.to().waveKind() == PortWaveKind.OUTGOING
                && edge.from().pos().equals(edge.to().pos())
                && isLoadedRuby(level, edge.from().pos());
    }

    private static boolean isLoadedRuby(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).is(Spectralization.RUBY_BLOCK.get());
    }

    private static double rubyAbsorption(ServerLevel level, BlockPos pos, FrequencyKey frequency) {
        BlockState state = level.getBlockState(pos);
        return OpticalMaterialProfiles.profileFor(level, pos, state).responseAt(frequency).absorption();
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private record MaterialLoad(
            double incomingPower,
            double outgoingPower,
            double absorbedPower,
            double maxAbsorption
    ) {
        private static final MaterialLoad ZERO = new MaterialLoad(0.0D, 0.0D, 0.0D, 0.0D);

        private double rawPortLoad() {
            return Math.max(incomingPower, outgoingPower);
        }

        private MaterialLoad withIncoming(double power) {
            return new MaterialLoad(
                    incomingPower + Math.max(0.0D, power),
                    outgoingPower,
                    absorbedPower,
                    maxAbsorption
            );
        }

        private MaterialLoad withOutgoing(double power) {
            return new MaterialLoad(
                    incomingPower,
                    outgoingPower + Math.max(0.0D, power),
                    absorbedPower,
                    maxAbsorption
            );
        }

        private MaterialLoad withAbsorbed(double power, double absorption) {
            return new MaterialLoad(
                    incomingPower,
                    outgoingPower,
                    absorbedPower + Math.max(0.0D, power),
                    Math.max(maxAbsorption, Math.max(0.0D, absorption))
            );
        }
    }

    private GainMediumOverloadMonitor() {
    }
}
