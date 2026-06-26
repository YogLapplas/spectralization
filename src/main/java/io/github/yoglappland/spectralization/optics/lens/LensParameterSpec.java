package io.github.yoglappland.spectralization.optics.lens;

public record LensParameterSpec(
        String symbol,
        String translationKey,
        int defaultValue,
        int minValue,
        int stoneToolMax,
        int ironToolMax,
        int goldToolMax,
        int maxValue,
        int earlyStep,
        int standardStep,
        int stoneError,
        int ironError,
        int goldError,
        int highTierError
) {
    public static LensParameterSpec standard(String symbol, String translationKey) {
        return new LensParameterSpec(
                symbol,
                translationKey,
                LensProfile.STANDARD.focalLength(),
                LensProfile.MIN_FOCAL_LENGTH,
                16,
                24,
                LensProfile.MAX_FOCAL_LENGTH,
                LensProfile.MAX_FOCAL_LENGTH,
                2,
                1,
                4,
                3,
                2,
                1
        );
    }

    public int maxForToolTier(int toolTier) {
        return switch (Math.max(0, toolTier)) {
            case 0 -> stoneToolMax;
            case 1 -> ironToolMax;
            case 2 -> goldToolMax;
            default -> maxValue;
        };
    }

    public int stepForToolTier(int toolTier) {
        return toolTier <= 0 ? earlyStep : standardStep;
    }

    public int baseErrorForToolTier(int toolTier) {
        return switch (Math.max(0, toolTier)) {
            case 0 -> stoneError;
            case 1 -> ironError;
            case 2 -> goldError;
            default -> highTierError;
        };
    }
}
