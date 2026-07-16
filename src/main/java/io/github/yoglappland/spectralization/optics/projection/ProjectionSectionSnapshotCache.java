package io.github.yoglappland.spectralization.optics.projection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;

/** Server-thread-owned bounded cache of immutable copy-on-write projection sections. */
public final class ProjectionSectionSnapshotCache {
    private static final int DEFAULT_MAX_SECTIONS = 256;

    private final int maxSections;
    private final Map<Long, ProjectionSectionSnapshot> sections;

    public ProjectionSectionSnapshotCache() {
        this(DEFAULT_MAX_SECTIONS);
    }

    ProjectionSectionSnapshotCache(int maxSections) {
        this.maxSections = Math.max(1, maxSections);
        this.sections = new LinkedHashMap<>(32, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ProjectionSectionSnapshot> eldest) {
                return size() > ProjectionSectionSnapshotCache.this.maxSections;
            }
        };
    }

    Capture capture(
            long sectionKey,
            long sectionVersion,
            long[] coverage,
            ProjectionWorldView liveWorld
    ) {
        Objects.requireNonNull(coverage, "coverage");
        Objects.requireNonNull(liveWorld, "liveWorld");
        if (coverage.length != ProjectionSectionSnapshot.COVERAGE_WORDS) {
            throw new IllegalArgumentException("Projection section coverage has an invalid size");
        }

        ProjectionSectionSnapshot current = sections.get(sectionKey);
        if (current == null || current.version() != sectionVersion) {
            current = ProjectionSectionSnapshot.empty(sectionKey, sectionVersion);
        }
        ProjectionBlockFacts[] updatedFacts = null;
        int resolved = 0;
        int reused = 0;

        for (int wordIndex = 0; wordIndex < coverage.length; wordIndex++) {
            long word = coverage[wordIndex];
            while (word != 0L) {
                int bit = Long.numberOfTrailingZeros(word);
                int localIndex = wordIndex * Long.SIZE + bit;
                ProjectionBlockFacts facts = current.facts(localIndex);
                if (facts != null) {
                    reused++;
                } else {
                    if (updatedFacts == null) {
                        updatedFacts = current.copyFacts();
                    }
                    updatedFacts[localIndex] = liveWorld.blockFacts(current.blockPos(localIndex));
                    resolved++;
                }
                word &= word - 1L;
            }
        }

        if (updatedFacts != null) {
            current = current.withUpdates(updatedFacts);
        }
        sections.put(sectionKey, current);
        return new Capture(current, resolved, reused);
    }

    public int size() {
        return sections.size();
    }

    static int verifyCopyOnWrite() {
        ProjectionSectionSnapshotCache cache = new ProjectionSectionSnapshotCache(2);
        BlockPos firstPos = new BlockPos(1, 2, 3);
        BlockPos secondPos = new BlockPos(4, 5, 6);
        long sectionKey = ProjectionSectionSnapshot.sectionKey(firstPos);
        int firstIndex = ProjectionSectionSnapshot.localIndex(firstPos);
        int secondIndex = ProjectionSectionSnapshot.localIndex(secondPos);
        long[] firstCoverage = new long[ProjectionSectionSnapshot.COVERAGE_WORDS];
        ProjectionSectionSnapshot.cover(firstCoverage, firstIndex);
        long[] expandedCoverage = firstCoverage.clone();
        ProjectionSectionSnapshot.cover(expandedCoverage, secondIndex);
        AtomicInteger reads = new AtomicInteger();
        ProjectionWorldView live = pos -> new ProjectionBlockFacts(
                true, true, List.of(), null, "verification_" + reads.incrementAndGet()
        );

        Capture first = cache.capture(sectionKey, 10L, firstCoverage, live);
        Capture reused = cache.capture(sectionKey, 10L, firstCoverage, live);
        Capture expanded = cache.capture(sectionKey, 10L, expandedCoverage, live);
        Capture changed = cache.capture(sectionKey, 11L, firstCoverage, live);
        if (first.resolvedBlocks() != 1 || first.reusedBlocks() != 0
                || reused.resolvedBlocks() != 0 || reused.reusedBlocks() != 1
                || expanded.resolvedBlocks() != 1 || expanded.reusedBlocks() != 1
                || changed.resolvedBlocks() != 1 || changed.reusedBlocks() != 0
                || reads.get() != 3
                || first.snapshot().facts(secondIndex) != null
                || expanded.snapshot().facts(secondIndex) == null
                || changed.snapshot().facts(secondIndex) != null
                || first.snapshot().version() != 10L
                || changed.snapshot().version() != 11L) {
            throw new IllegalStateException("Projection section copy-on-write or version reuse failed");
        }

        for (int sectionX = 1; sectionX <= 3; sectionX++) {
            BlockPos pos = new BlockPos(sectionX << 4, 0, 0);
            long key = ProjectionSectionSnapshot.sectionKey(pos);
            long[] coverage = new long[ProjectionSectionSnapshot.COVERAGE_WORDS];
            ProjectionSectionSnapshot.cover(coverage, ProjectionSectionSnapshot.localIndex(pos));
            cache.capture(key, sectionX, coverage, live);
        }
        if (cache.size() != 2) {
            throw new IllegalStateException("Projection section cache exceeded its configured bound");
        }
        return 12;
    }

    record Capture(ProjectionSectionSnapshot snapshot, int resolvedBlocks, int reusedBlocks) {
    }
}
