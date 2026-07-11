package io.github.yoglappland.spectralization.command;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalSpotTracker;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

final class SpotProjectionTestScene {
    static final int DEFAULT_DIVERGENCE_MILLI = 500;

    private static final int MIN_ALONG = -1;
    private static final int MAX_ALONG = 17;
    private static final int MIN_SIDE = -10;
    private static final int MAX_SIDE = 10;
    private static final int MIN_VERTICAL = -10;
    private static final int MAX_VERTICAL = 10;
    private static final int FIRST_RANDOM_DEPTH = 3;
    private static final int LAST_RANDOM_DEPTH = 13;
    private static final int SCREEN_DEPTH = 16;
    private static final int SOURCE_POWER_CENTI = 30_000;
    private static final int SOURCE_RADIUS_MILLI = 500;
    private static final int[] CACHE_POWER_SEQUENCE_CENTI = {
            7_500, 15_000, 30_000, 45_000, 22_500
    };
    private static final int[] CACHE_COLOR_SEQUENCE_BINS = {
            SpectralColorMap.VISIBLE_RED_BIN,
            SpectralColorMap.VISIBLE_GREEN_BIN,
            SpectralColorMap.VISIBLE_BLUE_BIN,
            SpectralColorMap.VISIBLE_MAGENTA_BIN,
            SpectralColorMap.VISIBLE_CYAN_BIN
    };
    private static final CoherenceKind SOURCE_COHERENCE = CoherenceKind.INCOHERENT;
    private static final long SIGNATURE_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long SIGNATURE_PRIME = 0x100000001b3L;
    private static final int SIGNATURE_VOXEL_RESOLUTION = 16;

    static BuildResult build(
            ServerLevel level,
            SpotTestLayout layout,
            long seed,
            double occupancy,
            boolean includeFixtures,
            int divergenceMilli
    ) {
        return build(level, layout, seed, occupancy, includeFixtures, divergenceMilli, true);
    }

    static BuildResult build(
            ServerLevel level,
            SpotTestLayout layout,
            long seed,
            double occupancy,
            boolean includeFixtures,
            int divergenceMilli,
            boolean computeSceneSignature
    ) {
        clear(level, layout);
        Random random = new Random(seed);
        int randomObstacles = placeRandomObstacles(level, layout, random, occupancy);
        int fixtures = includeFixtures ? placeRegressionFixtures(level, layout) : 0;
        int screenBlocks = placeScreen(level, layout);
        int sourceBlocks = placeSource(level, layout, divergenceMilli);
        long sceneSignature = computeSceneSignature ? relativeSceneSignature(level, layout) : 0L;
        refreshProjection(level);
        return new BuildResult(randomObstacles, fixtures, screenBlocks, sourceBlocks, sceneSignature);
    }

    static boolean validateVolume(CommandSourceStack source, ServerLevel level, SpotTestLayout layout) {
        int minY = layout.source().getY() + MIN_VERTICAL;
        int maxY = layout.source().getY() + MAX_VERTICAL;
        if (minY < level.getMinBuildHeight() || maxY >= level.getMaxBuildHeight()) {
            source.sendFailure(Component.literal(
                    "Spot test volume would leave the world's build height."
            ));
            return false;
        }

        for (int along = MIN_ALONG; along <= MAX_ALONG; along++) {
            for (int side = MIN_SIDE; side <= MAX_SIDE; side++) {
                if (!level.isLoaded(layout.at(along, side, 0))) {
                    source.sendFailure(Component.literal(
                            "Spot test volume crosses unloaded chunks. Move closer to the intended test area."
                    ));
                    return false;
                }
            }
        }
        return true;
    }

