package io.github.yoglappland.spectralization.optics.projection;

public final class SpotFootprintKernel {
    public static final int RESOLUTION = 64;
    public static final SpotFootprintKernel DEFAULT = gaussianDisc(RESOLUTION);

    private final int resolution;
    private final double[] weights;
    private final double[] prefixWeights;

    private SpotFootprintKernel(int resolution, double[] weights) {
        this.resolution = resolution;
        this.weights = weights.clone();
        this.prefixWeights = buildPrefixWeights(resolution, this.weights);
    }

    public double integral(double minU, double minV, double maxU, double maxV) {
        double clampedMinU = clamp01(minU);
        double clampedMinV = clamp01(minV);
        double clampedMaxU = clamp01(maxU);
        double clampedMaxV = clamp01(maxV);

        if (clampedMinU >= clampedMaxU || clampedMinV >= clampedMaxV) {
            return 0.0D;
        }

        double fraction = cumulative(clampedMaxU, clampedMaxV)
                - cumulative(clampedMinU, clampedMaxV)
                - cumulative(clampedMaxU, clampedMinV)
                + cumulative(clampedMinU, clampedMinV);

        return Math.max(0.0D, fraction);
    }

    private double cumulative(double u, double v) {
        double scaledU = clamp01(u) * resolution;
        double scaledV = clamp01(v) * resolution;
        int fullX = clampCell((int) Math.floor(scaledU));
        int fullY = clampCell((int) Math.floor(scaledV));
        double partialX = fullX >= resolution ? 0.0D : scaledU - fullX;
        double partialY = fullY >= resolution ? 0.0D : scaledV - fullY;
        double fraction = sumCells(0, 0, fullX, fullY);

        if (partialX > 0.0D && fullY > 0) {
            fraction += partialX * sumCells(fullX, 0, fullX + 1, fullY);
        }

        if (partialY > 0.0D && fullX > 0) {
            fraction += partialY * sumCells(0, fullY, fullX, fullY + 1);
        }

        if (partialX > 0.0D && partialY > 0.0D) {
            fraction += partialX * partialY * weights[index(fullX, fullY)];
        }

        return fraction;
    }

    private double sumCells(int minX, int minY, int maxX, int maxY) {
        int stride = resolution + 1;
        return prefixWeights[maxY * stride + maxX]
                - prefixWeights[minY * stride + maxX]
                - prefixWeights[maxY * stride + minX]
                + prefixWeights[minY * stride + minX];
    }

    private static double[] buildPrefixWeights(int resolution, double[] weights) {
        int stride = resolution + 1;
        double[] prefix = new double[stride * stride];

        for (int y = 0; y < resolution; y++) {
            double row = 0.0D;

            for (int x = 0; x < resolution; x++) {
                row += weights[y * resolution + x];
                prefix[(y + 1) * stride + x + 1] = prefix[y * stride + x + 1] + row;
            }
        }

        return prefix;
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
