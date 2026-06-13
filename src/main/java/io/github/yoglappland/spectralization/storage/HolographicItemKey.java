package io.github.yoglappland.spectralization.storage;

import net.minecraft.world.item.ItemStack;

public final class HolographicItemKey {
    private final ItemStack stack;
    private final int hash;

    private HolographicItemKey(ItemStack stack) {
        this.stack = stack.copyWithCount(1);
        this.hash = ItemStack.hashItemAndComponents(this.stack);
    }

    public static HolographicItemKey of(ItemStack stack) {
        return new HolographicItemKey(stack);
    }

    public ItemStack stack() {
        return stack.copyWithCount(1);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof HolographicItemKey other
                && ItemStack.isSameItemSameComponents(stack, other.stack);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
