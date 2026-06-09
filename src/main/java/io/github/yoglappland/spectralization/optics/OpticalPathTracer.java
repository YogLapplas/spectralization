package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.optics.field.OpticalFieldEffectType;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.geometry.SpatialModeCoupling;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalPathTracer {
    private static final int MAX_SEGMENTS = 128;
    private static final int MAX_STATES_PER_TRACE = 2048;
    private static final double MIN_POWER = 0.01;

    public static CompiledOpticalTrace trace(Level level, BlockPos sourcePos, OutputBeam sourceOutput) {
        return trace(level, sourcePos, sourceOutput, MAX_STATES_PER_TRACE, true);
    }

    public static CompiledOpticalTrace traceEffects(Level level, BlockPos sourcePos, OutputBeam sourceOutput, int maxStates) {
        return trace(level, sourcePos, sourceOutput, maxStates, true);
    }

    public static CompiledOpticalTrace traceForDebug(Level level, BlockPos sourcePos, OutputBeam sourceOutput, int maxStates) {
        return trace(level, sourcePos, sourceOutput, maxStates, false);
    }

    private static CompiledOpticalTrace trace(
            Level level,
            BlockPos sourcePos,
            OutputBeam sourceOutput,
            int maxStates,
            boolean applyWorldEffects
    ) {
        CompiledOpticalTrace.Builder trace = CompiledOpticalTrace.builder(sourcePos, sourceOutput);

        if (level.isClientSide || sourceOutput.beam().isEmpty()) {
            return trace.build();
        }

        Queue<TravelState> pending = new ArrayDeque<>();
        Set<Integer> affectedEntityIds = new HashSet<>();
        Direction direction = sourceOutput.outgoingDirection();
        BlockPos firstPos = sourcePos.relative(direction);

        pending.add(new TravelState(
                firstPos,
                direction,
                propagate(level, firstPos, sourceOutput.beam().withDirection(direction)),
                1
        ));

        int processedStates = 0;

        while (!pending.isEmpty()) {
            TravelState current = pending.remove();

            processedStates++;

            if (processedStates > Math.max(0, maxStates)) {
                trace.addTermination(termination(current, OpticalTraceTerminationReason.MAX_STATES));
                break;
            }

            if (current.segments > MAX_SEGMENTS) {
                trace.addTermination(termination(current, OpticalTraceTerminationReason.MAX_SEGMENTS));
                continue;
            }

            if (current.beam.totalPower() < MIN_POWER) {
                trace.addTermination(termination(current, OpticalTraceTerminationReason.LOW_POWER));
                continue;
            }

            if (!level.isLoaded(current.pos)) {
                trace.addTermination(termination(current, OpticalTraceTerminationReason.UNLOADED_CHUNK));
                continue;
            }

            if (applyWorldEffects) {
                OpticalPathExposure.mark(level, current.pos, current.beam);
            }

            if (applyWorldEffects && OpticalFieldSources.hasEffect(level, current.pos, OpticalFieldEffectType.SCATTERING)) {
                OpticalPathVisualization.spawn(level, current.pos, current.beam);
            }

            BeamPacket interactingBeam = applyWorldEffects
                    ? OpticalEntityInteractions.interact(
                    level,
                    current.pos,
                    current.direction,
                    current.beam,
                    affectedEntityIds
            )
                    : current.beam;

            if (interactingBeam.totalPower() < MIN_POWER) {
                trace.addTermination(termination(current, interactingBeam, OpticalTraceTerminationReason.LOW_POWER));
                continue;
            }

            BlockState state = level.getBlockState(current.pos);
            Block block = state.getBlock();
            Direction incomingDirection = current.direction.getOpposite();
            boolean opticalElement = block instanceof OpticalElement;
            OpticalInteractionKind interactionKind = interactionKind(state, opticalElement);

            if (applyWorldEffects && !opticalElement && !state.isAir()) {
                OpticalSpotTracker.markMaterialSpot(
                        level,
                        current.pos,
                        incomingDirection,
                        interactingBeam,
                        state
                );
            }

            CompiledOpticalNetwork compiledNetwork = OpticalNetworkCompiler.compile(level, current.pos, state);
            OpticalResult result = compiledNetwork.interact(interactingBeam, incomingDirection);

            trace.addStep(new OpticalTraceStep(
                    current.pos,
                    current.direction,
                    incomingDirection,
                    interactionKind,
                    current.beam,
                    interactingBeam,
                    result
            ));

            if (applyWorldEffects && opticalElement) {
                OpticalSpotTracker.markAbsorbedSpot(
                        level,
                        current.pos,
                        incomingDirection,
                        state,
                        interactingBeam,
                        result.absorbedPower()
                );
            }

            enqueueOutputs(level, pending, current.pos, result, current.segments);
        }

        return trace.build();
    }

    private static OpticalTraceTermination termination(
            TravelState state,
            OpticalTraceTerminationReason reason
    ) {
        return termination(state, state.beam, reason);
    }

    private static OpticalTraceTermination termination(
            TravelState state,
            BeamPacket beam,
            OpticalTraceTerminationReason reason
    ) {
        return new OpticalTraceTermination(
                state.pos,
                state.direction,
                state.direction.getOpposite(),
                beam,
                reason
        );
    }

    private static OpticalInteractionKind interactionKind(BlockState state, boolean opticalElement) {
        if (opticalElement) {
            return OpticalInteractionKind.OPTICAL_ELEMENT;
        }

        return state.isAir() ? OpticalInteractionKind.AIR : OpticalInteractionKind.MATERIAL;
    }

    private static void enqueueOutputs(
            Level level,
            Queue<TravelState> pending,
            BlockPos pos,
            OpticalResult result,
            int segments
    ) {
        for (OutputBeam output : result.outputs()) {
            if (!output.beam().isEmpty() && output.beam().totalPower() >= MIN_POWER) {
                enqueueNext(
                        level,
                        pending,
                        pos,
                        output.outgoingDirection(),
                        output.beam(),
                        segments
                );
            }
        }
    }

    private static void enqueueNext(
            Level level,
            Queue<TravelState> pending,
            BlockPos pos,
            Direction direction,
            BeamPacket beam,
            int segments
    ) {
        BlockPos nextPos = pos.relative(direction);

        pending.add(new TravelState(
                nextPos,
                direction,
                propagate(level, nextPos, beam.withDirection(direction)),
                segments + 1
        ));
    }

    private static BeamPacket propagate(Level level, BlockPos pos, BeamPacket beam) {
        SpatialModeCoupling coupling = BeamGeometryOps.passivePropagationCoupling(beam.envelope(), 1.0);
        BeamPacket spatialBeam = beam.applySpatialCoupling(coupling);
        return spatialBeam.scalePower(OpticalPropagationLoss.factor(level, pos, spatialBeam));
    }

    private record TravelState(
            BlockPos pos,
            Direction direction,
            BeamPacket beam,
            int segments
    ) {
    }

    private OpticalPathTracer() {
    }
}
