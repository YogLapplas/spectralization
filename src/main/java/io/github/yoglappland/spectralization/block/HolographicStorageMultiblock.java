package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class HolographicStorageMultiblock {
    public static final int CORE_RADIUS = 4;
    public static final int CORE_BOX_SIZE = CORE_RADIUS * 2 + 1;
    public static final int MIN_CORE_DISTANCE = 10;

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
        for (BlockPos corePos : nearbyCorePositions(memberPos)) {
            BlockState coreState = level.getBlockState(corePos);
            if (!isActiveMainCore(coreState)) {
                continue;
            }

            if (scan(level, corePos).positions().contains(memberPos.immutable())) {
                return Optional.of(corePos.immutable());
            }
        }

        return Optional.empty();
    }

    public static StructureReport scan(Level level, BlockPos corePos) {
        BlockState coreState = level.getBlockState(corePos);
        boolean error = !isActiveMainCore(coreState);
        Set<BlockPos> recognized = new HashSet<>();
        Map<Block, Integer> blockCounts = new HashMap<>();

        if (error) {
            return new StructureReport(error, Set.of(), Map.of(), 0, 0);
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
                if (!isStorageMember(nextState) || isMainCore(nextState)) {
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

    private static boolean isStorageMember(BlockState state) {
        if (state.getBlock() instanceof HolographicStorageScreenBlock) {
            return state.getValue(HolographicStorageScreenBlock.CONNECTED);
        }

        return state.is(SpectralBlockTags.HOLOGRAPHIC_STORAGE);
    }

    private static boolean isInsideCoreBox(BlockPos corePos, BlockPos pos) {
        return Math.abs(pos.getX() - corePos.getX()) <= CORE_RADIUS
                && Math.abs(pos.getY() - corePos.getY()) <= CORE_RADIUS
                && Math.abs(pos.getZ() - corePos.getZ()) <= CORE_RADIUS;
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

    public record StructureReport(
            boolean error,
            Set<BlockPos> positions,
            Map<Block, Integer> blockCounts,
            int exposedCrystalFaces,
            int storageCrystalCount
    ) {
    }
}
