package io.github.yoglappland.spectralization.optics.projection;

public final class SpotFootprintKernel {
    public static final int RESOLUTION = 64;
    public static final SpotFootprintKernel DEFAULT = gaussianDisc(RESOLUTION);

    private final int resolution;
    private final double[] weights;

    private SpotFootprintKernel(int resolution, double[] weights) {
        this.resolution = resolution;
        this.weights = weights.clone();
    }

    public double integral(double minU, double minV, double maxU, double maxV) {
        double clampedMinU = clamp01(minU);
        double clampedMinV = clamp01(minV);
        double clampedMaxU = clamp01(maxU);
        double clampedMaxV = clamp01(maxV);

        if (clampedMinU >= clampedMaxU || clampedMinV >= clampedMaxV) {
            return 0.0D;
        }

        int x0 = clampCell((int) Math.floor(clampedMinU * resolution));
        int y0 = clampCell((int) Math.floor(clampedMinV * resolution));
        int x1 = clampCell((int) Math.ceil(clampedMaxU * resolution));
        int y1 = clampCell((int) Math.ceil(clampedMaxV * resolution));
        double cellScale = resolution * resolution;
        double fraction = 0.0D;

        for (int y = y0; y < y1; y++) {
            double cellMinV = (double) y / resolution;
            double cellMaxV = (double) (y + 1) / resolution;
            double overlapV = Math.min(clampedMaxV, cellMaxV) - Math.max(clampedMinV, cellMinV);

            if (overlapV <= 0.0D) {
                continue;
            }

            for (int x = x0; x < x1; x++) {
                double cellMinU = (double) x / resolution;
                double cellMaxU = (double) (x + 1) / resolution;
                double overlapU = Math.min(clampedMaxU, cellMaxU) - Math.max(clampedMinU, cellMinU);

                if (overlapU <= 0.0D) {
                    continue;
                }

                fraction += weights[index(x, y)] * overlapU * overlapV * cellScale;
            }
        }

        return Math.max(0.0D, fraction);
    }

    private int clampCell(int value) {
        return Math.max(0, Math.min(resolution, value));
    }

    private int index(int x, int y) {
        return y * resolution + x;
    }

    private static SpotFootprintKernel gaussianDisc(int resolution) {
        double[] weights = new double[resolution * resolution];
        double total = 0.0D;

        for (int y = 0; y < resolution; y++) {
            for (int x = 0; x < resolution; x++) {
                double u = ((x + 0.5D) / resolution) * 2.0D - 1.0D;
                double v = ((y + 0.5D) / resolution) * 2.0D - 1.0D;
                double r2 = u * u + v * v;
                double weight = r2 <= 1.0D ? Math.exp(-2.0D * r2) : 0.0D;
                weights[y * resolution + x] = weight;
                total += weight;
            }
        }

        if (total <= 0.0D) {
            throw new IllegalStateException("Spot footprint kernel has no positive energy");
        }

        for (int index = 0; index < weights.length; index++) {
            weights[index] /= total;
        }

        return new SpotFootprintKernel(resolution, weights);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private SpotFootprintKernel() {
        throw new AssertionError();
    }
}
