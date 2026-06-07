package io.github.yoglappland.spectralization.optics.topology;

public record OpticalNodeFlags(
        boolean possibleSource,
        boolean opticalElement,
        boolean materialNode
) {
}
