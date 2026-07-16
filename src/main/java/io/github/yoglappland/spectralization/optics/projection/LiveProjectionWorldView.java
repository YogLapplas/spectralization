package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfile;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Main-thread adapter used by the legacy serial path and eager snapshot capture. */
public final class LiveProjectionWorldView implements ProjectionWorldView {
    private static final List<ProjectionLocalBox> FULL_BOX = List.of(ProjectionLocalBox.FULL);
    private static final Map<BlockState, List<ProjectionLocalBox>> MODEL_BOX_CACHE = new ConcurrentHashMap<>();
    private final Level level;

    public LiveProjectionWorldView(Level level) {
        this.level = java.util.Objects.requireNonNull(level, "level");
    }

    @Override
    public ProjectionBlockFacts blockFacts(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return ProjectionBlockFacts.UNLOADED;
        }
        BlockState state = level.getBlockState(pos);
        boolean airLike = OpticalMaterialProfiles.isAirLike(state);
        if (airLike) {
            return new ProjectionBlockFacts(true, true, List.of(), null, "air_like");
        }
        List<ProjectionLocalBox> boxes = localOpticalBoxes(pos, state);
        OpticalMaterialProfile materialProfile = boxes.isEmpty()
                ? null
                : OpticalMaterialProfiles.profileFor(level, pos, state);
        return new ProjectionBlockFacts(
                true,
                false,
                boxes,
                materialProfile,
                boxes.isEmpty() ? "non_projectable" : state.toString()
        );
    }

    private List<ProjectionLocalBox> localOpticalBoxes(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof LensHolderBlock) {
            return level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder && lensHolder.hasLens()
                    ? FULL_BOX
                    : List.of();
        }
        if (state.isCollisionShapeFullBlock(level, pos)
                || state.getBlock() instanceof OpticalElement
                || state.getBlock() instanceof OpticalSource) {
            return FULL_BOX;
        }
        if (!(state.getBlock() instanceof SlabBlock)
                && !(state.getBlock() instanceof StairBlock)
                && !(state.getBlock() instanceof FenceBlock)) {
            return List.of();
        }
        List<ProjectionLocalBox> cached = MODEL_BOX_CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            MODEL_BOX_CACHE.putIfAbsent(state, List.of());
            return List.of();
        }
        List<ProjectionLocalBox> boxes = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            try {
                boxes.add(ProjectionLocalBox.fromAabb(box));
            } catch (IllegalArgumentException ignored) {
                // VoxelShape may expose degenerate contacts; they have no projection volume.
            }
        }
        List<ProjectionLocalBox> immutable = boxes.isEmpty() ? List.of() : List.copyOf(boxes);
        List<ProjectionLocalBox> existing = MODEL_BOX_CACHE.putIfAbsent(state, immutable);
        return existing == null ? immutable : existing;
    }
}
