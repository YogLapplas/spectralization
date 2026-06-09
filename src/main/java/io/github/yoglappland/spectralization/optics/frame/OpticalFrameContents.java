package io.github.yoglappland.spectralization.optics.frame;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

public interface OpticalFrameContents {
    ItemStack opticalCore();

    ItemStack surfacePlate(Direction side);

    default boolean preservesOpticalDataOnDrop() {
        return true;
    }
}
