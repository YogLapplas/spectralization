package io.github.yoglappland.spectralization.command;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

record SpotTestLayout(BlockPos source, Direction direction) {
    private static final int PARALLEL_SOURCE_SPACING = 2;
    private static final int[][] PARALLEL_SOURCE_GRID = {
            {0, 0},
            {-1, 0}, {1, 0},
            {0, -1}, {0, 1},
            {-1, -1}, {1, -1}, {-1, 1}, {1, 1}
    };

    static SpotTestLayout inFrontOf(ServerPlayer player) {
        Direction direction = player.getDirection();
        if (!direction.getAxis().isHorizontal()) {
            direction = Direction.SOUTH;
        }
        return new SpotTestLayout(
                player.blockPosition().relative(direction, 4).above(4),
                direction
        );
    }

    Direction lateral() {
        return direction.getClockWise();
    }

    Direction relativeHorizontal(int quarterTurnsClockwise) {
        return switch (Math.floorMod(quarterTurnsClockwise, 4)) {
            case 0 -> direction;
            case 1 -> lateral();
            case 2 -> direction.getOpposite();
            default -> lateral().getOpposite();
        };
    }

    SpotTestLayout withDirection(Direction newDirection) {
        if (newDirection == null || !newDirection.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Spot test direction must be horizontal");
        }
        return new SpotTestLayout(source, newDirection);
    }

    List<SpotTestLayout> parallelSources(int sourceCount) {
        if (sourceCount < 1 || sourceCount > PARALLEL_SOURCE_GRID.length) {
            throw new IllegalArgumentException("Parallel spot-test source count must be between 1 and 9");
        }
        List<SpotTestLayout> layouts = new ArrayList<>(sourceCount);
        for (int index = 0; index < sourceCount; index++) {
            int[] offset = PARALLEL_SOURCE_GRID[index];
            layouts.add(new SpotTestLayout(
                    source.relative(lateral(), offset[0] * PARALLEL_SOURCE_SPACING)
                            .above(offset[1] * PARALLEL_SOURCE_SPACING),
                    direction
            ));
        }
        return List.copyOf(layouts);
    }

    BlockPos at(int along, int side, int vertical) {
        return source.relative(direction, along).relative(lateral(), side).above(vertical);
    }
}
