package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

public final class SpotProjectionPerformanceTracker {
    public static final int DEFAULT_REPORT_SAMPLES = 16;
    public static final int MAX_REPORT_SAMPLES = 64;
    private static final Map<ServerLevel, ArrayDeque<Sample>> SAMPLES_BY_LEVEL = new WeakHashMap<>();

    public static synchronized void record(
            ServerLevel level,
            BlockPos sourcePos,
            Direction direction,
            long elapsedNanos,
            SpotProjectionResult result
    ) {
        ArrayDeque<Sample> samples = SAMPLES_BY_LEVEL.computeIfAbsent(level, ignored -> new ArrayDeque<>());
        samples.addLast(new Sample(
                level.getGameTime(),
                sourcePos.immutable(),
                direction,
                Math.max(0L, elapsedNanos),
                result.spots().size(),
                result.dependencies().size(),
                result.stats(),
                result.cacheMode(),
                result.appearanceTimings(),
                SpectralizationConfig.opticalCompilerDebugLog()
                        ? result.spots()
                        : List.of()
        ));

        while (samples.size() > MAX_REPORT_SAMPLES) {
            samples.removeFirst();
        }
    }

    public static synchronized Report report(ServerLevel level, int requestedSamples) {
        return report(level, null, null, requestedSamples);
    }

