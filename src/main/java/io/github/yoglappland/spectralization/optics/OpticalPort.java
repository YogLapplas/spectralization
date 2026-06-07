package io.github.yoglappland.spectralization.optics;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record OpticalPort(BlockPos pos, Direction side) {
    public OpticalPort {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(side, "side");

        pos = pos.immutable();
    }
}
