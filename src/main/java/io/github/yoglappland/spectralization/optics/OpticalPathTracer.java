package io.github.yoglappland.spectralization.optics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalPathTracer {
    private static final int MAX_SEGMENTS = 128;
    private static final int SCATTER_VISUALIZATION_RADIUS = 2;
    private static final double MIN_POWER = 0.01;
    private static final double AIR_PROPAGATION_FACTOR = 0.995;
    private static final double SCATTER_PROPAGATION_FACTOR = 0.82;

    public static void trace(Level level, BlockPos sourcePos, OutputBeam sourceOutput) {
        if (level.isClientSide || sourceOutput.beam().isEmpty()) {
            return;
        }

        Queue<TravelState> pending = new ArrayDeque<>();
        Set<VisitKey> visited = new HashSet<>();
        Set<Integer> affectedEntityIds = new HashSet<>();
        Direction direction = sourceOutput.outgoingDirection();
        BlockPos firstPos = sourcePos.relative(direction);

        pending.add(new TravelState(
                firstPos,
                direction,
                sourceOutput.beam().withDirection(direction).scalePower(propagationFactor(level, firstPos)),
                1
        ));

        while (!pending.isEmpty()) {
            TravelState current = pending.remove();

            if (current.segments > MAX_SEGMENTS || current.beam.totalPower() < MIN_POWER || !level.isLoaded(current.pos)) {
                continue;
            }

            OpticalPathExposure.mark(level, current.pos, current.beam);

            if (isNearScatteringBlock(level, current.pos)) {
                OpticalPathVisualization.spawn(level, current.pos, current.beam);
            }

            BeamPacket interactingBeam = OpticalEntityInteractions.interact(
                    level,
                    current.pos,
                    current.direction,
                    current.beam,
                    affectedEntityIds
            );

            if (interactingBeam.totalPower() < MIN_POWER) {
                continue;
            }

            BlockState state = level.getBlockState(current.pos);
            Block block = state.getBlock();

            if (!(block instanceof OpticalElement opticalElement)) {
                if (!state.isAir()) {
                    Direction incomingDirection = current.direction.getOpposite();
                    VisitKey visitKey = VisitKey.from(current.pos, incomingDirection, interactingBeam);

                    if (!visited.add(visitKey)) {
                        continue;
                    }
                }

                enqueueOutputs(
                        level,
                        pending,
                        current.pos,
                        OpticalBlockProperties.interact(state, interactingBeam, current.direction),
                        current.segments
                );
                continue;
            }

            Direction incomingDirection = current.direction.getOpposite();
            VisitKey visitKey = VisitKey.from(current.pos, incomingDirection, interactingBeam);

            if (!visited.add(visitKey)) {
                continue;
            }

            OpticalResult result = opticalElement.interact(
                    interactingBeam,
                    incomingDirection,
                    state,
                    level,
                    current.pos
            );

            enqueueOutputs(level, pending, current.pos, result, current.segments);
        }
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
                enqueueNext(level, pending, pos, output.outgoingDirection(), output.beam(), segments);
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
                beam.withDirection(direction).scalePower(propagationFactor(level, nextPos)),
                segments + 1
        ));
    }

    private static double propagationFactor(Level level, BlockPos pos) {
        return isNearScatteringBlock(level, pos) ? SCATTER_PROPAGATION_FACTOR : AIR_PROPAGATION_FACTOR;
    }

    private static boolean isNearScatteringBlock(Level level, BlockPos center) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = -SCATTER_VISUALIZATION_RADIUS; x <= SCATTER_VISUALIZATION_RADIUS; x++) {
            for (int y = -SCATTER_VISUALIZATION_RADIUS; y <= SCATTER_VISUALIZATION_RADIUS; y++) {
                for (int z = -SCATTER_VISUALIZATION_RADIUS; z <= SCATTER_VISUALIZATION_RADIUS; z++) {
                    mutablePos.set(center.getX() + x, center.getY() + y, center.getZ() + z);

                    if (!level.isLoaded(mutablePos)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(mutablePos);

                    if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.GRAVEL)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static String profileKey(BeamPacket beam) {
        StringBuilder key = new StringBuilder();

        for (PlaneWaveComponent component : beam.components()) {
            key.append(component.frequency().id())
                    .append('/')
                    .append(component.coherence().name())
                    .append(';');
        }

        return key.toString();
    }

    private record TravelState(BlockPos pos, Direction direction, BeamPacket beam, int segments) {
    }

    private record VisitKey(BlockPos pos, Direction incomingDirection, String profileKey) {
        static VisitKey from(BlockPos pos, Direction incomingDirection, BeamPacket beam) {
            return new VisitKey(pos.immutable(), incomingDirection, OpticalPathTracer.profileKey(beam));
        }
    }

    private OpticalPathTracer() {
    }
}
