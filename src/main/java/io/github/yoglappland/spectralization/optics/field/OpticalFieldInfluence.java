package io.github.yoglappland.spectralization.optics.field;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record OpticalFieldInfluence(
        Set<OpticalFieldEffectType> effects,
        double propagationFactor
) {
    public static final OpticalFieldInfluence NONE = new OpticalFieldInfluence(Set.of(), 1.0D);

    public OpticalFieldInfluence {
        Objects.requireNonNull(effects, "effects");
        effects = effects.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(effects));

        if (!Double.isFinite(propagationFactor) || propagationFactor < 0.0D) {
            throw new IllegalArgumentException("Optical field propagation factor must be finite and non-negative");
        }
    }

    public static OpticalFieldInfluence scattering(double propagationFactor) {
        return new OpticalFieldInfluence(EnumSet.of(OpticalFieldEffectType.SCATTERING), propagationFactor);
    }

    public boolean has(OpticalFieldEffectType effectType) {
        return effects.contains(effectType);
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }
}
