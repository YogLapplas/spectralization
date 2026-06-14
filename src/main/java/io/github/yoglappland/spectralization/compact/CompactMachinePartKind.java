package io.github.yoglappland.spectralization.compact;

public enum CompactMachinePartKind {
    ANCHOR(true),
    CORE(true),
    IO_PORT(false);

    private final boolean connectionEndpoint;

    CompactMachinePartKind(boolean connectionEndpoint) {
        this.connectionEndpoint = connectionEndpoint;
    }

    public boolean isConnectionEndpoint() {
        return connectionEndpoint;
    }
}
