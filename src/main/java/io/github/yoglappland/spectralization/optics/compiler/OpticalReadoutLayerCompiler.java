package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.optics.OpticalPort;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutputKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalReadoutLayerCompiler {
    public static CompiledReadoutLayer compile(Level level, CompiledPortGraph graph) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(graph, "graph");

        List<OpticalReadoutBinding> bindings = new ArrayList<>();

        for (PortGraphNode node : graph.nodes()) {
            if (node.waveKind() != PortWaveKind.INCOMING || !level.isLoaded(node.pos())) {
                continue;
            }

            BlockState state = level.getBlockState(node.pos());

            if (state.getBlock() instanceof CmosSensorBlock) {
                Direction receivingSide = state.getValue(CmosSensorBlock.FACING).getOpposite();

                if (node.side() == receivingSide) {
                    bindings.add(new OpticalReadoutBinding(
                            node.pos(),
                            ReceiverOutputKind.CMOS,
                            node,
                            false
                    ));
                }

                continue;
            }

            if (state.getBlock() instanceof PassThroughSensorBlock) {
                addPassThroughSensorBinding(graph, state, node, bindings);
            }
        }

        if (bindings.isEmpty()) {
            return CompiledReadoutLayer.EMPTY;
        }

        return new CompiledReadoutLayer(bindings);
    }

    private static void addPassThroughSensorBinding(
            CompiledPortGraph graph,
            BlockState state,
            PortGraphNode inputNode,
            List<OpticalReadoutBinding> bindings
    ) {
        Direction positiveZDirection = state.getValue(PassThroughSensorBlock.FACING);
        Direction negativeZDirection = positiveZDirection.getOpposite();
        Direction outgoingDirection = inputNode.side().getOpposite();

        if (outgoingDirection != positiveZDirection && outgoingDirection != negativeZDirection) {
            return;
        }

        PortGraphNode outputNode = PortGraphNode.outgoing(new OpticalPort(inputNode.pos(), outgoingDirection));

        if (!hasPositiveLocalScattering(graph, inputNode, outputNode)) {
            return;
        }

        bindings.add(new OpticalReadoutBinding(
                inputNode.pos(),
                ReceiverOutputKind.PASS_THROUGH_SENSOR,
                inputNode,
                outgoingDirection == positiveZDirection
        ));
    }

    private static boolean hasPositiveLocalScattering(
            CompiledPortGraph graph,
            PortGraphNode from,
            PortGraphNode to
    ) {
        for (PortGraphEdge edge : graph.edges()) {
            if (edge.kind() == PortGraphEdgeKind.LOCAL_SCATTERING
                    && edge.from().equals(from)
                    && edge.to().equals(to)
                    && edge.sampleGain() > 0.0) {
                return true;
            }
        }

        return false;
    }

    private OpticalReadoutLayerCompiler() {
    }
}
