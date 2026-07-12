package io.github.yoglappland.spectralization.command;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPerformanceTracker;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class SpotProjectionTestHarnessVerifier {
    public static void main(String[] args) {
        SpotProjectionTestCommand.validateDirectionMatrixDefinition();
        SpotProjectionTestCommand.validateRandomStressDefinition();
        SpotProjectionTestCommand.validateSmartSuiteDefinition();
        SpotProjectionTestCommand.validateLoadVariants();
        verifyDefaultModeSelection();
        SpotProjectionTestScene.validateControlledVolumeDefinition();
        verifyRelativeDirectionFrames();
        verifyCanonicalShapeSignature();
        verifyOutputFingerprintRotation();
    }

    private static void verifyDefaultModeSelection() {
        if (SpotTestMode.byName("") != SpotTestMode.SMART
                || SpotTestMode.byName("unknown") != SpotTestMode.SMART
                || SpotTestMode.SMART.next() != SpotTestMode.QUICK
                || SpotTestLoad.byName("") != SpotTestLoad.LIGHTWEIGHT
                || SpotTestLoad.byName("unknown") != SpotTestLoad.LIGHTWEIGHT
                || SpotTestLoad.LIGHTWEIGHT.toggle() != SpotTestLoad.STRESS
                || SpotTestLoad.STRESS.toggle() != SpotTestLoad.LIGHTWEIGHT) {
            throw new IllegalStateException("Spot-test mode or load defaults are not stable");
        }
    }

    private static void verifyCanonicalShapeSignature() {
        List<AABB> horizontalFirst = List.of(
                new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D),
                new AABB(0.0D, 0.5D, 0.0D, 0.5D, 1.0D, 1.0D)
        );
        List<AABB> verticalFirst = List.of(
                new AABB(0.0D, 0.0D, 0.0D, 0.5D, 1.0D, 1.0D),
                new AABB(0.5D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D)
        );
        long first = SpotProjectionTestScene.canonicalShapeSignatureForTest(horizontalFirst);
        long second = SpotProjectionTestScene.canonicalShapeSignatureForTest(verticalFirst);
        if (first != second) {
            throw new IllegalStateException("Canonical scene signature depends on AABB decomposition order");
        }
    }

    private static void verifyOutputFingerprintRotation() {
        SpotTestLayout north = new SpotTestLayout(BlockPos.ZERO, Direction.NORTH);
        SpotTestLayout east = new SpotTestLayout(BlockPos.ZERO, Direction.EAST);
        SpotRecord northSpot = visibleSpot(north.at(3, 2, 1), Direction.SOUTH)
                .withFootprintSlice(10, 20, 100, 120, 12, 24, 104, 124);
        SpotRecord eastSpot = visibleSpot(east.at(3, 2, 1), Direction.WEST)
                .withFootprintSlice(10, 20, 100, 120, 12, 24, 104, 124);
        var northFingerprint = SpotProjectionPerformanceTracker.fingerprintOutputForDiagnostics(
                BlockPos.ZERO, Direction.NORTH, List.of(northSpot)
        );
        var eastFingerprint = SpotProjectionPerformanceTracker.fingerprintOutputForDiagnostics(
                BlockPos.ZERO, Direction.EAST, List.of(eastSpot)
        );
        if (!northFingerprint.equals(eastFingerprint)) {
            throw new IllegalStateException("Output fingerprint is not rotation-normalized");
        }
    }

    private static SpotRecord visibleSpot(BlockPos pos, Direction face) {
        return new SpotRecord(
                pos,
                face,
                15,
                1,
                255,
                255,
                255,
                0,
                0,
                0,
                0,
                0,
                0
        );
    }

    private static void verifyRelativeDirectionFrames() {
        for (Direction forward : Direction.Plane.HORIZONTAL) {
            SpotTestLayout layout = new SpotTestLayout(BlockPos.ZERO, forward);
            Set<Direction> directions = new HashSet<>();
            for (int quarterTurn = 0; quarterTurn < 4; quarterTurn++) {
                directions.add(layout.relativeHorizontal(quarterTurn));
            }
            if (directions.size() != 4
                    || layout.relativeHorizontal(0) != forward
                    || layout.relativeHorizontal(1) != forward.getClockWise()
                    || layout.relativeHorizontal(2) != forward.getOpposite()
                    || layout.relativeHorizontal(3) != forward.getCounterClockWise()) {
                throw new IllegalStateException("Spot-test relative direction frame is not rotation-equivalent");
            }

            BlockPos point = layout.at(3, 2, 1);
            BlockPos expected = BlockPos.ZERO
                    .relative(forward, 3)
                    .relative(forward.getClockWise(), 2)
                    .above();
            if (!point.equals(expected)) {
                throw new IllegalStateException("Spot-test relative position frame is not rotation-equivalent");
            }
        }
    }

    private SpotProjectionTestHarnessVerifier() {
    }
}
