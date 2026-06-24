package io.github.yoglappland.spectralization.optics.fiber;

import io.github.yoglappland.spectralization.block.FiberOpticInterfaceBlock;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public final class FiberLikeFaces {
    public static boolean isDirectGuidedAdjacency(
            Level level,
            BlockPos fromPos,
            Direction outgoingFace,
            BlockPos toPos,
            Direction incomingFace
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(fromPos, "fromPos");
        Objects.requireNonNull(outgoingFace, "outgoingFace");
        Objects.requireNonNull(toPos, "toPos");
        Objects.requireNonNull(incomingFace, "incomingFace");

        if (incomingFace != outgoingFace.getOpposite() || !fromPos.relative(outgoingFace).equals(toPos)) {
            return false;
        }

        if (!level.isLoaded(fromPos) || !level.isLoaded(toPos)) {
            return false;
        }

        return isFiberLikeFace(level.getBlockState(fromPos), level, fromPos, outgoingFace)
                && isFiberLikeFace(level.getBlockState(toPos), level, toPos, incomingFace);
    }

    public static boolean isFiberLikeFace(
            BlockState state,
            LevelAccessor level,
            BlockPos pos,
            Direction face
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");

        if (state.getBlock() instanceof FiberOpticInterfaceBlock) {
            return state.getValue(FiberOpticInterfaceBlock.FACING) == face;
        }

        if (!state.is(SpectralBlockTags.FIBER_LIKE)) {
            return false;
        }

        if (state.getBlock() instanceof FiberLikeFaceBlock fiberLikeFaceBlock) {
            return fiberLikeFaceBlock.isFiberLikeFace(state, level, pos, face);
        }

        return true;
    }

    private FiberLikeFaces() {
    }
}