    public static synchronized Report report(
            ServerLevel level,
            BlockPos sourcePos,
            Direction direction,
            int requestedSamples
    ) {
        ArrayDeque<Sample> stored = SAMPLES_BY_LEVEL.get(level);

        if (stored == null || stored.isEmpty()) {
            return Report.EMPTY;
        }

        int count = Math.max(1, Math.min(MAX_REPORT_SAMPLES, requestedSamples));
        List<Sample> all = stored.stream()
                .filter(sample -> sourcePos == null || sample.sourcePos().equals(sourcePos))
                .filter(sample -> direction == null || sample.direction() == direction)
                .toList();
        if (all.isEmpty()) {
            return Report.EMPTY;
        }
        List<Sample> samples = all.subList(Math.max(0, all.size() - count), all.size());
        long[] elapsed = new long[samples.size()];
        double spots = 0.0D;
        double dependencies = 0.0D;
        double tiles = 0.0D;
        double projectableTiles = 0.0D;
        double sideWindows = 0.0D;
        double sideQuads = 0.0D;
        double appearanceBuilds = 0.0D;
        double appearanceHits = 0.0D;
        double integralCalls = 0.0D;
        double candidateTilesVisited = 0.0D;
        double sideScanNanos = 0.0D;
        double sideEmitNanos = 0.0D;
        double sidePatchNanos = 0.0D;
        double sideOcclusionIndexBuildNanos = 0.0D;
        double sideSameDepthSplitNanos = 0.0D;
        double sidePrefixQueryNanos = 0.0D;
        double sideRegionIntersectNanos = 0.0D;
        double sideCanonicalNormalizeNanos = 0.0D;
        double sideDebugAuditNanos = 0.0D;
        double surfaceAppearanceBuildNanos = 0.0D;
        double patchClipNanos = 0.0D;
        double spotRecordPackNanos = 0.0D;
        double frontPassNanos = 0.0D;
        double frontEmitNanos = 0.0D;
        double remainingNanos = 0.0D;
        double residualNanos = 0.0D;
        double appearanceTotalNanos = 0.0D;
        double appearancePrepareNanos = 0.0D;
        double appearanceSurfaceBuildNanos = 0.0D;
        double appearanceRecordUpdateNanos = 0.0D;
        double appearanceResidualNanos = 0.0D;
        double appearanceTemplates = 0.0D;
        double appearanceUniqueSurfaces = 0.0D;
        int timedSamples = 0;
        int geometryTimedSamples = 0;
        int appearanceTimedSamples = 0;
        int fullRebuildSamples = 0;
        int appearanceOnlySamples = 0;

        for (int index = 0; index < samples.size(); index++) {
            Sample sample = samples.get(index);
            elapsed[index] = sample.elapsedNanos();
            SpotProjectionResult.Stats stats = sample.stats();
            SpotProjectionResult.SideDiagnostics side = stats.sideDiagnostics();
            SpotProjectionResult.OptimizationStats optimization = stats.optimization();
            SpotProjectionResult.StageTimings timings = stats.timings();
            SpotProjectionResult.AppearanceTimings appearance = sample.appearanceTimings();
            if (sample.cacheMode() == SpotProjectionResult.CacheMode.FULL_REBUILD) {
                fullRebuildSamples++;
            } else if (sample.cacheMode() == SpotProjectionResult.CacheMode.APPEARANCE_ONLY) {
                appearanceOnlySamples++;
            }
            spots += sample.spots();
            dependencies += sample.dependencies();
            tiles += stats.scannedTiles();
            projectableTiles += stats.projectableTiles();
            sideWindows += stats.sideWindowCandidates();
            sideQuads += side.internalEmittedQuads() + side.externalEmittedQuads();
            appearanceBuilds += optimization.surfaceAppearanceBuilds();
            appearanceHits += optimization.surfaceAppearanceCacheHits();
            integralCalls += optimization.footprintIntegralCalls();
            candidateTilesVisited += optimization.sideCandidateTilesVisited();

            if (hasStageTimings(timings)) {
                timedSamples++;
                geometryTimedSamples++;
                sideScanNanos += timings.sideScanNanos();
                sideEmitNanos += timings.sideEmitNanos();
                sidePatchNanos += timings.sidePatchEmitNanos();
                sideOcclusionIndexBuildNanos += timings.sideOcclusionIndexBuildNanos();
                sideSameDepthSplitNanos += timings.sideSameDepthSplitNanos();
                sidePrefixQueryNanos += timings.sidePrefixQueryNanos();
                sideRegionIntersectNanos += timings.sideRegionIntersectNanos();
                sideCanonicalNormalizeNanos += timings.sideCanonicalNormalizeNanos();
                sideDebugAuditNanos += timings.sideDebugAuditNanos();
                surfaceAppearanceBuildNanos += timings.surfaceAppearanceBuildNanos();
                patchClipNanos += timings.patchClipNanos();
                spotRecordPackNanos += timings.spotRecordPackNanos();
                frontPassNanos += timings.frontPassNanos();
                frontEmitNanos += timings.frontEmitNanos();
                remainingNanos += timings.fullOccupancyNanos();
                long attributedNanos = timings.tileRangeNanos()
                        + timings.projectionRectNanos()
                        + timings.blockLookupNanos()
                        + timings.projectableCheckNanos()
                        + timings.planeWindowNanos()
                        + timings.frontPassNanos()
                        + timings.sideScanNanos()
                        + timings.fullOccupancyNanos()
                        + timings.occlusionAddNanos();
                residualNanos += Math.max(0L, sample.elapsedNanos() - attributedNanos);
            }
            if (appearance.totalNanos() > 0L) {
                timedSamples++;
                appearanceTimedSamples++;
                appearanceTotalNanos += appearance.totalNanos();
                appearancePrepareNanos += appearance.prepareNanos();
                appearanceSurfaceBuildNanos += appearance.surfaceBuildNanos();
                appearanceRecordUpdateNanos += appearance.recordUpdateNanos();
                appearanceResidualNanos += Math.max(0L, sample.elapsedNanos() - appearance.totalNanos());
                appearanceTemplates += appearance.templates();
                appearanceUniqueSurfaces += appearance.uniqueSurfaces();
            }
        }

        Arrays.sort(elapsed);
        Sample latest = samples.get(samples.size() - 1);
        double divisor = samples.size();
        double geometryTimedDivisor = Math.max(1, geometryTimedSamples);
        double appearanceTimedDivisor = Math.max(1, appearanceTimedSamples);
        return new Report(
                samples.size(),
                timedSamples,
                geometryTimedSamples,
                appearanceTimedSamples,
                latest.tick(),
                latest.sourcePos(),
                latest.direction(),
                nanosToMicros(elapsed[0]),
                nanosToMicros(average(elapsed)),
                nanosToMicros(percentile(elapsed, 0.50D)),
                nanosToMicros(percentile(elapsed, 0.95D)),
                nanosToMicros(elapsed[elapsed.length - 1]),
                spots / divisor,
                dependencies / divisor,
                tiles / divisor,
                projectableTiles / divisor,
                sideWindows / divisor,
                sideQuads / divisor,
                appearanceBuilds / divisor,
                appearanceHits / divisor,
                integralCalls / divisor,
                candidateTilesVisited / divisor,
                nanosToMicros(sideScanNanos / geometryTimedDivisor),
                nanosToMicros(sideEmitNanos / geometryTimedDivisor),
                nanosToMicros(sidePatchNanos / geometryTimedDivisor),
                nanosToMicros(sideOcclusionIndexBuildNanos / geometryTimedDivisor),
                nanosToMicros(sideSameDepthSplitNanos / geometryTimedDivisor),
                nanosToMicros(sidePrefixQueryNanos / geometryTimedDivisor),
                nanosToMicros(sideRegionIntersectNanos / geometryTimedDivisor),
                nanosToMicros(sideCanonicalNormalizeNanos / geometryTimedDivisor),
                nanosToMicros(sideDebugAuditNanos / geometryTimedDivisor),
                nanosToMicros(surfaceAppearanceBuildNanos / geometryTimedDivisor),
                nanosToMicros(patchClipNanos / geometryTimedDivisor),
                nanosToMicros(spotRecordPackNanos / geometryTimedDivisor),
                nanosToMicros(frontPassNanos / geometryTimedDivisor),
                nanosToMicros(frontEmitNanos / geometryTimedDivisor),
                nanosToMicros(remainingNanos / geometryTimedDivisor),
                nanosToMicros(residualNanos / geometryTimedDivisor),
                nanosToMicros(appearanceTotalNanos / appearanceTimedDivisor),
                nanosToMicros(appearancePrepareNanos / appearanceTimedDivisor),
                nanosToMicros(appearanceSurfaceBuildNanos / appearanceTimedDivisor),
                nanosToMicros(appearanceRecordUpdateNanos / appearanceTimedDivisor),
                nanosToMicros(appearanceResidualNanos / appearanceTimedDivisor),
                appearanceTemplates / appearanceTimedDivisor,
                appearanceUniqueSurfaces / appearanceTimedDivisor,
                fullRebuildSamples,
                appearanceOnlySamples
        );
    }

