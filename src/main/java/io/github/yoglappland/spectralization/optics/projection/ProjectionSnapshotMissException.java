package io.github.yoglappland.spectralization.optics.projection;

import net.minecraft.core.BlockPos;

public final class ProjectionSnapshotMissException extends IllegalStateException {
    private final BlockPos position;

    public ProjectionSnapshotMissException(BlockPos position) {
        super("Spot projection requested a position outside its immutable snapshot: " + position.toShortString());
        this.position = position.immutable();
    }

    public BlockPos position() {
        return position;
    }
}
