package io.github.yoglappland.spectralization.optics;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class OpticalMaterialProfile {
    private final List<OpticalMaterialSample> samples;

    private OpticalMaterialProfile(List<OpticalMaterialSample> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Optical material profile needs at least one sample");
        }

        this.samples = samples.stream()
                .map(sample -> Objects.requireNonNull(sample, "sample"))
                .sorted(Comparator.comparingInt(sample -> coordinate(sample.frequency())))
                .toList();
    }

    public static OpticalMaterialProfile of(OpticalMaterialSample... samples) {
        return new OpticalMaterialProfile(List.of(samples));
    }

    public static OpticalMaterialProfile constant(OpticalMaterialResponse response) {
        return of(new OpticalMaterialSample(FrequencyKey.DEBUG_VISIBLE, response));
    }

    public OpticalMaterialResponse responseAt(FrequencyKey frequency) {
        Objects.requireNonNull(frequency, "frequency");

        if (samples.size() == 1) {
            return samples.getFirst().response();
        }

        int target = coordinate(frequency);
        OpticalMaterialSample previous = samples.getFirst();

        if (target <= coordinate(previous.frequency())) {
            return previous.response();
        }

        for (int i = 1; i < samples.size(); i++) {
            OpticalMaterialSample next = samples.get(i);
            int nextCoordinate = coordinate(next.frequency());

            if (target == nextCoordinate) {
                return next.response();
            }

            if (target < nextCoordinate) {
                int previousCoordinate = coordinate(previous.frequency());
                double factor = (double) (target - previousCoordinate) / (nextCoordinate - previousCoordinate);
                return OpticalMaterialResponse.lerp(previous.response(), next.response(), factor);
            }

            previous = next;
        }

        return previous.response();
    }

    private static int coordinate(FrequencyKey frequency) {
        return frequency.region().ordinal() * 1024 + frequency.bin();
    }
}
