package io.github.yoglappland.spectralization.microlizer;

import net.minecraft.core.Direction;

public final class MicrolizedMachineTransform {
    public static Direction localToWorld(Direction localDirection, Direction facing) {
        Direction horizontalFacing = horizontal(facing);

        return switch (localDirection) {
            case NORTH -> horizontalFacing;
            case SOUTH -> horizontalFacing.getOpposite();
            case EAST -> horizontalFacing.getClockWise();
            case WEST -> horizontalFacing.getCounterClockWise();
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    public static Direction worldToLocal(Direction worldDirection, Direction facing) {
        for (Direction localDirection : Direction.values()) {
            if (localToWorld(localDirection, facing) == worldDirection) {
                return localDirection;
            }
        }

        return worldDirection;
    }

    public static Direction rotateFacing(Direction facing, boolean clockwise) {
        Direction horizontalFacing = horizontal(facing);
        return clockwise ? horizontalFacing.getClockWise() : horizontalFacing.getCounterClockWise();
    }

    public static Direction horizontal(Direction facing) {
        if (facing == null || facing.getAxis().isVertical()) {
            return Direction.NORTH;
        }

        return facing;
    }

    private MicrolizedMachineTransform() {
    }
}
