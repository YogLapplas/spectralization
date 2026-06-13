package io.github.yoglappland.spectralization.optics.fiber;

import java.util.Objects;
import net.minecraft.core.BlockPos;

public record FiberCandidateEdge(
        FiberNode from,
        FiberNode to,
        double length
) {
    public FiberCandidateEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (from.pos().equals(to.pos())) {
            throw new IllegalArgumentException("Fiber edge endpoints must differ");
        }

        if (!Double.isFinite(length) || length <= 0.0) {
            throw new IllegalArgumentException("Fiber edge length must be finite and positive");
        }
    }

    public BlockPos other(BlockPos pos) {
        if (from.pos().equals(pos)) {
            return to.pos();
        }

        if (to.pos().equals(pos)) {
            return from.pos();
        }

        throw new IllegalArgumentException("Position is not on this fiber edge");
    }
}
