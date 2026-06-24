package io.github.yoglappland.spectralization.machine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SolarDopingChanceLut {
    public static final int PPM = 1_000_000;
    private static final int MAX_EXPECTED_TICKS = 1_000_000;
    private static final Map<Integer, Estimate> CACHE = new ConcurrentHashMap<>();

    private SolarDopingChanceLut() {
    }

    public static int chancePpm(double baseChance, int exposureTick) {
        if (!Double.isFinite(baseChance) || baseChance <= 0.0 || exposureTick <= 0) {
            return 0;
        }

        double chance = Math.min(1.0, baseChance * exposureTick);
        return (int) Math.round(chance * PPM);
    }

    public static Estimate estimate(double baseChance) {
        int key = quantize(baseChance);
        if (key <= 0) {
            return new Estimate(0, 0);
        }

        return CACHE.computeIfAbsent(key, SolarDopingChanceLut::compute);
    }

    private static int quantize(double baseChance) {
        if (!Double.isFinite(baseChance) || baseChance <= 0.0) {
            return 0;
        }

        return Math.max(1, Math.min(PPM, (int) Math.round(baseChance * PPM)));
    }

    private static Estimate compute(int key) {
        double baseChance = key / (double) PPM;
        int maxTicks = Math.max(1, (int) Math.ceil(1.0 / baseChance));
        double survival = 1.0;
        double expected = 0.0;

        for (int tick = 1; tick <= maxTicks && tick <= MAX_EXPECTED_TICKS; tick++) {
            expected += survival;
            double tickChance = Math.min(1.0, baseChance * tick);
            survival *= 1.0 - tickChance;

            if (survival < 1.0E-9 && tick > 1) {
                break;
            }
        }

        return new Estimate(
                Math.max(1, (int) Math.round(expected)),
                Math.min(maxTicks, MAX_EXPECTED_TICKS)
        );
    }

    public record Estimate(int expectedTicks, int maxTicks) {
    }
}
