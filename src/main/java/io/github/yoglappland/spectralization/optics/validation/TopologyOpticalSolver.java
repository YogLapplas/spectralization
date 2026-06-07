package io.github.yoglappland.spectralization.optics.validation;

import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CompiledOpticalNetwork;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalNetworkCompiler;
import io.github.yoglappland.spectralization.optics.OpticalPort;
import io.github.yoglappland.spectralization.optics.OpticalResult;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldEffectType;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldInfluence;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class TopologyOpticalSolver {
    private static final int MAX_SEGMENTS = 128;
    private static final int MAX_STATES = 2048;
    private static final double MIN_POWER = 0.01;
    private static final double AIR_PROPAGATION_FACTOR = 0.995;

    public static TopologyOpticalTrace solve(Level level, BlockPos sourcePos, OutputBeam sourceOutput) {
        TopologyOpticalTrace.Builder trace = new TopologyOpticalTrace.Builder();

        if (level.isClientSide || sourceOutput.beam().isEmpty()) {
            return trace.build();
        }

        Queue<TravelState> pending = new ArrayDeque<>();
        Direction direction = sourceOutput.outgoingDirection();

        pending.add(new TravelState(
                sourcePos.relative(direction),
                direction,
                sourceOutput.beam().withDirection(direction),
                1
        ));

        while (!pending.isEmpty() && trace.processedStates() < MAX_STATES) {
            TravelState current = pending.remove();
            scanReadablePath(level, pending, current, trace);
        }

        return trace.build();
    }

    private static void scanReadablePath(
            Level level,
            Queue<TravelState> pending,
            TravelState initialState,
            TopologyOpticalTrace.Builder trace
    ) {
        BlockPos pos = initialState.pos;
        Direction direction = initialState.direction;
        BeamPacket beam = initialState.beam;
        int segments = initialState.segments;

        while (segments <= MAX_SEGMENTS && trace.processedStates() < MAX_STATES) {
            trace.incrementProcessedStates();

            if (!level.isLoaded(pos)) {
                return;
            }

            beam = beam.withDirection(direction).scalePower(propagationFactor(level, pos));

            if (beam.totalPower() < MIN_POWER) {
                return;
            }

            BlockState state = level.getBlockState(pos);
            boolean affectedAir = state.isAir()
                    && OpticalFieldSources.hasEffect(level, pos, OpticalFieldEffectType.SCATTERING);

            if (affectedAir) {
                trace.addAffectedAirPower(pos, beam.totalPower());
                pos = pos.relative(direction);
                segments++;
                continue;
            }

            if (OpticalMaterialProfiles.isAirLike(state)) {
                pos = pos.relative(direction);
                segments++;
                continue;
            }

            if (!isReadableBlock(state)) {
                return;
            }

            Direction incomingDirection = direction.getOpposite();
            trace.addIncidentPower(new OpticalPort(pos, incomingDirection), beam.totalPower());

            CompiledOpticalNetwork compiledNetwork = OpticalNetworkCompiler.compile(level, pos, state);
            OpticalResult result = compiledNetwork.scatterWithoutEffects(beam, incomingDirection);

            for (OutputBeam output : result.outputs()) {
                if (!output.beam().isEmpty() && output.beam().totalPower() >= MIN_POWER) {
                    pending.add(new TravelState(
                            pos.relative(output.outgoingDirection()),
                            output.outgoingDirection(),
                            output.beam(),
                            segments + 1
                    ));
                }
            }

            return;
        }
    }

    private static boolean isReadableBlock(BlockState state) {
        Block block = state.getBlock();

        return block instanceof OpticalElement
                || OpticalMaterialProfiles.isExplicitOpticalMaterial(state)
                || OpticalFieldSources.isScatteringFieldSource(state);
    }

    private static double propagationFactor(Level level, BlockPos pos) {
        OpticalFieldInfluence fieldInfluence = OpticalFieldSources.influenceAt(level, pos);

        return fieldInfluence.has(OpticalFieldEffectType.SCATTERING)
                ? fieldInfluence.propagationFactor()
                : AIR_PROPAGATION_FACTOR;
    }

    private record TravelState(
            BlockPos pos,
            Direction direction,
            BeamPacket beam,
            int segments
    ) {
        private TravelState {
            pos = pos.immutable();
        }
    }

    private TopologyOpticalSolver() {
    }
}
