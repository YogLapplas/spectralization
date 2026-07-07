package io.github.yoglappland.spectralization.optics.projection;

import net.minecraft.core.Direction;

public final class SpotSurfaceFrame {
    public static Direction uDirection(Direction face) {
        return switch (face) {
            case NORTH -> Direction.EAST;
            case SOUTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case WEST -> Direction.NORTH;
            case UP, DOWN -> Direction.EAST;
        };
    }

    public static Direction vDirection(Direction face) {
        return switch (face) {
            case NORTH, SOUTH, EAST, WEST -> Direction.UP;
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
        };
    }

    public static LocalCoordinates surfaceLocal(Direction face, double localU, double localV, double offset) {
        double x = 0.5D;
        double y = 0.5D;
        double z = 0.5D;

        switch (face.getAxis()) {
            case X -> x = face == Direction.EAST ? 1.0D + offset : -offset;
            case Y -> y = face == Direction.UP ? 1.0D + offset : -offset;
            case Z -> z = face == Direction.SOUTH ? 1.0D + offset : -offset;
        }

        Direction uDirection = uDirection(face);
        Direction vDirection = vDirection(face);

        switch (uDirection.getAxis()) {
            case X -> x = axisLocal(uDirection, localU);
            case Y -> y = axisLocal(uDirection, localU);
            case Z -> z = axisLocal(uDirection, localU);
        }

        switch (vDirection.getAxis()) {
            case X -> x = axisLocal(vDirection, localV);
            case Y -> y = axisLocal(vDirection, localV);
            case Z -> z = axisLocal(vDirection, localV);
        }

        return new LocalCoordinates(x, y, z);
    }

    public static double axisLocal(Direction direction, double coordinate) {
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? coordinate
                : 1.0D - coordinate;
    }

    public record LocalCoordinates(double x, double y, double z) {
    }

    private SpotSurfaceFrame() {
    }
}
