package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.LocalPoint;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.Patch;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.TexturePoint;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Direction;

public final class SpotProjectionFormalProof {
    private static final double EPSILON = 1.0E-9D;
    private static final double[] RADII = {0.72D, 1.0D, 1.37D, 1.88D, 2.65D, 3.5D};
    private static final int TILE_LIMIT = 4;

    public static void main(String[] args) {
        ProofResult result = prove();

        if (!result.failures().isEmpty()) {
            throw new IllegalStateException(String.join(System.lineSeparator(), result.failures()));
        }

        System.out.println(result.format());
    }

    static ProofResult prove() {
        List<String> failures = new ArrayList<>();
        Map<Direction, Integer> patchCounts = new EnumMap<>(Direction.class);
        int rawNegativePatches = 0;
        int checkedPatches = 0;

        for (Direction travelDirection : Direction.values()) {
            Direction displayFace = travelDirection.getOpposite();
            Direction uDirection = SpotSurfaceFrame.uDirection(displayFace);
            Direction vDirection = SpotSurfaceFrame.vDirection(displayFace);
            int directionCount = 0;

            for (double entryRadius : RADII) {
                for (double exitRadius : RADII) {
                    for (int tileV = -TILE_LIMIT; tileV <= TILE_LIMIT; tileV++) {
                        for (int tileU = -TILE_LIMIT; tileU <= TILE_LIMIT; tileU++) {
                            SideCheck left = checkUSide(
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    uDirection.getOpposite(),
                                    tileU - 0.5D,
                                    0.0D,
                                    tileV,
                                    entryRadius,
                                    exitRadius
                            );
                            SideCheck right = checkUSide(
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    uDirection,
                                    tileU + 0.5D,
                                    1.0D,
                                    tileV,
                                    entryRadius,
                                    exitRadius
                            );
                            SideCheck bottom = checkVSide(
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    vDirection.getOpposite(),
                                    tileV - 0.5D,
                                    0.0D,
                                    tileU,
                                    entryRadius,
                                    exitRadius
                            );
                            SideCheck top = checkVSide(
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    vDirection,
                                    tileV + 0.5D,
                                    1.0D,
                                    tileU,
                                    entryRadius,
                                    exitRadius
                            );

                            SideCheck[] checks = {left, right, bottom, top};
                            for (SideCheck check : checks) {
                                if (!check.valid()) {
                                    continue;
                                }

                                checkedPatches++;
                                directionCount++;

                                if (check.rawDot() < -EPSILON) {
                                    rawNegativePatches++;
                                }

                                if (check.orientedDot() <= EPSILON) {
                                    failures.add(check.label() + " did not orient toward face; raw="
                                            + check.rawDot() + " oriented=" + check.orientedDot());
                                }

                                if (!check.onFace()) {
                                    failures.add(check.label() + " produced a patch not lying on its target face.");
                                }

                                if (!check.textureInUnitSquare()) {
                                    failures.add(check.label() + " produced texture coordinates outside [0, 1].");
                                }
                            }
                        }
                    }
                }
            }

            patchCounts.put(travelDirection, directionCount);
        }

        int firstCount = patchCounts.get(Direction.values()[0]);
        for (Direction direction : Direction.values()) {
            int count = patchCounts.get(direction);
            if (count != firstCount) {
                failures.add("direction symmetry failed: " + direction.getSerializedName()
                        + " count=" + count + " expected=" + firstCount);
            }
        }

        int clippedGeometryChecks = proveClippedSideGeometry(failures);

        if (rawNegativePatches == 0) {
            failures.add("formal proof did not exercise any negative-winding side patch.");
        }

        if (clippedGeometryChecks == 0) {
            failures.add("formal proof did not exercise clipped side geometry.");
        }

        return new ProofResult(
                checkedPatches,
                rawNegativePatches,
                clippedGeometryChecks,
                Map.copyOf(patchCounts),
                failures
        );
    }

