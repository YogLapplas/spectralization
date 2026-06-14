package io.github.yoglappland.spectralization.compact;

public record CompactMachineValidationResult(
        int validFrames,
        int invalidFrames,
        boolean changedErrorStates
) {
    public static final CompactMachineValidationResult EMPTY = new CompactMachineValidationResult(0, 0, false);
}
