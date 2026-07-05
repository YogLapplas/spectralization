package io.github.yoglappland.spectralization.event;

import io.github.yoglappland.spectralization.block.MicrolizerPartBlock;
import io.github.yoglappland.spectralization.block.FiberRelayBlock;
import io.github.yoglappland.spectralization.block.HolographicStorageMultiblock;
import io.github.yoglappland.spectralization.blockentity.GainMediumBlockEntity;
import io.github.yoglappland.spectralization.microlizer.MicrolizerNetworkData;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingData;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.world.OpticalWorldIndex;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public final class SpectralBlockChangeHandlers {
    private static final int PISTON_RESCAN_DISTANCE = 13;
    private static final int PISTON_POST_MOVE_RESCAN_TICKS = 4;
    private static final Map<ResourceKey<Level>, List<PendingPistonRescan>> PENDING_PISTON_RESCANS =
            new LinkedHashMap<>();

    private SpectralBlockChangeHandlers() {
    }

    public static void placed(LevelAccessor levelAccessor, BlockPos pos, BlockState placedBlock) {
        if (levelAccessor instanceof Level level) {
            SurfaceCoatingData.removeAll(level, pos);
        }

        if (!isFiberRelayOnly(levelAccessor.getBlockState(pos))) {
            if (OpticalFieldSources.isScatteringFieldSource(placedBlock)) {
                markScatteringFieldSourceChanged(levelAccessor, pos);
            }

            OpticalWorldIndex.onBlockPlaced(levelAccessor, pos);
            OpticalTraceCache.rememberSourceState(levelAccessor, pos);
            OpticalTraceCache.markChanged(levelAccessor, pos, OpticalDirtyKind.STRUCTURE);
            GainMediumBlockEntity.refreshNear(levelAccessor, pos);
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
            if (!(placedState.getBlock() instanceof MicrolizerPartBlock)
                    && MicrolizerNetworkData.isRelevantPlacement(serverLevel, pos, placedState)) {
                MicrolizerNetworkData.refreshNear(serverLevel, pos.immutable(), "block placed");
            }
        }
    }

    public static void broken(LevelAccessor levelAccessor, Level playerLevel, BlockPos pos, BlockState state) {
        SurfaceCoatingData.removeAll(playerLevel, pos);

        if (!isFiberRelayOnly(state)) {
            if (OpticalFieldSources.isScatteringFieldSource(state)) {
                markScatteringFieldSourceChanged(levelAccessor, pos);
            }

            OpticalWorldIndex.onBlockBroken(levelAccessor, pos);
            OpticalTraceCache.forgetDormantSource(levelAccessor, pos);
            OpticalTraceCache.markChanged(levelAccessor, pos, OpticalDirtyKind.STRUCTURE);
            GainMediumBlockEntity.refreshNear(levelAccessor, pos);
            OpticalTraceCache.requestIntrinsicSourcesNear(levelAccessor, pos);
            OpticalNetworkIndex.markDirty(levelAccessor);
        }

        FiberNetworkIndex.onBlockBroken(levelAccessor, pos);
        if (HolographicStorageMultiblock.isRelevantChange(levelAccessor, pos, state)
                && levelAccessor instanceof Level level) {
            HolographicStorageMultiblock.scheduleRefresh(level, pos.immutable(), "block broken");
        }
        if (levelAccessor instanceof ServerLevel serverLevel
                && !(state.getBlock() instanceof MicrolizerPartBlock)
                && MicrolizerNetworkData.isRelevantRemoval(serverLevel, pos, state)) {
            MicrolizerNetworkData.scheduleRefresh(serverLevel, pos.immutable(), "block broken");
        }
    }

    public static void neighborNotified(LevelAccessor levelAccessor, BlockPos pos) {
        boolean opticalDataChanged = OpticalTraceCache.markChanged(levelAccessor, pos, OpticalDirtyKind.PARAMETER);
        opticalDataChanged |= GainMediumBlockEntity.refreshNear(levelAccessor, pos);

        if (opticalDataChanged) {
            OpticalTraceCache.requestIntrinsicSourcesNear(levelAccessor, pos);
        }
    }

    public static void pistonMoved(LevelAccessor levelAccessor, BlockPos pistonPos, Direction direction) {
        if (!(levelAccessor instanceof ServerLevel serverLevel)) {
            return;
        }

        Set<BlockPos> changedPositions = new HashSet<>();
        changedPositions.add(pistonPos.immutable());

        for (int distance = 1; distance <= PISTON_RESCAN_DISTANCE; distance++) {
            changedPositions.add(pistonPos.relative(direction, distance).immutable());
        }

        for (BlockPos pos : changedPositions) {
            rescanChangedState(serverLevel, pos, "piston moved");
        }

        PENDING_PISTON_RESCANS.computeIfAbsent(serverLevel.dimension(), ignored -> new ArrayList<>())
                .add(new PendingPistonRescan(
                        Set.copyOf(changedPositions),
                        serverLevel.getGameTime() + 1L,
                        PISTON_POST_MOVE_RESCAN_TICKS
                ));
    }

    public static void processPendingPistonRescans(MinecraftServer server) {
        if (PENDING_PISTON_RESCANS.isEmpty()) {
            return;
        }

        Map<ResourceKey<Level>, List<PendingPistonRescan>> pendingByLevel =
                new LinkedHashMap<>(PENDING_PISTON_RESCANS);
        PENDING_PISTON_RESCANS.clear();

        for (Map.Entry<ResourceKey<Level>, List<PendingPistonRescan>> entry : pendingByLevel.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }

            List<PendingPistonRescan> deferred = new ArrayList<>();
            long gameTime = level.getGameTime();

            for (PendingPistonRescan pending : entry.getValue()) {
                if (pending.nextGameTime() > gameTime) {
                    deferred.add(pending);
                    continue;
                }

                for (BlockPos pos : pending.positions()) {
                    rescanChangedState(level, pos, "piston settled");
                }

                if (pending.remainingScans() > 1) {
                    deferred.add(new PendingPistonRescan(
                            pending.positions(),
                            gameTime + 1L,
                            pending.remainingScans() - 1
                    ));
                }
            }

            if (!deferred.isEmpty()) {
                PENDING_PISTON_RESCANS.computeIfAbsent(level.dimension(), ignored -> new ArrayList<>())
                        .addAll(deferred);
            }
        }
    }

    public static void clearPendingPistonRescans() {
        PENDING_PISTON_RESCANS.clear();
    }

    public static void clearPendingPistonRescans(LevelAccessor levelAccessor) {
        if (levelAccessor instanceof Level level) {
            PENDING_PISTON_RESCANS.remove(level.dimension());
        }
    }

    private static void rescanChangedState(ServerLevel level, BlockPos pos, String reason) {
        SurfaceCoatingData.removeAll(level, pos);
        markScatteringFieldSourceChanged(level, pos);
        OpticalWorldIndex.onBlockPlaced(level, pos);
        OpticalTraceCache.rememberSourceState(level, pos);
        OpticalTraceCache.markChanged(level, pos, OpticalDirtyKind.STRUCTURE);
        GainMediumBlockEntity.refreshNear(level, pos);
        OpticalTraceCache.requestIntrinsicSourcesNear(level, pos);
        OpticalNetworkIndex.markDirty(level);
        FiberNetworkIndex.onBlockPlaced(level, pos);

        BlockState currentState = level.getBlockState(pos);
        if (HolographicStorageMultiblock.isRelevantChange(level, pos, currentState)) {
            HolographicStorageMultiblock.scheduleRefresh(level, pos.immutable(), reason);
        }

        if (!(currentState.getBlock() instanceof MicrolizerPartBlock)
                && MicrolizerNetworkData.isRelevantPlacement(level, pos, currentState)) {
            MicrolizerNetworkData.scheduleRefresh(level, pos.immutable(), reason);
        }
    }

    private static boolean isFiberRelayOnly(BlockState state) {
        return state.getBlock() instanceof FiberRelayBlock;
    }

    private static void markScatteringFieldSourceChanged(LevelAccessor levelAccessor, BlockPos sourcePos) {
        OpticalFieldSources.invalidate(levelAccessor);

        boolean markedAffectedPath = false;

        for (BlockPos affectedPos : OpticalFieldSources.affectedPositionsAround(levelAccessor, sourcePos)) {
            markedAffectedPath |= OpticalTraceCache.markChanged(
                    levelAccessor,
                    affectedPos,
                    OpticalDirtyKind.TOPOLOGY
            );
        }

        if (markedAffectedPath) {
            OpticalNetworkIndex.markDirty(levelAccessor);
        }
    }

    private record PendingPistonRescan(Set<BlockPos> positions, long nextGameTime, int remainingScans) {
        private PendingPistonRescan {
            positions = Set.copyOf(positions);
        }
    }
}
