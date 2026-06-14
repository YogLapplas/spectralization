package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class HolographicStorageMultiblock {
    public static final int CORE_RADIUS = 4;
    public static final int CORE_BOX_SIZE = CORE_RADIUS * 2 + 1;
    public static final int MIN_CORE_DISTANCE = 10;

    private static final int EXPOSED_FACE_RADIUS = CORE_RADIUS + 1;
    private static final Map<ResourceKey<Level>, LevelCache> CACHES = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<BlockPos, PendingRefresh>> PENDING_REFRESHES = new LinkedHashMap<>();

    private HolographicStorageMultiblock() {
    }

    public static boolean isCoreTooClose(Level level, BlockPos corePos) {
        for (BlockPos pos : BlockPos.betweenClosed(
                corePos.offset(-MIN_CORE_DISTANCE + 1, -MIN_CORE_DISTANCE + 1, -MIN_CORE_DISTANCE + 1),
                corePos.offset(MIN_CORE_DISTANCE - 1, MIN_CORE_DISTANCE - 1, MIN_CORE_DISTANCE - 1)
        )) {
            if (pos.equals(corePos)) {
                continue;
            }

            if (isMainCore(level.getBlockState(pos))) {
                return true;
            }
        }

        return false;
    }

    public static boolean isRecognizedCrystal(Level level, BlockPos crystalPos) {
        return findCoreForMember(level, crystalPos).isPresent();
    }

    public static Optional<BlockPos> findCoreForMember(Level level, BlockPos memberPos) {
        BlockPos immutableMemberPos = memberPos.immutable();
        if (!level.isClientSide) {
            LevelCache cache = cache(level);
            BlockPos cachedCore = cache.coreByMember.get(immutableMemberPos);
            if (cachedCore != null) {
                StructureReport cachedReport = scan(level, cachedCore);
                if (!cachedReport.error() && cachedReport.positions().contains(immutableMemberPos)) {
                    return Optional.of(cachedCore);
                }
            }
        }

        for (BlockPos corePos : nearbyCorePositions(memberPos)) {
            BlockState coreState = level.getBlockState(corePos);
            if (!isActiveMainCore(coreState)) {
                continue;
            }

            StructureReport report = scan(level, corePos);
            if (report.positions().contains(immutableMemberPos)) {
                return Optional.of(corePos.immutable());
            }
        }

        return Optional.empty();
    }

    public static StructureReport scan(Level level, BlockPos corePos) {
        BlockPos immutableCorePos = corePos.immutable();
        if (level.isClientSide) {
            return scanDirect(level, immutableCorePos);
        }

        LevelCache cache = cache(level);
        StructureReport cached = cache.reportsByCore.get(immutableCorePos);
        if (cached != null && isActiveMainCore(level.getBlockState(immutableCorePos))) {
            return cached;
        }

        StructureReport report = scanDirect(level, immutableCorePos);
        cache.removeCore(immutableCorePos);
        if (!report.error()) {
            cache.install(immutableCorePos, report);
        }
        return report;
    }

    public static void scheduleRefresh(Level level, BlockPos origin, String reason) {
        if (level.isClientSide) {
            return;
        }

        PENDING_REFRESHES.computeIfAbsent(level.dimension(), ignored -> new LinkedHashMap<>())
                .put(origin.immutable(), new PendingRefresh(origin.immutable(), reason));
    }

    public static void processPendingRefreshes(MinecraftServer server) {
        if (PENDING_REFRESHES.isEmpty()) {
            return;
        }

        Map<ResourceKey<Level>, Map<BlockPos, PendingRefresh>> pendingByLevel = new LinkedHashMap<>(PENDING_REFRESHES);
        PENDING_REFRESHES.clear();

        for (Map.Entry<ResourceKey<Level>, Map<BlockPos, PendingRefresh>> entry : pendingByLevel.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) {
                continue;
            }

            for (PendingRefresh refresh : entry.getValue().values()) {
                refreshNow(level, refresh.pos(), refresh.reason());
            }
        }
    }

    public static void clear(LevelAccessor accessor) {
        if (accessor instanceof Level level) {
            CACHES.remove(level.dimension());
            PENDING_REFRESHES.remove(level.dimension());
        }
    }

    public static void clearAll() {
        CACHES.clear();
        PENDING_REFRESHES.clear();
    }

    public static boolean isRelevantChange(LevelAccessor accessor, BlockPos pos, BlockState changedState) {
        if (!(accessor instanceof Level level) || level.isClientSide) {
            return false;
        }

        if (isStorageRelevantState(changedState)) {
            return true;
        }

        for (Direction direction : Direction.values()) {
            if (isStorageRelevantState(level.getBlockState(pos.relative(direction)))) {
                return true;
            }
        }

        return false;
    }

    public static void refreshNearbyStorageVisuals(Level level, BlockPos origin) {
        scheduleRefresh(level, origin, "storage visual refresh requested");
    }

    private static void refreshNow(ServerLevel level, BlockPos origin, String reason) {
        LevelCache cache = cache(level);
        Set<BlockPos> affectedCores = affectedCorePositions(level, cache, origin);
        Set<BlockPos> visualPositions = new HashSet<>();
        visualPositions.add(origin.immutable());
        for (Direction direction : Direction.values()) {
            visualPositions.add(origin.relative(direction).immutable());
        }

        int changedReports = 0;
        int removedReports = 0;
        int validReports = 0;

        for (BlockPos corePos : affectedCores) {
            StructureReport oldReport = cache.reportsByCore.get(corePos);
            StructureReport newReport = scanDirect(level, corePos);
            cache.removeCore(corePos);

            if (!newReport.error()) {
                cache.install(corePos, newReport);
                validReports++;
            } else if (oldReport != null) {
                removedReports++;
            }

            if (oldReport == null || !oldReport.equals(newReport)) {
                changedReports++;
            }

            visualPositions.add(corePos);
            if (oldReport != null) {
                addVisualPositions(visualPositions, oldReport.positions());
            }
            addVisualPositions(visualPositions, newReport.positions());
        }

        updateVisualStates(level, visualPositions);

        if (!affectedCores.isEmpty()) {
            Spectralization.LOGGER.info(
                    "Holographic storage multiblock refreshed in {} at {} by {}: affected core(s) {}, changed report(s) {}, valid report(s) {}, removed report(s) {}, visual position(s) {}",
                    level.dimension().location(),
                    formatPos(origin),
                    reason,
                    affectedCores.size(),
                    changedReports,
                    validReports,
                    removedReports,
                    visualPositions.size()
            );
        }
    }

    private static Set<BlockPos> affectedCorePositions(ServerLevel level, LevelCache cache, BlockPos origin) {
        Set<BlockPos> cores = new HashSet<>();
        BlockPos immutableOrigin = origin.immutable();

        for (BlockPos corePos : BlockPos.betweenClosed(
                origin.offset(-EXPOSED_FACE_RADIUS, -EXPOSED_FACE_RADIUS, -EXPOSED_FACE_RADIUS),
                origin.offset(EXPOSED_FACE_RADIUS, EXPOSED_FACE_RADIUS, EXPOSED_FACE_RADIUS)
        )) {
            if (isMainCore(level.getBlockState(corePos))) {
                cores.add(corePos.immutable());
            }
        }

        for (Map.Entry<BlockPos, StructureReport> entry : cache.reportsByCore.entrySet()) {
            if (isInsideExpandedCoreBox(entry.getKey(), immutableOrigin)
                    || entry.getValue().positions().contains(immutableOrigin)
                    || touchesAnyMember(entry.getValue().positions(), immutableOrigin)) {
                cores.add(entry.getKey());
            }
        }

        return cores;
    }

    private static StructureReport scanDirect(Level level, BlockPos corePos) {
        BlockState coreState = level.getBlockState(corePos);
        boolean error = !isActiveMainCore(coreState);
        Set<BlockPos> recognized = new HashSet<>();
        Map<Block, Integer> blockCounts = new HashMap<>();

        if (error) {
            return new StructureReport(true, Set.of(), Map.of(), 0, 0);
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos immutableCorePos = corePos.immutable();
        queue.add(immutableCorePos);
        recognized.add(immutableCorePos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockState currentState = level.getBlockState(current);
            blockCounts.merge(currentState.getBlock(), 1, Integer::sum);

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!isInsideCoreBox(corePos, next) || recognized.contains(next)) {
                    continue;
                }

                BlockState nextState = level.getBlockState(next);
                if (!isStorageMember(level, next, nextState, recognized) || isMainCore(nextState)) {
                    continue;
                }

                BlockPos immutableNext = next.immutable();
                recognized.add(immutableNext);
                queue.add(immutableNext);
            }
        }

        return new StructureReport(
                false,
                Set.copyOf(recognized),
                Map.copyOf(blockCounts),
                countExposedCrystalFaces(level, recognized),
                countStorageCrystals(level, recognized)
        );
    }

    public static boolean isMainCore(BlockState state) {
        return state.getBlock() instanceof HolographicStorageMainCoreBlock;
    }

    public static boolean isActiveMainCore(BlockState state) {
        return isMainCore(state) && !state.getValue(HolographicStorageCrystalBlock.ERROR);
    }

    private static boolean isStorageMember(Level level, BlockPos pos, BlockState state, Set<BlockPos> recognized) {
        if (state.getBlock() instanceof HolographicStorageScreenBlock) {
            BlockPos attachedPos = pos.relative(state.getValue(HolographicStorageScreenBlock.FACING));
            BlockState attachedState = level.getBlockState(attachedPos);
            return recognized.contains(attachedPos)
                    && attachedState.getBlock() instanceof HolographicStorageCrystalBlock
                    && !(attachedState.getBlock() instanceof HolographicStorageMainCoreBlock);
        }

        return state.is(SpectralBlockTags.HOLOGRAPHIC_STORAGE);
    }

    private static boolean isStorageRelevantState(BlockState state) {
        return state.getBlock() instanceof HolographicStorageCrystalBlock
                || state.getBlock() instanceof HolographicStorageScreenBlock
                || state.is(SpectralBlockTags.HOLOGRAPHIC_STORAGE);
    }

    private static boolean isInsideCoreBox(BlockPos corePos, BlockPos pos) {
        return Math.abs(pos.getX() - corePos.getX()) <= CORE_RADIUS
                && Math.abs(pos.getY() - corePos.getY()) <= CORE_RADIUS
                && Math.abs(pos.getZ() - corePos.getZ()) <= CORE_RADIUS;
    }

    private static boolean isInsideExpandedCoreBox(BlockPos corePos, BlockPos pos) {
        return Math.abs(pos.getX() - corePos.getX()) <= EXPOSED_FACE_RADIUS
                && Math.abs(pos.getY() - corePos.getY()) <= EXPOSED_FACE_RADIUS
                && Math.abs(pos.getZ() - corePos.getZ()) <= EXPOSED_FACE_RADIUS;
    }

    private static List<BlockPos> nearbyCorePositions(BlockPos pos) {
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos corePos : BlockPos.betweenClosed(
                pos.offset(-CORE_RADIUS, -CORE_RADIUS, -CORE_RADIUS),
                pos.offset(CORE_RADIUS, CORE_RADIUS, CORE_RADIUS)
        )) {
            positions.add(corePos.immutable());
        }

        return positions;
    }

    private static int countExposedCrystalFaces(Level level, Set<BlockPos> recognized) {
        int exposedFaces = 0;

        for (BlockPos pos : recognized) {
            Block block = level.getBlockState(pos).getBlock();
            if (!(block instanceof HolographicStorageCrystalBlock) || block instanceof HolographicStorageMainCoreBlock) {
                continue;
            }

            for (Direction direction : Direction.values()) {
                if (level.getBlockState(pos.relative(direction)).isAir()) {
                    exposedFaces++;
                }
            }
        }

        return exposedFaces;
    }

    private static int countStorageCrystals(Level level, Set<BlockPos> recognized) {
        int crystals = 0;

        for (BlockPos pos : recognized) {
            Block block = level.getBlockState(pos).getBlock();
            if (block instanceof HolographicStorageCrystalBlock && !(block instanceof HolographicStorageMainCoreBlock)) {
                crystals++;
            }
        }

        return crystals;
    }

    private static void updateVisualStates(Level level, Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            HolographicStorageCrystalBlock.refreshVisualState(level, pos);
            HolographicStorageScreenBlock.refreshVisualState(level, pos);
        }
    }

    private static void addVisualPositions(Set<BlockPos> visualPositions, Set<BlockPos> memberPositions) {
        for (BlockPos memberPos : memberPositions) {
            visualPositions.add(memberPos);
            for (Direction direction : Direction.values()) {
                visualPositions.add(memberPos.relative(direction).immutable());
            }
        }
    }

    private static boolean touchesAnyMember(Set<BlockPos> memberPositions, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (memberPositions.contains(pos.relative(direction))) {
                return true;
            }
        }

        return false;
    }

    private static LevelCache cache(Level level) {
        return CACHES.computeIfAbsent(level.dimension(), ignored -> new LevelCache());
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public record StructureReport(
            boolean error,
            Set<BlockPos> positions,
            Map<Block, Integer> blockCounts,
            int exposedCrystalFaces,
            int storageCrystalCount
    ) {
    }

    private static final class LevelCache {
        private final Map<BlockPos, StructureReport> reportsByCore = new HashMap<>();
        private final Map<BlockPos, BlockPos> coreByMember = new HashMap<>();

        private void install(BlockPos corePos, StructureReport report) {
            reportsByCore.put(corePos, report);
            for (BlockPos memberPos : report.positions()) {
                coreByMember.put(memberPos, corePos);
            }
        }

        private void removeCore(BlockPos corePos) {
            StructureReport oldReport = reportsByCore.remove(corePos);
            if (oldReport == null) {
                return;
            }

            for (BlockPos memberPos : oldReport.positions()) {
                if (corePos.equals(coreByMember.get(memberPos))) {
                    coreByMember.remove(memberPos);
                }
            }
        }
    }

    private record PendingRefresh(BlockPos pos, String reason) {
        private PendingRefresh {
            pos = pos.immutable();
        }
    }
}
