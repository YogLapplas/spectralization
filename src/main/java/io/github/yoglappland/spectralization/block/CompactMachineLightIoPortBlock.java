package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.compact.CompactMachinePartKind;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class CompactMachineLightIoPortBlock extends CompactMachinePartBlock {
    public CompactMachineLightIoPortBlock(BlockBehaviour.Properties properties) {
        super(properties, CompactMachinePartKind.IO_PORT);
    }
}
