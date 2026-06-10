package io.github.yoglappland.spectralization.optics.world;

import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.IntrinsicOpticalSources;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalReceiver;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalWorldIndex {
    private static final Map<ResourceKey<Level>, OpticalWorldIndex> INDEXES = new ConcurrentHashMap<>();

    private final LongSet geometryPositions = new LongOpenHashSet();
    private final LongSet sourcePositions = new LongOpenHashSet();
    private final LongSet elementPositions = new LongOpenHashSet();
    private final LongSet receiverPositions = new LongOpenHashSet();
    private final LongSet materialPositions = new LongOpenHashSet();
    private final LongSet fieldSourcePositions = new LongOpenHashSet();
    private final EnumMap<OpticalIndexLayer, LongSet> dirtyPositionsByLayer =
            new EnumMap<>(OpticalIndexLayer.class);
    private long geometryEpoch = 0L;
    private long topologyEpoch = 0L;
    private long intrinsicDataEpoch = 0L;
    private long derivedDataEpoch = 0L;
    private long lastHighPriorityUpdateTick = Long.MIN_VALUE;
    private long lastDerivedCommitTick = Long.MIN_VALUE;
    private boolean derivedInterrupted = false;

    private OpticalWorldIndex() {
        for (OpticalIndexLayer layer : OpticalIndexLayer.values()) {
            dirtyPositionsByLayer.put(layer, new LongOpenHashSet());
        }
    }

    public static OpticalWorldIndex get(ServerLevel level) {
        return INDEXES.computeIfAbsent(level.dimension(), ignored -> new OpticalWorldIndex());
    }

    public static void clearAll() {
        INDEXES.clear();
    }

    public static void onBlockPlaced(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel).rescanGeometry(serverLevel, pos, OpticalIndexLayer.GEOMETRY);
        }
    }

    public static void onBlockBroken(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel).removeGeometry(serverLevel, pos);
        }
    }

    public static void markChanged(LevelAccessor level, BlockPos pos, OpticalDirtyKind dirtyKind) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        OpticalWorldIndex index = get(serverLevel);

        switch (dirtyKind) {
            case STRUCTURE -> index.rescanGeometry(serverLevel, pos, OpticalIndexLayer.GEOMETRY);
            case TOPOLOGY -> index.markDirty(serverLevel, OpticalIndexLayer.TOPOLOGY, pos);
            case PARAMETER -> index.markParameterizedChange(serverLevel, pos);
            case SOURCE -> index.markDirty(serverLevel, OpticalIndexLayer.INTRINSIC_DATA, pos);
            case FIELD -> index.markDirty(serverLevel, OpticalIndexLayer.TOPOLOGY, pos);
            case CONFIG -> index.markAllDirty(serverLevel);
        }
    }

    public static boolean canRunDerived(ServerLevel level) {
        return get(level).canRunDerivedNow(level);
    }

    public static void markDerivedCommitted(ServerLevel level) {
        get(level).commitDerived(level);
    }

    public static void markDataChanged(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel).markDirty(serverLevel, OpticalIndexLayer.INTRINSIC_DATA, pos);
        }
    }

    public long geometryEpoch() {
        return geometryEpoch;
    }

    public long topologyEpoch() {
        return topologyEpoch;
    }

    public long intrinsicDataEpoch() {
        return intrinsicDataEpoch;
    }

    public long derivedDataEpoch() {
        return derivedDataEpoch;
    }

    public boolean derivedInterrupted() {
        return derivedInterrupted;
    }

    public int geometrySize() {
        return geometryPositions.size();
    }

    public int dirtyCount(OpticalIndexLayer layer) {
        return dirtyPositionsByLayer.get(layer).size();
    }

    private void rescanGeometry(ServerLevel level, BlockPos pos, OpticalIndexLayer dirtyLayer) {
        long encodedPos = pos.asLong();
        removePosition(encodedPos);

        if (!level.isLoaded(pos)) {
            markDirty(level, dirtyLayer, pos);
            return;
        }

        OpticalNodeClassification classification = classify(level.getBlockState(pos));

        if (classification.optical()) {
            geometryPositions.add(encodedPos);

            if (classification.source()) {
                sourcePositions.add(encodedPos);
            }

            if (classification.element()) {
                elementPositions.add(encodedPos);
            }

            if (classification.receiver()) {
                receiverPositions.add(encodedPos);
            }

            if (classification.material()) {
                materialPositions.add(encodedPos);
            }

            if (classification.fieldSource()) {
                fieldSourcePositions.add(encodedPos);
            }
        }

        markDirty(level, dirtyLayer, pos);
    }

    private void removeGeometry(ServerLevel level, BlockPos pos) {
        removePosition(pos.asLong());
        markDirty(level, OpticalIndexLayer.GEOMETRY, pos);
    }

    private void removePosition(long encodedPos) {
        geometryPositions.remove(encodedPos);
        sourcePositions.remove(encodedPos);
        elementPositions.remove(encodedPos);
        receiverPositions.remove(encodedPos);
        materialPositions.remove(encodedPos);
        fieldSourcePositions.remove(encodedPos);
    }

    private void markParameterizedChange(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            markDirty(level, OpticalIndexLayer.TOPOLOGY, pos);
            return;
        }

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof CmosSensorBlock || block instanceof PassThroughSensorBlock || block instanceof BeamProfilerBlock) {
            return;
        }

        if (IntrinsicOpticalSources.isSource(state)) {
            markDirty(level, OpticalIndexLayer.INTRINSIC_DATA, pos);
            return;
        }

        if (block instanceof OpticalElement
                || OpticalMaterialProfiles.isExplicitOpticalMaterial(state)
                || OpticalFieldSources.isScatteringFieldSource(state)) {
            markDirty(level, OpticalIndexLayer.TOPOLOGY, pos);
        }
    }

    private void markAllDirty(ServerLevel level) {
        long gameTime = level.getGameTime();
        geometryEpoch++;
        topologyEpoch++;
        intrinsicDataEpoch++;
        derivedDataEpoch++;
        lastHighPriorityUpdateTick = gameTime;
        derivedInterrupted = true;
    }

    private void markDirty(ServerLevel level, OpticalIndexLayer layer, BlockPos pos) {
        dirtyPositionsByLayer.get(layer).add(pos.asLong());

        switch (layer) {
            case GEOMETRY -> {
                geometryEpoch++;
                topologyEpoch++;
                intrinsicDataEpoch++;
            }
            case TOPOLOGY -> topologyEpoch++;
            case INTRINSIC_DATA -> intrinsicDataEpoch++;
            case DERIVED_DATA -> {
            }
        }

        derivedDataEpoch++;

        if (layer == OpticalIndexLayer.GEOMETRY || layer == OpticalIndexLayer.TOPOLOGY) {
            lastHighPriorityUpdateTick = level.getGameTime();
            derivedInterrupted = true;
        }
    }

    private boolean canRunDerivedNow(ServerLevel level) {
        if (!derivedInterrupted) {
            return true;
        }

        long quietTicks = SpectralizationConfig.opticalCompilerDirectRecompileQuietTicks();
        return level.getGameTime() - lastHighPriorityUpdateTick >= quietTicks;
    }

    private void commitDerived(ServerLevel level) {
        if (!canRunDerivedNow(level)) {
            return;
        }

        dirtyPositionsByLayer.get(OpticalIndexLayer.DERIVED_DATA).clear();
        derivedInterrupted = false;
        lastDerivedCommitTick = level.getGameTime();
    }

    private static OpticalNodeClassification classify(BlockState state) {
        Block block = state.getBlock();

        return new OpticalNodeClassification(
                IntrinsicOpticalSources.isSource(state),
                block instanceof OpticalElement,
                block instanceof OpticalReceiver || block instanceof CmosSensorBlock,
                OpticalMaterialProfiles.isExplicitOpticalMaterial(state),
                OpticalFieldSources.isScatteringFieldSource(state)
        );
    }
}
