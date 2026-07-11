package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OpticalSpotTracker;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.LocalPoint;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.Patch;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.TexturePoint;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class VoxelSpotProjector {
    private static final double MIN_FRAGMENT_POWER = 0.0D;
    private static final double SIDE_FACE_VISUAL_FACTOR = 0.35D;
    private static final int MAX_SIDE_PATCH_TRAVEL_SUBDIVISIONS = 8;
    private static final double SIDE_PATCH_RADIUS_RATIO_PER_SEGMENT = 1.18D;
    private static final double SIDE_PATCH_RADIUS_DELTA_PER_SEGMENT = 0.35D;
    private static final double SIDE_TINY_TEXTURE_AREA_THRESHOLD = 1.0E-4D;
    private static final double SIDE_LARGE_STRETCH_RATIO_THRESHOLD = 200.0D;
    private static final double EDGE_TOUCH_EPSILON = 1.0E-6D;
    private static final double CONE_SIDE_EPSILON = 1.0E-8D;
    private static final double VISUAL_DISTANCE_FADE_LINEAR = 0.08D;
    private static final double VISUAL_DISTANCE_FADE_QUADRATIC = 0.004D;
    private static final int MAX_PROJECTED_DEPTH = 32;
    private static final int MAX_SIDE_BOUNDARY_MISSING_EXAMPLES = 8;
    private static final int MAX_STRUCTURAL_VALIDATION_EXAMPLES = 8;
    private static final int CONE_TRAVEL_SAMPLES = 16;
    private static final int SIDE_OCCLUSION_INDEX_BINS = 8;
    private static final CanonicalRect FULL_FOOTPRINT = new CanonicalRect(0.0D, 0.0D, 1.0D, 1.0D);
    private static final List<OpticalCollisionBox> FULL_OPTICAL_BOXES = List.of(OpticalCollisionBox.full());
    private static final Map<ModelShapeFrameKey, List<OpticalCollisionBox>> MODEL_SHAPE_BOX_CACHE =
            new ConcurrentHashMap<>();
    private static volatile boolean debugFaceCentersEnabled = false;
    private static volatile ValidationTarget targetedValidation;

    public static boolean debugFaceCentersEnabled() {
        return debugFaceCentersEnabled;
    }

    public static int occlusionPlaneCount() {
        return Math.max(2, SpectralizationConfig.spotProjectionOcclusionPlanes());
    }

    public static void setDebugFaceCentersEnabled(boolean enabled) {
        debugFaceCentersEnabled = enabled;
    }

    public static void setTargetedValidation(
            ResourceKey<Level> dimension,
            BlockPos sourcePos,
            Direction travelDirection
    ) {
        targetedValidation = new ValidationTarget(
                Objects.requireNonNull(dimension, "dimension"),
                Objects.requireNonNull(sourcePos, "sourcePos").immutable(),
                Objects.requireNonNull(travelDirection, "travelDirection")
        );
    }

    public static void clearTargetedValidation() {
        targetedValidation = null;
    }

    public static boolean validationEnabledFor(
            Level level,
            BlockPos sourcePos,
            Direction travelDirection
    ) {
        if (SpectralizationConfig.opticalCompilerDebugVerbose()) {
            return true;
        }
        ValidationTarget target = targetedValidation;
        return target != null
                && target.dimension().equals(level.dimension())
                && target.sourcePos().equals(sourcePos)
                && target.travelDirection() == travelDirection;
    }

    public static SpotProjectionResult projectLightConeSpots(
            Level level,
            BlockPos sourcePos,
            Direction sourceFace,
            Direction travelDirection,
            BeamPacket profileTemplate,
            double beamPower,
            double coherentBeamPower
    ) {
        if (travelDirection.getAxis() != sourceFace.getAxis()) {
            throw new IllegalArgumentException("Spot projection travel direction must be normal to the source face");
        }

        if (beamPower <= MIN_FRAGMENT_POWER) {
            return SpotProjectionResult.EMPTY;
        }

        LongSet dependencies = new LongOpenHashSet();
        List<SpotRecord> fragments = new ArrayList<>();
        List<SpotProjectionAllocation> allocations = new ArrayList<>();
        CanonicalRegion remainingRayWindows = CanonicalRegion.full();
        BeamPacket projectionTemplate = profileTemplate;
        boolean collectAllocations = validationEnabledFor(level, sourcePos, travelDirection);
        ProjectionStatsBuilder stats = new ProjectionStatsBuilder(collectAllocations);
        List<OcclusionWindow> occupiedDebugWindows = collectAllocations ? new ArrayList<>() : List.of();
        BeamGeometryOps.RadiusPropagation sourceRadiusPropagation =
                BeamGeometryOps.prepareRadiusPropagation(projectionTemplate.envelope());
        double previousDepthExitRadius = Double.NaN;
        stats.recordRemainingRegion(remainingRayWindows);

        for (int depth = 1; depth <= MAX_PROJECTED_DEPTH; depth++) {
            long depthStartNanos = stats.startTimer();
            long depthScannedStart = stats.scannedTiles;
            long depthCandidateStart = stats.candidateTiles;
            long depthProjectableStart = stats.projectableTiles;
            long depthPlaneWindowStart = stats.planeWindows;
            long depthFrontSubtractionStart = stats.frontSubtractions;
            long depthSideSubtractionStart = stats.sideSubtractions;
            long depthOccupiedTestStart = stats.occupiedWindowTests;
            long depthOccupiedHitStart = stats.occupiedWindowHits;
            int depthSpotStart = fragments.size();
            stats.depths++;
            BeamEnvelope envelope = envelopeAtDepth(projectionTemplate.envelope(), depth);
            BeamGeometryOps.RadiusPropagation radiusPropagation = sourceRadiusPropagation.offset(depth);
            double radius = radiusPropagation.radiusAt(0.0D);
            if (Double.isFinite(previousDepthExitRadius)) {
                stats.recordDepthBoundaryRadius(previousDepthExitRadius, radius);
            }
            previousDepthExitRadius = radiusPropagation.radiusAt(1.0D);

            if (!Double.isFinite(radius) || radius <= 0.0D) {
                stats.finishDepth(
                        depth,
                        depthStartNanos,
                        depthScannedStart,
                        depthCandidateStart,
                        depthProjectableStart,
                        depthPlaneWindowStart,
                        depthFrontSubtractionStart,
                        depthSideSubtractionStart,
                        depthOccupiedTestStart,
                        depthOccupiedHitStart,
                        depthSpotStart,
                        fragments.size()
                );
                continue;
            }

            long rangeStartNanos = stats.startTimer();
            Direction displayFace = travelDirection.getOpposite();
            Direction uDirection = SpotSurfaceFrame.uDirection(displayFace);
            Direction vDirection = SpotSurfaceFrame.vDirection(displayFace);
            double maxUnitRadius = maxEnvelopeRadiusOverUnit(radiusPropagation);
            int minTile = projectedMinTile(maxUnitRadius);
            int maxTile = projectedMaxTile(maxUnitRadius);
            int tileRadius = Math.max(Math.abs(minTile), Math.abs(maxTile));
            BeamPacket targetTemplate = projectionTemplate.withEnvelope(envelope);
            List<CuboidSweep> cuboidSweepsAtDepth = new ArrayList<>();
            List<OcclusionWindow> frontBlockersAtDepth = new ArrayList<>();
            List<FrontFaceCandidate> frontCandidatesAtDepth = new ArrayList<>();
            List<DepthCuboidProbe> cuboidProbesAtDepth = collectAllocations ? new ArrayList<>() : List.of();
            BlockPos depthOrigin = sourcePos.relative(travelDirection, depth);
            SideScanTileBounds sideTileBounds = sideScanTileBounds(minTile, maxTile, maxUnitRadius, remainingRayWindows);
            List<DepthTile> sideProjectableTiles = new ArrayList<>();
            int scanMinU = sideTileBounds.empty() ? minTile : Math.min(minTile, sideTileBounds.minU());
            int scanMaxU = sideTileBounds.empty() ? maxTile : Math.max(maxTile, sideTileBounds.maxU());
            int scanMinV = sideTileBounds.empty() ? minTile : Math.min(minTile, sideTileBounds.minV());
            int scanMaxV = sideTileBounds.empty() ? maxTile : Math.max(maxTile, sideTileBounds.maxV());
            SideScanTileBounds faceTileBounds = new SideScanTileBounds(
                    scanMinU,
                    scanMaxU,
                    scanMinV,
                    scanMaxV,
                    sideTileBounds.culledTiles()
            );
            DepthTileCache depthTileCache = new DepthTileCache(depthOrigin, travelDirection, uDirection, vDirection, faceTileBounds);
            stats.addTileRangeNanos(rangeStartNanos);

            for (int dv = scanMinV; dv <= scanMaxV; dv++) {
                for (int du = scanMinU; du <= scanMaxU; du++) {
                    stats.scannedTiles++;
                    long rectStartNanos = stats.startTimer();
                    boolean inFrontBounds = du >= minTile && du <= maxTile && dv >= minTile && dv <= maxTile;
                    boolean inSideBounds = sideTileBounds.contains(du, dv);
                    ProjectionRect rect = inFrontBounds ? projectionRect(radius, du, dv) : null;
                    boolean frontCandidateTile = inFrontBounds && (rect != null || tileIntersectsRadius(maxUnitRadius, du, dv));
                    stats.addProjectionRectNanos(rectStartNanos);

                    if (!frontCandidateTile && !inSideBounds) {
                        continue;
                    }

                    BlockPos targetPos = new BlockPos(
                            depthOrigin.getX() + uDirection.getStepX() * du + vDirection.getStepX() * dv,
                            depthOrigin.getY() + uDirection.getStepY() * du + vDirection.getStepY() * dv,
                            depthOrigin.getZ() + uDirection.getStepZ() * du + vDirection.getStepZ() * dv
                    );

                    if (frontCandidateTile) {
                        stats.candidateTiles++;
                    }

                    dependencies.add(targetPos.asLong());

                    long blockLookupStartNanos = stats.startTimer();
                    boolean loaded = level.isLoaded(targetPos);
                    BlockState targetState = loaded ? level.getBlockState(targetPos) : null;
                    stats.addBlockLookupNanos(blockLookupStartNanos);

                    if (!loaded) {
                        depthTileCache.put(new DepthTile(du, dv, targetPos, false, null, false, List.of()));
                        continue;
                    }

                    if (frontCandidateTile) {
                        stats.loadedTiles++;
                    }

                    if (OpticalMaterialProfiles.isAirLike(targetState)) {
                        if (frontCandidateTile) {
                            stats.airTiles++;
                        }
                        depthTileCache.put(new DepthTile(du, dv, targetPos, true, targetState, true, List.of()));
                        if (inSideBounds) {
                            stats.sideLoadedTiles++;
                        }
                        continue;
                    }

                    long projectableStartNanos = stats.startTimer();
                    List<OpticalCollisionBox> opticalBoxes = opticalCollisionBoxes(
                            level,
                            targetPos,
                            targetState,
                            travelDirection,
                            uDirection,
                            vDirection
                    );
                    boolean projectableSurface = !opticalBoxes.isEmpty();
                    stats.addProjectableCheckNanos(projectableStartNanos);
                    DepthTile depthTile = new DepthTile(du, dv, targetPos, true, targetState, false, opticalBoxes);
                    depthTileCache.put(depthTile);

                    if (inSideBounds) {
                        stats.sideLoadedTiles++;
                        if (projectableSurface) {
                            stats.sideProjectableTiles++;
                        }
                    }

                    if (projectableSurface) {
                        sideProjectableTiles.add(depthTile);
                    }

                    if (!projectableSurface) {
                        if (frontCandidateTile) {
                            stats.nonProjectableTiles++;
                        }
                        continue;
                    }

                    if (frontCandidateTile) {
                        stats.projectableTiles++;
                    }

                    for (int boxIndex = 0; boxIndex < opticalBoxes.size(); boxIndex++) {
                        OpticalCollisionBox opticalBox = opticalBoxes.get(boxIndex);
                        if (collectAllocations) {
                            cuboidProbesAtDepth.add(new DepthCuboidProbe(
                                    depthTile.pos(),
                                    depth,
                                    depthTile.tileU(),
                                    depthTile.tileV(),
                                    boxIndex,
                                    opticalBox
                            ));
                        }
                        BeamEnvelope boxFrontEnvelope = envelopeAtOffset(targetTemplate.envelope(), opticalBox.minTravel());
                        double boxFrontRadius = boxFrontEnvelope.radius();
                        ProjectionRect fullBoxFrontRect = projectionRectForLocalFace(
                                boxFrontRadius,
                                du,
                                dv,
                                opticalBox.minU(),
                                opticalBox.minV(),
                                opticalBox.maxU(),
                                opticalBox.maxV()
                        );

                        long planeStartNanos = stats.startTimer();
                        CuboidSweep cuboidSweep = createCuboidSweep(
                                radiusPropagation,
                                du,
                                dv,
                                targetPos,
                                depth,
                                boxIndex,
                                opticalBox,
                                remainingRayWindows,
                                stats
                        );
                        stats.addPlaneWindowNanos(planeStartNanos);

                        if (fullBoxFrontRect == null && cuboidSweep == null) {
                            continue;
                        }

                        if (depth > 0 && cuboidSweep != null) {
                            cuboidSweepsAtDepth.add(cuboidSweep);
                            OcclusionWindow fullBlocker = cuboidSweep.fullWindow();
                            frontBlockersAtDepth.add(fullBlocker);
                            if (collectAllocations) {
                                allocations.add(frontOcclusionProbeAllocation(
                                        targetPos,
                                        displayFace,
                                        depth,
                                        du,
                                        dv,
                                        boxFrontEnvelope,
                                        fullBoxFrontRect,
                                        List.of(fullBlocker)
                                ));
                            }

                        }

                        if (!frontCandidateTile) {
                            continue;
                        }

                        if (fullBoxFrontRect != null) {
                            frontCandidatesAtDepth.add(new FrontFaceCandidate(
                                    depthTile,
                                    boxIndex,
                                    opticalBox,
                                    boxFrontEnvelope
                            ));
                        }
                    }
                }
            }

            long frontPassStartNanos = stats.startTimer();
            emitDepthFrontFaces(
                    level,
                    depth,
                    displayFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    targetTemplate,
                    beamPower,
                    coherentBeamPower,
                    remainingRayWindows,
                    occupiedDebugWindows,
                    cuboidSweepsAtDepth,
                    frontCandidatesAtDepth,
                    collectAllocations,
                    stats,
                    allocations,
                    fragments
            );
            stats.addFrontPassNanos(frontPassStartNanos);

            if (depth > 0) {
                double visualDistanceFactor = visualDistanceFactor(depth);

                long sideStartNanos = stats.startTimer();
                addIndependentSideQuads(
                        level,
                        depthOrigin,
                        depth,
                        travelDirection,
                        uDirection,
                        vDirection,
                        minTile,
                        maxTile,
                        maxUnitRadius,
                        sideTileBounds,
                        faceTileBounds,
                        depthTileCache,
                        sideProjectableTiles,
                        targetTemplate,
                        radiusPropagation,
                        beamPower,
                        coherentBeamPower,
                        visualDistanceFactor,
                        remainingRayWindows,
                        cuboidSweepsAtDepth,
                        cuboidProbesAtDepth,
                        collectAllocations,
                        stats,
                        dependencies,
                        allocations,
                        fragments
                );
                stats.addSideScanNanos(sideStartNanos);
            }

            long occlusionAddStartNanos = stats.startTimer();
            if (collectAllocations) {
                occupiedDebugWindows.addAll(frontBlockersAtDepth);
            }
            stats.addOcclusionAddNanos(occlusionAddStartNanos);

            long fullOccupancyStartNanos = stats.startTimer();
            remainingRayWindows.subtractUnion(frontBlockersAtDepth, stats, depth);
            boolean fullyOccupied = remainingRayWindows.isEmpty();
            stats.addFullOccupancyNanos(fullOccupancyStartNanos);
            stats.finishDepth(
                    depth,
                    depthStartNanos,
                    depthScannedStart,
                    depthCandidateStart,
                    depthProjectableStart,
                    depthPlaneWindowStart,
                    depthFrontSubtractionStart,
                    depthSideSubtractionStart,
                    depthOccupiedTestStart,
                    depthOccupiedHitStart,
                    depthSpotStart,
                    fragments.size()
            );

            if (fullyOccupied) {
                break;
            }
        }

        List<SpotRecord> visibleFragments = fragments.stream().filter(SpotRecord::visible).toList();
        SpotProjectionResult.AppearancePlan appearancePlan = buildAppearancePlan(
                fragments,
                sourcePos,
                travelDirection,
                profileTemplate.envelope()
        );
        return new SpotProjectionResult(
                visibleFragments,
                new SpotProjectionResult.DependencySnapshot(dependencies),
                allocations,
                stats.toStats(),
                fragments,
                appearancePlan,
                SpotProjectionResult.CacheMode.FULL_REBUILD,
                SpotProjectionResult.AppearanceTimings.EMPTY
        );
    }

    public static SpotProjectionResult reapplyCachedAppearance(
            Level level,
            BlockPos sourcePos,
            Direction travelDirection,
            BeamPacket profileTemplate,
            double beamPower,
            double coherentBeamPower,
            SpotProjectionResult cachedGeometry
    ) {
        Objects.requireNonNull(cachedGeometry, "cachedGeometry");
        if (cachedGeometry.geometryTemplates().isEmpty()) {
            return new SpotProjectionResult(
                    List.of(),
                    cachedGeometry.dependencySnapshot(),
                    List.of(),
                    SpotProjectionResult.Stats.EMPTY,
                    List.of(),
                    SpotProjectionResult.AppearancePlan.EMPTY,
                    SpotProjectionResult.CacheMode.APPEARANCE_ONLY,
                    SpotProjectionResult.AppearanceTimings.EMPTY
            );
        }

        long totalStartNanos = System.nanoTime();
        long prepareNanos = 0L;
        long surfaceBuildNanos = 0L;
        long recordUpdateNanos = 0L;
        int surfaceBuilds = 0;
        int surfaceCacheHits = 0;
        List<SpotRecord> geometryTemplates = cachedGeometry.geometryTemplates();
        SpotProjectionResult.AppearancePlan appearancePlan = cachedGeometry.appearancePlan();
        boolean appearancePlanReused = appearancePlan.templateCount() == geometryTemplates.size();
        if (!appearancePlanReused) {
            appearancePlan = buildAppearancePlan(
                    geometryTemplates,
                    sourcePos,
                    travelDirection,
                    profileTemplate.envelope()
            );
        }

        int surfaceCount = appearancePlan.surfaceCount();
        BeamPacket[] templatesBySurface = new BeamPacket[surfaceCount];
        double[] powerBySurface = new double[surfaceCount];
        double[] coherentPowerBySurface = new double[surfaceCount];
        long prepareStartNanos = System.nanoTime();
        for (int surfaceIndex = 0; surfaceIndex < surfaceCount; surfaceIndex++) {
            SpotProjectionResult.AppearanceSurface surface = appearancePlan.surface(surfaceIndex);
            templatesBySurface[surfaceIndex] = profileTemplate.withEnvelope(surface.envelope());
            powerBySurface[surfaceIndex] = beamPower * surface.powerScale();
            coherentPowerBySurface[surfaceIndex] = coherentBeamPower * surface.powerScale();
        }
        prepareNanos = Math.max(0L, System.nanoTime() - prepareStartNanos);

        SpotRecord[] appearanceBySurface = new SpotRecord[surfaceCount];
        long surfaceStartNanos = System.nanoTime();
        for (int surfaceIndex = 0; surfaceIndex < surfaceCount; surfaceIndex++) {
            SpotProjectionResult.AppearanceSurface surface = appearancePlan.surface(surfaceIndex);
            appearanceBySurface[surfaceIndex] = OpticalSpotTracker.createCompiledSurfaceSpot(
                    level,
                    surface.pos(),
                    surface.face(),
                    level.getBlockState(surface.pos()),
                    templatesBySurface[surfaceIndex],
                    powerBySurface[surfaceIndex],
                    coherentPowerBySurface[surfaceIndex]
            );
            surfaceBuilds++;
        }
        surfaceBuildNanos = Math.max(0L, System.nanoTime() - surfaceStartNanos);

        List<SpotRecord> visible = new ArrayList<>(geometryTemplates.size());
        long updateStartNanos = System.nanoTime();
        for (int templateIndex = 0; templateIndex < geometryTemplates.size(); templateIndex++) {
            SpotRecord geometry = geometryTemplates.get(templateIndex);
            int surfaceIndex = appearancePlan.surfaceIndexForTemplate(templateIndex);
            if (surfaceIndex < 0) {
                visible.add(geometry);
                continue;
            }
            surfaceCacheHits++;
            SpotRecord updated = geometry.withAppearanceFrom(appearanceBySurface[surfaceIndex]);
            if (updated.visible()) {
                visible.add(updated);
            }
        }
        recordUpdateNanos = Math.max(0L, System.nanoTime() - updateStartNanos);

        SpotProjectionResult.AppearanceTimings appearanceTimings = new SpotProjectionResult.AppearanceTimings(
                Math.max(0L, System.nanoTime() - totalStartNanos),
                prepareNanos,
                surfaceBuildNanos,
                recordUpdateNanos,
                geometryTemplates.size(),
                appearancePlan.surfaceCount(),
                surfaceBuilds,
                Math.max(0, surfaceCacheHits - surfaceBuilds),
                appearancePlanReused
        );

        return new SpotProjectionResult(
                visible,
                cachedGeometry.dependencySnapshot(),
                List.of(),
                SpotProjectionResult.Stats.EMPTY,
                geometryTemplates,
                appearancePlan,
                SpotProjectionResult.CacheMode.APPEARANCE_ONLY,
                appearanceTimings
        );
    }

    private static SpotProjectionResult.AppearancePlan buildAppearancePlan(
            List<SpotRecord> geometryTemplates,
            BlockPos sourcePos,
            Direction travelDirection,
            BeamEnvelope sourceEnvelope
    ) {
        if (geometryTemplates.isEmpty()) {
            return SpotProjectionResult.AppearancePlan.EMPTY;
        }

        Map<SurfaceSpotKey, Integer> surfaceIndices = new HashMap<>();
        List<SpotProjectionResult.AppearanceSurface> surfaces = new ArrayList<>();
        int[] surfaceIndexByTemplate = new int[geometryTemplates.size()];
        for (int templateIndex = 0; templateIndex < geometryTemplates.size(); templateIndex++) {
            SpotRecord geometry = geometryTemplates.get(templateIndex);
            if (geometry.projectionMode() == SpotRecord.ProjectionMode.DEBUG_FACE_CENTER) {
                surfaceIndexByTemplate[templateIndex] = -1;
                continue;
            }

            SurfaceSpotKey key = new SurfaceSpotKey(geometry.pos().asLong(), geometry.face());
            Integer surfaceIndex = surfaceIndices.get(key);
            if (surfaceIndex == null) {
                int depth = projectionDepth(sourcePos, geometry.pos(), travelDirection);
                double distanceFactor = visualDistanceFactor(depth);
                double powerScale = geometry.face() == travelDirection.getOpposite()
                        ? distanceFactor
                        : distanceFactor * SIDE_FACE_VISUAL_FACTOR;
                surfaceIndex = surfaces.size();
                surfaceIndices.put(key, surfaceIndex);
                surfaces.add(new SpotProjectionResult.AppearanceSurface(
                        geometry.pos(),
                        geometry.face(),
                        envelopeAtDepth(sourceEnvelope, depth),
                        powerScale
                ));
            }
            surfaceIndexByTemplate[templateIndex] = surfaceIndex;
        }
        return new SpotProjectionResult.AppearancePlan(surfaces, surfaceIndexByTemplate);
    }

    private static int projectionDepth(BlockPos sourcePos, BlockPos targetPos, Direction travelDirection) {
        int dx = targetPos.getX() - sourcePos.getX();
        int dy = targetPos.getY() - sourcePos.getY();
        int dz = targetPos.getZ() - sourcePos.getZ();
        return Math.max(1, dx * travelDirection.getStepX()
                + dy * travelDirection.getStepY()
                + dz * travelDirection.getStepZ());
    }

    private static void emitDepthFrontFaces(
            Level level,
            int depth,
            Direction displayFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            BeamPacket targetTemplate,
            double beamPower,
            double coherentBeamPower,
            CanonicalRegion previousDepthRemaining,
            List<OcclusionWindow> occupiedDebugWindows,
            List<CuboidSweep> sameDepthSweeps,
            List<FrontFaceCandidate> candidates,
            boolean collectAllocations,
            ProjectionStatsBuilder stats,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        if (candidates.isEmpty()) {
            return;
        }

        candidates.sort(Comparator
                .comparingDouble((FrontFaceCandidate candidate) -> candidate.box().minTravel())
                .thenComparingInt(candidate -> candidate.tile().tileV())
                .thenComparingInt(candidate -> candidate.tile().tileU())
                .thenComparingInt(FrontFaceCandidate::boxIndex));

        CanonicalRegion depthFrontRemaining = copyRegion(previousDepthRemaining);
        LongSet debugCenteredPositions = new LongOpenHashSet();
        Map<SurfaceSpotKey, SpotRecord> appearanceCache = new HashMap<>();
        double visualDistanceFactor = visualDistanceFactor(depth);
        double visualFragmentPower = visualSurfacePower(beamPower, visualDistanceFactor);
        double visualCoherentPower = visualSurfacePower(coherentBeamPower, visualDistanceFactor);

        // Each front group reads the continuous prefix of every volume that started earlier.
        // A volume starting at the group's travel activates only after that coplanar group.
        for (int groupStart = 0; groupStart < candidates.size();) {
            double groupTravel = candidates.get(groupStart).box().minTravel();
            int groupEnd = groupStart + 1;

            while (groupEnd < candidates.size()
                    && Math.abs(candidates.get(groupEnd).box().minTravel() - groupTravel) <= EDGE_TOUCH_EPSILON) {
                groupEnd++;
            }

            List<OcclusionWindow> activeSameDepthFrontPrefix = sweepPrefixWindowsBeforeFront(
                    sameDepthSweeps,
                    groupTravel
            );
            if (!activeSameDepthFrontPrefix.isEmpty()) {
                depthFrontRemaining.subtractUnion(activeSameDepthFrontPrefix, null);
            }
            List<OcclusionWindow> frontDebugWindows;
            if (collectAllocations) {
                frontDebugWindows = new ArrayList<>(
                        occupiedDebugWindows.size() + activeSameDepthFrontPrefix.size()
                );
                frontDebugWindows.addAll(occupiedDebugWindows);
                frontDebugWindows.addAll(activeSameDepthFrontPrefix);
            } else {
                frontDebugWindows = List.of();
            }

            for (int candidateIndex = groupStart; candidateIndex < groupEnd; candidateIndex++) {
                FrontFaceCandidate candidate = candidates.get(candidateIndex);
                DepthTile targetTile = candidate.tile();
                OpticalCollisionBox opticalBox = candidate.box();
                int tileU = targetTile.tileU();
                int tileV = targetTile.tileV();
                double boxFrontRadius = candidate.frontEnvelope().radius();

                for (CanonicalRect exposedFrontRegion : exposedFrontFaceRegions(targetTile, opticalBox)) {
                    ProjectionRect boxRect = projectionRectForLocalFace(
                            boxFrontRadius,
                            tileU,
                            tileV,
                            exposedFrontRegion.minU(),
                            exposedFrontRegion.minV(),
                            exposedFrontRegion.maxU(),
                            exposedFrontRegion.maxV()
                    );

                    if (boxRect == null) {
                        continue;
                    }

                    List<ProjectionRect> visibleRects = visibleSubRects(
                            boxFrontRadius,
                            tileU,
                            tileV,
                            boxRect,
                            depthFrontRemaining,
                            stats,
                            false
                    );
                    visibleRects = mergeProjectionRects(boxFrontRadius, tileU, tileV, visibleRects, stats);

                    if (collectAllocations) {
                        List<OcclusionWindow> intersectingOccupied = intersectingOcclusionWindows(
                                boxRect.canonicalRect(),
                                frontDebugWindows
                        );
                        List<OcclusionWindow> intersectingPrefix = intersectingOcclusionWindows(
                                boxRect.canonicalRect(),
                                activeSameDepthFrontPrefix
                        );
                        List<ProjectionRect> visibleWithoutBackRects = visibleSubRects(
                                boxFrontRadius,
                                tileU,
                                tileV,
                                boxRect,
                                canonicalWindowsExcludingPlane(frontDebugWindows, "back"),
                                stats,
                                false
                        );

                        if (opticalBox.minTravel() > EDGE_TOUCH_EPSILON || !intersectingPrefix.isEmpty()) {
                            allocations.add(frontPrefixProbeAllocation(
                                    targetTile.pos(),
                                    displayFace,
                                    depth,
                                    tileU,
                                    tileV,
                                    candidate.boxIndex(),
                                    opticalBox,
                                    boxRect,
                                    activeSameDepthFrontPrefix.size(),
                                    intersectingPrefix,
                                    visibleRects
                            ));
                        } else if (!intersectingOccupied.isEmpty() || visibleRects.size() != 1) {
                            allocations.add(frontVisibleProbeAllocation(
                                    targetTile.pos(),
                                    displayFace,
                                    depth,
                                    tileU,
                                    tileV,
                                    boxRect,
                                    intersectingOccupied,
                                    visibleRects,
                                    visibleWithoutBackRects,
                                    frontDebugWindows.size()
                            ));
                        }
                    }

                    if (visibleRects.isEmpty() || visualFragmentPower <= MIN_FRAGMENT_POWER) {
                        continue;
                    }

                    if (debugCenteredPositions.add(targetTile.pos().asLong())) {
                        addDebugFaceCenter(targetTile.pos(), displayFace, fragments);
                    }

                    long frontEmitStartNanos = stats.startTimer();
                    SpotRecord surfaceSpot = cachedSurfaceSpot(
                            appearanceCache,
                            level,
                            targetTile.pos(),
                            displayFace,
                            targetTile.state(),
                            targetTemplate,
                            visualFragmentPower,
                            visualCoherentPower,
                            stats
                    );
                    for (ProjectionRect visibleRect : visibleRects) {
                        addFrontChartSpot(
                                surfaceSpot,
                                displayFace,
                                travelDirection,
                                uDirection,
                                vDirection,
                                opticalBox.minTravel(),
                                boxFrontRadius,
                                tileU,
                                tileV,
                                visibleRect,
                                fragments
                        );
                    }
                    stats.addFrontEmitNanos(frontEmitStartNanos);
                }
            }

            groupStart = groupEnd;
        }
    }

    static FrontPlanePhase classifyFrontPlaneEvent(
            double planeTravel,
            double volumeStartTravel,
            double frontGroupTravel
    ) {
        if (planeTravel < frontGroupTravel - EDGE_TOUCH_EPSILON) {
            return FrontPlanePhase.BEFORE;
        }

        if (Math.abs(planeTravel - frontGroupTravel) <= EDGE_TOUCH_EPSILON) {
            return volumeStartTravel < frontGroupTravel - EDGE_TOUCH_EPSILON
                    ? FrontPlanePhase.CONTINUING
                    : FrontPlanePhase.STARTING;
        }

        return FrontPlanePhase.AFTER;
    }

    private static List<OcclusionWindow> sweepPrefixWindowsBeforeFront(
            List<CuboidSweep> sweeps,
            double frontTravel
    ) {
        if (sweeps.isEmpty()) {
            return List.of();
        }
        List<OcclusionWindow> prefixes = new ArrayList<>();
        for (CuboidSweep sweep : sweeps) {
            if (sweep.startTravel() >= frontTravel - EDGE_TOUCH_EPSILON) {
                continue;
            }
            OcclusionWindow prefix = sweep.prefixWindow(frontTravel);
            if (prefix != null) {
                prefixes.add(prefix);
            }
        }
        return prefixes.isEmpty() ? List.of() : prefixes;
    }

    static int verifyCanonicalRegionSubtractSweep() {
        Random random = new Random(0x5EEDC0DEL);
        int checks = 0;
        for (int iteration = 0; iteration < 4096; iteration++) {
            CanonicalRegion remaining = CanonicalRegion.fromRects(randomVerificationRects(random, 1 + random.nextInt(8)));
            CanonicalRegion blockers = CanonicalRegion.fromRects(randomVerificationRects(random, 1 + random.nextInt(8)));
            List<Slab> optimized = CanonicalRegion.subtractRegionSlabs(remaining.slabs, blockers.slabs, null);
            List<Slab> legacy = CanonicalRegion.subtractRegionSlabsLegacy(remaining.slabs, blockers.slabs);
            if (!CanonicalRegion.sameSlabs(optimized, legacy)) {
                throw new IllegalStateException(String.format(
                        Locale.ROOT,
                        "canonical region sweep mismatch iteration=%d optimized_area=%.9f legacy_area=%.9f",
                        iteration,
                        CanonicalRegion.areaOfSlabs(optimized),
                        CanonicalRegion.areaOfSlabs(legacy)
                ));
            }

            CanonicalRect source = randomVerificationRect(random);
            List<CanonicalRect> sameDepthWindows = randomVerificationRects(random, random.nextInt(9));
            List<CanonicalRect> legacyVisible = remaining.intersect(source, null);
            if (!sameDepthWindows.isEmpty() && !legacyVisible.isEmpty()) {
                legacyVisible = subtractOccupied(legacyVisible, sameDepthWindows, null);
            }
            CanonicalRegion legacyVisibleRegion = CanonicalRegion.fromRects(legacyVisible);

            CanonicalIntersection directIntersection = remaining.intersectRegion(source, null);
            CanonicalRegion directVisibleRegion = directIntersection.region();
            if (!sameDepthWindows.isEmpty() && !directVisibleRegion.isEmpty()) {
                CanonicalRegion sameDepthUnion = CanonicalRegion.fromRects(sameDepthWindows);
                directVisibleRegion = CanonicalRegion.fromNormalizedSlabs(
                        CanonicalRegion.subtractRegionSlabs(
                                directVisibleRegion.slabs,
                                sameDepthUnion.slabs,
                                null
                        )
                );
            }
            if (!CanonicalRegion.sameSlabs(legacyVisibleRegion.slabs, directVisibleRegion.slabs)) {
                throw new IllegalStateException(String.format(
                        Locale.ROOT,
                        "canonical side visibility mismatch iteration=%d direct_area=%.9f legacy_area=%.9f",
                        iteration,
                        directVisibleRegion.area(),
                        legacyVisibleRegion.area()
                ));
            }
            checks++;
        }
        return checks;
    }

    static int verifySameDepthOcclusionIndex() {
        Random random = new Random(0x51DE1D3EL);
        BlockPos ownerPos = BlockPos.ZERO;
        BeamGeometryOps.RadiusPropagation propagation = BeamGeometryOps.prepareRadiusPropagation(
                new BeamEnvelope(BeamModel.FOCUSED, 1.25D, 0.15D, 0.7D, 1.0D, 1.0D, 0.0D, 0, 0)
        );
        int checks = 0;
        for (int iteration = 0; iteration < 1024; iteration++) {
            List<CuboidSweep> sweeps = new ArrayList<>();
            int sweepCount = 1 + random.nextInt(24);
            for (int sweepIndex = 0; sweepIndex < sweepCount; sweepIndex++) {
                BlockPos pos = random.nextBoolean()
                        ? ownerPos
                        : new BlockPos(1 + random.nextInt(3), 0, random.nextInt(3));
                double startTravel = random.nextInt(8) / 8.0D;
                double endTravel = startTravel + (1 + random.nextInt(8 - (int) Math.round(startTravel * 8.0D))) / 8.0D;
                int minUStep = random.nextInt(12);
                int maxUStep = minUStep + 1 + random.nextInt(16 - minUStep);
                int minVStep = random.nextInt(12);
                int maxVStep = minVStep + 1 + random.nextInt(16 - minVStep);
                OpticalCollisionBox box = new OpticalCollisionBox(
                        startTravel,
                        endTravel,
                        minUStep / 16.0D,
                        maxUStep / 16.0D,
                        minVStep / 16.0D,
                        maxVStep / 16.0D
                );
                CuboidSweep sweep = CuboidSweep.create(
                        propagation,
                        random.nextInt(3) - 1,
                        random.nextInt(3) - 1,
                        pos,
                        1,
                        random.nextInt(3),
                        box
                );
                if (sweep != null) {
                    sweeps.add(sweep);
                }
            }

            SameDepthOcclusionIndex index = new SameDepthOcclusionIndex(sweeps);
            for (int query = 0; query < 4; query++) {
                int minStep = random.nextInt(8);
                int maxStep = minStep + 1 + random.nextInt(8 - minStep);
                TravelInterval source = new TravelInterval(minStep / 8.0D, maxStep / 8.0D);
                int ownerBoxIndex = random.nextInt(3);
                List<TravelInterval> optimizedSplit = index.split(source, ownerPos, ownerBoxIndex, null);
                List<TravelInterval> legacySplit = splitSideTravelAtSameDepthSweeps(
                        source, sweeps, ownerPos, ownerBoxIndex
                );
                if (!optimizedSplit.equals(legacySplit)) {
                    throw new IllegalStateException("same-depth split index mismatch iteration=" + iteration
                            + " source=" + source
                            + " owner_box=" + ownerBoxIndex
                            + " optimized=" + optimizedSplit
                            + " legacy=" + legacySplit
                            + " sweeps=" + sweeps.stream().map(sweep -> sweep.pos() + "/" + sweep.boxIndex()
                                    + "/" + sweep.startTravel() + ".." + sweep.endTravel()
                                    + "/waist=" + sweep.radiusPropagation().waistTravel()).toList());
                }

                double surfaceTravel = random.nextInt(9) / 8.0D;
                CanonicalRect candidate = randomVerificationRect(random);
                List<OcclusionWindow> optimizedPrefix = index.before(
                        ownerPos, ownerBoxIndex, surfaceTravel, candidate, null
                );
                List<OcclusionWindow> legacyPrefix = sameDepthOccludersBefore(
                        sweeps, ownerPos, ownerBoxIndex, surfaceTravel, candidate
                );
                if (!optimizedPrefix.equals(legacyPrefix)) {
                    throw new IllegalStateException("same-depth prefix index mismatch iteration=" + iteration);
                }
                checks += 2;
            }
        }
        return checks;
    }

    static int verifyCuboidSweeps() {
        BeamGeometryOps.RadiusPropagation focused = BeamGeometryOps.prepareRadiusPropagation(
                new BeamEnvelope(BeamModel.FOCUSED, 1.4D, 0.12D, 0.55D, 1.0D, 1.0D, 0.0D, 0, 0)
        );
        OpticalCollisionBox box = new OpticalCollisionBox(0.125D, 0.875D, 0.125D, 0.875D, 0.25D, 0.75D);
        CuboidSweep sweep = CuboidSweep.create(focused, 0, 0, BlockPos.ZERO, 1, 0, box);
        if (sweep == null) {
            throw new IllegalStateException("cuboid sweep verification did not create the focused sweep");
        }

        int checks = 0;
        if (sweep.prefixHull(box.minTravel()) != null) {
            throw new IllegalStateException("cuboid sweep activated on its own coplanar front");
        }
        checks++;

        CanonicalRect previous = null;
        for (int step = 1; step <= 32; step++) {
            double travel = lerp(box.minTravel(), box.maxTravel(), step / 32.0D);
            CanonicalRect prefix = sweep.prefixHull(travel);
            if (prefix == null) {
                throw new IllegalStateException("cuboid sweep lost a non-empty prefix at step=" + step);
            }
            if (previous != null && !containsRect(prefix, previous)) {
                throw new IllegalStateException("cuboid sweep prefix is not monotone at step=" + step);
            }
            for (int sample = 0; sample <= step; sample++) {
                double sampleTravel = lerp(box.minTravel(), travel, sample / (double) step);
                ProjectionRect sampleRect = projectionRectForLocalFace(
                        radiusAt(focused, sampleTravel),
                        0,
                        0,
                        box.minU(),
                        box.minV(),
                        box.maxU(),
                        box.maxV()
                );
                if (sampleRect != null && !containsRect(prefix, sampleRect.canonicalRect())) {
                    throw new IllegalStateException("cuboid sweep prefix missed a sampled section at step=" + step);
                }
            }
            previous = prefix;
            checks++;
        }
        if (previous == null || !sameRect(previous, sweep.fullHull())) {
            throw new IllegalStateException("cuboid sweep final prefix differs from full hull");
        }
        checks++;

        if (!sweepPrefixWindowsBeforeFront(List.of(sweep), box.minTravel()).isEmpty()) {
            throw new IllegalStateException("cuboid sweep violated front-before-activation ordering");
        }
        if (sweepPrefixWindowsBeforeFront(List.of(sweep), box.minTravel() + 0.125D).size() != 1) {
            throw new IllegalStateException("cuboid sweep did not activate after its front");
        }
        checks += 2;

        SameDepthOcclusionIndex ownerIndex = new SameDepthOcclusionIndex(List.of(sweep));
        CanonicalRect candidate = sweep.fullHull();
        if (!ownerIndex.before(BlockPos.ZERO, 0, box.maxTravel(), candidate, null).isEmpty()) {
            throw new IllegalStateException("cuboid sweep did not exclude its receiving cuboid");
        }
        if (ownerIndex.before(new BlockPos(1, 0, 0), 0, box.maxTravel(), candidate, null).size() != 1) {
            throw new IllegalStateException("cuboid sweep index missed a non-owner prefix");
        }
        checks += 2;

        BeamGeometryOps.RadiusPropagation constant = BeamGeometryOps.prepareRadiusPropagation(
                BeamEnvelope.collimated(2.0D)
        );
        CuboidSweep edgeA = CuboidSweep.create(
                constant,
                0,
                0,
                BlockPos.ZERO,
                1,
                0,
                new OpticalCollisionBox(0.0D, 0.5D, 0.5D, 1.0D, 0.0D, 1.0D)
        );
        CuboidSweep edgeB = CuboidSweep.create(
                constant,
                1,
                0,
                new BlockPos(1, 0, 0),
                1,
                0,
                new OpticalCollisionBox(0.5D, 1.0D, 0.0D, 0.5D, 0.0D, 1.0D)
        );
        if (edgeA == null || edgeB == null
                || edgeA.fullHull().maxU() + EDGE_TOUCH_EPSILON < edgeB.fullHull().minU()
                || edgeB.fullHull().maxU() + EDGE_TOUCH_EPSILON < edgeA.fullHull().minU()
                || edgeA.fullHull().maxV() + EDGE_TOUCH_EPSILON < edgeB.fullHull().minV()
                || edgeB.fullHull().maxV() + EDGE_TOUCH_EPSILON < edgeA.fullHull().minV()) {
            throw new IllegalStateException("edge-connected cuboid sweeps left a canonical gap");
        }
        checks++;
        return checks;
    }

    private static boolean containsRect(CanonicalRect outer, CanonicalRect inner) {
        return outer.minU() <= inner.minU() + EDGE_TOUCH_EPSILON
                && outer.minV() <= inner.minV() + EDGE_TOUCH_EPSILON
                && outer.maxU() + EDGE_TOUCH_EPSILON >= inner.maxU()
                && outer.maxV() + EDGE_TOUCH_EPSILON >= inner.maxV();
    }

    private static boolean sameRect(CanonicalRect first, CanonicalRect second) {
        return Math.abs(first.minU() - second.minU()) <= EDGE_TOUCH_EPSILON
                && Math.abs(first.minV() - second.minV()) <= EDGE_TOUCH_EPSILON
                && Math.abs(first.maxU() - second.maxU()) <= EDGE_TOUCH_EPSILON
                && Math.abs(first.maxV() - second.maxV()) <= EDGE_TOUCH_EPSILON;
    }

    private static List<CanonicalRect> randomVerificationRects(Random random, int count) {
        List<CanonicalRect> rects = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rects.add(randomVerificationRect(random));
        }
        return rects;
    }

    private static CanonicalRect randomVerificationRect(Random random) {
        int minU = random.nextInt(8);
        int maxU = minU + 1 + random.nextInt(8 - minU);
        int minV = random.nextInt(8);
        int maxV = minV + 1 + random.nextInt(8 - minV);
        return new CanonicalRect(minU / 8.0D, minV / 8.0D, maxU / 8.0D, maxV / 8.0D);
    }

    private static void addIndependentSideQuads(
            Level level,
            BlockPos depthOrigin,
            int depth,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            int minTile,
            int maxTile,
            double maxUnitRadius,
            SideScanTileBounds tileBounds,
            SideScanTileBounds faceTileBounds,
            DepthTileCache depthTileCache,
            List<DepthTile> sideProjectableTiles,
            BeamPacket targetTemplate,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            CanonicalRegion remainingRayWindows,
            List<CuboidSweep> sameDepthSweeps,
            List<DepthCuboidProbe> sameDepthCuboidProbes,
            boolean collectAllocations,
            ProjectionStatsBuilder stats,
            LongSet dependencies,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        stats.sideRangeCulledTiles += tileBounds.culledTiles();
        stats.sideTilesScanned += faceTileBounds.tileCount();

        if (faceTileBounds.empty()) {
            return;
        }

        long occlusionIndexStartNanos = stats.startTimer();
        SameDepthOcclusionIndex sameDepthIndex = SameDepthOcclusionIndex.create(sameDepthSweeps);
        stats.addSideOcclusionIndexBuildNanos(occlusionIndexStartNanos);

        boolean validateCandidates = stats.sideCandidateValidationEnabled();
        Map<SideCandidateKey, String> boundaryRejections = validateCandidates ? new HashMap<>() : null;
        long candidateStartNanos = stats.startTimer();
        List<SideFaceCandidate> sideCandidates = collectBoundarySideCandidates(
                level,
                sideProjectableTiles,
                uDirection,
                vDirection,
                depthTileCache,
                radiusPropagation,
                collectAllocations,
                stats,
                dependencies,
                boundaryRejections
        );
        stats.addSideCandidateNanos(candidateStartNanos);
        stats.recordSideBoundaryCandidateCount(sideCandidates.size());

        Set<SideCandidateKey> boundarySideCandidates = validateCandidates
                ? sideCandidateKeys(sideCandidates)
                : null;
        Map<SurfaceSpotKey, SpotRecord> appearanceCache = new HashMap<>();

        long emitStartNanos = stats.startTimer();
        for (SideFaceCandidate candidate : sideCandidates) {
            addSideCandidateQuads(
                    level,
                    candidate,
                    travelDirection,
                    uDirection,
                    vDirection,
                    targetTemplate,
                    radiusPropagation,
                    beamPower,
                    coherentBeamPower,
                    visualDistanceFactor,
                    remainingRayWindows,
                    sameDepthSweeps,
                    sameDepthIndex,
                    sameDepthCuboidProbes,
                    collectAllocations,
                    stats,
                    appearanceCache,
                    allocations,
                    fragments
            );
        }
        stats.addSideEmitNanos(emitStartNanos);

        if (boundarySideCandidates != null) {
            long validationStartNanos = stats.startTimer();
            Set<SideCandidateKey> legacySideCandidates = collectLegacySideCandidates(
                    level,
                    sideProjectableTiles,
                    uDirection,
                    vDirection,
                    targetTemplate.envelope()
            );
            stats.addSideBoundaryVerifyNanos(validationStartNanos);
            stats.recordSideCandidateComparison(
                    level,
                    depth,
                    legacySideCandidates,
                    boundarySideCandidates,
                    boundaryRejections
            );
        }
    }

    private static List<SideFaceCandidate> collectBoundarySideCandidates(
            Level level,
            List<DepthTile> projectableTiles,
            Direction uDirection,
            Direction vDirection,
            DepthTileCache depthTileCache,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            boolean collectAllocationDetails,
            ProjectionStatsBuilder stats,
            LongSet dependencies,
            Map<SideCandidateKey, String> rejectionReasons
    ) {
        List<SideFaceCandidate> candidates = new ArrayList<>();

        for (DepthTile targetTile : projectableTiles) {
            stats.sideCandidateTilesVisited++;
            int tileU = targetTile.tileU();
            int tileV = targetTile.tileV();

            for (int boxIndex = 0; boxIndex < targetTile.opticalBoxes().size(); boxIndex++) {
                OpticalCollisionBox box = targetTile.opticalBoxes().get(boxIndex);
                maybeRecordBoxUSideCandidate(
                            level,
                            targetTile,
                            boxIndex,
                            box,
                            uDirection.getOpposite(),
                            tileU - 0.5D + box.minU(),
                            box.minU(),
                            tileV,
                            false,
                            depthTileCache,
                            radiusPropagation,
                            collectAllocationDetails,
                            stats,
                            dependencies,
                            candidates,
                            rejectionReasons
                    );
                maybeRecordBoxUSideCandidate(
                            level,
                            targetTile,
                            boxIndex,
                            box,
                            uDirection,
                            tileU - 0.5D + box.maxU(),
                            box.maxU(),
                            tileV,
                            true,
                            depthTileCache,
                            radiusPropagation,
                            collectAllocationDetails,
                            stats,
                            dependencies,
                            candidates,
                            rejectionReasons
                    );
                maybeRecordBoxVSideCandidate(
                            level,
                            targetTile,
                            boxIndex,
                            box,
                            vDirection.getOpposite(),
                            tileV - 0.5D + box.minV(),
                            box.minV(),
                            tileU,
                            false,
                            depthTileCache,
                            radiusPropagation,
                            collectAllocationDetails,
                            stats,
                            dependencies,
                            candidates,
                            rejectionReasons
                    );
                maybeRecordBoxVSideCandidate(
                            level,
                            targetTile,
                            boxIndex,
                            box,
                            vDirection,
                            tileV - 0.5D + box.maxV(),
                            box.maxV(),
                            tileU,
                            true,
                            depthTileCache,
                            radiusPropagation,
                            collectAllocationDetails,
                            stats,
                            dependencies,
                            candidates,
                            rejectionReasons
                    );
            }
        }

        return candidates;
    }

    private static void maybeRecordBoxUSideCandidate(
            Level level,
            DepthTile targetTile,
            int boxIndex,
            OpticalCollisionBox box,
            Direction sideFace,
            double boundaryWorldU,
            double fixedULocal,
            int tileV,
            boolean positiveSide,
            DepthTileCache depthTileCache,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            boolean collectAllocationDetails,
            ProjectionStatsBuilder stats,
            LongSet dependencies,
            List<SideFaceCandidate> candidates,
            Map<SideCandidateKey, String> rejectionReasons
    ) {
        stats.sideBoundaryFaceTests++;
        stats.sideBoundaryProjectableFaces++;
        SideCandidateKey candidateKey = new SideCandidateKey(targetTile.pos().asLong(), sideFace);
        if (!isOpticalBoxSideReachable(level, depthTileCache, targetTile, sideFace, fixedULocal, stats, dependencies)) {
            recordBoundaryRejection(rejectionReasons, candidateKey, boundaryRejectionDetail(
                    "unreachable_or_closed", SideAxis.U, boxIndex, fixedULocal, positiveSide, 0
            ));
            return;
        }

        List<ReceivingSideRegion> exposedRegions = receivingSideFaceRegions(targetTile, box, true, fixedULocal, positiveSide);
        boolean emittedCandidate = false;

        for (ReceivingSideRegion receivingRegion : exposedRegions) {
            CanonicalRect exposedRegion = receivingRegion.region();
            List<TravelInterval> travels = uSideTravelIntervals(
                    radiusPropagation,
                    boundaryWorldU,
                    tileV,
                    exposedRegion.minV(),
                    exposedRegion.maxV(),
                    exposedRegion.minU(),
                    exposedRegion.maxU()
            );
            stats.sideBoundaryTravelIntervals += travels.size();

            if (travels.isEmpty()) {
                continue;
            }

            emittedCandidate = true;
            stats.sideBoundaryTravelFaces++;
            ProjectionFace projectionFace = ProjectionFace.sideU(
                    sideFace,
                    positiveSide,
                    fixedULocal,
                    exposedRegion.minU(),
                    exposedRegion.maxU(),
                    exposedRegion.minV(),
                    exposedRegion.maxV()
            );
            candidates.add(new SideFaceCandidate(
                    SideAxis.U,
                    targetTile,
                    boxIndex,
                    sideFace,
                    boundaryWorldU,
                    fixedULocal,
                    tileV,
                    exposedRegion.minU(),
                    exposedRegion.maxU(),
                    exposedRegion.minV(),
                    exposedRegion.maxV(),
                    List.copyOf(travels),
                    projectionFace,
                    collectAllocationDetails && isInternalProjectionSide(fixedULocal)
                            ? sideCandidateDetail("u-side", targetTile, boxIndex, box, fixedULocal, positiveSide, receivingRegion, travels)
                            : ""
            ));
        }

        if (emittedCandidate) {
            stats.sideBoundaryOpenFaces++;
            clearBoundaryRejection(rejectionReasons, candidateKey);
        } else {
            recordBoundaryRejection(
                    rejectionReasons,
                    candidateKey,
                    boundaryRejectionDetail(
                            exposedRegions.isEmpty() ? "no_exposed_region" : "no_beam_travel",
                            SideAxis.U,
                            boxIndex,
                            fixedULocal,
                            positiveSide,
                            exposedRegions.size()
                    )
            );
        }
    }

    private static void maybeRecordBoxVSideCandidate(
            Level level,
            DepthTile targetTile,
            int boxIndex,
            OpticalCollisionBox box,
            Direction sideFace,
            double boundaryWorldV,
            double fixedVLocal,
            int tileU,
            boolean positiveSide,
            DepthTileCache depthTileCache,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            boolean collectAllocationDetails,
            ProjectionStatsBuilder stats,
            LongSet dependencies,
            List<SideFaceCandidate> candidates,
            Map<SideCandidateKey, String> rejectionReasons
    ) {
        stats.sideBoundaryFaceTests++;
        stats.sideBoundaryProjectableFaces++;
        SideCandidateKey candidateKey = new SideCandidateKey(targetTile.pos().asLong(), sideFace);
        if (!isOpticalBoxSideReachable(level, depthTileCache, targetTile, sideFace, fixedVLocal, stats, dependencies)) {
            recordBoundaryRejection(rejectionReasons, candidateKey, boundaryRejectionDetail(
                    "unreachable_or_closed", SideAxis.V, boxIndex, fixedVLocal, positiveSide, 0
            ));
            return;
        }

        List<ReceivingSideRegion> exposedRegions = receivingSideFaceRegions(targetTile, box, false, fixedVLocal, positiveSide);
        boolean emittedCandidate = false;

        for (ReceivingSideRegion receivingRegion : exposedRegions) {
            CanonicalRect exposedRegion = receivingRegion.region();
            List<TravelInterval> travels = vSideTravelIntervals(
                    radiusPropagation,
                    boundaryWorldV,
                    tileU,
                    exposedRegion.minV(),
                    exposedRegion.maxV(),
                    exposedRegion.minU(),
                    exposedRegion.maxU()
            );
            stats.sideBoundaryTravelIntervals += travels.size();

            if (travels.isEmpty()) {
                continue;
            }

            emittedCandidate = true;
            stats.sideBoundaryTravelFaces++;
            ProjectionFace projectionFace = ProjectionFace.sideV(
                    sideFace,
                    positiveSide,
                    fixedVLocal,
                    exposedRegion.minU(),
                    exposedRegion.maxU(),
                    exposedRegion.minV(),
                    exposedRegion.maxV()
            );
            candidates.add(new SideFaceCandidate(
                    SideAxis.V,
                    targetTile,
                    boxIndex,
                    sideFace,
                    boundaryWorldV,
                    fixedVLocal,
                    tileU,
                    exposedRegion.minU(),
                    exposedRegion.maxU(),
                    exposedRegion.minV(),
                    exposedRegion.maxV(),
                    List.copyOf(travels),
                    projectionFace,
                    collectAllocationDetails && isInternalProjectionSide(fixedVLocal)
                            ? sideCandidateDetail("v-side", targetTile, boxIndex, box, fixedVLocal, positiveSide, receivingRegion, travels)
                            : ""
            ));
        }

        if (emittedCandidate) {
            stats.sideBoundaryOpenFaces++;
            clearBoundaryRejection(rejectionReasons, candidateKey);
        } else {
            recordBoundaryRejection(
                    rejectionReasons,
                    candidateKey,
                    boundaryRejectionDetail(
                            exposedRegions.isEmpty() ? "no_exposed_region" : "no_beam_travel",
                            SideAxis.V,
                            boxIndex,
                            fixedVLocal,
                            positiveSide,
                            exposedRegions.size()
                    )
            );
        }
    }

    private static String boundaryRejectionDetail(
            String stage,
            SideAxis axis,
            int boxIndex,
            double fixedLocal,
            boolean positiveSide,
            int exposedRegions
    ) {
        return String.format(
                Locale.ROOT,
                "stage=%s,axis=%s,box=%d,fixed=%.6f,polarity=%s,exposed_regions=%d",
                stage,
                axis.name().toLowerCase(Locale.ROOT),
                boxIndex,
                fixedLocal,
                positiveSide ? "positive" : "negative",
                Math.max(0, exposedRegions)
        );
    }

    private static void recordBoundaryRejection(
            Map<SideCandidateKey, String> rejectionReasons,
            SideCandidateKey key,
            String reason
    ) {
        if (rejectionReasons != null) {
            rejectionReasons.merge(key, reason, (first, next) -> first + " || " + next);
        }
    }

    private static void clearBoundaryRejection(
            Map<SideCandidateKey, String> rejectionReasons,
            SideCandidateKey key
    ) {
        if (rejectionReasons != null) {
            rejectionReasons.remove(key);
        }
    }

    private static boolean isBoundarySideOpen(
            Level level,
            DepthTileCache depthTileCache,
            DepthTile targetTile,
            Direction sideFace,
            int neighborU,
            int neighborV,
            ProjectionStatsBuilder stats,
            LongSet dependencies
    ) {
        stats.sideOpenChecks++;
        BlockPos neighborPos = targetTile.pos().relative(sideFace);
        dependencies.add(neighborPos.asLong());
        DepthTile neighborTile = loadDepthTile(level, depthTileCache, neighborU, neighborV, stats);

        if (!neighborTile.loaded()) {
            return false;
        }

        boolean open = neighborTile.airLike();
        if (open) {
            stats.sideOpenFaces++;
        }
        return open;
    }

    private static boolean isOpticalBoxSideReachable(
            Level level,
            DepthTileCache depthTileCache,
            DepthTile targetTile,
            Direction sideFace,
            double fixedLocal,
            ProjectionStatsBuilder stats,
            LongSet dependencies
    ) {
        if (fixedLocal > EDGE_TOUCH_EPSILON && fixedLocal < 1.0D - EDGE_TOUCH_EPSILON) {
            return true;
        }

        int neighborU = targetTile.tileU();
        int neighborV = targetTile.tileV();
        if (sideFace == depthTileCache.uDirection) {
            neighborU++;
        } else if (sideFace == depthTileCache.uDirection.getOpposite()) {
            neighborU--;
        } else if (sideFace == depthTileCache.vDirection) {
            neighborV++;
        } else if (sideFace == depthTileCache.vDirection.getOpposite()) {
            neighborV--;
        }

        return isBoundarySideOpen(level, depthTileCache, targetTile, sideFace, neighborU, neighborV, stats, dependencies);
    }

    private static List<CanonicalRect> exposedFrontFaceRegions(DepthTile targetTile, OpticalCollisionBox box) {
        CanonicalRect faceRegion = new CanonicalRect(
                box.minU(),
                box.minV(),
                box.maxU(),
                box.maxV()
        );
        List<OcclusionWindow> coveredRegions = new ArrayList<>();

        for (OpticalCollisionBox other : targetTile.opticalBoxes()) {
            if (other == box) {
                continue;
            }

            if (Math.abs(other.maxTravel() - box.minTravel()) > EDGE_TOUCH_EPSILON) {
                continue;
            }

            if (!rangesOverlap(box.minU(), box.maxU(), other.minU(), other.maxU())
                    || !rangesOverlap(box.minV(), box.maxV(), other.minV(), other.maxV())) {
                continue;
            }

            double minU = Math.max(box.minU(), other.minU());
            double maxU = Math.min(box.maxU(), other.maxU());
            double minV = Math.max(box.minV(), other.minV());
            double maxV = Math.min(box.maxV(), other.maxV());

            if (maxU - minU <= EDGE_TOUCH_EPSILON || maxV - minV <= EDGE_TOUCH_EPSILON) {
                continue;
            }

            coveredRegions.add(new OcclusionWindow(
                    new CanonicalRect(minU, minV, maxU, maxV),
                    "sibling_front_cover",
                    targetTile.pos(),
                    0,
                    targetTile.tileU(),
                    targetTile.tileV(),
                    -1,
                    0.0D,
                    0.0D
            ));
        }

        if (coveredRegions.isEmpty()) {
            return List.of(faceRegion);
        }

        CanonicalRegion visibleRegion = CanonicalRegion.fromRects(List.of(faceRegion));
        visibleRegion.subtractUnion(coveredRegions, null);
        return visibleRegion.toRects();
    }

    private static List<CanonicalRect> exposedSideFaceRegions(
            DepthTile targetTile,
            OpticalCollisionBox box,
            boolean uSide,
            double fixedLocal,
            boolean positiveSide
    ) {
        CanonicalRect faceRegion = new CanonicalRect(
                box.minTravel(),
                uSide ? box.minV() : box.minU(),
                box.maxTravel(),
                uSide ? box.maxV() : box.maxU()
        );
        List<OcclusionWindow> coveredRegions = new ArrayList<>();

        for (OpticalCollisionBox other : targetTile.opticalBoxes()) {
            if (other == box) {
                continue;
            }

            if (!rangesOverlap(box.minTravel(), box.maxTravel(), other.minTravel(), other.maxTravel())) {
                continue;
            }

            if (uSide) {
                if (!rangesOverlap(box.minV(), box.maxV(), other.minV(), other.maxV())) {
                    continue;
                }

                if (positiveSide && Math.abs(other.minU() - fixedLocal) <= EDGE_TOUCH_EPSILON) {
                    addCoveredSideRegion(coveredRegions, targetTile, box, other, true);
                }
                if (!positiveSide && Math.abs(other.maxU() - fixedLocal) <= EDGE_TOUCH_EPSILON) {
                    addCoveredSideRegion(coveredRegions, targetTile, box, other, true);
                }
            } else {
                if (!rangesOverlap(box.minU(), box.maxU(), other.minU(), other.maxU())) {
                    continue;
                }

                if (positiveSide && Math.abs(other.minV() - fixedLocal) <= EDGE_TOUCH_EPSILON) {
                    addCoveredSideRegion(coveredRegions, targetTile, box, other, false);
                }
                if (!positiveSide && Math.abs(other.maxV() - fixedLocal) <= EDGE_TOUCH_EPSILON) {
                    addCoveredSideRegion(coveredRegions, targetTile, box, other, false);
                }
            }
        }

        if (coveredRegions.isEmpty()) {
            return List.of(faceRegion);
        }

        CanonicalRegion visibleRegion = CanonicalRegion.fromRects(List.of(faceRegion));
        visibleRegion.subtractUnion(coveredRegions, null);
        return visibleRegion.toRects();
    }

    private static List<ReceivingSideRegion> receivingSideFaceRegions(
            DepthTile targetTile,
            OpticalCollisionBox box,
            boolean uSide,
            double fixedLocal,
            boolean positiveSide
    ) {
        List<CanonicalRect> exposedRegions = exposedSideFaceRegions(targetTile, box, uSide, fixedLocal, positiveSide);

        if (exposedRegions.isEmpty()) {
            return List.of();
        }

        List<ReceivingSideRegion> receivingRegions = new ArrayList<>();

        for (CanonicalRect exposedRegion : exposedRegions) {
            List<OcclusionWindow> sourceSideCovers = sourceSideCoverRegions(
                    targetTile,
                    box,
                    uSide,
                    fixedLocal,
                    exposedRegion
            );

            if (sourceSideCovers.isEmpty()) {
                receivingRegions.add(new ReceivingSideRegion(exposedRegion, exposedRegion, 0, 0.0D));
                continue;
            }

            CanonicalRegion visibleRegion = CanonicalRegion.fromRects(List.of(exposedRegion));
            visibleRegion.subtractUnion(sourceSideCovers, null);
            double sourceCoverArea = occlusionAreaSum(sourceSideCovers);
            for (CanonicalRect region : visibleRegion.toRects()) {
                receivingRegions.add(new ReceivingSideRegion(region, exposedRegion, sourceSideCovers.size(), sourceCoverArea));
            }
        }

        return receivingRegions.isEmpty() ? List.of() : receivingRegions;
    }

    private static List<OcclusionWindow> sourceSideCoverRegions(
            DepthTile targetTile,
            OpticalCollisionBox box,
            boolean uSide,
            double fixedLocal,
            CanonicalRect exposedRegion
    ) {
        List<OcclusionWindow> coveredRegions = new ArrayList<>();

        for (int otherIndex = 0; otherIndex < targetTile.opticalBoxes().size(); otherIndex++) {
            OpticalCollisionBox other = targetTile.opticalBoxes().get(otherIndex);

            if (other == box) {
                continue;
            }

            if (other.minTravel() >= exposedRegion.maxU() - EDGE_TOUCH_EPSILON) {
                continue;
            }

            if (!boxCoversSideCoordinate(other, uSide, fixedLocal)) {
                continue;
            }

            double minCross = uSide
                    ? Math.max(exposedRegion.minV(), other.minV())
                    : Math.max(exposedRegion.minV(), other.minU());
            double maxCross = uSide
                    ? Math.min(exposedRegion.maxV(), other.maxV())
                    : Math.min(exposedRegion.maxV(), other.maxU());

            if (maxCross - minCross <= EDGE_TOUCH_EPSILON) {
                continue;
            }

            double minTravel = Math.max(exposedRegion.minU(), other.minTravel());
            double maxTravel = exposedRegion.maxU();

            if (maxTravel - minTravel <= EDGE_TOUCH_EPSILON) {
                continue;
            }

            coveredRegions.add(new OcclusionWindow(
                    new CanonicalRect(minTravel, minCross, maxTravel, maxCross),
                    "sibling_source_side_cover",
                    targetTile.pos(),
                    0,
                    targetTile.tileU(),
                    targetTile.tileV(),
                    otherIndex,
                    other.minTravel(),
                    other.minTravel()
            ));
        }

        return coveredRegions.isEmpty() ? List.of() : coveredRegions;
    }

    private static boolean boxCoversSideCoordinate(OpticalCollisionBox box, boolean uSide, double fixedLocal) {
        return uSide
                ? fixedLocal >= box.minU() - EDGE_TOUCH_EPSILON && fixedLocal <= box.maxU() + EDGE_TOUCH_EPSILON
                : fixedLocal >= box.minV() - EDGE_TOUCH_EPSILON && fixedLocal <= box.maxV() + EDGE_TOUCH_EPSILON;
    }

    private static void addCoveredSideRegion(
            List<OcclusionWindow> coveredRegions,
            DepthTile targetTile,
            OpticalCollisionBox box,
            OpticalCollisionBox other,
            boolean uSide
    ) {
        double minTravel = Math.max(box.minTravel(), other.minTravel());
        double maxTravel = Math.min(box.maxTravel(), other.maxTravel());
        double minCross = uSide ? Math.max(box.minV(), other.minV()) : Math.max(box.minU(), other.minU());
        double maxCross = uSide ? Math.min(box.maxV(), other.maxV()) : Math.min(box.maxU(), other.maxU());

        if (maxTravel - minTravel <= EDGE_TOUCH_EPSILON || maxCross - minCross <= EDGE_TOUCH_EPSILON) {
            return;
        }

        coveredRegions.add(new OcclusionWindow(
                new CanonicalRect(minTravel, minCross, maxTravel, maxCross),
                "sibling_side_cover",
                targetTile.pos(),
                0,
                targetTile.tileU(),
                targetTile.tileV(),
                -1,
                minTravel,
                minTravel
        ));
    }

    private static boolean rangesOverlap(double aMin, double aMax, double bMin, double bMax) {
        return aMax - bMin > EDGE_TOUCH_EPSILON && bMax - aMin > EDGE_TOUCH_EPSILON;
    }

    private static Set<SideCandidateKey> sideCandidateKeys(List<SideFaceCandidate> candidates) {
        Set<SideCandidateKey> keys = new HashSet<>();

        for (SideFaceCandidate candidate : candidates) {
            recordSideCandidate(keys, candidate.tile().pos(), candidate.sideFace());
        }

        return keys;
    }

    private static Set<SideCandidateKey> collectLegacySideCandidates(
            Level level,
            List<DepthTile> sideProjectableTiles,
            Direction uDirection,
            Direction vDirection,
            BeamEnvelope envelope
    ) {
        Set<SideCandidateKey> candidates = new HashSet<>();

        for (DepthTile targetTile : sideProjectableTiles) {
            for (OpticalCollisionBox box : targetTile.opticalBoxes()) {
                recordLegacyUSideCandidate(
                        level,
                        targetTile,
                        box,
                        uDirection.getOpposite(),
                        envelope,
                        targetTile.tileU() - 0.5D + box.minU(),
                        box.minU(),
                        false,
                        targetTile.tileV(),
                        candidates
                );
                recordLegacyUSideCandidate(
                        level,
                        targetTile,
                        box,
                        uDirection,
                        envelope,
                        targetTile.tileU() - 0.5D + box.maxU(),
                        box.maxU(),
                        true,
                        targetTile.tileV(),
                        candidates
                );
                recordLegacyVSideCandidate(
                        level,
                        targetTile,
                        box,
                        vDirection.getOpposite(),
                        envelope,
                        targetTile.tileV() - 0.5D + box.minV(),
                        box.minV(),
                        false,
                        targetTile.tileU(),
                        candidates
                );
                recordLegacyVSideCandidate(
                        level,
                        targetTile,
                        box,
                        vDirection,
                        envelope,
                        targetTile.tileV() - 0.5D + box.maxV(),
                        box.maxV(),
                        true,
                        targetTile.tileU(),
                        candidates
                );
            }
        }

        return candidates;
    }

    private static void recordLegacyUSideCandidate(
            Level level,
            DepthTile targetTile,
            OpticalCollisionBox box,
            Direction sideFace,
            BeamEnvelope envelope,
            double boundaryWorldU,
            double fixedULocal,
            boolean positiveSide,
            int tileV,
            Set<SideCandidateKey> candidates
    ) {
        if (!isLegacyShapeSideReachable(level, targetTile, sideFace, fixedULocal)) {
            return;
        }

        for (ReceivingSideRegion receivingRegion : receivingSideFaceRegions(
                targetTile, box, true, fixedULocal, positiveSide
        )) {
            CanonicalRect region = receivingRegion.region();
            for (TravelInterval travel : uSideTravelIntervals(
                    envelope,
                    boundaryWorldU,
                    tileV,
                    region.minV(),
                    region.maxV(),
                    region.minU(),
                    region.maxU()
            )) {
                if (uSideIntervalHasWindow(envelope, travel, boundaryWorldU, positiveSide, tileV)) {
                    recordSideCandidate(candidates, targetTile.pos(), sideFace);
                    return;
                }
            }
        }
    }

    private static void recordLegacyVSideCandidate(
            Level level,
            DepthTile targetTile,
            OpticalCollisionBox box,
            Direction sideFace,
            BeamEnvelope envelope,
            double boundaryWorldV,
            double fixedVLocal,
            boolean positiveSide,
            int tileU,
            Set<SideCandidateKey> candidates
    ) {
        if (!isLegacyShapeSideReachable(level, targetTile, sideFace, fixedVLocal)) {
            return;
        }

        for (ReceivingSideRegion receivingRegion : receivingSideFaceRegions(
                targetTile, box, false, fixedVLocal, positiveSide
        )) {
            CanonicalRect region = receivingRegion.region();
            for (TravelInterval travel : vSideTravelIntervals(
                    envelope,
                    boundaryWorldV,
                    tileU,
                    region.minV(),
                    region.maxV(),
                    region.minU(),
                    region.maxU()
            )) {
                if (vSideIntervalHasWindow(envelope, travel, boundaryWorldV, positiveSide, tileU)) {
                    recordSideCandidate(candidates, targetTile.pos(), sideFace);
                    return;
                }
            }
        }
    }

    private static boolean isLegacyShapeSideReachable(
            Level level,
            DepthTile targetTile,
            Direction sideFace,
            double fixedLocal
    ) {
        return (fixedLocal > EDGE_TOUCH_EPSILON && fixedLocal < 1.0D - EDGE_TOUCH_EPSILON)
                || isSideOpenForValidation(level, targetTile.pos(), sideFace);
    }

    private static boolean uSideIntervalHasWindow(
            BeamEnvelope envelope,
            TravelInterval visibleTravel,
            double boundaryWorldU,
            boolean positiveSide,
            int tileV
    ) {
        for (TravelInterval chartTravel : splitSideTravelAtCrossBoundaries(
                envelope,
                visibleTravel,
                tileV - 0.5D,
                tileV + 0.5D
        )) {
            int travelSteps = sideChartTravelSubdivisionCount(envelope, chartTravel.min(), chartTravel.max());

            for (int travelIndex = 0; travelIndex < travelSteps; travelIndex++) {
                double rawTravel0 = lerp(chartTravel.min(), chartTravel.max(), travelIndex / (double) travelSteps);
                double rawTravel1 = lerp(chartTravel.min(), chartTravel.max(), (travelIndex + 1) / (double) travelSteps);
                double crossTravel0 = nudgeUSideTravelEndpoint(envelope, rawTravel0, rawTravel1, boundaryWorldU, tileV);
                double crossTravel1 = nudgeUSideTravelEndpoint(envelope, rawTravel1, rawTravel0, boundaryWorldU, tileV);

                if (sideWindowExistsForUSide(envelope, crossTravel0, crossTravel1, boundaryWorldU, positiveSide, tileV)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean vSideIntervalHasWindow(
            BeamEnvelope envelope,
            TravelInterval visibleTravel,
            double boundaryWorldV,
            boolean positiveSide,
            int tileU
    ) {
        for (TravelInterval chartTravel : splitSideTravelAtCrossBoundaries(
                envelope,
                visibleTravel,
                tileU - 0.5D,
                tileU + 0.5D
        )) {
            int travelSteps = sideChartTravelSubdivisionCount(envelope, chartTravel.min(), chartTravel.max());

            for (int travelIndex = 0; travelIndex < travelSteps; travelIndex++) {
                double rawTravel0 = lerp(chartTravel.min(), chartTravel.max(), travelIndex / (double) travelSteps);
                double rawTravel1 = lerp(chartTravel.min(), chartTravel.max(), (travelIndex + 1) / (double) travelSteps);
                double crossTravel0 = nudgeVSideTravelEndpoint(envelope, rawTravel0, rawTravel1, boundaryWorldV, tileU);
                double crossTravel1 = nudgeVSideTravelEndpoint(envelope, rawTravel1, rawTravel0, boundaryWorldV, tileU);

                if (sideWindowExistsForVSide(envelope, crossTravel0, crossTravel1, boundaryWorldV, positiveSide, tileU)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean sideWindowExistsForUSide(
            BeamEnvelope envelope,
            double crossTravel0,
            double crossTravel1,
            double boundaryWorldU,
            boolean positiveSide,
            int tileV
    ) {
        if (crossTravel1 - crossTravel0 <= EDGE_TOUCH_EPSILON) {
            return false;
        }

        double radius0 = radiusAt(envelope, crossTravel0);
        double radius1 = radiusAt(envelope, crossTravel1);

        if (!isRenderableSide(boundaryWorldU, positiveSide, radius0, radius1)) {
            return false;
        }

        SideCrossSection cross0 = uSideCrossSection(radius0, boundaryWorldU, tileV);
        SideCrossSection cross1 = uSideCrossSection(radius1, boundaryWorldU, tileV);

        return cross0 != null
                && cross1 != null
                && sideCanonicalWindow(
                cross0.axisCanonical(),
                cross0.axisCanonical(),
                cross1.axisCanonical(),
                cross1.axisCanonical(),
                cross0.minCanonical(),
                cross0.maxCanonical(),
                cross1.minCanonical(),
                cross1.maxCanonical()
        ) != null;
    }

    private static boolean sideWindowExistsForVSide(
            BeamEnvelope envelope,
            double crossTravel0,
            double crossTravel1,
            double boundaryWorldV,
            boolean positiveSide,
            int tileU
    ) {
        if (crossTravel1 - crossTravel0 <= EDGE_TOUCH_EPSILON) {
            return false;
        }

        double radius0 = radiusAt(envelope, crossTravel0);
        double radius1 = radiusAt(envelope, crossTravel1);

        if (!isRenderableSide(boundaryWorldV, positiveSide, radius0, radius1)) {
            return false;
        }

        SideCrossSection cross0 = vSideCrossSection(radius0, boundaryWorldV, tileU);
        SideCrossSection cross1 = vSideCrossSection(radius1, boundaryWorldV, tileU);

        return cross0 != null
                && cross1 != null
                && sideCanonicalWindow(
                cross0.minCanonical(),
                cross0.maxCanonical(),
                cross1.minCanonical(),
                cross1.maxCanonical(),
                cross0.axisCanonical(),
                cross0.axisCanonical(),
                cross1.axisCanonical(),
                cross1.axisCanonical()
        ) != null;
    }

    private static void recordSideCandidate(Set<SideCandidateKey> candidates, BlockPos pos, Direction face) {
        if (candidates != null) {
            candidates.add(new SideCandidateKey(pos.asLong(), face));
        }
    }

    private static DepthTile loadDepthTile(
            Level level,
            DepthTileCache cache,
            int tileU,
            int tileV,
            ProjectionStatsBuilder stats
    ) {
        DepthTile cached = cache.get(tileU, tileV);

        if (cached != null) {
            stats.depthTileCacheHits++;
            return cached;
        }

        stats.depthTileCacheMisses++;
        BlockPos targetPos = cache.pos(tileU, tileV);
        boolean loaded = level.isLoaded(targetPos);

        if (!loaded) {
            DepthTile tile = new DepthTile(tileU, tileV, targetPos, false, null, false, List.of());
            cache.put(tile);
            return tile;
        }

        BlockState targetState = level.getBlockState(targetPos);
        boolean airLike = OpticalMaterialProfiles.isAirLike(targetState);
        List<OpticalCollisionBox> opticalBoxes = airLike
                ? List.of()
                : opticalCollisionBoxes(
                level,
                targetPos,
                targetState,
                cache.travelDirection,
                cache.uDirection,
                cache.vDirection
        );
        DepthTile tile = new DepthTile(tileU, tileV, targetPos, true, targetState, airLike, opticalBoxes);
        cache.put(tile);
        return tile;
    }

    private static void addSideCandidateQuads(
            Level level,
            SideFaceCandidate candidate,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            BeamPacket targetTemplate,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            CanonicalRegion remainingRayWindows,
            List<CuboidSweep> sameDepthSweeps,
            SameDepthOcclusionIndex sameDepthIndex,
            List<DepthCuboidProbe> sameDepthCuboidProbes,
            boolean collectAllocations,
            ProjectionStatsBuilder stats,
            Map<SurfaceSpotKey, SpotRecord> appearanceCache,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        stats.sideTravelIntervals += candidate.visibleTravels().size();
        ProjectionFace face = candidate.projectionFace();
        stats.recordSideTravelIntervals(face.fixedLocal(), candidate.visibleTravels().size());
        if (candidate.axis() == SideAxis.U) {
            addUSideVolumeQuads(
                    level,
                    candidate.tile().pos(),
                    candidate.tile().state(),
                    candidate.boxIndex(),
                    candidate.sideFace(),
                    face.positiveSide(),
                    candidate.candidateDetail(),
                    travelDirection,
                    uDirection,
                    vDirection,
                    face.minTravel(),
                    face.maxTravel(),
                    face.fixedLocal(),
                    candidate.crossTile(),
                    face.minCross(),
                    face.maxCross(),
                    candidate.boundaryWorld(),
                    candidate.visibleTravels(),
                    targetTemplate,
                    radiusPropagation,
                    beamPower,
                    coherentBeamPower,
                    visualDistanceFactor,
                    remainingRayWindows,
                    sameDepthSweeps,
                    sameDepthIndex,
                    sameDepthCuboidProbes,
                    collectAllocations,
                    stats,
                    appearanceCache,
                    allocations,
                    fragments,
                    null
            );
        } else {
            addVSideVolumeQuads(
                    level,
                    candidate.tile().pos(),
                    candidate.tile().state(),
                    candidate.boxIndex(),
                    candidate.sideFace(),
                    face.positiveSide(),
                    candidate.candidateDetail(),
                    travelDirection,
                    uDirection,
                    vDirection,
                    face.minTravel(),
                    face.maxTravel(),
                    face.fixedLocal(),
                    candidate.crossTile(),
                    face.minCross(),
                    face.maxCross(),
                    candidate.boundaryWorld(),
                    candidate.visibleTravels(),
                    targetTemplate,
                    radiusPropagation,
                    beamPower,
                    coherentBeamPower,
                    visualDistanceFactor,
                    remainingRayWindows,
                    sameDepthSweeps,
                    sameDepthIndex,
                    sameDepthCuboidProbes,
                    collectAllocations,
                    stats,
                    appearanceCache,
                    allocations,
                    fragments,
                    null
            );
        }
    }

    private static boolean isProjectableSurface(Level level, BlockPos pos, BlockState state) {
        return !opticalCollisionBoxes(level, pos, state, Direction.NORTH, Direction.EAST, Direction.UP).isEmpty();
    }

    private static List<OpticalCollisionBox> opticalCollisionBoxes(
            Level level,
            BlockPos pos,
            BlockState state,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection
    ) {
        if (OpticalMaterialProfiles.isAirLike(state)) {
            return List.of();
        }

        if (state.getBlock() instanceof LensHolderBlock) {
            return level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder && lensHolder.hasLens()
                    ? FULL_OPTICAL_BOXES
                    : List.of();
        }

        if (state.isCollisionShapeFullBlock(level, pos)
                || state.getBlock() instanceof OpticalElement
                || state.getBlock() instanceof OpticalSource) {
            return FULL_OPTICAL_BOXES;
        }

        if (!usesModelOpticalShape(state)) {
            return List.of();
        }

        ModelShapeFrameKey cacheKey = new ModelShapeFrameKey(
                state, travelDirection, uDirection, vDirection
        );
        List<OpticalCollisionBox> cached = MODEL_SHAPE_BOX_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            MODEL_SHAPE_BOX_CACHE.putIfAbsent(cacheKey, List.of());
            return List.of();
        }

        List<OpticalCollisionBox> boxes = new ArrayList<>();
        for (AABB aabb : shape.toAabbs()) {
            OpticalCollisionBox box = OpticalCollisionBox.fromAabb(aabb, travelDirection, uDirection, vDirection);
            if (box != null) {
                boxes.add(box);
            }
        }

        boxes.sort(Comparator
                .comparingDouble(OpticalCollisionBox::minTravel)
                .thenComparingDouble(OpticalCollisionBox::minU)
                .thenComparingDouble(OpticalCollisionBox::minV)
                .thenComparingDouble(OpticalCollisionBox::maxTravel)
                .thenComparingDouble(OpticalCollisionBox::maxU)
                .thenComparingDouble(OpticalCollisionBox::maxV));
        List<OpticalCollisionBox> immutable = boxes.isEmpty() ? List.of() : List.copyOf(boxes);
        List<OpticalCollisionBox> existing = MODEL_SHAPE_BOX_CACHE.putIfAbsent(cacheKey, immutable);
        return existing == null ? immutable : existing;
    }

    private static boolean usesModelOpticalShape(BlockState state) {
        return state.getBlock() instanceof SlabBlock
                || state.getBlock() instanceof StairBlock
                || state.getBlock() instanceof FenceBlock;
    }

    private static boolean isSideOpen(
            Level level,
            BlockPos pos,
            Direction sideFace,
            LongSet dependencies,
            ProjectionStatsBuilder stats
    ) {
        stats.sideOpenChecks++;
        BlockPos neighborPos = pos.relative(sideFace);
        dependencies.add(neighborPos.asLong());

        if (!level.isLoaded(neighborPos)) {
            return false;
        }

        boolean open = OpticalMaterialProfiles.isAirLike(level.getBlockState(neighborPos));
        if (open) {
            stats.sideOpenFaces++;
        }
        return open;
    }

    private static boolean isSideOpenForValidation(Level level, BlockPos pos, Direction sideFace) {
        BlockPos neighborPos = pos.relative(sideFace);

        if (!level.isLoaded(neighborPos)) {
            return false;
        }

        return OpticalMaterialProfiles.isAirLike(level.getBlockState(neighborPos));
    }

    private static void addFrontChartSpot(
            SpotRecord surfaceSpot,
            Direction displayFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double faceTravel,
            double radius,
            int tileU,
            int tileV,
            ProjectionRect visibleRect,
            List<SpotRecord> fragments
    ) {
        double diameter = radius * 2.0D;
        double tileMinU = tileU - 0.5D;
        double tileMinV = tileV - 0.5D;
        double hitMinU = visibleRect.kernelMinU() * diameter - radius;
        double hitMinV = visibleRect.kernelMinV() * diameter - radius;
        double hitMaxU = visibleRect.kernelMaxU() * diameter - radius;
        double hitMaxV = visibleRect.kernelMaxV() * diameter - radius;
        double localMinU = hitMinU - tileMinU;
        double localMinV = hitMinV - tileMinV;
        double localMaxU = hitMaxU - tileMinU;
        double localMaxV = hitMaxV - tileMinV;
        Patch patch = orientedPatch(
                displayFace,
                localPoint(travelDirection, uDirection, vDirection, faceTravel, localMinU, localMinV),
                new TexturePoint(visibleRect.kernelMinU(), renderTextureV(visibleRect.kernelMinV())),
                localPoint(travelDirection, uDirection, vDirection, faceTravel, localMinU, localMaxV),
                new TexturePoint(visibleRect.kernelMinU(), renderTextureV(visibleRect.kernelMaxV())),
                localPoint(travelDirection, uDirection, vDirection, faceTravel, localMaxU, localMaxV),
                new TexturePoint(visibleRect.kernelMaxU(), renderTextureV(visibleRect.kernelMaxV())),
                localPoint(travelDirection, uDirection, vDirection, faceTravel, localMaxU, localMinV),
                new TexturePoint(visibleRect.kernelMaxU(), renderTextureV(visibleRect.kernelMinV()))
        );

        if (patch != null) {
            addPatchSpot(surfaceSpot, patch, fragments);
        }
    }

    private static void addDebugFaceCenter(BlockPos targetPos, Direction face, List<SpotRecord> fragments) {
        if (debugFaceCentersEnabled) {
            fragments.add(SpotRecord.debugFaceCenter(targetPos, face));
        }
    }

    private static void addUSideVolumeQuads(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            int ownerBoxIndex,
            Direction sideFace,
            boolean positiveSide,
            String candidateDetail,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double entryTravel,
            double exitTravel,
            double fixedULocal,
            int tileV,
            double crossMinLocal,
            double crossMaxLocal,
            double boundaryWorldU,
            List<TravelInterval> visibleTravels,
            BeamPacket targetTemplate,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            CanonicalRegion remainingRayWindows,
            List<CuboidSweep> sameDepthSweeps,
            SameDepthOcclusionIndex sameDepthIndex,
            List<DepthCuboidProbe> sameDepthCuboidProbes,
            boolean collectAllocations,
            ProjectionStatsBuilder stats,
            Map<SurfaceSpotKey, SpotRecord> appearanceCache,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments,
            Set<SideCandidateKey> legacySideCandidates
    ) {
        if (visibleTravels.isEmpty()) {
            return;
        }

        boolean addedDebugCenter = false;
        boolean internalSide = isInternalProjectionSide(fixedULocal);
        double sidePower = visualSidePower(beamPower, visualDistanceFactor);
        double sideCoherentPower = visualSidePower(coherentBeamPower, visualDistanceFactor);
        SpotRecord candidateSurfaceSpot = null;

        for (TravelInterval visibleTravel : visibleTravels) {
            long travelSplitStartNanos = stats.startTimer();
            List<TravelInterval> chartTravels = splitSideTravelAtCrossBoundaries(
                    radiusPropagation,
                    visibleTravel,
                    tileV - 0.5D + crossMinLocal,
                    tileV - 0.5D + crossMaxLocal
            );
            stats.addSideTravelSplitNanos(travelSplitStartNanos);

            for (TravelInterval chartTravel : chartTravels) {
                double chartEntryRadius = radiusAt(radiusPropagation, chartTravel.min());
                double chartExitRadius = radiusAt(radiusPropagation, chartTravel.max());
                if (!isRenderableSide(boundaryWorldU, positiveSide, chartEntryRadius, chartExitRadius)) {
                    stats.recordSideWindowAttempt(internalSide);
                    stats.recordSideNotRenderable(internalSide);
                    if (collectAllocations && internalSide) {
                        allocations.add(sideAllocation(
                                targetPos,
                                sideFace,
                                "u-side",
                                true,
                                0.0D,
                                0.0D,
                                0.0D,
                                0.0D,
                                0.0D,
                                0,
                                "not_renderable",
                                sideIncidenceDetail(
                                        candidateDetail,
                                        ownerBoxIndex,
                                        entryTravel,
                                        exitTravel,
                                        fixedULocal,
                                        crossMinLocal,
                                        crossMaxLocal,
                                        chartTravel,
                                        boundaryWorldU,
                                        positiveSide,
                                        chartEntryRadius,
                                        chartExitRadius
                                )
                        ));
                    }
                    continue;
                }

                long sameDepthSplitStartNanos = stats.startTimer();
                List<TravelInterval> occlusionTravels = splitSideTravelAtSameDepthOccludersIndexed(
                        chartTravel,
                        sameDepthSweeps,
                        sameDepthIndex,
                        targetPos,
                        ownerBoxIndex,
                        stats
                );
                stats.addSideSameDepthSplitNanos(sameDepthSplitStartNanos);

                for (TravelInterval occlusionTravel : occlusionTravels) {
                    long travelSubdivisionStartNanos = stats.startTimer();
                    int travelSteps = sideChartTravelSubdivisionCount(
                            radiusPropagation,
                            occlusionTravel.min(),
                            occlusionTravel.max()
                    );
                    stats.addSideTravelSplitNanos(travelSubdivisionStartNanos);

                    for (int travelIndex = 0; travelIndex < travelSteps; travelIndex++) {
                    stats.recordSideWindowAttempt(internalSide);
                    long sideWindowStartNanos = stats.startTimer();
                    double rawTravel0 = lerp(occlusionTravel.min(), occlusionTravel.max(), travelIndex / (double) travelSteps);
                    double rawTravel1 = lerp(occlusionTravel.min(), occlusionTravel.max(), (travelIndex + 1) / (double) travelSteps);
                    double crossTravel0 = nudgeUSideTravelEndpoint(radiusPropagation, rawTravel0, rawTravel1, boundaryWorldU, tileV, crossMinLocal, crossMaxLocal);
                    double crossTravel1 = nudgeUSideTravelEndpoint(radiusPropagation, rawTravel1, rawTravel0, boundaryWorldU, tileV, crossMinLocal, crossMaxLocal);

                    if (crossTravel1 - crossTravel0 <= EDGE_TOUCH_EPSILON) {
                        stats.recordSideDegenerateTravel(internalSide);
                        stats.addSideWindowNanos(sideWindowStartNanos);
                        continue;
                    }

                    double radius0 = radiusAt(radiusPropagation, crossTravel0);
                    double radius1 = radiusAt(radiusPropagation, crossTravel1);

                    SideCrossSection cross0 = uSideCrossSection(radius0, boundaryWorldU, tileV, crossMinLocal, crossMaxLocal);
                    SideCrossSection cross1 = uSideCrossSection(radius1, boundaryWorldU, tileV, crossMinLocal, crossMaxLocal);

                    if (cross0 == null || cross1 == null) {
                        stats.recordSideCrossNull(internalSide);
                        stats.addSideWindowNanos(sideWindowStartNanos);
                        continue;
                    }

                    CanonicalRect sideWindow = sideCanonicalWindow(
                            cross0.axisCanonical(),
                            cross0.axisCanonical(),
                            cross1.axisCanonical(),
                            cross1.axisCanonical(),
                            cross0.minCanonical(),
                            cross0.maxCanonical(),
                            cross1.minCanonical(),
                            cross1.maxCanonical()
                    );

                    if (sideWindow == null) {
                        stats.recordSideWindowNull(internalSide);
                        stats.addSideWindowNanos(sideWindowStartNanos);
                        continue;
                    }
                    stats.addSideWindowNanos(sideWindowStartNanos);

                    recordSideCandidate(legacySideCandidates, targetPos, sideFace);
                    stats.sideWindowCandidates++;
                    stats.recordSideWindowCandidate(internalSide);
                    long sideRemainingStartNanos = stats.startTimer();
                    double segmentPrefixTravel = crossTravel1;
                    long prefixQueryStartNanos = stats.startTimer();
                    List<OcclusionWindow> sameDepthClipOccluders = sameDepthOccludersBeforeIndexed(
                            sameDepthSweeps,
                            sameDepthIndex,
                            targetPos,
                            ownerBoxIndex,
                            segmentPrefixTravel,
                            sideWindow,
                            stats
                    );
                    stats.addSidePrefixQueryNanos(prefixQueryStartNanos);
                    List<CanonicalRect> sameDepthClipWindows = occlusionRects(sameDepthClipOccluders);
                    List<CanonicalRect> visibleSideWindows = visibleRayWindows(
                            sideWindow,
                            remainingRayWindows,
                            sameDepthClipWindows,
                            stats,
                            true
                    );
                    stats.addSideRemainingIntersectNanos(sideRemainingStartNanos);
                    double candidateArea = canonicalArea(sideWindow);
                    boolean collectDetailedSideGeometry = collectAllocations && internalSide;
                    long debugAuditStartNanos = collectDetailedSideGeometry ? stats.startTimer() : 0L;
                    String occlusionAudit = collectDetailedSideGeometry
                            ? sideOcclusionAudit(
                                    targetTemplate.envelope(),
                                    sameDepthCuboidProbes,
                                    sameDepthSweeps,
                                    targetPos,
                                    ownerBoxIndex,
                                    segmentPrefixTravel,
                                    sideWindow
                            )
                            : "";
                    stats.addSideDebugAuditNanos(debugAuditStartNanos);
                    String traceDetail = collectDetailedSideGeometry
                            ? sideTraceDetail(
                            candidateDetail,
                            ownerBoxIndex,
                            entryTravel,
                            exitTravel,
                            fixedULocal,
                            crossMinLocal,
                            crossMaxLocal,
                            occlusionTravel,
                            sideWindow,
                            sameDepthClipOccluders,
                            visibleSideWindows,
                            occlusionAudit
                    )
                            : "";

                    if (visibleSideWindows.isEmpty()) {
                        stats.recordSideVisibleEmpty(internalSide);
                        if (collectAllocations) {
                            allocations.add(sideAllocation(
                                     targetPos,
                                     sideFace,
                                     "u-side",
                                     internalSide,
                                     candidateArea,
                                    0.0D,
                                    0.0D,
                                    0.0D,
                                    0.0D,
                                    0,
                                    "occluded",
                                    traceDetail
                            ));
                        }
                        continue;
                    }

                    if (!addedDebugCenter) {
                        addDebugFaceCenter(targetPos, sideFace, fragments);
                        addedDebugCenter = true;
                    }

                    double travel0 = crossTravel0;
                    double travel1 = crossTravel1;

                    for (CanonicalRect visibleSideWindow : visibleSideWindows) {
                        double assignedArea = canonicalArea(visibleSideWindow);
                        double sideFraction = collectAllocations ? integratedFraction(visibleSideWindow) : 0.0D;
                        if (collectAllocations) {
                            stats.footprintIntegralCalls++;
                        }

                        if (sidePower <= MIN_FRAGMENT_POWER) {
                            stats.recordSideLowPower(internalSide);
                            if (collectAllocations) {
                                allocations.add(sideAllocation(
                                         targetPos,
                                         sideFace,
                                         "u-side",
                                         internalSide,
                                         candidateArea,
                                        assignedArea,
                                        0.0D,
                                        sideFraction,
                                        0.0D,
                                        0,
                                        "low_power",
                                        traceDetail + ";visible_window=" + formatRect(visibleSideWindow)
                                ));
                            }
                            continue;
                        }

                        long sidePatchStartNanos = stats.startTimer();
                        if (candidateSurfaceSpot == null) {
                            candidateSurfaceSpot = cachedSurfaceSpot(
                                    appearanceCache,
                                    level,
                                    targetPos,
                                    sideFace,
                                    targetState,
                                    targetTemplate,
                                    sidePower,
                                    sideCoherentPower,
                                    stats
                            );
                        }
                        PatchEmissionReport emission = addUSideChartSpot(
                                candidateSurfaceSpot,
                                sideFace,
                                travelDirection,
                                uDirection,
                                vDirection,
                                fixedULocal,
                                travel0,
                                travel1,
                                cross0,
                                cross1,
                                sideWindow,
                                visibleSideWindow,
                                collectDetailedSideGeometry,
                                stats,
                                fragments
                        );
                        stats.addSidePatchEmitNanos(sidePatchStartNanos);
                        stats.recordSidePatchEmission(internalSide, emission);

                        if (collectAllocations) {
                            allocations.add(sideAllocation(
                                     targetPos,
                                     sideFace,
                                     "u-side",
                                     internalSide,
                                     candidateArea,
                                    assignedArea,
                                    emission.emitted() ? assignedArea : 0.0D,
                                    sideFraction,
                                    emission.emitted() ? sideFraction : 0.0D,
                                    emission.emittedQuads(),
                                    emission.resultName(),
                                    traceDetail + ";visible_window=" + formatRect(visibleSideWindow) + ";emit=" + emission.detail()
                            ));
                        }
                    }
                }
                }
            }
        }
    }

    private static void addVSideVolumeQuads(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            int ownerBoxIndex,
            Direction sideFace,
            boolean positiveSide,
            String candidateDetail,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double entryTravel,
            double exitTravel,
            double fixedVLocal,
            int tileU,
            double crossMinLocal,
            double crossMaxLocal,
            double boundaryWorldV,
            List<TravelInterval> visibleTravels,
            BeamPacket targetTemplate,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            CanonicalRegion remainingRayWindows,
            List<CuboidSweep> sameDepthSweeps,
            SameDepthOcclusionIndex sameDepthIndex,
            List<DepthCuboidProbe> sameDepthCuboidProbes,
            boolean collectAllocations,
            ProjectionStatsBuilder stats,
            Map<SurfaceSpotKey, SpotRecord> appearanceCache,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments,
            Set<SideCandidateKey> legacySideCandidates
    ) {
        if (visibleTravels.isEmpty()) {
            return;
        }

        boolean addedDebugCenter = false;
        boolean internalSide = isInternalProjectionSide(fixedVLocal);
        double sidePower = visualSidePower(beamPower, visualDistanceFactor);
        double sideCoherentPower = visualSidePower(coherentBeamPower, visualDistanceFactor);
        SpotRecord candidateSurfaceSpot = null;

        for (TravelInterval visibleTravel : visibleTravels) {
            long travelSplitStartNanos = stats.startTimer();
            List<TravelInterval> chartTravels = splitSideTravelAtCrossBoundaries(
                    radiusPropagation,
                    visibleTravel,
                    tileU - 0.5D + crossMinLocal,
                    tileU - 0.5D + crossMaxLocal
            );
            stats.addSideTravelSplitNanos(travelSplitStartNanos);

            for (TravelInterval chartTravel : chartTravels) {
                double chartEntryRadius = radiusAt(radiusPropagation, chartTravel.min());
                double chartExitRadius = radiusAt(radiusPropagation, chartTravel.max());
                if (!isRenderableSide(boundaryWorldV, positiveSide, chartEntryRadius, chartExitRadius)) {
                    stats.recordSideWindowAttempt(internalSide);
                    stats.recordSideNotRenderable(internalSide);
                    if (collectAllocations && internalSide) {
                        allocations.add(sideAllocation(
                                targetPos,
                                sideFace,
                                "v-side",
                                true,
                                0.0D,
                                0.0D,
                                0.0D,
                                0.0D,
                                0.0D,
                                0,
                                "not_renderable",
                                sideIncidenceDetail(
                                        candidateDetail,
                                        ownerBoxIndex,
                                        entryTravel,
                                        exitTravel,
                                        fixedVLocal,
                                        crossMinLocal,
                                        crossMaxLocal,
                                        chartTravel,
                                        boundaryWorldV,
                                        positiveSide,
                                        chartEntryRadius,
                                        chartExitRadius
                                )
                        ));
                    }
                    continue;
                }

                long sameDepthSplitStartNanos = stats.startTimer();
                List<TravelInterval> occlusionTravels = splitSideTravelAtSameDepthOccludersIndexed(
                        chartTravel,
                        sameDepthSweeps,
                        sameDepthIndex,
                        targetPos,
                        ownerBoxIndex,
                        stats
                );
                stats.addSideSameDepthSplitNanos(sameDepthSplitStartNanos);

                for (TravelInterval occlusionTravel : occlusionTravels) {
                    long travelSubdivisionStartNanos = stats.startTimer();
                    int travelSteps = sideChartTravelSubdivisionCount(
                            radiusPropagation,
                            occlusionTravel.min(),
                            occlusionTravel.max()
                    );
                    stats.addSideTravelSplitNanos(travelSubdivisionStartNanos);

                    for (int travelIndex = 0; travelIndex < travelSteps; travelIndex++) {
                    stats.recordSideWindowAttempt(internalSide);
                    long sideWindowStartNanos = stats.startTimer();
                    double rawTravel0 = lerp(occlusionTravel.min(), occlusionTravel.max(), travelIndex / (double) travelSteps);
                    double rawTravel1 = lerp(occlusionTravel.min(), occlusionTravel.max(), (travelIndex + 1) / (double) travelSteps);
                    double crossTravel0 = nudgeVSideTravelEndpoint(radiusPropagation, rawTravel0, rawTravel1, boundaryWorldV, tileU, crossMinLocal, crossMaxLocal);
                    double crossTravel1 = nudgeVSideTravelEndpoint(radiusPropagation, rawTravel1, rawTravel0, boundaryWorldV, tileU, crossMinLocal, crossMaxLocal);

                    if (crossTravel1 - crossTravel0 <= EDGE_TOUCH_EPSILON) {
                        stats.recordSideDegenerateTravel(internalSide);
                        stats.addSideWindowNanos(sideWindowStartNanos);
                        continue;
                    }

                    double radius0 = radiusAt(radiusPropagation, crossTravel0);
                    double radius1 = radiusAt(radiusPropagation, crossTravel1);

                    SideCrossSection cross0 = vSideCrossSection(radius0, boundaryWorldV, tileU, crossMinLocal, crossMaxLocal);
                    SideCrossSection cross1 = vSideCrossSection(radius1, boundaryWorldV, tileU, crossMinLocal, crossMaxLocal);

                    if (cross0 == null || cross1 == null) {
                        stats.recordSideCrossNull(internalSide);
                        stats.addSideWindowNanos(sideWindowStartNanos);
                        continue;
                    }

                    CanonicalRect sideWindow = sideCanonicalWindow(
                            cross0.minCanonical(),
                            cross0.maxCanonical(),
                            cross1.minCanonical(),
                            cross1.maxCanonical(),
                            cross0.axisCanonical(),
                            cross0.axisCanonical(),
                            cross1.axisCanonical(),
                            cross1.axisCanonical()
                    );

                    if (sideWindow == null) {
                        stats.recordSideWindowNull(internalSide);
                        stats.addSideWindowNanos(sideWindowStartNanos);
                        continue;
                    }
                    stats.addSideWindowNanos(sideWindowStartNanos);

                    recordSideCandidate(legacySideCandidates, targetPos, sideFace);
                    stats.sideWindowCandidates++;
                    stats.recordSideWindowCandidate(internalSide);
                    long sideRemainingStartNanos = stats.startTimer();
                    double segmentPrefixTravel = crossTravel1;
                    long prefixQueryStartNanos = stats.startTimer();
                    List<OcclusionWindow> sameDepthClipOccluders = sameDepthOccludersBeforeIndexed(
                            sameDepthSweeps,
                            sameDepthIndex,
                            targetPos,
                            ownerBoxIndex,
                            segmentPrefixTravel,
                            sideWindow,
                            stats
                    );
                    stats.addSidePrefixQueryNanos(prefixQueryStartNanos);
                    List<CanonicalRect> sameDepthClipWindows = occlusionRects(sameDepthClipOccluders);
                    List<CanonicalRect> visibleSideWindows = visibleRayWindows(
                            sideWindow,
                            remainingRayWindows,
                            sameDepthClipWindows,
                            stats,
                            true
                    );
                    stats.addSideRemainingIntersectNanos(sideRemainingStartNanos);
                    double candidateArea = canonicalArea(sideWindow);
                    boolean collectDetailedSideGeometry = collectAllocations && internalSide;
                    long debugAuditStartNanos = collectDetailedSideGeometry ? stats.startTimer() : 0L;
                    String occlusionAudit = collectDetailedSideGeometry
                            ? sideOcclusionAudit(
                                    targetTemplate.envelope(),
                                    sameDepthCuboidProbes,
                                    sameDepthSweeps,
                                    targetPos,
                                    ownerBoxIndex,
                                    segmentPrefixTravel,
                                    sideWindow
                            )
                            : "";
                    stats.addSideDebugAuditNanos(debugAuditStartNanos);
                    String traceDetail = collectDetailedSideGeometry
                            ? sideTraceDetail(
                            candidateDetail,
                            ownerBoxIndex,
                            entryTravel,
                            exitTravel,
                            fixedVLocal,
                            crossMinLocal,
                            crossMaxLocal,
                            occlusionTravel,
                            sideWindow,
                            sameDepthClipOccluders,
                            visibleSideWindows,
                            occlusionAudit
                    )
                            : "";

                    if (visibleSideWindows.isEmpty()) {
                        stats.recordSideVisibleEmpty(internalSide);
                        if (collectAllocations) {
                            allocations.add(sideAllocation(
                                     targetPos,
                                     sideFace,
                                     "v-side",
                                     internalSide,
                                     candidateArea,
                                    0.0D,
                                    0.0D,
                                    0.0D,
                                    0.0D,
                                    0,
                                    "occluded",
                                    traceDetail
                            ));
                        }
                        continue;
                    }

                    if (!addedDebugCenter) {
                        addDebugFaceCenter(targetPos, sideFace, fragments);
                        addedDebugCenter = true;
                    }

                    double travel0 = crossTravel0;
                    double travel1 = crossTravel1;

                    for (CanonicalRect visibleSideWindow : visibleSideWindows) {
                        double assignedArea = canonicalArea(visibleSideWindow);
                        double sideFraction = collectAllocations ? integratedFraction(visibleSideWindow) : 0.0D;
                        if (collectAllocations) {
                            stats.footprintIntegralCalls++;
                        }

                        if (sidePower <= MIN_FRAGMENT_POWER) {
                            stats.recordSideLowPower(internalSide);
                            if (collectAllocations) {
                                allocations.add(sideAllocation(
                                         targetPos,
                                         sideFace,
                                         "v-side",
                                         internalSide,
                                         candidateArea,
                                        assignedArea,
                                        0.0D,
                                        sideFraction,
                                        0.0D,
                                        0,
                                        "low_power",
                                        traceDetail + ";visible_window=" + formatRect(visibleSideWindow)
                                ));
                            }
                            continue;
                        }

                        long sidePatchStartNanos = stats.startTimer();
                        if (candidateSurfaceSpot == null) {
                            candidateSurfaceSpot = cachedSurfaceSpot(
                                    appearanceCache,
                                    level,
                                    targetPos,
                                    sideFace,
                                    targetState,
                                    targetTemplate,
                                    sidePower,
                                    sideCoherentPower,
                                    stats
                            );
                        }
                        PatchEmissionReport emission = addVSideChartSpot(
                                candidateSurfaceSpot,
                                sideFace,
                                travelDirection,
                                uDirection,
                                vDirection,
                                fixedVLocal,
                                travel0,
                                travel1,
                                cross0,
                                cross1,
                                sideWindow,
                                visibleSideWindow,
                                collectDetailedSideGeometry,
                                stats,
                                fragments
                        );
                        stats.addSidePatchEmitNanos(sidePatchStartNanos);
                        stats.recordSidePatchEmission(internalSide, emission);

                        if (collectAllocations) {
                            allocations.add(sideAllocation(
                                     targetPos,
                                     sideFace,
                                     "v-side",
                                     internalSide,
                                     candidateArea,
                                    assignedArea,
                                    emission.emitted() ? assignedArea : 0.0D,
                                    sideFraction,
                                    emission.emitted() ? sideFraction : 0.0D,
                                    emission.emittedQuads(),
                                    emission.resultName(),
                                    traceDetail + ";visible_window=" + formatRect(visibleSideWindow) + ";emit=" + emission.detail()
                            ));
                        }
                    }
                }
                }
            }
        }
    }

    private static CanonicalRect canonicalRectOrNull(
            double minU,
            double minV,
            double maxU,
            double maxV
    ) {
        minU = clamp01(minU);
        minV = clamp01(minV);
        maxU = clamp01(maxU);
        maxV = clamp01(maxV);

        if (maxU - minU <= EDGE_TOUCH_EPSILON || maxV - minV <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        return new CanonicalRect(minU, minV, maxU, maxV);
    }

    static CanonicalRect sideCanonicalWindow(
            double minU0,
            double maxU0,
            double minU1,
            double maxU1,
            double minV0,
            double maxV0,
            double minV1,
            double maxV1
    ) {
        return canonicalRectOrNull(
                Math.min(minU0, minU1),
                Math.min(minV0, minV1),
                Math.max(maxU0, maxU1),
                Math.max(maxV0, maxV1)
        );
    }

    private static TravelInterval clipTravelByRadius(
            BeamEnvelope envelope,
            double t0,
            double t1,
            double requiredRadius
    ) {
        if (!Double.isFinite(requiredRadius) || requiredRadius < 0.0D) {
            return null;
        }

        if (t1 - t0 <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double previousT = t0;
        double previousRadius = radiusAt(envelope, previousT);

        if (!Double.isFinite(previousRadius) || previousRadius <= 0.0D) {
            return null;
        }

        boolean previousInside = previousRadius + EDGE_TOUCH_EPSILON >= requiredRadius;
        double firstInside = previousInside ? t0 : Double.NaN;
        double lastInside = previousInside ? t0 : Double.NaN;

        for (int sample = 1; sample <= CONE_TRAVEL_SAMPLES; sample++) {
            double currentT = lerp(t0, t1, sample / (double) CONE_TRAVEL_SAMPLES);
            double currentRadius = radiusAt(envelope, currentT);

            if (!Double.isFinite(currentRadius) || currentRadius <= 0.0D) {
                previousT = currentT;
                previousRadius = currentRadius;
                previousInside = false;
                continue;
            }

            boolean currentInside = currentRadius + EDGE_TOUCH_EPSILON >= requiredRadius;

            if (previousInside != currentInside) {
                double crossing = radiusCrossing(previousT, currentT, previousRadius, currentRadius, requiredRadius);

                if (currentInside) {
                    firstInside = Double.isNaN(firstInside) ? crossing : Math.min(firstInside, crossing);
                    lastInside = Math.max(lastInsideOr(firstInside, lastInside), currentT);
                } else if (!Double.isNaN(firstInside)) {
                    lastInside = Math.max(lastInsideOr(firstInside, lastInside), crossing);
                }
            } else if (currentInside) {
                if (Double.isNaN(firstInside)) {
                    firstInside = currentT;
                }

                lastInside = currentT;
            }

            previousT = currentT;
            previousRadius = currentRadius;
            previousInside = currentInside;
        }

        if (Double.isNaN(firstInside) || Double.isNaN(lastInside) || lastInside - firstInside <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        return new TravelInterval(firstInside, lastInside);
    }

    private static List<TravelInterval> uSideTravelIntervals(
            BeamEnvelope envelope,
            double boundaryWorldU,
            int tileV
    ) {
        return uSideTravelIntervals(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                boundaryWorldU,
                tileV,
                0.0D,
                1.0D,
                0.0D,
                1.0D
        );
    }

    private static List<TravelInterval> uSideTravelIntervals(
            BeamEnvelope envelope,
            double boundaryWorldU,
            int tileV,
            double minLocalV,
            double maxLocalV,
            double minTravel,
            double maxTravel
    ) {
        return uSideTravelIntervals(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                boundaryWorldU,
                tileV,
                minLocalV,
                maxLocalV,
                minTravel,
                maxTravel
        );
    }

    private static List<TravelInterval> uSideTravelIntervals(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double boundaryWorldU,
            int tileV,
            double minLocalV,
            double maxLocalV,
            double minTravel,
            double maxTravel
    ) {
        return sideTravelIntervals(
                radiusPropagation,
                true,
                boundaryWorldU,
                tileV,
                minLocalV,
                maxLocalV,
                minTravel,
                maxTravel
        );
    }

    private static List<TravelInterval> vSideTravelIntervals(
            BeamEnvelope envelope,
            double boundaryWorldV,
            int tileU
    ) {
        return vSideTravelIntervals(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                boundaryWorldV,
                tileU,
                0.0D,
                1.0D,
                0.0D,
                1.0D
        );
    }

    private static List<TravelInterval> vSideTravelIntervals(
            BeamEnvelope envelope,
            double boundaryWorldV,
            int tileU,
            double minLocalU,
            double maxLocalU,
            double minTravel,
            double maxTravel
    ) {
        return vSideTravelIntervals(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                boundaryWorldV,
                tileU,
                minLocalU,
                maxLocalU,
                minTravel,
                maxTravel
        );
    }

    private static List<TravelInterval> vSideTravelIntervals(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double boundaryWorldV,
            int tileU,
            double minLocalU,
            double maxLocalU,
            double minTravel,
            double maxTravel
    ) {
        return sideTravelIntervals(
                radiusPropagation,
                false,
                boundaryWorldV,
                tileU,
                minLocalU,
                maxLocalU,
                minTravel,
                maxTravel
        );
    }

    private static List<TravelInterval> sideTravelIntervals(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            boolean uSide,
            double boundaryWorld,
            int crossTile,
            double minLocalCross,
            double maxLocalCross,
            double minTravel,
            double maxTravel
    ) {
        List<TravelInterval> intervals = new ArrayList<>();
        if (maxTravel - minTravel <= EDGE_TOUCH_EPSILON) {
            return intervals;
        }

        double previousT = minTravel;
        boolean previousInside = sideIntersectsAt(
                radiusPropagation, uSide, boundaryWorld, crossTile, minLocalCross, maxLocalCross, previousT
        );
        double start = previousInside ? previousT : Double.NaN;

        for (int sample = 1; sample <= CONE_TRAVEL_SAMPLES; sample++) {
            double currentT = lerp(minTravel, maxTravel, sample / (double) CONE_TRAVEL_SAMPLES);
            boolean currentInside = sideIntersectsAt(
                    radiusPropagation, uSide, boundaryWorld, crossTile, minLocalCross, maxLocalCross, currentT
            );

            if (previousInside != currentInside) {
                double boundary = sideIntersectionBoundary(
                        radiusPropagation,
                        uSide,
                        boundaryWorld,
                        crossTile,
                        minLocalCross,
                        maxLocalCross,
                        previousT,
                        currentT,
                        previousInside
                );

                if (currentInside) {
                    start = boundary;
                } else if (!Double.isNaN(start)) {
                    addTravelInterval(intervals, start, boundary);
                    start = Double.NaN;
                }
            } else if (currentInside && Double.isNaN(start)) {
                start = previousT;
            }

            previousT = currentT;
            previousInside = currentInside;
        }

        if (previousInside && !Double.isNaN(start)) {
            addTravelInterval(intervals, start, maxTravel);
        }

        return intervals;
    }

    private static void addTravelInterval(List<TravelInterval> intervals, double min, double max) {
        if (max - min > EDGE_TOUCH_EPSILON) {
            intervals.add(new TravelInterval(min, max));
        }
    }

    private static List<TravelInterval> splitSideTravelAtCrossBoundaries(
            BeamEnvelope envelope,
            TravelInterval interval,
            double firstBoundary,
            double secondBoundary
    ) {
        return splitSideTravelAtCrossBoundaries(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                interval,
                firstBoundary,
                secondBoundary
        );
    }

    private static List<TravelInterval> splitSideTravelAtCrossBoundaries(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            TravelInterval interval,
            double firstBoundary,
            double secondBoundary
    ) {
        List<Double> cuts = new ArrayList<>();
        cuts.add(interval.min());
        cuts.add(interval.max());
        addBeamWaistCut(cuts, radiusPropagation, interval);
        addRadiusBoundaryCuts(cuts, radiusPropagation, interval, Math.abs(firstBoundary));
        addRadiusBoundaryCuts(cuts, radiusPropagation, interval, Math.abs(secondBoundary));
        cuts.sort(Double::compare);

        List<TravelInterval> intervals = new ArrayList<>();
        double previous = cuts.get(0);

        for (int index = 1; index < cuts.size(); index++) {
            double current = cuts.get(index);

            if (current - previous <= EDGE_TOUCH_EPSILON) {
                continue;
            }

            addTravelInterval(intervals, previous, current);
            previous = current;
        }

        return intervals.isEmpty() ? List.of(interval) : intervals;
    }

    private static void addBeamWaistCut(
            List<Double> cuts,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            TravelInterval interval
    ) {
        double waistTravel = radiusPropagation.waistTravel();
        if (Double.isFinite(waistTravel)
                && waistTravel > interval.min() + EDGE_TOUCH_EPSILON
                && waistTravel < interval.max() - EDGE_TOUCH_EPSILON) {
            cuts.add(waistTravel);
        }
    }

    private static void addRadiusBoundaryCuts(
            List<Double> cuts,
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            TravelInterval interval,
            double requiredRadius
    ) {
        if (!Double.isFinite(requiredRadius) || requiredRadius <= EDGE_TOUCH_EPSILON) {
            return;
        }

        double previousT = interval.min();
        boolean previousInside = radiusAt(radiusPropagation, previousT) + EDGE_TOUCH_EPSILON >= requiredRadius;

        for (int sample = 1; sample <= CONE_TRAVEL_SAMPLES; sample++) {
            double currentT = lerp(interval.min(), interval.max(), sample / (double) CONE_TRAVEL_SAMPLES);
            boolean currentInside = radiusAt(radiusPropagation, currentT) + EDGE_TOUCH_EPSILON >= requiredRadius;

            if (previousInside != currentInside) {
                double crossing = radiusThresholdBoundary(
                        radiusPropagation, previousT, currentT, previousInside, requiredRadius
                );

                if (crossing > interval.min() + EDGE_TOUCH_EPSILON && crossing < interval.max() - EDGE_TOUCH_EPSILON) {
                    cuts.add(crossing);
                }
            }

            previousT = currentT;
            previousInside = currentInside;
        }
    }

    private static double radiusThresholdBoundary(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double t0,
            double t1,
            boolean insideAtT0,
            double requiredRadius
    ) {
        double valid = insideAtT0 ? t0 : t1;
        double invalid = insideAtT0 ? t1 : t0;

        for (int iteration = 0; iteration < 24; iteration++) {
            double mid = (valid + invalid) * 0.5D;

            if (radiusAt(radiusPropagation, mid) + EDGE_TOUCH_EPSILON >= requiredRadius) {
                valid = mid;
            } else {
                invalid = mid;
            }
        }

        return valid;
    }

    private static double radiusAt(BeamEnvelope envelope, double travel) {
        return radiusAt(BeamGeometryOps.prepareRadiusPropagation(envelope), travel);
    }

    private static double radiusAt(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double travel
    ) {
        double radius = radiusPropagation.radiusAt(Math.max(0.0D, travel));
        return Double.isFinite(radius) ? radius : 0.0D;
    }

    private static double sideIntersectionBoundary(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            boolean uSide,
            double boundaryWorld,
            int crossTile,
            double minLocalCross,
            double maxLocalCross,
            double t0,
            double t1,
            boolean insideAtT0
    ) {
        double valid = insideAtT0 ? t0 : t1;
        double invalid = insideAtT0 ? t1 : t0;

        for (int iteration = 0; iteration < 24; iteration++) {
            double mid = (valid + invalid) * 0.5D;

            if (sideIntersectsAt(
                    radiusPropagation, uSide, boundaryWorld, crossTile, minLocalCross, maxLocalCross, mid
            )) {
                valid = mid;
            } else {
                invalid = mid;
            }
        }

        return valid;
    }

    private static double nudgeUSideTravelEndpoint(
            BeamEnvelope envelope,
            double endpoint,
            double toward,
            double boundaryWorldU,
            int tileV
    ) {
        return nudgeUSideTravelEndpoint(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                endpoint,
                toward,
                boundaryWorldU,
                tileV,
                0.0D,
                1.0D
        );
    }

    private static double nudgeUSideTravelEndpoint(
            BeamEnvelope envelope,
            double endpoint,
            double toward,
            double boundaryWorldU,
            int tileV,
            double minLocalV,
            double maxLocalV
    ) {
        return nudgeUSideTravelEndpoint(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                endpoint,
                toward,
                boundaryWorldU,
                tileV,
                minLocalV,
                maxLocalV
        );
    }

    private static double nudgeUSideTravelEndpoint(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double endpoint,
            double toward,
            double boundaryWorldU,
            int tileV,
            double minLocalV,
            double maxLocalV
    ) {
        return nudgeSideTravelEndpoint(
                radiusPropagation,
                true,
                boundaryWorldU,
                tileV,
                minLocalV,
                maxLocalV,
                endpoint,
                toward
        );
    }

    private static double nudgeVSideTravelEndpoint(
            BeamEnvelope envelope,
            double endpoint,
            double toward,
            double boundaryWorldV,
            int tileU
    ) {
        return nudgeVSideTravelEndpoint(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                endpoint,
                toward,
                boundaryWorldV,
                tileU,
                0.0D,
                1.0D
        );
    }

    private static double nudgeVSideTravelEndpoint(
            BeamEnvelope envelope,
            double endpoint,
            double toward,
            double boundaryWorldV,
            int tileU,
            double minLocalU,
            double maxLocalU
    ) {
        return nudgeVSideTravelEndpoint(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                endpoint,
                toward,
                boundaryWorldV,
                tileU,
                minLocalU,
                maxLocalU
        );
    }

    private static double nudgeVSideTravelEndpoint(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double endpoint,
            double toward,
            double boundaryWorldV,
            int tileU,
            double minLocalU,
            double maxLocalU
    ) {
        return nudgeSideTravelEndpoint(
                radiusPropagation,
                false,
                boundaryWorldV,
                tileU,
                minLocalU,
                maxLocalU,
                endpoint,
                toward
        );
    }

    private static double nudgeSideTravelEndpoint(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            boolean uSide,
            double boundaryWorld,
            int crossTile,
            double minLocalCross,
            double maxLocalCross,
            double endpoint,
            double toward
    ) {
        if (sideIntersectsAt(
                radiusPropagation, uSide, boundaryWorld, crossTile, minLocalCross, maxLocalCross, endpoint
        )) {
            return endpoint;
        }

        if (Math.abs(toward - endpoint) <= EDGE_TOUCH_EPSILON
                || !sideIntersectsAt(
                        radiusPropagation, uSide, boundaryWorld, crossTile, minLocalCross, maxLocalCross, toward
                )) {
            return endpoint;
        }

        double invalid = endpoint;
        double valid = toward;

        for (int iteration = 0; iteration < 24; iteration++) {
            double mid = (invalid + valid) * 0.5D;

            if (sideIntersectsAt(
                    radiusPropagation, uSide, boundaryWorld, crossTile, minLocalCross, maxLocalCross, mid
            )) {
                valid = mid;
            } else {
                invalid = mid;
            }
        }

        return valid;
    }

    private static boolean sideIntersectsAt(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            boolean uSide,
            double boundaryWorld,
            int crossTile,
            double minLocalCross,
            double maxLocalCross,
            double travel
    ) {
        double radius = radiusAt(radiusPropagation, travel);
        return uSide
                ? uSideCrossSection(radius, boundaryWorld, crossTile, minLocalCross, maxLocalCross) != null
                : vSideCrossSection(radius, boundaryWorld, crossTile, minLocalCross, maxLocalCross) != null;
    }

    private static double radiusCrossing(
            double t0,
            double t1,
            double radius0,
            double radius1,
            double requiredRadius
    ) {
        double radiusDelta = radius1 - radius0;

        if (Math.abs(radiusDelta) <= CONE_SIDE_EPSILON) {
            return t0;
        }

        return lerp(t0, t1, clamp01((requiredRadius - radius0) / radiusDelta));
    }

    private static double lastInsideOr(double firstInside, double lastInside) {
        return Double.isNaN(lastInside) ? firstInside : lastInside;
    }

    private static double nudgeVisibleTravelEndpoint(
            BeamEnvelope envelope,
            double endpoint,
            double toward,
            double requiredRadius
    ) {
        double radius = radiusAt(envelope, endpoint);
        if (Double.isFinite(radius) && radius > requiredRadius + EDGE_TOUCH_EPSILON) {
            return endpoint;
        }

        double span = Math.abs(toward - endpoint);
        if (span <= EDGE_TOUCH_EPSILON) {
            return endpoint;
        }

        double direction = Math.signum(toward - endpoint);
        double nudge = Math.min(span * 0.01D, EDGE_TOUCH_EPSILON);
        return clamp01(endpoint + direction * nudge);
    }

    static SideCrossSection uSideCrossSection(double radius, double boundaryWorldU, int tileV) {
        return uSideCrossSection(radius, boundaryWorldU, tileV, 0.0D, 1.0D);
    }

    static SideCrossSection uSideCrossSection(
            double radius,
            double boundaryWorldU,
            int tileV,
            double minLocalV,
            double maxLocalV
    ) {
        if (!Double.isFinite(radius) || radius <= 0.0D || Math.abs(boundaryWorldU) > radius + EDGE_TOUCH_EPSILON) {
            return null;
        }

        double tileMinV = tileV - 0.5D;
        double boxMinV = tileMinV + minLocalV;
        double boxMaxV = tileMinV + maxLocalV;
        double hitMinV = Math.max(boxMinV, -radius);
        double hitMaxV = Math.min(boxMaxV, radius);

        if (hitMaxV - hitMinV <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        return new SideCrossSection(
                clamp01(hitMinV - tileMinV),
                clamp01(hitMaxV - tileMinV),
                clamp01(canonicalAtWorldCoordinate(radius, boundaryWorldU)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMinV)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMaxV))
        );
    }

    static SideCrossSection vSideCrossSection(double radius, double boundaryWorldV, int tileU) {
        return vSideCrossSection(radius, boundaryWorldV, tileU, 0.0D, 1.0D);
    }

    static SideCrossSection vSideCrossSection(
            double radius,
            double boundaryWorldV,
            int tileU,
            double minLocalU,
            double maxLocalU
    ) {
        if (!Double.isFinite(radius) || radius <= 0.0D || Math.abs(boundaryWorldV) > radius + EDGE_TOUCH_EPSILON) {
            return null;
        }

        double tileMinU = tileU - 0.5D;
        double boxMinU = tileMinU + minLocalU;
        double boxMaxU = tileMinU + maxLocalU;
        double hitMinU = Math.max(boxMinU, -radius);
        double hitMaxU = Math.min(boxMaxU, radius);

        if (hitMaxU - hitMinU <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        return new SideCrossSection(
                clamp01(hitMinU - tileMinU),
                clamp01(hitMaxU - tileMinU),
                clamp01(canonicalAtWorldCoordinate(radius, boundaryWorldV)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMinU)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMaxU))
        );
    }

    private static PatchEmissionReport addUSideChartSpot(
            SpotRecord surfaceSpot,
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double fixedULocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect sideWindow,
            CanonicalRect visibleWindow,
            boolean collectDetail,
            ProjectionStatsBuilder stats,
            List<SpotRecord> fragments
    ) {
        PatchEmission result = PatchEmission.PATCH_NULL;
        int emittedQuads = 0;
        boolean internalSide = isInternalProjectionSide(fixedULocal);

        if (wouldUseFullTravelUSidePatch(cross0, cross1, visibleWindow)) {
            stats.recordSideFastPathSkipped();
        }

        long patchClipStartNanos = stats.startTimer();
        List<Patch> patches = clippedUSidePatches(
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                fixedULocal,
                travel0,
                travel1,
                cross0,
                cross1,
                visibleWindow
        );
        stats.addPatchClipNanos(patchClipStartNanos);

        for (Patch patch : patches) {
            long spotRecordPackStartNanos = stats.startTimer();
            PatchEmission emission = addPatchSpot(
                    surfaceSpot,
                    patch,
                    fragments
            );
            stats.addSpotRecordPackNanos(spotRecordPackStartNanos);

            if (emission.emitted()) {
                emittedQuads++;
                stats.recordSidePatchGeometry(internalSide, patch);
            }

            result = emission;
        }

        String detail = collectDetail
                ? sidePatchDetail("u-side", sideWindow, visibleWindow, travel0, travel1, cross0, cross1, patches)
                : "";
        return new PatchEmissionReport(
                emittedQuads > 0 ? PatchEmission.EMITTED : result,
                emittedQuads,
                detail
        );
    }

    private static PatchEmissionReport addVSideChartSpot(
            SpotRecord surfaceSpot,
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double fixedVLocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect sideWindow,
            CanonicalRect visibleWindow,
            boolean collectDetail,
            ProjectionStatsBuilder stats,
            List<SpotRecord> fragments
    ) {
        PatchEmission result = PatchEmission.PATCH_NULL;
        int emittedQuads = 0;
        boolean internalSide = isInternalProjectionSide(fixedVLocal);

        if (wouldUseFullTravelVSidePatch(cross0, cross1, visibleWindow)) {
            stats.recordSideFastPathSkipped();
        }

        long patchClipStartNanos = stats.startTimer();
        List<Patch> patches = clippedVSidePatches(
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                fixedVLocal,
                travel0,
                travel1,
                cross0,
                cross1,
                visibleWindow
        );
        stats.addPatchClipNanos(patchClipStartNanos);

        for (Patch patch : patches) {
            long spotRecordPackStartNanos = stats.startTimer();
            PatchEmission emission = addPatchSpot(
                    surfaceSpot,
                    patch,
                    fragments
            );
            stats.addSpotRecordPackNanos(spotRecordPackStartNanos);

            if (emission.emitted()) {
                emittedQuads++;
                stats.recordSidePatchGeometry(internalSide, patch);
            }

            result = emission;
        }

        String detail = collectDetail
                ? sidePatchDetail("v-side", sideWindow, visibleWindow, travel0, travel1, cross0, cross1, patches)
                : "";
        return new PatchEmissionReport(
                emittedQuads > 0 ? PatchEmission.EMITTED : result,
                emittedQuads,
                detail
        );
    }

    private static List<CanonicalRect> splitUSideWindow(
            CanonicalRect window,
            BeamEnvelope envelope,
            double travel0,
            double travel1
    ) {
        return splitSideWindow(window, true, envelope, travel0, travel1);
    }

    private static List<CanonicalRect> splitVSideWindow(
            CanonicalRect window,
            BeamEnvelope envelope,
            double travel0,
            double travel1
    ) {
        return splitSideWindow(window, false, envelope, travel0, travel1);
    }

    private static List<CanonicalRect> splitSideWindow(
            CanonicalRect window,
            boolean travelAxisIsU,
            BeamEnvelope envelope,
            double travel0,
            double travel1
    ) {
        int travelSteps = adaptiveSideTravelSubdivisionCount(envelope, travel0, travel1);

        if (travelSteps <= 1) {
            return List.of(window);
        }

        List<CanonicalRect> windows = new ArrayList<>(travelSteps);

        for (int travelIndex = 0; travelIndex < travelSteps; travelIndex++) {
            double travelMin = travelIndex / (double) travelSteps;
            double travelMax = (travelIndex + 1) / (double) travelSteps;

            if (travelAxisIsU) {
                addCanonicalRect(
                        windows,
                        lerp(window.minU(), window.maxU(), travelMin),
                        window.minV(),
                        lerp(window.minU(), window.maxU(), travelMax),
                        window.maxV()
                );
            } else {
                addCanonicalRect(
                        windows,
                        window.minU(),
                        lerp(window.minV(), window.maxV(), travelMin),
                        window.maxU(),
                        lerp(window.minV(), window.maxV(), travelMax)
                );
            }
        }

        return windows;
    }

    private static int adaptiveSideTravelSubdivisionCount(
            BeamEnvelope envelope,
            double travel0,
            double travel1
    ) {
        if (envelope == null) {
            return 1;
        }
        return adaptiveSideTravelSubdivisionCount(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                travel0,
                travel1
        );
    }

    private static int adaptiveSideTravelSubdivisionCount(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double travel0,
            double travel1
    ) {
        if (travel1 - travel0 <= EDGE_TOUCH_EPSILON) {
            return 1;
        }

        double minRadius = Double.POSITIVE_INFINITY;
        double maxRadius = 0.0D;

        for (int sample = 0; sample <= 4; sample++) {
            double travel = lerp(travel0, travel1, sample / 4.0D);
            double radius = radiusAt(radiusPropagation, travel);

            if (!Double.isFinite(radius) || radius <= EDGE_TOUCH_EPSILON) {
                return MAX_SIDE_PATCH_TRAVEL_SUBDIVISIONS;
            }

            minRadius = Math.min(minRadius, radius);
            maxRadius = Math.max(maxRadius, radius);
        }

        if (!Double.isFinite(minRadius) || !Double.isFinite(maxRadius) || minRadius <= EDGE_TOUCH_EPSILON) {
            return MAX_SIDE_PATCH_TRAVEL_SUBDIVISIONS;
        }

        double radiusRatio = maxRadius / minRadius;
        double radiusDelta = maxRadius - minRadius;
        int ratioSteps = radiusRatio <= 1.0D + EDGE_TOUCH_EPSILON
                ? 1
                : (int) Math.ceil(Math.log(radiusRatio) / Math.log(SIDE_PATCH_RADIUS_RATIO_PER_SEGMENT));
        int deltaSteps = radiusDelta <= EDGE_TOUCH_EPSILON
                ? 1
                : (int) Math.ceil(radiusDelta / SIDE_PATCH_RADIUS_DELTA_PER_SEGMENT);
        int steps = Math.max(ratioSteps, deltaSteps);

        return Math.max(1, Math.min(MAX_SIDE_PATCH_TRAVEL_SUBDIVISIONS, steps));
    }

    private static int sideChartTravelSubdivisionCount(
            BeamEnvelope envelope,
            double travel0,
            double travel1
    ) {
        return adaptiveSideTravelSubdivisionCount(envelope, travel0, travel1);
    }

    private static int sideChartTravelSubdivisionCount(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            double travel0,
            double travel1
    ) {
        return adaptiveSideTravelSubdivisionCount(radiusPropagation, travel0, travel1);
    }

    private static SpotProjectionAllocation sideAllocation(
            BlockPos targetPos,
            Direction sideFace,
            String kind,
            boolean internalProjectionSide,
            double candidateArea,
            double assignedArea,
            double emittedArea,
            double assignedPowerFraction,
            double emittedPowerFraction,
            int emittedQuads,
            String result
    ) {
        return sideAllocation(
                targetPos,
                sideFace,
                kind,
                internalProjectionSide,
                candidateArea,
                assignedArea,
                emittedArea,
                assignedPowerFraction,
                emittedPowerFraction,
                emittedQuads,
                result,
                ""
        );
    }

    private static SpotProjectionAllocation sideAllocation(
            BlockPos targetPos,
            Direction sideFace,
            String kind,
            boolean internalProjectionSide,
            double candidateArea,
            double assignedArea,
            double emittedArea,
            double assignedPowerFraction,
            double emittedPowerFraction,
            int emittedQuads,
            String result,
            String detail
    ) {
        return new SpotProjectionAllocation(
                targetPos,
                sideFace,
                kind,
                candidateArea,
                assignedArea,
                emittedArea,
                assignedPowerFraction,
                emittedPowerFraction,
                emittedQuads,
                result,
                internalProjectionSide,
                detail
        );
    }

    private static String sideCandidateDetail(
            String kind,
            DepthTile targetTile,
            int boxIndex,
            OpticalCollisionBox box,
            double fixedLocal,
            boolean positiveSide,
            ReceivingSideRegion region,
            List<TravelInterval> travels
    ) {
        return "candidate=" + kind
                + ";tile=" + targetTile.tileU() + "," + targetTile.tileV()
                + ";pos=" + formatPos(targetTile.pos())
                + ";box=" + boxIndex + formatBox(box)
                + ";fixed=" + formatDecimal(fixedLocal)
                + ";side=" + (positiveSide ? "positive" : "negative")
                + ";exposed=" + formatRect(region.exposedRegion())
                + ";receiving=" + formatRect(region.region())
                + ";source_covers=" + region.sourceCoverCount()
                + ";source_cover_area=" + formatDecimal(region.sourceCoverArea())
                + ";travels=" + formatTravelIntervals(travels, 6);
    }

    private static String sideTraceDetail(
            String candidateDetail,
            int ownerBoxIndex,
            double entryTravel,
            double exitTravel,
            double fixedLocal,
            double crossMinLocal,
            double crossMaxLocal,
            TravelInterval occlusionTravel,
            CanonicalRect sideWindow,
            List<OcclusionWindow> sameDepthClipOccluders,
            List<CanonicalRect> visibleSideWindows,
            String occlusionAudit
    ) {
        return candidateDetail
                + ";owner_box=" + ownerBoxIndex
                + ";face_travel=" + formatDecimal(entryTravel) + ".." + formatDecimal(exitTravel)
                + ";face_fixed=" + formatDecimal(fixedLocal)
                + ";face_cross=" + formatDecimal(crossMinLocal) + ".." + formatDecimal(crossMaxLocal)
                + ";segment=" + formatDecimal(occlusionTravel.min()) + ".." + formatDecimal(occlusionTravel.max())
                + ";side_window=" + formatRect(sideWindow)
                + ";same_depth_occluders=" + formatOcclusionWindowList(sameDepthClipOccluders, 8)
                + ";visible_windows=" + formatRectList(visibleSideWindows, 8)
                + ";occlusion_audit=" + occlusionAudit;
    }

    private static String sideIncidenceDetail(
            String candidateDetail,
            int ownerBoxIndex,
            double entryTravel,
            double exitTravel,
            double fixedLocal,
            double crossMinLocal,
            double crossMaxLocal,
            TravelInterval segment,
            double boundaryWorldCoordinate,
            boolean positiveSide,
            double entryRadius,
            double exitRadius
    ) {
        double entryCanonical = canonicalAtWorldCoordinate(entryRadius, boundaryWorldCoordinate);
        double exitCanonical = canonicalAtWorldCoordinate(exitRadius, boundaryWorldCoordinate);
        return candidateDetail
                + ";owner_box=" + ownerBoxIndex
                + ";face_travel=" + formatDecimal(entryTravel) + ".." + formatDecimal(exitTravel)
                + ";face_fixed=" + formatDecimal(fixedLocal)
                + ";face_cross=" + formatDecimal(crossMinLocal) + ".." + formatDecimal(crossMaxLocal)
                + ";segment=" + formatDecimal(segment.min()) + ".." + formatDecimal(segment.max())
                + ";boundary_world=" + formatDecimal(boundaryWorldCoordinate)
                + ";side=" + (positiveSide ? "positive" : "negative")
                + ";radius=" + formatDecimal(entryRadius) + ".." + formatDecimal(exitRadius)
                + ";canonical_axis=" + formatDecimal(entryCanonical) + ".." + formatDecimal(exitCanonical);
    }

    private static String sidePatchDetail(
            String kind,
            CanonicalRect sideWindow,
            CanonicalRect visibleWindow,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            List<Patch> patches
    ) {
        StringBuilder builder = new StringBuilder(192);
        builder.append(kind)
                .append(";side_window=").append(formatRect(sideWindow))
                .append(";visible_window=").append(formatRect(visibleWindow))
                .append(";travel=").append(formatDecimal(travel0)).append("..").append(formatDecimal(travel1))
                .append(";cross0=").append(formatCross(cross0))
                .append(";cross1=").append(formatCross(cross1))
                .append(";patches=").append(patches.size());

        int maxPatches = Math.min(4, patches.size());
        for (int index = 0; index < maxPatches; index++) {
            builder.append(";patch").append(index).append('=').append(formatPatch(patches.get(index)));
        }

        if (patches.size() > maxPatches) {
            builder.append(";patches_omitted=").append(patches.size() - maxPatches);
        }

        return builder.toString();
    }

    private static String formatRect(CanonicalRect rect) {
        return '['
                + formatDecimal(rect.minU()) + ','
                + formatDecimal(rect.minV()) + "->"
                + formatDecimal(rect.maxU()) + ','
                + formatDecimal(rect.maxV()) + ']';
    }

    private static String formatCross(SideCrossSection cross) {
        return "[local="
                + formatDecimal(cross.minLocal()) + ".."
                + formatDecimal(cross.maxLocal()) + ",axis="
                + formatDecimal(cross.axisCanonical()) + ",canon="
                + formatDecimal(cross.minCanonical()) + ".."
                + formatDecimal(cross.maxCanonical()) + ']';
    }

    private static String formatPatch(Patch patch) {
        return "[p="
                + formatLocal(patch.p0()) + '|'
                + formatLocal(patch.p1()) + '|'
                + formatLocal(patch.p2()) + '|'
                + formatLocal(patch.p3()) + ",t="
                + formatTexture(patch.t0()) + '|'
                + formatTexture(patch.t1()) + '|'
                + formatTexture(patch.t2()) + '|'
                + formatTexture(patch.t3()) + ']';
    }

    private static String formatLocal(LocalPoint point) {
        return formatDecimal(point.x()) + ',' + formatDecimal(point.y()) + ',' + formatDecimal(point.z());
    }

    private static String formatTexture(TexturePoint point) {
        return formatDecimal(point.u()) + ',' + formatDecimal(point.v());
    }

    private static String formatDecimal(double value) {
        if (!Double.isFinite(value)) {
            return "nan";
        }

        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String formatBox(OpticalCollisionBox box) {
        return "[t="
                + formatDecimal(box.minTravel()) + ".." + formatDecimal(box.maxTravel())
                + ",u=" + formatDecimal(box.minU()) + ".." + formatDecimal(box.maxU())
                + ",v=" + formatDecimal(box.minV()) + ".." + formatDecimal(box.maxV())
                + ']';
    }

    private static String formatTravelIntervals(List<TravelInterval> travels, int limit) {
        if (travels.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder();
        builder.append('[');
        int count = Math.min(limit, travels.size());

        for (int index = 0; index < count; index++) {
            if (index > 0) {
                builder.append('|');
            }

            TravelInterval travel = travels.get(index);
            builder.append(formatDecimal(travel.min())).append("..").append(formatDecimal(travel.max()));
        }

        if (travels.size() > count) {
            builder.append("|...+").append(travels.size() - count);
        }

        builder.append(']');
        return builder.toString();
    }

    private static SpotProjectionAllocation frontOcclusionProbeAllocation(
            BlockPos targetPos,
            Direction displayFace,
            int depth,
            int tileU,
            int tileV,
            BeamEnvelope envelope,
            ProjectionRect frontRect,
            List<OcclusionWindow> occlusionWindows
    ) {
        BeamEnvelope backEnvelope = envelopeAtOffset(envelope, 1.0D);
        ProjectionRect backRect = projectionRect(backEnvelope.radius(), tileU, tileV);
        String detail = "depth=" + depth
                + ";tile=" + tileU + "," + tileV
                + ";front_radius=" + formatDecimal(envelope.radius())
                + ";back_radius=" + formatDecimal(backEnvelope.radius())
                + ";front_rect=" + formatProjectionRect(frontRect)
                + ";back_rect=" + formatProjectionRect(backRect)
                + ";windows=" + formatOcclusionWindowList(occlusionWindows, 6);

        return new SpotProjectionAllocation(
                targetPos,
                displayFace,
                "front-occlusion-probe",
                occlusionAreaSum(occlusionWindows),
                occlusionWindows.size(),
                0.0D,
                0.0D,
                0.0D,
                0,
                "debug",
                detail
        );
    }

    private static SpotProjectionAllocation frontVisibleProbeAllocation(
            BlockPos targetPos,
            Direction displayFace,
            int depth,
            int tileU,
            int tileV,
            ProjectionRect rawRect,
            List<OcclusionWindow> intersectingOccupied,
            List<ProjectionRect> visibleRects,
            List<ProjectionRect> visibleWithoutBackRects,
            int occupiedCount
    ) {
        double rawArea = canonicalArea(rawRect.canonicalRect());
        double visibleArea = projectionRectAreaSum(visibleRects);
        double visibleWithoutBackArea = projectionRectAreaSum(visibleWithoutBackRects);
        double backDeltaArea = Math.max(0.0D, visibleWithoutBackArea - visibleArea);
        List<OcclusionWindow> intersectingBack = occlusionWindowsWithPlane(intersectingOccupied, "back");
        String result;

        if (visibleRects.isEmpty()) {
            result = "fully_occluded";
        } else if (visibleArea < rawArea - EDGE_TOUCH_EPSILON) {
            result = "clipped";
        } else {
            result = "unchanged";
        }

        String detail = "depth=" + depth
                + ";tile=" + tileU + "," + tileV
                + ";raw=" + formatProjectionRect(rawRect)
                + ";occupied_count=" + occupiedCount
                + ";intersecting_count=" + intersectingOccupied.size()
                + ";intersecting_back_count=" + intersectingBack.size()
                + ";intersecting=" + formatOcclusionWindowList(intersectingOccupied, 8)
                + ";intersecting_back=" + formatOcclusionWindowList(intersectingBack, 8)
                + ";visible=" + formatProjectionRectList(visibleRects, 8)
                + ";visible_without_back=" + formatProjectionRectList(visibleWithoutBackRects, 8)
                + ";raw_area=" + formatDecimal(rawArea)
                + ";visible_area=" + formatDecimal(visibleArea)
                + ";visible_without_back_area=" + formatDecimal(visibleWithoutBackArea)
                + ";back_delta_area=" + formatDecimal(backDeltaArea);

        return new SpotProjectionAllocation(
                targetPos,
                displayFace,
                "front-visible-probe",
                rawArea,
                visibleArea,
                0.0D,
                0.0D,
                0.0D,
                0,
                result,
                detail
        );
    }

    private static SpotProjectionAllocation frontPrefixProbeAllocation(
            BlockPos targetPos,
            Direction displayFace,
            int depth,
            int tileU,
            int tileV,
            int boxIndex,
            OpticalCollisionBox box,
            ProjectionRect rawRect,
            int activePrefixCount,
            List<OcclusionWindow> intersectingPrefix,
            List<ProjectionRect> visibleRects
    ) {
        double rawArea = canonicalArea(rawRect.canonicalRect());
        double visibleArea = projectionRectAreaSum(visibleRects);
        int crossBlockHits = 0;
        int sameBlockSiblingHits = 0;
        int continuingAtFrontHits = 0;

        for (OcclusionWindow window : intersectingPrefix) {
            if (window.pos().equals(targetPos)) {
                if (window.boxIndex() != boxIndex) {
                    sameBlockSiblingHits++;
                }
            } else {
                crossBlockHits++;
            }

            if (classifyFrontPlaneEvent(
                    window.travel(),
                    window.volumeStartTravel(),
                    box.minTravel()
            ) == FrontPlanePhase.CONTINUING) {
                continuingAtFrontHits++;
            }
        }

        String result;
        if (visibleRects.isEmpty()) {
            result = "fully_occluded";
        } else if (visibleArea < rawArea - EDGE_TOUCH_EPSILON) {
            result = "clipped";
        } else {
            result = "unchanged";
        }

        String detail = "depth=" + depth
                + ";tile=" + tileU + "," + tileV
                + ";box=" + boxIndex + formatBox(box)
                + ";front_travel=" + formatDecimal(box.minTravel())
                + ";active_prefix_count=" + activePrefixCount
                + ";intersecting_prefix_count=" + intersectingPrefix.size()
                + ";cross_block_prefix_hits=" + crossBlockHits
                + ";same_block_sibling_prefix_hits=" + sameBlockSiblingHits
                + ";continuing_at_front_hits=" + continuingAtFrontHits
                + ";prefix_windows=" + formatOcclusionWindowList(intersectingPrefix, 12)
                + ";raw=" + formatProjectionRect(rawRect)
                + ";visible=" + formatProjectionRectList(visibleRects, 12)
                + ";raw_area=" + formatDecimal(rawArea)
                + ";visible_area=" + formatDecimal(visibleArea);

        return new SpotProjectionAllocation(
                targetPos,
                displayFace,
                "front-prefix-probe",
                rawArea,
                visibleArea,
                0.0D,
                0.0D,
                0.0D,
                0,
                result,
                detail
        );
    }

    private static String formatProjectionRect(ProjectionRect rect) {
        return rect == null ? "null" : formatRect(rect.canonicalRect());
    }

    private static String formatProjectionRectList(List<ProjectionRect> rects, int limit) {
        if (rects.isEmpty()) {
            return "[]";
        }

        List<CanonicalRect> canonicalRects = new ArrayList<>(rects.size());

        for (ProjectionRect rect : rects) {
            canonicalRects.add(rect.canonicalRect());
        }

        return formatRectList(canonicalRects, limit);
    }

    private static String formatRectList(List<CanonicalRect> rects, int limit) {
        if (rects.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder();
        builder.append('[');
        int count = Math.min(limit, rects.size());

        for (int index = 0; index < count; index++) {
            if (index > 0) {
                builder.append('|');
            }

            builder.append(formatRect(rects.get(index)));
        }

        if (rects.size() > count) {
            builder.append("|...+").append(rects.size() - count);
        }

        builder.append(']');
        return builder.toString();
    }

    private static String formatOcclusionWindowList(List<OcclusionWindow> windows, int limit) {
        if (windows.isEmpty()) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder();
        builder.append('[');
        int count = Math.min(limit, windows.size());

        for (int index = 0; index < count; index++) {
            if (index > 0) {
                builder.append('|');
            }

            builder.append(formatOcclusionWindow(windows.get(index)));
        }

        if (windows.size() > count) {
            builder.append("|...+").append(windows.size() - count);
        }

        builder.append(']');
        return builder.toString();
    }

    private static String formatOcclusionWindow(OcclusionWindow window) {
        return window.plane()
                + "@"
                + formatPos(window.pos())
                + "/d=" + window.depth()
                + "/box=" + window.boxIndex()
                + "/travel=" + formatDecimal(window.travel())
                + "/start=" + formatDecimal(window.volumeStartTravel())
                + "/tile=" + window.tileU() + "," + window.tileV()
                + "/rect=" + formatRect(window.rect());
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    static Patch clippedUSidePatch(
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double fixedULocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect visibleWindow
    ) {
        List<Patch> patches = clippedUSidePatches(
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                fixedULocal,
                travel0,
                travel1,
                cross0,
                cross1,
                visibleWindow
        );

        return patches.isEmpty() ? null : patches.get(0);
    }

    private static boolean wouldUseFullTravelUSidePatch(
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect visibleWindow
    ) {
        if (!coversFullSideTravelAxis(visibleWindow.minU(), visibleWindow.maxU(), cross0.axisCanonical(), cross1.axisCanonical())) {
            return false;
        }

        CrossClip clip0 = clippedCrossSectionAt(cross0, cross1, 0.0D, visibleWindow.minV(), visibleWindow.maxV());
        CrossClip clip1 = clippedCrossSectionAt(cross0, cross1, 1.0D, visibleWindow.minV(), visibleWindow.maxV());

        return clip0 != null && clip1 != null;
    }

    private static boolean wouldUseFullTravelVSidePatch(
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect visibleWindow
    ) {
        if (!coversFullSideTravelAxis(visibleWindow.minV(), visibleWindow.maxV(), cross0.axisCanonical(), cross1.axisCanonical())) {
            return false;
        }

        CrossClip clip0 = clippedCrossSectionAt(cross0, cross1, 0.0D, visibleWindow.minU(), visibleWindow.maxU());
        CrossClip clip1 = clippedCrossSectionAt(cross0, cross1, 1.0D, visibleWindow.minU(), visibleWindow.maxU());

        return clip0 != null && clip1 != null;
    }

    private static boolean coversFullSideTravelAxis(
            double windowMin,
            double windowMax,
            double axis0,
            double axis1
    ) {
        double axisMin = Math.min(axis0, axis1);
        double axisMax = Math.max(axis0, axis1);
        return windowMin <= axisMin + EDGE_TOUCH_EPSILON
                && windowMax >= axisMax - EDGE_TOUCH_EPSILON;
    }

    private static List<Patch> clippedUSidePatches(
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double fixedULocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect visibleWindow
    ) {
        List<ParameterRange> ranges = sideParameterSubRanges(
                cross0.axisCanonical(),
                cross1.axisCanonical(),
                visibleWindow.minU(),
                visibleWindow.maxU(),
                cross0.minCanonical(),
                cross1.minCanonical(),
                cross0.maxCanonical(),
                cross1.maxCanonical(),
                visibleWindow.minV(),
                visibleWindow.maxV()
        );

        if (ranges.isEmpty()) {
            return List.of();
        }

        List<Patch> patches = new ArrayList<>(ranges.size());

        for (ParameterRange range : ranges) {
            Patch patch = clippedUSidePatchForRange(
                    sideFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    fixedULocal,
                    travel0,
                    travel1,
                    cross0,
                    cross1,
                    visibleWindow,
                    range
            );

            if (patch != null) {
                patches.add(patch);
            }
        }

        return patches.isEmpty() ? List.of() : patches;
    }

    private static Patch clippedUSidePatchForRange(
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double fixedULocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect visibleWindow,
            ParameterRange travelRange
    ) {
        double mid = (travelRange.min() + travelRange.max()) * 0.5D;
        SideClipAt clip0 = clippedCrossSectionNear(cross0, cross1, travelRange.min(), mid, visibleWindow.minV(), visibleWindow.maxV());
        SideClipAt clip1 = clippedCrossSectionNear(cross0, cross1, travelRange.max(), mid, visibleWindow.minV(), visibleWindow.maxV());

        if (clip0 == null || clip1 == null) {
            return null;
        }

        if (clip1.travelParameter() - clip0.travelParameter() <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double axis0 = lerp(cross0.axisCanonical(), cross1.axisCanonical(), clip0.travelParameter());
        double axis1 = lerp(cross0.axisCanonical(), cross1.axisCanonical(), clip1.travelParameter());
        double localTravel0 = lerp(travel0, travel1, clip0.travelParameter());
        double localTravel1 = lerp(travel0, travel1, clip1.travelParameter());
        double fixedAxisCoordinate = SpotSurfaceFrame.axisLocal(uDirection, fixedULocal);

        return SpotProjectionPatch.orientedOnPlane(
                sideFace,
                fixedAxisCoordinate,
                localPoint(travelDirection, uDirection, vDirection, localTravel0, fixedULocal, clip0.crossClip().minLocal()),
                new TexturePoint(axis0, renderTextureV(clip0.crossClip().minCanonical())),
                localPoint(travelDirection, uDirection, vDirection, localTravel0, fixedULocal, clip0.crossClip().maxLocal()),
                new TexturePoint(axis0, renderTextureV(clip0.crossClip().maxCanonical())),
                localPoint(travelDirection, uDirection, vDirection, localTravel1, fixedULocal, clip1.crossClip().maxLocal()),
                new TexturePoint(axis1, renderTextureV(clip1.crossClip().maxCanonical())),
                localPoint(travelDirection, uDirection, vDirection, localTravel1, fixedULocal, clip1.crossClip().minLocal()),
                new TexturePoint(axis1, renderTextureV(clip1.crossClip().minCanonical()))
        );
    }

    static Patch clippedVSidePatch(
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double fixedVLocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect visibleWindow
    ) {
        List<Patch> patches = clippedVSidePatches(
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                fixedVLocal,
                travel0,
                travel1,
                cross0,
                cross1,
                visibleWindow
        );

        return patches.isEmpty() ? null : patches.get(0);
    }

    private static List<Patch> clippedVSidePatches(
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double fixedVLocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect visibleWindow
    ) {
        List<ParameterRange> ranges = sideParameterSubRanges(
                cross0.axisCanonical(),
                cross1.axisCanonical(),
                visibleWindow.minV(),
                visibleWindow.maxV(),
                cross0.minCanonical(),
                cross1.minCanonical(),
                cross0.maxCanonical(),
                cross1.maxCanonical(),
                visibleWindow.minU(),
                visibleWindow.maxU()
        );

        if (ranges.isEmpty()) {
            return List.of();
        }

        List<Patch> patches = new ArrayList<>(ranges.size());

        for (ParameterRange range : ranges) {
            Patch patch = clippedVSidePatchForRange(
                    sideFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    fixedVLocal,
                    travel0,
                    travel1,
                    cross0,
                    cross1,
                    visibleWindow,
                    range
            );

            if (patch != null) {
                patches.add(patch);
            }
        }

        return patches.isEmpty() ? List.of() : patches;
    }

    private static Patch clippedVSidePatchForRange(
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double fixedVLocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect visibleWindow,
            ParameterRange travelRange
    ) {
        double mid = (travelRange.min() + travelRange.max()) * 0.5D;
        SideClipAt clip0 = clippedCrossSectionNear(cross0, cross1, travelRange.min(), mid, visibleWindow.minU(), visibleWindow.maxU());
        SideClipAt clip1 = clippedCrossSectionNear(cross0, cross1, travelRange.max(), mid, visibleWindow.minU(), visibleWindow.maxU());

        if (clip0 == null || clip1 == null) {
            return null;
        }

        if (clip1.travelParameter() - clip0.travelParameter() <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double axis0 = lerp(cross0.axisCanonical(), cross1.axisCanonical(), clip0.travelParameter());
        double axis1 = lerp(cross0.axisCanonical(), cross1.axisCanonical(), clip1.travelParameter());
        double localTravel0 = lerp(travel0, travel1, clip0.travelParameter());
        double localTravel1 = lerp(travel0, travel1, clip1.travelParameter());
        double fixedAxisCoordinate = SpotSurfaceFrame.axisLocal(vDirection, fixedVLocal);

        return SpotProjectionPatch.orientedOnPlane(
                sideFace,
                fixedAxisCoordinate,
                localPoint(travelDirection, uDirection, vDirection, localTravel0, clip0.crossClip().minLocal(), fixedVLocal),
                new TexturePoint(clip0.crossClip().minCanonical(), renderTextureV(axis0)),
                localPoint(travelDirection, uDirection, vDirection, localTravel0, clip0.crossClip().maxLocal(), fixedVLocal),
                new TexturePoint(clip0.crossClip().maxCanonical(), renderTextureV(axis0)),
                localPoint(travelDirection, uDirection, vDirection, localTravel1, clip1.crossClip().maxLocal(), fixedVLocal),
                new TexturePoint(clip1.crossClip().maxCanonical(), renderTextureV(axis1)),
                localPoint(travelDirection, uDirection, vDirection, localTravel1, clip1.crossClip().minLocal(), fixedVLocal),
                new TexturePoint(clip1.crossClip().minCanonical(), renderTextureV(axis1))
        );
    }

    private static ParameterRange sideParameterRange(
            double axis0,
            double axis1,
            double windowAxisMin,
            double windowAxisMax,
            double crossMin0,
            double crossMin1,
            double crossMax0,
            double crossMax1,
            double windowCrossMin,
            double windowCrossMax
    ) {
        ParameterRange range = parameterRangeForInterval(axis0, axis1, windowAxisMin, windowAxisMax);
        range = intersect(range, rangeWhereLinearLess(crossMin0, crossMin1, windowCrossMax));
        range = intersect(range, rangeWhereLinearGreater(crossMax0, crossMax1, windowCrossMin));
        return range;
    }

    private static List<ParameterRange> sideParameterSubRanges(
            double axis0,
            double axis1,
            double windowAxisMin,
            double windowAxisMax,
            double crossMin0,
            double crossMin1,
            double crossMax0,
            double crossMax1,
            double windowCrossMin,
            double windowCrossMax
    ) {
        ParameterRange range = sideParameterRange(
                axis0,
                axis1,
                windowAxisMin,
                windowAxisMax,
                crossMin0,
                crossMin1,
                crossMax0,
                crossMax1,
                windowCrossMin,
                windowCrossMax
        );

        if (range == null) {
            return List.of();
        }

        List<Double> cuts = new ArrayList<>();
        cuts.add(range.min());
        cuts.add(range.max());
        addLinearCut(cuts, range, axis0, axis1, windowAxisMin);
        addLinearCut(cuts, range, axis0, axis1, windowAxisMax);
        addLinearCut(cuts, range, crossMin0, crossMin1, windowCrossMin);
        addLinearCut(cuts, range, crossMin0, crossMin1, windowCrossMax);
        addLinearCut(cuts, range, crossMax0, crossMax1, windowCrossMin);
        addLinearCut(cuts, range, crossMax0, crossMax1, windowCrossMax);
        cuts.sort(Double::compare);

        List<ParameterRange> ranges = new ArrayList<>();
        double previous = cuts.get(0);

        for (int index = 1; index < cuts.size(); index++) {
            double current = cuts.get(index);

            if (current - previous <= EDGE_TOUCH_EPSILON) {
                continue;
            }

            ranges.add(new ParameterRange(previous, current));
            previous = current;
        }

        return ranges.isEmpty() ? List.of(range) : ranges;
    }

    private static void addLinearCut(
            List<Double> cuts,
            ParameterRange range,
            double value0,
            double value1,
            double target
    ) {
        double delta = value1 - value0;

        if (Math.abs(delta) <= CONE_SIDE_EPSILON) {
            return;
        }

        double cut = (target - value0) / delta;

        if (!Double.isFinite(cut)) {
            return;
        }

        if (cut > range.min() + EDGE_TOUCH_EPSILON && cut < range.max() - EDGE_TOUCH_EPSILON) {
            cuts.add(cut);
        }
    }

    private static ParameterRange parameterRangeForInterval(
            double value0,
            double value1,
            double intervalMin,
            double intervalMax
    ) {
        if (intervalMax - intervalMin <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double delta = value1 - value0;

        if (Math.abs(delta) <= CONE_SIDE_EPSILON) {
            if (value0 < intervalMin - EDGE_TOUCH_EPSILON || value0 > intervalMax + EDGE_TOUCH_EPSILON) {
                return null;
            }

            return new ParameterRange(0.0D, 1.0D);
        }

        double t0 = (intervalMin - value0) / delta;
        double t1 = (intervalMax - value0) / delta;
        double min = Math.max(0.0D, Math.min(t0, t1));
        double max = Math.min(1.0D, Math.max(t0, t1));

        if (max - min <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        return new ParameterRange(min, max);
    }

    private static ParameterRange rangeWhereLinearLess(double value0, double value1, double threshold) {
        double delta = value1 - value0;

        if (Math.abs(delta) <= CONE_SIDE_EPSILON) {
            return value0 < threshold - EDGE_TOUCH_EPSILON ? new ParameterRange(0.0D, 1.0D) : null;
        }

        double crossing = clamp01((threshold - value0) / delta);

        if (delta > 0.0D) {
            return crossing <= EDGE_TOUCH_EPSILON ? null : new ParameterRange(0.0D, crossing);
        }

        return 1.0D - crossing <= EDGE_TOUCH_EPSILON ? null : new ParameterRange(crossing, 1.0D);
    }

    private static ParameterRange rangeWhereLinearGreater(double value0, double value1, double threshold) {
        double delta = value1 - value0;

        if (Math.abs(delta) <= CONE_SIDE_EPSILON) {
            return value0 > threshold + EDGE_TOUCH_EPSILON ? new ParameterRange(0.0D, 1.0D) : null;
        }

        double crossing = clamp01((threshold - value0) / delta);

        if (delta > 0.0D) {
            return 1.0D - crossing <= EDGE_TOUCH_EPSILON ? null : new ParameterRange(crossing, 1.0D);
        }

        return crossing <= EDGE_TOUCH_EPSILON ? null : new ParameterRange(0.0D, crossing);
    }

    private static ParameterRange intersect(ParameterRange first, ParameterRange second) {
        if (first == null || second == null) {
            return null;
        }

        double min = Math.max(first.min(), second.min());
        double max = Math.min(first.max(), second.max());

        if (max - min <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        return new ParameterRange(min, max);
    }

    private static CrossClip clippedCrossSectionAt(
            SideCrossSection cross0,
            SideCrossSection cross1,
            double travelParameter,
            double windowMin,
            double windowMax
    ) {
        return clippedCrossSectionAt(cross0, cross1, travelParameter, windowMin, windowMax, false);
    }

    private static CrossClip clippedCrossSectionAt(
            SideCrossSection cross0,
            SideCrossSection cross1,
            double travelParameter,
            double windowMin,
            double windowMax,
            boolean allowDegenerate
    ) {
        double minCanonical = lerp(cross0.minCanonical(), cross1.minCanonical(), travelParameter);
        double maxCanonical = lerp(cross0.maxCanonical(), cross1.maxCanonical(), travelParameter);
        double minLocal = lerp(cross0.minLocal(), cross1.minLocal(), travelParameter);
        double maxLocal = lerp(cross0.maxLocal(), cross1.maxLocal(), travelParameter);
        double clippedMinCanonical = Math.max(minCanonical, windowMin);
        double clippedMaxCanonical = Math.min(maxCanonical, windowMax);

        if (clippedMaxCanonical < clippedMinCanonical - EDGE_TOUCH_EPSILON) {
            return null;
        }

        double canonicalSpan = maxCanonical - minCanonical;

        if (canonicalSpan <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        if (clippedMaxCanonical - clippedMinCanonical <= EDGE_TOUCH_EPSILON) {
            if (!allowDegenerate) {
                return null;
            }

            double clippedCanonical = (clippedMinCanonical + clippedMaxCanonical) * 0.5D;
            double localT = clamp01((clippedCanonical - minCanonical) / canonicalSpan);
            double clippedLocal = lerp(minLocal, maxLocal, localT);
            return new CrossClip(
                    clippedLocal,
                    clippedLocal,
                    clippedCanonical,
                    clippedCanonical
            );
        }

        double minT = clamp01((clippedMinCanonical - minCanonical) / canonicalSpan);
        double maxT = clamp01((clippedMaxCanonical - minCanonical) / canonicalSpan);
        return new CrossClip(
                lerp(minLocal, maxLocal, minT),
                lerp(minLocal, maxLocal, maxT),
                clippedMinCanonical,
                clippedMaxCanonical
        );
    }

    private static SideClipAt clippedCrossSectionNear(
            SideCrossSection cross0,
            SideCrossSection cross1,
            double travelParameter,
            double toward,
            double windowMin,
            double windowMax
    ) {
        CrossClip exact = clippedCrossSectionAt(cross0, cross1, travelParameter, windowMin, windowMax, true);

        if (exact != null) {
            return new SideClipAt(travelParameter, exact);
        }

        if (Math.abs(toward - travelParameter) <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        CrossClip towardClip = clippedCrossSectionAt(cross0, cross1, toward, windowMin, windowMax);

        if (towardClip == null) {
            return null;
        }

        double invalid = travelParameter;
        double valid = toward;

        for (int iteration = 0; iteration < 24; iteration++) {
            double mid = (invalid + valid) * 0.5D;
            CrossClip midClip = clippedCrossSectionAt(cross0, cross1, mid, windowMin, windowMax);

            if (midClip == null) {
                invalid = mid;
            } else {
                valid = mid;
                towardClip = midClip;
            }
        }

        return new SideClipAt(valid, towardClip);
    }

    private static double squareConeRequiredRadius(double fixedCoordinate, double crossMin, double crossMax) {
        return Math.max(Math.abs(fixedCoordinate), intervalDistanceFromAxis(crossMin, crossMax));
    }

    private static double intervalDistanceFromAxis(double min, double max) {
        if (min <= 0.0D && max >= 0.0D) {
            return 0.0D;
        }

        return Math.min(Math.abs(min), Math.abs(max));
    }

    private static boolean addSideSpot(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction sideFace,
            BeamPacket targetTemplate,
            double sidePower,
            double sideCoherentPower,
            LocalPoint p0,
            double textureU0,
            double textureV0,
            LocalPoint p1,
            double textureU1,
            double textureV1,
            LocalPoint p2,
            double textureU2,
            double textureV2,
            LocalPoint p3,
            double textureU3,
            double textureV3,
            List<SpotRecord> fragments
    ) {
        Patch patch = SpotProjectionPatch.oriented(
                sideFace,
                p0,
                new TexturePoint(textureU0, textureV0),
                p1,
                new TexturePoint(textureU1, textureV1),
                p2,
                new TexturePoint(textureU2, textureV2),
                p3,
                new TexturePoint(textureU3, textureV3)
        );

        if (patch == null) {
            return false;
        }

        return addPatchSpot(
                level,
                targetPos,
                targetState,
                sideFace,
                targetTemplate,
                sidePower,
                sideCoherentPower,
                patch,
                fragments
        ).emitted();
    }

    private static PatchEmission addPatchSpot(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction sideFace,
            BeamPacket targetTemplate,
            double sidePower,
            double sideCoherentPower,
            Patch patch,
            List<SpotRecord> fragments
    ) {
        SpotRecord surfaceSpot = OpticalSpotTracker.createCompiledSurfaceSpot(
                level,
                targetPos,
                sideFace,
                targetState,
                targetTemplate,
                sidePower,
                sideCoherentPower
        );
        return addPatchSpot(surfaceSpot, patch, fragments);
    }

    private static SpotRecord cachedSurfaceSpot(
            Map<SurfaceSpotKey, SpotRecord> cache,
            Level level,
            BlockPos targetPos,
            Direction face,
            BlockState targetState,
            BeamPacket targetTemplate,
            double power,
            double coherentPower,
            ProjectionStatsBuilder stats
    ) {
        SurfaceSpotKey key = new SurfaceSpotKey(targetPos.asLong(), face);
        SpotRecord existing = cache.get(key);

        if (existing != null) {
            stats.surfaceAppearanceCacheHits++;
            return existing;
        }

        long appearanceBuildStartNanos = stats.startTimer();
        SpotRecord created = OpticalSpotTracker.createCompiledSurfaceSpot(
                level,
                targetPos,
                face,
                targetState,
                targetTemplate,
                power,
                coherentPower
        );
        stats.addSurfaceAppearanceBuildNanos(appearanceBuildStartNanos);
        cache.put(key, created);
        stats.surfaceAppearanceBuilds++;
        return created;
    }

    private static PatchEmission addPatchSpot(
            SpotRecord surfaceSpot,
            Patch patch,
            List<SpotRecord> fragments
    ) {
        SpotRecord spot = surfaceSpot.withFootprintQuad(
                quantizeQuadUnit(patch.p0().x()),
                quantizeQuadUnit(patch.p0().y()),
                quantizeQuadUnit(patch.p0().z()),
                quantizeQuadUnit(patch.t0().u()),
                quantizeQuadUnit(patch.t0().v()),
                quantizeQuadUnit(patch.p1().x()),
                quantizeQuadUnit(patch.p1().y()),
                quantizeQuadUnit(patch.p1().z()),
                quantizeQuadUnit(patch.t1().u()),
                quantizeQuadUnit(patch.t1().v()),
                quantizeQuadUnit(patch.p2().x()),
                quantizeQuadUnit(patch.p2().y()),
                quantizeQuadUnit(patch.p2().z()),
                quantizeQuadUnit(patch.t2().u()),
                quantizeQuadUnit(patch.t2().v()),
                quantizeQuadUnit(patch.p3().x()),
                quantizeQuadUnit(patch.p3().y()),
                quantizeQuadUnit(patch.p3().z()),
                quantizeQuadUnit(patch.t3().u()),
                quantizeQuadUnit(patch.t3().v())
        );

        fragments.add(spot);
        if (spot.visible()) {
            return PatchEmission.EMITTED;
        }

        return PatchEmission.SPOT_INVISIBLE;
    }

    private static BeamEnvelope envelopeAtDepth(BeamEnvelope sourceEnvelope, int depth) {
        if (depth <= 0) {
            return sourceEnvelope;
        }

        return BeamGeometryOps.propagate(sourceEnvelope, depth);
    }

    private static BeamEnvelope envelopeAtOffset(BeamEnvelope sourceEnvelope, double offset) {
        if (offset <= 0.0D) {
            return sourceEnvelope;
        }

        return BeamGeometryOps.propagate(sourceEnvelope, offset);
    }

    private static double maxEnvelopeRadiusOverUnit(BeamGeometryOps.RadiusPropagation radiusPropagation) {
        double entryRadius = radiusPropagation.radiusAt(0.0D);
        double exitRadius = radiusPropagation.radiusAt(1.0D);
        double maxRadius = Math.max(
                Double.isFinite(entryRadius) ? entryRadius : 0.0D,
                Double.isFinite(exitRadius) ? exitRadius : 0.0D
        );
        return Math.max(0.0D, maxRadius);
    }

    private static int projectedMinTile(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return 0;
        }

        return (int) Math.ceil(-radius - 0.5D + EDGE_TOUCH_EPSILON);
    }

    private static int projectedMaxTile(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return 0;
        }

        return (int) Math.floor(radius + 0.5D - EDGE_TOUCH_EPSILON);
    }

    private static SideScanTileBounds sideScanTileBounds(
            int minTile,
            int maxTile,
            double maxUnitRadius,
            CanonicalRegion remainingRayWindows
    ) {
        int fallbackMin = minTile - 1;
        int fallbackMax = maxTile + 1;
        int fallbackCount = tileRangeCount(fallbackMin, fallbackMax, fallbackMin, fallbackMax);
        CanonicalRect remainingBounds = remainingRayWindows.bounds();

        if (remainingBounds == null || !Double.isFinite(maxUnitRadius) || maxUnitRadius <= 0.0D) {
            return new SideScanTileBounds(1, 0, 1, 0, fallbackCount);
        }

        AxisRange uRange = canonicalRangeToConservativeWorldRange(
                remainingBounds.minU(),
                remainingBounds.maxU(),
                maxUnitRadius
        );
        AxisRange vRange = canonicalRangeToConservativeWorldRange(
                remainingBounds.minV(),
                remainingBounds.maxV(),
                maxUnitRadius
        );
        int boundedMinU = Math.max(fallbackMin, tileMinForWorldRange(uRange.min()));
        int boundedMaxU = Math.min(fallbackMax, tileMaxForWorldRange(uRange.max()));
        int boundedMinV = Math.max(fallbackMin, tileMinForWorldRange(vRange.min()));
        int boundedMaxV = Math.min(fallbackMax, tileMaxForWorldRange(vRange.max()));
        int scannedCount = tileRangeCount(boundedMinU, boundedMaxU, boundedMinV, boundedMaxV);
        int culled = Math.max(0, fallbackCount - scannedCount);
        return new SideScanTileBounds(boundedMinU, boundedMaxU, boundedMinV, boundedMaxV, culled);
    }

    private static int tileMinForWorldRange(double worldMin) {
        return (int) Math.ceil(worldMin - 0.5D - EDGE_TOUCH_EPSILON);
    }

    private static int tileMaxForWorldRange(double worldMax) {
        return (int) Math.floor(worldMax + 0.5D + EDGE_TOUCH_EPSILON);
    }

    private static int tileRangeCount(int minU, int maxU, int minV, int maxV) {
        if (minU > maxU || minV > maxV) {
            return 0;
        }

        long width = (long) maxU - minU + 1L;
        long height = (long) maxV - minV + 1L;
        return (int) Math.min(Integer.MAX_VALUE, width * height);
    }

    private static AxisRange canonicalRangeToConservativeWorldRange(
            double canonicalMin,
            double canonicalMax,
            double maxRadius
    ) {
        double min = 0.0D;
        double max = 0.0D;
        double worldMin = canonicalToWorldAtRadius(canonicalMin, maxRadius);
        double worldMax = canonicalToWorldAtRadius(canonicalMax, maxRadius);
        min = Math.min(min, Math.min(worldMin, worldMax));
        max = Math.max(max, Math.max(worldMin, worldMax));
        return new AxisRange(min, max);
    }

    private static double canonicalToWorldAtRadius(double canonical, double radius) {
        return (clamp01(canonical) * 2.0D - 1.0D) * Math.max(0.0D, radius);
    }

    private static boolean tileIntersectsRadius(double radius, int tileU, int tileV) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return false;
        }

        double tileMinU = tileU - 0.5D;
        double tileMinV = tileV - 0.5D;
        double tileMaxU = tileU + 0.5D;
        double tileMaxV = tileV + 0.5D;
        return tileMinU < radius && tileMaxU > -radius
                && tileMinV < radius && tileMaxV > -radius;
    }

    private static double visualDistanceFactor(int depth) {
        if (depth <= 0) {
            return 1.0D;
        }

        double d = depth;
        double propagationFade = 1.0D / (1.0D + VISUAL_DISTANCE_FADE_LINEAR * d);
        double sourceDistanceFade = 1.0D / Math.sqrt(1.0D + VISUAL_DISTANCE_FADE_QUADRATIC * d * d);
        return propagationFade * sourceDistanceFade;
    }

    private static double visualSurfacePower(double beamPower, double visualDistanceFactor) {
        return beamPower * visualDistanceFactor;
    }

    private static double visualSidePower(double beamPower, double visualDistanceFactor) {
        return beamPower * visualDistanceFactor * SIDE_FACE_VISUAL_FACTOR;
    }

    private static boolean isRenderableSide(
            double boundaryWorldCoordinate,
            boolean positiveSide,
            double entryRadius,
            double exitRadius
    ) {
        return isEntranceSide(boundaryWorldCoordinate, positiveSide, entryRadius, exitRadius);
    }

    private static boolean isInternalProjectionSide(double fixedLocal) {
        return fixedLocal > EDGE_TOUCH_EPSILON && fixedLocal < 1.0D - EDGE_TOUCH_EPSILON;
    }

    private static boolean isEntranceSide(
            double boundaryWorldCoordinate,
            boolean positiveSide,
            double entryRadius,
            double exitRadius
    ) {
        double entryCanonical = canonicalAtWorldCoordinate(entryRadius, boundaryWorldCoordinate);
        double exitCanonical = canonicalAtWorldCoordinate(exitRadius, boundaryWorldCoordinate);
        double canonicalDelta = exitCanonical - entryCanonical;
        if (!Double.isFinite(canonicalDelta) || Math.abs(canonicalDelta) <= CONE_SIDE_EPSILON) {
            return false;
        }

        // A min face receives newly admitted texture when its canonical boundary moves down;
        // a max face receives it when the boundary moves up.
        return positiveSide ? canonicalDelta > 0.0D : canonicalDelta < 0.0D;
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static LocalPoint localPoint(
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double travelLocal,
            double uLocal,
            double vLocal
    ) {
        return SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, travelLocal, uLocal, vLocal);
    }

    private static Patch orientedPatch(
            Direction face,
            LocalPoint p0,
            TexturePoint t0,
            LocalPoint p1,
            TexturePoint t1,
            LocalPoint p2,
            TexturePoint t2,
            LocalPoint p3,
            TexturePoint t3
    ) {
        double dot = SpotProjectionPatch.faceNormalDot(face, p0, p1, p2, p3);

        if (Math.abs(dot) <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        if (dot >= 0.0D) {
            return new Patch(p0, t0, p1, t1, p2, t2, p3, t3);
        }

        return new Patch(p0, t0, p3, t3, p2, t2, p1, t1);
    }

    private static CanonicalRegion copyRegion(CanonicalRegion region) {
        return CanonicalRegion.fromRects(region.toRects());
    }

    private static double canonicalAtWorldCoordinate(double radius, double worldCoordinate) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return 0.0D;
        }

        return (worldCoordinate + radius) / (radius * 2.0D);
    }

    private static double renderTextureV(double canonicalV) {
        return 1.0D - clamp01(canonicalV);
    }

    private static ProjectionRect projectionRect(double radius, int tileU, int tileV) {
        double tileMinU = tileU - 0.5D;
        double tileMinV = tileV - 0.5D;
        double tileMaxU = tileU + 0.5D;
        double tileMaxV = tileV + 0.5D;
        double hitMinU = Math.max(tileMinU, -radius);
        double hitMinV = Math.max(tileMinV, -radius);
        double hitMaxU = Math.min(tileMaxU, radius);
        double hitMaxV = Math.min(tileMaxV, radius);

        if (hitMinU >= hitMaxU || hitMinV >= hitMaxV) {
            return null;
        }

        double diameter = radius * 2.0D;
        double textureMinU = (hitMinU + radius) / diameter;
        double textureMinV = (hitMinV + radius) / diameter;
        double textureMaxU = (hitMaxU + radius) / diameter;
        double textureMaxV = (hitMaxV + radius) / diameter;

        return projectionRect(radius, tileU, tileV, textureMinU, textureMinV, textureMaxU, textureMaxV);
    }

    private static ProjectionRect projectionRectForLocalFace(
            double radius,
            int tileU,
            int tileV,
            double localMinU,
            double localMinV,
            double localMaxU,
            double localMaxV
    ) {
        if (!Double.isFinite(radius) || radius <= 0.0D
                || localMaxU - localMinU <= EDGE_TOUCH_EPSILON
                || localMaxV - localMinV <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double tileMinU = tileU - 0.5D;
        double tileMinV = tileV - 0.5D;
        double faceMinU = tileMinU + localMinU;
        double faceMinV = tileMinV + localMinV;
        double faceMaxU = tileMinU + localMaxU;
        double faceMaxV = tileMinV + localMaxV;
        double hitMinU = Math.max(faceMinU, -radius);
        double hitMinV = Math.max(faceMinV, -radius);
        double hitMaxU = Math.min(faceMaxU, radius);
        double hitMaxV = Math.min(faceMaxV, radius);

        if (hitMaxU - hitMinU <= EDGE_TOUCH_EPSILON || hitMaxV - hitMinV <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double diameter = radius * 2.0D;
        double textureMinU = (hitMinU + radius) / diameter;
        double textureMinV = (hitMinV + radius) / diameter;
        double textureMaxU = (hitMaxU + radius) / diameter;
        double textureMaxV = (hitMaxV + radius) / diameter;
        return projectionRect(radius, tileU, tileV, textureMinU, textureMinV, textureMaxU, textureMaxV);
    }

    private static CuboidSweep createCuboidSweep(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            int tileU,
            int tileV,
            BlockPos pos,
            int depth,
            int boxIndex,
            OpticalCollisionBox opticalBox,
            CanonicalRegion remainingRayWindows,
            ProjectionStatsBuilder stats
    ) {
        stats.planeWindowTests++;
        CuboidSweep sweep = CuboidSweep.create(
                radiusPropagation,
                tileU,
                tileV,
                pos,
                depth,
                boxIndex,
                opticalBox
        );
        if (sweep == null) {
            return null;
        }

        stats.planeWindowCandidates++;
        if (remainingRayWindows != null
                && !remainingRayWindows.intersectsForPlanePrefilter(sweep.fullHull(), stats)) {
            stats.planeWindowRemainingCulled++;
            return null;
        }

        stats.planeWindows++;
        return sweep;
    }

    private static ProjectionRect projectionRect(
            double radius,
            int tileU,
            int tileV,
            double kernelMinU,
            double kernelMinV,
            double kernelMaxU,
            double kernelMaxV
    ) {
        if (kernelMinU >= kernelMaxU || kernelMinV >= kernelMaxV) {
            return null;
        }

        double diameter = radius * 2.0D;
        double tileMinU = tileU - 0.5D;
        double tileMinV = tileV - 0.5D;
        double hitMinU = kernelMinU * diameter - radius;
        double hitMinV = kernelMinV * diameter - radius;
        double hitMaxU = kernelMaxU * diameter - radius;
        double hitMaxV = kernelMaxV * diameter - radius;
        // Kernel V is geometric bottom-to-top; Minecraft texture V is sampled top-to-bottom here.
        double renderTextureMinV = 1.0D - kernelMaxV;
        double renderTextureMaxV = 1.0D - kernelMinV;
        double clipMinU = hitMinU - tileMinU;
        double clipMinV = hitMinV - tileMinV;
        double clipMaxU = hitMaxU - tileMinU;
        double clipMaxV = hitMaxV - tileMinV;

        return new ProjectionRect(
                quantizeMin(clipMinU),
                quantizeMin(clipMinV),
                quantizeMax(clipMaxU),
                quantizeMax(clipMaxV),
                quantizeMin(kernelMinU),
                quantizeMin(renderTextureMinV),
                quantizeMax(kernelMaxU),
                quantizeMax(renderTextureMaxV),
                kernelMinU,
                kernelMinV,
                kernelMaxU,
                kernelMaxV
        );
    }

    private static List<ProjectionRect> visibleSubRects(
            double radius,
            int tileU,
            int tileV,
            ProjectionRect rect,
            CanonicalRegion remainingRayWindows,
            ProjectionStatsBuilder stats,
            boolean side
    ) {
        List<CanonicalRect> visibleRayWindows = visibleRayWindows(
                rect.canonicalRect(),
                remainingRayWindows,
                List.of(),
                stats,
                side
        );
        return visibleSubRectsFromRayWindows(radius, tileU, tileV, visibleRayWindows, stats);
    }

    private static List<TravelInterval> splitSideTravelAtSameDepthSweeps(
            TravelInterval source,
            List<CuboidSweep> sameDepthSweeps,
            BlockPos ownerPos,
            int ownerBoxIndex
    ) {
        if (sameDepthSweeps.isEmpty()) {
            return List.of(source);
        }

        List<Double> cuts = new ArrayList<>();
        cuts.add(source.min());
        cuts.add(source.max());

        for (CuboidSweep sweep : sameDepthSweeps) {
            if (isOwnCuboidSweep(sweep, ownerPos, ownerBoxIndex)) {
                continue;
            }
            addSweepTravelCut(cuts, source, sweep.startTravel());
            addSweepTravelCut(cuts, source, sweep.endTravel());
            double waistTravel = sweep.radiusPropagation().waistTravel();
            if (waistTravel > sweep.startTravel() + EDGE_TOUCH_EPSILON
                    && waistTravel < sweep.endTravel() - EDGE_TOUCH_EPSILON) {
                addSweepTravelCut(cuts, source, waistTravel);
            }
        }

        cuts.sort(Double::compare);
        List<TravelInterval> intervals = new ArrayList<>();
        double previous = cuts.get(0);

        for (int index = 1; index < cuts.size(); index++) {
            double current = cuts.get(index);
            if (current - previous > EDGE_TOUCH_EPSILON) {
                intervals.add(new TravelInterval(previous, current));
            }
            previous = current;
        }

        return intervals.isEmpty() ? List.of(source) : intervals;
    }

    private static void addSweepTravelCut(List<Double> cuts, TravelInterval source, double travel) {
        if (Double.isFinite(travel)
                && travel > source.min() + EDGE_TOUCH_EPSILON
                && travel < source.max() - EDGE_TOUCH_EPSILON) {
            cuts.add(travel);
        }
    }

    private static List<TravelInterval> splitSideTravelAtSameDepthOccludersIndexed(
            TravelInterval source,
            List<CuboidSweep> sameDepthSweeps,
            SameDepthOcclusionIndex index,
            BlockPos ownerPos,
            int ownerBoxIndex,
        ProjectionStatsBuilder stats
    ) {
        if (index == null) {
            return splitSideTravelAtSameDepthSweeps(source, sameDepthSweeps, ownerPos, ownerBoxIndex);
        }

        List<TravelInterval> optimized = index.split(source, ownerPos, ownerBoxIndex, stats);
        if (stats.structuralValidationEnabled()) {
            List<TravelInterval> legacy = splitSideTravelAtSameDepthSweeps(
                    source,
                    sameDepthSweeps,
                    ownerPos,
                    ownerBoxIndex
            );
            stats.recordSameDepthSplitComparison(ownerPos, ownerBoxIndex, source, optimized, legacy);
        }
        return optimized;
    }

    private static final class SameDepthOcclusionIndex {
        private static final SameDepthOcclusionIndex EMPTY = new SameDepthOcclusionIndex(List.of());
        private final List<CuboidSweep> sweeps;
        private final List<IndexedSweep> sortedByStart;
        private final List<SweepBoundaryGroup> boundaryGroups;
        private final List<IndexedSweep>[] bins;
        private final int[] seenStamps;
        private int queryStamp;

        private static SameDepthOcclusionIndex create(List<CuboidSweep> sweeps) {
            return sweeps.isEmpty() ? EMPTY : new SameDepthOcclusionIndex(sweeps);
        }

        @SuppressWarnings("unchecked")
        private SameDepthOcclusionIndex(List<CuboidSweep> sweeps) {
            this.sweeps = sweeps.isEmpty() ? List.of() : List.copyOf(sweeps);
            this.sortedByStart = new ArrayList<>(sweeps.size());
            for (int index = 0; index < sweeps.size(); index++) {
                sortedByStart.add(new IndexedSweep(index, sweeps.get(index)));
            }
            sortedByStart.sort(Comparator
                    .comparingDouble((IndexedSweep indexed) -> indexed.sweep().startTravel())
                    .thenComparingInt(IndexedSweep::id));
            this.boundaryGroups = buildBoundaryGroups(sortedByStart);
            this.bins = (List<IndexedSweep>[]) new List<?>[
                    SIDE_OCCLUSION_INDEX_BINS * SIDE_OCCLUSION_INDEX_BINS
            ];
            for (int index = 0; index < bins.length; index++) {
                bins[index] = new ArrayList<>();
            }
            for (IndexedSweep indexed : sortedByStart) {
                CanonicalRect rect = indexed.sweep().fullHull();
                int minU = minOcclusionBin(rect.minU());
                int maxU = maxOcclusionBin(rect.maxU());
                int minV = minOcclusionBin(rect.minV());
                int maxV = maxOcclusionBin(rect.maxV());
                for (int binV = minV; binV <= maxV; binV++) {
                    for (int binU = minU; binU <= maxU; binU++) {
                        bins[binV * SIDE_OCCLUSION_INDEX_BINS + binU].add(indexed);
                    }
                }
            }
            this.seenStamps = new int[sweeps.size()];
        }

        private List<TravelInterval> split(
                TravelInterval source,
                BlockPos ownerPos,
                int ownerBoxIndex,
                ProjectionStatsBuilder stats
        ) {
            if (boundaryGroups.isEmpty()) {
                return List.of(source);
            }

            if (stats != null) {
                stats.sameDepthSplitIndexQueries++;
            }
            int groupIndex = firstBoundaryGroupAfter(source.min() + EDGE_TOUCH_EPSILON);
            double cursor = source.min();
            List<TravelInterval> intervals = null;

            for (; groupIndex < boundaryGroups.size(); groupIndex++) {
                SweepBoundaryGroup group = boundaryGroups.get(groupIndex);
                if (group.travel() >= source.max() - EDGE_TOUCH_EPSILON) {
                    break;
                }
                if (stats != null) {
                    stats.sameDepthTravelGroupsVisited++;
                }
                if (!group.hasNonOwner(ownerPos, ownerBoxIndex)) {
                    continue;
                }

                if (intervals == null) {
                    intervals = new ArrayList<>();
                }
                if (group.travel() - cursor > EDGE_TOUCH_EPSILON) {
                    intervals.add(new TravelInterval(cursor, group.travel()));
                }
                cursor = group.travel();
            }

            if (intervals == null) {
                return List.of(source);
            }
            if (source.max() - cursor > EDGE_TOUCH_EPSILON) {
                intervals.add(new TravelInterval(cursor, source.max()));
            }
            return intervals.isEmpty() ? List.of(source) : intervals;
        }

        private List<OcclusionWindow> before(
                BlockPos ownerPos,
                int ownerBoxIndex,
                double surfaceTravel,
                CanonicalRect candidateWindow,
                ProjectionStatsBuilder stats
        ) {
            if (sweeps.isEmpty()) {
                return List.of();
            }

            if (stats != null) {
                stats.sameDepthPrefixIndexQueries++;
            }
            int stamp = nextQueryStamp();
            int minU = minOcclusionBin(candidateWindow.minU());
            int maxU = maxOcclusionBin(candidateWindow.maxU());
            int minV = minOcclusionBin(candidateWindow.minV());
            int maxV = maxOcclusionBin(candidateWindow.maxV());
            List<IndexedSweepHit> hits = new ArrayList<>();

            for (int binV = minV; binV <= maxV; binV++) {
                for (int binU = minU; binU <= maxU; binU++) {
                    List<IndexedSweep> bin = bins[binV * SIDE_OCCLUSION_INDEX_BINS + binU];
                    for (IndexedSweep indexed : bin) {
                        CuboidSweep sweep = indexed.sweep();
                        if (sweep.startTravel() >= surfaceTravel - EDGE_TOUCH_EPSILON) {
                            break;
                        }
                        if (seenStamps[indexed.id()] == stamp) {
                            continue;
                        }
                        seenStamps[indexed.id()] = stamp;
                        if (stats != null) {
                            stats.sameDepthPrefixIndexCandidates++;
                        }
                        if (isOwnCuboidSweep(sweep, ownerPos, ownerBoxIndex)
                                || !intersects(sweep.fullHull(), candidateWindow)) {
                            continue;
                        }
                        OcclusionWindow prefix = sweep.prefixWindow(surfaceTravel);
                        if (prefix != null && intersects(prefix.rect(), candidateWindow)) {
                            hits.add(new IndexedSweepHit(indexed.id(), prefix));
                        }
                    }
                }
            }

            if (hits.isEmpty()) {
                return List.of();
            }
            // Preserve cuboid construction order so sequential rectangle subtraction and
            // downstream quantization remain byte-for-byte deterministic.
            hits.sort(Comparator.comparingInt(IndexedSweepHit::id));
            List<OcclusionWindow> result = new ArrayList<>(hits.size());
            for (IndexedSweepHit hit : hits) {
                result.add(hit.window());
            }
            if (stats != null) {
                stats.sameDepthPrefixIndexHits += result.size();
            }
            return result;
        }

        private int firstBoundaryGroupAfter(double travel) {
            int low = 0;
            int high = boundaryGroups.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (boundaryGroups.get(middle).travel() <= travel) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return low;
        }

        private int nextQueryStamp() {
            if (queryStamp == Integer.MAX_VALUE) {
                java.util.Arrays.fill(seenStamps, 0);
                queryStamp = 1;
            } else {
                queryStamp++;
            }
            return queryStamp;
        }

        private static List<SweepBoundaryGroup> buildBoundaryGroups(List<IndexedSweep> sorted) {
            if (sorted.isEmpty()) {
                return List.of();
            }
            List<IndexedSweepBoundary> boundaries = new ArrayList<>(sorted.size() * 3);
            for (IndexedSweep indexed : sorted) {
                CuboidSweep sweep = indexed.sweep();
                boundaries.add(new IndexedSweepBoundary(sweep.startTravel(), indexed));
                boundaries.add(new IndexedSweepBoundary(sweep.endTravel(), indexed));
                double waistTravel = sweep.radiusPropagation().waistTravel();
                if (Double.isFinite(waistTravel)
                        && waistTravel > sweep.startTravel() + EDGE_TOUCH_EPSILON
                        && waistTravel < sweep.endTravel() - EDGE_TOUCH_EPSILON) {
                    boundaries.add(new IndexedSweepBoundary(waistTravel, indexed));
                }
            }
            boundaries.sort(Comparator
                    .comparingDouble(IndexedSweepBoundary::travel)
                    .thenComparingInt(boundary -> boundary.indexed().id()));
            List<SweepBoundaryGroup> groups = new ArrayList<>();
            int start = 0;
            while (start < boundaries.size()) {
                double travel = boundaries.get(start).travel();
                int end = start + 1;
                while (end < boundaries.size()
                        && Double.compare(boundaries.get(end).travel(), travel) == 0) {
                    end++;
                }
                groups.add(new SweepBoundaryGroup(travel, List.copyOf(boundaries.subList(start, end))));
                start = end;
            }
            return List.copyOf(groups);
        }

        private static int minOcclusionBin(double coordinate) {
            return Math.max(0, Math.min(
                    SIDE_OCCLUSION_INDEX_BINS - 1,
                    (int) Math.floor(clamp01(coordinate) * SIDE_OCCLUSION_INDEX_BINS)
            ));
        }

        private static int maxOcclusionBin(double coordinate) {
            return Math.max(0, Math.min(
                    SIDE_OCCLUSION_INDEX_BINS - 1,
                    (int) Math.ceil(clamp01(coordinate) * SIDE_OCCLUSION_INDEX_BINS) - 1
            ));
        }
    }

    private record IndexedSweep(int id, CuboidSweep sweep) {
    }

    private record IndexedSweepHit(int id, OcclusionWindow window) {
    }

    private record IndexedSweepBoundary(double travel, IndexedSweep indexed) {
    }

    private record SweepBoundaryGroup(double travel, List<IndexedSweepBoundary> boundaries) {
        private boolean hasNonOwner(BlockPos ownerPos, int ownerBoxIndex) {
            for (IndexedSweepBoundary boundary : boundaries) {
                if (!isOwnCuboidSweep(boundary.indexed().sweep(), ownerPos, ownerBoxIndex)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static CanonicalRect conservativeCuboidPrefixRect(
            BeamEnvelope envelope,
            int tileU,
            int tileV,
            OpticalCollisionBox box,
            double travel0,
            double travel1
    ) {
        return conservativeCuboidSweepRect(
                BeamGeometryOps.prepareRadiusPropagation(envelope),
                tileU,
                tileV,
                box,
                travel0,
                travel1
        );
    }

    private static CanonicalRect conservativeCuboidSweepRect(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            int tileU,
            int tileV,
            OpticalCollisionBox box,
            double travel0,
            double travel1
    ) {
        if (travel1 < travel0 - EDGE_TOUCH_EPSILON) {
            return null;
        }

        double radius0 = radiusAt(radiusPropagation, travel0);
        double radius1 = radiusAt(radiusPropagation, travel1);
        double minRadius = Math.min(radius0, radius1);
        double maxRadius = Math.max(radius0, radius1);
        double waistTravel = radiusPropagation.waistTravel();
        if (Double.isFinite(waistTravel)
                && waistTravel > travel0 + EDGE_TOUCH_EPSILON
                && waistTravel < travel1 - EDGE_TOUCH_EPSILON) {
            double waistRadius = radiusAt(radiusPropagation, waistTravel);
            minRadius = Math.min(minRadius, waistRadius);
            maxRadius = Math.max(maxRadius, waistRadius);
        }
        if (minRadius <= EDGE_TOUCH_EPSILON || maxRadius <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double worldMinU = tileU - 0.5D + box.minU();
        double worldMaxU = tileU - 0.5D + box.maxU();
        double worldMinV = tileV - 0.5D + box.minV();
        double worldMaxV = tileV - 0.5D + box.maxV();
        double minU = Math.min(
                clamp01(canonicalAtWorldCoordinate(minRadius, worldMinU)),
                clamp01(canonicalAtWorldCoordinate(maxRadius, worldMinU))
        );
        double maxU = Math.max(
                clamp01(canonicalAtWorldCoordinate(minRadius, worldMaxU)),
                clamp01(canonicalAtWorldCoordinate(maxRadius, worldMaxU))
        );
        double minV = Math.min(
                clamp01(canonicalAtWorldCoordinate(minRadius, worldMinV)),
                clamp01(canonicalAtWorldCoordinate(maxRadius, worldMinV))
        );
        double maxV = Math.max(
                clamp01(canonicalAtWorldCoordinate(minRadius, worldMaxV)),
                clamp01(canonicalAtWorldCoordinate(maxRadius, worldMaxV))
        );

        if (maxU - minU <= EDGE_TOUCH_EPSILON || maxV - minV <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        return new CanonicalRect(minU, minV, maxU, maxV);
    }

    private static String sideOcclusionAudit(
            BeamEnvelope envelope,
            List<DepthCuboidProbe> probes,
            List<CuboidSweep> sweeps,
            BlockPos ownerPos,
            int ownerBoxIndex,
            double surfaceTravel,
            CanonicalRect candidateWindow
    ) {
        int ownProbes = 0;
        int afterSurface = 0;
        int priorProbes = 0;
        int crossBlockPrior = 0;
        int nearbyCrossBlockPrior = 0;
        int conservativeHits = 0;
        int sampledHits = 0;
        int sampleGaps = 0;
        int writtenEntries = 0;
        StringBuilder entries = new StringBuilder(384);
        int ownerTileU = Integer.MIN_VALUE;
        int ownerTileV = Integer.MIN_VALUE;

        for (DepthCuboidProbe probe : probes) {
            if (probe.boxIndex() == ownerBoxIndex && probe.pos().equals(ownerPos)) {
                ownerTileU = probe.tileU();
                ownerTileV = probe.tileV();
                break;
            }
        }

        for (DepthCuboidProbe probe : probes) {
            boolean ownProbe = ownerBoxIndex >= 0
                    && probe.boxIndex() == ownerBoxIndex
                    && probe.pos().equals(ownerPos);
            if (ownProbe) {
                ownProbes++;
                continue;
            }

            OpticalCollisionBox box = probe.box();
            double prefixEnd = Math.min(box.maxTravel(), surfaceTravel);
            if (prefixEnd - box.minTravel() <= EDGE_TOUCH_EPSILON) {
                afterSurface++;
                continue;
            }

            priorProbes++;
            boolean crossBlock = !probe.pos().equals(ownerPos);
            if (crossBlock) {
                crossBlockPrior++;
            }
            boolean nearbyCrossBlock = crossBlock
                    && ownerTileU != Integer.MIN_VALUE
                    && Math.abs(probe.tileU() - ownerTileU) <= 1
                    && Math.abs(probe.tileV() - ownerTileV) <= 1;
            if (nearbyCrossBlock) {
                nearbyCrossBlockPrior++;
            }

            CanonicalRect conservativePrefix = conservativeCuboidPrefixRect(
                    envelope,
                    probe.tileU(),
                    probe.tileV(),
                    box,
                    box.minTravel(),
                    prefixEnd
            );
            boolean conservativeHit = conservativePrefix != null
                    && intersects(conservativePrefix, candidateWindow);
            boolean sampledHit = sweepOccluderHits(
                    sweeps,
                    probe,
                    surfaceTravel,
                    candidateWindow
            );
            boolean sampleGap = conservativeHit && !sampledHit;

            if (conservativeHit) {
                conservativeHits++;
            }
            if (sampledHit) {
                sampledHits++;
            }
            if (sampleGap) {
                sampleGaps++;
            }

            if ((conservativeHit || sampledHit || nearbyCrossBlock) && writtenEntries < 12) {
                if (writtenEntries > 0) {
                    entries.append('|');
                }
                entries.append("{pos=").append(formatPos(probe.pos()))
                        .append(",tile=").append(probe.tileU()).append(',').append(probe.tileV())
                        .append(",depth=").append(probe.depth())
                        .append(",box=").append(probe.boxIndex()).append(formatBox(box))
                        .append(",relation=").append(crossBlock ? "cross_block" : "same_block")
                        .append(",prefix_end=").append(formatDecimal(prefixEnd))
                        .append(",conservative=")
                        .append(conservativePrefix == null ? "none" : formatRect(conservativePrefix))
                        .append(",sampled_hit=").append(sampledHit)
                        .append(",sample_gap=").append(sampleGap)
                        .append('}');
                writtenEntries++;
            }
        }

        return "probes=" + probes.size()
                + ",own=" + ownProbes
                + ",after=" + afterSurface
                + ",prior=" + priorProbes
                + ",cross_block_prior=" + crossBlockPrior
                + ",nearby_cross_block_prior=" + nearbyCrossBlockPrior
                + ",conservative_hits=" + conservativeHits
                + ",sampled_hits=" + sampledHits
                + ",sample_gaps=" + sampleGaps
                + ",entries=" + entries;
    }

    private static boolean sweepOccluderHits(
            List<CuboidSweep> sweeps,
            DepthCuboidProbe probe,
            double surfaceTravel,
            CanonicalRect candidateWindow
    ) {
        for (CuboidSweep sweep : sweeps) {
            if (sweep.boxIndex() == probe.boxIndex()
                    && sweep.pos().equals(probe.pos())) {
                OcclusionWindow prefix = sweep.prefixWindow(surfaceTravel);
                if (prefix != null && intersects(prefix.rect(), candidateWindow)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<OcclusionWindow> sameDepthOccludersBefore(
            List<CuboidSweep> sameDepthSweeps,
            BlockPos ownerPos,
            int ownerBoxIndex,
            double surfaceTravel,
            CanonicalRect candidateWindow
    ) {
        if (sameDepthSweeps.isEmpty()) {
            return List.of();
        }

        List<OcclusionWindow> windows = new ArrayList<>();

        for (CuboidSweep sweep : sameDepthSweeps) {
            if (isOwnCuboidSweep(sweep, ownerPos, ownerBoxIndex)
                    || sweep.startTravel() >= surfaceTravel - EDGE_TOUCH_EPSILON
                    || !intersects(sweep.fullHull(), candidateWindow)) {
                continue;
            }
            OcclusionWindow prefix = sweep.prefixWindow(surfaceTravel);
            if (prefix != null && intersects(prefix.rect(), candidateWindow)) {
                windows.add(prefix);
            }
        }

        return windows.isEmpty() ? List.of() : windows;
    }

    private static List<OcclusionWindow> sameDepthOccludersBeforeIndexed(
            List<CuboidSweep> sameDepthSweeps,
            SameDepthOcclusionIndex index,
            BlockPos ownerPos,
            int ownerBoxIndex,
            double surfaceTravel,
            CanonicalRect candidateWindow,
            ProjectionStatsBuilder stats
    ) {
        if (index == null) {
            return sameDepthOccludersBefore(
                    sameDepthSweeps,
                    ownerPos,
                    ownerBoxIndex,
                    surfaceTravel,
                    candidateWindow
            );
        }

        List<OcclusionWindow> optimized = index.before(
                ownerPos,
                ownerBoxIndex,
                surfaceTravel,
                candidateWindow,
                stats
        );
        if (stats.structuralValidationEnabled()) {
            List<OcclusionWindow> legacy = sameDepthOccludersBefore(
                    sameDepthSweeps,
                    ownerPos,
                    ownerBoxIndex,
                    surfaceTravel,
                    candidateWindow
            );
            stats.recordSameDepthPrefixComparison(
                    ownerPos,
                    ownerBoxIndex,
                    surfaceTravel,
                    candidateWindow,
                    optimized,
                    legacy
            );
        }
        return optimized;
    }

    private static List<CanonicalRect> occlusionRects(List<OcclusionWindow> windows) {
        if (windows.isEmpty()) {
            return List.of();
        }

        List<CanonicalRect> rects = new ArrayList<>(windows.size());

        for (OcclusionWindow window : windows) {
            rects.add(window.rect());
        }

        return rects;
    }

    private static boolean isOwnCuboidOccluder(OcclusionWindow occluder, BlockPos ownerPos, int ownerBoxIndex) {
        return ownerBoxIndex >= 0
                && occluder.boxIndex() == ownerBoxIndex
                && occluder.pos().equals(ownerPos);
    }

    private static boolean isOwnCuboidSweep(CuboidSweep sweep, BlockPos ownerPos, int ownerBoxIndex) {
        return ownerBoxIndex >= 0
                && sweep.boxIndex() == ownerBoxIndex
                && sweep.pos().equals(ownerPos);
    }

    private static List<ProjectionRect> mergeProjectionRects(
            double radius,
            int tileU,
            int tileV,
            List<ProjectionRect> rects,
            ProjectionStatsBuilder stats
    ) {
        if (rects.size() < 2) {
            if (stats != null) {
                stats.recordFrontFragmentMerge(rects.size(), rects.size());
            }
            return rects;
        }

        List<CanonicalRect> rayWindows = new ArrayList<>(rects.size());

        for (ProjectionRect rect : rects) {
            rayWindows.add(rect.canonicalRect());
        }

        List<CanonicalRect> mergedWindows = CanonicalRegion.fromRects(rayWindows).toRects();
        List<ProjectionRect> mergedRects = visibleSubRectsFromRayWindows(radius, tileU, tileV, mergedWindows, null);

        if (stats != null) {
            stats.recordFrontFragmentMerge(rects.size(), mergedRects.size());
        }

        return mergedRects.isEmpty() ? List.of() : mergedRects;
    }

    private static List<ProjectionRect> visibleSubRects(
            double radius,
            int tileU,
            int tileV,
            ProjectionRect rect,
            List<CanonicalRect> clipWindows,
            ProjectionStatsBuilder stats,
            boolean side
    ) {
        if (clipWindows.isEmpty()) {
            return List.of(rect);
        }

        List<CanonicalRect> visibleRayWindows = subtractOccupied(rect.canonicalRect(), clipWindows, stats, side);
        return visibleSubRectsFromRayWindows(radius, tileU, tileV, visibleRayWindows, stats);
    }

    private static List<CanonicalRect> visibleRayWindows(
            CanonicalRect source,
            CanonicalRegion remainingRayWindows,
            List<CanonicalRect> sameDepthWindows,
            ProjectionStatsBuilder stats,
            boolean side
    ) {
        long intersectionStartNanos = stats == null ? 0L : stats.startTimer();
        recordSubtractionStats(stats, side, remainingRayWindows.intervalCount() + sameDepthWindows.size());
        long regionIntersectStartNanos = stats == null ? 0L : stats.startTimer();
        CanonicalIntersection intersection = remainingRayWindows.intersectRegion(source, stats);
        if (stats != null && side) {
            stats.addSideRegionIntersectNanos(regionIntersectStartNanos);
        }

        long canonicalNormalizeStartNanos = stats == null ? 0L : stats.startTimer();
        CanonicalRegion visibleRegion = intersection.region();
        int beforeMerge = intersection.rawFragments();
        if (!sameDepthWindows.isEmpty() && !visibleRegion.isEmpty()) {
            CanonicalRegion sameDepthUnion = CanonicalRegion.fromRects(sameDepthWindows);
            SlabTransform difference = CanonicalRegion.subtractRegionSlabsMeasured(
                    visibleRegion.slabs,
                    sameDepthUnion.slabs,
                    null
            );
            visibleRegion = CanonicalRegion.fromNormalizedSlabs(difference.slabs());
            beforeMerge = difference.rawFragments();
        }
        List<CanonicalRect> visible = visibleRegion.toRects();

        if (stats != null && side) {
            stats.addSideCanonicalNormalizeNanos(canonicalNormalizeStartNanos);
            stats.recordSideVisibleWindowMerge(beforeMerge, visible.size());
            if (stats.structuralValidationEnabled()) {
                List<CanonicalRect> legacyVisible = remainingRayWindows.intersect(source, null);
                if (!sameDepthWindows.isEmpty() && !legacyVisible.isEmpty()) {
                    legacyVisible = subtractOccupied(legacyVisible, sameDepthWindows, null);
                }
                stats.recordSideCanonicalComparison(
                        CanonicalRegion.fromRects(legacyVisible),
                        visibleRegion
                );
            }
        }

        if (stats != null) {
            stats.addSubtractNanos(intersectionStartNanos, side);
            stats.maxVisibleFragments = Math.max(stats.maxVisibleFragments, visible.size());
        }
        return visible.isEmpty() ? List.of() : visible;
    }

    private static List<ProjectionRect> visibleSubRectsFromRayWindows(
            double radius,
            int tileU,
            int tileV,
            List<CanonicalRect> visibleRayWindows,
            ProjectionStatsBuilder stats
    ) {

        if (visibleRayWindows.isEmpty()) {
            return List.of();
        }

        List<ProjectionRect> visibleRects = new ArrayList<>();

        for (CanonicalRect visibleRayWindow : visibleRayWindows) {
            ProjectionRect visibleRect = projectionRect(
                    radius,
                    tileU,
                    tileV,
                    visibleRayWindow.minU(),
                    visibleRayWindow.minV(),
                    visibleRayWindow.maxU(),
                    visibleRayWindow.maxV()
            );

            if (visibleRect != null) {
                visibleRects.add(visibleRect);
            }
        }

        if (stats != null) {
            stats.maxVisibleFragments = Math.max(stats.maxVisibleFragments, visibleRects.size());
        }
        return visibleRects;
    }

    private static double integratedFraction(List<CanonicalRect> rayWindows) {
        double fraction = 0.0D;

        for (CanonicalRect rayWindow : rayWindows) {
            fraction += integratedFraction(rayWindow);
        }

        return Math.max(0.0D, fraction);
    }

    private static double integratedFraction(CanonicalRect rayWindow) {
        return Math.max(0.0D, SpotFootprintKernel.DEFAULT.integral(
                rayWindow.minU(),
                rayWindow.minV(),
                rayWindow.maxU(),
                rayWindow.maxV()
        ));
    }

    private static double densityFraction(CanonicalRect rayWindow) {
        double area = Math.max(
                EDGE_TOUCH_EPSILON * EDGE_TOUCH_EPSILON,
                (rayWindow.maxU() - rayWindow.minU()) * (rayWindow.maxV() - rayWindow.minV())
        );
        double fraction = SpotFootprintKernel.DEFAULT.integral(
                rayWindow.minU(),
                rayWindow.minV(),
                rayWindow.maxU(),
                rayWindow.maxV()
        );

        return Math.max(0.0D, fraction / area);
    }

    private static double canonicalArea(CanonicalRect rayWindow) {
        return Math.max(
                EDGE_TOUCH_EPSILON * EDGE_TOUCH_EPSILON,
                (rayWindow.maxU() - rayWindow.minU()) * (rayWindow.maxV() - rayWindow.minV())
        );
    }

    private static double patchTextureArea(Patch patch) {
        return Math.abs(shoelaceArea(
                patch.t0().u(), patch.t0().v(),
                patch.t1().u(), patch.t1().v(),
                patch.t2().u(), patch.t2().v(),
                patch.t3().u(), patch.t3().v()
        ));
    }

    private static double patchWorldArea(Patch patch) {
        return triangleArea(
                patch.p0(),
                patch.p1(),
                patch.p2()
        ) + triangleArea(
                patch.p0(),
                patch.p2(),
                patch.p3()
        );
    }

    private static double shoelaceArea(
            double x0,
            double y0,
            double x1,
            double y1,
            double x2,
            double y2,
            double x3,
            double y3
    ) {
        return 0.5D * (
                x0 * y1 - y0 * x1
                        + x1 * y2 - y1 * x2
                        + x2 * y3 - y2 * x3
                        + x3 * y0 - y3 * x0
        );
    }

    private static double triangleArea(LocalPoint p0, LocalPoint p1, LocalPoint p2) {
        double ax = p1.x() - p0.x();
        double ay = p1.y() - p0.y();
        double az = p1.z() - p0.z();
        double bx = p2.x() - p0.x();
        double by = p2.y() - p0.y();
        double bz = p2.z() - p0.z();
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        return 0.5D * Math.sqrt(nx * nx + ny * ny + nz * nz);
    }

    private static double canonicalAreaSum(List<CanonicalRect> rayWindows) {
        double area = 0.0D;

        for (CanonicalRect rayWindow : rayWindows) {
            area += canonicalArea(rayWindow);
        }

        return Math.max(0.0D, area);
    }

    private static double occlusionAreaSum(List<OcclusionWindow> windows) {
        double area = 0.0D;

        for (OcclusionWindow window : windows) {
            area += canonicalArea(window.rect());
        }

        return Math.max(0.0D, area);
    }

    private static double projectionRectAreaSum(List<ProjectionRect> rects) {
        double area = 0.0D;

        for (ProjectionRect rect : rects) {
            area += canonicalArea(rect.canonicalRect());
        }

        return Math.max(0.0D, area);
    }

    private static List<OcclusionWindow> intersectingOcclusionWindows(
            CanonicalRect source,
            List<OcclusionWindow> debugWindows
    ) {
        if (debugWindows.isEmpty()) {
            return List.of();
        }

        List<OcclusionWindow> intersections = new ArrayList<>();

        for (OcclusionWindow occupied : debugWindows) {
            if (intersects(source, occupied.rect())) {
                intersections.add(occupied);
            }
        }

        return intersections.isEmpty() ? List.of() : intersections;
    }

    private static List<OcclusionWindow> occlusionWindowsWithPlane(List<OcclusionWindow> windows, String plane) {
        if (windows.isEmpty()) {
            return List.of();
        }

        List<OcclusionWindow> filtered = new ArrayList<>();

        for (OcclusionWindow window : windows) {
            if (matchesPlane(window.plane(), plane)) {
                filtered.add(window);
            }
        }

        return filtered.isEmpty() ? List.of() : filtered;
    }

    private static List<CanonicalRect> canonicalWindowsExcludingPlane(
            List<OcclusionWindow> windows,
            String excludedPlane
    ) {
        if (windows.isEmpty()) {
            return List.of();
        }

        List<CanonicalRect> rects = new ArrayList<>();

        for (OcclusionWindow window : windows) {
            if (!matchesPlane(window.plane(), excludedPlane)) {
                rects.add(window.rect());
            }
        }

        return rects.isEmpty() ? List.of() : rects;
    }

    private static boolean matchesPlane(String planeName, String requestedPlane) {
        return requestedPlane.equals(planeName) || planeName.endsWith("." + requestedPlane);
    }

    private static boolean intersects(CanonicalRect first, CanonicalRect second) {
        double minU = Math.max(first.minU(), second.minU());
        double minV = Math.max(first.minV(), second.minV());
        double maxU = Math.min(first.maxU(), second.maxU());
        double maxV = Math.min(first.maxV(), second.maxV());
        return maxU - minU > EDGE_TOUCH_EPSILON && maxV - minV > EDGE_TOUCH_EPSILON;
    }

    private static List<CanonicalRect> subtractOccupied(CanonicalRect source, List<CanonicalRect> clipWindows) {
        return subtractOccupied(source, clipWindows, null, false);
    }

    private static List<CanonicalRect> subtractOccupied(
            List<CanonicalRect> sources,
            List<CanonicalRect> clipWindows,
            ProjectionStatsBuilder stats
    ) {
        if (sources.isEmpty() || clipWindows.isEmpty()) {
            return sources.isEmpty() ? List.of() : sources;
        }

        List<CanonicalRect> output = new ArrayList<>();

        for (CanonicalRect source : sources) {
            output.addAll(subtractOccupied(source, clipWindows, stats, false));
        }

        return output.isEmpty() ? List.of() : output;
    }

    private static List<CanonicalRect> subtractOccupied(
            CanonicalRect source,
            List<CanonicalRect> clipWindows,
            ProjectionStatsBuilder stats,
            boolean side
    ) {
        long subtractionStartNanos = stats == null ? 0L : stats.startTimer();
        recordSubtractionStats(stats, side, clipWindows.size());
        List<CanonicalRect> intersectingWindows = new ArrayList<>();

        for (CanonicalRect occupied : clipWindows) {
            if (stats != null) {
                stats.occupiedWindowTests++;
            }

            if (!intersects(source, occupied)) {
                continue;
            }

            if (stats != null) {
                stats.occupiedWindowHits++;
            }
            intersectingWindows.add(occupied);
        }

        if (stats != null) {
            stats.recordIntersectingWindowCount(intersectingWindows.size());
        }
        long applyStartNanos = stats == null ? 0L : stats.startTimer();
        List<CanonicalRect> result = subtractIntersectingOccupied(source, intersectingWindows, stats);
        if (stats != null) {
            stats.addSubtractApplyNanos(applyStartNanos);
            stats.addSubtractNanos(subtractionStartNanos, side);
        }
        return result;
    }

    private static void recordSubtractionStats(ProjectionStatsBuilder stats, boolean side, int occupiedWindowCount) {
        if (stats == null) {
            return;
        }

        if (side) {
            stats.sideSubtractions++;
        } else {
            stats.frontSubtractions++;
        }
        stats.maxOccupiedWindows = Math.max(stats.maxOccupiedWindows, occupiedWindowCount);
    }

    private static List<CanonicalRect> subtractIntersectingOccupied(
            CanonicalRect source,
            List<CanonicalRect> intersectingWindows,
            ProjectionStatsBuilder stats
    ) {
        if (intersectingWindows.isEmpty()) {
            return List.of(source);
        }

        List<CanonicalRect> remaining = new ArrayList<>();
        remaining.add(source);

        for (CanonicalRect occupied : intersectingWindows) {
            if (remaining.isEmpty()) {
                if (stats != null) {
                    stats.subtractEmptyResults++;
                }
                return List.of();
            }

            List<CanonicalRect> next = new ArrayList<>();

            for (CanonicalRect candidate : remaining) {
                if (stats != null) {
                    stats.subtractApplySteps++;
                }
                if (intersects(candidate, occupied)) {
                    if (stats != null) {
                        stats.subtractSplitSteps++;
                    }
                    subtract(candidate, occupied, next);
                } else {
                    next.add(candidate);
                }
            }

            remaining = next;
            if (stats != null) {
                stats.maxVisibleFragments = Math.max(stats.maxVisibleFragments, remaining.size());
            }
        }

        if (remaining.isEmpty() && stats != null) {
            stats.subtractEmptyResults++;
        }
        return remaining.isEmpty() ? List.of() : remaining;
    }

    private static void subtract(CanonicalRect source, CanonicalRect occupied, List<CanonicalRect> output) {
        double minU = Math.max(source.minU(), occupied.minU());
        double minV = Math.max(source.minV(), occupied.minV());
        double maxU = Math.min(source.maxU(), occupied.maxU());
        double maxV = Math.min(source.maxV(), occupied.maxV());

        if (maxU - minU <= EDGE_TOUCH_EPSILON || maxV - minV <= EDGE_TOUCH_EPSILON) {
            output.add(source);
            return;
        }

        addCanonicalRect(output, source.minU(), source.minV(), minU, source.maxV());
        addCanonicalRect(output, maxU, source.minV(), source.maxU(), source.maxV());
        addCanonicalRect(output, minU, source.minV(), maxU, minV);
        addCanonicalRect(output, minU, maxV, maxU, source.maxV());
    }

    private static void addCanonicalRect(
            List<CanonicalRect> output,
            double minU,
            double minV,
            double maxU,
            double maxV
    ) {
        if (maxU - minU > EDGE_TOUCH_EPSILON && maxV - minV > EDGE_TOUCH_EPSILON) {
            output.add(new CanonicalRect(minU, minV, maxU, maxV));
        }
    }

    private record CanonicalIntersection(CanonicalRegion region, int rawFragments) {
        private CanonicalIntersection {
            rawFragments = Math.max(0, rawFragments);
        }
    }

    private record SlabTransform(List<Slab> slabs, int rawFragments) {
        private SlabTransform {
            slabs = slabs.isEmpty() ? List.of() : slabs;
            rawFragments = Math.max(0, rawFragments);
        }
    }

    private static final class CanonicalRegion {
        private List<Slab> slabs;
        private CanonicalRect bounds;

        private CanonicalRegion(List<Slab> slabs) {
            this(slabs, false);
        }

        private CanonicalRegion(List<Slab> slabs, boolean normalized) {
            this.slabs = normalized
                    ? (slabs.isEmpty() ? List.of() : slabs)
                    : normalizeSlabs(slabs);
            this.bounds = boundsOf(this.slabs);
        }

        private static CanonicalRegion fromNormalizedSlabs(List<Slab> slabs) {
            return new CanonicalRegion(slabs, true);
        }

        private static CanonicalRegion full() {
            return new CanonicalRegion(List.of(new Slab(
                    0.0D,
                    1.0D,
                    List.of(new Interval(0.0D, 1.0D))
            )));
        }

        private static CanonicalRegion fromRects(List<CanonicalRect> rects) {
            if (rects.isEmpty()) {
                return new CanonicalRegion(List.of());
            }

            List<Double> edges = new ArrayList<>(rects.size() * 2);

            for (CanonicalRect rect : rects) {
                edges.add(rect.minV());
                edges.add(rect.maxV());
            }

            edges.sort(Double::compare);
            List<Double> uniqueEdges = new ArrayList<>(edges.size());

            for (double edge : edges) {
                if (uniqueEdges.isEmpty()
                        || Math.abs(uniqueEdges.get(uniqueEdges.size() - 1) - edge) > EDGE_TOUCH_EPSILON) {
                    uniqueEdges.add(edge);
                }
            }

            if (uniqueEdges.size() < 2) {
                return new CanonicalRegion(List.of());
            }

            List<Slab> slabs = new ArrayList<>();

            for (int index = 0; index < uniqueEdges.size() - 1; index++) {
                double minV = uniqueEdges.get(index);
                double maxV = uniqueEdges.get(index + 1);

                if (maxV - minV <= EDGE_TOUCH_EPSILON) {
                    continue;
                }

                List<Interval> intervals = new ArrayList<>();

                for (CanonicalRect rect : rects) {
                    if (rect.maxV() <= minV + EDGE_TOUCH_EPSILON
                            || rect.minV() >= maxV - EDGE_TOUCH_EPSILON) {
                        continue;
                    }

                    intervals.add(new Interval(rect.minU(), rect.maxU()));
                }

                intervals = mergeIntervals(intervals);

                if (!intervals.isEmpty()) {
                    appendSlab(slabs, new Slab(minV, maxV, intervals));
                }
            }

            return new CanonicalRegion(slabs);
        }

        private List<CanonicalRect> toRects() {
            if (slabs.isEmpty()) {
                return List.of();
            }

            List<CanonicalRect> rects = new ArrayList<>();

            for (Slab slab : slabs) {
                for (Interval interval : slab.intervals()) {
                    addCanonicalRect(rects, interval.minU(), slab.minV(), interval.maxU(), slab.maxV());
                }
            }

            return rects.isEmpty() ? List.of() : rects;
        }

        private boolean isEmpty() {
            return slabs.isEmpty();
        }

        private int slabCount() {
            return slabs.size();
        }

        private int intervalCount() {
            int count = 0;

            for (Slab slab : slabs) {
                count += slab.intervals().size();
            }

            return count;
        }

        private double area() {
            double area = 0.0D;

            for (Slab slab : slabs) {
                double height = Math.max(0.0D, slab.maxV() - slab.minV());

                for (Interval interval : slab.intervals()) {
                    area += Math.max(0.0D, interval.maxU() - interval.minU()) * height;
                }
            }

            return Math.max(0.0D, area);
        }

        private CanonicalRect bounds() {
            return bounds;
        }

        private CanonicalIntersection intersectRegion(CanonicalRect source, ProjectionStatsBuilder stats) {
            if (slabs.isEmpty() || !intersects(source, bounds)) {
                if (stats != null) {
                    stats.recordRemainingIntersection(0L, 0L, 0);
                }
                return new CanonicalIntersection(fromNormalizedSlabs(List.of()), 0);
            }

            List<Slab> output = new ArrayList<>();
            long slabTests = 0L;
            long intervalTests = 0L;
            int rawFragments = 0;

            for (int slabIndex = firstCandidateSlabIndex(source.minV()); slabIndex < slabs.size(); slabIndex++) {
                Slab slab = slabs.get(slabIndex);

                if (slab.minV() >= source.maxV() - EDGE_TOUCH_EPSILON) {
                    break;
                }

                slabTests++;
                double minV = Math.max(source.minV(), slab.minV());
                double maxV = Math.min(source.maxV(), slab.maxV());

                if (maxV - minV <= EDGE_TOUCH_EPSILON) {
                    continue;
                }

                List<Interval> clippedIntervals = new ArrayList<>();
                List<Interval> intervals = slab.intervals();
                for (int intervalIndex = firstCandidateIntervalIndex(intervals, source.minU()); intervalIndex < intervals.size(); intervalIndex++) {
                    Interval interval = intervals.get(intervalIndex);

                    if (interval.minU() >= source.maxU() - EDGE_TOUCH_EPSILON) {
                        break;
                    }

                    intervalTests++;
                    double minU = Math.max(source.minU(), interval.minU());
                    double maxU = Math.min(source.maxU(), interval.maxU());

                    if (maxU - minU > EDGE_TOUCH_EPSILON) {
                        clippedIntervals.add(new Interval(minU, maxU));
                    }
                }

                if (!clippedIntervals.isEmpty()) {
                    rawFragments += clippedIntervals.size();
                    appendSlab(output, new Slab(minV, maxV, clippedIntervals));
                }
            }

            if (stats != null) {
                stats.recordRemainingIntersection(slabTests, intervalTests, rawFragments);
            }
            return new CanonicalIntersection(fromNormalizedSlabs(output), rawFragments);
        }

        private List<CanonicalRect> intersect(CanonicalRect source, ProjectionStatsBuilder stats) {
            if (slabs.isEmpty() || !intersects(source, bounds)) {
                if (stats != null) {
                    stats.recordRemainingIntersection(0L, 0L, 0);
                }
                return List.of();
            }

            List<CanonicalRect> output = new ArrayList<>();
            long slabTests = 0L;
            long intervalTests = 0L;

            for (int slabIndex = firstCandidateSlabIndex(source.minV()); slabIndex < slabs.size(); slabIndex++) {
                Slab slab = slabs.get(slabIndex);

                if (slab.minV() >= source.maxV() - EDGE_TOUCH_EPSILON) {
                    break;
                }

                slabTests++;
                double minV = Math.max(source.minV(), slab.minV());
                double maxV = Math.min(source.maxV(), slab.maxV());

                if (maxV - minV <= EDGE_TOUCH_EPSILON) {
                    continue;
                }

                List<Interval> intervals = slab.intervals();
                for (int intervalIndex = firstCandidateIntervalIndex(intervals, source.minU()); intervalIndex < intervals.size(); intervalIndex++) {
                    Interval interval = intervals.get(intervalIndex);

                    if (interval.minU() >= source.maxU() - EDGE_TOUCH_EPSILON) {
                        break;
                    }

                    intervalTests++;
                    double minU = Math.max(source.minU(), interval.minU());
                    double maxU = Math.min(source.maxU(), interval.maxU());

                    if (maxU - minU > EDGE_TOUCH_EPSILON) {
                        output.add(new CanonicalRect(minU, minV, maxU, maxV));
                    }
                }
            }

            if (stats != null) {
                stats.recordRemainingIntersection(slabTests, intervalTests, output.size());
            }
            return output.isEmpty() ? List.of() : output;
        }

        private boolean intersectsForPlanePrefilter(CanonicalRect source, ProjectionStatsBuilder stats) {
            if (slabs.isEmpty() || !intersects(source, bounds)) {
                if (stats != null) {
                    stats.recordRemainingPrefilter(0L, 0L, false);
                }
                return false;
            }

            long slabTests = 0L;
            long intervalTests = 0L;

            for (int slabIndex = firstCandidateSlabIndex(source.minV()); slabIndex < slabs.size(); slabIndex++) {
                Slab slab = slabs.get(slabIndex);

                if (slab.minV() >= source.maxV() - EDGE_TOUCH_EPSILON) {
                    break;
                }

                slabTests++;
                double minV = Math.max(source.minV(), slab.minV());
                double maxV = Math.min(source.maxV(), slab.maxV());

                if (maxV - minV <= EDGE_TOUCH_EPSILON) {
                    continue;
                }

                List<Interval> intervals = slab.intervals();
                for (int intervalIndex = firstCandidateIntervalIndex(intervals, source.minU()); intervalIndex < intervals.size(); intervalIndex++) {
                    Interval interval = intervals.get(intervalIndex);

                    if (interval.minU() >= source.maxU() - EDGE_TOUCH_EPSILON) {
                        break;
                    }

                    intervalTests++;

                    if (Math.min(source.maxU(), interval.maxU()) - Math.max(source.minU(), interval.minU()) > EDGE_TOUCH_EPSILON) {
                        if (stats != null) {
                            stats.recordRemainingPrefilter(slabTests, intervalTests, true);
                        }
                        return true;
                    }
                }
            }

            if (stats != null) {
                stats.recordRemainingPrefilter(slabTests, intervalTests, false);
            }
            return false;
        }

        private void subtractUnion(List<OcclusionWindow> blockers, ProjectionStatsBuilder stats) {
            subtractUnion(blockers, stats, -1);
        }

        private void subtractUnion(
                List<OcclusionWindow> blockers,
                ProjectionStatsBuilder stats,
                int depth
        ) {
            if (slabs.isEmpty() || blockers.isEmpty()) {
                if (stats != null) {
                    stats.recordRemainingRegion(this);
                }
                return;
            }

            if (stats != null) {
                stats.remainingUnionInputRects += blockers.size();
            }

            long applyStartNanos = stats == null ? 0L : stats.startTimer();
            List<Slab> current = slabs;
            List<CanonicalRect> blockerRects = new ArrayList<>(blockers.size());

            for (OcclusionWindow blocker : blockers) {
                blockerRects.add(blocker.rect());
            }

            CanonicalRegion blockerUnion = CanonicalRegion.fromRects(blockerRects);

            if (stats != null) {
                stats.remainingUnionMergedRects += blockerUnion.intervalCount();
            }

            List<Slab> legacyResult = null;
            if (stats != null && stats.structuralValidationEnabled()) {
                legacyResult = subtractRegionSlabsLegacy(current, blockerUnion.slabs);
            }

            current = subtractRegionSlabs(current, blockerUnion.slabs, stats);

            if (legacyResult != null) {
                stats.recordRemainingSubtractComparison(depth, current, legacyResult);
            }

            setSlabs(current);
            if (stats != null) {
                stats.addRemainingUnionNanos(applyStartNanos);
                stats.recordRemainingRegion(this);
            }
        }

        private static List<Slab> subtractRegionSlabs(
                List<Slab> remainingSlabs,
                List<Slab> blockerSlabs,
                ProjectionStatsBuilder stats
        ) {
            return subtractRegionSlabsMeasured(remainingSlabs, blockerSlabs, stats).slabs();
        }

        private static SlabTransform subtractRegionSlabsMeasured(
                List<Slab> remainingSlabs,
                List<Slab> blockerSlabs,
                ProjectionStatsBuilder stats
        ) {
            if (remainingSlabs.isEmpty() || blockerSlabs.isEmpty()) {
                return new SlabTransform(remainingSlabs, rectFragmentCount(remainingSlabs));
            }

            List<Slab> output = new ArrayList<>(remainingSlabs.size());
            int blockerIndex = 0;
            int rawFragments = 0;

            for (Slab remaining : remainingSlabs) {
                double cursorV = remaining.minV();

                while (blockerIndex < blockerSlabs.size()
                        && blockerSlabs.get(blockerIndex).maxV() <= cursorV + EDGE_TOUCH_EPSILON) {
                    blockerIndex++;
                }

                int localBlockerIndex = blockerIndex;
                while (cursorV < remaining.maxV() - EDGE_TOUCH_EPSILON) {
                    if (stats != null) {
                        stats.remainingBlockerSlabSteps++;
                    }

                    if (localBlockerIndex >= blockerSlabs.size()) {
                        rawFragments += appendMeasuredSlab(
                                output,
                                new Slab(cursorV, remaining.maxV(), remaining.intervals())
                        );
                        break;
                    }

                    Slab blocker = blockerSlabs.get(localBlockerIndex);
                    if (blocker.minV() >= remaining.maxV() - EDGE_TOUCH_EPSILON) {
                        rawFragments += appendMeasuredSlab(
                                output,
                                new Slab(cursorV, remaining.maxV(), remaining.intervals())
                        );
                        break;
                    }

                    if (blocker.maxV() <= cursorV + EDGE_TOUCH_EPSILON) {
                        localBlockerIndex++;
                        continue;
                    }

                    if (blocker.minV() > cursorV + EDGE_TOUCH_EPSILON) {
                        double uncoveredMaxV = Math.min(remaining.maxV(), blocker.minV());
                        rawFragments += appendMeasuredSlab(
                                output,
                                new Slab(cursorV, uncoveredMaxV, remaining.intervals())
                        );
                        cursorV = uncoveredMaxV;
                        continue;
                    }

                    double overlapMaxV = Math.min(remaining.maxV(), blocker.maxV());
                    if (stats != null) {
                        stats.remainingBlockerTests++;
                    }
                    boolean intersects = intersectsAnyInterval(
                            remaining.intervals(),
                            blocker.intervals(),
                            stats
                    );
                    if (intersects) {
                        List<Interval> clippedIntervals = subtractIntervals(
                                remaining.intervals(),
                                blocker.intervals(),
                                stats
                        );
                        if (!clippedIntervals.isEmpty()) {
                            rawFragments += appendMeasuredSlab(
                                    output,
                                    new Slab(cursorV, overlapMaxV, clippedIntervals)
                            );
                        } else if (stats != null) {
                            stats.remainingEmptyResults++;
                        }
                        if (stats != null) {
                            stats.remainingBlockerHits++;
                            stats.remainingClippedBlockers++;
                        }
                    } else {
                        rawFragments += appendMeasuredSlab(
                                output,
                                new Slab(cursorV, overlapMaxV, remaining.intervals())
                        );
                    }

                    cursorV = overlapMaxV;
                    if (blocker.maxV() <= cursorV + EDGE_TOUCH_EPSILON) {
                        localBlockerIndex++;
                    }
                }

                blockerIndex = Math.max(blockerIndex, localBlockerIndex);
            }

            return new SlabTransform(output.isEmpty() ? List.of() : output, rawFragments);
        }

        private static int appendMeasuredSlab(List<Slab> output, Slab slab) {
            if (slab.intervals().isEmpty() || slab.maxV() - slab.minV() <= EDGE_TOUCH_EPSILON) {
                return 0;
            }
            int fragments = slab.intervals().size();
            appendSlab(output, slab);
            return fragments;
        }

        private static int rectFragmentCount(List<Slab> slabs) {
            int count = 0;
            for (Slab slab : slabs) {
                count += slab.intervals().size();
            }
            return count;
        }

        private static List<Slab> subtractRegionSlabsLegacy(
                List<Slab> remainingSlabs,
                List<Slab> blockerSlabs
        ) {
            List<Slab> current = remainingSlabs;
            for (Slab blocker : blockerSlabs) {
                if (current.isEmpty()) {
                    break;
                }
                current = subtractBlockerSlabFromRemaining(current, blocker, null);
            }
            return current;
        }

        private static List<Slab> subtractBlockerSlabFromRemaining(
                List<Slab> remainingSlabs,
                Slab blocker,
                ProjectionStatsBuilder stats
        ) {
            if (remainingSlabs.isEmpty()) {
                return remainingSlabs;
            }

            List<Slab> output = new ArrayList<>();
            boolean hit = false;

            for (Slab remaining : remainingSlabs) {
                if (stats != null) {
                    stats.remainingBlockerSlabSteps++;
                }

                double minV = Math.max(remaining.minV(), blocker.minV());
                double maxV = Math.min(remaining.maxV(), blocker.maxV());

                if (maxV - minV <= EDGE_TOUCH_EPSILON) {
                    appendSlab(output, remaining);
                    continue;
                }

                if (!intersectsAnyInterval(remaining.intervals(), blocker.intervals(), stats)) {
                    appendSlab(output, remaining);
                    continue;
                }

                hit = true;

                if (minV - remaining.minV() > EDGE_TOUCH_EPSILON) {
                    appendSlab(output, new Slab(remaining.minV(), minV, remaining.intervals()));
                }

                List<Interval> clippedIntervals = subtractIntervals(
                        remaining.intervals(),
                        blocker.intervals(),
                        stats
                );

                if (!clippedIntervals.isEmpty()) {
                    appendSlab(output, new Slab(minV, maxV, clippedIntervals));
                } else if (stats != null) {
                    stats.remainingEmptyResults++;
                }

                if (remaining.maxV() - maxV > EDGE_TOUCH_EPSILON) {
                    appendSlab(output, new Slab(maxV, remaining.maxV(), remaining.intervals()));
                }
            }

            if (stats != null && hit) {
                stats.remainingBlockerHits++;
                stats.remainingClippedBlockers++;
            }
            return output.isEmpty() ? List.of() : output;
        }

        private static boolean intersectsAnyInterval(
                List<Interval> intervals,
                double minU,
                double maxU,
                ProjectionStatsBuilder stats
        ) {
            for (int intervalIndex = firstCandidateIntervalIndex(intervals, minU); intervalIndex < intervals.size(); intervalIndex++) {
                Interval interval = intervals.get(intervalIndex);

                if (interval.minU() >= maxU - EDGE_TOUCH_EPSILON) {
                    break;
                }

                if (stats != null) {
                    stats.remainingIntervalClipTests++;
                }

                if (Math.min(maxU, interval.maxU()) - Math.max(minU, interval.minU()) > EDGE_TOUCH_EPSILON) {
                    return true;
                }
            }

            return false;
        }

        private static boolean intersectsAnyInterval(
                List<Interval> remainingIntervals,
                List<Interval> blockerIntervals,
                ProjectionStatsBuilder stats
        ) {
            if (remainingIntervals.isEmpty() || blockerIntervals.isEmpty()) {
                return false;
            }

            int blockerIndex = 0;

            for (Interval remaining : remainingIntervals) {
                while (blockerIndex < blockerIntervals.size()
                        && blockerIntervals.get(blockerIndex).maxU() <= remaining.minU() + EDGE_TOUCH_EPSILON) {
                    blockerIndex++;
                }

                for (int index = blockerIndex; index < blockerIntervals.size(); index++) {
                    Interval blocker = blockerIntervals.get(index);

                    if (blocker.minU() >= remaining.maxU() - EDGE_TOUCH_EPSILON) {
                        break;
                    }

                    if (stats != null) {
                        stats.remainingIntervalClipTests++;
                    }

                    if (Math.min(remaining.maxU(), blocker.maxU()) - Math.max(remaining.minU(), blocker.minU()) > EDGE_TOUCH_EPSILON) {
                        return true;
                    }
                }
            }

            return false;
        }

        private void setSlabs(List<Slab> slabs) {
            this.slabs = slabs.isEmpty() ? List.of() : slabs;
            this.bounds = boundsOf(this.slabs);
        }

        private int firstCandidateSlabIndex(double minV) {
            int low = 0;
            int high = slabs.size();
            double threshold = minV + EDGE_TOUCH_EPSILON;

            while (low < high) {
                int middle = (low + high) >>> 1;
                if (slabs.get(middle).maxV() <= threshold) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }

            return low;
        }

        private static int firstCandidateIntervalIndex(List<Interval> intervals, double minU) {
            int low = 0;
            int high = intervals.size();
            double threshold = minU + EDGE_TOUCH_EPSILON;

            while (low < high) {
                int middle = (low + high) >>> 1;
                if (intervals.get(middle).maxU() <= threshold) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }

            return low;
        }

        private static boolean intersects(CanonicalRect left, CanonicalRect right) {
            return left != null
                    && right != null
                    && Math.min(left.maxU(), right.maxU()) - Math.max(left.minU(), right.minU()) > EDGE_TOUCH_EPSILON
                    && Math.min(left.maxV(), right.maxV()) - Math.max(left.minV(), right.minV()) > EDGE_TOUCH_EPSILON;
        }

        private static CanonicalRect boundsOf(List<Slab> slabs) {
            if (slabs.isEmpty()) {
                return null;
            }

            double minU = 1.0D;
            double minV = 1.0D;
            double maxU = 0.0D;
            double maxV = 0.0D;

            for (Slab slab : slabs) {
                if (slab.intervals().isEmpty()) {
                    continue;
                }

                minV = Math.min(minV, slab.minV());
                maxV = Math.max(maxV, slab.maxV());

                for (Interval interval : slab.intervals()) {
                    minU = Math.min(minU, interval.minU());
                    maxU = Math.max(maxU, interval.maxU());
                }
            }

            return canonicalRectOrNull(minU, minV, maxU, maxV);
        }

        private static void appendSlab(List<Slab> slabs, Slab slab) {
            if (slab.intervals().isEmpty() || slab.maxV() - slab.minV() <= EDGE_TOUCH_EPSILON) {
                return;
            }

            if (!slabs.isEmpty()) {
                Slab previous = slabs.get(slabs.size() - 1);

                if (Math.abs(previous.maxV() - slab.minV()) <= EDGE_TOUCH_EPSILON
                        && sameIntervals(previous.intervals(), slab.intervals())) {
                    slabs.set(slabs.size() - 1, new Slab(previous.minV(), slab.maxV(), previous.intervals()));
                    return;
                }
            }

            slabs.add(slab);
        }

        private static List<Interval> subtractIntervals(
                List<Interval> remainingIntervals,
                List<Interval> blockerIntervals,
                ProjectionStatsBuilder stats
        ) {
            if (remainingIntervals.isEmpty() || blockerIntervals.isEmpty()) {
                return remainingIntervals;
            }

            List<Interval> output = new ArrayList<>();

            for (Interval remaining : remainingIntervals) {
                if (stats != null) {
                    stats.remainingApplySteps++;
                }

                double cursor = remaining.minU();

                for (Interval blocker : blockerIntervals) {
                    if (blocker.maxU() <= cursor + EDGE_TOUCH_EPSILON) {
                        continue;
                    }

                    if (blocker.minU() >= remaining.maxU() - EDGE_TOUCH_EPSILON) {
                        break;
                    }

                    if (stats != null) {
                        stats.remainingSplitSteps++;
                    }

                    if (blocker.minU() > cursor + EDGE_TOUCH_EPSILON) {
                        output.add(new Interval(cursor, Math.min(blocker.minU(), remaining.maxU())));
                    }

                    cursor = Math.max(cursor, blocker.maxU());

                    if (cursor >= remaining.maxU() - EDGE_TOUCH_EPSILON) {
                        break;
                    }
                }

                if (cursor < remaining.maxU() - EDGE_TOUCH_EPSILON) {
                    output.add(new Interval(cursor, remaining.maxU()));
                }
            }

            return output.isEmpty() ? List.of() : output;
        }

        private static List<Interval> mergeIntervals(List<Interval> intervals) {
            if (intervals.isEmpty()) {
                return List.of();
            }

            List<Interval> sorted = new ArrayList<>(intervals);
            sorted.sort((first, second) -> {
                int compare = Double.compare(first.minU(), second.minU());
                return compare != 0 ? compare : Double.compare(first.maxU(), second.maxU());
            });

            List<Interval> merged = new ArrayList<>();
            double minU = sorted.get(0).minU();
            double maxU = sorted.get(0).maxU();

            for (int index = 1; index < sorted.size(); index++) {
                Interval interval = sorted.get(index);

                if (interval.minU() <= maxU + EDGE_TOUCH_EPSILON) {
                    maxU = Math.max(maxU, interval.maxU());
                } else {
                    merged.add(new Interval(minU, maxU));
                    minU = interval.minU();
                    maxU = interval.maxU();
                }
            }

            merged.add(new Interval(minU, maxU));
            return merged;
        }

        private static List<Slab> normalizeSlabs(List<Slab> slabs) {
            if (slabs.isEmpty()) {
                return List.of();
            }

            List<Slab> sorted = new ArrayList<>(slabs);
            sorted.sort((first, second) -> {
                int compare = Double.compare(first.minV(), second.minV());
                return compare != 0 ? compare : Double.compare(first.maxV(), second.maxV());
            });

            List<Slab> normalized = new ArrayList<>();

            for (Slab slab : sorted) {
                if (slab.maxV() - slab.minV() <= EDGE_TOUCH_EPSILON || slab.intervals().isEmpty()) {
                    continue;
                }

                List<Interval> intervals = mergeIntervals(new ArrayList<>(slab.intervals()));
                if (intervals.isEmpty()) {
                    continue;
                }

                if (!normalized.isEmpty()) {
                    Slab previous = normalized.get(normalized.size() - 1);

                    if (Math.abs(previous.maxV() - slab.minV()) <= EDGE_TOUCH_EPSILON
                            && sameIntervals(previous.intervals(), intervals)) {
                        normalized.set(
                                normalized.size() - 1,
                                new Slab(previous.minV(), slab.maxV(), previous.intervals())
                        );
                        continue;
                    }
                }

                normalized.add(new Slab(slab.minV(), slab.maxV(), intervals));
            }

            return normalized.isEmpty() ? List.of() : normalized;
        }

        private static boolean sameIntervals(List<Interval> first, List<Interval> second) {
            if (first.size() != second.size()) {
                return false;
            }

            for (int index = 0; index < first.size(); index++) {
                Interval a = first.get(index);
                Interval b = second.get(index);

                if (Math.abs(a.minU() - b.minU()) > EDGE_TOUCH_EPSILON
                        || Math.abs(a.maxU() - b.maxU()) > EDGE_TOUCH_EPSILON) {
                    return false;
                }
            }

            return true;
        }

        private static boolean sameSlabs(List<Slab> first, List<Slab> second) {
            if (first.size() != second.size()) {
                return false;
            }

            for (int index = 0; index < first.size(); index++) {
                Slab a = first.get(index);
                Slab b = second.get(index);
                if (Math.abs(a.minV() - b.minV()) > EDGE_TOUCH_EPSILON
                        || Math.abs(a.maxV() - b.maxV()) > EDGE_TOUCH_EPSILON
                        || !sameIntervals(a.intervals(), b.intervals())) {
                    return false;
                }
            }
            return true;
        }

        private static double areaOfSlabs(List<Slab> slabs) {
            double area = 0.0D;
            for (Slab slab : slabs) {
                double height = slab.maxV() - slab.minV();
                for (Interval interval : slab.intervals()) {
                    area += (interval.maxU() - interval.minU()) * height;
                }
            }
            return Math.max(0.0D, area);
        }

        private static List<Double> uniqueSortedCuts(List<Double> cuts) {
            cuts.sort(Double::compare);
            List<Double> unique = new ArrayList<>();

            for (double cut : cuts) {
                double clamped = clamp01(cut);

                if (unique.isEmpty() || Math.abs(unique.get(unique.size() - 1) - clamped) > EDGE_TOUCH_EPSILON) {
                    unique.add(clamped);
                }
            }

            return unique;
        }
    }

    private record Slab(double minV, double maxV, List<Interval> intervals) {
        private Slab {
            minV = clamp01(minV);
            maxV = clamp01(maxV);
            intervals = List.copyOf(intervals);

            if (maxV - minV <= EDGE_TOUCH_EPSILON) {
                throw new IllegalArgumentException("Canonical slab must have positive height");
            }
        }
    }

    private record Interval(double minU, double maxU) {
        private Interval {
            minU = clamp01(minU);
            maxU = clamp01(maxU);

            if (maxU - minU <= EDGE_TOUCH_EPSILON) {
                throw new IllegalArgumentException("Canonical interval must have positive width");
            }
        }
    }

    private static int quantizeMin(double value) {
        return Math.max(0, Math.min(254, (int) Math.floor(clamp01(value) * 255.0D)));
    }

    private static int quantizeMax(double value) {
        return Math.max(1, Math.min(255, (int) Math.ceil(clamp01(value) * 255.0D)));
    }

    private static int quantizeQuadUnit(double value) {
        return Math.max(
                0,
                Math.min(
                        SpotRecord.QUAD_QUANTIZATION_LEVEL,
                        (int) Math.round(clamp01(value) * SpotRecord.QUAD_QUANTIZATION_LEVEL)
                )
        );
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private record ModelShapeFrameKey(
            BlockState state,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection
    ) {
    }

    private record OpticalCollisionBox(
            double minTravel,
            double maxTravel,
            double minU,
            double maxU,
            double minV,
            double maxV
    ) {
        private OpticalCollisionBox {
            minTravel = clamp01(minTravel);
            maxTravel = clamp01(maxTravel);
            minU = clamp01(minU);
            maxU = clamp01(maxU);
            minV = clamp01(minV);
            maxV = clamp01(maxV);

            if (maxTravel - minTravel <= EDGE_TOUCH_EPSILON
                    || maxU - minU <= EDGE_TOUCH_EPSILON
                    || maxV - minV <= EDGE_TOUCH_EPSILON) {
                throw new IllegalArgumentException("Optical collision box must have positive volume");
            }
        }

        private static OpticalCollisionBox full() {
            return new OpticalCollisionBox(0.0D, 1.0D, 0.0D, 1.0D, 0.0D, 1.0D);
        }

        private boolean isFullUnit() {
            return minTravel <= EDGE_TOUCH_EPSILON
                    && maxTravel >= 1.0D - EDGE_TOUCH_EPSILON
                    && minU <= EDGE_TOUCH_EPSILON
                    && maxU >= 1.0D - EDGE_TOUCH_EPSILON
                    && minV <= EDGE_TOUCH_EPSILON
                    && maxV >= 1.0D - EDGE_TOUCH_EPSILON;
        }

        private static OpticalCollisionBox fromAabb(
                AABB box,
                Direction travelDirection,
                Direction uDirection,
                Direction vDirection
        ) {
            double minX = clamp01(box.minX);
            double minY = clamp01(box.minY);
            double minZ = clamp01(box.minZ);
            double maxX = clamp01(box.maxX);
            double maxY = clamp01(box.maxY);
            double maxZ = clamp01(box.maxZ);

            double minTravel = frameMin(travelDirection, minX, minY, minZ, maxX, maxY, maxZ);
            double maxTravel = frameMax(travelDirection, minX, minY, minZ, maxX, maxY, maxZ);
            double minU = frameMin(uDirection, minX, minY, minZ, maxX, maxY, maxZ);
            double maxU = frameMax(uDirection, minX, minY, minZ, maxX, maxY, maxZ);
            double minV = frameMin(vDirection, minX, minY, minZ, maxX, maxY, maxZ);
            double maxV = frameMax(vDirection, minX, minY, minZ, maxX, maxY, maxZ);

            if (maxTravel - minTravel <= EDGE_TOUCH_EPSILON
                    || maxU - minU <= EDGE_TOUCH_EPSILON
                    || maxV - minV <= EDGE_TOUCH_EPSILON) {
                return null;
            }

            return new OpticalCollisionBox(minTravel, maxTravel, minU, maxU, minV, maxV);
        }

        private static double frameMin(
                Direction direction,
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ
        ) {
            return Math.min(
                    SpotSurfaceFrame.axisLocal(direction, axisMin(direction, minX, minY, minZ)),
                    SpotSurfaceFrame.axisLocal(direction, axisMax(direction, maxX, maxY, maxZ))
            );
        }

        private static double frameMax(
                Direction direction,
                double minX,
                double minY,
                double minZ,
                double maxX,
                double maxY,
                double maxZ
        ) {
            return Math.max(
                    SpotSurfaceFrame.axisLocal(direction, axisMin(direction, minX, minY, minZ)),
                    SpotSurfaceFrame.axisLocal(direction, axisMax(direction, maxX, maxY, maxZ))
            );
        }

        private static double axisMin(Direction direction, double minX, double minY, double minZ) {
            return switch (direction.getAxis()) {
                case X -> minX;
                case Y -> minY;
                case Z -> minZ;
            };
        }

        private static double axisMax(Direction direction, double maxX, double maxY, double maxZ) {
            return switch (direction.getAxis()) {
                case X -> maxX;
                case Y -> maxY;
                case Z -> maxZ;
            };
        }
    }

    private record TravelInterval(double min, double max) {
        private TravelInterval {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0.0D || max > 1.0D || min >= max) {
                throw new IllegalArgumentException("Side projection travel interval must be inside [0, 1]");
            }
        }
    }

    private record AxisRange(double min, double max) {
    }

    private record SideScanTileBounds(int minU, int maxU, int minV, int maxV, int culledTiles) {
        private boolean empty() {
            return minU > maxU || minV > maxV;
        }

        private boolean contains(int tileU, int tileV) {
            return !empty() && tileU >= minU && tileU <= maxU && tileV >= minV && tileV <= maxV;
        }

        private int tileCount() {
            return tileRangeCount(minU, maxU, minV, maxV);
        }
    }

    private static final class DepthTileCache {
        private final BlockPos depthOrigin;
        private final Direction travelDirection;
        private final Direction uDirection;
        private final Direction vDirection;
        private final int minU;
        private final int maxU;
        private final int minV;
        private final int maxV;
        private final int width;
        private final DepthTile[] tiles;

        private DepthTileCache(
                BlockPos depthOrigin,
                Direction travelDirection,
                Direction uDirection,
                Direction vDirection,
                SideScanTileBounds bounds
        ) {
            this.depthOrigin = depthOrigin;
            this.travelDirection = travelDirection;
            this.uDirection = uDirection;
            this.vDirection = vDirection;
            this.minU = bounds.minU();
            this.maxU = bounds.maxU();
            this.minV = bounds.minV();
            this.maxV = bounds.maxV();
            this.width = bounds.empty() ? 0 : maxU - minU + 1;
            int height = bounds.empty() ? 0 : maxV - minV + 1;
            this.tiles = width <= 0 || height <= 0 ? new DepthTile[0] : new DepthTile[width * height];
        }

        private DepthTile get(int tileU, int tileV) {
            int index = index(tileU, tileV);
            return index < 0 ? null : tiles[index];
        }

        private void put(DepthTile tile) {
            int index = index(tile.tileU(), tile.tileV());
            if (index >= 0) {
                tiles[index] = tile;
            }
        }

        private BlockPos pos(int tileU, int tileV) {
            return depthOrigin
                    .relative(uDirection, tileU)
                    .relative(vDirection, tileV);
        }

        private int index(int tileU, int tileV) {
            if (width <= 0 || tileU < minU || tileU > maxU || tileV < minV || tileV > maxV) {
                return -1;
            }

            return (tileV - minV) * width + (tileU - minU);
        }
    }

    private record DepthTile(
            int tileU,
            int tileV,
            BlockPos pos,
            boolean loaded,
            BlockState state,
            boolean airLike,
            List<OpticalCollisionBox> opticalBoxes
    ) {
        private boolean projectable() {
            return !opticalBoxes.isEmpty();
        }
    }

    private record FrontFaceCandidate(
            DepthTile tile,
            int boxIndex,
            OpticalCollisionBox box,
            BeamEnvelope frontEnvelope
    ) {
    }

    private record DepthCuboidProbe(
            BlockPos pos,
            int depth,
            int tileU,
            int tileV,
            int boxIndex,
            OpticalCollisionBox box
    ) {
    }

    enum FrontPlanePhase {
        BEFORE,
        CONTINUING,
        STARTING,
        AFTER
    }

    private enum SideAxis {
        U,
        V
    }

    private enum ProjectionFaceRole {
        SIDE_U,
        SIDE_V
    }

    private record ReceivingSideRegion(
            CanonicalRect region,
            CanonicalRect exposedRegion,
            int sourceCoverCount,
            double sourceCoverArea
    ) {
        private ReceivingSideRegion {
            Objects.requireNonNull(region, "region");
            Objects.requireNonNull(exposedRegion, "exposedRegion");
            sourceCoverCount = Math.max(0, sourceCoverCount);
            sourceCoverArea = Double.isFinite(sourceCoverArea) ? Math.max(0.0D, sourceCoverArea) : 0.0D;
        }
    }

    private record ProjectionFace(
            ProjectionFaceRole role,
            Direction normal,
            boolean positiveSide,
            double fixedLocal,
            double minTravel,
            double maxTravel,
            double minCross,
            double maxCross
    ) {
        private ProjectionFace {
            fixedLocal = clamp01(fixedLocal);
            minTravel = clamp01(minTravel);
            maxTravel = clamp01(maxTravel);
            minCross = clamp01(minCross);
            maxCross = clamp01(maxCross);

            if (role == null || normal == null) {
                throw new IllegalArgumentException("Projection face must include role and normal");
            }

            if (maxTravel - minTravel <= EDGE_TOUCH_EPSILON
                    || maxCross - minCross <= EDGE_TOUCH_EPSILON) {
                throw new IllegalArgumentException("Projection face must have positive surface area");
            }
        }

        private static ProjectionFace sideU(
                Direction normal,
                boolean positiveSide,
                double fixedLocal,
                double minTravel,
                double maxTravel,
                double minCross,
                double maxCross
        ) {
            return new ProjectionFace(
                    ProjectionFaceRole.SIDE_U,
                    normal,
                    positiveSide,
                    fixedLocal,
                    minTravel,
                    maxTravel,
                    minCross,
                    maxCross
            );
        }

        private static ProjectionFace sideV(
                Direction normal,
                boolean positiveSide,
                double fixedLocal,
                double minTravel,
                double maxTravel,
                double minCross,
                double maxCross
        ) {
            return new ProjectionFace(
                    ProjectionFaceRole.SIDE_V,
                    normal,
                    positiveSide,
                    fixedLocal,
                    minTravel,
                    maxTravel,
                    minCross,
                    maxCross
            );
        }
    }

    private record SideFaceCandidate(
            SideAxis axis,
            DepthTile tile,
            int boxIndex,
            Direction sideFace,
            double boundaryWorld,
            double fixedLocal,
            int crossTile,
            double entryTravel,
            double exitTravel,
            double crossMinLocal,
            double crossMaxLocal,
            List<TravelInterval> visibleTravels,
            ProjectionFace projectionFace,
            String candidateDetail
    ) {
    }

    private record SideCandidateKey(long pos, Direction face) {
    }

    private record ValidationTarget(
            ResourceKey<Level> dimension,
            BlockPos sourcePos,
            Direction travelDirection
    ) {
    }

    private record SurfaceSpotKey(long pos, Direction face) {
    }

    private record ParameterRange(double min, double max) {
        private ParameterRange {
            min = clamp01(min);
            max = clamp01(max);

            if (max - min <= EDGE_TOUCH_EPSILON) {
                throw new IllegalArgumentException("Projection parameter range must have positive length");
            }
        }
    }

    private record CrossClip(
            double minLocal,
            double maxLocal,
            double minCanonical,
            double maxCanonical
    ) {
        private CrossClip {
            minLocal = clamp01(minLocal);
            maxLocal = clamp01(maxLocal);
            minCanonical = clamp01(minCanonical);
            maxCanonical = clamp01(maxCanonical);

            if (minLocal > maxLocal || minCanonical > maxCanonical) {
                throw new IllegalArgumentException("Projection cross clip must be ordered");
            }
        }
    }

    private record SideClipAt(double travelParameter, CrossClip crossClip) {
        private SideClipAt {
            travelParameter = clamp01(travelParameter);

            if (crossClip == null) {
                throw new IllegalArgumentException("Side clip point must include a cross-section clip");
            }
        }
    }

    private enum PatchEmission {
        EMITTED("emitted", true),
        PATCH_NULL("patch_null", false),
        SPOT_INVISIBLE("spot_invisible", false);

        private final String result;
        private final boolean emitted;

        PatchEmission(String result, boolean emitted) {
            this.result = result;
            this.emitted = emitted;
        }

        private String result() {
            return result;
        }

        private boolean emitted() {
            return emitted;
        }
    }

    private record PatchEmissionReport(PatchEmission result, int emittedQuads, String detail) {
        private PatchEmissionReport {
            emittedQuads = Math.max(0, emittedQuads);
            detail = detail == null ? "" : detail;
        }

        private boolean emitted() {
            return emittedQuads > 0;
        }

        private String resultName() {
            return result.result();
        }
    }

    private static final class ProjectionStatsBuilder {
        private final boolean timingEnabled = SpectralizationConfig.opticalCompilerDebugLog();
        private final boolean geometryHealthEnabled = SpectralizationConfig.opticalCompilerDebugLog();
        private final boolean validationEnabled;
        private int depths;
        private long scannedTiles;
        private long candidateTiles;
        private long loadedTiles;
        private long airTiles;
        private long nonProjectableTiles;
        private long projectableTiles;
        private long sideTilesScanned;
        private long sideLoadedTiles;
        private long sideProjectableTiles;
        private long sideOpenChecks;
        private long sideOpenFaces;
        private long sideTravelIntervals;
        private long sideWindowCandidates;
        private long sideFastPathPatches;
        private long sideFastPathSkipped;
        private long sideRangeCulledTiles;
        private long depthTileCacheHits;
        private long depthTileCacheMisses;
        private long sideBoundaryFaceTests;
        private long sideBoundaryProjectableFaces;
        private long sideBoundaryOpenFaces;
        private long sideBoundaryTravelIntervals;
        private long sideBoundaryTravelFaces;
        private long sideLegacyCandidateFaces;
        private long sideBoundaryCandidateFaces;
        private long sideBoundaryMissingFaces;
        private long sideBoundaryExtraFaces;
        private long sideInternalTravelIntervals;
        private long sideExternalTravelIntervals;
        private long sideInternalWindowAttempts;
        private long sideExternalWindowAttempts;
        private long sideInternalDegenerateTravel;
        private long sideExternalDegenerateTravel;
        private long sideInternalNotRenderable;
        private long sideExternalNotRenderable;
        private long sideInternalCrossNull;
        private long sideExternalCrossNull;
        private long sideInternalWindowNull;
        private long sideExternalWindowNull;
        private long sideInternalWindowCandidates;
        private long sideExternalWindowCandidates;
        private long sideInternalVisibleEmpty;
        private long sideExternalVisibleEmpty;
        private long sideInternalLowPower;
        private long sideExternalLowPower;
        private long sideInternalPatchNull;
        private long sideExternalPatchNull;
        private long sideInternalSpotInvisible;
        private long sideExternalSpotInvisible;
        private long sideInternalEmittedQuads;
        private long sideExternalEmittedQuads;
        private long sideInternalTinyTexturePatches;
        private long sideExternalTinyTexturePatches;
        private long sideInternalLargeStretchPatches;
        private long sideExternalLargeStretchPatches;
        private double sideMaxStretchRatio;
        private long planeWindowTests;
        private long planeWindowCandidates;
        private long planeWindowRemainingCulled;
        private long planeWindows;
        private long frontSubtractions;
        private long sideSubtractions;
        private long occupiedWindowTests;
        private long occupiedWindowHits;
        private int maxOccupiedWindows;
        private int maxVisibleFragments;
        private long frontFragmentsBeforeMerge;
        private long frontFragmentsAfterMerge;
        private long sideVisibleWindowsBeforeMerge;
        private long sideVisibleWindowsAfterMerge;
        private long footprintIntegralCalls;
        private long surfaceAppearanceBuilds;
        private long surfaceAppearanceCacheHits;
        private long sideCandidateTilesVisited;
        private final List<String> sideBoundaryMissingExamples = new ArrayList<>();
        private final List<SpotProjectionResult.BoundaryMissingFace> sideBoundaryMissingDetails = new ArrayList<>();
        private long remainingSubtractValidationChecks;
        private long remainingSubtractValidationMismatches;
        private final List<String> structuralValidationExamples = new ArrayList<>();
        private long sameDepthSplitIndexQueries;
        private long sameDepthTravelGroupsVisited;
        private long sameDepthPrefixIndexQueries;
        private long sameDepthPrefixIndexCandidates;
        private long sameDepthPrefixIndexHits;
        private long sameDepthSplitValidationChecks;
        private long sameDepthSplitValidationMismatches;
        private long sameDepthPrefixValidationChecks;
        private long sameDepthPrefixValidationMismatches;
        private long sideCanonicalValidationChecks;
        private long sideCanonicalValidationMismatches;
        private long depthBoundaryRadiusChecks;
        private long depthBoundaryRadiusMismatches;
        private double depthBoundaryRadiusMaxGap;
        private long tileRangeNanos;
        private long projectionRectNanos;
        private long blockLookupNanos;
        private long projectableCheckNanos;
        private long planeWindowNanos;
        private long frontPassNanos;
        private long frontSubtractNanos;
        private long sideScanNanos;
        private long sideCandidateNanos;
        private long sideEmitNanos;
        private long sideTravelSplitNanos;
        private long sideOcclusionIndexBuildNanos;
        private long sideSameDepthSplitNanos;
        private long sideWindowNanos;
        private long sidePrefixQueryNanos;
        private long sideRemainingIntersectNanos;
        private long sideRegionIntersectNanos;
        private long sideCanonicalNormalizeNanos;
        private long sideDebugAuditNanos;
        private long sidePatchEmitNanos;
        private long surfaceAppearanceBuildNanos;
        private long patchClipNanos;
        private long spotRecordPackNanos;
        private long sideCandidateVerifyNanos;
        private long sideSubtractNanos;
        private long indexQueryNanos;
        private long subtractApplyNanos;
        private long frontEmitNanos;
        private long fullOccupancyNanos;
        private long remainingUnionNanos;
        private long remainingSubtractNanos;
        private long occlusionAddNanos;
        private long indexBucketQueries;
        private long indexBucketEntries;
        private long indexDuplicateSkips;
        private long indexLargeWindowTests;
        private long indexLargeWindowHits;
        private int indexMaxQueryBuckets;
        private int indexMaxQueryEntries;
        private int indexMaxLargeWindows;
        private long subtractIntersectingWindows;
        private int subtractMaxIntersectingWindows;
        private long subtractApplySteps;
        private long subtractSplitSteps;
        private long subtractEmptyResults;
        private int remainingSlabs;
        private int maxRemainingSlabs;
        private int remainingIntervals;
        private int maxRemainingIntervals;
        private double remainingArea = 1.0D;
        private double minRemainingArea = 1.0D;
        private long remainingIntersectionQueries;
        private long remainingIntersectionSlabTests;
        private long remainingIntersectionIntervalTests;
        private long remainingVisibleFragments;
        private long remainingPrefilterQueries;
        private long remainingPrefilterHits;
        private long remainingPrefilterSlabTests;
        private long remainingPrefilterIntervalTests;
        private long remainingUnionInputRects;
        private long remainingUnionMergedRects;
        private long remainingBlockerTests;
        private long remainingBlockerHits;
        private long remainingClippedBlockers;
        private long remainingBlockerSlabSteps;
        private long remainingIntervalClipTests;
        private long remainingApplySteps;
        private long remainingSplitSteps;
        private long remainingEmptyResults;
        private int hotDepth;
        private long hotDepthNanos;
        private long hotDepthScannedTiles;
        private long hotDepthCandidateTiles;
        private long hotDepthProjectableTiles;
        private long hotDepthPlaneWindows;
        private long hotDepthFrontSubtractions;
        private long hotDepthSideSubtractions;
        private long hotDepthOccupiedWindowTests;
        private long hotDepthOccupiedWindowHits;
        private int hotDepthSpots;

        private ProjectionStatsBuilder(boolean validationEnabled) {
            this.validationEnabled = validationEnabled;
        }

        private long startTimer() {
            return timingEnabled ? System.nanoTime() : 0L;
        }

        private long elapsedSince(long startNanos) {
            if (!timingEnabled || startNanos <= 0L) {
                return 0L;
            }

            return Math.max(0L, System.nanoTime() - startNanos);
        }

        private void addTileRangeNanos(long startNanos) {
            tileRangeNanos += elapsedSince(startNanos);
        }

        private void addProjectionRectNanos(long startNanos) {
            projectionRectNanos += elapsedSince(startNanos);
        }

        private void addBlockLookupNanos(long startNanos) {
            blockLookupNanos += elapsedSince(startNanos);
        }

        private void addProjectableCheckNanos(long startNanos) {
            projectableCheckNanos += elapsedSince(startNanos);
        }

        private void addPlaneWindowNanos(long startNanos) {
            planeWindowNanos += elapsedSince(startNanos);
        }

        private void addFrontPassNanos(long startNanos) {
            frontPassNanos += elapsedSince(startNanos);
        }

        private void addSideScanNanos(long startNanos) {
            sideScanNanos += elapsedSince(startNanos);
        }

        private void addSideCandidateNanos(long startNanos) {
            sideCandidateNanos += elapsedSince(startNanos);
        }

        private void addSideEmitNanos(long startNanos) {
            sideEmitNanos += elapsedSince(startNanos);
        }

        private void addSideTravelSplitNanos(long startNanos) {
            sideTravelSplitNanos += elapsedSince(startNanos);
        }

        private void addSideOcclusionIndexBuildNanos(long startNanos) {
            sideOcclusionIndexBuildNanos += elapsedSince(startNanos);
        }

        private void addSideSameDepthSplitNanos(long startNanos) {
            sideSameDepthSplitNanos += elapsedSince(startNanos);
        }

        private void addSideWindowNanos(long startNanos) {
            sideWindowNanos += elapsedSince(startNanos);
        }

        private void addSidePrefixQueryNanos(long startNanos) {
            sidePrefixQueryNanos += elapsedSince(startNanos);
        }

        private void addSideRemainingIntersectNanos(long startNanos) {
            sideRemainingIntersectNanos += elapsedSince(startNanos);
        }

        private void addSideRegionIntersectNanos(long startNanos) {
            sideRegionIntersectNanos += elapsedSince(startNanos);
        }

        private void addSideCanonicalNormalizeNanos(long startNanos) {
            sideCanonicalNormalizeNanos += elapsedSince(startNanos);
        }

        private void addSideDebugAuditNanos(long startNanos) {
            sideDebugAuditNanos += elapsedSince(startNanos);
        }

        private void addSidePatchEmitNanos(long startNanos) {
            sidePatchEmitNanos += elapsedSince(startNanos);
        }

        private void addSurfaceAppearanceBuildNanos(long startNanos) {
            surfaceAppearanceBuildNanos += elapsedSince(startNanos);
        }

        private void addPatchClipNanos(long startNanos) {
            patchClipNanos += elapsedSince(startNanos);
        }

        private void addSpotRecordPackNanos(long startNanos) {
            spotRecordPackNanos += elapsedSince(startNanos);
        }

        private void addSideBoundaryVerifyNanos(long startNanos) {
            sideCandidateVerifyNanos += elapsedSince(startNanos);
        }

        private boolean sideCandidateValidationEnabled() {
            return timingEnabled && validationEnabled;
        }

        private boolean structuralValidationEnabled() {
            return timingEnabled && validationEnabled;
        }

        private void recordRemainingSubtractComparison(
                int depth,
                List<Slab> optimized,
                List<Slab> legacy
        ) {
            remainingSubtractValidationChecks++;
            if (CanonicalRegion.sameSlabs(optimized, legacy)) {
                return;
            }

            remainingSubtractValidationMismatches++;
            if (structuralValidationExamples.size() < MAX_STRUCTURAL_VALIDATION_EXAMPLES) {
                structuralValidationExamples.add(String.format(
                        Locale.ROOT,
                        "remaining_subtract depth=%d optimized_slabs=%d legacy_slabs=%d optimized_area=%.9f legacy_area=%.9f",
                        depth,
                        optimized.size(),
                        legacy.size(),
                        CanonicalRegion.areaOfSlabs(optimized),
                        CanonicalRegion.areaOfSlabs(legacy)
                ));
            }
        }

        private void recordSameDepthSplitComparison(
                BlockPos ownerPos,
                int ownerBoxIndex,
                TravelInterval source,
                List<TravelInterval> optimized,
                List<TravelInterval> legacy
        ) {
            sameDepthSplitValidationChecks++;
            if (optimized.equals(legacy)) {
                return;
            }

            sameDepthSplitValidationMismatches++;
            recordStructuralValidationExample(String.format(
                    Locale.ROOT,
                    "same_depth_split pos=%s box=%d source=%s optimized=%s legacy=%s",
                    ownerPos.toShortString(), ownerBoxIndex, source, optimized, legacy
            ));
        }

        private void recordSameDepthPrefixComparison(
                BlockPos ownerPos,
                int ownerBoxIndex,
                double surfaceTravel,
                CanonicalRect candidateWindow,
                List<OcclusionWindow> optimized,
                List<OcclusionWindow> legacy
        ) {
            sameDepthPrefixValidationChecks++;
            boolean same = optimized.size() == legacy.size()
                    && new HashSet<>(optimized).equals(new HashSet<>(legacy));
            if (same) {
                return;
            }

            sameDepthPrefixValidationMismatches++;
            recordStructuralValidationExample(String.format(
                    Locale.ROOT,
                    "same_depth_prefix pos=%s box=%d travel=%.9f window=%s optimized=%d legacy=%d",
                    ownerPos.toShortString(), ownerBoxIndex, surfaceTravel,
                    formatRect(candidateWindow), optimized.size(), legacy.size()
            ));
        }

        private void recordStructuralValidationExample(String example) {
            if (structuralValidationExamples.size() < MAX_STRUCTURAL_VALIDATION_EXAMPLES) {
                structuralValidationExamples.add(example);
            }
        }

        private void addIndexQueryNanos(long startNanos) {
            indexQueryNanos += elapsedSince(startNanos);
        }

        private void addSubtractApplyNanos(long startNanos) {
            subtractApplyNanos += elapsedSince(startNanos);
        }

        private void addSubtractNanos(long startNanos, boolean side) {
            long elapsed = elapsedSince(startNanos);

            if (side) {
                sideSubtractNanos += elapsed;
            } else {
                frontSubtractNanos += elapsed;
            }
        }

        private void addFrontEmitNanos(long startNanos) {
            frontEmitNanos += elapsedSince(startNanos);
        }

        private void addFullOccupancyNanos(long startNanos) {
            fullOccupancyNanos += elapsedSince(startNanos);
        }

        private void addRemainingUnionNanos(long startNanos) {
            remainingUnionNanos += elapsedSince(startNanos);
        }

        private void addRemainingSubtractNanos(long startNanos) {
            remainingSubtractNanos += elapsedSince(startNanos);
        }

        private void addOcclusionAddNanos(long startNanos) {
            occlusionAddNanos += elapsedSince(startNanos);
        }

        private void recordIndexBucketScan(int bucketQueries, long bucketEntries, long duplicateSkips) {
            indexBucketQueries += Math.max(0, bucketQueries);
            indexBucketEntries += Math.max(0L, bucketEntries);
            indexDuplicateSkips += Math.max(0L, duplicateSkips);
            indexMaxQueryBuckets = Math.max(indexMaxQueryBuckets, Math.max(0, bucketQueries));
            indexMaxQueryEntries = Math.max(indexMaxQueryEntries, (int) Math.min(Integer.MAX_VALUE, Math.max(0L, bucketEntries)));
        }

        private void recordIndexLargeWindowScan(int largeWindowCount, long largeWindowTests, long largeWindowHits) {
            indexLargeWindowTests += Math.max(0L, largeWindowTests);
            indexLargeWindowHits += Math.max(0L, largeWindowHits);
            indexMaxLargeWindows = Math.max(indexMaxLargeWindows, Math.max(0, largeWindowCount));
        }

        private void recordIntersectingWindowCount(int count) {
            int safeCount = Math.max(0, count);
            subtractIntersectingWindows += safeCount;
            subtractMaxIntersectingWindows = Math.max(subtractMaxIntersectingWindows, safeCount);
        }

        private void recordRemainingRegion(CanonicalRegion region) {
            remainingSlabs = region.slabCount();
            remainingIntervals = region.intervalCount();
            remainingArea = region.area();
            maxRemainingSlabs = Math.max(maxRemainingSlabs, remainingSlabs);
            maxRemainingIntervals = Math.max(maxRemainingIntervals, remainingIntervals);
            minRemainingArea = Math.min(minRemainingArea, remainingArea);
        }

        private void recordRemainingIntersection(long slabTests, long intervalTests, int visibleFragments) {
            remainingIntersectionQueries++;
            remainingIntersectionSlabTests += Math.max(0L, slabTests);
            remainingIntersectionIntervalTests += Math.max(0L, intervalTests);
            remainingVisibleFragments += Math.max(0, visibleFragments);
        }

        private void recordRemainingPrefilter(long slabTests, long intervalTests, boolean hit) {
            remainingPrefilterQueries++;
            remainingPrefilterSlabTests += Math.max(0L, slabTests);
            remainingPrefilterIntervalTests += Math.max(0L, intervalTests);

            if (hit) {
                remainingPrefilterHits++;
            }
        }

        private void recordFrontFragmentMerge(int before, int after) {
            frontFragmentsBeforeMerge += Math.max(0, before);
            frontFragmentsAfterMerge += Math.max(0, after);
        }

        private void recordSideVisibleWindowMerge(int before, int after) {
            sideVisibleWindowsBeforeMerge += Math.max(0, before);
            sideVisibleWindowsAfterMerge += Math.max(0, after);
        }

        private void recordDepthBoundaryRadius(double previousExit, double nextEntry) {
            depthBoundaryRadiusChecks++;
            double gap = Math.abs(previousExit - nextEntry);
            depthBoundaryRadiusMaxGap = Math.max(depthBoundaryRadiusMaxGap, gap);
            if (Double.doubleToLongBits(previousExit) != Double.doubleToLongBits(nextEntry)) {
                depthBoundaryRadiusMismatches++;
            }
        }

        private void recordSideCanonicalComparison(CanonicalRegion legacy, CanonicalRegion optimized) {
            sideCanonicalValidationChecks++;
            if (CanonicalRegion.sameSlabs(legacy.slabs, optimized.slabs)) {
                return;
            }

            sideCanonicalValidationMismatches++;
            recordStructuralValidationExample(String.format(
                    Locale.ROOT,
                    "side_canonical legacy_area=%.9f optimized_area=%.9f legacy_rects=%d optimized_rects=%d",
                    legacy.area(),
                    optimized.area(),
                    legacy.intervalCount(),
                    optimized.intervalCount()
            ));
        }

        private void recordSideCandidateComparison(
                Level level,
                int depth,
                Set<SideCandidateKey> legacyCandidates,
                Set<SideCandidateKey> boundaryCandidates,
                Map<SideCandidateKey, String> rejectionReasons
        ) {
            sideLegacyCandidateFaces += legacyCandidates.size();

            for (SideCandidateKey legacyCandidate : legacyCandidates) {
                if (!boundaryCandidates.contains(legacyCandidate)) {
                    sideBoundaryMissingFaces++;
                    if (sideBoundaryMissingExamples.size() < MAX_SIDE_BOUNDARY_MISSING_EXAMPLES) {
                        BlockPos pos = BlockPos.of(legacyCandidate.pos());
                        String reason = rejectionReasons.getOrDefault(legacyCandidate, "boundary_no_candidate");
                        BlockState state = level.getBlockState(pos);
                        sideBoundaryMissingExamples.add(String.format(
                                Locale.ROOT,
                                "depth=%d,pos=%s,face=%s,state=%s,reject=%s",
                                depth,
                                pos.toShortString(),
                                legacyCandidate.face().getSerializedName(),
                                state,
                                reason
                        ));
                        sideBoundaryMissingDetails.add(new SpotProjectionResult.BoundaryMissingFace(
                                depth,
                                pos,
                                legacyCandidate.face(),
                                state.toString(),
                                "legacy_block_boundary_present_shape_boundary_absent",
                                reason
                        ));
                    }
                }
            }

            for (SideCandidateKey boundaryCandidate : boundaryCandidates) {
                if (!legacyCandidates.contains(boundaryCandidate)) {
                    sideBoundaryExtraFaces++;
                }
            }
        }

        private void recordSideBoundaryCandidateCount(int count) {
            sideBoundaryCandidateFaces += Math.max(0, count);
        }

        private void recordSideFastPathSkipped() {
            sideFastPathSkipped++;
        }

        private void recordSideTravelIntervals(double fixedLocal, int count) {
            if (count <= 0) {
                return;
            }

            if (isInternalProjectionSide(fixedLocal)) {
                sideInternalTravelIntervals += count;
            } else {
                sideExternalTravelIntervals += count;
            }
        }

        private void recordSideWindowAttempt(boolean internalSide) {
            if (internalSide) {
                sideInternalWindowAttempts++;
            } else {
                sideExternalWindowAttempts++;
            }
        }

        private void recordSideDegenerateTravel(boolean internalSide) {
            if (internalSide) {
                sideInternalDegenerateTravel++;
            } else {
                sideExternalDegenerateTravel++;
            }
        }

        private void recordSideNotRenderable(boolean internalSide) {
            if (internalSide) {
                sideInternalNotRenderable++;
            } else {
                sideExternalNotRenderable++;
            }
        }

        private void recordSideCrossNull(boolean internalSide) {
            if (internalSide) {
                sideInternalCrossNull++;
            } else {
                sideExternalCrossNull++;
            }
        }

        private void recordSideWindowNull(boolean internalSide) {
            if (internalSide) {
                sideInternalWindowNull++;
            } else {
                sideExternalWindowNull++;
            }
        }

        private void recordSideWindowCandidate(boolean internalSide) {
            if (internalSide) {
                sideInternalWindowCandidates++;
            } else {
                sideExternalWindowCandidates++;
            }
        }

        private void recordSideVisibleEmpty(boolean internalSide) {
            if (internalSide) {
                sideInternalVisibleEmpty++;
            } else {
                sideExternalVisibleEmpty++;
            }
        }

        private void recordSideLowPower(boolean internalSide) {
            if (internalSide) {
                sideInternalLowPower++;
            } else {
                sideExternalLowPower++;
            }
        }

        private void recordSidePatchEmission(boolean internalSide, PatchEmissionReport emission) {
            if (emission.result() == PatchEmission.PATCH_NULL) {
                if (internalSide) {
                    sideInternalPatchNull++;
                } else {
                    sideExternalPatchNull++;
                }
            } else if (emission.result() == PatchEmission.SPOT_INVISIBLE) {
                if (internalSide) {
                    sideInternalSpotInvisible++;
                } else {
                    sideExternalSpotInvisible++;
                }
            }

            if (emission.emittedQuads() > 0) {
                if (internalSide) {
                    sideInternalEmittedQuads += emission.emittedQuads();
                } else {
                    sideExternalEmittedQuads += emission.emittedQuads();
                }
            }
        }

        private void recordSidePatchGeometry(boolean internalSide, Patch patch) {
            if (!geometryHealthEnabled) {
                return;
            }

            double textureArea = patchTextureArea(patch);
            double worldArea = patchWorldArea(patch);
            if (!Double.isFinite(textureArea) || !Double.isFinite(worldArea) || worldArea <= 0.0D) {
                return;
            }

            if (textureArea <= SIDE_TINY_TEXTURE_AREA_THRESHOLD) {
                if (internalSide) {
                    sideInternalTinyTexturePatches++;
                } else {
                    sideExternalTinyTexturePatches++;
                }
            }

            double stretchRatio = textureArea <= EDGE_TOUCH_EPSILON * EDGE_TOUCH_EPSILON
                    ? Double.POSITIVE_INFINITY
                    : worldArea / textureArea;
            if (Double.isFinite(stretchRatio)) {
                sideMaxStretchRatio = Math.max(sideMaxStretchRatio, stretchRatio);
            }

            if (stretchRatio >= SIDE_LARGE_STRETCH_RATIO_THRESHOLD) {
                if (internalSide) {
                    sideInternalLargeStretchPatches++;
                } else {
                    sideExternalLargeStretchPatches++;
                }
            }
        }

        private void finishDepth(
                int depth,
                long depthStartNanos,
                long scannedStart,
                long candidateStart,
                long projectableStart,
                long planeWindowStart,
                long frontSubtractionStart,
                long sideSubtractionStart,
                long occupiedTestStart,
                long occupiedHitStart,
                int spotStart,
                int spotEnd
        ) {
            long elapsed = elapsedSince(depthStartNanos);

            if (elapsed <= hotDepthNanos) {
                return;
            }

            hotDepth = depth;
            hotDepthNanos = elapsed;
            hotDepthScannedTiles = Math.max(0L, scannedTiles - scannedStart);
            hotDepthCandidateTiles = Math.max(0L, candidateTiles - candidateStart);
            hotDepthProjectableTiles = Math.max(0L, projectableTiles - projectableStart);
            hotDepthPlaneWindows = Math.max(0L, planeWindows - planeWindowStart);
            hotDepthFrontSubtractions = Math.max(0L, frontSubtractions - frontSubtractionStart);
            hotDepthSideSubtractions = Math.max(0L, sideSubtractions - sideSubtractionStart);
            hotDepthOccupiedWindowTests = Math.max(0L, occupiedWindowTests - occupiedTestStart);
            hotDepthOccupiedWindowHits = Math.max(0L, occupiedWindowHits - occupiedHitStart);
            hotDepthSpots = Math.max(0, spotEnd - spotStart);
        }

        private SpotProjectionResult.Stats toStats() {
            return new SpotProjectionResult.Stats(
                    depths,
                    scannedTiles,
                    candidateTiles,
                    loadedTiles,
                    airTiles,
                    nonProjectableTiles,
                    projectableTiles,
                    sideTilesScanned,
                    sideLoadedTiles,
                    sideProjectableTiles,
                    sideOpenChecks,
                    sideOpenFaces,
                    sideTravelIntervals,
                    sideWindowCandidates,
                    sideFastPathPatches,
                    sideFastPathSkipped,
                    sideRangeCulledTiles,
                    depthTileCacheHits,
                    depthTileCacheMisses,
                    sideBoundaryFaceTests,
                    sideBoundaryProjectableFaces,
                    sideBoundaryOpenFaces,
                    sideBoundaryTravelIntervals,
                    sideBoundaryTravelFaces,
                    sideLegacyCandidateFaces,
                    sideBoundaryCandidateFaces,
                    sideBoundaryMissingFaces,
                    sideBoundaryExtraFaces,
                    planeWindowTests,
                    planeWindowCandidates,
                    planeWindowRemainingCulled,
                    planeWindows,
                    frontSubtractions,
                    sideSubtractions,
                    occupiedWindowTests,
                    occupiedWindowHits,
                    maxOccupiedWindows,
                    maxVisibleFragments,
                    frontFragmentsBeforeMerge,
                    frontFragmentsAfterMerge,
                    new SpotProjectionResult.SideDiagnostics(
                            sideInternalTravelIntervals,
                            sideExternalTravelIntervals,
                            sideInternalWindowAttempts,
                            sideExternalWindowAttempts,
                            sideInternalDegenerateTravel,
                            sideExternalDegenerateTravel,
                            sideInternalNotRenderable,
                            sideExternalNotRenderable,
                            sideInternalCrossNull,
                            sideExternalCrossNull,
                            sideInternalWindowNull,
                            sideExternalWindowNull,
                            sideInternalWindowCandidates,
                            sideExternalWindowCandidates,
                            sideInternalVisibleEmpty,
                            sideExternalVisibleEmpty,
                            sideInternalLowPower,
                            sideExternalLowPower,
                            sideInternalPatchNull,
                            sideExternalPatchNull,
                            sideInternalSpotInvisible,
                            sideExternalSpotInvisible,
                            sideInternalEmittedQuads,
                            sideExternalEmittedQuads,
                            sideInternalTinyTexturePatches,
                            sideExternalTinyTexturePatches,
                            sideInternalLargeStretchPatches,
                            sideExternalLargeStretchPatches,
                            sideMaxStretchRatio
                    ),
                    new SpotProjectionResult.StageTimings(
                            tileRangeNanos,
                            projectionRectNanos,
                            blockLookupNanos,
                            projectableCheckNanos,
                            planeWindowNanos,
                            frontPassNanos,
                            frontSubtractNanos,
                            sideScanNanos,
                            sideCandidateNanos,
                            sideEmitNanos,
                            sideTravelSplitNanos,
                            sideOcclusionIndexBuildNanos,
                            sideSameDepthSplitNanos,
                            sideWindowNanos,
                            sidePrefixQueryNanos,
                            sideRemainingIntersectNanos,
                            sideRegionIntersectNanos,
                            sideCanonicalNormalizeNanos,
                            sideDebugAuditNanos,
                            sidePatchEmitNanos,
                            surfaceAppearanceBuildNanos,
                            patchClipNanos,
                            spotRecordPackNanos,
                            sideCandidateVerifyNanos,
                            sideSubtractNanos,
                            indexQueryNanos,
                            subtractApplyNanos,
                            frontEmitNanos,
                            fullOccupancyNanos,
                            remainingUnionNanos,
                            remainingSubtractNanos,
                            occlusionAddNanos
                    ),
                    new SpotProjectionResult.IndexStats(
                            indexBucketQueries,
                            indexBucketEntries,
                            indexDuplicateSkips,
                            indexLargeWindowTests,
                            indexLargeWindowHits,
                            indexMaxQueryBuckets,
                            indexMaxQueryEntries,
                            indexMaxLargeWindows
                    ),
                    new SpotProjectionResult.SubtractionStats(
                            subtractIntersectingWindows,
                            subtractMaxIntersectingWindows,
                            subtractApplySteps,
                            subtractSplitSteps,
                            subtractEmptyResults
                    ),
                    new SpotProjectionResult.RemainingStats(
                            remainingSlabs,
                            maxRemainingSlabs,
                            remainingIntervals,
                            maxRemainingIntervals,
                            remainingArea,
                            minRemainingArea,
                            remainingIntersectionQueries,
                            remainingIntersectionSlabTests,
                            remainingIntersectionIntervalTests,
                            remainingVisibleFragments,
                            remainingPrefilterQueries,
                            remainingPrefilterHits,
                            remainingPrefilterSlabTests,
                            remainingPrefilterIntervalTests,
                            remainingUnionInputRects,
                            remainingUnionMergedRects,
                            remainingBlockerTests,
                            remainingBlockerHits,
                            remainingClippedBlockers,
                            remainingBlockerSlabSteps,
                            remainingIntervalClipTests,
                            remainingApplySteps,
                            remainingSplitSteps,
                            remainingEmptyResults
                    ),
                    new SpotProjectionResult.OptimizationStats(
                            footprintIntegralCalls,
                            surfaceAppearanceBuilds,
                            surfaceAppearanceCacheHits,
                            sideCandidateTilesVisited,
                            sideVisibleWindowsBeforeMerge,
                            sideVisibleWindowsAfterMerge,
                            sideBoundaryMissingExamples,
                            sideBoundaryMissingDetails,
                            remainingSubtractValidationChecks,
                            remainingSubtractValidationMismatches,
                            sameDepthSplitIndexQueries,
                            sameDepthTravelGroupsVisited,
                            sameDepthPrefixIndexQueries,
                            sameDepthPrefixIndexCandidates,
                            sameDepthPrefixIndexHits,
                            sameDepthSplitValidationChecks,
                            sameDepthSplitValidationMismatches,
                            sameDepthPrefixValidationChecks,
                            sameDepthPrefixValidationMismatches,
                            sideCanonicalValidationChecks,
                            sideCanonicalValidationMismatches,
                            depthBoundaryRadiusChecks,
                            depthBoundaryRadiusMismatches,
                            depthBoundaryRadiusMaxGap,
                            structuralValidationExamples
                    ),
                    new SpotProjectionResult.HotDepth(
                            hotDepth,
                            hotDepthNanos,
                            hotDepthScannedTiles,
                            hotDepthCandidateTiles,
                            hotDepthProjectableTiles,
                            hotDepthPlaneWindows,
                            hotDepthFrontSubtractions,
                            hotDepthSideSubtractions,
                            hotDepthOccupiedWindowTests,
                            hotDepthOccupiedWindowHits,
                            hotDepthSpots
                    )
            );
        }
    }

    record SideCrossSection(
            double minLocal,
            double maxLocal,
            double axisCanonical,
            double minCanonical,
            double maxCanonical
    ) {
        SideCrossSection {
            minLocal = clamp01(minLocal);
            maxLocal = clamp01(maxLocal);
            axisCanonical = clamp01(axisCanonical);
            minCanonical = clamp01(minCanonical);
            maxCanonical = clamp01(maxCanonical);

            if (minLocal >= maxLocal || minCanonical >= maxCanonical) {
                throw new IllegalArgumentException("Side projection cross-section must have positive width");
            }
        }
    }

    private record ProjectionRect(
            int clipMinUByte,
            int clipMinVByte,
            int clipMaxUByte,
            int clipMaxVByte,
            int textureMinUByte,
            int textureMinVByte,
            int textureMaxUByte,
            int textureMaxVByte,
            double kernelMinU,
            double kernelMinV,
            double kernelMaxU,
            double kernelMaxV
    ) {
        private CanonicalRect canonicalRect() {
            return new CanonicalRect(kernelMinU, kernelMinV, kernelMaxU, kernelMaxV);
        }
    }

    record CanonicalRect(double minU, double minV, double maxU, double maxV) {
        CanonicalRect {
            minU = clamp01(minU);
            minV = clamp01(minV);
            maxU = clamp01(maxU);
            maxV = clamp01(maxV);

            if (minU >= maxU || minV >= maxV) {
                throw new IllegalArgumentException("Canonical projection rectangle must have positive area");
            }
        }
    }

    private record OcclusionWindow(
            CanonicalRect rect,
            String plane,
            BlockPos pos,
            int depth,
            int tileU,
            int tileV,
            int boxIndex,
            double travel,
            double volumeStartTravel
    ) {
    }

    /**
     * One conservative, continuous texture-domain blocker for one optical cuboid.
     * The cached full hull is downstream authority; prefix hulls are materialized
     * only for same-depth receiver ordering.
     */
    private record CuboidSweep(
            BeamGeometryOps.RadiusPropagation radiusPropagation,
            int tileU,
            int tileV,
            BlockPos pos,
            int depth,
            int boxIndex,
            OpticalCollisionBox box,
            CanonicalRect fullHull
    ) {
        private static CuboidSweep create(
                BeamGeometryOps.RadiusPropagation radiusPropagation,
                int tileU,
                int tileV,
                BlockPos pos,
                int depth,
                int boxIndex,
                OpticalCollisionBox box
        ) {
            CanonicalRect fullHull = conservativeCuboidSweepRect(
                    radiusPropagation,
                    tileU,
                    tileV,
                    box,
                    box.minTravel(),
                    box.maxTravel()
            );
            return fullHull == null
                    ? null
                    : new CuboidSweep(
                            radiusPropagation,
                            tileU,
                            tileV,
                            pos,
                            depth,
                            boxIndex,
                            box,
                            fullHull
                    );
        }

        private double startTravel() {
            return box.minTravel();
        }

        private double endTravel() {
            return box.maxTravel();
        }

        private CanonicalRect prefixHull(double surfaceTravel) {
            if (surfaceTravel <= startTravel() + EDGE_TOUCH_EPSILON) {
                return null;
            }
            if (surfaceTravel >= endTravel() - EDGE_TOUCH_EPSILON) {
                return fullHull;
            }
            return conservativeCuboidSweepRect(
                    radiusPropagation,
                    tileU,
                    tileV,
                    box,
                    startTravel(),
                    surfaceTravel
            );
        }

        private OcclusionWindow fullWindow() {
            return window(fullHull, endTravel(), "sweep_full");
        }

        private OcclusionWindow prefixWindow(double surfaceTravel) {
            CanonicalRect prefix = prefixHull(surfaceTravel);
            return prefix == null
                    ? null
                    : window(prefix, Math.min(surfaceTravel, endTravel()), "sweep_prefix");
        }

        private OcclusionWindow window(CanonicalRect rect, double travel, String phase) {
            return new OcclusionWindow(
                    rect,
                    "box" + boxIndex + "." + phase,
                    pos,
                    depth,
                    tileU,
                    tileV,
                    boxIndex,
                    travel,
                    startTravel()
            );
        }
    }

    private VoxelSpotProjector() {
    }
}
