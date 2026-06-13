package io.github.yoglappland.spectralization.storage;

public record HolographicStorageBlockEntry(String descriptionId, int count) {
    public HolographicStorageBlockEntry {
        if (count < 0) {
            throw new IllegalArgumentException("Block count must not be negative");
        }
    }
}
