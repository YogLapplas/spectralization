package io.github.yoglappland.spectralization.optics;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.Direction;

public final class HorizontalOpticalOrientation {
    public static Set<Direction> mirrorActiveSides(int rotation) {
        return switch (normalize(rotation)) {
            case 2, 6 -> EnumSet.of(Direction.EAST, Direction.WEST);
            case 1, 3, 5, 7 -> EnumSet.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
            default -> EnumSet.of(Direction.NORTH, Direction.SOUTH);
        };
    }

    public static Direction reflectMirror(int rotation, Direction incomingDirection) {
        return switch (normalize(rotation)) {
            case 1, 5 -> reflectNorthwestSoutheast(incomingDirection);
            case 2, 6 -> reflectNorthSouth(incomingDirection);
            case 3, 7 -> reflectNortheastSouthwest(incomingDirection);
            default -> reflectEastWest(incomingDirection);
        };
    }

    public static int rotationForHorizontalFacing(Direction direction) {
        return switch (direction) {
            case EAST -> 2;
            case SOUTH -> 4;
            case WEST -> 6;
            default -> 0;
        };
    }

    private static Direction reflectEastWest(Direction incomingDirection) {
        return switch (incomingDirection) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            default -> incomingDirection;
        };
    }

    private static Direction reflectNorthSouth(Direction incomingDirection) {
        return switch (incomingDirection) {
            case EAST -> Direction.EAST;
            case WEST -> Direction.WEST;
            default -> incomingDirection;
        };
    }

    private static Direction reflectNorthwestSoutheast(Direction incomingDirection) {
        return switch (incomingDirection) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            default -> incomingDirection;
        };
    }

    private static Direction reflectNortheastSouthwest(Direction incomingDirection) {
        return switch (incomingDirection) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            default -> incomingDirection;
        };
    }

    private static int normalize(int rotation) {
        return rotation & 7;
    }

    private HorizontalOpticalOrientation() {
    }
}
