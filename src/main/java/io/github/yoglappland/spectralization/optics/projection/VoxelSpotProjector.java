package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class VoxelSpotProjector {
    private static final double MIN_FRAGMENT_POWER = 0.0D;
    private static final double SIDE_FACE_VISUAL_FACTOR = 0.35D;
    private static final double EDGE_TOUCH_EPSILON = 1.0E-4D;
    private static final double CONE_SIDE_EPSILON = 1.0E-6D;
    private static final double VISUAL_DISTANCE_FADE_LINEAR = 0.08D;
    private static final double VISUAL_DISTANCE_FADE_QUADRATIC = 0.004D;
    private static final int MAX_PROJECTED_TILE_RADIUS = 6;
    private static final int MAX_PROJECTED_DEPTH = 32;
    private static final CanonicalRect FULL_FOOTPRINT = new CanonicalRect(0.0D, 0.0D, 1.0D, 1.0D);

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
        // Occupancy lives in canonical ray coordinates; each block face maps that window back to local surface coordinates.
        List<CanonicalRect> occupiedRayWindows = new ArrayList<>();

        for (int depth = 0; depth <= MAX_PROJECTED_DEPTH; depth++) {
            BeamEnvelope envelope = envelopeAtDepth(profileTemplate.envelope(), depth);
            double radius = envelope.radius();

            if (!Double.isFinite(radius) || radius <= 0.0D) {
                continue;
            }

            double exitRadius = envelopeAtOffset(envelope, 1.0D).radius();

            if (!Double.isFinite(exitRadius) || exitRadius <= 0.0D) {
                exitRadius = radius;
            }

            Direction displayFace = depth == 0 ? sourceFace : travelDirection.getOpposite();
            Direction uDirection = SpotSurfaceFrame.uDirection(displayFace);
            Direction vDirection = SpotSurfaceFrame.vDirection(displayFace);
            int tileRadius = Math.min(MAX_PROJECTED_TILE_RADIUS, (int) Math.ceil(Math.max(radius, exitRadius) + 0.5D));
            BeamPacket targetTemplate = profileTemplate.withEnvelope(envelope);
            List<CanonicalRect> frontBlockersAtDepth = new ArrayList<>();
            List<CanonicalRect> sideBlockersAtDepth = new ArrayList<>();
            BlockPos depthOrigin = sourcePos.relative(travelDirection, depth);

            for (int dv = -tileRadius; dv <= tileRadius; dv++) {
                for (int du = -tileRadius; du <= tileRadius; du++) {
                    ProjectionRect rect = projectionRect(radius, du, dv);
                    CanonicalRect sweptWindow = sweptProjectionWindow(radius, exitRadius, du, dv);

                    if (rect == null && sweptWindow == null) {
                        continue;
                    }

                    BlockPos targetPos = depthOrigin
                            .relative(uDirection, du)
                            .relative(vDirection, dv);
                    dependencies.add(targetPos.asLong());

                    if (!level.isLoaded(targetPos)) {
                        continue;
                    }

                    BlockState targetState = level.getBlockState(targetPos);

                    if (OpticalMaterialProfiles.isAirLike(targetState)) {
                        continue;
                    }

                    if (!isProjectableFullSurface(level, targetPos, targetState)) {
                        continue;
                    }

                    if (depth > 0 && sweptWindow != null) {
                        frontBlockersAtDepth.add(sweptWindow);
                    }

                    if (rect == null) {
                        continue;
                    }

                    List<ProjectionRect> visibleRects = visibleSubRects(radius, du, dv, rect, occupiedRayWindows);

                    if (visibleRects.isEmpty()) {
                        continue;
                    }

                    addDebugFaceCenter(targetPos, displayFace, fragments);

                    for (ProjectionRect visibleRect : visibleRects) {
                        double visualDistanceFactor = visualDistanceFactor(depth);
                        double fraction = SpotFootprintKernel.DEFAULT.integral(
                                visibleRect.kernelMinU(),
                                visibleRect.kernelMinV(),
                                visibleRect.kernelMaxU(),
                                visibleRect.kernelMaxV()
                        );

                        if (fraction <= 0.0D) {
                            continue;
                        }

                        double fragmentPower = beamPower * fraction;
                        double visualFragmentPower = fragmentPower * visualDistanceFactor;

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
                                coherentBeamPower * fraction * visualDistanceFactor,
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
                        occupiedRayWindows,
                        sideBlockersAtDepth,
                        dependencies,
                        allocations,
                        fragments
                );
            }

            for (CanonicalRect blocker : frontBlockersAtDepth) {
                occupiedRayWindows.add(blocker);
            }

            for (CanonicalRect blocker : sideBlockersAtDepth) {
                occupiedRayWindows.add(blocker);
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

                if (!isProjectableFullSurface(level, targetPos, targetState)) {
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

        if (openTileFacesCanonicalInterior(tileU, tileV, tileU - 1, tileV)) {
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
        }

        if (openTileFacesCanonicalInterior(tileU, tileV, tileU + 1, tileV)) {
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
        }

        if (openTileFacesCanonicalInterior(tileU, tileV, tileU, tileV - 1)) {
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
        }

        if (openTileFacesCanonicalInterior(tileU, tileV, tileU, tileV + 1)) {
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
    }

    private static boolean openTileFacesCanonicalInterior(int solidTileU, int solidTileV, int openTileU, int openTileV) {
        return tileDistanceFromAxis(openTileU, openTileV) + EDGE_TOUCH_EPSILON < tileDistanceFromAxis(solidTileU, solidTileV);
    }

    private static double tileDistanceFromAxis(int tileU, int tileV) {
        return Math.hypot(
                intervalDistanceFromAxis(tileU - 0.5D, tileU + 0.5D),
                intervalDistanceFromAxis(tileV - 0.5D, tileV + 0.5D)
        );
    }

    private static boolean isProjectableFullSurface(Level level, BlockPos pos, BlockState state) {
        return !OpticalMaterialProfiles.isAirLike(state) && state.isCollisionShapeFullBlock(level, pos);
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
        fragments.add(SpotRecord.debugFaceCenter(targetPos, face));
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
        double requiredRadius = Math.hypot(
                boundaryWorldU,
                intervalDistanceFromAxis(tileV - 0.5D, tileV + 0.5D)
        );
        TravelInterval visibleTravel = clipTravelByRadius(targetTemplate.envelope(), 0.0D, 1.0D, requiredRadius);

        if (visibleTravel == null) {
            return;
        }

        double crossTravel0 = nudgeVisibleTravelEndpoint(targetTemplate.envelope(), visibleTravel.min(), visibleTravel.max(), requiredRadius);
        double crossTravel1 = nudgeVisibleTravelEndpoint(targetTemplate.envelope(), visibleTravel.max(), visibleTravel.min(), requiredRadius);
        BeamEnvelope envelope0 = envelopeAtOffset(targetTemplate.envelope(), crossTravel0);
        BeamEnvelope envelope1 = envelopeAtOffset(targetTemplate.envelope(), crossTravel1);
        SideCrossSection cross0 = uSideCrossSection(envelope0.radius(), boundaryWorldU, tileV);
        SideCrossSection cross1 = uSideCrossSection(envelope1.radius(), boundaryWorldU, tileV);

        if (cross0 == null || cross1 == null) {
            return;
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
            return;
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
            return;
        }

        addDebugFaceCenter(targetPos, sideFace, fragments);

        double travel0 = lerp(entryTravel, exitTravel, crossTravel0);
        double travel1 = lerp(entryTravel, exitTravel, crossTravel1);

        for (CanonicalRect visibleSideWindow : visibleSideWindows) {
            double assignedArea = canonicalArea(visibleSideWindow);
            double sideFraction = integratedFraction(visibleSideWindow);
            double sidePower = beamPower * SIDE_FACE_VISUAL_FACTOR * visualDistanceFactor * sideFraction;

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

            PatchEmission emission = addUSideChartSpot(
                    level,
                    targetPos,
                    targetState,
                    sideFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    targetTemplate,
                    sidePower,
                    coherentBeamPower * SIDE_FACE_VISUAL_FACTOR * visualDistanceFactor * sideFraction,
                    fixedULocal,
                    travel0,
                    travel1,
                    cross0,
                    cross1,
                    visibleSideWindow,
                    fragments
            );

            if (emission.emitted()) {
                sideBlockersAtDepth.add(visibleSideWindow);
            }

            allocations.add(sideAllocation(
                    targetPos,
                    sideFace,
                    "u-side",
                    candidateArea,
                    assignedArea,
                    emission.emitted() ? assignedArea : 0.0D,
                    sideFraction,
                    emission.emitted() ? sideFraction : 0.0D,
                    emission.emitted() ? 1 : 0,
                    emission.result()
            ));
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
        double requiredRadius = Math.hypot(
                boundaryWorldV,
                intervalDistanceFromAxis(tileU - 0.5D, tileU + 0.5D)
        );
        TravelInterval visibleTravel = clipTravelByRadius(targetTemplate.envelope(), 0.0D, 1.0D, requiredRadius);

        if (visibleTravel == null) {
            return;
        }

        double crossTravel0 = nudgeVisibleTravelEndpoint(targetTemplate.envelope(), visibleTravel.min(), visibleTravel.max(), requiredRadius);
        double crossTravel1 = nudgeVisibleTravelEndpoint(targetTemplate.envelope(), visibleTravel.max(), visibleTravel.min(), requiredRadius);
        BeamEnvelope envelope0 = envelopeAtOffset(targetTemplate.envelope(), crossTravel0);
        BeamEnvelope envelope1 = envelopeAtOffset(targetTemplate.envelope(), crossTravel1);
        SideCrossSection cross0 = vSideCrossSection(envelope0.radius(), boundaryWorldV, tileU);
        SideCrossSection cross1 = vSideCrossSection(envelope1.radius(), boundaryWorldV, tileU);

        if (cross0 == null || cross1 == null) {
            return;
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
            return;
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
            return;
        }

        addDebugFaceCenter(targetPos, sideFace, fragments);

        double travel0 = lerp(entryTravel, exitTravel, crossTravel0);
        double travel1 = lerp(entryTravel, exitTravel, crossTravel1);

        for (CanonicalRect visibleSideWindow : visibleSideWindows) {
            double assignedArea = canonicalArea(visibleSideWindow);
            double sideFraction = integratedFraction(visibleSideWindow);
            double sidePower = beamPower * SIDE_FACE_VISUAL_FACTOR * visualDistanceFactor * sideFraction;

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

            PatchEmission emission = addVSideChartSpot(
                    level,
                    targetPos,
                    targetState,
                    sideFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    targetTemplate,
                    sidePower,
                    coherentBeamPower * SIDE_FACE_VISUAL_FACTOR * visualDistanceFactor * sideFraction,
                    fixedVLocal,
                    travel0,
                    travel1,
                    cross0,
                    cross1,
                    visibleSideWindow,
                    fragments
            );

            if (emission.emitted()) {
                sideBlockersAtDepth.add(visibleSideWindow);
            }

            allocations.add(sideAllocation(
                    targetPos,
                    sideFace,
                    "v-side",
                    candidateArea,
                    assignedArea,
                    emission.emitted() ? assignedArea : 0.0D,
                    sideFraction,
                    emission.emitted() ? sideFraction : 0.0D,
                    emission.emitted() ? 1 : 0,
                    emission.result()
            ));
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

        double radius0 = envelopeAtOffset(envelope, t0).radius();
        double radius1 = envelopeAtOffset(envelope, t1).radius();

        if (!Double.isFinite(radius0) || !Double.isFinite(radius1) || radius0 <= 0.0D || radius1 <= 0.0D) {
            return null;
        }

        boolean inside0 = radius0 + EDGE_TOUCH_EPSILON >= requiredRadius;
        boolean inside1 = radius1 + EDGE_TOUCH_EPSILON >= requiredRadius;

        if (inside0 && inside1) {
            return new TravelInterval(t0, t1);
        }

        if (!inside0 && !inside1) {
            return null;
        }

        double radiusDelta = radius1 - radius0;

        if (Math.abs(radiusDelta) <= CONE_SIDE_EPSILON) {
            return null;
        }

        double crossing = lerp(t0, t1, clamp01((requiredRadius - radius0) / radiusDelta));

        if (inside0) {
            return crossing - t0 <= EDGE_TOUCH_EPSILON ? null : new TravelInterval(t0, crossing);
        }

        return t1 - crossing <= EDGE_TOUCH_EPSILON ? null : new TravelInterval(crossing, t1);
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

        double perpendicularLimit = perpendicularLimit(radius, boundaryWorldU);
        if (perpendicularLimit <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double tileMinV = tileV - 0.5D;
        double tileMaxV = tileV + 0.5D;
        double hitMinV = Math.max(tileMinV, -perpendicularLimit);
        double hitMaxV = Math.min(tileMaxV, perpendicularLimit);

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

        double perpendicularLimit = perpendicularLimit(radius, boundaryWorldV);
        if (perpendicularLimit <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double tileMinU = tileU - 0.5D;
        double tileMaxU = tileU + 0.5D;
        double hitMinU = Math.max(tileMinU, -perpendicularLimit);
        double hitMaxU = Math.min(tileMaxU, perpendicularLimit);

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

    private static PatchEmission addUSideChartSpot(
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
            CanonicalRect visibleWindow,
            List<SpotRecord> fragments
    ) {
        Patch patch = clippedUSidePatch(
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

        if (patch == null) {
            return PatchEmission.PATCH_NULL;
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
        );
    }

    private static PatchEmission addVSideChartSpot(
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
            CanonicalRect visibleWindow,
            List<SpotRecord> fragments
    ) {
        Patch patch = clippedVSidePatch(
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

        if (patch == null) {
            return PatchEmission.PATCH_NULL;
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
            String result
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
                result
        );
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
        ParameterRange travelRange = sideParameterRange(
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

        if (travelRange == null) {
            return null;
        }

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
        ParameterRange travelRange = sideParameterRange(
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

        if (travelRange == null) {
            return null;
        }

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
        double minCanonical = lerp(cross0.minCanonical(), cross1.minCanonical(), travelParameter);
        double maxCanonical = lerp(cross0.maxCanonical(), cross1.maxCanonical(), travelParameter);
        double minLocal = lerp(cross0.minLocal(), cross1.minLocal(), travelParameter);
        double maxLocal = lerp(cross0.maxLocal(), cross1.maxLocal(), travelParameter);
        double clippedMinCanonical = Math.max(minCanonical, windowMin);
        double clippedMaxCanonical = Math.min(maxCanonical, windowMax);

        if (clippedMaxCanonical - clippedMinCanonical <= EDGE_TOUCH_EPSILON) {
            return null;
        }

        double canonicalSpan = maxCanonical - minCanonical;

        if (canonicalSpan <= EDGE_TOUCH_EPSILON) {
            return null;
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
        CrossClip exact = clippedCrossSectionAt(cross0, cross1, travelParameter, windowMin, windowMax);

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

    private static double perpendicularLimit(double radius, double fixedCoordinate) {
        double remaining = radius * radius - fixedCoordinate * fixedCoordinate;

        if (remaining <= 0.0D) {
            return 0.0D;
        }

        return Math.sqrt(remaining);
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

    private static double visualDistanceFactor(int depth) {
        if (depth <= 0) {
            return 1.0D;
        }

        double d = depth;
        return 1.0D / (1.0D + VISUAL_DISTANCE_FADE_LINEAR * d + VISUAL_DISTANCE_FADE_QUADRATIC * d * d);
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

    private static CanonicalRect sweptProjectionWindow(double entryRadius, double exitRadius, int tileU, int tileV) {
        ProjectionRect entryRect = projectionRect(entryRadius, tileU, tileV);
        ProjectionRect exitRect = projectionRect(exitRadius, tileU, tileV);

        if (entryRect == null && exitRect == null) {
            return null;
        }

        if (entryRect == null) {
            return exitRect.canonicalRect();
        }

        if (exitRect == null) {
            return entryRect.canonicalRect();
        }

        CanonicalRect entry = entryRect.canonicalRect();
        CanonicalRect exit = exitRect.canonicalRect();
        return canonicalRectOrNull(
                Math.min(entry.minU(), exit.minU()),
                Math.min(entry.minV(), exit.minV()),
                Math.max(entry.maxU(), exit.maxU()),
                Math.max(entry.maxV(), exit.maxV())
        );
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

            if (minLocal >= maxLocal || minCanonical >= maxCanonical) {
                throw new IllegalArgumentException("Projection cross clip must have positive width");
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

    private VoxelSpotProjector() {
    }
}
