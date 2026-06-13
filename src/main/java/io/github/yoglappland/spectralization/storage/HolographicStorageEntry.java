package io.github.yoglappland.spectralization.storage;

import net.minecraft.world.item.ItemStack;

public record HolographicStorageEntry(ItemStack stack, long count) {
    public HolographicStorageEntry {
        stack = stack.copyWithCount(1);
        if (count <= 0) {
            throw new IllegalArgumentException("Stored item count must be positive");
        }
    }
}
