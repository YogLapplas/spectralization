package io.github.yoglappland.spectralization.heat;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public interface PhotothermalReceiverBlock {
    Direction photothermalReceivingSide(BlockState state);
}
