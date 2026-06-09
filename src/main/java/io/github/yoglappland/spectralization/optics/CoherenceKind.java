package io.github.yoglappland.spectralization.optics;

public enum CoherenceKind {
    COHERENT,
    INCOHERENT;

    public boolean supportsResonance() {
        return this == COHERENT;
    }

    public boolean isStrayLike() {
        return this == INCOHERENT;
    }
}
