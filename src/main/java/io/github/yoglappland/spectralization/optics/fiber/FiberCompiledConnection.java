package io.github.yoglappland.spectralization.optics.fiber;

import java.util.Objects;

public record FiberCompiledConnection(
        FiberConnection connection,
        FiberConnectionStatus status
) {
    public FiberCompiledConnection {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(status, "status");
    }

    public boolean valid() {
        return status == FiberConnectionStatus.VALID;
    }
}
