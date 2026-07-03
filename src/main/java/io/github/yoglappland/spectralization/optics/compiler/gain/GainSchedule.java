package io.github.yoglappland.spectralization.optics.compiler.gain;

import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import java.util.List;
import java.util.Objects;

public record GainSchedule(
        CompiledPortGraph graph,
        boolean scheduled,
        String schedulerMode,
        int gainSourceCount,
        double totalGainHeadroom,
        double maxBaseGain,
        double maxEffectiveGain,
        List<GainSourceDebugInfo> gainSources
) {
    public GainSchedule {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(schedulerMode, "schedulerMode");
        Objects.requireNonNull(gainSources, "gainSources");
        gainSources = List.copyOf(gainSources);

        if (gainSourceCount < 0) {
            throw new IllegalArgumentException("Gain schedule source count must be non-negative");
        }

        if (!Double.isFinite(totalGainHeadroom) || totalGainHeadroom < 0.0) {
            throw new IllegalArgumentException("Gain schedule totalGainHeadroom must be finite and non-negative");
        }

        if (!Double.isFinite(maxBaseGain) || maxBaseGain < 0.0) {
            throw new IllegalArgumentException("Gain schedule maxBaseGain must be finite and non-negative");
        }

        if (!Double.isFinite(maxEffectiveGain) || maxEffectiveGain < 0.0) {
            throw new IllegalArgumentException("Gain schedule maxEffectiveGain must be finite and non-negative");
        }
    }

    public static GainSchedule none(CompiledPortGraph graph) {
        return new GainSchedule(graph, false, "none", 0, 0.0, 1.0, 1.0, List.of());
    }

    public record GainSourceDebugInfo(
            int edgeId,
            int sccId,
            int x,
            int y,
            int z,
            String materialId,
            String spectralScope,
            double materialWeight,
            double baseGain,
            double saturatedExtraOutput,
            int baseFrequencyCount,
            int saturatedFrequencyCount
    ) {
        public GainSourceDebugInfo {
            Objects.requireNonNull(materialId, "materialId");
            Objects.requireNonNull(spectralScope, "spectralScope");
        }
    }
}
