package io.github.yoglappland.spectralization.microlizer;

public enum MicrolizerPartKind {
    ANCHOR(true),
    CORE(false),
    IO_PORT(false);

    private final boolean connectionEndpoint;

    MicrolizerPartKind(boolean connectionEndpoint) {
        this.connectionEndpoint = connectionEndpoint;
    }

    public boolean isConnectionEndpoint() {
        return connectionEndpoint;
    }
}