    private static int proveClippedSideGeometry(List<String> failures) {
        int checked = 0;

        for (Direction travelDirection : Direction.values()) {
            Direction displayFace = travelDirection.getOpposite();
            Direction uDirection = SpotSurfaceFrame.uDirection(displayFace);
            Direction vDirection = SpotSurfaceFrame.vDirection(displayFace);

            for (double entryRadius : RADII) {
                for (double exitRadius : RADII) {
                    for (int tileV = -TILE_LIMIT; tileV <= TILE_LIMIT; tileV++) {
                        for (int tileU = -TILE_LIMIT; tileU <= TILE_LIMIT; tileU++) {
                            checked += proveClippedUSideGeometry(
                                    failures,
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    uDirection.getOpposite(),
                                    tileU - 0.5D,
                                    0.0D,
                                    tileV,
                                    entryRadius,
                                    exitRadius
                            );
                            checked += proveClippedUSideGeometry(
                                    failures,
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    uDirection,
                                    tileU + 0.5D,
                                    1.0D,
                                    tileV,
                                    entryRadius,
                                    exitRadius
                            );
                            checked += proveClippedVSideGeometry(
                                    failures,
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    vDirection.getOpposite(),
                                    tileV - 0.5D,
                                    0.0D,
                                    tileU,
                                    entryRadius,
                                    exitRadius
                            );
                            checked += proveClippedVSideGeometry(
                                    failures,
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    vDirection,
                                    tileV + 0.5D,
                                    1.0D,
                                    tileU,
                                    entryRadius,
                                    exitRadius
                            );
                        }
                    }
                }
            }
        }

        return checked;
    }

    private static int proveClippedUSideGeometry(
            List<String> failures,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            Direction sideFace,
            double boundaryWorldU,
            double fixedULocal,
            int tileV,
            double entryRadius,
            double exitRadius
    ) {
        VoxelSpotProjector.SideCrossSection entry = VoxelSpotProjector.uSideCrossSection(entryRadius, boundaryWorldU, tileV);
        VoxelSpotProjector.SideCrossSection exit = VoxelSpotProjector.uSideCrossSection(exitRadius, boundaryWorldU, tileV);

        if (entry == null || exit == null) {
            return 0;
        }

        VoxelSpotProjector.CanonicalRect fullWindow = VoxelSpotProjector.sideCanonicalWindow(
                entry.axisCanonical(),
                entry.axisCanonical(),
                exit.axisCanonical(),
                exit.axisCanonical(),
                entry.minCanonical(),
                entry.maxCanonical(),
                exit.minCanonical(),
                exit.maxCanonical()
        );

        if (fullWindow == null) {
            return 0;
        }

        Patch full = VoxelSpotProjector.clippedUSidePatch(
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                fixedULocal,
                0.0D,
                1.0D,
                entry,
                exit,
                fullWindow
        );

        if (full == null) {
            failures.add("u-side clipped proof could not construct a full patch.");
            return 0;
        }

        int checked = 0;
        for (VoxelSpotProjector.CanonicalRect clippedWindow : clippedTestWindows(fullWindow)) {
            Patch clipped = VoxelSpotProjector.clippedUSidePatch(
                    sideFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    fixedULocal,
                    0.0D,
                    1.0D,
                    entry,
                    exit,
                    clippedWindow
            );
            checked += checkClippedPatch(failures, "u-side/" + travelDirection.getSerializedName(), full, clipped, clippedWindow);
        }

        return checked;
    }

