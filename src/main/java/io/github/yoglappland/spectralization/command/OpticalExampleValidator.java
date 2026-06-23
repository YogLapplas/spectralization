package io.github.yoglappland.spectralization.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
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

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("opticaltest")
                .then(Commands.literal("splitter_lens_splitter")
                        .executes(context -> runSplitterLensSplitter(context.getSource())));
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

    private static void placeSplitterLensSplitter(ServerLevel level, TestLayout layout) {
        clearTestVolume(level, layout.origin(), layout.direction());

        level.setBlock(
                layout.source(),
                Spectralization.CREATIVE_LIGHT_SOURCE.get()
                        .defaultBlockState()
                        .setValue(CreativeLightSourceBlock.FACING, layout.direction()),
                3
        );
        configureSource(level, layout.source());

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

    private static void configureSource(ServerLevel level, BlockPos sourcePos) {
        if (!(level.getBlockEntity(sourcePos) instanceof CreativeLightSourceBlockEntity source)) {
            return;
        }

        ContainerData data = source.createDataAccess();
        data.set(CreativeLightSourceBlockEntity.DATA_REGION, FrequencyKey.DEBUG_VISIBLE.region().ordinal());
        data.set(CreativeLightSourceBlockEntity.DATA_BIN, FrequencyKey.DEBUG_VISIBLE.bin());
        data.set(CreativeLightSourceBlockEntity.DATA_POWER, (int) SOURCE_POWER);
        data.set(CreativeLightSourceBlockEntity.DATA_COHERENCE, CoherenceKind.COHERENT.ordinal());
        data.set(CreativeLightSourceBlockEntity.DATA_BEAM_MODEL, BeamModel.COLLIMATED.ordinal());
        data.set(CreativeLightSourceBlockEntity.DATA_RADIUS_MILLI, 250);
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
