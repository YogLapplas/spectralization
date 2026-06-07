package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Objects;

public record ChordFeedbackSystem(
        int graphSccId,
        int nodeCount,
        int edgeCount,
        int beta1,
        List<ChordFeedbackVariable> variables,
        boolean compilable
) {
    public ChordFeedbackSystem {
        Objects.requireNonNull(variables, "variables");

        if (graphSccId < 0 || nodeCount < 0 || edgeCount < 0 || beta1 < 0) {
            throw new IllegalArgumentException("Chord feedback system counters must be non-negative");
        }

        variables = List.copyOf(variables);
    }

    public int variableCount() {
        return variables.size();
    }
}
