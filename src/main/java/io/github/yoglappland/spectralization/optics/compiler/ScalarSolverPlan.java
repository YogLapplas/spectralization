package io.github.yoglappland.spectralization.optics.compiler;

import java.util.List;
import java.util.Objects;

public record ScalarSolverPlan(
        ScalarSolverKind primarySolverKind,
        List<ScalarSolverRegion> regions,
        int dagRegionCount,
        int feedbackRegionCount,
        int fallbackRegionCount,
        int maxBeta1,
        ChordFeedbackPlan chordFeedbackPlan
) {
    public ScalarSolverPlan {
        Objects.requireNonNull(primarySolverKind, "primarySolverKind");
        Objects.requireNonNull(regions, "regions");
        Objects.requireNonNull(chordFeedbackPlan, "chordFeedbackPlan");

        if (dagRegionCount < 0 || feedbackRegionCount < 0 || fallbackRegionCount < 0 || maxBeta1 < 0) {
            throw new IllegalArgumentException("Solver plan counters must be non-negative");
        }

        regions = List.copyOf(regions);
    }

    public static ScalarSolverPlan empty() {
        return new ScalarSolverPlan(ScalarSolverKind.NONE, List.of(), 0, 0, 0, 0, ChordFeedbackPlan.empty());
    }

    public boolean acyclic() {
        return feedbackRegionCount == 0 && maxBeta1 == 0;
    }
}
