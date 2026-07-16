package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.function.LongUnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Immutable section-backed snapshot for one complete source-output projection. */
public final class ProjectionWorldSnapshot implements ProjectionWorldView {
    private final Long2ObjectOpenHashMap<SectionUse> sectionsByKey;
    private final int blockCount;
    private final int resolvedBlocks;
    private final int reusedBlocks;

    private ProjectionWorldSnapshot(
            Long2ObjectOpenHashMap<SectionUse> sectionsByKey,
            int blockCount,
            int resolvedBlocks,
            int reusedBlocks
    ) {
        if (resolvedBlocks + reusedBlocks != blockCount) {
            throw new IllegalArgumentException("Projection snapshot facts do not cover the complete cone");
        }
        this.sectionsByKey = new Long2ObjectOpenHashMap<>(sectionsByKey);
        this.blockCount = Math.max(0, blockCount);
        this.resolvedBlocks = Math.max(0, resolvedBlocks);
        this.reusedBlocks = Math.max(0, reusedBlocks);
    }

    public static ProjectionWorldSnapshot capture(
            Level level,
            BlockPos sourcePos,
            Direction travelDirection,
            BeamEnvelope envelope
    ) {
        return capture(level, sourcePos, travelDirection, envelope, ignored -> 0L);
    }

    public static ProjectionWorldSnapshot capture(
            Level level,
            BlockPos sourcePos,
            Direction travelDirection,
            BeamEnvelope envelope,
            LongUnaryOperator revisionAtSection
    ) {
        return capture(
                level,
                sourcePos,
                travelDirection,
                envelope,
                revisionAtSection,
                new ProjectionSectionSnapshotCache()
        );
    }

    public static ProjectionWorldSnapshot capture(
            Level level,
            BlockPos sourcePos,
            Direction travelDirection,
            BeamEnvelope envelope,
            LongUnaryOperator revisionAtSection,
            ProjectionSectionSnapshotCache sectionCache
    ) {
        LiveProjectionWorldView live = new LiveProjectionWorldView(level);
        Direction displayFace = travelDirection.getOpposite();
        Direction uDirection = SpotSurfaceFrame.uDirection(displayFace);
        Direction vDirection = SpotSurfaceFrame.vDirection(displayFace);
        BeamGeometryOps.RadiusPropagation propagation = BeamGeometryOps.prepareRadiusPropagation(envelope);
        Long2ObjectOpenHashMap<long[]> coverageBySection = new Long2ObjectOpenHashMap<>();
        int capturedBlocks = 0;

        for (int depth = 1; depth <= VoxelSpotProjector.MAX_PROJECTED_DEPTH; depth++) {
            BeamGeometryOps.RadiusPropagation depthPropagation = propagation.offset(depth);
            double maxRadius = VoxelSpotProjector.maxEnvelopeRadiusOverUnit(depthPropagation);
            int minTile = VoxelSpotProjector.projectedMinTile(maxRadius)
                    - VoxelSpotProjector.SNAPSHOT_HALO_TILES;
            int maxTile = VoxelSpotProjector.projectedMaxTile(maxRadius)
                    + VoxelSpotProjector.SNAPSHOT_HALO_TILES;
            BlockPos depthOrigin = sourcePos.relative(travelDirection, depth);
            for (int dv = minTile; dv <= maxTile; dv++) {
                for (int du = minTile; du <= maxTile; du++) {
                    BlockPos pos = new BlockPos(
                            depthOrigin.getX() + uDirection.getStepX() * du + vDirection.getStepX() * dv,
                            depthOrigin.getY() + uDirection.getStepY() * du + vDirection.getStepY() * dv,
                            depthOrigin.getZ() + uDirection.getStepZ() * du + vDirection.getStepZ() * dv
                    );
                    long sectionKey = ProjectionSectionSnapshot.sectionKey(pos);
                    long[] coverage = coverageBySection.computeIfAbsent(
                            sectionKey,
                            ignored -> new long[ProjectionSectionSnapshot.COVERAGE_WORDS]
                    );
                    int localIndex = ProjectionSectionSnapshot.localIndex(pos);
                    if (!ProjectionSectionSnapshot.covered(coverage, localIndex)) {
                        ProjectionSectionSnapshot.cover(coverage, localIndex);
                        capturedBlocks++;
                    }
                }
            }
        }

        Long2ObjectOpenHashMap<SectionUse> capturedSections = new Long2ObjectOpenHashMap<>();
        int resolved = 0;
        int reused = 0;
        for (var entry : coverageBySection.long2ObjectEntrySet()) {
            ProjectionSectionSnapshotCache.Capture capture = sectionCache.capture(
                    entry.getLongKey(),
                    revisionAtSection.applyAsLong(entry.getLongKey()),
                    entry.getValue(),
                    live
            );
            capturedSections.put(
                    entry.getLongKey(),
                    new SectionUse(capture.snapshot(), entry.getValue())
            );
            resolved += capture.resolvedBlocks();
            reused += capture.reusedBlocks();
        }
        return new ProjectionWorldSnapshot(capturedSections, capturedBlocks, resolved, reused);
    }

    @Override
    public ProjectionBlockFacts blockFacts(BlockPos pos) {
        SectionUse section = sectionsByKey.get(ProjectionSectionSnapshot.sectionKey(pos));
        int localIndex = ProjectionSectionSnapshot.localIndex(pos);
        if (section == null || !ProjectionSectionSnapshot.covered(section.coverage(), localIndex)) {
            throw new ProjectionSnapshotMissException(pos);
        }
        ProjectionBlockFacts facts = section.snapshot().facts(localIndex);
        if (facts == null) {
            throw new ProjectionSnapshotMissException(pos);
        }
        return facts;
    }

    public int blockCount() {
        return blockCount;
    }

    public int sectionCount() {
        return sectionsByKey.size();
    }

    public int resolvedBlocks() {
        return resolvedBlocks;
    }

    public int reusedBlocks() {
        return reusedBlocks;
    }

    public boolean versionsMatch(LongUnaryOperator revisionAtSection) {
        for (var entry : sectionsByKey.long2ObjectEntrySet()) {
            if (revisionAtSection.applyAsLong(entry.getLongKey())
                    != entry.getValue().snapshot().version()) {
                return false;
            }
        }
        return true;
    }

    private record SectionUse(ProjectionSectionSnapshot snapshot, long[] coverage) {
        private SectionUse {
            coverage = coverage.clone();
        }
    }
}
