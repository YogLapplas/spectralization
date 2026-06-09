package io.github.yoglappland.spectralization.optics.metasurface;

import java.util.Map;
import java.util.Objects;

public record MetasurfaceEnvelope(Map<MetasurfaceParameter, ParameterRange> ranges) {
    public MetasurfaceEnvelope {
        Objects.requireNonNull(ranges, "ranges");
        ranges = Map.copyOf(ranges);

        for (Map.Entry<MetasurfaceParameter, ParameterRange> entry : ranges.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "range parameter");
            Objects.requireNonNull(entry.getValue(), "range");
        }
    }

    public boolean contains(MetasurfaceTarget target) {
        for (Map.Entry<MetasurfaceParameter, Double> entry : target.targets().entrySet()) {
            ParameterRange range = ranges.get(entry.getKey());

            if (range == null || !range.contains(entry.getValue())) {
                return false;
            }
        }

        return true;
    }
}