    private static int proveClippedVSideGeometry(
            List<String> failures,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            Direction sideFace,
            double boundaryWorldV,
            double fixedVLocal,
            int tileU,
            double entryRadius,
            double exitRadius
    ) {
        VoxelSpotProjector.SideCrossSection entry = VoxelSpotProjector.vSideCrossSection(entryRadius, boundaryWorldV, tileU);
        VoxelSpotProjector.SideCrossSection exit = VoxelSpotProjector.vSideCrossSection(exitRadius, boundaryWorldV, tileU);

        if (entry == null || exit == null) {
            return 0;
        }

        VoxelSpotProjector.CanonicalRect fullWindow = VoxelSpotProjector.sideCanonicalWindow(
                entry.minCanonical(),
                entry.maxCanonical(),
                exit.minCanonical(),
                exit.maxCanonical(),
                entry.axisCanonical(),
                entry.axisCanonical(),
                exit.axisCanonical(),
                exit.axisCanonical()
        );

        if (fullWindow == null) {
            return 0;
        }

        Patch full = VoxelSpotProjector.clippedVSidePatch(
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                fixedVLocal,
                0.0D,
                1.0D,
                entry,
                exit,
                fullWindow
        );

        if (full == null) {
            failures.add("v-side clipped proof could not construct a full patch.");
            return 0;
        }

        int checked = 0;
        for (VoxelSpotProjector.CanonicalRect clippedWindow : clippedTestWindows(fullWindow)) {
            Patch clipped = VoxelSpotProjector.clippedVSidePatch(
                    sideFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    fixedVLocal,
                    0.0D,
                    1.0D,
                    entry,
                    exit,
                    clippedWindow
            );
            checked += checkClippedPatch(failures, "v-side/" + travelDirection.getSerializedName(), full, clipped, clippedWindow);
        }

        return checked;
    }

    private static List<VoxelSpotProjector.CanonicalRect> clippedTestWindows(VoxelSpotProjector.CanonicalRect fullWindow) {
        double midU = (fullWindow.minU() + fullWindow.maxU()) * 0.5D;
        double midV = (fullWindow.minV() + fullWindow.maxV()) * 0.5D;
        List<VoxelSpotProjector.CanonicalRect> windows = new ArrayList<>(4);
        windows.add(new VoxelSpotProjector.CanonicalRect(fullWindow.minU(), fullWindow.minV(), midU, fullWindow.maxV()));
        windows.add(new VoxelSpotProjector.CanonicalRect(midU, fullWindow.minV(), fullWindow.maxU(), fullWindow.maxV()));
        windows.add(new VoxelSpotProjector.CanonicalRect(fullWindow.minU(), fullWindow.minV(), fullWindow.maxU(), midV));
        windows.add(new VoxelSpotProjector.CanonicalRect(fullWindow.minU(), midV, fullWindow.maxU(), fullWindow.maxV()));
        return windows;
    }

    private static int checkClippedPatch(
            List<String> failures,
            String label,
            Patch full,
            Patch clipped,
            VoxelSpotProjector.CanonicalRect clippedWindow
    ) {
        if (clipped == null) {
            return 0;
        }

        double fullWorldArea = worldArea(full);
        double clippedWorldArea = worldArea(clipped);
        double fullTextureArea = textureArea(full);
        double clippedTextureArea = textureArea(clipped);
        double clippedWindowArea = (clippedWindow.maxU() - clippedWindow.minU())
                * (clippedWindow.maxV() - clippedWindow.minV());

        if (clippedWorldArea <= EPSILON || clippedTextureArea <= EPSILON) {
            failures.add(label + " clipped patch has no positive area.");
        }

        if (clippedWorldArea >= fullWorldArea - EPSILON) {
            failures.add(label + " clipped patch still occupies the full world side area.");
        }

        if (clippedTextureArea >= fullTextureArea - EPSILON) {
            failures.add(label + " clipped patch still occupies the full texture area.");
        }

        if (clippedTextureArea > clippedWindowArea + EPSILON) {
            failures.add(label + " clipped patch texture area exceeds its canonical clipping window.");
        }

        return 1;
    }

