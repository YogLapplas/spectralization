package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Objects;

public record ChordFeedbackPlan(
        List<ChordFeedbackSystem> systems,
        int systemCount,
        int variableCount,
        int maxBeta1,
        boolean complete
) {
    public ChordFeedbackPlan {
        Objects.requireNonNull(systems, "systems");

        if (systemCount < 0 || variableCount < 0 || maxBeta1 < 0) {
            throw new IllegalArgumentException("Chord feedback plan counters must be non-negative");
        }

        systems = List.copyOf(systems);
    }

    public static ChordFeedbackPlan empty() {
        return new ChordFeedbackPlan(List.of(), 0, 0, 0, true);
    }
}
