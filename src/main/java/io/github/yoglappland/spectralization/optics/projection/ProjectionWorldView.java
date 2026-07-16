package io.github.yoglappland.spectralization.optics.projection;

import net.minecraft.core.BlockPos;

/** Read-only world facts for projection math. Implementations passed to workers must be immutable. */
@FunctionalInterface
public interface ProjectionWorldView {
    ProjectionBlockFacts blockFacts(BlockPos pos);
}
