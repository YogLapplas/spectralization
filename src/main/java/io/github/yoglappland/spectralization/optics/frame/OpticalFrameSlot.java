package io.github.yoglappland.spectralization.optics.frame;

import net.minecraft.core.Direction;

public enum OpticalFrameSlot {
    CORE,
    NORTH_PLATE,
    SOUTH_PLATE,
    EAST_PLATE,
    WEST_PLATE,
    UP_PLATE,
    DOWN_PLATE;

    public static OpticalFrameSlot plate(Direction side) {
        return switch (side) {
            case NORTH -> NORTH_PLATE;
            case SOUTH -> SOUTH_PLATE;
            case EAST -> EAST_PLATE;
            case WEST -> WEST_PLATE;
            case UP -> UP_PLATE;
            case DOWN -> DOWN_PLATE;
        };
    }
}
