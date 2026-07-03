package io.github.yoglappland.spectralization.optics.compiler;

public record ProfileSolverDiagnostics(
        int stateCount,
        int transitionCount,
        int readoutProjectionCount,
        int saturatingTransitionCount,
        int internalSaturatingTransitionCount,
        int saturatingSccCount,
        int maxSaturatingSccStates
) {
    private static final ProfileSolverDiagnostics NONE = new ProfileSolverDiagnostics(0, 0, 0, 0, 0, 0, 0);

    public ProfileSolverDiagnostics {
        if (stateCount < 0
                || transitionCount < 0
                || readoutProjectionCount < 0
                || saturatingTransitionCount < 0
                || internalSaturatingTransitionCount < 0
                || saturatingSccCount < 0
                || maxSaturatingSccStates < 0) {
            throw new IllegalArgumentException("Profile solver diagnostic counts must be non-negative");
        }
    }

    public static ProfileSolverDiagnostics none() {
        return NONE;
    }

    public boolean present() {
        return stateCount > 0
                || transitionCount > 0
                || readoutProjectionCount > 0
                || saturatingTransitionCount > 0
                || internalSaturatingTransitionCount > 0
                || saturatingSccCount > 0
                || maxSaturatingSccStates > 0;
    }
}
