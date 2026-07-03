package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ScalarPowerSolution(
        ScalarSolverKind solverKind,
        ScalarSolverPlan solverPlan,
        boolean converged,
        boolean unstable,
        int iterations,
        double residual,
        double maxNodePower,
        double totalNodePower,
        Map<PortGraphNode, Double> powerByNode,
        Map<PortGraphNode, Double> coherentPowerByNode,
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> powerByLane,
        List<ScalarSolverRegionResult> regionResults,
        boolean profileCollapsedFallback,
        boolean profileOverflow,
        ProfileSolverDiagnostics profileDiagnostics
) {
    public ScalarPowerSolution(
            ScalarSolverKind solverKind,
            ScalarSolverPlan solverPlan,
            boolean converged,
            boolean unstable,
            int iterations,
            double residual,
            double maxNodePower,
            double totalNodePower,
            Map<PortGraphNode, Double> powerByNode,
            Map<PortGraphNode, Double> coherentPowerByNode,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> powerByLane,
            List<ScalarSolverRegionResult> regionResults
    ) {
        this(
                solverKind,
                solverPlan,
                converged,
                unstable,
                iterations,
                residual,
                maxNodePower,
                totalNodePower,
                powerByNode,
                coherentPowerByNode,
                powerByLane,
                regionResults,
                false,
                false
        );
    }

    public ScalarPowerSolution(
            ScalarSolverKind solverKind,
            ScalarSolverPlan solverPlan,
            boolean converged,
            boolean unstable,
            int iterations,
            double residual,
            double maxNodePower,
            double totalNodePower,
            Map<PortGraphNode, Double> powerByNode,
            Map<PortGraphNode, Double> coherentPowerByNode,
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> powerByLane,
            List<ScalarSolverRegionResult> regionResults,
            boolean profileCollapsedFallback,
            boolean profileOverflow
    ) {
        this(
                solverKind,
                solverPlan,
                converged,
                unstable,
                iterations,
                residual,
                maxNodePower,
                totalNodePower,
                powerByNode,
                coherentPowerByNode,
                powerByLane,
                regionResults,
                profileCollapsedFallback,
                profileOverflow,
                ProfileSolverDiagnostics.none()
        );
    }

    public ScalarPowerSolution {
        Objects.requireNonNull(solverKind, "solverKind");
        Objects.requireNonNull(solverPlan, "solverPlan");
        Objects.requireNonNull(powerByNode, "powerByNode");
        Objects.requireNonNull(coherentPowerByNode, "coherentPowerByNode");
        Objects.requireNonNull(powerByLane, "powerByLane");
        Objects.requireNonNull(regionResults, "regionResults");
        Objects.requireNonNull(profileDiagnostics, "profileDiagnostics");

        if (iterations < 0) {
            throw new IllegalArgumentException("Power solution iterations must be non-negative");
        }

        if (!Double.isFinite(residual) || residual < 0.0) {
            throw new IllegalArgumentException("Power solution residual must be finite and non-negative");
        }

        if (!Double.isFinite(maxNodePower) || maxNodePower < 0.0) {
            throw new IllegalArgumentException("Power solution max node power must be finite and non-negative");
        }

        if (!Double.isFinite(totalNodePower) || totalNodePower < 0.0) {
            throw new IllegalArgumentException("Power solution total node power must be finite and non-negative");
        }

        powerByNode = Map.copyOf(powerByNode);
        coherentPowerByNode = Map.copyOf(coherentPowerByNode);
        powerByLane = copyPowerByLane(powerByLane);
        regionResults = List.copyOf(regionResults);
    }

    public static ScalarPowerSolution empty() {
        return ScalarPowerSolutions.empty(ScalarSolverKind.NONE, ScalarSolverPlan.empty());
    }

    public double powerAt(PortGraphNode node) {
        return powerByNode.getOrDefault(node, 0.0);
    }

    public double coherentPowerAt(PortGraphNode node) {
        double coherentPower = coherentPowerByNode.getOrDefault(node, 0.0);

        if (coherentPower > 0.0) {
            return coherentPower;
        }

        double lanePower = 0.0;

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : powerByLane.entrySet()) {
            if (entry.getKey().coherence() == CoherenceKind.COHERENT) {
                lanePower += entry.getValue().getOrDefault(node, 0.0);
            }
        }

        return lanePower;
    }

    public double powerAt(PortGraphNode node, SpectralPowerLane lane) {
        Map<PortGraphNode, Double> lanePowers = powerByLane.get(lane);
        return lanePowers == null ? 0.0 : lanePowers.getOrDefault(node, 0.0);
    }

    public Map<PortGraphNode, Double> powerByNodeForLane(SpectralPowerLane lane) {
        return powerByLane.getOrDefault(lane, Map.of());
    }

    public Map<PortGraphNode, Double> powerByNodeForSpectral(FrequencyKey frequency, CoherenceKind coherence) {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(coherence, "coherence");

        Map<PortGraphNode, Double> powers = new HashMap<>();

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : powerByLane.entrySet()) {
            SpectralPowerLane lane = entry.getKey();

            if (!lane.frequency().equals(frequency) || lane.coherence() != coherence) {
                continue;
            }

            for (Map.Entry<PortGraphNode, Double> powerEntry : entry.getValue().entrySet()) {
                if (powerEntry.getValue() > 0.0) {
                    powers.merge(powerEntry.getKey(), powerEntry.getValue(), Double::sum);
                }
            }
        }

        return powers.isEmpty() ? Map.of() : Map.copyOf(powers);
    }

    public Map<FrequencyKey, Double> powerByFrequencyAt(PortGraphNode node) {
        if (node == null) {
            return Map.of();
        }

        Map<FrequencyKey, Double> powers = new HashMap<>();

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : powerByLane.entrySet()) {
            double power = entry.getValue().getOrDefault(node, 0.0);

            if (power > 0.0) {
                powers.merge(entry.getKey().frequency(), power, Double::sum);
            }
        }

        return powers.isEmpty() ? Map.of() : Map.copyOf(powers);
    }

    public int mixedVisibleRgbAt(PortGraphNode node, CoherenceKind coherence, int fallbackRgb) {
        if (node == null) {
            return fallbackRgb;
        }

        List<SpectralColorMap.WeightedFrequency> frequencies = new ArrayList<>();

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : powerByLane.entrySet()) {
            SpectralPowerLane lane = entry.getKey();

            if (lane.coherence() != coherence) {
                continue;
            }

            double power = entry.getValue().getOrDefault(node, 0.0);

            if (power > 0.0) {
                frequencies.add(new SpectralColorMap.WeightedFrequency(lane.frequency(), power));
            }
        }

        return SpectralColorMap.mixVisibleRgb(frequencies, fallbackRgb);
    }

    public FrequencyKey strongestFrequencyAt(PortGraphNode node, CoherenceKind coherence) {
        FrequencyKey strongestFrequency = FrequencyKey.DEBUG_VISIBLE;
        double strongestPower = 0.0;

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : powerByLane.entrySet()) {
            SpectralPowerLane lane = entry.getKey();

            if (lane.coherence() != coherence) {
                continue;
            }

            double power = entry.getValue().getOrDefault(node, 0.0);

            if (power > strongestPower) {
                strongestPower = power;
                strongestFrequency = lane.frequency();
            }
        }

        return strongestFrequency;
    }

    public int strongestVisibleBinAt(PortGraphNode node, CoherenceKind coherence, int fallbackBin) {
        FrequencyKey frequency = strongestFrequencyAt(node, coherence);

        if (frequency.region() != SpectralRegion.VISIBLE) {
            return fallbackBin;
        }

        return frequency.bin();
    }

    public ScalarPowerSolution withCoherentPowerByNode(Map<PortGraphNode, Double> coherentPowerByNode) {
        return new ScalarPowerSolution(
                solverKind,
                solverPlan,
                converged,
                unstable,
                iterations,
                residual,
                maxNodePower,
                totalNodePower,
                powerByNode,
                coherentPowerByNode,
                powerByLane,
                regionResults,
                profileCollapsedFallback,
                profileOverflow,
                profileDiagnostics
        );
    }

    public boolean reliableForReadout() {
        return converged && !unstable;
    }

    public List<Map.Entry<PortGraphNode, Double>> strongestNodes(int limit) {
        return powerByNode.entrySet().stream()
                .filter(entry -> entry.getValue() > 0.0)
                .sorted((left, right) -> Double.compare(right.getValue(), left.getValue()))
                .limit(Math.max(0, limit))
                .toList();
    }

    private static Map<SpectralPowerLane, Map<PortGraphNode, Double>> copyPowerByLane(
            Map<SpectralPowerLane, Map<PortGraphNode, Double>> powerByLane
    ) {
        return powerByLane.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Map.copyOf(entry.getValue())
                ));
    }
}
