package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.compact.CompactMachinePartKind;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class CompactMachineAnchorBlock extends CompactMachinePartBlock {
    public CompactMachineAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties, CompactMachinePartKind.ANCHOR);
    }
}