    public static synchronized SpotProjectionResult.Stats latestStats(
            ServerLevel level,
            BlockPos sourcePos,
            Direction direction
    ) {
        ArrayDeque<Sample> stored = SAMPLES_BY_LEVEL.get(level);
        if (stored == null || stored.isEmpty()) {
            return SpotProjectionResult.Stats.EMPTY;
        }

        Sample[] samples = stored.toArray(Sample[]::new);
        for (int index = samples.length - 1; index >= 0; index--) {
            Sample sample = samples[index];
            if (sample.sourcePos().equals(sourcePos) && sample.direction() == direction) {
                return sample.stats();
            }
        }
        return SpotProjectionResult.Stats.EMPTY;
    }

    public static synchronized OutputFingerprint latestOutputFingerprint(
            ServerLevel level,
            BlockPos sourcePos,
            Direction direction
    ) {
        ArrayDeque<Sample> stored = SAMPLES_BY_LEVEL.get(level);
        if (stored == null || stored.isEmpty()) {
            return OutputFingerprint.EMPTY;
        }

        Sample[] samples = stored.toArray(Sample[]::new);
        for (int index = samples.length - 1; index >= 0; index--) {
            Sample sample = samples[index];
            if (sample.sourcePos().equals(sourcePos) && sample.direction() == direction) {
                return sample.spotsSnapshot().isEmpty()
                        ? OutputFingerprint.EMPTY
                        : fingerprintOutputForDiagnostics(sourcePos, direction, sample.spotsSnapshot());
            }
        }
        return OutputFingerprint.EMPTY;
    }

    public static synchronized void reset(ServerLevel level) {
        SAMPLES_BY_LEVEL.remove(level);
    }

    public static synchronized void reset(ServerLevel level, BlockPos sourcePos, Direction direction) {
        ArrayDeque<Sample> stored = SAMPLES_BY_LEVEL.get(level);
        if (stored == null) {
            return;
        }

        stored.removeIf(sample -> sample.sourcePos().equals(sourcePos) && sample.direction() == direction);
        if (stored.isEmpty()) {
            SAMPLES_BY_LEVEL.remove(level);
        }
    }

