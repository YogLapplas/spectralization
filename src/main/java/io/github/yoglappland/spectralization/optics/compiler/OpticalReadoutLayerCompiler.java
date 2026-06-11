package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.block.PhotothermalGeneratorBlock;
import io.github.yoglappland.spectralization.block.SpectrometerBlock;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutputKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalReadoutLayerCompiler {
    public static CompiledReadoutLayer compile(Level level, CompiledPortGraph graph) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(graph, "graph");

        List<OpticalReadoutBinding> bindings = new ArrayList<>();
        Set<BlockPos> cmosPositions = new HashSet<>();
        Set<BlockPos> boundCmosPositions = new HashSet<>();
        Set<BlockPos> beamProfilerPositions = new HashSet<>();
        Set<BlockPos> boundBeamProfilerPositions = new HashSet<>();
        Set<BlockPos> spectrometerPositions = new HashSet<>();
        Set<BlockPos> boundSpectrometerPositions = new HashSet<>();
        Set<BlockPos> photothermalGeneratorPositions = new HashSet<>();
        Set<BlockPos> boundPhotothermalGeneratorPositions = new HashSet<>();
        Map<BlockPos, PassThroughChannels> passThroughChannelsByPos = new HashMap<>();

        for (PortGraphNode node : graph.nodes()) {
            if (node.waveKind() != PortWaveKind.INCOMING || !level.isLoaded(node.pos())) {
                continue;
            }

            BlockState state = level.getBlockState(node.pos());

            if (state.getBlock() instanceof CmosSensorBlock) {
                cmosPositions.add(node.pos());
                Direction receivingSide = state.getValue(CmosSensorBlock.FACING).getOpposite();

                if (node.side() == receivingSide) {
                    boundCmosPositions.add(node.pos());
                    bindings.add(new OpticalReadoutBinding(
                            node.pos(),
                            ReceiverOutputKind.CMOS,
                            node,
                            false
                    ));
                }

                continue;
            }

            if (state.getBlock() instanceof BeamProfilerBlock) {
                beamProfilerPositions.add(node.pos());
                Direction receivingSide = BeamProfilerBlock.getReceivingSide(state);

                if (node.side() == receivingSide) {
                    boundBeamProfilerPositions.add(node.pos());
                    bindings.add(new OpticalReadoutBinding(
                            node.pos(),
                            ReceiverOutputKind.BEAM_PROFILER,
                            node,
                            false
                    ));
                }

                continue;
            }

            if (state.getBlock() instanceof SpectrometerBlock) {
                spectrometerPositions.add(node.pos());
                Direction receivingSide = SpectrometerBlock.getReceivingSide(state);

                if (node.side() == receivingSide) {
                    boundSpectrometerPositions.add(node.pos());
                    bindings.add(new OpticalReadoutBinding(
                            node.pos(),
                            ReceiverOutputKind.SPECTROMETER,
                            node,
                            false
                    ));
                }

                continue;
            }

            if (state.getBlock() instanceof PhotothermalGeneratorBlock) {
                photothermalGeneratorPositions.add(node.pos());
                Direction receivingSide = PhotothermalGeneratorBlock.getReceivingSide(state);

                if (node.side() == receivingSide) {
                    boundPhotothermalGeneratorPositions.add(node.pos());
                    bindings.add(new OpticalReadoutBinding(
                            node.pos(),
                            ReceiverOutputKind.PHOTOTHERMAL_GENERATOR,
                            node,
                            false
                    ));
                }

                continue;
            }

            if (state.getBlock() instanceof PassThroughSensorBlock) {
                passThroughChannelsByPos.computeIfAbsent(node.pos(), ignored -> new PassThroughChannels());
                addPassThroughSensorBinding(state, node, bindings, passThroughChannelsByPos);
            }
        }

        addZeroBindings(
                bindings,
                cmosPositions,
                boundCmosPositions,
                beamProfilerPositions,
                boundBeamProfilerPositions,
                spectrometerPositions,
                boundSpectrometerPositions,
                photothermalGeneratorPositions,
                boundPhotothermalGeneratorPositions,
                passThroughChannelsByPos
        );

        if (bindings.isEmpty()) {
            return CompiledReadoutLayer.EMPTY;
        }

        return new CompiledReadoutLayer(bindings);
    }

    private static void addPassThroughSensorBinding(
            BlockState state,
            PortGraphNode inputNode,
            List<OpticalReadoutBinding> bindings,
            Map<BlockPos, PassThroughChannels> passThroughChannelsByPos
    ) {
        Direction positiveZDirection = state.getValue(PassThroughSensorBlock.FACING);
        Direction negativeZDirection = positiveZDirection.getOpposite();
        Direction outgoingDirection = inputNode.side().getOpposite();

        if (outgoingDirection != positiveZDirection && outgoingDirection != negativeZDirection) {
            return;
        }

        boolean positiveZ = outgoingDirection == positiveZDirection;
        PassThroughChannels channels = passThroughChannelsByPos.computeIfAbsent(
                inputNode.pos(),
                ignored -> new PassThroughChannels()
        );
        channels.mark(positiveZ);
        bindings.add(new OpticalReadoutBinding(
                inputNode.pos(),
                ReceiverOutputKind.PASS_THROUGH_SENSOR,
                inputNode,
                positiveZ
        ));
    }

    private static void addZeroBindings(
            List<OpticalReadoutBinding> bindings,
            Set<BlockPos> cmosPositions,
            Set<BlockPos> boundCmosPositions,
            Set<BlockPos> beamProfilerPositions,
            Set<BlockPos> boundBeamProfilerPositions,
            Set<BlockPos> spectrometerPositions,
            Set<BlockPos> boundSpectrometerPositions,
            Set<BlockPos> photothermalGeneratorPositions,
            Set<BlockPos> boundPhotothermalGeneratorPositions,
            Map<BlockPos, PassThroughChannels> passThroughChannelsByPos
    ) {
        for (BlockPos pos : cmosPositions) {
            if (!boundCmosPositions.contains(pos)) {
                bindings.add(new OpticalReadoutBinding(
                        pos,
                        ReceiverOutputKind.CMOS,
                        null,
                        false
                ));
            }
        }

        for (BlockPos pos : beamProfilerPositions) {
            if (!boundBeamProfilerPositions.contains(pos)) {
                bindings.add(new OpticalReadoutBinding(
                        pos,
                        ReceiverOutputKind.BEAM_PROFILER,
                        null,
                        false
                ));
            }
        }

        for (BlockPos pos : spectrometerPositions) {
            if (!boundSpectrometerPositions.contains(pos)) {
                bindings.add(new OpticalReadoutBinding(
                        pos,
                        ReceiverOutputKind.SPECTROMETER,
                        null,
                        false
                ));
            }
        }

        for (BlockPos pos : photothermalGeneratorPositions) {
            if (!boundPhotothermalGeneratorPositions.contains(pos)) {
                bindings.add(new OpticalReadoutBinding(
                        pos,
                        ReceiverOutputKind.PHOTOTHERMAL_GENERATOR,
                        null,
                        false
                ));
            }
        }

        for (Map.Entry<BlockPos, PassThroughChannels> entry : passThroughChannelsByPos.entrySet()) {
            BlockPos pos = entry.getKey();
            PassThroughChannels channels = entry.getValue();

            if (!channels.positiveZ) {
                bindings.add(new OpticalReadoutBinding(
                        pos,
                        ReceiverOutputKind.PASS_THROUGH_SENSOR,
                        null,
                        true
                ));
            }

            if (!channels.negativeZ) {
                bindings.add(new OpticalReadoutBinding(
                        pos,
                        ReceiverOutputKind.PASS_THROUGH_SENSOR,
                        null,
                        false
                ));
            }
        }
    }

    private static final class PassThroughChannels {
        private boolean positiveZ;
        private boolean negativeZ;

        private void mark(boolean positiveZ) {
            if (positiveZ) {
                this.positiveZ = true;
            } else {
                this.negativeZ = true;
            }
        }
    }

    private OpticalReadoutLayerCompiler() {
    }
}
