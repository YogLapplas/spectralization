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
import io.github.yoglappland.spectralization.optics.compiler.SpectralPowerLane;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkData;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeKind;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeProfile;
import io.github.yoglappland.spectralization.optics.fiber.FiberRoute;
import io.github.yoglappland.spectralization.optics.lens.LensProfile;
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
    private static final double LENS_TRANSMITTANCE = 0.96D;
    private static final double LENS_REFLECTANCE = 0.02D;
    private static final double AIR_PROPAGATION_FACTOR = 0.995D;
    private static final double POWER_TOLERANCE = 1.0E-6D;
    private static final int DEFAULT_RADIUS_MILLI = 250;
    private static final int NARROW_FIBER_RADIUS_MILLI = 125;
    private static final int WIDE_FIBER_RADIUS_MILLI = 250;
    private static final int WIDE_LENS_RADIUS_MILLI = 2000;

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("opticaltest")
                .then(Commands.literal("splitter_lens_splitter")
                        .executes(context -> runSplitterLensSplitter(context.getSource())))
                .then(Commands.literal("lens_aperture_clip")
                        .executes(context -> runLensApertureClip(context.getSource())))
                .then(Commands.literal("fiber_radius_coupling")
                        .executes(context -> runFiberRadiusCoupling(context.getSource())))
                .then(Commands.literal("feedback_fiber_radius_loss")
                        .executes(context -> runFeedbackFiberRadiusLoss(context.getSource())))
                .then(Commands.literal("parallel_fiber_same_endpoint")
                        .executes(context -> runParallelFiberSameEndpoint(context.getSource())));
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
        OutputBeam outputBeam = sourceOutput(level, layout.source());
        CompiledPortGraph graph = PortGraphCompiler.compileDirect(level, layout.source(), outputBeam);
        ScalarPowerSolution solution = ProfileLanePowerSolver.solve(
                level,
                graph,
                sourcePowerByLane(graph.sourceNode(), outputBeam)
        );
        CompiledReadoutLayer readoutLayer = OpticalReadoutLayerCompiler.compile(
                level,
                graph,
                List.of(new BeamProfileSource(layout.source(), outputBeam))
        );
        List<ReceiverOutput> outputs = readoutLayer.sample(solution);
        Optional<ReceiverOutput> profilerOutput = outputs.stream()
                .filter(output -> output.kind() == ReceiverOutputKind.BEAM_PROFILER)
                .filter(output -> output.pos().equals(layout.profiler()))
                .findFirst();
        double expectedPower = expectedSplitterLensSplitterPower();
        ValidationReport report = validate(graph, solution, profilerOutput, expectedPower);
        OpticalCompilerDebugLogger.logExampleValidation(
                level,
                "splitter_lens_splitter",
                layout.source(),
                layout.direction(),
                graph,
                solution,
                outputs,
                report.passed(),
                report.message(),
                report.actualPower(),
                expectedPower,
                POWER_TOLERANCE
        );

        if (report.passed()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "Optical test splitter_lens_splitter PASS at %s: profiler=%.9f expected=%.9f graph=%d/%d feedback_scc=%d lanes=%d",
                    layout.source().toShortString(),
                    report.actualPower(),
                    expectedPower,
                    graph.nodes().size(),
                    graph.edges().size(),
                        graph.feedbackSccCount(),
                        solution.powerByLane().size()
            )), true);
            return 1;
        }

        source.sendFailure(Component.literal(String.format(
                "Optical test splitter_lens_splitter FAIL at %s: %s profiler=%.9f expected=%.9f graph=%d/%d feedback_scc=%d solver=%s reliable=%s",
                layout.source().toShortString(),
                report.message(),
                report.actualPower(),
                expectedPower,
                graph.nodes().size(),
                graph.edges().size(),
                        graph.feedbackSccCount(),
                        solution.solverKind(),
                        solution.reliableForReadout()
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
                .thinLens(
                        LensProfile.STANDARD.focalLength(),
                        LensProfile.STANDARD.aperture() / 100.0D,
                        1.15D
                )
                .gain();
        double expectedPower = SOURCE_POWER
                * Math.pow(AIR_PROPAGATION_FACTOR, 8)
                * LENS_TRANSMITTANCE
                * apertureGain;
        ValidationReport report = validateSimplePower(sample, expectedPower, false);
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
        boolean passed = narrow.solution().reliableForReadout()
                && wide.solution().reliableForReadout()
                && narrow.graph().feedbackSccCount() == 0
                && wide.graph().feedbackSccCount() == 0
                && ratio > 0.18D
                && ratio < 0.36D;
        ValidationReport report = passed
                ? ValidationReport.pass(wide.actualPower())
                : ValidationReport.fail("fiber radius coupling ratio out of range: " + ratio, wide.actualPower());
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
        boolean passed = narrow.solution().reliableForReadout()
                && wide.solution().reliableForReadout()
                && narrow.graph().feedbackSccCount() > 0
                && wide.graph().feedbackSccCount() > 0
                && narrow.solution().solverKind().name().contains("COLLAPSED")
                && wide.solution().solverKind().name().contains("COLLAPSED")
                && wide.actualPower() < narrow.actualPower() * 0.6D;
        ValidationReport report = passed
                ? ValidationReport.pass(wide.actualPower())
                : ValidationReport.fail("feedback fiber radius loss did not reduce wide beam enough: " + ratio, wide.actualPower());
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
        boolean passed = single.solution().reliableForReadout()
                && parallel.solution().reliableForReadout()
                && parallel.actualPower() + POWER_TOLERANCE >= single.actualPower()
                && parallel.actualPower() <= SOURCE_POWER + POWER_TOLERANCE;
        ValidationReport report = passed
                ? ValidationReport.pass(parallel.actualPower())
                : ValidationReport.fail("parallel fiber output decreased or exceeded cap", parallel.actualPower());
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
        Direction lateral = direction.getClockWise();
        for (int along = -4; along <= 12; along++) {
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

    private static ValidationReport validate(
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            Optional<ReceiverOutput> profilerOutput,
            double expectedPower
    ) {
        if (!solution.reliableForReadout()) {
            return ValidationReport.fail("solution is not reliable", profilerOutput.map(ReceiverOutput::power).orElse(0.0D));
        }

        if (graph.feedbackSccCount() <= 0) {
            return ValidationReport.fail("expected at least one feedback SCC", profilerOutput.map(ReceiverOutput::power).orElse(0.0D));
        }

        if (profilerOutput.isEmpty()) {
            return ValidationReport.fail("beam profiler readout missing", 0.0D);
        }

        double actualPower = profilerOutput.get().power();

        if (Math.abs(actualPower - expectedPower) > POWER_TOLERANCE) {
            return ValidationReport.fail("profiler power mismatch", actualPower);
        }

        if (Math.abs(profilerOutput.get().coherentPower() - actualPower) > POWER_TOLERANCE) {
            return ValidationReport.fail("profiler coherent power mismatch", actualPower);
        }

        if (!Double.isFinite(profilerOutput.get().envelope().radius()) || profilerOutput.get().envelope().radius() <= 0.0D) {
            return ValidationReport.fail("profiler envelope radius invalid", actualPower);
        }

        return ValidationReport.pass(actualPower);
    }

    private static ValidationReport validateSimplePower(
            TestSample sample,
            double expectedPower,
            boolean requireFeedback
    ) {
        if (!sample.solution().reliableForReadout()) {
            return ValidationReport.fail("solution is not reliable", sample.actualPower());
        }

        if (requireFeedback && sample.graph().feedbackSccCount() <= 0) {
            return ValidationReport.fail("expected at least one feedback SCC", sample.actualPower());
        }

        if (sample.profilerOutput().isEmpty()) {
            return ValidationReport.fail("beam profiler readout missing", 0.0D);
        }

        double actualPower = sample.actualPower();

        if (Math.abs(actualPower - expectedPower) > POWER_TOLERANCE) {
            return ValidationReport.fail("profiler power mismatch", actualPower);
        }

        return ValidationReport.pass(actualPower);
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
                {-q * LENS_REFLECTANCE, 1.0D, 0.0D, -q * LENS_TRANSMITTANCE},
                {-q * LENS_TRANSMITTANCE, 0.0D, 1.0D, -q * LENS_REFLECTANCE},
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