    public static void log(ServerLevel level, Report report) {
        if (report.empty()) {
            return;
        }
        OutputFingerprint outputFingerprint = latestOutputFingerprint(
                level, report.latestSource(), report.latestDirection()
        );

        SpectralDiagnostics.event(level, "spot_projection", "performance_report")
                .field("samples", report.samples())
                .field("timed_samples", report.timedSamples())
                .field("geometry_timed_samples", report.geometryTimedSamples())
                .field("appearance_timed_samples", report.appearanceTimedSamples())
                .field("full_rebuild_samples", report.fullRebuildSamples())
                .field("appearance_only_samples", report.appearanceOnlySamples())
                .field("elapsed_min_us", report.elapsedMinUs())
                .field("elapsed_avg_us", report.elapsedAverageUs())
                .field("elapsed_p50_us", report.elapsedP50Us())
                .field("elapsed_p95_us", report.elapsedP95Us())
                .field("elapsed_max_us", report.elapsedMaxUs())
                .field("spots_avg", report.spotsAverage())
                .field("dependencies_avg", report.dependenciesAverage())
                .field("tiles_avg", report.tilesAverage())
                .field("projectable_tiles_avg", report.projectableTilesAverage())
                .field("side_windows_avg", report.sideWindowsAverage())
                .field("side_quads_avg", report.sideQuadsAverage())
                .field("appearance_builds_avg", report.appearanceBuildsAverage())
                .field("appearance_hits_avg", report.appearanceHitsAverage())
                .field("footprint_integrals_avg", report.integralCallsAverage())
                .field("side_candidate_tiles_avg", report.candidateTilesVisitedAverage())
                .field("side_scan_avg_us", report.sideScanAverageUs())
                .field("side_emit_avg_us", report.sideEmitAverageUs())
                .field("side_patch_avg_us", report.sidePatchAverageUs())
                .field("side_occlusion_index_build_avg_us", report.sideOcclusionIndexBuildAverageUs())
                .field("side_same_depth_split_avg_us", report.sideSameDepthSplitAverageUs())
                .field("side_prefix_query_avg_us", report.sidePrefixQueryAverageUs())
                .field("side_region_intersect_avg_us", report.sideRegionIntersectAverageUs())
                .field("side_canonical_normalize_avg_us", report.sideCanonicalNormalizeAverageUs())
                .field("side_debug_audit_avg_us", report.sideDebugAuditAverageUs())
                .field("surface_appearance_build_avg_us", report.surfaceAppearanceBuildAverageUs())
                .field("patch_clip_avg_us", report.patchClipAverageUs())
                .field("spot_record_pack_avg_us", report.spotRecordPackAverageUs())
                .field("front_pass_avg_us", report.frontPassAverageUs())
                .field("front_emit_avg_us", report.frontEmitAverageUs())
                .field("remaining_avg_us", report.remainingAverageUs())
                .field("projection_residual_avg_us", report.projectionResidualAverageUs())
                .field("appearance_total_avg_us", report.appearanceTotalAverageUs())
                .field("appearance_prepare_avg_us", report.appearancePrepareAverageUs())
                .field("appearance_surface_build_avg_us", report.appearanceSurfaceBuildAverageUs())
                .field("appearance_record_update_avg_us", report.appearanceRecordUpdateAverageUs())
                .field("appearance_residual_avg_us", report.appearanceResidualAverageUs())
                .field("appearance_templates_avg", report.appearanceTemplatesAverage())
                .field("appearance_unique_surfaces_avg", report.appearanceUniqueSurfacesAverage())
                .field("output_coverage_signature", Long.toUnsignedString(
                        outputFingerprint.coverageSignature(), 16
                ))
                .field("output_fragmentation_signature", Long.toUnsignedString(
                        outputFingerprint.fragmentationSignature(), 16
                ))
                .write();
    }

    private static boolean hasStageTimings(SpotProjectionResult.StageTimings timings) {
        return timings.sideScanNanos() > 0L
                || timings.frontEmitNanos() > 0L
                || timings.fullOccupancyNanos() > 0L;
    }

