package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import java.util.Objects;

public record GainSchedule(
        CompiledPortGraph graph,
        boolean scheduled,
        boolean stable,
        String schedulerMode,
        double passiveRho,
        double rhoTarget,
        double rhoHard,
        double rhoBefore,
        double rhoAfter,
        int gainSourceCount,
        double totalGainHeadroom,
        double maxModeWeight,
        double maxSourceCap,
        double maxBaseGain,
        double maxEffectiveGain
) {
    public GainSchedule {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(schedulerMode, "schedulerMode");

        if (!Double.isFinite(passiveRho) || passiveRho < 0.0) {
            throw new IllegalArgumentException("Gain schedule passiveRho must be finite and non-negative");
        }

        if (!Double.isFinite(rhoTarget) || rhoTarget < 0.0) {
            throw new IllegalArgumentException("Gain schedule rhoTarget must be finite and non-negative");
        }

        if (!Double.isFinite(rhoHard) || rhoHard < 0.0) {
            throw new IllegalArgumentException("Gain schedule rhoHard must be finite and non-negative");
        }

        if (!Double.isFinite(rhoBefore) || rhoBefore < 0.0) {
            throw new IllegalArgumentException("Gain schedule rhoBefore must be finite and non-negative");
        }

        if (!Double.isFinite(rhoAfter) || rhoAfter < 0.0) {
            throw new IllegalArgumentException("Gain schedule rhoAfter must be finite and non-negative");
        }

        if (gainSourceCount < 0) {
            throw new IllegalArgumentException("Gain schedule source count must be non-negative");
        }

        if (!Double.isFinite(totalGainHeadroom) || totalGainHeadroom < 0.0) {
            throw new IllegalArgumentException("Gain schedule totalGainHeadroom must be finite and non-negative");
        }

        if (!Double.isFinite(maxModeWeight) || maxModeWeight < 0.0) {
            throw new IllegalArgumentException("Gain schedule maxModeWeight must be finite and non-negative");
        }

        if (!Double.isFinite(maxSourceCap) || maxSourceCap < 0.0) {
            throw new IllegalArgumentException("Gain schedule maxSourceCap must be finite and non-negative");
        }

        if (!Double.isFinite(maxBaseGain) || maxBaseGain < 0.0) {
            throw new IllegalArgumentException("Gain schedule maxBaseGain must be finite and non-negative");
        }

        if (!Double.isFinite(maxEffectiveGain) || maxEffectiveGain < 0.0) {
            throw new IllegalArgumentException("Gain schedule maxEffectiveGain must be finite and non-negative");
        }
    }

    public static GainSchedule none(CompiledPortGraph graph) {
        return new GainSchedule(graph, false, true, "none", 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0, 1.0, 1.0, 1.0);
    }
}