    static int clear(ServerLevel level, SpotTestLayout layout) {
        level.setBlock(layout.source(), Blocks.AIR.defaultBlockState(), 3);
        int cleared = 0;
        for (int along = MIN_ALONG; along <= MAX_ALONG; along++) {
            for (int side = MIN_SIDE; side <= MAX_SIDE; side++) {
                for (int vertical = MIN_VERTICAL; vertical <= MAX_VERTICAL; vertical++) {
                    BlockPos pos = layout.at(along, side, vertical);
                    if (!level.getBlockState(pos).isAir()) {
                        cleared++;
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        return cleared;
    }

    static int clearAllHorizontalDirections(ServerLevel level, SpotTestLayout layout) {
        LongOpenHashSet positions = new LongOpenHashSet(8_192);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            SpotTestLayout rotated = layout.withDirection(direction);
            for (int along = MIN_ALONG; along <= MAX_ALONG; along++) {
                for (int side = MIN_SIDE; side <= MAX_SIDE; side++) {
                    for (int vertical = MIN_VERTICAL; vertical <= MAX_VERTICAL; vertical++) {
                        positions.add(rotated.at(along, side, vertical).asLong());
                    }
                }
            }
        }

        int cleared = 0;
        for (long packedPos : positions) {
            BlockPos pos = BlockPos.of(packedPos);
            if (!level.getBlockState(pos).isAir()) {
                cleared++;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        return cleared;
    }

    static void refreshProjection(ServerLevel level) {
        OpticalSpotTracker.clear(level);
        OpticalTraceCache.clear(level);
        OpticalNetworkIndex.markDirty(level);
    }

    static int cachePowerCenti(int step) {
        return CACHE_POWER_SEQUENCE_CENTI[Math.floorMod(step, CACHE_POWER_SEQUENCE_CENTI.length)];
    }

    static int cacheColorBin(int step) {
        return CACHE_COLOR_SEQUENCE_BINS[Math.floorMod(step, CACHE_COLOR_SEQUENCE_BINS.length)];
    }

    static boolean setSourcePower(ServerLevel level, SpotTestLayout layout, int powerCenti) {
        if (!(level.getBlockEntity(layout.source()) instanceof CreativeLightSourceBlockEntity source)) {
            return false;
        }
        source.createDataAccess().set(CreativeLightSourceBlockEntity.DATA_POWER, powerCenti);
        return true;
    }

    static boolean setSourceColor(ServerLevel level, SpotTestLayout layout, int bin) {
        if (!(level.getBlockEntity(layout.source()) instanceof CreativeLightSourceBlockEntity source)) {
            return false;
        }
        ContainerData data = source.createDataAccess();
        data.set(CreativeLightSourceBlockEntity.DATA_REGION, SpectralRegion.VISIBLE.ordinal());
        data.set(CreativeLightSourceBlockEntity.DATA_BIN, bin);
        return true;
    }

    static double sourcePower() {
        return SOURCE_POWER_CENTI / (double) CreativeLightSourceBlockEntity.POWER_SCALE;
    }

    static double sourceRadius() {
        return SOURCE_RADIUS_MILLI / 1000.0D;
    }

    static String sourceCoherenceName() {
        return SOURCE_COHERENCE.name().toLowerCase(java.util.Locale.ROOT);
    }

    static CoherenceKind sourceCoherence() {
        return SOURCE_COHERENCE;
    }

    static void validateControlledVolumeDefinition() {
        int requiredScreenRadius = (int) Math.ceil(
                SOURCE_RADIUS_MILLI / 1000.0D
                        + DEFAULT_DIVERGENCE_MILLI / 1000.0D * SCREEN_DEPTH
        );
        if (MIN_SIDE > -requiredScreenRadius
                || MAX_SIDE < requiredScreenRadius
                || MIN_VERTICAL > -requiredScreenRadius
                || MAX_VERTICAL < requiredScreenRadius) {
            throw new IllegalStateException("Spot-test screen does not enclose the default beam cone");
        }
    }

    private static int placeRandomObstacles(
            ServerLevel level,
            SpotTestLayout layout,
            Random random,
            double occupancy
    ) {
        int placed = 0;
        for (int along = FIRST_RANDOM_DEPTH; along <= LAST_RANDOM_DEPTH; along++) {
            for (int side = -4; side <= 4; side++) {
                for (int vertical = -2; vertical <= 3; vertical++) {
                    if (random.nextDouble() >= occupancy) {
                        continue;
                    }
                    level.setBlock(layout.at(along, side, vertical), randomProjectionState(random, layout), 3);
                    placed++;
                }
            }
        }
        return placed;
    }

    private static int placeRegressionFixtures(ServerLevel level, SpotTestLayout layout) {
        int placed = 0;
        Direction lateral = layout.lateral();
        Direction oppositeLateral = lateral.getOpposite();

        placed += place(level, layout.at(5, -2, -1), stairState(lateral, Half.BOTTOM));
        placed += place(level, layout.at(5, -1, -1), stairState(oppositeLateral, Half.TOP));
        placed += place(level, layout.at(5, 0, -1), stairState(lateral, Half.BOTTOM));
        placed += place(level, layout.at(5, 1, -1), stairState(oppositeLateral, Half.TOP));

        placed += place(level, layout.at(8, -2, 1), slabState(SlabType.BOTTOM));
        placed += place(level, layout.at(8, -1, 1), slabState(SlabType.TOP));
        placed += place(level, layout.at(8, 1, 0), slabState(SlabType.BOTTOM));
        placed += place(level, layout.at(8, 2, 0), slabState(SlabType.TOP));

        placed += place(level, layout.at(11, -1, 0), Blocks.OAK_FENCE.defaultBlockState());
        placed += place(level, layout.at(11, 0, 0), Blocks.OAK_FENCE.defaultBlockState());
        placed += place(level, layout.at(11, 1, 0), Blocks.OAK_FENCE.defaultBlockState());
        placed += place(level, layout.at(11, 0, 1), Blocks.OAK_FENCE.defaultBlockState());

        placed += place(level, layout.at(13, -1, -1), stairState(layout.direction(), Half.BOTTOM));
        placed += place(level, layout.at(13, 0, 0), stairState(layout.direction().getOpposite(), Half.TOP));
        placed += place(level, layout.at(13, 1, 1), stairState(lateral, Half.BOTTOM));

        // Reproductions captured by boundary-missing diagnostics on 2026-07-11.
        // Keep these coordinates stable so structured events remain directly comparable.
        placed += place(level, layout.at(4, 3, 0), Blocks.OAK_FENCE.defaultBlockState());
        placed += place(level, layout.at(6, -4, 0), Blocks.NETHER_BRICK_FENCE.defaultBlockState());
        placed += place(level, layout.at(6, 4, -1), stairState(layout.direction(), Half.TOP));
        return placed;
    }

    private static int placeScreen(ServerLevel level, SpotTestLayout layout) {
        int placed = 0;
        for (int side = MIN_SIDE; side <= MAX_SIDE; side++) {
            for (int vertical = MIN_VERTICAL; vertical <= MAX_VERTICAL; vertical++) {
                level.setBlock(
                        layout.at(SCREEN_DEPTH, side, vertical),
                        Blocks.WHITE_CONCRETE.defaultBlockState(),
                        3
                );
                placed++;
            }
        }
        return placed;
    }

    private static int placeSource(ServerLevel level, SpotTestLayout layout, int divergenceMilli) {
        level.setBlock(
                layout.source(),
                Spectralization.CREATIVE_LIGHT_SOURCE.get()
                        .defaultBlockState()
                        .setValue(CreativeLightSourceBlock.FACING, layout.direction()),
                3
        );

        if (level.getBlockEntity(layout.source()) instanceof CreativeLightSourceBlockEntity source) {
            ContainerData data = source.createDataAccess();
            data.set(CreativeLightSourceBlockEntity.DATA_REGION, FrequencyKey.DEBUG_VISIBLE.region().ordinal());
            data.set(CreativeLightSourceBlockEntity.DATA_BIN, FrequencyKey.DEBUG_VISIBLE.bin());
            data.set(CreativeLightSourceBlockEntity.DATA_POWER, SOURCE_POWER_CENTI);
            data.set(CreativeLightSourceBlockEntity.DATA_COHERENCE, SOURCE_COHERENCE.ordinal());
            data.set(CreativeLightSourceBlockEntity.DATA_BEAM_MODEL, BeamModel.DIVERGING.ordinal());
            data.set(CreativeLightSourceBlockEntity.DATA_RADIUS_MILLI, SOURCE_RADIUS_MILLI);
            data.set(CreativeLightSourceBlockEntity.DATA_DIVERGENCE_MILLI, divergenceMilli);
            data.set(CreativeLightSourceBlockEntity.DATA_FOCUS_DISTANCE_MILLI, 0);
            data.set(CreativeLightSourceBlockEntity.DATA_MODE_M, 0);
            data.set(CreativeLightSourceBlockEntity.DATA_MODE_N, 0);
            for (int index = CreativeLightSourceBlockEntity.DATA_SPECTRUM_START;
                 index < CreativeLightSourceBlockEntity.DATA_COUNT;
                 index++) {
                data.set(index, 0);
            }
        }
        return 1;
    }

    private static BlockState randomProjectionState(Random random, SpotTestLayout layout) {
        int roll = random.nextInt(100);
        if (roll < 45) {
            return stairState(
                    layout.relativeHorizontal(random.nextInt(4)),
                    random.nextBoolean() ? Half.BOTTOM : Half.TOP
            );
        }
        if (roll < 72) {
            return slabState(random.nextBoolean() ? SlabType.BOTTOM : SlabType.TOP);
        }
        if (roll < 88) {
            return random.nextBoolean()
                    ? Blocks.OAK_FENCE.defaultBlockState()
                    : Blocks.NETHER_BRICK_FENCE.defaultBlockState();
        }
        return switch (random.nextInt(3)) {
            case 0 -> Blocks.STONE_BRICKS.defaultBlockState();
            case 1 -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
            default -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        };
    }

    private static BlockState stairState(Direction facing, Half half) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, half);
    }

    private static BlockState slabState(SlabType type) {
        return Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, type);
    }

    private static int place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
        return 1;
    }

    private static long relativeSceneSignature(ServerLevel level, SpotTestLayout layout) {
        long hash = SIGNATURE_OFFSET_BASIS;
        for (int along = MIN_ALONG; along <= MAX_ALONG; along++) {
            for (int side = MIN_SIDE; side <= MAX_SIDE; side++) {
                for (int vertical = MIN_VERTICAL; vertical <= MAX_VERTICAL; vertical++) {
                    BlockPos pos = layout.at(along, side, vertical);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    hash = mixSignature(hash, along);
                    hash = mixSignature(hash, side);
                    hash = mixSignature(hash, vertical);
                    hash = mixSignature(hash, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                    List<double[]> relativeBoxes = new ArrayList<>();
                    for (AABB box : state.getShape(level, pos).toAabbs()) {
                        relativeBoxes.add(relativeBox(box, layout));
                    }
                    hash = mixCanonicalShape(hash, relativeBoxes);
                }
            }
        }
        return hash;
    }

    private static long mixCanonicalShape(long hash, List<double[]> relativeBoxes) {
        hash = mixSignature(hash, SIGNATURE_VOXEL_RESOLUTION);
        long occupiedWord = 0L;
        int bitIndex = 0;
        int occupiedCells = 0;
        for (int along = 0; along < SIGNATURE_VOXEL_RESOLUTION; along++) {
            double alongCenter = (along + 0.5D) / SIGNATURE_VOXEL_RESOLUTION;
            for (int side = 0; side < SIGNATURE_VOXEL_RESOLUTION; side++) {
                double sideCenter = (side + 0.5D) / SIGNATURE_VOXEL_RESOLUTION;
                for (int vertical = 0; vertical < SIGNATURE_VOXEL_RESOLUTION; vertical++) {
                    double verticalCenter = (vertical + 0.5D) / SIGNATURE_VOXEL_RESOLUTION;
                    if (contains(relativeBoxes, alongCenter, sideCenter, verticalCenter)) {
                        occupiedWord |= 1L << bitIndex;
                        occupiedCells++;
                    }
                    bitIndex++;
                    if (bitIndex == Long.SIZE) {
                        hash = mixSignature(hash, occupiedWord);
                        occupiedWord = 0L;
                        bitIndex = 0;
                    }
                }
            }
        }
        if (bitIndex != 0) {
            hash = mixSignature(hash, occupiedWord);
        }
        return mixSignature(hash, occupiedCells);
    }

    static long canonicalShapeSignatureForTest(List<AABB> boxes) {
        List<double[]> canonicalBoxes = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            canonicalBoxes.add(new double[]{
                    box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ
            });
        }
        return mixCanonicalShape(SIGNATURE_OFFSET_BASIS, canonicalBoxes);
    }

    private static boolean contains(List<double[]> boxes, double along, double side, double vertical) {
        for (double[] box : boxes) {
            if (along > box[0] && along < box[3]
                    && side > box[1] && side < box[4]
                    && vertical > box[2] && vertical < box[5]) {
                return true;
            }
        }
        return false;
    }

    private static double[] relativeBox(AABB box, SpotTestLayout layout) {
        double minAlong = Double.POSITIVE_INFINITY;
        double minSide = Double.POSITIVE_INFINITY;
        double minVertical = Double.POSITIVE_INFINITY;
        double maxAlong = Double.NEGATIVE_INFINITY;
        double maxSide = Double.NEGATIVE_INFINITY;
        double maxVertical = Double.NEGATIVE_INFINITY;
        Direction alongDirection = layout.direction();
        Direction sideDirection = layout.lateral();

        for (double x : new double[]{box.minX, box.maxX}) {
            for (double y : new double[]{box.minY, box.maxY}) {
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    double centeredX = x - 0.5D;
                    double centeredY = y - 0.5D;
                    double centeredZ = z - 0.5D;
                    double along = 0.5D
                            + centeredX * alongDirection.getStepX()
                            + centeredY * alongDirection.getStepY()
                            + centeredZ * alongDirection.getStepZ();
                    double side = 0.5D
                            + centeredX * sideDirection.getStepX()
                            + centeredY * sideDirection.getStepY()
                            + centeredZ * sideDirection.getStepZ();
                    double relativeVertical = y;
                    minAlong = Math.min(minAlong, along);
                    minSide = Math.min(minSide, side);
                    minVertical = Math.min(minVertical, relativeVertical);
                    maxAlong = Math.max(maxAlong, along);
                    maxSide = Math.max(maxSide, side);
                    maxVertical = Math.max(maxVertical, relativeVertical);
                }
            }
        }
        return new double[]{minAlong, minSide, minVertical, maxAlong, maxSide, maxVertical};
    }

    private static long mixSignature(long hash, long value) {
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            hash ^= (value >>> shift) & 0xffL;
            hash *= SIGNATURE_PRIME;
        }
        return hash;
    }

    private static long mixSignature(long hash, String value) {
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= SIGNATURE_PRIME;
        }
        return hash;
    }

    record BuildResult(
            int randomObstacles,
            int fixtures,
            int screenBlocks,
            int sourceBlocks,
            long sceneSignature
    ) {
        int placed() {
            return randomObstacles + fixtures + screenBlocks + sourceBlocks;
        }
    }

    private SpotProjectionTestScene() {
    }
}
