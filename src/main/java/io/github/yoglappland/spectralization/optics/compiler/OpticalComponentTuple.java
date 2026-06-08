package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record OpticalComponentTuple(
        BlockPos pos,
        OpticalComponentTemplateKind templateKind,
        List<OpticalComponentPort> ports
) {
    public OpticalComponentTuple {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(templateKind, "templateKind");
        Objects.requireNonNull(ports, "ports");
        pos = pos.immutable();
        ports = List.copyOf(ports);
    }
}
