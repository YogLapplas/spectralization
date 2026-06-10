package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Objects;

public record PortGraphEdge(
        int id,
        PortGraphEdgeKind kind,
        PortGraphNode from,
        PortGraphNode to,
        int distance,
        double sampleInputPower,
        double sampleOutputPower,
        FrequencyKey sampleFrequency
) {
    public PortGraphEdge(
            int id,
            PortGraphEdgeKind kind,
            PortGraphNode from,
            PortGraphNode to,
            int distance,
            double sampleInputPower,
            double sampleOutputPower
    ) {
        this(id, kind, from, to, distance, sampleInputPower, sampleOutputPower, FrequencyKey.DEBUG_VISIBLE);
    }

    public PortGraphEdge {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(sampleFrequency, "sampleFrequency");

        if (id < 0) {
            throw new IllegalArgumentException("Port graph edge id must be non-negative");
        }

        if (distance < 0) {
            throw new IllegalArgumentException("Port graph edge distance must be non-negative");
        }

        if (!Double.isFinite(sampleInputPower) || sampleInputPower < 0.0) {
            throw new IllegalArgumentException("Sample input power must be finite and non-negative");
        }

        if (!Double.isFinite(sampleOutputPower) || sampleOutputPower < 0.0) {
            throw new IllegalArgumentException("Sample output power must be finite and non-negative");
        }
    }

    public double sampleGain() {
        if (sampleInputPower <= 0.0) {
            return 0.0;
        }

        return sampleOutputPower / sampleInputPower;
    }
}
