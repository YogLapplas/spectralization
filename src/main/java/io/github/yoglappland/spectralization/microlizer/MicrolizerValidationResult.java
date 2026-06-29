package io.github.yoglappland.spectralization.microlizer;

public record MicrolizerValidationResult(
        int validFrames,
        int invalidFrames,
        boolean changedErrorStates
) {
    public static final MicrolizerValidationResult EMPTY = new MicrolizerValidationResult(0, 0, false);
}
