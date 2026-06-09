package io.github.yoglappland.spectralization.optics.metasurface;

import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record MetasurfaceTarget(ResourceLocation id, Map<MetasurfaceParameter, Double> targets) {
    public MetasurfaceTarget {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(targets, "targets");
        targets = Map.copyOf(targets);

        for (Map.Entry<MetasurfaceParameter, Double> entry : targets.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "target parameter");
            Double value = Objects.requireNonNull(entry.getValue(), "target value");

            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Target value must be finite");
            }
        }
    }
}