    private static long average(long[] values) {
        long sum = 0L;

        for (long value : values) {
            sum += value;
        }

        return values.length == 0 ? 0L : sum / values.length;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    private static double nanosToMicros(double nanos) {
        return nanos / 1_000.0D;
    }

    public static OutputFingerprint fingerprintOutputForDiagnostics(
            BlockPos sourcePos,
            Direction direction,
            List<SpotRecord> spots
    ) {
        Direction lateral = direction.getClockWise();
        Map<OutputSurfaceKey, MutableOutputSurface> grouped = new HashMap<>();
        for (SpotRecord spot : spots) {
            if (!spot.visible()) {
                continue;
            }
            int dx = spot.pos().getX() - sourcePos.getX();
            int dy = spot.pos().getY() - sourcePos.getY();
            int dz = spot.pos().getZ() - sourcePos.getZ();
            OutputSurfaceKey key = new OutputSurfaceKey(
                    dx * direction.getStepX() + dz * direction.getStepZ(),
                    dx * lateral.getStepX() + dz * lateral.getStepZ(),
                    dy,
                    spot.face().getStepX() * direction.getStepX()
                            + spot.face().getStepZ() * direction.getStepZ(),
                    spot.face().getStepX() * lateral.getStepX()
                            + spot.face().getStepZ() * lateral.getStepZ(),
                    spot.face().getStepY()
            );
            grouped.computeIfAbsent(key, ignored -> new MutableOutputSurface()).add(spot);
        }

        List<OutputSurface> surfaces = grouped.entrySet().stream()
                .map(entry -> entry.getValue().freeze(entry.getKey()))
                .sorted(Comparator.comparing(OutputSurface::key))
                .toList();
        long coverage = 0xcbf29ce484222325L;
        long fragmentation = 0xcbf29ce484222325L;
        for (OutputSurface surface : surfaces) {
            coverage = mixSurfaceKey(coverage, surface.key());
            coverage = mix(coverage, surface.sliceArea());
            coverage = mix(coverage, surface.textureArea());
            coverage = mix(coverage, surface.textureMomentU());
            coverage = mix(coverage, surface.textureMomentV());
            coverage = mix(coverage, surface.quadAreaTwice());
            coverage = mix(coverage, surface.quadTextureAreaTwice());

            fragmentation = mixSurfaceKey(fragmentation, surface.key());
            fragmentation = mix(fragmentation, surface.records());
            fragmentation = mix(fragmentation, surface.centeredRecords());
            fragmentation = mix(fragmentation, surface.sliceRecords());
            fragmentation = mix(fragmentation, surface.quadRecords());
        }
        coverage = mix(coverage, surfaces.size());
        fragmentation = mix(fragmentation, surfaces.size());
        return new OutputFingerprint(coverage, fragmentation, surfaces);
    }

    private static long mixSurfaceKey(long hash, OutputSurfaceKey key) {
        hash = mix(hash, key.along());
        hash = mix(hash, key.side());
        hash = mix(hash, key.vertical());
        hash = mix(hash, key.faceAlong());
        hash = mix(hash, key.faceSide());
        return mix(hash, key.faceVertical());
    }

    private static long mix(long hash, long value) {
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            hash ^= (value >>> shift) & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long canonicalQuadAreaTwice(SpotRecord spot) {
        int[] a;
        int[] b;
        if (spot.face().getAxis() == Direction.Axis.X) {
            a = new int[]{spot.quadY0(), spot.quadY1(), spot.quadY2(), spot.quadY3()};
            b = new int[]{spot.quadZ0(), spot.quadZ1(), spot.quadZ2(), spot.quadZ3()};
        } else if (spot.face().getAxis() == Direction.Axis.Y) {
            a = new int[]{spot.quadX0(), spot.quadX1(), spot.quadX2(), spot.quadX3()};
            b = new int[]{spot.quadZ0(), spot.quadZ1(), spot.quadZ2(), spot.quadZ3()};
        } else {
            a = new int[]{spot.quadX0(), spot.quadX1(), spot.quadX2(), spot.quadX3()};
            b = new int[]{spot.quadY0(), spot.quadY1(), spot.quadY2(), spot.quadY3()};
        }
        return polygonAreaTwice(quantizeQuadCoordinates(a), quantizeQuadCoordinates(b));
    }

    private static long canonicalQuadTextureAreaTwice(SpotRecord spot) {
        return polygonAreaTwice(
                quantizeQuadCoordinates(new int[]{
                        spot.quadTextureU0(), spot.quadTextureU1(),
                        spot.quadTextureU2(), spot.quadTextureU3()
                }),
                quantizeQuadCoordinates(new int[]{
                        spot.quadTextureV0(), spot.quadTextureV1(),
                        spot.quadTextureV2(), spot.quadTextureV3()
                })
        );
    }

    private static long rawQuadAreaTwice(SpotRecord spot) {
        int[] a;
        int[] b;
        if (spot.face().getAxis() == Direction.Axis.X) {
            a = new int[]{spot.quadY0(), spot.quadY1(), spot.quadY2(), spot.quadY3()};
            b = new int[]{spot.quadZ0(), spot.quadZ1(), spot.quadZ2(), spot.quadZ3()};
        } else if (spot.face().getAxis() == Direction.Axis.Y) {
            a = new int[]{spot.quadX0(), spot.quadX1(), spot.quadX2(), spot.quadX3()};
            b = new int[]{spot.quadZ0(), spot.quadZ1(), spot.quadZ2(), spot.quadZ3()};
        } else {
            a = new int[]{spot.quadX0(), spot.quadX1(), spot.quadX2(), spot.quadX3()};
            b = new int[]{spot.quadY0(), spot.quadY1(), spot.quadY2(), spot.quadY3()};
        }
        return polygonAreaTwice(a, b);
    }

    private static long rawQuadTextureAreaTwice(SpotRecord spot) {
        return polygonAreaTwice(
                new int[]{spot.quadTextureU0(), spot.quadTextureU1(), spot.quadTextureU2(), spot.quadTextureU3()},
                new int[]{spot.quadTextureV0(), spot.quadTextureV1(), spot.quadTextureV2(), spot.quadTextureV3()}
        );
    }

    private static int[] quantizeQuadCoordinates(int[] coordinates) {
        int[] quantized = new int[coordinates.length];
        for (int index = 0; index < coordinates.length; index++) {
            quantized[index] = (coordinates[index] * SpotRecord.SLICE_QUANTIZATION_LEVEL
                    + SpotRecord.QUAD_QUANTIZATION_LEVEL / 2)
                    / SpotRecord.QUAD_QUANTIZATION_LEVEL;
        }
        return quantized;
    }

    private static long polygonAreaTwice(int[] a, int[] b) {
        long areaTwice = 0L;
        for (int index = 0; index < 4; index++) {
            int next = (index + 1) & 3;
            areaTwice += (long) a[index] * b[next] - (long) a[next] * b[index];
        }
        return Math.abs(areaTwice);
    }

    private record Sample(
            long tick,
            BlockPos sourcePos,
            Direction direction,
            long elapsedNanos,
            int spots,
            int dependencies,
            SpotProjectionResult.Stats stats,
            SpotProjectionResult.CacheMode cacheMode,
            SpotProjectionResult.AppearanceTimings appearanceTimings,
            List<SpotRecord> spotsSnapshot
    ) {
        private Sample {
            spotsSnapshot = List.copyOf(spotsSnapshot);
        }
    }

    public record OutputFingerprint(
            long coverageSignature,
            long fragmentationSignature,
            List<OutputSurface> surfaces
    ) {
        public static final OutputFingerprint EMPTY = new OutputFingerprint(0L, 0L, List.of());

        public OutputFingerprint {
            surfaces = List.copyOf(surfaces);
        }
    }

    public record OutputSurfaceKey(
            int along,
            int side,
            int vertical,
            int faceAlong,
            int faceSide,
            int faceVertical
    ) implements Comparable<OutputSurfaceKey> {
        @Override
        public int compareTo(OutputSurfaceKey other) {
            int result = Integer.compare(along, other.along);
            if (result == 0) result = Integer.compare(side, other.side);
            if (result == 0) result = Integer.compare(vertical, other.vertical);
            if (result == 0) result = Integer.compare(faceAlong, other.faceAlong);
            if (result == 0) result = Integer.compare(faceSide, other.faceSide);
            if (result == 0) result = Integer.compare(faceVertical, other.faceVertical);
            return result;
        }

        public String summary() {
            return "pos=" + along + "," + side + "," + vertical
                    + ",face=" + faceAlong + "," + faceSide + "," + faceVertical;
        }
    }

    public record OutputSurface(
            OutputSurfaceKey key,
            int records,
            int centeredRecords,
            int sliceRecords,
            int quadRecords,
            long sliceArea,
            long textureArea,
            long textureMomentU,
            long textureMomentV,
            long quadAreaTwice,
            long quadTextureAreaTwice,
            long rawQuadAreaTwice,
            long rawQuadTextureAreaTwice
    ) {
        public boolean sameCoverage(OutputSurface other) {
            return other != null
                    && key.equals(other.key)
                    && sliceArea == other.sliceArea
                    && textureArea == other.textureArea
                    && textureMomentU == other.textureMomentU
                    && textureMomentV == other.textureMomentV
                    && quadAreaTwice == other.quadAreaTwice
                    && quadTextureAreaTwice == other.quadTextureAreaTwice;
        }

        public boolean sameFragmentation(OutputSurface other) {
            return sameCoverage(other)
                    && records == other.records
                    && centeredRecords == other.centeredRecords
                    && sliceRecords == other.sliceRecords
                    && quadRecords == other.quadRecords;
        }

        public String summary() {
            return key.summary()
                    + ",records=" + records
                    + ",centered=" + centeredRecords
                    + ",slices=" + sliceRecords
                    + ",quads=" + quadRecords
                    + ",slice_area=" + sliceArea
                    + ",texture_area=" + textureArea
                    + ",quad_area2=" + quadAreaTwice
                    + ",raw_quad_area2=" + rawQuadAreaTwice
                    + ",raw_quad_texture_area2=" + rawQuadTextureAreaTwice;
        }
    }

    private static final class MutableOutputSurface {
        private int records;
        private int centeredRecords;
        private int sliceRecords;
        private int quadRecords;
        private long sliceArea;
        private long textureArea;
        private long textureMomentU;
        private long textureMomentV;
        private long quadAreaTwice;
        private long quadTextureAreaTwice;
        private long rawQuadAreaTwice;
        private long rawQuadTextureAreaTwice;

        private void add(SpotRecord spot) {
            records++;
            if (spot.projectionMode() == SpotRecord.ProjectionMode.FOOTPRINT_QUAD) {
                quadRecords++;
                quadAreaTwice += canonicalQuadAreaTwice(spot);
                quadTextureAreaTwice += canonicalQuadTextureAreaTwice(spot);
                rawQuadAreaTwice += rawQuadAreaTwice(spot);
                rawQuadTextureAreaTwice += rawQuadTextureAreaTwice(spot);
                return;
            }
            if (spot.projectionMode() == SpotRecord.ProjectionMode.FOOTPRINT_SLICE) {
                sliceRecords++;
            } else {
                centeredRecords++;
            }
            long area = (long) (spot.clipMaxU() - spot.clipMinU())
                    * (spot.clipMaxV() - spot.clipMinV());
            long mappedArea = (long) (spot.textureMaxU() - spot.textureMinU())
                    * (spot.textureMaxV() - spot.textureMinV());
            sliceArea += area;
            textureArea += mappedArea;
            textureMomentU += mappedArea * (spot.textureMinU() + spot.textureMaxU());
            textureMomentV += mappedArea * (spot.textureMinV() + spot.textureMaxV());
        }

        private OutputSurface freeze(OutputSurfaceKey key) {
            return new OutputSurface(
                    key,
                    records,
                    centeredRecords,
                    sliceRecords,
                    quadRecords,
                    sliceArea,
                    textureArea,
                    textureMomentU,
                    textureMomentV,
                    quadAreaTwice,
                    quadTextureAreaTwice,
                    rawQuadAreaTwice,
                    rawQuadTextureAreaTwice
            );
        }
    }

    public record Report(
            int samples,
            int timedSamples,
            int geometryTimedSamples,
            int appearanceTimedSamples,
            long latestTick,
            BlockPos latestSource,
            Direction latestDirection,
            double elapsedMinUs,
            double elapsedAverageUs,
            double elapsedP50Us,
            double elapsedP95Us,
            double elapsedMaxUs,
            double spotsAverage,
            double dependenciesAverage,
            double tilesAverage,
            double projectableTilesAverage,
            double sideWindowsAverage,
            double sideQuadsAverage,
            double appearanceBuildsAverage,
            double appearanceHitsAverage,
            double integralCallsAverage,
            double candidateTilesVisitedAverage,
            double sideScanAverageUs,
            double sideEmitAverageUs,
            double sidePatchAverageUs,
            double sideOcclusionIndexBuildAverageUs,
            double sideSameDepthSplitAverageUs,
            double sidePrefixQueryAverageUs,
            double sideRegionIntersectAverageUs,
            double sideCanonicalNormalizeAverageUs,
            double sideDebugAuditAverageUs,
            double surfaceAppearanceBuildAverageUs,
            double patchClipAverageUs,
            double spotRecordPackAverageUs,
            double frontPassAverageUs,
            double frontEmitAverageUs,
            double remainingAverageUs,
            double projectionResidualAverageUs,
            double appearanceTotalAverageUs,
            double appearancePrepareAverageUs,
            double appearanceSurfaceBuildAverageUs,
            double appearanceRecordUpdateAverageUs,
            double appearanceResidualAverageUs,
            double appearanceTemplatesAverage,
            double appearanceUniqueSurfacesAverage,
            int fullRebuildSamples,
            int appearanceOnlySamples
    ) {
        public static final Report EMPTY = new Report(
                0, 0, 0, 0, 0L, BlockPos.ZERO, Direction.NORTH,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
                0, 0
        );

        public boolean empty() {
            return samples <= 0;
        }

        public List<String> lines() {
            if (empty()) {
                return List.of("No spot projection performance samples are available in this dimension.");
            }

            List<String> lines = new ArrayList<>();
            lines.add(String.format(
                    Locale.ROOT,
                    "Spot projection: n=%d total us min=%.1f avg=%.1f p50=%.1f p95=%.1f max=%.1f",
                    samples, elapsedMinUs, elapsedAverageUs, elapsedP50Us, elapsedP95Us, elapsedMaxUs
            ));
            lines.add(String.format(
                    Locale.ROOT,
                    "Work avg: spots=%.1f deps=%.1f tiles=%.1f projectable=%.1f sideWindows=%.1f sideQuads=%.1f",
                    spotsAverage, dependenciesAverage, tilesAverage, projectableTilesAverage,
                    sideWindowsAverage, sideQuadsAverage
            ));
            lines.add(String.format(
                    Locale.ROOT,
                    "Optimization avg: appearance builds=%.1f hits=%.1f kernelIntegrals=%.1f sideCandidateTiles=%.1f",
                    appearanceBuildsAverage, appearanceHitsAverage, integralCallsAverage, candidateTilesVisitedAverage
            ));
            lines.add(String.format(
                    Locale.ROOT,
                    "Geometry cache: fullRebuild=%d appearanceOnly=%d",
                    fullRebuildSamples, appearanceOnlySamples
            ));

            if (geometryTimedSamples > 0) {
                lines.add(String.format(
                        Locale.ROOT,
                        "Stages avg us (%d timed): sideScan=%.1f sideEmit=%.1f sidePatch=%.1f frontPass=%.1f frontEmit=%.1f remaining=%.1f residual=%.1f",
                        geometryTimedSamples, sideScanAverageUs, sideEmitAverageUs, sidePatchAverageUs,
                        frontPassAverageUs, frontEmitAverageUs, remainingAverageUs, projectionResidualAverageUs
                ));
                lines.add(String.format(
                        Locale.ROOT,
                        "Side detail avg us: indexBuild=%.1f sameDepthSplit=%.1f prefixQuery=%.1f regionIntersect=%.1f canonicalNormalize=%.1f debugAudit=%.1f appearanceBuild=%.1f patchClip=%.1f recordPack=%.1f",
                        sideOcclusionIndexBuildAverageUs, sideSameDepthSplitAverageUs,
                        sidePrefixQueryAverageUs, sideRegionIntersectAverageUs,
                        sideCanonicalNormalizeAverageUs, sideDebugAuditAverageUs,
                        surfaceAppearanceBuildAverageUs, patchClipAverageUs, spotRecordPackAverageUs
                ));
            } else if (appearanceTimedSamples > 0) {
                lines.add(String.format(
                        Locale.ROOT,
                        "Appearance avg us (%d timed): total=%.1f prepare=%.1f surfaceBuild=%.1f recordUpdate=%.1f residual=%.1f templates=%.1f surfaces=%.1f",
                        appearanceTimedSamples, appearanceTotalAverageUs, appearancePrepareAverageUs,
                        appearanceSurfaceBuildAverageUs, appearanceRecordUpdateAverageUs,
                        appearanceResidualAverageUs, appearanceTemplatesAverage, appearanceUniqueSurfacesAverage
                ));
            } else {
                lines.add("Stages: unavailable; enable compilerdebug for detailed stage timings.");
            }

            lines.add(String.format(
                    Locale.ROOT,
                    "Latest: tick=%d source=%s direction=%s",
                    latestTick, latestSource.toShortString(), latestDirection.getSerializedName()
            ));
            return List.copyOf(lines);
        }
    }

    private SpotProjectionPerformanceTracker() {
    }
}
