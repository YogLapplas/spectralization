package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.OutputBeam;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record BeamProfileSource(BlockPos pos, OutputBeam outputBeam) {
    public BeamProfileSource {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(outputBeam, "outputBeam");
        pos = pos.immutable();
    }
}
