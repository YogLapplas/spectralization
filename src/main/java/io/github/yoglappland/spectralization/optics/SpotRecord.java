package io.github.yoglappland.spectralization.optics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SpotRecord(
        BlockPos pos,
        Direction face,
        int coherentAlphaLevel,
        int coherentRadiusLevel,
        int coherentRed,
        int coherentGreen,
        int coherentBlue,
        int strayAlphaLevel,
        int strayRadiusLevel,
        int strayRed,
        int strayGreen,
        int strayBlue,
        int ringAlphaLevel
) {
    public SpotRecord {
        validateLevel(coherentAlphaLevel, "Coherent alpha level");
        validateLevel(coherentRadiusLevel, "Coherent radius level");
        validateColor(coherentRed, "Coherent red");
        validateColor(coherentGreen, "Coherent green");
        validateColor(coherentBlue, "Coherent blue");
        validateLevel(strayAlphaLevel, "Stray alpha level");
        validateLevel(strayRadiusLevel, "Stray radius level");
        validateColor(strayRed, "Stray red");
        validateColor(strayGreen, "Stray green");
        validateColor(strayBlue, "Stray blue");
        validateLevel(ringAlphaLevel, "Ring alpha level");
    }

    public boolean visible() {
        return coherentAlphaLevel > 0 || strayAlphaLevel > 0 || ringAlphaLevel > 0;
    }

    private static void validateLevel(int level, String name) {
        if (level < 0 || level > 15) {
            throw new IllegalArgumentException(name + " must be between 0 and 15");
        }
    }

    private static void validateColor(int channel, String name) {
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException(name + " must be between 0 and 255");
        }
    }
}
