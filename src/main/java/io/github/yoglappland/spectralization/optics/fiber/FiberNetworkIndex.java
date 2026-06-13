package io.github.yoglappland.spectralization.optics.fiber;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class FiberNetworkIndex {
    private static final Map<ResourceKey<Level>, FiberNetworkIndex> INDEXES = new ConcurrentHashMap<>();

    private final Map<Long, FiberNode> nodesByPos = new HashMap<>();
    private FiberNetworkSnapshot snapshot = FiberNetworkSnapshot.EMPTY;
    private boolean dirty = true;
    private long epoch;

    public static void clearAll() {
        INDEXES.clear();
    }

    public static void clear(LevelAccessor level) {
        if (level instanceof ServerLevel serverLevel) {
            INDEXES.remove(serverLevel.dimension());
        }
    }

    public static void onBlockPlaced(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        get(serverLevel).registerIfNode(serverLevel, pos);
        markObstructionChanged(serverLevel);
    }

    public static void onBlockBroken(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        get(serverLevel).unregisterNode(pos);
        FiberNetworkData.removeConnectionsTouching(serverLevel, pos);
        markObstructionChanged(serverLevel);
    }

    public static void registerNode(LevelAccessor level, BlockPos pos, FiberNodeKind kind, FiberNodeProfile profile) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel).putNode(new FiberNode(pos, kind, profile));
        }
    }

    public static void unregisterNode(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel).unregisterNode(pos);
        }
    }

    public static void markObstructionChanged(LevelAccessor level) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel).markDirty();
            FiberNetworkData.removeBlockedConnections(serverLevel);
        }
    }

    public static void markDataChanged(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel).markDirty();
        }
    }

    public static Optional<FiberNode> nodeAt(ServerLevel level, BlockPos pos) {
        return snapshot(level).nodeAt(pos);
    }

    public static boolean isFiberNode(ServerLevel level, BlockPos pos) {
        return snapshot(level).hasNode(pos);
    }

    public static Optional<FiberRoute> findRoute(ServerLevel level, BlockPos start, BlockPos end) {
        return FiberPathfinder.findRoute(snapshot(level), start.immutable(), end.immutable());
    }

    public static FiberNetworkSnapshot snapshot(ServerLevel level) {
        return get(level).snapshotNow(level);
    }

    private static FiberNetworkIndex get(ServerLevel level) {
        return INDEXES.computeIfAbsent(level.dimension(), ignored -> new FiberNetworkIndex());
    }

    private FiberNetworkSnapshot snapshotNow(ServerLevel level) {
        if (!dirty) {
            return snapshot;
        }

        snapshot = FiberGraphCompiler.compile(
                level,
                nodesByPos.values(),
                FiberNetworkData.connections(level),
                epoch
        );
        dirty = false;
        return snapshot;
    }

    private void registerIfNode(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            unregisterNode(pos);
            return;
        }

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof FiberNodeBlock fiberNodeBlock) {
            putNode(new FiberNode(
                    pos,
                    fiberNodeBlock.fiberNodeKind(state, level, pos),
                    fiberNodeBlock.fiberNodeProfile(state, level, pos)
            ));
            return;
        }

        unregisterNode(pos);
    }

    private void putNode(FiberNode node) {
        FiberNode previous = nodesByPos.put(node.pos().asLong(), node);

        if (!node.equals(previous)) {
            markDirty();
        }
    }

    private void unregisterNode(BlockPos pos) {
        if (nodesByPos.remove(pos.asLong()) != null) {
            markDirty();
        }
    }

    private void markDirty() {
        dirty = true;
        epoch++;
    }

    private FiberNetworkIndex() {
    }
}
