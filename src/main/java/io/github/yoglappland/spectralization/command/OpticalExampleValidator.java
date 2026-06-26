package io.github.yoglappland.spectralization.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
import io.github.yoglappland.spectralization.block.FiberOpticInterfaceBlock;
import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.block.MirrorBlock;
import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutput;
import io.github.yoglappland.spectralization.optics.cache.ReceiverOutputKind;
import io.github.yoglappland.spectralization.optics.compiler.BeamProfileSource;
import io.github.yoglappland.spectralization.optics.compiler.CompiledPortGraph;
import io.github.yoglappland.spectralization.optics.compiler.CompiledReadoutLayer;
import io.github.yoglappland.spectralization.optics.compiler.OpticalCompilerDebugLogger;
import io.github.yoglappland.spectralization.optics.compiler.OpticalReadoutLayerCompiler;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphCompiler;
import io.github.yoglappland.spectralization.optics.compiler.PortGraphNode;
import io.github.yoglappland.spectralization.optics.compiler.ProfileLanePowerSolver;
import io.github.yoglappland.spectralization.optics.compiler.ScalarPowerSolution;
import io.github.yoglappland.spectralization.optics.compiler.ScalarSolverKind;
import io.github.yoglappland.spectralization.optics.compiler.SpectralPowerLane;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkData;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeKind;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeProfile;
import io.github.yoglappland.spectralization.optics.fiber.FiberRoute;
import io.github.yoglappland.spectralization.optics.lens.LensProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class OpticalExampleValidator {
    private static final Direction DEFAULT_TEST_DIRECTION = Direction.EAST;
    private static final double SOURCE_POWER = 1.0D;
    private static final double BEAM_SPLITTER_TRANSMITTANCE = 0.5D;
    private static final double BEAM_SPLITTER_REFLECTANCE = 0.5D;
    private static final double LENS_TRANSMITTANCE = LensProfile.STANDARD.transmittance();
    private static final double AIR_PROPAGATION_FACTOR = 0.995D;
    private static final double POWER_TOLERANCE = 1.0E-6D;
    private static final int DEFAULT_RADIUS_MILLI = 250;
    private static final int NARROW_FIBER_RADIUS_MILLI = 125;
    private static final int WIDE_FIBER_RADIUS_MILLI = 250;
    private static final int WIDE_LENS_RADIUS_MILLI = 2000;
    private static final int BEAM_EXPANDER_SOURCE_RADIUS_MILLI = 125;
    private static final int BEAM_EXPANDER_FIRST_LENS_DISTANCE = 2;
    private static final int BEAM_EXPANDER_SECOND_LENS_DISTANCE = 22;
    private static final int BEAM_EXPANDER_INPUT_DISTANCE = 64;
    private static final double BEAM_EXPANDER_MIN_IMPROVEMENT = 2.0D;
    private static final double BEAM_EXPANDER_MAX_IMPROVEMENT = 20.0D;

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("opticaltest")
                .executes(context -> listTests(context.getSource()))
                .then(Commands.literal("1")
                        .executes(context -> runSplitterLensSplitter(context.getSource())))
                .then(Commands.literal("splitter_lens_splitter")
                        .executes(context -> runSplitterLensSplitter(context.getSource())))
                .then(Commands.literal("2")
                        .executes(context -> runLensApertureClip(context.getSource())))
                .then(Commands.literal("lens_aperture_clip")
                        .executes(context -> runLensApertureClip(context.getSource())))
                .then(Commands.literal("3")
                        .executes(context -> runFiberRadiusCoupling(context.getSource())))
                .then(Commands.literal("fiber_radius_coupling")
                        .executes(context -> runFiberRadiusCoupling(context.getSource())))
                .then(Commands.literal("4")
                        .executes(context -> runFeedbackFiberRadiusLoss(context.getSource())))
                .then(Commands.literal("feedback_fiber_radius_loss")
                        .executes(context -> runFeedbackFiberRadiusLoss(context.getSource())))
                .then(Commands.literal("5")
                        .executes(context -> runParallelFiberSameEndpoint(context.getSource())))
                .then(Commands.literal("parallel_fiber_same_endpoint")
                        .executes(context -> runParallelFiberSameEndpoint(context.getSource())))
                .then(Commands.literal("6")
                        .executes(context -> runBeamExpanderFiberCoupling(context.getSource())))
                .then(Commands.literal("beam_expander_fiber_coupling")
                        .executes(context -> runBeamExpanderFiberCoupling(context.getSource())));
    }

    private static int listTests(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Optical tests: 1=splitter_lens_splitter, 2=lens_aperture_clip, "
                        + "3=fiber_radius_coupling, 4=feedback_fiber_radius_loss, "
                        + "5=parallel_fiber_same_endpoint, 6=beam_expander_fiber_coupling"
        ), false);
        return 1;
    }

    private static int runSplitterLensSplitter(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Direction testDirection = source.getEntity() instanceof ServerPlayer player
                ? player.getDirection()
                : DEFAULT_TEST_DIRECTION;
        if (!testDirection.getAxis().isHorizontal()) {
            testDirection = DEFAULT_TEST_DIRECTION;
        }
        BlockPos origin = BlockPos.containing(source.getPosition())
                .relative(testDirection, 3)
                .above();
        TestLayout layout = new TestLayout(origin, testDirection);

        placeSplitterLensSplitter(level, layout);
        TestSample sample = sample(level, layout.source(), layout.profiler());
        double expectedPower = expectedSplitterLensSplitterPower();
        ValidationReport report = validateSplitterLensSplitter(sample, expectedPower);
        logValidation(level, "splitter_lens_splitter", layout, sample, report, expectedPower);

        if (report.passed()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Optical test splitter_lens_splitter PASS at %s: profiler=%.9f expected=%.9f graph=%d/%d feedback_scc=%d solver=%s fallback=%s overflow=%s lanes=%d",
                    layout.source().toShortString(),
                    report.actualPower(),
                    expectedPower,
                    sample.graph().nodes().size(),
                    sample.graph().edges().size(),
                    sample.graph().feedbackSccCount(),
                    sample.solution().solverKind(),
                    sample.solution().profileCollapsedFallback(),
                    sample.solution().profileOverflow(),
                    sample.solution().powerByLane().size()
            )), true);
            return 1;
        }

        source.sendFailure(Component.literal(String.format(
                "Optical test splitter_lens_splitter FAIL at %s: %s profiler=%.9f expected=%.9f graph=%d/%d feedback_scc=%d solver=%s fallback=%s overflow=%s reliable=%s",
                layout.source().toShortString(),
                report.message(),
                report.actualPower(),
                expectedPower,
                sample.graph().nodes().size(),
                sample.graph().edges().size(),
                sample.graph().feedbackSccCount(),
                sample.solution().solverKind(),
                sample.solution().profileCollapsedFallback(),
                sample.solution().profileOverflow(),
                sample.solution().reliableForReadout()
        )));
        return 0;
    }

    private static int runLensApertureClip(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Direction testDirection = horizontalTestDirection(source);
        TestLayout layout = new TestLayout(BlockPos.containing(source.getPosition()).relative(testDirection, 3).above(), testDirection);
        placeLensApertureClip(level, layout);
        TestSample sample = sample(level, layout.source(), layout.profiler());
        double apertureGain = BeamProfileKey.collimated(WIDE_LENS_RADIUS_MILLI / 1000.0D)
                .toShape()
                .propagate(2)
                .toKey()
                .toShape()
                .thinLens(
                        LensProfile.STANDARD.focalLengthBlocks(),
                        LensProfile.STANDARD.aperture() / 100.0D,
                        1.15D
                )
                .gain();
        double expectedPower = SOURCE_POWER
                * Math.pow(AIR_PROPAGATION_FACTOR, 8)
                * LENS_TRANSMITTANCE
                * apertureGain;
        ValidationReport report = validateLensApertureClip(sample, expectedPower, apertureGain);
        logValidation(level, "lens_aperture_clip", layout, sample, report, expectedPower);

        if (report.passed()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Optical test lens_aperture_clip PASS at %s: profiler=%.9f expected=%.9f aperture_gain=%.6f",
                    layout.source().toShortString(),
                    report.actualPower(),
                    expectedPower,
                    apertureGain
            )), true);
            return 1;
        }

        source.sendFailure(Component.literal(String.format(
                "Optical test lens_aperture_clip FAIL at %s: %s profiler=%.9f expected=%.9f solver=%s reliable=%s",
                layout.source().toShortString(),
                report.message(),
                report.actualPower(),
                expectedPower,
                sample.solution().solverKind(),
                sample.solution().reliableForReadout()
        )));
        return 0;
    }

    private static int runFiberRadiusCoupling(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Direction testDirection = horizontalTestDirection(source);
        FiberTestLayout layout = new FiberTestLayout(
                BlockPos.containing(source.getPosition()).relative(testDirection, 3).above(),
                testDirection
        );
        placeFiberLine(level, layout, 1, NARROW_FIBER_RADIUS_MILLI);
        TestSample narrow = sample(level, layout.source(), layout.profiler());
        configureSource(level, layout.source(), WIDE_FIBER_RADIUS_MILLI);
        TestSample wide = sample(level, layout.source(), layout.profiler());
        double ratio = narrow.actualPower() <= 0.0D ? 0.0D : wide.actualPower() / narrow.actualPower();
        ValidationReport report = validateFiberRadiusCoupling(narrow, wide, ratio);
        logValidation(level, "fiber_radius_coupling", layout.asTestLayout(), wide, report, narrow.actualPower() * 0.25D);

        if (report.passed()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Optical test fiber_radius_coupling PASS at %s: narrow=%.9f wide=%.9f ratio=%.6f",
                    layout.source().toShortString(),
                    narrow.actualPower(),
                    wide.actualPower(),
                    ratio
            )), true);
            return 1;
        }

        source.sendFailure(Component.literal(String.format(
                "Optical test fiber_radius_coupling FAIL at %s: %s narrow=%.9f wide=%.9f ratio=%.6f",
                layout.source().toShortString(),
                report.message(),
                narrow.actualPower(),
                wide.actualPower(),
                ratio
        )));
        return 0;
    }

    private static int runFeedbackFiberRadiusLoss(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Direction testDirection = horizontalTestDirection(source);
        FiberTestLayout layout = new FiberTestLayout(
                BlockPos.containing(source.getPosition()).relative(testDirection, 3).above(),
                testDirection
        );
        placeFeedbackFiberLoop(level, layout, NARROW_FIBER_RADIUS_MILLI);
        TestSample narrow = sample(level, layout.source(), layout.profiler());
        configureSource(level, layout.source(), WIDE_FIBER_RADIUS_MILLI);
        TestSample wide = sample(level, layout.source(), layout.profiler());
        double ratio = narrow.actualPower() <= 0.0D ? 0.0D : wide.actualPower() / narrow.actualPower();
        ValidationReport report = validateFeedbackFiberRadiusLoss(narrow, wide, ratio);
        logValidation(level, "feedback_fiber_radius_loss", layout.asTestLayout(), wide, report, narrow.actualPower() * 0.25D);

        if (report.passed()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Optical test feedback_fiber_radius_loss PASS at %s: narrow=%.9f wide=%.9f ratio=%.6f feedback_scc=%d",
                    layout.source().toShortString(),
                    narrow.actualPower(),
                    wide.actualPower(),
                    ratio,
                    wide.graph().feedbackSccCount()
            )), true);
            return 1;
        }

        source.sendFailure(Component.literal(String.format(
                "Optical test feedback_fiber_radius_loss FAIL at %s: %s narrow=%.9f wide=%.9f ratio=%.6f feedback_scc=%d solver=%s",
                layout.source().toShortString(),
                report.message(),
                narrow.actualPower(),
                wide.actualPower(),
                ratio,
                wide.graph().feedbackSccCount(),
                wide.solution().solverKind()
        )));
        return 0;
    }

    private static int runParallelFiberSameEndpoint(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Direction testDirection = horizontalTestDirection(source);
        FiberTestLayout layout = new FiberTestLayout(
                BlockPos.containing(source.getPosition()).relative(testDirection, 3).above(),
                testDirection
        );
        placeFiberLine(level, layout, 1, WIDE_FIBER_RADIUS_MILLI);
        TestSample single = sample(level, layout.source(), layout.profiler());
        placeFiberLine(level, layout, 2, WIDE_FIBER_RADIUS_MILLI);
        TestSample parallel = sample(level, layout.source(), layout.profiler());
        ValidationReport report = validateParallelFiberSameEndpoint(single, parallel);
        logValidation(level, "parallel_fiber_same_endpoint", layout.asTestLayout(), parallel, report, single.actualPower());

        if (report.passed()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Optical test parallel_fiber_same_endpoint PASS at %s: single=%.9f parallel=%.9f",
                    layout.source().toShortString(),
                    single.actualPower(),
                    parallel.actualPower()
            )), true);
            return 1;
        }

        source.sendFailure(Component.literal(String.format(
                "Optical test parallel_fiber_same_endpoint FAIL at %s: %s single=%.9f parallel=%.9f",
                layout.source().toShortString(),
                report.message(),
                single.actualPower(),
                parallel.actualPower()
        )));
        return 0;
    }

    private static int runBeamExpanderFiberCoupling(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Direction testDirection = horizontalTestDirection(source);
        BeamExpanderTestLayout layout = new BeamExpanderTestLayout(
                BlockPos.containing(source.getPosition()).relative(testDirection, 3).above(),
                testDirection
        );
        placeBeamExpanderFiberLine(level, layout, false);
        TestSample direct = sample(level, layout.source(), layout.profiler());
        placeBeamExpanderFiberLine(level, layout, true);
        TestSample expanded = sample(level, layout.source(), layout.profiler());
        double ratio = direct.actualPower() <= 0.0D ? 0.0D : expanded.actualPower() / direct.actualPower();
        ValidationReport report = validateBeamExpanderFiberCoupling(direct, expanded, ratio);
        logValidation(
                level,
                "beam_expander_fiber_coupling",
                layout.asTestLayout(),
                expanded,
                report,
                direct.actualPower() * BEAM_EXPANDER_MIN_IMPROVEMENT
        );

        if (report.passed()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Optical test beam_expander_fiber_coupling PASS at %s: direct=%.9f expanded=%.9f ratio=%.6f",
                    layout.source().toShortString(),
                    direct.actualPower(),
                    expanded.actualPower(),
                    ratio
            )), true);
            return 1;
        }

        source.sendFailure(Component.literal(String.format(
                "Optical test beam_expander_fiber_coupling FAIL at %s: %s direct=%.9f expanded=%.9f ratio=%.6f solver=%s reliable=%s",
                layout.source().toShortString(),
                report.message(),
                direct.actualPower(),
                expanded.actualPower(),
                ratio,
                expanded.solution().solverKind(),
                expanded.solution().reliableForReadout()
        )));
        return 0;
    }

    private static void placeSplitterLensSplitter(ServerLevel level, TestLayout layout) {
        clearTestVolume(level, layout.origin(), layout.direction());

        level.setBlock(
                layout.source(),
                Spectralization.CREATIVE_LIGHT_SOURCE.get()
                        .defaultBlockState()
                        .setValue(CreativeLightSourceBlock.FACING, layout.direction()),
                3
        );
        configureSource(level, layout.source(), DEFAULT_RADIUS_MILLI);

        BlockState splitter = Spectralization.BEAM_SPLITTER.get()
                .defaultBlockState()
                .setValue(MirrorBlock.ROTATION, splitterRotation(layout.direction()));
        level.setBlock(layout.firstSplitter(), splitter, 3);
        level.setBlock(
                layout.lens(),
                Spectralization.LENS_HOLDER.get()
                        .defaultBlockState()
                        .setValue(LensHolderBlock.FACING, layout.direction()),
                3
        );
        if (level.getBlockEntity(layout.lens()) instanceof LensHolderBlockEntity lensHolder) {
            lensHolder.setLens(LensProfile.STANDARD.createStack());
        }
        level.setBlock(layout.secondSplitter(), splitter, 3);
        level.setBlock(layout.profilerSupport(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(
                layout.profiler(),
                Spectralization.BEAM_PROFILER.get()
                        .defaultBlockState()
                        .setValue(BeamProfilerBlock.FACING, layout.direction()),
                3
        );
    }

    private static void placeLensApertureClip(ServerLevel level, TestLayout layout) {
        clearTestVolume(level, layout.origin(), layout.direction());

        level.setBlock(
                layout.source(),
                Spectralization.CREATIVE_LIGHT_SOURCE.get()
                        .defaultBlockState()
                        .setValue(CreativeLightSourceBlock.FACING, layout.direction()),
                3
        );
        configureSource(level, layout.source(), WIDE_LENS_RADIUS_MILLI);
        level.setBlock(
                layout.firstSplitter(),
                Spectralization.LENS_HOLDER.get()
                        .defaultBlockState()
                        .setValue(LensHolderBlock.FACING, layout.direction()),
                3
        );
        if (level.getBlockEntity(layout.firstSplitter()) instanceof LensHolderBlockEntity lensHolder) {
            lensHolder.setLens(LensProfile.STANDARD.createStack());
        }
        level.setBlock(layout.profilerSupport(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(
                layout.profiler(),
                Spectralization.BEAM_PROFILER.get()
                        .defaultBlockState()
                        .setValue(BeamProfilerBlock.FACING, layout.direction()),
                3
        );
    }

    private static void placeFiberLine(ServerLevel level, FiberTestLayout layout, int parallelRoutes, int radiusMilli) {
        clearTestVolume(level, layout.origin(), layout.direction());
        removeFiberConnections(level, layout.inputInterface(), layout.outputInterface());

        level.setBlock(
                layout.source(),
                Spectralization.CREATIVE_LIGHT_SOURCE.get()
                        .defaultBlockState()
                        .setValue(CreativeLightSourceBlock.FACING, layout.direction()),
                3
        );
        configureSource(level, layout.source(), radiusMilli);
        placeFiberInterface(level, layout.inputInterface(), layout.direction().getOpposite());
        placeFiberInterface(level, layout.outputInterface(), layout.direction());
        addFiberRoutes(level, layout.inputInterface(), layout.outputInterface(), parallelRoutes);
        level.setBlock(layout.profilerSupport(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(
                layout.profiler(),
                Spectralization.BEAM_PROFILER.get()
                        .defaultBlockState()
                        .setValue(BeamProfilerBlock.FACING, layout.direction()),
                3
        );
    }

    private static void placeFeedbackFiberLoop(ServerLevel level, FiberTestLayout layout, int radiusMilli) {
        clearTestVolume(level, layout.origin(), layout.direction());
        removeFiberConnections(level, layout.inputInterface(), layout.outputInterface());

        level.setBlock(
                layout.source(),
                Spectralization.CREATIVE_LIGHT_SOURCE.get()
                        .defaultBlockState()
                        .setValue(CreativeLightSourceBlock.FACING, layout.direction()),
                3
        );
        configureSource(level, layout.source(), radiusMilli);
        BlockState splitter = Spectralization.BEAM_SPLITTER.get()
                .defaultBlockState()
                .setValue(MirrorBlock.ROTATION, splitterRotation(layout.direction()));
        level.setBlock(layout.firstSplitter(), splitter, 3);
        placeFiberInterface(level, layout.inputInterface(), layout.direction().getOpposite());
        placeFiberInterface(level, layout.outputInterface(), layout.direction());
        addFiberRoutes(level, layout.inputInterface(), layout.outputInterface(), 1);
        level.setBlock(layout.secondSplitter(), splitter, 3);
        level.setBlock(layout.profilerSupport(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(
                layout.profiler(),
                Spectralization.BEAM_PROFILER.get()
                        .defaultBlockState()
                        .setValue(BeamProfilerBlock.FACING, layout.direction()),
                3
        );
    }

    private static void placeBeamExpanderFiberLine(ServerLevel level, BeamExpanderTestLayout layout, boolean expanded) {
        clearTestVolume(level, layout.origin(), layout.direction(), layout.profilerSupportDistance() + 2);
        removeFiberConnections(level, layout.inputInterface(), layout.outputInterface());

        level.setBlock(
                layout.source(),
                Spectralization.CREATIVE_LIGHT_SOURCE.get()
                        .defaultBlockState()
                        .setValue(CreativeLightSourceBlock.FACING, layout.direction()),
                3
        );
        configureSource(level, layout.source(), BEAM_EXPANDER_SOURCE_RADIUS_MILLI);

        if (expanded) {
            placeLensHolder(level, layout.firstLens(), layout.direction(), LensProfile.preset("short"));
            placeLensHolder(level, layout.secondLens(), layout.direction(), LensProfile.preset("long"));
        }

        placeFiberInterface(level, layout.inputInterface(), layout.direction().getOpposite());
        placeFiberInterface(level, layout.outputInterface(), layout.direction());
        addFiberRoutes(level, layout.inputInterface(), layout.outputInterface(), 1);
        level.setBlock(layout.profilerSupport(), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
        level.setBlock(
                layout.profiler(),
                Spectralization.BEAM_PROFILER.get()
                        .defaultBlockState()
                        .setValue(BeamProfilerBlock.FACING, layout.direction()),
                3
        );
    }

    private static void placeLensHolder(ServerLevel level, BlockPos pos, Direction direction, LensProfile lensProfile) {
        level.setBlock(
                pos,
                Spectralization.LENS_HOLDER.get()
                        .defaultBlockState()
                        .setValue(LensHolderBlock.FACING, direction),
                3
        );

        if (level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder) {
            lensHolder.setLens(lensProfile.createStack());
        }
    }

    private static void placeFiberInterface(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlock(
                pos,
                Spectralization.FIBER_OPTIC_INTERFACE.get()
                        .defaultBlockState()
                        .setValue(FiberOpticInterfaceBlock.FACING, facing),
                3
        );
        FiberNetworkIndex.registerNode(level, pos, FiberNodeKind.INTERFACE, FiberNodeProfile.BASIC_INTERFACE);
    }

    private static void removeFiberConnections(ServerLevel level, BlockPos input, BlockPos output) {
        FiberNetworkData.removeConnectionsTouching(level, input);
        FiberNetworkData.removeConnectionsTouching(level, output);
    }

    private static void addFiberRoutes(ServerLevel level, BlockPos input, BlockPos output, int count) {
        for (int index = 0; index < Math.max(1, count); index++) {
            FiberNetworkData.addConnection(level, FiberRoute.fromNodes(List.of(input, output)));
        }
    }

    private static void clearTestVolume(ServerLevel level, BlockPos origin, Direction direction) {
        clearTestVolume(level, origin, direction, 12);
    }

    private static void clearTestVolume(ServerLevel level, BlockPos origin, Direction direction, int maxAlong) {
        Direction lateral = direction.getClockWise();
        for (int along = -4; along <= Math.max(12, maxAlong); along++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int side = -8; side <= 8; side++) {
                    level.setBlock(
                            origin.relative(direction, along).relative(lateral, side).offset(0, dy, 0),
                            Blocks.AIR.defaultBlockState(),
                            3
                    );
                }
            }
        }
    }

    private static int splitterRotation(Direction direction) {
        return direction.getAxis() == Direction.Axis.X ? 2 : 0;
    }

    private static void configureSource(ServerLevel level, BlockPos sourcePos, int radiusMilli) {
        if (!(level.getBlockEntity(sourcePos) instanceof CreativeLightSourceBlockEntity source)) {
            return;
        }

        ContainerData data = source.createDataAccess();
        data.set(CreativeLightSourceBlockEntity.DATA_REGION, FrequencyKey.DEBUG_VISIBLE.region().ordinal());
        data.set(CreativeLightSourceBlockEntity.DATA_BIN, FrequencyKey.DEBUG_VISIBLE.bin());
        data.set(CreativeLightSourceBlockEntity.DATA_POWER, (int) SOURCE_POWER);
        data.set(CreativeLightSourceBlockEntity.DATA_COHERENCE, CoherenceKind.COHERENT.ordinal());
        data.set(CreativeLightSourceBlockEntity.DATA_BEAM_MODEL, BeamModel.COLLIMATED.ordinal());
        data.set(CreativeLightSourceBlockEntity.DATA_RADIUS_MILLI, radiusMilli);
        data.set(CreativeLightSourceBlockEntity.DATA_DIVERGENCE_MILLI, 0);
        data.set(CreativeLightSourceBlockEntity.DATA_FOCUS_DISTANCE_MILLI, 0);

        for (int index = CreativeLightSourceBlockEntity.DATA_SPECTRUM_START;
             index < CreativeLightSourceBlockEntity.DATA_COUNT;
             index++) {
            data.set(index, 0);
        }
    }

    private static OutputBeam sourceOutput(ServerLevel level, BlockPos sourcePos) {
        BlockState state = level.getBlockState(sourcePos);

        if (state.getBlock() instanceof OpticalSource opticalSource) {
            List<OutputBeam> outputs = opticalSource.getOutputBeams(state, level, sourcePos);

            if (!outputs.isEmpty()) {
                return outputs.getFirst();
            }
        }

        throw new IllegalStateException("Optical test source did not create an output beam");
    }

    private static TestSample sample(ServerLevel level, BlockPos sourcePos, BlockPos profilerPos) {
        OutputBeam outputBeam = sourceOutput(level, sourcePos);
        CompiledPortGraph graph = PortGraphCompiler.compileDirect(level, sourcePos, outputBeam);
        ScalarPowerSolution solution = ProfileLanePowerSolver.solve(
                level,
                graph,
                sourcePowerByLane(graph.sourceNode(), outputBeam)
        );
        CompiledReadoutLayer readoutLayer = OpticalReadoutLayerCompiler.compile(
                level,
                graph,
                List.of(new BeamProfileSource(sourcePos, outputBeam))
        );
        List<ReceiverOutput> outputs = readoutLayer.sample(solution);
        Optional<ReceiverOutput> profilerOutput = outputs.stream()
                .filter(output -> output.kind() == ReceiverOutputKind.BEAM_PROFILER)
                .filter(output -> output.pos().equals(profilerPos))
                .findFirst();
        return new TestSample(graph, solution, outputs, profilerOutput);
    }

    private static Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowerByLane(
            PortGraphNode sourceNode,
            OutputBeam outputBeam
    ) {
        Map<SpectralPowerLane, Map<PortGraphNode, Double>> sourcePowers = new HashMap<>();
        BeamProfileKey profile = BeamProfileKey.fromEnvelope(outputBeam.beam().envelope());

        for (var component : outputBeam.beam().components()) {
            if (component.power() <= 0.0D) {
                continue;
            }

            SpectralPowerLane lane = new SpectralPowerLane(
                    component.frequency(),
                    component.coherence(),
                    profile
            );
            sourcePowers.computeIfAbsent(lane, ignored -> new HashMap<>())
                    .merge(sourceNode, component.power(), Double::sum);
        }

        return sourcePowers;
    }

    private static ValidationReport validateSplitterLensSplitter(TestSample sample, double expectedPower) {
        List<String> failures = commonProfilerFailures(sample);
        expectFeedback(sample, true, failures);
        expectFeedbackOracleSolver(sample, failures);
        expectPowerClose(sample.actualPower(), expectedPower, "profiler power", failures);
        expectPositive(sample.solution().powerByLane().size(), "profile lane count", failures);
        return report(failures, sample.actualPower());
    }

    private static ValidationReport validateLensApertureClip(
            TestSample sample,
            double expectedPower,
            double apertureGain
    ) {
        List<String> failures = commonProfilerFailures(sample);
        expectFeedback(sample, false, failures);
        expectSolver(sample, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectNoProfileDegradation(sample, failures);
        expectPowerClose(sample.actualPower(), expectedPower, "profiler power", failures);

        if (!(apertureGain > 0.0D && apertureGain < 1.0D)) {
            failures.add("aperture gain should be a clipping loss, got " + apertureGain);
        }

        return report(failures, sample.actualPower());
    }

    private static ValidationReport validateFiberRadiusCoupling(
            TestSample narrow,
            TestSample wide,
            double ratio
    ) {
        List<String> failures = commonProfilerFailures(narrow, "narrow");
        failures.addAll(commonProfilerFailures(wide, "wide"));
        expectFeedback(narrow, false, failures);
        expectFeedback(wide, false, failures);
        expectSolver(narrow, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectSolver(wide, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectNoProfileDegradation(narrow, failures);
        expectNoProfileDegradation(wide, failures);

        if (!(wide.actualPower() < narrow.actualPower())) {
            failures.add("wide beam should couple less power than narrow beam");
        }

        if (!(ratio > 0.18D && ratio < 0.36D)) {
            failures.add("wide/narrow coupling ratio out of range: " + ratio);
        }

        return report(failures, wide.actualPower());
    }

    private static ValidationReport validateFeedbackFiberRadiusLoss(
            TestSample narrow,
            TestSample wide,
            double ratio
    ) {
        List<String> failures = commonProfilerFailures(narrow, "narrow");
        failures.addAll(commonProfilerFailures(wide, "wide"));
        expectFeedback(narrow, true, failures);
        expectFeedback(wide, true, failures);
        expectSolver(narrow, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectSolver(wide, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectNoProfileDegradation(narrow, failures);
        expectNoProfileDegradation(wide, failures);

        if (!(wide.actualPower() < narrow.actualPower() * 0.6D)) {
            failures.add("feedback fiber radius loss did not reduce wide beam enough: " + ratio);
        }

        if (!(ratio > 0.05D && ratio < 0.6D)) {
            failures.add("feedback wide/narrow ratio out of range: " + ratio);
        }

        return report(failures, wide.actualPower());
    }

    private static ValidationReport validateParallelFiberSameEndpoint(
            TestSample single,
            TestSample parallel
    ) {
        List<String> failures = commonProfilerFailures(single, "single");
        failures.addAll(commonProfilerFailures(parallel, "parallel"));
        expectFeedback(single, false, failures);
        expectFeedback(parallel, false, failures);
        expectSolver(single, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectSolver(parallel, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectNoProfileDegradation(single, failures);
        expectNoProfileDegradation(parallel, failures);

        double expectedParallel = Math.min(SOURCE_POWER, single.actualPower() * 2.0D);
        expectPowerClose(parallel.actualPower(), expectedParallel, "parallel route power", failures);

        if (parallel.actualPower() + POWER_TOLERANCE < single.actualPower()) {
            failures.add("parallel fiber output decreased compared to single route");
        }

        return report(failures, parallel.actualPower());
    }

    private static ValidationReport validateBeamExpanderFiberCoupling(
            TestSample direct,
            TestSample expanded,
            double ratio
    ) {
        List<String> failures = commonProfilerFailures(direct, "direct");
        failures.addAll(commonProfilerFailures(expanded, "expanded"));
        expectFeedback(direct, false, failures);
        expectFeedback(expanded, true, failures);
        expectSolver(direct, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectSolver(expanded, ScalarSolverKind.PROFILE_STATE_EXACT, failures);
        expectNoProfileDegradation(direct, failures);
        expectNoProfileDegradation(expanded, failures);

        if (!(expanded.actualPower() > direct.actualPower() * BEAM_EXPANDER_MIN_IMPROVEMENT)) {
            failures.add("beam expander did not improve long-distance fiber coupling enough: " + ratio);
        }

        if (!(ratio < BEAM_EXPANDER_MAX_IMPROVEMENT)) {
            failures.add("beam expander improvement is suspiciously large: " + ratio);
        }

        return report(failures, expanded.actualPower());
    }

    private static List<String> commonProfilerFailures(TestSample sample) {
        return commonProfilerFailures(sample, "sample");
    }

    private static List<String> commonProfilerFailures(TestSample sample, String label) {
        List<String> failures = new ArrayList<>();

        if (!sample.solution().reliableForReadout()) {
            failures.add(label + " solution is not reliable");
        }

        if (sample.profilerOutput().isEmpty()) {
            failures.add(label + " beam profiler readout missing");
            return failures;
        }

        ReceiverOutput output = sample.profilerOutput().get();

        if (!Double.isFinite(output.power()) || output.power() < 0.0D) {
            failures.add(label + " profiler power is invalid");
        }

        if (output.power() > SOURCE_POWER + POWER_TOLERANCE) {
            failures.add(label + " profiler power exceeds source power");
        }

        if (Math.abs(output.coherentPower() - output.power()) > POWER_TOLERANCE) {
            failures.add(label + " coherent power does not match total profiler power");
        }

        if (output.strayPower() > POWER_TOLERANCE) {
            failures.add(label + " unexpected stray power");
        }

        if (!Double.isFinite(output.envelope().radius()) || output.envelope().radius() <= 0.0D) {
            failures.add(label + " envelope radius invalid");
        }

        if (!Double.isFinite(output.envelope().waistRadius()) || output.envelope().waistRadius() <= 0.0D) {
            failures.add(label + " envelope waist radius invalid");
        }

        if (!Double.isFinite(output.envelope().divergence())) {
            failures.add(label + " envelope divergence invalid");
        }

        if (!Double.isFinite(output.envelope().focusDistance())) {
            failures.add(label + " envelope focus distance invalid");
        }

        return failures;
    }

    private static void expectFeedback(TestSample sample, boolean feedbackExpected, List<String> failures) {
        if (feedbackExpected && sample.graph().feedbackSccCount() <= 0) {
            failures.add("expected at least one feedback SCC");
        }

        if (!feedbackExpected && sample.graph().feedbackSccCount() != 0) {
            failures.add("expected no feedback SCC, got " + sample.graph().feedbackSccCount());
        }
    }

    private static void expectSolver(
            TestSample sample,
            ScalarSolverKind expectedSolver,
            List<String> failures
    ) {
        if (sample.solution().solverKind() != expectedSolver) {
            failures.add("expected solver " + expectedSolver + ", got " + sample.solution().solverKind());
        }
    }

    private static void expectFeedbackOracleSolver(TestSample sample, List<String> failures) {
        ScalarSolverKind solverKind = sample.solution().solverKind();

        if (solverKind == ScalarSolverKind.PROFILE_STATE_EXACT) {
            expectNoProfileDegradation(sample, failures);
            return;
        }

        if (solverKind == ScalarSolverKind.PROFILE_COLLAPSED_EXACT
                && sample.solution().profileCollapsedFallback()) {
            return;
        }

        failures.add("expected profile-state exact or explicit collapsed feedback fallback, got " + solverKind);
    }

    private static void expectNoProfileDegradation(TestSample sample, List<String> failures) {
        if (sample.solution().profileCollapsedFallback()) {
            failures.add("profile solver fell back to collapsed mode");
        }

        if (sample.solution().profileOverflow()) {
            failures.add("profile state overflow");
        }
    }

    private static void expectPowerClose(
            double actualPower,
            double expectedPower,
            String label,
            List<String> failures
    ) {
        if (Math.abs(actualPower - expectedPower) > POWER_TOLERANCE) {
            failures.add(label + " mismatch: actual=" + actualPower + " expected=" + expectedPower);
        }
    }

    private static void expectPositive(int value, String label, List<String> failures) {
        if (value <= 0) {
            failures.add(label + " should be positive");
        }
    }

    private static ValidationReport report(List<String> failures, double actualPower) {
        return failures.isEmpty()
                ? ValidationReport.pass(actualPower)
                : ValidationReport.fail(String.join("; ", failures), actualPower);
    }

    private static void logValidation(
            ServerLevel level,
            String testName,
            TestLayout layout,
            TestSample sample,
            ValidationReport report,
            double expectedPower
    ) {
        OpticalCompilerDebugLogger.logExampleValidation(
                level,
                testName,
                layout.source(),
                layout.direction(),
                sample.graph(),
                sample.solution(),
                sample.outputs(),
                report.passed(),
                report.message(),
                report.actualPower(),
                expectedPower,
                POWER_TOLERANCE
        );
    }

    private static Direction horizontalTestDirection(CommandSourceStack source) {
        Direction testDirection = source.getEntity() instanceof ServerPlayer player
                ? player.getDirection()
                : DEFAULT_TEST_DIRECTION;
        return testDirection.getAxis().isHorizontal() ? testDirection : DEFAULT_TEST_DIRECTION;
    }

    private static double expectedSplitterLensSplitterPower() {
        double q = AIR_PROPAGATION_FACTOR * AIR_PROPAGATION_FACTOR;
        double sourceToFirstSplitter = q * SOURCE_POWER;

        double[][] matrix = {
                {1.0D, -q * BEAM_SPLITTER_REFLECTANCE, 0.0D, 0.0D},
                {0.0D, 1.0D, 0.0D, -q * LENS_TRANSMITTANCE},
                {-q * LENS_TRANSMITTANCE, 0.0D, 1.0D, 0.0D},
                {0.0D, 0.0D, -q * BEAM_SPLITTER_REFLECTANCE, 1.0D}
        };
        double[] rhs = {
                q * BEAM_SPLITTER_TRANSMITTANCE * sourceToFirstSplitter,
                0.0D,
                0.0D,
                0.0D
        };
        double[] solution = solveFourByFour(matrix, rhs);
        double secondSplitterInput = solution[2];
        return q * BEAM_SPLITTER_TRANSMITTANCE * secondSplitterInput;
    }

    private static double[] solveFourByFour(double[][] matrix, double[] rhs) {
        int size = 4;
        double[][] augmented = new double[size][size + 1];

        for (int row = 0; row < size; row++) {
            System.arraycopy(matrix[row], 0, augmented[row], 0, size);
            augmented[row][size] = rhs[row];
        }

        for (int pivotColumn = 0; pivotColumn < size; pivotColumn++) {
            int pivotRow = pivotColumn;
            double pivotMagnitude = Math.abs(augmented[pivotRow][pivotColumn]);

            for (int row = pivotColumn + 1; row < size; row++) {
                double candidate = Math.abs(augmented[row][pivotColumn]);

                if (candidate > pivotMagnitude) {
                    pivotRow = row;
                    pivotMagnitude = candidate;
                }
            }

            if (pivotMagnitude <= 1.0E-12D) {
                throw new IllegalStateException("Optical test expectation matrix is singular");
            }

            if (pivotRow != pivotColumn) {
                double[] swap = augmented[pivotColumn];
                augmented[pivotColumn] = augmented[pivotRow];
                augmented[pivotRow] = swap;
            }

            double pivot = augmented[pivotColumn][pivotColumn];

            for (int column = pivotColumn; column <= size; column++) {
                augmented[pivotColumn][column] /= pivot;
            }

            for (int row = 0; row < size; row++) {
                if (row == pivotColumn) {
                    continue;
                }

                double factor = augmented[row][pivotColumn];

                if (factor == 0.0D) {
                    continue;
                }

                for (int column = pivotColumn; column <= size; column++) {
                    augmented[row][column] -= factor * augmented[pivotColumn][column];
                }
            }
        }

        double[] solution = new double[size];

        for (int row = 0; row < size; row++) {
            solution[row] = augmented[row][size];
        }

        return solution;
    }

    private record TestLayout(BlockPos origin, Direction direction) {
        private BlockPos source() {
            return origin;
        }

        private BlockPos firstSplitter() {
            return origin.relative(direction, 2);
        }

        private BlockPos lens() {
            return origin.relative(direction, 4);
        }

        private BlockPos secondSplitter() {
            return origin.relative(direction, 6);
        }

        private BlockPos profiler() {
            return origin.relative(direction, 8);
        }

        private BlockPos profilerSupport() {
            return origin.relative(direction, 9);
        }
    }

    private record FiberTestLayout(BlockPos origin, Direction direction) {
        private BlockPos source() {
            return origin;
        }

        private BlockPos firstSplitter() {
            return origin.relative(direction, 2);
        }

        private BlockPos inputInterface() {
            return origin.relative(direction, 4);
        }

        private BlockPos outputInterface() {
            return origin.relative(direction, 6);
        }

        private BlockPos secondSplitter() {
            return origin.relative(direction, 8);
        }

        private BlockPos profiler() {
            return origin.relative(direction, 10);
        }

        private BlockPos profilerSupport() {
            return origin.relative(direction, 11);
        }

        private TestLayout asTestLayout() {
            return new TestLayout(origin, direction);
        }
    }

    private record BeamExpanderTestLayout(BlockPos origin, Direction direction) {
        private BlockPos source() {
            return origin;
        }

        private BlockPos firstLens() {
            return origin.relative(direction, BEAM_EXPANDER_FIRST_LENS_DISTANCE);
        }

        private BlockPos secondLens() {
            return origin.relative(direction, BEAM_EXPANDER_SECOND_LENS_DISTANCE);
        }

        private BlockPos inputInterface() {
            return origin.relative(direction, BEAM_EXPANDER_INPUT_DISTANCE);
        }

        private BlockPos outputInterface() {
            return origin.relative(direction, BEAM_EXPANDER_INPUT_DISTANCE + 2);
        }

        private BlockPos profiler() {
            return origin.relative(direction, BEAM_EXPANDER_INPUT_DISTANCE + 4);
        }

        private BlockPos profilerSupport() {
            return origin.relative(direction, profilerSupportDistance());
        }

        private int profilerSupportDistance() {
            return BEAM_EXPANDER_INPUT_DISTANCE + 5;
        }

        private TestLayout asTestLayout() {
            return new TestLayout(origin, direction);
        }
    }

    private record TestSample(
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            List<ReceiverOutput> outputs,
            Optional<ReceiverOutput> profilerOutput
    ) {
        private double actualPower() {
            return profilerOutput.map(ReceiverOutput::power).orElse(0.0D);
        }
    }

    private record ValidationReport(boolean passed, String message, double actualPower) {
        private static ValidationReport pass(double actualPower) {
            return new ValidationReport(true, "ok", actualPower);
        }

        private static ValidationReport fail(String message, double actualPower) {
            return new ValidationReport(false, message, actualPower);
        }
    }

    private OpticalExampleValidator() {
    }
}
