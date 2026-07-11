package io.github.yoglappland.spectralization.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

record SpotTestLayout(BlockPos source, Direction direction) {
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

    BlockPos at(int along, int side, int vertical) {
        return source.relative(direction, along).relative(lateral(), side).above(vertical);
    }
}
