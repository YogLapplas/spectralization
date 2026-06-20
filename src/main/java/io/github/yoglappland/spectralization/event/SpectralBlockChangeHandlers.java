package io.github.yoglappland.spectralization.event;

import io.github.yoglappland.spectralization.block.CompactMachinePartBlock;
import io.github.yoglappland.spectralization.block.FiberRelayBlock;
import io.github.yoglappland.spectralization.block.HolographicStorageMultiblock;
import io.github.yoglappland.spectralization.blockentity.RubyBlockEntity;
import io.github.yoglappland.spectralization.compact.CompactMachineNetworkData;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingData;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.world.OpticalWorldIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public final class SpectralBlockChangeHandlers {
    private SpectralBlockChangeHandlers() {
    }

    public static void placed(LevelAccessor levelAccessor, BlockPos pos, BlockState placedBlock) {
        if (levelAccessor instanceof Level level) {
            SurfaceCoatingData.removeAll(level, pos);
        }

        if (!isFiberRelayOnly(levelAccessor.getBlockState(pos))) {
            OpticalFieldSources.invalidate(levelAccessor);
            OpticalWorldIndex.onBlockPlaced(levelAccessor, pos);
            OpticalTraceCache.rememberSourceState(levelAccessor, pos);
            OpticalTraceCache.markChanged(levelAccessor, pos, OpticalDirtyKind.STRUCTURE);
            RubyBlockEntity.refreshNear(levelAccessor, pos);
            OpticalTraceCache.requestIntrinsicSourcesNear(levelAccessor, pos);
            OpticalNetworkIndex.markDirty(levelAccessor);
        }

        FiberNetworkIndex.onBlockPlaced(levelAccessor, pos);
        if (HolographicStorageMultiblock.isRelevantChange(levelAccessor, pos, placedBlock)
                && levelAccessor instanceof Level level) {
            HolographicStorageMultiblock.scheduleRefresh(level, pos.immutable(), "block placed");
        }
        if (levelAccessor instanceof ServerLevel serverLevel) {
            BlockState placedState = serverLevel.getBlockState(pos);
            if (!(placedState.getBlock() instanceof CompactMachinePartBlock)
                    && CompactMachineNetworkData.isRelevantPlacement(serverLevel, pos, placedState)) {
                CompactMachineNetworkData.refreshNear(serverLevel, pos.immutable(), "block placed");
            }
        }
    }

    public static void broken(LevelAccessor levelAccessor, Level playerLevel, BlockPos pos, BlockState state) {
        SurfaceCoatingData.removeAll(playerLevel, pos);

        if (!isFiberRelayOnly(state)) {
            OpticalFieldSources.invalidate(levelAccessor);
            OpticalWorldIndex.onBlockBroken(levelAccessor, pos);
            OpticalTraceCache.forgetDormantSource(levelAccessor, pos);
            OpticalTraceCache.markChanged(levelAccessor, pos, OpticalDirtyKind.STRUCTURE);
            OpticalTraceCache.requestIntrinsicSourcesNear(levelAccessor, pos);
            OpticalNetworkIndex.markDirty(levelAccessor);
        }

        FiberNetworkIndex.onBlockBroken(levelAccessor, pos);
        if (HolographicStorageMultiblock.isRelevantChange(levelAccessor, pos, state)
                && levelAccessor instanceof Level level) {
            HolographicStorageMultiblock.scheduleRefresh(level, pos.immutable(), "block broken");
        }
        if (levelAccessor instanceof ServerLevel serverLevel
                && !(state.getBlock() instanceof CompactMachinePartBlock)
                && CompactMachineNetworkData.isRelevantRemoval(serverLevel, pos, state)) {
            CompactMachineNetworkData.scheduleRefresh(serverLevel, pos.immutable(), "block broken");
        }
    }

    public static void neighborNotified(LevelAccessor levelAccessor, BlockPos pos) {
        boolean opticalDataChanged = OpticalTraceCache.markChanged(levelAccessor, pos, OpticalDirtyKind.PARAMETER);
        opticalDataChanged |= RubyBlockEntity.refreshNear(levelAccessor, pos);

        if (opticalDataChanged) {
            OpticalTraceCache.requestIntrinsicSourcesNear(levelAccessor, pos);
        }
    }

    private static boolean isFiberRelayOnly(BlockState state) {
        return state.getBlock() instanceof FiberRelayBlock;
    }
}
