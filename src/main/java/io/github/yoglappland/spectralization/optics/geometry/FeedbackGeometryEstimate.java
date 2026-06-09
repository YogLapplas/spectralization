package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import java.util.Objects;

public record FeedbackGeometryEstimate(
        int sccId,
        FeedbackGeometryState state,
        BeamEnvelope cavityEnvelope,
        double modeCouplingEfficiency,
        double strayFraction,
        double stabilityMargin
) {
    public FeedbackGeometryEstimate {
        if (sccId < 0) {
            throw new IllegalArgumentException("Feedback geometry SCC id must be non-negative");
        }

        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(cavityEnvelope, "cavityEnvelope");

        if (!Double.isFinite(modeCouplingEfficiency) || modeCouplingEfficiency < 0.0 || modeCouplingEfficiency > 1.0) {
            throw new IllegalArgumentException("Mode coupling efficiency must be finite and between 0 and 1");
        }

        if (!Double.isFinite(strayFraction) || strayFraction < 0.0 || strayFraction > 1.0) {
            throw new IllegalArgumentException("Stray fraction must be finite and between 0 and 1");
        }

        if (!Double.isFinite(stabilityMargin)) {
            throw new IllegalArgumentException("Feedback geometry stability margin must be finite");
        }
    }

    public static FeedbackGeometryEstimate unresolved(int sccId) {
        return new FeedbackGeometryEstimate(
                sccId,
                FeedbackGeometryState.UNRESOLVED,
                BeamEnvelope.DEFAULT_COLLIMATED,
                0.0,
                1.0,
                0.0
        );
    }
}
