package io.github.yoglappland.spectralization.optics.fiber;

public enum FiberConnectionStatus {
    VALID,
    EMPTY_ROUTE,
    MISSING_NODE,
    WRONG_NODE_KIND,
    SEGMENT_TOO_LONG,
    BLOCKED
}
