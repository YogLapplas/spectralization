package io.github.yoglappland.spectralization.optics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public final class CompiledOpticalTrace {
    private final BlockPos sourcePos;
    private final OutputBeam sourceOutput;
    private final List<OpticalTraceStep> steps;
    private final List<OpticalTraceTermination> terminations;
    private final List<OpticalFeedbackExpansion> feedbackExpansions;

    private CompiledOpticalTrace(
            BlockPos sourcePos,
            OutputBeam sourceOutput,
            List<OpticalTraceStep> steps,
            List<OpticalTraceTermination> terminations,
            List<OpticalFeedbackExpansion> feedbackExpansions
    ) {
        this.sourcePos = Objects.requireNonNull(sourcePos, "sourcePos").immutable();
        this.sourceOutput = Objects.requireNonNull(sourceOutput, "sourceOutput");
        this.steps = List.copyOf(steps);
        this.terminations = List.copyOf(terminations);
        this.feedbackExpansions = List.copyOf(feedbackExpansions);
    }

    public static Builder builder(BlockPos sourcePos, OutputBeam sourceOutput) {
        return new Builder(sourcePos, sourceOutput);
    }

    public BlockPos sourcePos() {
        return sourcePos;
    }

    public OutputBeam sourceOutput() {
        return sourceOutput;
    }

    public List<OpticalTraceStep> steps() {
        return steps;
    }

    public List<OpticalTraceTermination> terminations() {
        return terminations;
    }

    public List<OpticalFeedbackExpansion> feedbackExpansions() {
        return feedbackExpansions;
    }

    public int segmentCount() {
        return steps.size();
    }

    public double totalAbsorbedPower() {
        double absorbedPower = 0.0;

        for (OpticalTraceStep step : steps) {
            absorbedPower += step.result().absorbedPower();
        }

        return absorbedPower;
    }

    public List<OpticalPropagationEdge> propagationEdges() {
        return steps.stream()
                .map(OpticalTraceStep::propagationEdge)
                .toList();
    }

    public List<OpticalTransferEdge> transferEdges() {
        return steps.stream()
                .flatMap(step -> step.transferEdges().stream())
                .toList();
    }

    public static final class Builder {
        private final BlockPos sourcePos;
        private final OutputBeam sourceOutput;
        private final List<OpticalTraceStep> steps = new ArrayList<>();
        private final List<OpticalTraceTermination> terminations = new ArrayList<>();
        private final List<OpticalFeedbackExpansion> feedbackExpansions = new ArrayList<>();

        private Builder(BlockPos sourcePos, OutputBeam sourceOutput) {
            this.sourcePos = Objects.requireNonNull(sourcePos, "sourcePos").immutable();
            this.sourceOutput = Objects.requireNonNull(sourceOutput, "sourceOutput");
        }

        public Builder addStep(OpticalTraceStep step) {
            steps.add(Objects.requireNonNull(step, "step"));
            return this;
        }

        public Builder addTermination(OpticalTraceTermination termination) {
            terminations.add(Objects.requireNonNull(termination, "termination"));
            return this;
        }

        public Builder addFeedbackExpansion(OpticalFeedbackExpansion feedbackExpansion) {
            feedbackExpansions.add(Objects.requireNonNull(feedbackExpansion, "feedbackExpansion"));
            return this;
        }

        public CompiledOpticalTrace build() {
            return new CompiledOpticalTrace(sourcePos, sourceOutput, steps, terminations, feedbackExpansions);
        }
    }
}
