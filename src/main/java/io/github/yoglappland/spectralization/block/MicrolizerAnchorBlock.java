package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.microlizer.MicrolizerPartKind;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class MicrolizerAnchorBlock extends MicrolizerPartBlock {
    public MicrolizerAnchorBlock(BlockBehaviour.Properties properties) {
        super(properties, MicrolizerPartKind.ANCHOR);
    }
}
