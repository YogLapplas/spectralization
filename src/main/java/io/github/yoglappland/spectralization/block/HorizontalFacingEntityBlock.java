package io.github.yoglappland.spectralization.block;

import net.minecraft.world.level.block.EntityBlock;

public abstract class HorizontalFacingEntityBlock extends HorizontalFacingBlock implements EntityBlock {
    protected HorizontalFacingEntityBlock(Properties properties) {
        super(properties);
    }
}
