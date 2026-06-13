package io.github.yoglappland.spectralization.optics.fiber;

public final class FiberRenderStyle {
    public static final int COLOR_RGB = 0xB0DCD2;

    public static float usageAlpha(int usageCount) {
        if (usageCount <= 0) {
            return 0.0F;
        }

        return Math.min(0.9F, 0.22F + usageCount * 0.16F);
    }

    private FiberRenderStyle() {
    }
}
