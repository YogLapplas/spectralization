package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.BeamEnvelope;
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
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class VoxelSpotProjector {
    private static final double MIN_FRAGMENT_POWER = 0.0D;
    private static final double SIDE_FACE_VISUAL_FACTOR = 0.35D;
    private static final int MAX_SIDE_PATCH_TRAVEL_SUBDIVISIONS = 8;
    private static final double SIDE_PATCH_RADIUS_RATIO_PER_SEGMENT = 1.18D;
    private static final double SIDE_PATCH_RADIUS_DELTA_PER_SEGMENT = 0.35D;
    private static final double EDGE_TOUCH_EPSILON = 1.0E-6D;
    private static final double CONE_SIDE_EPSILON = 1.0E-8D;
    private static final double VISUAL_DISTANCE_FADE_LINEAR = 0.08D;
    private static final double VISUAL_DISTANCE_FADE_QUADRATIC = 0.004D;
    private static final int MAX_PROJECTED_DEPTH = 32;
    private static final int CONE_TRAVEL_SAMPLES = 16;
    private static final double[] OCCLUSION_PLANE_OFFSETS = {0.0D, 0.25D, 0.5D, 0.75D, 1.0D};
    private static final String[] OCCLUSION_PLANE_NAMES = {"front", "q25", "q50", "q75", "back"};
    private static final CanonicalRect FULL_FOOTPRINT = new CanonicalRect(0.0D, 0.0D, 1.0D, 1.0D);
    private static volatile boolean debugFaceCentersEnabled = false;

    public static boolean debugFaceCentersEnabled() {
        return debugFaceCentersEnabled;
    }

    public static void setDebugFaceCentersEnabled(boolean enabled) {
        debugFaceCentersEnabled = enabled;
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
        List<CanonicalRect> occupiedRayWindows = new ArrayList<>();
        List<OcclusionWindow> occupiedDebugWindows = new ArrayList<>();
        BeamPacket projectionTemplate = profileTemplate;

        for (int depth = 1; depth <= MAX_PROJECTED_DEPTH; depth++) {
            BeamEnvelope envelope = envelopeAtDepth(projectionTemplate.envelope(), depth);
            double radius = envelope.radius();

            if (!Double.isFinite(radius) || radius <= 0.0D) {
                continue;
            }

            Direction displayFace = travelDirection.getOpposite();
            Direction uDirection = SpotSurfaceFrame.uDirection(displayFace);
            Direction vDirection = SpotSurfaceFrame.vDirection(displayFace);
            int tileRadius = projectedTileRadius(maxEnvelopeRadiusOverUnit(envelope));
            BeamPacket targetTemplate = projectionTemplate.withEnvelope(envelope);
            List<OcclusionWindow> frontBlockersAtDepth = new ArrayList<>();
            List<CanonicalRect> frontFaceWindowsAtDepth = new ArrayList<>();
            List<CanonicalRect> sideBlockersAtDepth = new ArrayList<>();
            BlockPos depthOrigin = sourcePos.relative(travelDirection, depth);

            for (int dv = -tileRadius; dv <= tileRadius; dv++) {
                for (int du = -tileRadius; du <= tileRadius; du++) {
                    ProjectionRect rect = projectionRect(radius, du, dv);
                    BlockPos targetPos = depthOrigin
                            .relative(uDirection, du)
                            .relative(vDirection, dv);
                    List<OcclusionWindow> blockOcclusionWindows = blockPlaneOcclusionWindows(
                            targetTemplate.envelope(),
                            du,
                            dv,
                            targetPos,
                            depth
                    );

                    if (rect == null && blockOcclusionWindows.isEmpty()) {
                        continue;
                    }

                    dependencies.add(targetPos.asLong());

                    if (!level.isLoaded(targetPos)) {
                        continue;
                    }

                    BlockState targetState = level.getBlockState(targetPos);

                    if (OpticalMaterialProfiles.isAirLike(targetState)) {
                        continue;
                    }

                    if (!isProjectableSurface(level, targetPos, targetState)) {
                        continue;
                    }

                    if (depth > 0 && !blockOcclusionWindows.isEmpty()) {
                        frontBlockersAtDepth.addAll(blockOcclusionWindows);
                        allocations.add(frontOcclusionProbeAllocation(
                                targetPos,
                                displayFace,
                                depth,
                                du,
                                dv,
                                targetTemplate.envelope(),
                                rect,
                                blockOcclusionWindows
                        ));

                        if (rect != null) {
                            frontFaceWindowsAtDepth.add(rect.canonicalRect());
                        }
                    }

                    if (rect == null) {
                        continue;
                    }

                    List<ProjectionRect> visibleRects = visibleSubRects(radius, du, dv, rect, occupiedRayWindows);
                    List<OcclusionWindow> intersectingOccupied = intersectingOcclusionWindows(rect.canonicalRect(), occupiedDebugWindows);
                    List<ProjectionRect> visibleWithoutBackRects = visibleSubRects(
                            radius,
                            du,
                            dv,
                            rect,
                            canonicalWindowsExcludingPlane(occupiedDebugWindows, "back")
                    );

                    if (!intersectingOccupied.isEmpty() || visibleRects.size() != 1) {
                        allocations.add(frontVisibleProbeAllocation(
                                targetPos,
                                displayFace,
                                depth,
                                du,
                                dv,
                                rect,
                                intersectingOccupied,
                                visibleRects,
                                visibleWithoutBackRects,
                                occupiedDebugWindows.size()
                        ));
                    }

                    if (visibleRects.isEmpty()) {
                        continue;
                    }

                    addDebugFaceCenter(targetPos, displayFace, fragments);

                    for (ProjectionRect visibleRect : visibleRects) {
                        double visualDistanceFactor = visualDistanceFactor(depth);
                        double visualFragmentPower = visualSurfacePower(beamPower, visualDistanceFactor);

                        if (visualFragmentPower <= MIN_FRAGMENT_POWER) {
                            continue;
                        }

                        addFrontChartSpot(
                                level,
                                targetPos,
                                displayFace,
                                targetState,
                                targetTemplate,
                                visualFragmentPower,
                                visualSurfacePower(coherentBeamPower, visualDistanceFactor),
                                radius,
                                du,
                                dv,
                                visibleRect,
                                fragments
                        );
                    }
                }
            }

            if (depth > 0) {
                double visualDistanceFactor = visualDistanceFactor(depth);
                List<CanonicalRect> sideOcclusionWindows = sameDepthSideOcclusionWindows(
                        occupiedRayWindows,
                        frontFaceWindowsAtDepth
                );

                addIndependentSideQuads(
                        level,
                        depthOrigin,
                        travelDirection,
                        uDirection,
                        vDirection,
                        tileRadius,
                        targetTemplate,
                        beamPower,
                        coherentBeamPower,
                        visualDistanceFactor,
                        sideOcclusionWindows,
                        sideBlockersAtDepth,
                        dependencies,
                        allocations,
                        fragments
                );
            }

            for (OcclusionWindow blocker : frontBlockersAtDepth) {
                occupiedRayWindows.add(blocker.rect());
                occupiedDebugWindows.add(blocker);
            }

            if (fullyOccupied(occupiedRayWindows)) {
                break;
            }
        }

        return new SpotProjectionResult(fragments, dependencies, allocations);
    }

    private static void addIndependentSideQuads(
            Level level,
            BlockPos depthOrigin,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            int tileRadius,
            BeamPacket targetTemplate,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            List<CanonicalRect> occupiedRayWindows,
            List<CanonicalRect> sideBlockersAtDepth,
            LongSet dependencies,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        int sideTileRadius = tileRadius + 1;

        for (int tileV = -sideTileRadius; tileV <= sideTileRadius; tileV++) {
            for (int tileU = -sideTileRadius; tileU <= sideTileRadius; tileU++) {
                BlockPos targetPos = depthOrigin
                        .relative(uDirection, tileU)
                        .relative(vDirection, tileV);
                dependencies.add(targetPos.asLong());

                if (!level.isLoaded(targetPos)) {
                    continue;
                }

                BlockState targetState = level.getBlockState(targetPos);

                if (!isProjectableSurface(level, targetPos, targetState)) {
                    continue;
                }

                addSideQuads(
                        level,
                        targetPos,
                        targetState,
                        travelDirection,
                        uDirection,
                        vDirection,
                        tileU,
                        tileV,
                        targetTemplate,
                        beamPower,
                        coherentBeamPower,
                        visualDistanceFactor,
                        occupiedRayWindows,
                        sideBlockersAtDepth,
                        dependencies,
                        allocations,
                        fragments
                );
            }
        }
    }

    private static void addSideQuads(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            int tileU,
            int tileV,
            BeamPacket targetTemplate,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            List<CanonicalRect> occupiedRayWindows,
            List<CanonicalRect> sideBlockersAtDepth,
            LongSet dependencies,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        double entryTravel = 0.0D;
        double exitTravel = 1.0D;

        addUSideStrip(
                level,
                targetPos,
                targetState,
                uDirection.getOpposite(),
                travelDirection,
                uDirection,
                vDirection,
                entryTravel,
                exitTravel,
                tileU - 0.5D,
                0.0D,
                tileV,
                targetTemplate,
                beamPower,
                coherentBeamPower,
                visualDistanceFactor,
                occupiedRayWindows,
                sideBlockersAtDepth,
                dependencies,
                allocations,
                fragments
        );

        addUSideStrip(
                level,
                targetPos,
                targetState,
                uDirection,
                travelDirection,
                uDirection,
                vDirection,
                entryTravel,
                exitTravel,
                tileU + 0.5D,
                1.0D,
                tileV,
                targetTemplate,
                beamPower,
                coherentBeamPower,
                visualDistanceFactor,
                occupiedRayWindows,
                sideBlockersAtDepth,
                dependencies,
                allocations,
                fragments
        );

        addVSideStrip(
                level,
                targetPos,
                targetState,
                vDirection.getOpposite(),
                travelDirection,
                uDirection,
                vDirection,
                entryTravel,
                exitTravel,
                tileV - 0.5D,
                0.0D,
                tileU,
                targetTemplate,
                beamPower,
                coherentBeamPower,
                visualDistanceFactor,
                occupiedRayWindows,
                sideBlockersAtDepth,
                dependencies,
                allocations,
                fragments
        );

        addVSideStrip(
                level,
                targetPos,
                targetState,
                vDirection,
                travelDirection,
                uDirection,
                vDirection,
                entryTravel,
                exitTravel,
                tileV + 0.5D,
                1.0D,
                tileU,
                targetTemplate,
                beamPower,
                coherentBeamPower,
                visualDistanceFactor,
                occupiedRayWindows,
                sideBlockersAtDepth,
                dependencies,
                allocations,
                fragments
        );
    }

    private static boolean isProjectableSurface(Level level, BlockPos pos, BlockState state) {
        if (OpticalMaterialProfiles.isAirLike(state)) {
            return false;
        }

        if (state.getBlock() instanceof LensHolderBlock) {
            return level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder && lensHolder.hasLens();
        }

        return state.isCollisionShapeFullBlock(level, pos)
                || state.getBlock() instanceof OpticalElement
                || state.getBlock() instanceof OpticalSource;
    }

    private static boolean isSideOpen(
            Level level,
            BlockPos pos,
            Direction sideFace,
            LongSet dependencies
    ) {
        BlockPos neighborPos = pos.relative(sideFace);
        dependencies.add(neighborPos.asLong());

        if (!level.isLoaded(neighborPos)) {
            return false;
        }

        return OpticalMaterialProfiles.isAirLike(level.getBlockState(neighborPos));
    }

    private static void addFrontChartSpot(
            Level level,
            BlockPos targetPos,
            Direction displayFace,
            BlockState targetState,
            BeamPacket targetTemplate,
            double sidePower,
            double sideCoherentPower,
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
        SpotSurfaceFrame.LocalCoordinates p0 = SpotSurfaceFrame.surfaceLocal(displayFace, localMinU, localMinV, 0.0D);
        SpotSurfaceFrame.LocalCoordinates p1 = SpotSurfaceFrame.surfaceLocal(displayFace, localMinU, localMaxV, 0.0D);
        SpotSurfaceFrame.LocalCoordinates p2 = SpotSurfaceFrame.surfaceLocal(displayFace, localMaxU, localMaxV, 0.0D);
        SpotSurfaceFrame.LocalCoordinates p3 = SpotSurfaceFrame.surfaceLocal(displayFace, localMaxU, localMinV, 0.0D);

        addSideSpot(
                level,
                targetPos,
                targetState,
                displayFace,
                targetTemplate,
                sidePower,
                sideCoherentPower,
                surfacePoint(p0),
                visibleRect.kernelMinU(),
                renderTextureV(visibleRect.kernelMinV()),
                surfacePoint(p1),
                visibleRect.kernelMinU(),
                renderTextureV(visibleRect.kernelMaxV()),
                surfacePoint(p2),
                visibleRect.kernelMaxU(),
                renderTextureV(visibleRect.kernelMaxV()),
                surfacePoint(p3),
                visibleRect.kernelMaxU(),
                renderTextureV(visibleRect.kernelMinV()),
                fragments
        );
    }

    private static LocalPoint surfacePoint(SpotSurfaceFrame.LocalCoordinates point) {
        return new LocalPoint(point.x(), point.y(), point.z());
    }

    private static void addDebugFaceCenter(BlockPos targetPos, Direction face, List<SpotRecord> fragments) {
        if (debugFaceCentersEnabled) {
            fragments.add(SpotRecord.debugFaceCenter(targetPos, face));
        }
    }

    private static void addUSideStrip(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double entryTravel,
            double exitTravel,
            double boundaryWorldU,
            double fixedULocal,
            int tileV,
            BeamPacket targetTemplate,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            List<CanonicalRect> occupiedRayWindows,
            List<CanonicalRect> sideBlockersAtDepth,
            LongSet dependencies,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        if (!isSideOpen(level, targetPos, sideFace, dependencies)) {
            return;
        }

        addUSideVolumeQuads(
                level,
                targetPos,
                targetState,
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                entryTravel,
                exitTravel,
                fixedULocal,
                tileV,
                boundaryWorldU,
                targetTemplate,
                beamPower,
                coherentBeamPower,
                visualDistanceFactor,
                occupiedRayWindows,
                sideBlockersAtDepth,
                allocations,
                fragments
        );
    }

    private static void addVSideStrip(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double entryTravel,
            double exitTravel,
            double boundaryWorldV,
            double fixedVLocal,
            int tileU,
            BeamPacket targetTemplate,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            List<CanonicalRect> occupiedRayWindows,
            List<CanonicalRect> sideBlockersAtDepth,
            LongSet dependencies,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        if (!isSideOpen(level, targetPos, sideFace, dependencies)) {
            return;
        }

        addVSideVolumeQuads(
                level,
                targetPos,
                targetState,
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                entryTravel,
                exitTravel,
                fixedVLocal,
                tileU,
                boundaryWorldV,
                targetTemplate,
                beamPower,
                coherentBeamPower,
                visualDistanceFactor,
                occupiedRayWindows,
                sideBlockersAtDepth,
                allocations,
                fragments
        );
    }

    private static void addUSideVolumeQuads(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double entryTravel,
            double exitTravel,
            double fixedULocal,
            int tileV,
            double boundaryWorldU,
            BeamPacket targetTemplate,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            List<CanonicalRect> occupiedRayWindows,
            List<CanonicalRect> sideBlockersAtDepth,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        List<TravelInterval> visibleTravels = uSideTravelIntervals(targetTemplate.envelope(), boundaryWorldU, tileV);

        if (visibleTravels.isEmpty()) {
            return;
        }

        boolean addedDebugCenter = false;

        for (TravelInterval visibleTravel : visibleTravels) {
            List<TravelInterval> chartTravels = splitSideTravelAtCrossBoundaries(
                    targetTemplate.envelope(),
                    visibleTravel,
                    tileV - 0.5D,
                    tileV + 0.5D
            );

            for (TravelInterval chartTravel : chartTravels) {
                int travelSteps = sideChartTravelSubdivisionCount(targetTemplate.envelope(), chartTravel.min(), chartTravel.max());

                for (int travelIndex = 0; travelIndex < travelSteps; travelIndex++) {
                    double rawTravel0 = lerp(chartTravel.min(), chartTravel.max(), travelIndex / (double) travelSteps);
                    double rawTravel1 = lerp(chartTravel.min(), chartTravel.max(), (travelIndex + 1) / (double) travelSteps);
                double crossTravel0 = nudgeUSideTravelEndpoint(targetTemplate.envelope(), rawTravel0, rawTravel1, boundaryWorldU, tileV);
                double crossTravel1 = nudgeUSideTravelEndpoint(targetTemplate.envelope(), rawTravel1, rawTravel0, boundaryWorldU, tileV);

                if (crossTravel1 - crossTravel0 <= EDGE_TOUCH_EPSILON) {
                    continue;
                }

                BeamEnvelope envelope0 = envelopeAtOffset(targetTemplate.envelope(), crossTravel0);
                BeamEnvelope envelope1 = envelopeAtOffset(targetTemplate.envelope(), crossTravel1);
                double radius0 = envelope0.radius();
                double radius1 = envelope1.radius();

                if (!isEntranceSide(boundaryWorldU, fixedULocal, radius0, radius1)) {
                    continue;
                }

                SideCrossSection cross0 = uSideCrossSection(radius0, boundaryWorldU, tileV);
                SideCrossSection cross1 = uSideCrossSection(radius1, boundaryWorldU, tileV);

                if (cross0 == null || cross1 == null) {
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
                    continue;
                }

                List<CanonicalRect> visibleSideWindows = subtractOccupied(sideWindow, occupiedRayWindows);
                double candidateArea = canonicalArea(sideWindow);

                if (visibleSideWindows.isEmpty()) {
                    allocations.add(sideAllocation(
                            targetPos,
                            sideFace,
                            "u-side",
                            candidateArea,
                            0.0D,
                            0.0D,
                            0.0D,
                            0.0D,
                            0,
                            "occluded"
                    ));
                    continue;
                }

                if (!addedDebugCenter) {
                    addDebugFaceCenter(targetPos, sideFace, fragments);
                    addedDebugCenter = true;
                }

                double travel0 = lerp(entryTravel, exitTravel, crossTravel0);
                double travel1 = lerp(entryTravel, exitTravel, crossTravel1);

                for (CanonicalRect visibleSideWindow : visibleSideWindows) {
                    double assignedArea = canonicalArea(visibleSideWindow);
                    double sideFraction = integratedFraction(visibleSideWindow);
                    double sidePower = visualSidePower(beamPower, visualDistanceFactor);

                    if (sidePower <= MIN_FRAGMENT_POWER) {
                        allocations.add(sideAllocation(
                                targetPos,
                                sideFace,
                                "u-side",
                                candidateArea,
                                assignedArea,
                                0.0D,
                                sideFraction,
                                0.0D,
                                0,
                                "low_power"
                        ));
                        continue;
                    }

                    PatchEmissionReport emission = addUSideChartSpot(
                            level,
                            targetPos,
                            targetState,
                            sideFace,
                            travelDirection,
                            uDirection,
                            vDirection,
                            targetTemplate,
                            sidePower,
                            visualSidePower(coherentBeamPower, visualDistanceFactor),
                            fixedULocal,
                            travel0,
                            travel1,
                            cross0,
                            cross1,
                            sideWindow,
                            visibleSideWindow,
                            fragments
                    );

                    sideBlockersAtDepth.add(visibleSideWindow);

                    allocations.add(sideAllocation(
                            targetPos,
                            sideFace,
                            "u-side",
                            candidateArea,
                            assignedArea,
                            emission.emitted() ? assignedArea : 0.0D,
                            sideFraction,
                            emission.emitted() ? sideFraction : 0.0D,
                            emission.emittedQuads(),
                            emission.resultName(),
                            emission.detail()
                    ));
                }
            }
            }
        }
    }

    private static void addVSideVolumeQuads(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double entryTravel,
            double exitTravel,
            double fixedVLocal,
            int tileU,
            double boundaryWorldV,
            BeamPacket targetTemplate,
            double beamPower,
            double coherentBeamPower,
            double visualDistanceFactor,
            List<CanonicalRect> occupiedRayWindows,
            List<CanonicalRect> sideBlockersAtDepth,
            List<SpotProjectionAllocation> allocations,
            List<SpotRecord> fragments
    ) {
        List<TravelInterval> visibleTravels = vSideTravelIntervals(targetTemplate.envelope(), boundaryWorldV, tileU);

        if (visibleTravels.isEmpty()) {
            return;
        }

        boolean addedDebugCenter = false;

        for (TravelInterval visibleTravel : visibleTravels) {
            List<TravelInterval> chartTravels = splitSideTravelAtCrossBoundaries(
                    targetTemplate.envelope(),
                    visibleTravel,
                    tileU - 0.5D,
                    tileU + 0.5D
            );

            for (TravelInterval chartTravel : chartTravels) {
                int travelSteps = sideChartTravelSubdivisionCount(targetTemplate.envelope(), chartTravel.min(), chartTravel.max());

                for (int travelIndex = 0; travelIndex < travelSteps; travelIndex++) {
                    double rawTravel0 = lerp(chartTravel.min(), chartTravel.max(), travelIndex / (double) travelSteps);
                    double rawTravel1 = lerp(chartTravel.min(), chartTravel.max(), (travelIndex + 1) / (double) travelSteps);
                double crossTravel0 = nudgeVSideTravelEndpoint(targetTemplate.envelope(), rawTravel0, rawTravel1, boundaryWorldV, tileU);
                double crossTravel1 = nudgeVSideTravelEndpoint(targetTemplate.envelope(), rawTravel1, rawTravel0, boundaryWorldV, tileU);

                if (crossTravel1 - crossTravel0 <= EDGE_TOUCH_EPSILON) {
                    continue;
                }

                BeamEnvelope envelope0 = envelopeAtOffset(targetTemplate.envelope(), crossTravel0);
                BeamEnvelope envelope1 = envelopeAtOffset(targetTemplate.envelope(), crossTravel1);
                double radius0 = envelope0.radius();
                double radius1 = envelope1.radius();

                if (!isEntranceSide(boundaryWorldV, fixedVLocal, radius0, radius1)) {
                    continue;
                }

                SideCrossSection cross0 = vSideCrossSection(radius0, boundaryWorldV, tileU);
                SideCrossSection cross1 = vSideCrossSection(radius1, boundaryWorldV, tileU);

                if (cross0 == null || cross1 == null) {
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
                    continue;
                }

                List<CanonicalRect> visibleSideWindows = subtractOccupied(sideWindow, occupiedRayWindows);
                double candidateArea = canonicalArea(sideWindow);

                if (visibleSideWindows.isEmpty()) {
                    allocations.add(sideAllocation(
                            targetPos,
                            sideFace,
                            "v-side",
                            candidateArea,
                            0.0D,
                            0.0D,
                            0.0D,
                            0.0D,
                            0,
                            "occluded"
                    ));
                    continue;
                }

                if (!addedDebugCenter) {
                    addDebugFaceCenter(targetPos, sideFace, fragments);
                    addedDebugCenter = true;
                }

                double travel0 = lerp(entryTravel, exitTravel, crossTravel0);
                double travel1 = lerp(entryTravel, exitTravel, crossTravel1);

                for (CanonicalRect visibleSideWindow : visibleSideWindows) {
                    double assignedArea = canonicalArea(visibleSideWindow);
                    double sideFraction = integratedFraction(visibleSideWindow);
                    double sidePower = visualSidePower(beamPower, visualDistanceFactor);

                    if (sidePower <= MIN_FRAGMENT_POWER) {
                        allocations.add(sideAllocation(
                                targetPos,
                                sideFace,
                                "v-side",
                                candidateArea,
                                assignedArea,
                                0.0D,
                                sideFraction,
                                0.0D,
                                0,
                                "low_power"
                        ));
                        continue;
                    }

                    PatchEmissionReport emission = addVSideChartSpot(
                            level,
                            targetPos,
                            targetState,
                            sideFace,
                            travelDirection,
                            uDirection,
                            vDirection,
                            targetTemplate,
                            sidePower,
                            visualSidePower(coherentBeamPower, visualDistanceFactor),
                            fixedVLocal,
                            travel0,
                            travel1,
                            cross0,
                            cross1,
                            sideWindow,
                            visibleSideWindow,
                            fragments
                    );

                    sideBlockersAtDepth.add(visibleSideWindow);

                    allocations.add(sideAllocation(
                            targetPos,
                            sideFace,
                            "v-side",
                            candidateArea,
                            assignedArea,
                            emission.emitted() ? assignedArea : 0.0D,
                            sideFraction,
                            emission.emitted() ? sideFraction : 0.0D,
                            emission.emittedQuads(),
                            emission.resultName(),
                            emission.detail()
                    ));
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
        double previousRadius = envelopeAtOffset(envelope, previousT).radius();

        if (!Double.isFinite(previousRadius) || previousRadius <= 0.0D) {
            return null;
        }

        boolean previousInside = previousRadius + EDGE_TOUCH_EPSILON >= requiredRadius;
        double firstInside = previousInside ? t0 : Double.NaN;
        double lastInside = previousInside ? t0 : Double.NaN;

        for (int sample = 1; sample <= CONE_TRAVEL_SAMPLES; sample++) {
            double currentT = lerp(t0, t1, sample / (double) CONE_TRAVEL_SAMPLES);
            double currentRadius = envelopeAtOffset(envelope, currentT).radius();

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
        return sideTravelIntervals(
                travel -> uSideIntersectsAt(envelope, boundaryWorldU, tileV, travel)
        );
    }

    private static List<TravelInterval> vSideTravelIntervals(
            BeamEnvelope envelope,
            double boundaryWorldV,
            int tileU
    ) {
        return sideTravelIntervals(
                travel -> vSideIntersectsAt(envelope, boundaryWorldV, tileU, travel)
        );
    }

    private static List<TravelInterval> sideTravelIntervals(SideIntersectionPredicate predicate) {
        List<TravelInterval> intervals = new ArrayList<>();
        double previousT = 0.0D;
        boolean previousInside = predicate.intersects(previousT);
        double start = previousInside ? previousT : Double.NaN;

        for (int sample = 1; sample <= CONE_TRAVEL_SAMPLES; sample++) {
            double currentT = sample / (double) CONE_TRAVEL_SAMPLES;
            boolean currentInside = predicate.intersects(currentT);

            if (previousInside != currentInside) {
                double boundary = sideIntersectionBoundary(predicate, previousT, currentT, previousInside);

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
            addTravelInterval(intervals, start, 1.0D);
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
        List<Double> cuts = new ArrayList<>();
        cuts.add(interval.min());
        cuts.add(interval.max());
        addRadiusBoundaryCuts(cuts, envelope, interval, Math.abs(firstBoundary));
        addRadiusBoundaryCuts(cuts, envelope, interval, Math.abs(secondBoundary));
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

    private static void addRadiusBoundaryCuts(
            List<Double> cuts,
            BeamEnvelope envelope,
            TravelInterval interval,
            double requiredRadius
    ) {
        if (!Double.isFinite(requiredRadius) || requiredRadius <= EDGE_TOUCH_EPSILON) {
            return;
        }

        double previousT = interval.min();
        boolean previousInside = radiusAt(envelope, previousT) + EDGE_TOUCH_EPSILON >= requiredRadius;

        for (int sample = 1; sample <= CONE_TRAVEL_SAMPLES; sample++) {
            double currentT = lerp(interval.min(), interval.max(), sample / (double) CONE_TRAVEL_SAMPLES);
            boolean currentInside = radiusAt(envelope, currentT) + EDGE_TOUCH_EPSILON >= requiredRadius;

            if (previousInside != currentInside) {
                double crossing = radiusThresholdBoundary(envelope, previousT, currentT, previousInside, requiredRadius);

                if (crossing > interval.min() + EDGE_TOUCH_EPSILON && crossing < interval.max() - EDGE_TOUCH_EPSILON) {
                    cuts.add(crossing);
                }
            }

            previousT = currentT;
            previousInside = currentInside;
        }
    }

    private static double radiusThresholdBoundary(
            BeamEnvelope envelope,
            double t0,
            double t1,
            boolean insideAtT0,
            double requiredRadius
    ) {
        double valid = insideAtT0 ? t0 : t1;
        double invalid = insideAtT0 ? t1 : t0;

        for (int iteration = 0; iteration < 24; iteration++) {
            double mid = (valid + invalid) * 0.5D;

            if (radiusAt(envelope, mid) + EDGE_TOUCH_EPSILON >= requiredRadius) {
                valid = mid;
            } else {
                invalid = mid;
            }
        }

        return valid;
    }

    private static double radiusAt(BeamEnvelope envelope, double travel) {
        double radius = envelopeAtOffset(envelope, travel).radius();
        return Double.isFinite(radius) ? radius : 0.0D;
    }

    private static double sideIntersectionBoundary(
            SideIntersectionPredicate predicate,
            double t0,
            double t1,
            boolean insideAtT0
    ) {
        double valid = insideAtT0 ? t0 : t1;
        double invalid = insideAtT0 ? t1 : t0;

        for (int iteration = 0; iteration < 24; iteration++) {
            double mid = (valid + invalid) * 0.5D;

            if (predicate.intersects(mid)) {
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
        return nudgeSideTravelEndpoint(
                endpoint,
                toward,
                travel -> uSideIntersectsAt(envelope, boundaryWorldU, tileV, travel)
        );
    }

    private static double nudgeVSideTravelEndpoint(
            BeamEnvelope envelope,
            double endpoint,
            double toward,
            double boundaryWorldV,
            int tileU
    ) {
        return nudgeSideTravelEndpoint(
                endpoint,
                toward,
                travel -> vSideIntersectsAt(envelope, boundaryWorldV, tileU, travel)
        );
    }

    private static double nudgeSideTravelEndpoint(
            double endpoint,
            double toward,
            SideIntersectionPredicate predicate
    ) {
        if (predicate.intersects(endpoint)) {
            return endpoint;
        }

        if (Math.abs(toward - endpoint) <= EDGE_TOUCH_EPSILON || !predicate.intersects(toward)) {
            return endpoint;
        }

        double invalid = endpoint;
        double valid = toward;

        for (int iteration = 0; iteration < 24; iteration++) {
            double mid = (invalid + valid) * 0.5D;

            if (predicate.intersects(mid)) {
                valid = mid;
            } else {
                invalid = mid;
            }
        }

        return valid;
    }

    private static boolean uSideIntersectsAt(
            BeamEnvelope envelope,
            double boundaryWorldU,
            int tileV,
            double travel
    ) {
        double radius = envelopeAtOffset(envelope, travel).radius();
        return uSideCrossSection(radius, boundaryWorldU, tileV) != null;
    }

    private static boolean vSideIntersectsAt(
            BeamEnvelope envelope,
            double boundaryWorldV,
            int tileU,
            double travel
    ) {
        double radius = envelopeAtOffset(envelope, travel).radius();
        return vSideCrossSection(radius, boundaryWorldV, tileU) != null;
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
        double radius = envelopeAtOffset(envelope, endpoint).radius();
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
        if (!Double.isFinite(radius) || radius <= 0.0D || Math.abs(boundaryWorldU) > radius + EDGE_TOUCH_EPSILON) {
            return null;
        }

        double tileMinV = tileV - 0.5D;
        double tileMaxV = tileV + 0.5D;
        double hitMinV = Math.max(tileMinV, -radius);
        double hitMaxV = Math.min(tileMaxV, radius);

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
        if (!Double.isFinite(radius) || radius <= 0.0D || Math.abs(boundaryWorldV) > radius + EDGE_TOUCH_EPSILON) {
            return null;
        }

        double tileMinU = tileU - 0.5D;
        double tileMaxU = tileU + 0.5D;
        double hitMinU = Math.max(tileMinU, -radius);
        double hitMaxU = Math.min(tileMaxU, radius);

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
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            BeamPacket targetTemplate,
            double sidePower,
            double sideCoherentPower,
            double fixedULocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect sideWindow,
            CanonicalRect visibleWindow,
            List<SpotRecord> fragments
    ) {
        PatchEmission result = PatchEmission.PATCH_NULL;
        int emittedQuads = 0;
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

        for (Patch patch : patches) {
            PatchEmission emission = addPatchSpot(
                    level,
                    targetPos,
                    targetState,
                    sideFace,
                    targetTemplate,
                    sidePower,
                    sideCoherentPower,
                    patch,
                    fragments
            );

            if (emission.emitted()) {
                emittedQuads++;
            }

            result = emission;
        }

        String detail = sidePatchDetail("u-side", sideWindow, visibleWindow, travel0, travel1, cross0, cross1, patches);
        return new PatchEmissionReport(
                emittedQuads > 0 ? PatchEmission.EMITTED : result,
                emittedQuads,
                detail
        );
    }

    private static PatchEmissionReport addVSideChartSpot(
            Level level,
            BlockPos targetPos,
            BlockState targetState,
            Direction sideFace,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            BeamPacket targetTemplate,
            double sidePower,
            double sideCoherentPower,
            double fixedVLocal,
            double travel0,
            double travel1,
            SideCrossSection cross0,
            SideCrossSection cross1,
            CanonicalRect sideWindow,
            CanonicalRect visibleWindow,
            List<SpotRecord> fragments
    ) {
        PatchEmission result = PatchEmission.PATCH_NULL;
        int emittedQuads = 0;
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

        for (Patch patch : patches) {
            PatchEmission emission = addPatchSpot(
                    level,
                    targetPos,
                    targetState,
                    sideFace,
                    targetTemplate,
                    sidePower,
                    sideCoherentPower,
                    patch,
                    fragments
            );

            if (emission.emitted()) {
                emittedQuads++;
            }

            result = emission;
        }

        String detail = sidePatchDetail("v-side", sideWindow, visibleWindow, travel0, travel1, cross0, cross1, patches);
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
        if (envelope == null || travel1 - travel0 <= EDGE_TOUCH_EPSILON) {
            return 1;
        }

        double minRadius = Double.POSITIVE_INFINITY;
        double maxRadius = 0.0D;

        for (int sample = 0; sample <= 4; sample++) {
            double travel = lerp(travel0, travel1, sample / 4.0D);
            double radius = envelopeAtOffset(envelope, travel).radius();

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
        return 1;
    }

    private static SpotProjectionAllocation sideAllocation(
            BlockPos targetPos,
            Direction sideFace,
            String kind,
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
                detail
        );
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

        return SpotProjectionPatch.oriented(
                sideFace,
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

        return SpotProjectionPatch.oriented(
                sideFace,
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
        SpotRecord spot = OpticalSpotTracker.createCompiledSurfaceSpot(
                level,
                targetPos,
                sideFace,
                targetState,
                targetTemplate,
                sidePower,
                sideCoherentPower
        ).withFootprintQuad(
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

        if (spot.visible()) {
            fragments.add(spot);
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

    private static double maxEnvelopeRadiusOverUnit(BeamEnvelope envelope) {
        double maxRadius = 0.0D;

        for (int sample = 0; sample <= CONE_TRAVEL_SAMPLES; sample++) {
            double offset = sample / (double) CONE_TRAVEL_SAMPLES;
            double radius = envelopeAtOffset(envelope, offset).radius();

            if (Double.isFinite(radius) && radius > maxRadius) {
                maxRadius = radius;
            }
        }

        return maxRadius;
    }

    private static int projectedTileRadius(double radius) {
        if (!Double.isFinite(radius) || radius <= 0.0D) {
            return 0;
        }

        return Math.max(0, (int) Math.ceil(radius + 0.5D));
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

    private static boolean isEntranceSide(
            double boundaryWorldCoordinate,
            double fixedLocal,
            double entryRadius,
            double exitRadius
    ) {
        double radiusDelta = exitRadius - entryRadius;

        if (!Double.isFinite(radiusDelta) || Math.abs(radiusDelta) <= CONE_SIDE_EPSILON) {
            return false;
        }

        boolean minSide = fixedLocal < 0.5D;

        if (radiusDelta > 0.0D) {
            return minSide
                    ? boundaryWorldCoordinate > CONE_SIDE_EPSILON
                    : boundaryWorldCoordinate < -CONE_SIDE_EPSILON;
        }

        return minSide
                ? boundaryWorldCoordinate < -CONE_SIDE_EPSILON
                : boundaryWorldCoordinate > CONE_SIDE_EPSILON;
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

    private static List<OcclusionWindow> blockPlaneOcclusionWindows(
            BeamEnvelope envelope,
            int tileU,
            int tileV,
            BlockPos pos,
            int depth
    ) {
        List<OcclusionWindow> windows = new ArrayList<>();

        for (int index = 0; index < OCCLUSION_PLANE_OFFSETS.length; index++) {
            double offset = OCCLUSION_PLANE_OFFSETS[index];
            BeamEnvelope planeEnvelope = envelopeAtOffset(envelope, offset);
            ProjectionRect planeRect = projectionRect(planeEnvelope.radius(), tileU, tileV);

            if (planeRect != null) {
                windows.add(new OcclusionWindow(
                        planeRect.canonicalRect(),
                        OCCLUSION_PLANE_NAMES[index],
                        pos,
                        depth,
                        tileU,
                        tileV
                ));
            }
        }

        return windows.isEmpty() ? List.of() : windows;
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
            List<CanonicalRect> occupiedRayWindows
    ) {
        if (occupiedRayWindows.isEmpty()) {
            return List.of(rect);
        }

        List<CanonicalRect> visibleRayWindows = subtractOccupied(rect.canonicalRect(), occupiedRayWindows);

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
            List<OcclusionWindow> occupiedRayWindows
    ) {
        if (occupiedRayWindows.isEmpty()) {
            return List.of();
        }

        List<OcclusionWindow> intersections = new ArrayList<>();

        for (OcclusionWindow occupied : occupiedRayWindows) {
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
            if (plane.equals(window.plane())) {
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
            if (!excludedPlane.equals(window.plane())) {
                rects.add(window.rect());
            }
        }

        return rects.isEmpty() ? List.of() : rects;
    }

    private static boolean intersects(CanonicalRect first, CanonicalRect second) {
        double minU = Math.max(first.minU(), second.minU());
        double minV = Math.max(first.minV(), second.minV());
        double maxU = Math.min(first.maxU(), second.maxU());
        double maxV = Math.min(first.maxV(), second.maxV());
        return maxU - minU > EDGE_TOUCH_EPSILON && maxV - minV > EDGE_TOUCH_EPSILON;
    }

    private static List<CanonicalRect> sameDepthSideOcclusionWindows(
            List<CanonicalRect> occupiedRayWindows,
            List<CanonicalRect> frontFaceWindowsAtDepth
    ) {
        if (frontFaceWindowsAtDepth.isEmpty()) {
            return occupiedRayWindows;
        }

        List<CanonicalRect> occlusionWindows = new ArrayList<>(occupiedRayWindows.size() + frontFaceWindowsAtDepth.size());
        occlusionWindows.addAll(occupiedRayWindows);
        occlusionWindows.addAll(frontFaceWindowsAtDepth);
        return occlusionWindows;
    }

    private static List<CanonicalRect> subtractOccupied(CanonicalRect source, List<CanonicalRect> occupiedRayWindows) {
        List<CanonicalRect> remaining = new ArrayList<>();
        remaining.add(source);

        for (CanonicalRect occupied : occupiedRayWindows) {
            if (remaining.isEmpty()) {
                return List.of();
            }

            List<CanonicalRect> next = new ArrayList<>();

            for (CanonicalRect candidate : remaining) {
                subtract(candidate, occupied, next);
            }

            remaining = next;
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

    private static boolean fullyOccupied(List<CanonicalRect> occupiedRayWindows) {
        return subtractOccupied(FULL_FOOTPRINT, occupiedRayWindows).isEmpty();
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

    @FunctionalInterface
    private interface SideIntersectionPredicate {
        boolean intersects(double travel);
    }

    private record TravelInterval(double min, double max) {
        private TravelInterval {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0.0D || max > 1.0D || min >= max) {
                throw new IllegalArgumentException("Side projection travel interval must be inside [0, 1]");
            }
        }
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
            int tileV
    ) {
    }

    private VoxelSpotProjector() {
    }
}
