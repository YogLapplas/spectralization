package io.github.yoglappland.spectralization.optics.compiler;

import java.util.Objects;

public record PortGraphChord(
        int id,
        int sccId,
        int edgeId,
        PortGraphNode from,
        PortGraphNode to
) {
    public PortGraphChord {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (id < 0) {
            throw new IllegalArgumentException("Chord id must be non-negative");
        }

        if (sccId < 0) {
            throw new IllegalArgumentException("Chord SCC id must be non-negative");
        }

        if (edgeId < 0) {
            throw new IllegalArgumentException("Chord edge id must be non-negative");
        }
    }
}
