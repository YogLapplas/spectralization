package io.github.yoglappland.spectralization.optics.projection;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/** Immutable copy-on-write page of projection facts for one 16^3 world section. */
final class ProjectionSectionSnapshot {
    static final int BLOCK_COUNT = 16 * 16 * 16;
    static final int COVERAGE_WORDS = BLOCK_COUNT / Long.SIZE;

    private final long sectionKey;
    private final int sectionX;
    private final int sectionY;
    private final int sectionZ;
    private final long version;
    private final ProjectionBlockFacts[] facts;

    private ProjectionSectionSnapshot(
            long sectionKey,
            int sectionX,
            int sectionY,
            int sectionZ,
            long version,
            ProjectionBlockFacts[] facts
    ) {
        this.sectionKey = sectionKey;
        this.sectionX = sectionX;
        this.sectionY = sectionY;
        this.sectionZ = sectionZ;
        this.version = version;
        this.facts = facts;
    }

    static ProjectionSectionSnapshot empty(long sectionKey, long version) {
        return new ProjectionSectionSnapshot(
                sectionKey,
                SectionPos.x(sectionKey),
                SectionPos.y(sectionKey),
                SectionPos.z(sectionKey),
                version,
                new ProjectionBlockFacts[BLOCK_COUNT]
        );
    }

    ProjectionSectionSnapshot withUpdates(ProjectionBlockFacts[] updatedFacts) {
        if (updatedFacts.length != BLOCK_COUNT) {
            throw new IllegalArgumentException("Projection section update has an invalid size");
        }
        return new ProjectionSectionSnapshot(
                sectionKey,
                sectionX,
                sectionY,
                sectionZ,
                version,
                updatedFacts
        );
    }

    ProjectionBlockFacts[] copyFacts() {
        return Arrays.copyOf(facts, facts.length);
    }

    ProjectionBlockFacts facts(int localIndex) {
        return facts[localIndex];
    }

    long version() {
        return version;
    }

    BlockPos blockPos(int localIndex) {
        return new BlockPos(
                (sectionX << 4) + localX(localIndex),
                (sectionY << 4) + localY(localIndex),
                (sectionZ << 4) + localZ(localIndex)
        );
    }

    long blockPositionKey(int localIndex) {
        return BlockPos.asLong(
                (sectionX << 4) + localX(localIndex),
                (sectionY << 4) + localY(localIndex),
                (sectionZ << 4) + localZ(localIndex)
        );
    }

    static long sectionKey(BlockPos pos) {
        return SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    static int localIndex(BlockPos pos) {
        return localIndex(pos.getX(), pos.getY(), pos.getZ());
    }

    static int localIndex(int blockX, int blockY, int blockZ) {
        return (blockY & 15) << 8 | (blockZ & 15) << 4 | (blockX & 15);
    }

    static boolean covered(long[] coverage, int localIndex) {
        return (coverage[localIndex >>> 6] & (1L << (localIndex & 63))) != 0L;
    }

    static void cover(long[] coverage, int localIndex) {
        coverage[localIndex >>> 6] |= 1L << (localIndex & 63);
    }

    static int verifyAddressing() {
        int checks = 0;
        for (int sectionX : new int[]{-2, 0, 3}) {
            for (int sectionY : new int[]{-4, 0, 5}) {
                for (int sectionZ : new int[]{-1, 0, 7}) {
                    ProjectionSectionSnapshot snapshot = empty(
                            SectionPos.asLong(sectionX, sectionY, sectionZ), 17L
                    );
                    for (int index = 0; index < BLOCK_COUNT; index++) {
                        if (localIndex(snapshot.blockPos(index)) != index
                                || sectionKey(snapshot.blockPos(index)) != snapshot.sectionKey) {
                            throw new IllegalStateException("Projection section addressing is not reversible");
                        }
                        checks++;
                    }
                }
            }
        }
        return checks;
    }

    private static int localX(int localIndex) {
        return localIndex & 15;
    }

    private static int localZ(int localIndex) {
        return localIndex >>> 4 & 15;
    }

    private static int localY(int localIndex) {
        return localIndex >>> 8 & 15;
    }
}
