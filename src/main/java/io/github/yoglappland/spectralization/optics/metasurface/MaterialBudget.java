package io.github.yoglappland.spectralization.optics.metasurface;

import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record MaterialBudget(Map<ResourceLocation, Double> amounts) {
    public MaterialBudget {
        Objects.requireNonNull(amounts, "amounts");
        amounts = Map.copyOf(amounts);

        for (Map.Entry<ResourceLocation, Double> entry : amounts.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "material id");
            Double amount = Objects.requireNonNull(entry.getValue(), "material amount");

            if (!Double.isFinite(amount) || amount < 0.0) {
                throw new IllegalArgumentException("Material amount must be finite and non-negative");
            }
        }
    }
}
