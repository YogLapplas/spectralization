package io.github.yoglappland.spectralization.optics.fiber;

import java.util.Objects;
import net.minecraft.core.BlockPos;

public record FiberNode(
        BlockPos pos,
        FiberNodeKind kind,
        FiberNodeProfile profile
) {
    public FiberNode {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(profile, "profile");
        pos = pos.immutable();
    }
}
