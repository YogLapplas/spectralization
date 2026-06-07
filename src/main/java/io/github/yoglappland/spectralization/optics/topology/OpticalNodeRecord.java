package io.github.yoglappland.spectralization.optics.topology;

import java.util.Objects;
import net.minecraft.core.BlockPos;

public record OpticalNodeRecord(BlockPos pos, OpticalNodeFlags flags) {
    public OpticalNodeRecord {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(flags, "flags");
        pos = pos.immutable();
    }
}
