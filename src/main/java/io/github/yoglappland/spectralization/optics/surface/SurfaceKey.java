package io.github.yoglappland.spectralization.optics.surface;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SurfaceKey(BlockPos pos, Direction side) {
    public SurfaceKey {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(side, "side");
    }

    public SurfaceKey neighborKey() {
        return new SurfaceKey(pos.relative(side), side.getOpposite());
    }
}
