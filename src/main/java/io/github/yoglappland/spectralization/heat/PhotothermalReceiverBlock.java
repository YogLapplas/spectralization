package io.github.yoglappland.spectralization.heat;

import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public interface PhotothermalReceiverBlock {
    Direction photothermalReceivingSide(BlockState state);

    default Set<Direction> photothermalReceivingSides(BlockState state) {
        return Set.of(photothermalReceivingSide(state));
    }
}
