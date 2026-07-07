package io.github.yoglappland.spectralization.optics.projection;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SpotProjectionAllocation(
        BlockPos pos,
        Direction face,
        String kind,
        double candidateArea,
        double assignedArea,
        double emittedArea,
        double assignedPowerFraction,
        double emittedPowerFraction,
        int emittedQuads,
        String result
) {
    public SpotProjectionAllocation {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(result, "result");
        candidateArea = finiteNonNegative(candidateArea);
        assignedArea = finiteNonNegative(assignedArea);
        emittedArea = finiteNonNegative(emittedArea);
        assignedPowerFraction = finiteNonNegative(assignedPowerFraction);
        emittedPowerFraction = finiteNonNegative(emittedPowerFraction);
        emittedQuads = Math.max(0, emittedQuads);
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }
}
