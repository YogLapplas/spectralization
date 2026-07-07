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
        int ringAlphaLevel,
        ProjectionMode projectionMode,
        int clipMinU,
        int clipMinV,
        int clipMaxU,
        int clipMaxV,
        int textureMinU,
        int textureMinV,
        int textureMaxU,
        int textureMaxV,
        int quadX0,
        int quadY0,
        int quadZ0,
        int quadTextureU0,
        int quadTextureV0,
        int quadX1,
        int quadY1,
        int quadZ1,
        int quadTextureU1,
        int quadTextureV1,
        int quadX2,
        int quadY2,
        int quadZ2,
        int quadTextureU2,
        int quadTextureV2,
        int quadX3,
        int quadY3,
        int quadZ3,
        int quadTextureU3,
        int quadTextureV3
) {
    public static final int SLICE_QUANTIZATION_LEVEL = 255;
    public static final int QUAD_QUANTIZATION_LEVEL = 65535;

    public enum ProjectionMode {
        CENTERED_QUAD,
        FOOTPRINT_SLICE,
        FOOTPRINT_QUAD,
        DEBUG_FACE_CENTER
    }

    public SpotRecord(
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
        this(
                pos,
                face,
                coherentAlphaLevel,
                coherentRadiusLevel,
                coherentRed,
                coherentGreen,
                coherentBlue,
                strayAlphaLevel,
                strayRadiusLevel,
                strayRed,
                strayGreen,
                strayBlue,
                ringAlphaLevel,
                ProjectionMode.CENTERED_QUAD,
                0,
                0,
                255,
                255,
                0,
                0,
                255,
                255,
                0,
                0,
                0,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL
        );
    }

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
        validateByte(clipMinU, "Clip min U");
        validateByte(clipMinV, "Clip min V");
        validateByte(clipMaxU, "Clip max U");
        validateByte(clipMaxV, "Clip max V");
        validateByte(textureMinU, "Texture min U");
        validateByte(textureMinV, "Texture min V");
        validateByte(textureMaxU, "Texture max U");
        validateByte(textureMaxV, "Texture max V");
        validateQuadUnit(quadX0, "Quad x0");
        validateQuadUnit(quadY0, "Quad y0");
        validateQuadUnit(quadZ0, "Quad z0");
        validateQuadUnit(quadTextureU0, "Quad texture u0");
        validateQuadUnit(quadTextureV0, "Quad texture v0");
        validateQuadUnit(quadX1, "Quad x1");
        validateQuadUnit(quadY1, "Quad y1");
        validateQuadUnit(quadZ1, "Quad z1");
        validateQuadUnit(quadTextureU1, "Quad texture u1");
        validateQuadUnit(quadTextureV1, "Quad texture v1");
        validateQuadUnit(quadX2, "Quad x2");
        validateQuadUnit(quadY2, "Quad y2");
        validateQuadUnit(quadZ2, "Quad z2");
        validateQuadUnit(quadTextureU2, "Quad texture u2");
        validateQuadUnit(quadTextureV2, "Quad texture v2");
        validateQuadUnit(quadX3, "Quad x3");
        validateQuadUnit(quadY3, "Quad y3");
        validateQuadUnit(quadZ3, "Quad z3");
        validateQuadUnit(quadTextureU3, "Quad texture u3");
        validateQuadUnit(quadTextureV3, "Quad texture v3");

        if (projectionMode == null) {
            throw new IllegalArgumentException("Spot projection mode must not be null");
        }

        if (clipMinU >= clipMaxU || clipMinV >= clipMaxV) {
            throw new IllegalArgumentException("Spot clip rectangle must have positive area");
        }

        if (textureMinU >= textureMaxU || textureMinV >= textureMaxV) {
            throw new IllegalArgumentException("Spot texture rectangle must have positive area");
        }
    }

    public boolean visible() {
        return coherentAlphaLevel > 0 || strayAlphaLevel > 0 || ringAlphaLevel > 0;
    }

    public static SpotRecord debugFaceCenter(BlockPos pos, Direction face) {
        return new SpotRecord(
                pos,
                face,
                15,
                1,
                255,
                24,
                24,
                0,
                0,
                0,
                0,
                0,
                0,
                ProjectionMode.DEBUG_FACE_CENTER,
                0,
                0,
                255,
                255,
                0,
                0,
                255,
                255,
                0,
                0,
                0,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL
        );
    }

    public SpotRecord withFootprintSlice(
            int clipMinU,
            int clipMinV,
            int clipMaxU,
            int clipMaxV,
            int textureMinU,
            int textureMinV,
            int textureMaxU,
            int textureMaxV
    ) {
        return new SpotRecord(
                pos,
                face,
                coherentAlphaLevel,
                coherentRadiusLevel,
                coherentRed,
                coherentGreen,
                coherentBlue,
                strayAlphaLevel,
                strayRadiusLevel,
                strayRed,
                strayGreen,
                strayBlue,
                ringAlphaLevel,
                ProjectionMode.FOOTPRINT_SLICE,
                clipMinU,
                clipMinV,
                clipMaxU,
                clipMaxV,
                textureMinU,
                textureMinV,
                textureMaxU,
                textureMaxV,
                0,
                0,
                0,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                QUAD_QUANTIZATION_LEVEL,
                0,
                QUAD_QUANTIZATION_LEVEL,
                0,
                0,
                QUAD_QUANTIZATION_LEVEL
        );
    }

    public SpotRecord withFootprintQuad(
            int quadX0,
            int quadY0,
            int quadZ0,
            int quadTextureU0,
            int quadTextureV0,
            int quadX1,
            int quadY1,
            int quadZ1,
            int quadTextureU1,
            int quadTextureV1,
            int quadX2,
            int quadY2,
            int quadZ2,
            int quadTextureU2,
            int quadTextureV2,
            int quadX3,
            int quadY3,
            int quadZ3,
            int quadTextureU3,
            int quadTextureV3
    ) {
        return new SpotRecord(
                pos,
                face,
                coherentAlphaLevel,
                coherentRadiusLevel,
                coherentRed,
                coherentGreen,
                coherentBlue,
                strayAlphaLevel,
                strayRadiusLevel,
                strayRed,
                strayGreen,
                strayBlue,
                ringAlphaLevel,
                ProjectionMode.FOOTPRINT_QUAD,
                0,
                0,
                255,
                255,
                0,
                0,
                255,
                255,
                quadX0,
                quadY0,
                quadZ0,
                quadTextureU0,
                quadTextureV0,
                quadX1,
                quadY1,
                quadZ1,
                quadTextureU1,
                quadTextureV1,
                quadX2,
                quadY2,
                quadZ2,
                quadTextureU2,
                quadTextureV2,
                quadX3,
                quadY3,
                quadZ3,
                quadTextureU3,
                quadTextureV3
        );
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

    private static void validateByte(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be between 0 and 255");
        }
    }

    private static void validateQuadUnit(int value, String name) {
        if (value < 0 || value > QUAD_QUANTIZATION_LEVEL) {
            throw new IllegalArgumentException(name + " must be between 0 and " + QUAD_QUANTIZATION_LEVEL);
        }
    }
}
