package io.github.yoglappland.spectralization.machine;

public final class ThermalSmelterBalance {
    public static final double HEAT_CAPACITY = 4.0;
    public static final double AMBIENT_TEMPERATURE = 300.0;
    public static final double MAX_TEMPERATURE = 1850.0;

    public static final double PASSIVE_COOLING_LINEAR = 0.004;
    public static final double PASSIVE_COOLING_QUADRATIC = 0.000008;

    public static final double MAX_ACCEPTED_OPTICAL_HEAT_POWER = 90.0;
    public static final double OPTICAL_TO_COMPAT_HEAT = 0.35;
    public static final int MAX_PARALLEL_COUNT = 4;

    public static final int FULL_SP_HEAT_POWER_X100 =
            (int) Math.round(MAX_ACCEPTED_OPTICAL_HEAT_POWER * OPTICAL_TO_COMPAT_HEAT * 100.0);

    public static double acceptedCompatibleHeatPower(double opticalHeatPower) {
        if (!Double.isFinite(opticalHeatPower) || opticalHeatPower <= 0.0) {
            return 0.0;
        }

        return Math.min(opticalHeatPower, MAX_ACCEPTED_OPTICAL_HEAT_POWER) * OPTICAL_TO_COMPAT_HEAT;
    }

    public static double passiveCooling(double temperature, double ambientTemperature, double heatStored) {
        if (!Double.isFinite(temperature) || !Double.isFinite(ambientTemperature) || heatStored <= 0.0) {
            return 0.0;
        }

        double deltaTemperature = Math.max(0.0, temperature - ambientTemperature);
        double cooling = PASSIVE_COOLING_LINEAR * deltaTemperature
                + PASSIVE_COOLING_QUADRATIC * deltaTemperature * deltaTemperature;
        return Math.min(heatStored, cooling);
    }

    private ThermalSmelterBalance() {
    }
}
