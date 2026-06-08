package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalNetworkCompiler;
import io.github.yoglappland.spectralization.optics.OpticalReceiver;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.topology.OpticalTopologyProvider;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalLocalTopologyCompiler {
    public static OpticalLocalTopology compile(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction incomingDirection,
            BeamPacket inputBeam
    ) {
        CompiledOpticalNetwork compiledNetwork = OpticalNetworkCompiler.compile(level, pos, state);
        OpticalResult result = compiledNetwork.scatterWithoutEffects(inputBeam, incomingDirection);
        Set<Direction> outgoingDirections = outgoingDirections(level, pos, state, incomingDirection, result);
        Map<Direction, Double> outputPowerByDirection = outputPowerByDirection(result);
        OpticalComponentTuple component = componentTuple(pos, state, incomingDirection, outgoingDirections);
        OpticalComponentPort inputPort = portFor(component, incomingDirection);
        List<OpticalLocalScattering> scattering = new ArrayList<>();

        for (Direction outgoingDirection : outgoingDirections) {
            scattering.add(new OpticalLocalScattering(
                    inputPort,
                    portFor(component, outgoingDirection),
                    inputBeam.totalPower(),
                    outputPowerByDirection.getOrDefault(outgoingDirection, 0.0)
            ));
        }

        return new OpticalLocalTopology(component, scattering);
    }

    private static Map<Direction, Double> outputPowerByDirection(OpticalResult result) {
        Map<Direction, Double> outputPowerByDirection = new HashMap<>();

        for (OutputBeam outputBeam : result.outputs()) {
            outputPowerByDirection.merge(
                    outputBeam.outgoingDirection(),
                    outputBeam.beam().totalPower(),
                    Double::sum
            );
        }

        return outputPowerByDirection;
    }

    private static Set<Direction> outgoingDirections(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction incomingDirection,
            OpticalResult result
    ) {
        Set<Direction> outgoingDirections = EnumSet.noneOf(Direction.class);
        Block block = state.getBlock();

        if (block instanceof OpticalTopologyProvider topologyProvider) {
            outgoingDirections.addAll(topologyProvider.potentialOutgoingDirections(state, level, pos, incomingDirection));
        }

        for (OutputBeam outputBeam : result.outputs()) {
            outgoingDirections.add(outputBeam.outgoingDirection());
        }

        return outgoingDirections;
    }

    private static OpticalComponentTuple componentTuple(
            BlockPos pos,
            BlockState state,
            Direction incomingDirection,
            Set<Direction> outgoingDirections
    ) {
        OpticalComponentTemplateKind templateKind = templateKind(state);
        List<OpticalComponentPort> ports = new ArrayList<>();
        ports.add(portFor(pos, templateKind, incomingDirection));

        for (Direction outgoingDirection : outgoingDirections) {
            ports.add(portFor(pos, templateKind, outgoingDirection));
        }

        return new OpticalComponentTuple(pos, templateKind, ports);
    }

    private static OpticalComponentPort portFor(OpticalComponentTuple component, Direction direction) {
        return portFor(component.pos(), component.templateKind(), direction);
    }

    private static OpticalComponentPort portFor(
            BlockPos pos,
            OpticalComponentTemplateKind templateKind,
            Direction direction
    ) {
        if (templateKind == OpticalComponentTemplateKind.AXIS_MATERIAL) {
            return OpticalComponentPort.axis(pos, direction);
        }

        return OpticalComponentPort.face(pos, direction);
    }

    private static OpticalComponentTemplateKind templateKind(BlockState state) {
        Block block = state.getBlock();

        if (block instanceof OpticalSource) {
            return OpticalComponentTemplateKind.SOURCE_PORT;
        }

        if (block instanceof OpticalElement || block instanceof OpticalReceiver) {
            return OpticalComponentTemplateKind.FACE_PORT_ELEMENT;
        }

        if (OpticalMaterialProfiles.isExplicitOpticalMaterial(state)
                || OpticalMaterialProfiles.isScatteringMarker(state)) {
            return OpticalComponentTemplateKind.AXIS_MATERIAL;
        }

        return OpticalComponentTemplateKind.OPAQUE_TERMINATOR;
    }

    private OpticalLocalTopologyCompiler() {
    }
}
