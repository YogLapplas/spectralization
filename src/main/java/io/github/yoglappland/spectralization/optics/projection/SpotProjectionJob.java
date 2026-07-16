package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.BeamPacket;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Immutable worker input for one complete outgoing-node projection. */
public final class SpotProjectionJob {
    private final int nodeOrdinal;
    private final BlockPos sourcePos;
    private final Direction sourceFace;
    private final Direction travelDirection;
    private final ProjectionWorldSnapshot snapshot;
    private final BeamPacket profileTemplate;
    private final double beamPower;
    private final double coherentBeamPower;
    private final SpotProjectionResult cachedGeometry;
    private final int earliestInvalidatedDepth;
    private final long snapshotNanos;
    private final int snapshotBlocks;
    private final int snapshotSections;
    private final int snapshotResolvedBlocks;
    private final int snapshotReusedBlocks;

    public SpotProjectionJob(
            int nodeOrdinal,
            BlockPos sourcePos,
            Direction sourceFace,
            Direction travelDirection,
            ProjectionWorldSnapshot snapshot,
            BeamPacket profileTemplate,
            double beamPower,
            double coherentBeamPower,
            SpotProjectionResult cachedGeometry,
            int earliestInvalidatedDepth,
            long snapshotNanos,
            int snapshotBlocks,
            int snapshotSections,
            int snapshotResolvedBlocks,
            int snapshotReusedBlocks
    ) {
        this.nodeOrdinal = Math.max(0, nodeOrdinal);
        this.sourcePos = Objects.requireNonNull(sourcePos, "sourcePos").immutable();
        this.sourceFace = Objects.requireNonNull(sourceFace, "sourceFace");
        this.travelDirection = Objects.requireNonNull(travelDirection, "travelDirection");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.profileTemplate = Objects.requireNonNull(profileTemplate, "profileTemplate");
        this.beamPower = beamPower;
        this.coherentBeamPower = coherentBeamPower;
        this.cachedGeometry = cachedGeometry;
        this.earliestInvalidatedDepth = Math.max(1, earliestInvalidatedDepth);
        this.snapshotNanos = Math.max(0L, snapshotNanos);
        this.snapshotBlocks = Math.max(0, snapshotBlocks);
        this.snapshotSections = Math.max(0, snapshotSections);
        this.snapshotResolvedBlocks = Math.max(0, snapshotResolvedBlocks);
        this.snapshotReusedBlocks = Math.max(0, snapshotReusedBlocks);
    }

    public SpotProjectionJobResult compute() {
        long startedNanos = System.nanoTime();
        try {
            SpotProjectionResult result = VoxelSpotProjector.projectLightConeSpots(
                    snapshot,
                    sourcePos,
                    sourceFace,
                    travelDirection,
                    profileTemplate,
                    beamPower,
                    coherentBeamPower,
                    cachedGeometry,
                    earliestInvalidatedDepth,
                    false
            );
            return new SpotProjectionJobResult(
                    this, result, startedNanos, Math.max(0L, System.nanoTime() - startedNanos), null
            );
        } catch (Throwable failure) {
            return new SpotProjectionJobResult(
                    this, null, startedNanos, Math.max(0L, System.nanoTime() - startedNanos), failure
            );
        }
    }

    public int nodeOrdinal() {
        return nodeOrdinal;
    }

    public long snapshotNanos() {
        return snapshotNanos;
    }

    public int snapshotBlocks() {
        return snapshotBlocks;
    }

    public int snapshotSections() {
        return snapshotSections;
    }

    public int snapshotResolvedBlocks() {
        return snapshotResolvedBlocks;
    }

    public int snapshotReusedBlocks() {
        return snapshotReusedBlocks;
    }

    public boolean snapshotVersionMatches(java.util.function.LongUnaryOperator revisionAtSection) {
        return snapshot.versionsMatch(revisionAtSection);
    }
}