    private static SideCheck checkUSide(
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            Direction sideFace,
            double boundaryWorldU,
            double fixedULocal,
            int tileV,
            double entryRadius,
            double exitRadius
    ) {
        CrossSection entry = uSideCrossSection(entryRadius, boundaryWorldU, tileV);
        CrossSection exit = uSideCrossSection(exitRadius, boundaryWorldU, tileV);

        if (entry == null || exit == null) {
            return SideCheck.invalid();
        }

        LocalPoint p0 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 0.0D, fixedULocal, entry.minLocal());
        LocalPoint p1 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 0.0D, fixedULocal, entry.maxLocal());
        LocalPoint p2 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 1.0D, fixedULocal, exit.maxLocal());
        LocalPoint p3 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 1.0D, fixedULocal, exit.minLocal());
        TexturePoint t0 = new TexturePoint(entry.axisCanonical(), renderTextureV(entry.minCanonical()));
        TexturePoint t1 = new TexturePoint(entry.axisCanonical(), renderTextureV(entry.maxCanonical()));
        TexturePoint t2 = new TexturePoint(exit.axisCanonical(), renderTextureV(exit.maxCanonical()));
        TexturePoint t3 = new TexturePoint(exit.axisCanonical(), renderTextureV(exit.minCanonical()));
        return checkPatch("u-side/" + travelDirection.getSerializedName() + "/" + sideFace.getSerializedName(),
                sideFace, p0, t0, p1, t1, p2, t2, p3, t3);
    }

    private static SideCheck checkVSide(
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            Direction sideFace,
            double boundaryWorldV,
            double fixedVLocal,
            int tileU,
            double entryRadius,
            double exitRadius
    ) {
        CrossSection entry = vSideCrossSection(entryRadius, boundaryWorldV, tileU);
        CrossSection exit = vSideCrossSection(exitRadius, boundaryWorldV, tileU);

        if (entry == null || exit == null) {
            return SideCheck.invalid();
        }

        LocalPoint p0 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 0.0D, entry.minLocal(), fixedVLocal);
        LocalPoint p1 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 0.0D, entry.maxLocal(), fixedVLocal);
        LocalPoint p2 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 1.0D, exit.maxLocal(), fixedVLocal);
        LocalPoint p3 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 1.0D, exit.minLocal(), fixedVLocal);
        TexturePoint t0 = new TexturePoint(entry.minCanonical(), renderTextureV(entry.axisCanonical()));
        TexturePoint t1 = new TexturePoint(entry.maxCanonical(), renderTextureV(entry.axisCanonical()));
        TexturePoint t2 = new TexturePoint(exit.maxCanonical(), renderTextureV(exit.axisCanonical()));
        TexturePoint t3 = new TexturePoint(exit.minCanonical(), renderTextureV(exit.axisCanonical()));
        return checkPatch("v-side/" + travelDirection.getSerializedName() + "/" + sideFace.getSerializedName(),
                sideFace, p0, t0, p1, t1, p2, t2, p3, t3);
    }

    private static SideCheck checkPatch(
            String label,
            Direction sideFace,
            LocalPoint p0,
            TexturePoint t0,
            LocalPoint p1,
            TexturePoint t1,
            LocalPoint p2,
            TexturePoint t2,
            LocalPoint p3,
            TexturePoint t3
    ) {
        double rawDot = SpotProjectionPatch.faceNormalDot(sideFace, p0, p1, p2, p3);
        Patch patch = SpotProjectionPatch.oriented(sideFace, p0, t0, p1, t1, p2, t2, p3, t3);

        if (patch == null) {
            return new SideCheck(label, true, rawDot, Double.NaN, false, false);
        }

        double orientedDot = SpotProjectionPatch.faceNormalDot(sideFace, patch.p0(), patch.p1(), patch.p2(), patch.p3());
        boolean onFace = SpotProjectionPatch.liesOnFace(sideFace, patch.p0(), patch.p1(), patch.p2(), patch.p3());
        boolean textureInUnitSquare = textureInUnitSquare(patch.t0())
                && textureInUnitSquare(patch.t1())
                && textureInUnitSquare(patch.t2())
                && textureInUnitSquare(patch.t3());
        return new SideCheck(label, true, rawDot, orientedDot, onFace, textureInUnitSquare);
    }

    private static CrossSection uSideCrossSection(double radius, double boundaryWorldU, int tileV) {
        if (!Double.isFinite(radius) || radius <= 0.0D || Math.abs(boundaryWorldU) > radius) {
            return null;
        }

        double perpendicularLimit = perpendicularLimit(radius, boundaryWorldU);
        double tileMinV = tileV - 0.5D;
        double tileMaxV = tileV + 0.5D;
        double hitMinV = Math.max(tileMinV, -perpendicularLimit);
        double hitMaxV = Math.min(tileMaxV, perpendicularLimit);

        if (hitMaxV - hitMinV <= EPSILON) {
            return null;
        }

        return new CrossSection(
                clamp01(hitMinV - tileMinV),
                clamp01(hitMaxV - tileMinV),
                clamp01(canonicalAtWorldCoordinate(radius, boundaryWorldU)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMinV)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMaxV))
        );
    }

    private static CrossSection vSideCrossSection(double radius, double boundaryWorldV, int tileU) {
        if (!Double.isFinite(radius) || radius <= 0.0D || Math.abs(boundaryWorldV) > radius) {
            return null;
        }

        double perpendicularLimit = perpendicularLimit(radius, boundaryWorldV);
        double tileMinU = tileU - 0.5D;
        double tileMaxU = tileU + 0.5D;
        double hitMinU = Math.max(tileMinU, -perpendicularLimit);
        double hitMaxU = Math.min(tileMaxU, perpendicularLimit);

        if (hitMaxU - hitMinU <= EPSILON) {
            return null;
        }

        return new CrossSection(
                clamp01(hitMinU - tileMinU),
                clamp01(hitMaxU - tileMinU),
                clamp01(canonicalAtWorldCoordinate(radius, boundaryWorldV)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMinU)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMaxU))
        );
    }

    private static double perpendicularLimit(double radius, double fixedCoordinate) {
        double remaining = radius * radius - fixedCoordinate * fixedCoordinate;
        return remaining <= 0.0D ? 0.0D : Math.sqrt(remaining);
    }

    private static double canonicalAtWorldCoordinate(double radius, double worldCoordinate) {
        return (worldCoordinate + radius) / (radius * 2.0D);
    }

    private static double renderTextureV(double canonicalV) {
        return 1.0D - clamp01(canonicalV);
    }

    private static boolean textureInUnitSquare(TexturePoint texturePoint) {
        return texturePoint.u() >= 0.0D
                && texturePoint.u() <= 1.0D
                && texturePoint.v() >= 0.0D
                && texturePoint.v() <= 1.0D;
    }

    private static double worldArea(Patch patch) {
        return triangleArea(patch.p0(), patch.p1(), patch.p2())
                + triangleArea(patch.p0(), patch.p2(), patch.p3());
    }

    private static double textureArea(Patch patch) {
        return Math.abs(
                patch.t0().u() * patch.t1().v() - patch.t1().u() * patch.t0().v()
                        + patch.t1().u() * patch.t2().v() - patch.t2().u() * patch.t1().v()
                        + patch.t2().u() * patch.t3().v() - patch.t3().u() * patch.t2().v()
                        + patch.t3().u() * patch.t0().v() - patch.t0().u() * patch.t3().v()
        ) * 0.5D;
    }

    private static double triangleArea(LocalPoint a, LocalPoint b, LocalPoint c) {
        double abX = b.x() - a.x();
        double abY = b.y() - a.y();
        double abZ = b.z() - a.z();
        double acX = c.x() - a.x();
        double acY = c.y() - a.y();
        double acZ = c.z() - a.z();
        double crossX = abY * acZ - abZ * acY;
        double crossY = abZ * acX - abX * acZ;
        double crossZ = abX * acY - abY * acX;
        return Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) * 0.5D;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private record CrossSection(
            double minLocal,
            double maxLocal,
            double axisCanonical,
            double minCanonical,
            double maxCanonical
    ) {
    }

    private record SideCheck(
            String label,
            boolean valid,
            double rawDot,
            double orientedDot,
            boolean onFace,
            boolean textureInUnitSquare
    ) {
        private static SideCheck invalid() {
            return new SideCheck("", false, 0.0D, 0.0D, true, true);
        }
    }

    record ProofResult(
            int checkedPatches,
            int rawNegativePatches,
            int clippedGeometryChecks,
            Map<Direction, Integer> patchCounts,
            List<String> failures
    ) {
        ProofResult {
            patchCounts = Map.copyOf(patchCounts);
            failures = List.copyOf(failures);
        }

        String format() {
            return String.format(
                    Locale.ROOT,
                    "spot_projection_formal_proof checked=%d negative_winding=%d clipped_geometry=%d counts=%s",
                    checkedPatches,
                    rawNegativePatches,
                    clippedGeometryChecks,
                    patchCounts
            );
        }
    }

    private SpotProjectionFormalProof() {
    }
}
