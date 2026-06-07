package io.github.yoglappland.spectralization.optics.compiler;

import java.util.Objects;

public record ChordFeedbackVariable(
        int id,
        int graphSccId,
        int chordId,
        int edgeId,
        int fromGraphNodeId,
        int toGraphNodeId,
        int fromLocalNodeId,
        int toLocalNodeId,
        PortGraphNode from,
        PortGraphNode to,
        double gain
) {
    public ChordFeedbackVariable {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        if (id < 0
                || graphSccId < 0
                || chordId < 0
                || edgeId < 0
                || fromGraphNodeId < 0
                || toGraphNodeId < 0
                || fromLocalNodeId < 0
                || toLocalNodeId < 0) {
            throw new IllegalArgumentException("Chord feedback variable ids must be non-negative");
        }

        if (!Double.isFinite(gain) || gain < 0.0) {
            throw new IllegalArgumentException("Chord feedback variable gain must be finite and non-negative");
        }
    }
}
