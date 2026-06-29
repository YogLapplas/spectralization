package io.github.yoglappland.spectralization.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface DropsContentsOnRemove {
    void dropContents(Level level, BlockPos pos);
}
