package io.github.yoglappland.spectralization.optics.medium;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class OpticalMediumProfile {
    private final List<OpticalMediumSample> samples;

    private OpticalMediumProfile(List<OpticalMediumSample> samples) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("Optical medium profile needs at least one sample");
        }

        this.samples = samples.stream()
                .map(sample -> Objects.requireNonNull(sample, "sample"))
                .sorted(Comparator.comparingInt(sample -> coordinate(sample.frequency())))
                .toList();
    }

    public static OpticalMediumProfile of(OpticalMediumSample... samples) {
        return new OpticalMediumProfile(List.of(samples));
    }

    public static OpticalMediumProfile constant(OpticalMediumResponse response) {
        return of(new OpticalMediumSample(FrequencyKey.DEBUG_VISIBLE, response));
    }

    public OpticalMediumResponse responseAt(FrequencyKey frequency) {
        Objects.requireNonNull(frequency, "frequency");

        if (samples.size() == 1) {
            return samples.getFirst().response();
        }

        int target = coordinate(frequency);
        OpticalMediumSample previous = samples.getFirst();

        if (target <= coordinate(previous.frequency())) {
            return previous.response();
        }

        for (int i = 1; i < samples.size(); i++) {
            OpticalMediumSample next = samples.get(i);
            int nextCoordinate = coordinate(next.frequency());

            if (target == nextCoordinate) {
                return next.response();
            }

            if (target < nextCoordinate) {
                int previousCoordinate = coordinate(previous.frequency());
                double factor = (double) (target - previousCoordinate) / (nextCoordinate - previousCoordinate);
                return OpticalMediumResponse.lerp(previous.response(), next.response(), factor);
            }

            previous = next;
        }

        return previous.response();
    }

    private static int coordinate(FrequencyKey frequency) {
        return frequency.region().ordinal() * 1024 + frequency.bin();
    }
}
