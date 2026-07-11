package io.github.yoglappland.spectralization.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.compiler.CompiledSpotLayer;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPerformanceTracker;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPerformanceTracker.OutputFingerprint;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPerformanceTracker.OutputSurface;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPerformanceTracker.OutputSurfaceKey;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionResult;
import io.github.yoglappland.spectralization.optics.projection.VoxelSpotProjector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

public final class SpotProjectionTestCommand {
    private static final int DEFAULT_BENCHMARK_SAMPLES = 5;
    private static final int MAX_BENCHMARK_SAMPLES = 16;
    private static final long BENCHMARK_RETRY_TICKS = 40L;
    private static final long BENCHMARK_TIMEOUT_TICKS = 400L;
    private static final double RANDOM_OCCUPANCY = 0.17D;
    private static final int RANDOM_STRESS_CASES = 1_000;
    private static final int RANDOM_STRESS_PROGRESS_INTERVAL = 100;
    private static final long[] DIRECTION_MATRIX_WARMUP_SEEDS = {
            0x13579bdf2468aceL,
            0x2468ace13579bdfL,
            0x6a09e667f3bcc909L,
            0xbb67ae8584caa73bL
    };
    private static final long[] DIRECTION_MATRIX_SEEDS = {
            42L,
            20_260_711L,
            -6_495_254_288_592_929_499L,
            0x5deece66dL
    };
    private static final Direction[][] DIRECTION_MATRIX_ORDERS = {
            {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST},
            {Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.NORTH},
            {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH},
            {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}
    };
    private static final Map<UUID, GeneratedTest> LAST_TEST_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, BenchmarkRun> BENCHMARKS_BY_PLAYER = new HashMap<>();
    private static ItemSuiteRun activeItemSuite;

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("spottest")
                .executes(context -> describe(context.getSource()))
                .then(Commands.literal("random")
                        .executes(context -> generateNew(
                                context.getSource(),
                                context.getSource().getLevel().getRandom().nextLong()
                        ))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(context -> generateNew(
                                        context.getSource(),
                                        LongArgumentType.getLong(context, "seed")
                                ))))
                .then(Commands.literal("rerun")
                        .executes(context -> rerun(context.getSource())))
                .then(Commands.literal("report")
                        .executes(context -> report(
                                context.getSource(),
                                SpotProjectionPerformanceTracker.DEFAULT_REPORT_SAMPLES
                        ))
                        .then(Commands.argument(
                                        "count",
                                        IntegerArgumentType.integer(1, SpotProjectionPerformanceTracker.MAX_REPORT_SAMPLES)
                                )
                                .executes(context -> report(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")
                                ))))
                .then(Commands.literal("benchmark")
                        .executes(context -> benchmark(context.getSource(), DEFAULT_BENCHMARK_SAMPLES))
                        .then(Commands.argument(
                                        "count",
                                        IntegerArgumentType.integer(1, MAX_BENCHMARK_SAMPLES)
                                )
                                .executes(context -> benchmark(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "count")
                                ))))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource())));
    }

    private static int describe(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Spot tests: random [seed], rerun, report [count], benchmark [count], clear."
        ), false);
        return 1;
    }

    public static boolean isItemSuiteRunning(ServerPlayer player) {
        return activeItemSuite != null && activeItemSuite.owner.equals(player.getUUID());
    }

    public static Component itemSuiteStatus(ServerPlayer player) {
        if (!isItemSuiteRunning(player)) {
            return Component.translatable("item.spectralization.spot_test.message.idle");
        }
        SuiteCase testCase = activeItemSuite.currentCase();
        SpotTestLayout layout = activeItemSuite.currentLayout();
        return Component.translatable(
                "item.spectralization.spot_test.message.status",
                activeItemSuite.caseIndex + 1,
                activeItemSuite.cases.size(),
                testCase.displayName(layout.direction())
        );
    }

    public static int startItemSuite(ServerPlayer player, SpotTestMode mode) {
        CommandSourceStack source = player.createCommandSourceStack();
        if (!source.hasPermission(2)) {
            source.sendFailure(Component.translatable("item.spectralization.spot_test.message.permission"));
            return 0;
        }
        if (activeItemSuite != null) {
            source.sendFailure(Component.translatable("item.spectralization.spot_test.message.global_busy"));
            return 0;
        }
        if (BENCHMARKS_BY_PLAYER.containsKey(player.getUUID())) {
            source.sendFailure(Component.translatable("item.spectralization.spot_test.message.benchmark_busy"));
            return 0;
        }

        SpotTestLayout layout = SpotTestLayout.inFrontOf(player);
        List<SuiteCase> cases = suiteCases(mode);
        for (Direction direction : cases.stream()
                .map(testCase -> testCase.directionOr(layout.direction()))
                .distinct()
                .toList()) {
            if (!SpotProjectionTestScene.validateVolume(
                    source,
                    player.serverLevel(),
                    layout.withDirection(direction)
            )) {
                return 0;
            }
        }

        GeneratedTest previous = LAST_TEST_BY_PLAYER.remove(player.getUUID());
        if (previous != null) {
            clearGeneratedTest(source, previous);
        }

        ItemSuiteRun run = new ItemSuiteRun(
                UUID.randomUUID(),
                player.getUUID(),
                player.serverLevel().dimension(),
                layout,
                mode,
                cases,
                DebugConfigSnapshot.capture()
        );
        activeItemSuite = run;
        SpotTestLayout firstLayout = run.currentLayout();
        SpectralDiagnostics.event(player.serverLevel(), "spot_projection_test", "suite_started")
                .field("run_id", run.runId)
                .field("suite", mode.serializedName())
                .field("cases", cases.size())
                .field("repeats", switch (mode) {
                    case DIRECTION_MATRIX -> DIRECTION_MATRIX_SEEDS.length;
                    case RANDOM_STRESS -> RANDOM_STRESS_CASES;
                    default -> 1;
                })
                .pos("source", firstLayout.source())
                .field("direction", firstLayout.direction())
                .write();
        player.sendSystemMessage(Component.translatable(
                "item.spectralization.spot_test.message.started",
                Component.translatable(mode.translationKey()),
                cases.size()
        ).withStyle(ChatFormatting.GOLD));
        startItemSuiteCase(player, run);
        return 1;
    }

    private static void startItemSuiteCase(ServerPlayer player, ItemSuiteRun run) {
        SuiteCase testCase = run.currentCase();
        SpotTestLayout caseLayout = run.currentLayout();
        applyDebugPolicy(run, caseLayout, testCase.verboseValidation());
        GeneratedTest generatedTest = new GeneratedTest(
                run.dimension,
                caseLayout,
                testCase
        );
        GeneratedTest previous = LAST_TEST_BY_PLAYER.remove(player.getUUID());
        if (previous != null && !previous.layout().equals(caseLayout)) {
            clearGeneratedTest(player.createCommandSourceStack(), previous);
        }
        if (run.mode == SpotTestMode.DIRECTION_MATRIX) {
            SpotProjectionTestScene.clearAllHorizontalDirections(player.serverLevel(), run.layout);
        }
        SpotProjectionTestScene.BuildResult buildResult = build(
                player.serverLevel(),
                generatedTest,
                run.mode != SpotTestMode.RANDOM_STRESS
        );
        int placed = buildResult.placed();
        run.currentSceneSignature = buildResult.sceneSignature();
        LAST_TEST_BY_PLAYER.put(player.getUUID(), generatedTest);
        SpotProjectionPerformanceTracker.reset(
                player.serverLevel(),
                caseLayout.source(),
                caseLayout.direction()
        );
        long gameTime = player.serverLevel().getGameTime();
        BENCHMARKS_BY_PLAYER.put(player.getUUID(), new BenchmarkRun(
                generatedTest,
                testCase.samples(),
                gameTime + BENCHMARK_RETRY_TICKS,
                gameTime + BENCHMARK_TIMEOUT_TICKS,
                run.runId,
                testCase.id(),
                testCase.benchmarkMode()
        ));
        startBenchmarkProjection(player.serverLevel(), BENCHMARKS_BY_PLAYER.get(player.getUUID()));
        if (run.mode != SpotTestMode.RANDOM_STRESS) {
            player.sendSystemMessage(Component.translatable(
                    "item.spectralization.spot_test.message.case_started",
                    run.caseIndex + 1,
                    run.cases.size(),
                    testCase.displayName(caseLayout.direction()),
                    Component.translatable(testCase.benchmarkDescriptionKey()),
                    testCase.samples()
            ).withStyle(ChatFormatting.AQUA));
        }
        if (run.mode != SpotTestMode.RANDOM_STRESS) {
            SpectralDiagnostics.Event startedEvent = SpectralDiagnostics
                    .event(player.serverLevel(), "spot_projection_test", "case_started")
                    .field("run_id", run.runId)
                    .field("suite", run.mode.serializedName())
                    .field("case_id", testCase.id())
                    .field("case_index", run.caseIndex)
                    .field("repeat", testCase.repeatIndex())
                    .field("direction", caseLayout.direction());
            if (testCase.recordSeed()) {
                startedEvent.field("seed", testCase.seed());
            }
            startedEvent
                    .field("scene_signature", Long.toUnsignedString(buildResult.sceneSignature(), 16))
                    .field("occupancy", testCase.occupancy())
                    .field("fixtures", testCase.fixtures())
                    .field("divergence", testCase.divergenceMilli() / 1000.0D)
                    .field("samples", testCase.samples())
                    .field("benchmark_mode", testCase.benchmarkMode().serializedName)
                    .field("appearance_mutation", testCase.appearanceMutation().serializedName)
                    .field("verbose_validation", testCase.verboseValidation())
                    .field("placed", placed)
                    .write();
        }
    }

    private static void applyDebugPolicy(
            ItemSuiteRun run,
            SpotTestLayout layout,
            boolean verboseValidation
    ) {
        SpectralizationConfig.setOpticalCompilerDebugLog(run.mode != SpotTestMode.RANDOM_STRESS);
        SpectralizationConfig.setOpticalCompilerDebugVerbose(false);
        SpectralizationConfig.setSpotColorDebug(false);
        VoxelSpotProjector.setDebugFaceCentersEnabled(false);
        if (verboseValidation) {
            VoxelSpotProjector.setTargetedValidation(
                    run.dimension,
                    layout.source(),
                    layout.direction()
            );
        } else {
            VoxelSpotProjector.clearTargetedValidation();
        }
    }

    private static int generateNew(CommandSourceStack source, long seed) {
        if (rejectWhileItemSuiteRuns(source)) {
            return 0;
        }
        ServerPlayer player = player(source);
        if (player == null) {
            return 0;
        }

        SpotTestLayout layout = SpotTestLayout.inFrontOf(player);
        if (!SpotProjectionTestScene.validateVolume(source, player.serverLevel(), layout)) {
            return 0;
        }

        GeneratedTest previous = LAST_TEST_BY_PLAYER.remove(player.getUUID());
        BENCHMARKS_BY_PLAYER.remove(player.getUUID());
        if (previous != null) {
            clearGeneratedTest(source, previous);
        }

        GeneratedTest generatedTest = new GeneratedTest(player.serverLevel().dimension(), layout, seed);
        int placed = build(player.serverLevel(), generatedTest).placed();
        LAST_TEST_BY_PLAYER.put(player.getUUID(), generatedTest);
        source.sendSuccess(() -> Component.literal(String.format(
                "Generated spot test seed=%d source=%s direction=%s coherence=%s divergence=%.3f placed=%d. Use /spectralization spottest rerun or clear.",
                seed,
                layout.source().toShortString(),
                layout.direction().getSerializedName(),
                SpotProjectionTestScene.sourceCoherenceName(),
                SpotProjectionTestScene.DEFAULT_DIVERGENCE_MILLI / 1000.0D,
                placed
        )), true);
        return Math.max(1, placed);
    }

    private static int rerun(CommandSourceStack source) {
        if (rejectWhileItemSuiteRuns(source)) {
            return 0;
        }
        ServerPlayer player = player(source);
        if (player == null) {
            return 0;
        }

        GeneratedTest generatedTest = LAST_TEST_BY_PLAYER.get(player.getUUID());
        if (generatedTest == null) {
            source.sendFailure(Component.literal("No spot test is recorded for this player. Run spottest random first."));
            return 0;
        }

        ServerLevel level = source.getServer().getLevel(generatedTest.dimension());
        if (level == null || !SpotProjectionTestScene.validateVolume(source, level, generatedTest.layout())) {
            return 0;
        }

        int placed = build(level, generatedTest).placed();
        source.sendSuccess(() -> Component.literal(String.format(
                "Rebuilt spot test seed=%d source=%s direction=%s placed=%d",
                generatedTest.seed(),
                generatedTest.layout().source().toShortString(),
                generatedTest.layout().direction().getSerializedName(),
                placed
        )), true);
        return Math.max(1, placed);
    }

    private static int report(CommandSourceStack source, int count) {
        ServerPlayer player = player(source);
        if (player == null) {
            return 0;
        }

        GeneratedTest test = LAST_TEST_BY_PLAYER.get(player.getUUID());
        if (test == null) {
            source.sendFailure(Component.literal("No spot test is recorded for this player. Run spottest random first."));
            return 0;
        }

        ServerLevel level = source.getServer().getLevel(test.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("The recorded spot test dimension is not loaded."));
            return 0;
        }

        SpotProjectionPerformanceTracker.Report report = SpotProjectionPerformanceTracker.report(
                level,
                test.layout().source(),
                test.layout().direction(),
                count
        );
        sendReport(player, level, report);
        return Math.max(1, report.samples());
    }

    private static int benchmark(CommandSourceStack source, int count) {
        if (rejectWhileItemSuiteRuns(source)) {
            return 0;
        }
        ServerPlayer player = player(source);
        if (player == null) {
            return 0;
        }

        GeneratedTest test = LAST_TEST_BY_PLAYER.get(player.getUUID());
        if (test == null) {
            source.sendFailure(Component.literal("No spot test is recorded for this player. Run spottest random first."));
            return 0;
        }

        ServerLevel level = source.getServer().getLevel(test.dimension());
        if (level == null || !SpotProjectionTestScene.validateVolume(source, level, test.layout())) {
            return 0;
        }

        SpotProjectionPerformanceTracker.reset(level, test.layout().source(), test.layout().direction());
        BENCHMARKS_BY_PLAYER.put(player.getUUID(), new BenchmarkRun(
                test,
                count,
                level.getGameTime() + BENCHMARK_RETRY_TICKS,
                level.getGameTime() + BENCHMARK_TIMEOUT_TICKS
        ));
        startBenchmarkProjection(level, BENCHMARKS_BY_PLAYER.get(player.getUUID()));
        source.sendSuccess(() -> Component.literal(String.format(
                "Started source-bound spot benchmark: samples=%d source=%s direction=%s",
                count,
                test.layout().source().toShortString(),
                test.layout().direction().getSerializedName()
        )), false);
        return 1;
    }

    public static void tickBenchmarks(MinecraftServer server) {
        List<SuiteBenchmarkCompletion> suiteCompletions = new ArrayList<>();
        var iterator = BENCHMARKS_BY_PLAYER.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BenchmarkRun> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            BenchmarkRun run = entry.getValue();
            ServerLevel level = server.getLevel(run.test.dimension());
            if (player == null || level == null) {
                if (run.suiteRunId != null) {
                    suiteCompletions.add(new SuiteBenchmarkCompletion(
                            entry.getKey(),
                            run.suiteRunId,
                            SpotProjectionPerformanceTracker.Report.EMPTY,
                            run.latencyReport(),
                            false,
                            "owner_or_dimension_unavailable"
                    ));
                }
                iterator.remove();
                continue;
            }

            SpotProjectionPerformanceTracker.Report report = SpotProjectionPerformanceTracker.report(
                    level,
                    run.test.layout().source(),
                    run.test.layout().direction(),
                    run.targetSamples
            );
            if (run.warmupPhase > 0 && report.samples() >= 1) {
                run.discardPendingLatency();
                SpotProjectionPerformanceTracker.reset(
                        level,
                        run.test.layout().source(),
                        run.test.layout().direction()
                );
                run.observedSamples = 0;
                if (run.warmupPhase == 2) {
                    run.warmupPhase = 1;
                } else {
                    run.warmupPhase = 0;
                    run.appearanceStep = 0;
                }
                if (!applyNextAppearance(level, run)) {
                    suiteCompletions.add(new SuiteBenchmarkCompletion(
                            entry.getKey(), run.suiteRunId, report, run.latencyReport(), false,
                            "appearance_mutation_failed"
                    ));
                    iterator.remove();
                    continue;
                }
                startBenchmarkProjection(level, run);
                run.nextRetryTick = level.getGameTime() + BENCHMARK_RETRY_TICKS;
                continue;
            }
            if (report.samples() >= run.targetSamples) {
                run.completePendingLatency();
                if (run.suiteRunId == null) {
                    sendReport(player, level, report);
                    player.sendSystemMessage(Component.literal("Spot benchmark complete."));
                } else {
                    if (activeItemSuite == null
                            || !activeItemSuite.runId.equals(run.suiteRunId)
                            || activeItemSuite.mode != SpotTestMode.RANDOM_STRESS) {
                        SpotProjectionPerformanceTracker.log(level, report);
                    }
                    suiteCompletions.add(new SuiteBenchmarkCompletion(
                            entry.getKey(),
                            run.suiteRunId,
                            report,
                            run.latencyReport(),
                            true,
                            "samples_complete"
                    ));
                }
                iterator.remove();
                continue;
            }

            long gameTime = level.getGameTime();
            if (gameTime >= run.deadlineTick) {
                if (run.suiteRunId == null) {
                    player.sendSystemMessage(Component.literal(String.format(
                            "Spot benchmark timed out after collecting %d/%d source-bound samples.",
                            report.samples(),
                            run.targetSamples
                    )));
                } else {
                    suiteCompletions.add(new SuiteBenchmarkCompletion(
                            entry.getKey(),
                            run.suiteRunId,
                            report,
                            run.latencyReport(),
                            false,
                            "benchmark_timeout"
                    ));
                }
                iterator.remove();
                continue;
            }

            if (report.samples() > run.observedSamples) {
                run.completePendingLatency();
                run.observedSamples = report.samples();
                if (!applyNextAppearance(level, run)) {
                    suiteCompletions.add(new SuiteBenchmarkCompletion(
                            entry.getKey(), run.suiteRunId, report, run.latencyReport(), false,
                            "appearance_mutation_failed"
                    ));
                    iterator.remove();
                    continue;
                }
                startBenchmarkProjection(level, run);
                run.nextRetryTick = gameTime + BENCHMARK_RETRY_TICKS;
            } else if (gameTime >= run.nextRetryTick) {
                requestBenchmarkProjection(level, run);
                run.nextRetryTick = gameTime + BENCHMARK_RETRY_TICKS;
            }
        }

        for (SuiteBenchmarkCompletion completion : suiteCompletions) {
            completeItemSuiteBenchmark(server, completion);
        }
    }

    private static void completeItemSuiteBenchmark(
            MinecraftServer server,
            SuiteBenchmarkCompletion completion
    ) {
        ItemSuiteRun run = activeItemSuite;
        if (run == null
                || !run.runId.equals(completion.runId())
                || !run.owner.equals(completion.owner())) {
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(run.owner);
        ServerLevel level = server.getLevel(run.dimension);
        if (!completion.completed() || player == null || level == null) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable(
                        "item.spectralization.spot_test.message.case_aborted",
                        run.caseIndex + 1,
                        run.cases.size(),
                        run.currentCase().displayName(run.currentLayout().direction()),
                        completion.reason()
                ).withStyle(ChatFormatting.RED));
            }
            finishItemSuite(player, level, false, completion.reason());
            return;
        }

        SuiteCase testCase = run.currentCase();
        SpotTestLayout caseLayout = run.currentLayout();
        SpotProjectionResult.Stats stats = SpotProjectionPerformanceTracker.latestStats(
                level,
                caseLayout.source(),
                caseLayout.direction()
        );
        OutputFingerprint outputFingerprint = SpotProjectionPerformanceTracker.latestOutputFingerprint(
                level,
                caseLayout.source(),
                caseLayout.direction()
        );
        SpotProjectionResult.OptimizationStats optimization = stats.optimization();
        long structuralMismatches = optimization.remainingSubtractValidationMismatches()
                + optimization.sameDepthSplitValidationMismatches()
                + optimization.sameDepthPrefixValidationMismatches()
                + optimization.sideCanonicalValidationMismatches();
        boolean validationPassed = !testCase.verboseValidation()
                || (stats.sideBoundaryMissingFaces() == 0L && structuralMismatches == 0L);
        boolean cachePassed = testCase.benchmarkMode() != BenchmarkMode.CACHE_REUSE
                || (completion.report().appearanceOnlySamples() == testCase.samples()
                && completion.report().fullRebuildSamples() == 0);
        boolean passed = validationPassed && cachePassed;
        String result = passed
                ? (testCase.verboseValidation() ? "pass" : "complete")
                : "fail";

        if (run.mode == SpotTestMode.RANDOM_STRESS) {
            run.recordRandomStressResult(completion.report(), completion.latency());
        } else {
            SpectralDiagnostics.Event completedEvent = SpectralDiagnostics
                    .event(level, "spot_projection_test", "case_complete")
                    .field("run_id", run.runId)
                    .field("suite", run.mode.serializedName())
                    .field("case_id", testCase.id())
                    .field("case_index", run.caseIndex)
                    .field("repeat", testCase.repeatIndex())
                    .field("direction", caseLayout.direction());
            if (testCase.recordSeed()) {
                completedEvent.field("seed", testCase.seed());
            }
            completedEvent
                    .field("scene_signature", Long.toUnsignedString(run.currentSceneSignature, 16))
                    .field("output_coverage_signature", Long.toUnsignedString(
                            outputFingerprint.coverageSignature(), 16
                    ))
                    .field("output_fragmentation_signature", Long.toUnsignedString(
                            outputFingerprint.fragmentationSignature(), 16
                    ))
                    .field("output_surfaces", outputFingerprint.surfaces().size())
                    .field("samples", completion.report().samples())
                    .field("elapsed_avg_us", completion.report().elapsedAverageUs())
                    .field("elapsed_p50_us", completion.report().elapsedP50Us())
                    .field("elapsed_p95_us", completion.report().elapsedP95Us())
                    .field("response_samples", completion.latency().samples())
                    .field("response_avg_us", completion.latency().averageUs())
                    .field("response_p95_us", completion.latency().p95Us())
                    .field("response_max_us", completion.latency().maxUs())
                    .field("spots_avg", completion.report().spotsAverage())
                    .field("benchmark_mode", testCase.benchmarkMode().serializedName)
                    .field("appearance_mutation", testCase.appearanceMutation().serializedName)
                    .field("full_rebuild_samples", completion.report().fullRebuildSamples())
                    .field("appearance_only_samples", completion.report().appearanceOnlySamples())
                    .field("validation_enabled", testCase.verboseValidation())
                    .field("boundary_missing", stats.sideBoundaryMissingFaces())
                    .field("boundary_missing_details", optimization.sideBoundaryMissingDetails().size())
                    .field("remaining_validation_checks", optimization.remainingSubtractValidationChecks())
                    .field("same_depth_split_validation_checks", optimization.sameDepthSplitValidationChecks())
                    .field("same_depth_prefix_validation_checks", optimization.sameDepthPrefixValidationChecks())
                    .field("side_canonical_validation_checks", optimization.sideCanonicalValidationChecks())
                    .field("structural_mismatches", structuralMismatches)
                    .field("cache_expected_appearance_only", testCase.benchmarkMode() == BenchmarkMode.CACHE_REUSE
                            ? testCase.samples() : 0)
                    .field("cache_validation_passed", cachePassed)
                    .field("result", result)
                    .write();
            sendItemSuiteCaseSummary(player, run, testCase, completion.report(), completion.latency(), passed);
        }

        if (!passed) {
            if (!cachePassed) {
                player.sendSystemMessage(Component.translatable(
                        "item.spectralization.spot_test.message.case_failed_cache",
                        completion.report().appearanceOnlySamples(),
                        testCase.samples(),
                        completion.report().fullRebuildSamples()
                ).withStyle(ChatFormatting.RED));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "item.spectralization.spot_test.message.case_failed",
                        stats.sideBoundaryMissingFaces(),
                        structuralMismatches
                ).withStyle(ChatFormatting.RED));
            }
            finishItemSuite(player, level, false, cachePassed ? "validation_failed" : "cache_validation_failed");
            return;
        }

        run.passedCases++;
        if (run.mode == SpotTestMode.RANDOM_STRESS
                && (run.passedCases % RANDOM_STRESS_PROGRESS_INTERVAL == 0
                || run.passedCases == run.cases.size())) {
            RandomStressSummary summary = run.randomStressSummary();
            player.sendSystemMessage(Component.translatable(
                    "item.spectralization.spot_test.message.random_stress_progress",
                    run.passedCases,
                    run.cases.size(),
                    formatMillis(summary.elapsedAverageUs()),
                    Math.round(summary.spotsAverage())
            ).withStyle(ChatFormatting.AQUA));
            writeRandomStressProgress(level, run, summary);
        }
        DirectionMatrixComparison matrixComparison = run.recordDirectionMatrixResult(
                testCase,
                caseLayout,
                completion.report(),
                outputFingerprint
        );
        if (matrixComparison != null && !matrixComparison.allMetricsMatch()) {
            writeDirectionMatrixDifference(level, run, caseLayout, matrixComparison);
        }
        if (run.caseIndex + 1 >= run.cases.size()) {
            boolean matrixPassed = run.directionMatrixPassed();
            boolean matrixAsymmetric = run.directionMatrixHasAsymmetry();
            finishItemSuite(
                    player,
                    level,
                    matrixPassed,
                    matrixPassed
                            ? (matrixAsymmetric ? "direction_matrix_asymmetry" : "complete")
                            : "direction_matrix_correctness_mismatch"
            );
            return;
        }

        run.caseIndex++;
        startItemSuiteCase(player, run);
    }

    private static void sendItemSuiteCaseSummary(
            ServerPlayer player,
            ItemSuiteRun run,
            SuiteCase testCase,
            SpotProjectionPerformanceTracker.Report report,
            RequestLatencyReport latency,
            boolean passed
    ) {
        String key = testCase.benchmarkMode() == BenchmarkMode.CACHE_REUSE
                ? "item.spectralization.spot_test.message.case_summary_cache"
                : "item.spectralization.spot_test.message.case_summary_rebuild";
        long modeSamples = testCase.benchmarkMode() == BenchmarkMode.CACHE_REUSE
                ? report.appearanceOnlySamples()
                : report.fullRebuildSamples();
        player.sendSystemMessage(Component.translatable(
                key,
                passed ? "✓" : "✗",
                run.caseIndex + 1,
                run.cases.size(),
                testCase.displayName(run.currentLayout().direction()),
                formatMillis(report.elapsedAverageUs()),
                formatMillis(report.elapsedP95Us()),
                formatMillis(latency.averageUs()),
                formatMillis(latency.p95Us()),
                Math.round(report.spotsAverage()),
                modeSamples,
                testCase.samples()
        ).withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private static String formatMillis(double microseconds) {
        return String.format(Locale.ROOT, "%.2f", microseconds / 1_000.0D);
    }

    private static void writeDirectionMatrixDifference(
            ServerLevel level,
            ItemSuiteRun run,
            SpotTestLayout layout,
            DirectionMatrixComparison comparison
    ) {
        DirectionMatrixResult baseline = comparison.baseline();
        DirectionMatrixResult current = comparison.current();
        SpectralDiagnostics.Event event = SpectralDiagnostics
                .event(level, "spot_projection_test", "direction_matrix_difference")
                .field("run_id", run.runId)
                .field("repeat", current.repeatIndex())
                .field("seed", current.seed())
                .field("baseline_direction", baseline.direction())
                .field("direction", current.direction())
                .field("correctness_match", comparison.correctnessMatches())
                .field("scene_signature_match", comparison.sceneSignatureMatch())
                .field("output_coverage_match", comparison.outputCoverageMatch())
                .field("output_fragmentation_match", comparison.outputFragmentationMatch())
                .field("workload_match", comparison.workloadMatch())
                .field("baseline_scene_signature", Long.toUnsignedString(baseline.sceneSignature(), 16))
                .field("scene_signature", Long.toUnsignedString(current.sceneSignature(), 16))
                .field("baseline_output_coverage_signature", Long.toUnsignedString(
                        baseline.outputFingerprint().coverageSignature(), 16
                ))
                .field("output_coverage_signature", Long.toUnsignedString(
                        current.outputFingerprint().coverageSignature(), 16
                ))
                .field("baseline_output_fragmentation_signature", Long.toUnsignedString(
                        baseline.outputFingerprint().fragmentationSignature(), 16
                ))
                .field("output_fragmentation_signature", Long.toUnsignedString(
                        current.outputFingerprint().fragmentationSignature(), 16
                ))
                .field("baseline_workload", baseline.workloadSummary())
                .field("workload", current.workloadSummary());

        OutputSurfaceDifference outputDifference = comparison.firstOutputDifference();
        if (outputDifference == null) {
            event.field("first_output_difference", "none")
                    .field("difference_class", comparison.workloadMatch()
                            ? "none" : "intermediate_workload_only");
        } else {
            OutputSurfaceKey key = outputDifference.key();
            BlockPos pos = layout.at(key.along(), key.side(), key.vertical());
            var state = level.getBlockState(pos);
            event.field("first_output_difference", key.summary())
                    .field("difference_class", outputDifference.coverageMatch()
                            ? "fragmentation" : "coverage")
                    .pos("output_pos", pos)
                    .field("block_state", state)
                    .field("shape_boxes", state.getShape(level, pos).toAabbs().size())
                    .field("baseline_surface", outputDifference.baseline() == null
                            ? "absent" : outputDifference.baseline().summary())
                    .field("surface", outputDifference.current() == null
                            ? "absent" : outputDifference.current().summary());
        }
        event.write();
    }

    private static void finishItemSuite(
            ServerPlayer player,
            ServerLevel level,
            boolean passed,
            String reason
    ) {
        ItemSuiteRun run = activeItemSuite;
        if (run == null) {
            return;
        }
        activeItemSuite = null;
        VoxelSpotProjector.clearTargetedValidation();
        run.previousDebugConfig.restore();

        if (level != null) {
            if (run.mode == SpotTestMode.DIRECTION_MATRIX) {
                writeDirectionMatrixSummary(level, run);
            } else if (run.mode == SpotTestMode.RANDOM_STRESS) {
                writeRandomStressSummary(level, run, passed);
            }
            SpectralDiagnostics.event(level, "spot_projection_test", "suite_complete")
                    .field("run_id", run.runId)
                    .field("suite", run.mode.serializedName())
                    .field("cases", run.cases.size())
                    .field("passed_cases", run.passedCases)
                    .field("result", passed ? "pass" : "fail")
                    .field("reason", reason)
                    .write();
        }
        if (player != null) {
            if (run.mode == SpotTestMode.DIRECTION_MATRIX) {
                ChatFormatting matrixStyle = !run.directionMatrixPassed()
                        ? ChatFormatting.RED
                        : (run.directionMatrixHasAsymmetry() ? ChatFormatting.YELLOW : ChatFormatting.GREEN);
                player.sendSystemMessage(Component.translatable(
                        "item.spectralization.spot_test.message.direction_matrix_summary",
                        DIRECTION_MATRIX_SEEDS.length,
                        run.sceneSignatureMismatches,
                        run.outputCoverageMismatches,
                        run.outputFragmentationMismatches,
                        run.workloadMismatches,
                        formatMillis(run.directionAverageUs(Direction.NORTH)),
                        formatMillis(run.directionAverageUs(Direction.EAST)),
                        formatMillis(run.directionAverageUs(Direction.SOUTH)),
                        formatMillis(run.directionAverageUs(Direction.WEST))
                ).withStyle(matrixStyle));
            } else if (run.mode == SpotTestMode.RANDOM_STRESS) {
                RandomStressSummary summary = run.randomStressSummary();
                player.sendSystemMessage(Component.translatable(
                        "item.spectralization.spot_test.message.random_stress_summary",
                        summary.cases(),
                        formatMillis(summary.elapsedAverageUs()),
                        formatMillis(summary.elapsedP50Us()),
                        formatMillis(summary.elapsedP95Us()),
                        formatMillis(summary.responseAverageUs()),
                        Math.round(summary.spotsAverage())
                ).withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            player.sendSystemMessage(Component.translatable(
                    run.mode == SpotTestMode.DIRECTION_MATRIX
                            && passed
                            && run.directionMatrixHasAsymmetry()
                            ? "item.spectralization.spot_test.message.complete_warning"
                            : (passed
                            ? "item.spectralization.spot_test.message.complete"
                            : "item.spectralization.spot_test.message.failed"),
                    Component.translatable(run.mode.translationKey()),
                    run.passedCases,
                    run.cases.size()
            ).withStyle(run.mode == SpotTestMode.DIRECTION_MATRIX
                    && passed
                    && run.directionMatrixHasAsymmetry()
                    ? ChatFormatting.YELLOW
                    : (passed ? ChatFormatting.GREEN : ChatFormatting.RED)));
        }
    }

    private static void writeDirectionMatrixSummary(ServerLevel level, ItemSuiteRun run) {
        SpectralDiagnostics.event(level, "spot_projection_test", "direction_matrix_complete")
                .field("run_id", run.runId)
                .field("repeats", DIRECTION_MATRIX_SEEDS.length)
                .field("directions", 4)
                .field("cases", run.directionMatrixResults.size())
                .field("scene_signature_mismatches", run.sceneSignatureMismatches)
                .field("output_coverage_mismatches", run.outputCoverageMismatches)
                .field("output_fragmentation_mismatches", run.outputFragmentationMismatches)
                .field("workload_asymmetries", run.workloadMismatches)
                .field("north_avg_us", run.directionAverageUs(Direction.NORTH))
                .field("east_avg_us", run.directionAverageUs(Direction.EAST))
                .field("south_avg_us", run.directionAverageUs(Direction.SOUTH))
                .field("west_avg_us", run.directionAverageUs(Direction.WEST))
                .field("result", !run.directionMatrixPassed()
                        ? "fail"
                        : (run.directionMatrixHasAsymmetry() ? "pass_with_asymmetry" : "pass"))
                .write();
    }

    private static void writeRandomStressProgress(
            ServerLevel level,
            ItemSuiteRun run,
            RandomStressSummary summary
    ) {
        SpectralDiagnostics.event(level, "spot_projection_test", "random_stress_progress")
                .field("run_id", run.runId)
                .field("completed", summary.cases())
                .field("total", RANDOM_STRESS_CASES)
                .field("elapsed_avg_us", summary.elapsedAverageUs())
                .field("elapsed_p95_us", summary.elapsedP95Us())
                .field("response_avg_us", summary.responseAverageUs())
                .field("spots_avg", summary.spotsAverage())
                .write();
    }

    private static void writeRandomStressSummary(ServerLevel level, ItemSuiteRun run, boolean passed) {
        RandomStressSummary summary = run.randomStressSummary();
        SpectralDiagnostics.event(level, "spot_projection_test", "random_stress_complete")
                .field("run_id", run.runId)
                .field("cases", summary.cases())
                .field("elapsed_avg_us", summary.elapsedAverageUs())
                .field("elapsed_p50_us", summary.elapsedP50Us())
                .field("elapsed_p95_us", summary.elapsedP95Us())
                .field("response_avg_us", summary.responseAverageUs())
                .field("response_p95_us", summary.responseP95Us())
                .field("spots_avg", summary.spotsAverage())
                .field("result", passed ? "pass" : "fail")
                .write();
    }

    private static void sendReport(
            ServerPlayer player,
            ServerLevel level,
            SpotProjectionPerformanceTracker.Report report
    ) {
        for (String line : report.lines()) {
            player.sendSystemMessage(Component.literal(line));
        }
        SpotProjectionPerformanceTracker.log(level, report);
    }

    private static int clear(CommandSourceStack source) {
        if (rejectWhileItemSuiteRuns(source)) {
            return 0;
        }
        ServerPlayer player = player(source);
        if (player == null) {
            return 0;
        }

        GeneratedTest generatedTest = LAST_TEST_BY_PLAYER.remove(player.getUUID());
        BENCHMARKS_BY_PLAYER.remove(player.getUUID());
        if (generatedTest == null) {
            source.sendFailure(Component.literal("No spot test is recorded for this player."));
            return 0;
        }

        int cleared = clearGeneratedTest(source, generatedTest);
        source.sendSuccess(() -> Component.literal(generatedTest.testCase().recordSeed()
                ? String.format(Locale.ROOT, "Cleared spot test seed=%d blocks=%d", generatedTest.seed(), cleared)
                : String.format(Locale.ROOT, "Cleared anonymous random spot test blocks=%d", cleared)), true);
        return Math.max(1, cleared);
    }

    private static SpotProjectionTestScene.BuildResult build(
            ServerLevel level,
            GeneratedTest generatedTest
    ) {
        return build(level, generatedTest, true);
    }

    private static SpotProjectionTestScene.BuildResult build(
            ServerLevel level,
            GeneratedTest generatedTest,
            boolean logDetails
    ) {
        SpotProjectionTestScene.BuildResult result = SpotProjectionTestScene.build(
                level,
                generatedTest.layout(),
                generatedTest.seed(),
                generatedTest.testCase().occupancy(),
                generatedTest.testCase().fixtures(),
                generatedTest.testCase().divergenceMilli(),
                logDetails
        );
        if (logDetails) {
            SpectralDiagnostics.Event event = SpectralDiagnostics
                    .event(level, "spot_projection_test", "generated")
                    .pos("source", generatedTest.layout().source())
                    .field("direction", generatedTest.layout().direction())
                    .field("case_id", generatedTest.testCase().id())
                    .field("repeat", generatedTest.testCase().repeatIndex());
            if (generatedTest.testCase().recordSeed()) {
                event.field("seed", generatedTest.seed());
            }
            event.field("scene_signature", Long.toUnsignedString(result.sceneSignature(), 16))
                    .field("power", SpotProjectionTestScene.sourcePower())
                    .field("coherence", SpotProjectionTestScene.sourceCoherence())
                    .field("radius", SpotProjectionTestScene.sourceRadius())
                    .field("divergence", generatedTest.testCase().divergenceMilli() / 1000.0D)
                    .field("occupancy", generatedTest.testCase().occupancy())
                    .field("random_obstacles", result.randomObstacles())
                    .field("fixtures", result.fixtures())
                    .field("screen_blocks", result.screenBlocks())
                    .field("placed", result.placed())
                    .write();
        }
        return result;
    }

    private static int clearGeneratedTest(CommandSourceStack source, GeneratedTest generatedTest) {
        ServerLevel level = source.getServer().getLevel(generatedTest.dimension());
        if (level == null) {
            return 0;
        }

        int cleared = SpotProjectionTestScene.clear(level, generatedTest.layout());
        SpotProjectionTestScene.refreshProjection(level);
        SpectralDiagnostics.Event event = SpectralDiagnostics.event(level, "spot_projection_test", "cleared")
                .pos("source", generatedTest.layout().source())
                .field("direction", generatedTest.layout().direction());
        if (generatedTest.testCase().recordSeed()) {
            event.field("seed", generatedTest.seed());
        }
        event
                .field("cleared", cleared)
                .write();
        return cleared;
    }

    private static boolean rejectWhileItemSuiteRuns(CommandSourceStack source) {
        if (activeItemSuite == null) {
            return false;
        }
        source.sendFailure(Component.translatable("item.spectralization.spot_test.message.busy"));
        return true;
    }

    private static void requestBenchmarkProjection(ServerLevel level, BenchmarkRun run) {
        if (run == null) {
            return;
        }
        if (run.benchmarkMode == BenchmarkMode.FULL_REBUILD) {
            CompiledSpotLayer.invalidateProjectionGeometry(
                    level,
                    run.test.layout().source(),
                    run.test.layout().direction(),
                    "benchmark_forced_rebuild"
            );
        }
        SpotProjectionTestScene.refreshProjection(level);
    }

    private static void startBenchmarkProjection(ServerLevel level, BenchmarkRun run) {
        if (run == null) {
            return;
        }
        if (run.forceInitialGeometryWarmup) {
            CompiledSpotLayer.invalidateProjectionGeometry(
                    level,
                    run.test.layout().source(),
                    run.test.layout().direction(),
                    "benchmark_cache_geometry_warmup"
            );
            run.forceInitialGeometryWarmup = false;
        }
        run.startLatency();
        requestBenchmarkProjection(level, run);
    }

    private static boolean applyNextAppearance(ServerLevel level, BenchmarkRun run) {
        AppearanceMutation mutation = run.test.testCase().appearanceMutation();
        if (mutation == AppearanceMutation.NONE) {
            return true;
        }
        int step = run.appearanceStep;
        SpectralDiagnostics.Event event = SpectralDiagnostics.event(
                        level,
                        "spot_projection_test",
                        "appearance_step"
                )
                .field("run_id", run.suiteRunId)
                .field("case_id", run.suiteCaseId)
                .field("mutation", mutation.serializedName)
                .field("step", step);
        switch (mutation) {
            case POWER_SEQUENCE -> {
                int powerCenti = SpotProjectionTestScene.cachePowerCenti(step);
                if (!SpotProjectionTestScene.setSourcePower(level, run.test.layout(), powerCenti)) {
                    return false;
                }
                event.field("power_centi", powerCenti)
                        .field("power", powerCenti / (double) CreativeLightSourceBlockEntity.POWER_SCALE);
            }
            case COLOR_SEQUENCE -> {
                int bin = SpotProjectionTestScene.cacheColorBin(step);
                if (!SpotProjectionTestScene.setSourceColor(level, run.test.layout(), bin)) {
                    return false;
                }
                event.field("region", SpectralRegion.VISIBLE.id())
                        .field("bin", bin);
            }
            case NONE -> {
                return true;
            }
        }
        run.appearanceStep++;
        event.write();
        return true;
    }

    private static ServerPlayer player(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("Spot test commands can only be run by a player."));
            return null;
        }
    }

    private static List<SuiteCase> suiteCases(SpotTestMode mode) {
        SuiteCase quick = new SuiteCase(
                "quick_seed_42", 42L, RANDOM_OCCUPANCY, true,
                SpotProjectionTestScene.DEFAULT_DIVERGENCE_MILLI,
                3, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
        );
        SuiteCase partial = new SuiteCase(
                "partial_geometry", 42L, 0.0D, true,
                SpotProjectionTestScene.DEFAULT_DIVERGENCE_MILLI,
                3, true, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
        );
        List<SuiteCase> performance = List.of(
                new SuiteCase("performance_sparse", 42L, 0.08D, true, 500, 5, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE),
                new SuiteCase("performance_mixed", 20_260_711L, 0.17D, true, 500, 5, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE),
                new SuiteCase("performance_dense", -6_495_254_288_592_929_499L, 0.30D, true, 650, 5, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE),
                new SuiteCase("performance_cached_power", 20_260_711L, 0.17D, true, 500, 5, false, BenchmarkMode.CACHE_REUSE, AppearanceMutation.POWER_SEQUENCE),
                new SuiteCase("performance_cached_color", 20_260_711L, 0.17D, true, 500, 5, false, BenchmarkMode.CACHE_REUSE, AppearanceMutation.COLOR_SEQUENCE)
        );
        return switch (mode) {
            case QUICK -> List.of(quick);
            case PARTIAL_GEOMETRY -> List.of(partial);
            case PERFORMANCE -> performance;
            case DIRECTION_MATRIX -> directionMatrixCases();
            case RANDOM_STRESS -> randomStressCases();
        };
    }

    private static List<SuiteCase> directionMatrixCases() {
        validateDirectionMatrixDefinition();
        List<SuiteCase> cases = new ArrayList<>(
                DIRECTION_MATRIX_WARMUP_SEEDS.length + DIRECTION_MATRIX_SEEDS.length * 4
        );
        Direction[] warmupDirections = {
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        for (int index = 0; index < DIRECTION_MATRIX_WARMUP_SEEDS.length; index++) {
            cases.add(new SuiteCase(
                    "direction_matrix_warmup",
                    DIRECTION_MATRIX_WARMUP_SEEDS[index],
                    0.17D,
                    true,
                    500,
                    MAX_BENCHMARK_SAMPLES,
                    false,
                    BenchmarkMode.FULL_REBUILD,
                    AppearanceMutation.NONE,
                    warmupDirections[index],
                    -1,
                    true
            ));
        }
        for (int repeat = 0; repeat < DIRECTION_MATRIX_SEEDS.length; repeat++) {
            long seed = DIRECTION_MATRIX_SEEDS[repeat];
            for (Direction direction : DIRECTION_MATRIX_ORDERS[repeat]) {
                cases.add(new SuiteCase(
                        "direction_matrix",
                        seed,
                        0.17D,
                        true,
                        500,
                        5,
                        false,
                        BenchmarkMode.FULL_REBUILD,
                        AppearanceMutation.NONE,
                        direction,
                        repeat,
                        true
                ));
            }
        }
        return List.copyOf(cases);
    }

    private static List<SuiteCase> randomStressCases() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<SuiteCase> cases = new ArrayList<>(RANDOM_STRESS_CASES);
        for (int index = 0; index < RANDOM_STRESS_CASES; index++) {
            cases.add(new SuiteCase(
                    "random_stress",
                    random.nextLong(),
                    RANDOM_OCCUPANCY,
                    false,
                    SpotProjectionTestScene.DEFAULT_DIVERGENCE_MILLI,
                    1,
                    false,
                    BenchmarkMode.FULL_REBUILD,
                    AppearanceMutation.NONE,
                    null,
                    index,
                    false
            ));
        }
        return List.copyOf(cases);
    }

    static void validateDirectionMatrixDefinition() {
        if (DIRECTION_MATRIX_WARMUP_SEEDS.length != 4) {
            throw new IllegalStateException("Direction matrix must warm all four horizontal directions");
        }
        if (DIRECTION_MATRIX_ORDERS.length != DIRECTION_MATRIX_SEEDS.length) {
            throw new IllegalStateException("Direction-matrix seed and order counts must match");
        }
        Set<Long> seeds = new HashSet<>();
        int[][] positions = new int[4][4];
        for (int repeat = 0; repeat < DIRECTION_MATRIX_ORDERS.length; repeat++) {
            if (!seeds.add(DIRECTION_MATRIX_SEEDS[repeat])) {
                throw new IllegalStateException("Direction-matrix seeds must be unique");
            }
            Direction[] order = DIRECTION_MATRIX_ORDERS[repeat];
            if (order.length != 4 || Set.of(order).size() != 4) {
                throw new IllegalStateException("Each direction-matrix order must contain four unique directions");
            }
            for (int position = 0; position < order.length; position++) {
                Direction direction = order[position];
                if (!direction.getAxis().isHorizontal()) {
                    throw new IllegalStateException("Direction matrix only supports horizontal directions");
                }
                positions[direction.get2DDataValue()][position]++;
            }
        }
        for (int direction = 0; direction < positions.length; direction++) {
            for (int position = 0; position < positions[direction].length; position++) {
                if (positions[direction][position] != 1) {
                    throw new IllegalStateException("Direction-matrix execution positions must be balanced");
                }
            }
        }
    }

    static void validateRandomStressDefinition() {
        List<SuiteCase> cases = randomStressCases();
        if (cases.size() != RANDOM_STRESS_CASES) {
            throw new IllegalStateException("Random stress suite must contain exactly 1000 cases");
        }
        for (int index = 0; index < cases.size(); index++) {
            SuiteCase testCase = cases.get(index);
            if (testCase.recordSeed()
                    || testCase.samples() != 1
                    || testCase.fixtures()
                    || testCase.repeatIndex() != index) {
                throw new IllegalStateException("Random stress cases must be anonymous, pure-random single samples");
            }
        }
    }

    private record SuiteCase(
            String id,
            long seed,
            double occupancy,
            boolean fixtures,
            int divergenceMilli,
            int samples,
            boolean verboseValidation,
            BenchmarkMode benchmarkMode,
            AppearanceMutation appearanceMutation,
            Direction direction,
            int repeatIndex,
            boolean recordSeed
    ) {
        private SuiteCase(
                String id,
                long seed,
                double occupancy,
                boolean fixtures,
                int divergenceMilli,
                int samples,
                boolean verboseValidation,
                BenchmarkMode benchmarkMode,
                AppearanceMutation appearanceMutation
        ) {
            this(
                    id,
                    seed,
                    occupancy,
                    fixtures,
                    divergenceMilli,
                    samples,
                    verboseValidation,
                    benchmarkMode,
                    appearanceMutation,
                    null,
                    -1,
                    true
            );
        }

        private SuiteCase {
            occupancy = Math.max(0.0D, Math.min(1.0D, occupancy));
            divergenceMilli = Math.max(0, divergenceMilli);
            samples = Math.max(1, Math.min(MAX_BENCHMARK_SAMPLES, samples));
            benchmarkMode = benchmarkMode == null ? BenchmarkMode.FULL_REBUILD : benchmarkMode;
            appearanceMutation = appearanceMutation == null ? AppearanceMutation.NONE : appearanceMutation;
            if (direction != null && !direction.getAxis().isHorizontal()) {
                throw new IllegalArgumentException("Spot test direction must be horizontal");
            }
        }

        private static SuiteCase manual(long seed) {
            return new SuiteCase(
                    "manual",
                    seed,
                    RANDOM_OCCUPANCY,
                    true,
                    SpotProjectionTestScene.DEFAULT_DIVERGENCE_MILLI,
                    DEFAULT_BENCHMARK_SAMPLES,
                    SpectralizationConfig.opticalCompilerDebugVerbose(),
                    BenchmarkMode.FULL_REBUILD,
                    AppearanceMutation.NONE
            );
        }

        private String translationKey() {
            return "item.spectralization.spot_test.case." + id;
        }

        private Component displayName(Direction actualDirection) {
            Component name = Component.translatable(translationKey());
            if (repeatIndex < 0) {
                return name;
            }
            return name.copy()
                    .append(" #")
                    .append(Integer.toString(repeatIndex + 1))
                    .append(recordSeed ? " seed=" + seed : "")
                    .append(" ")
                    .append(Component.translatable(
                            "direction.spectralization." + actualDirection.getSerializedName()
                    ));
        }

        private Direction directionOr(Direction fallback) {
            return direction == null ? fallback : direction;
        }

        private String benchmarkDescriptionKey() {
            return "item.spectralization.spot_test.benchmark." + switch (appearanceMutation) {
                case POWER_SEQUENCE -> "cache_power";
                case COLOR_SEQUENCE -> "cache_color";
                case NONE -> benchmarkMode.serializedName;
            };
        }
    }

    private enum BenchmarkMode {
        FULL_REBUILD("full_rebuild"),
        CACHE_REUSE("cache_reuse");

        private final String serializedName;

        BenchmarkMode(String serializedName) {
            this.serializedName = serializedName;
        }
    }

    private enum AppearanceMutation {
        NONE("none"),
        POWER_SEQUENCE("power_sequence"),
        COLOR_SEQUENCE("color_sequence");

        private final String serializedName;

        AppearanceMutation(String serializedName) {
            this.serializedName = serializedName;
        }
    }

    private record GeneratedTest(ResourceKey<Level> dimension, SpotTestLayout layout, SuiteCase testCase) {
        private GeneratedTest(ResourceKey<Level> dimension, SpotTestLayout layout, long seed) {
            this(dimension, layout, SuiteCase.manual(seed));
        }

        private long seed() {
            return testCase.seed();
        }
    }

    private record DirectionMatrixResult(
            int repeatIndex,
            long seed,
            Direction direction,
            long sceneSignature,
            SpotProjectionPerformanceTracker.Report report,
            OutputFingerprint outputFingerprint
    ) {
        private boolean sameWorkload(DirectionMatrixResult other) {
            return rounded(report.spotsAverage()) == rounded(other.report.spotsAverage())
                    && rounded(report.dependenciesAverage()) == rounded(other.report.dependenciesAverage())
                    && rounded(report.tilesAverage()) == rounded(other.report.tilesAverage())
                    && rounded(report.projectableTilesAverage()) == rounded(other.report.projectableTilesAverage())
                    && rounded(report.sideWindowsAverage()) == rounded(other.report.sideWindowsAverage())
                    && rounded(report.sideQuadsAverage()) == rounded(other.report.sideQuadsAverage());
        }

        private static long rounded(double value) {
            return Math.round(value);
        }

        private String workloadSummary() {
            return "spots=" + rounded(report.spotsAverage())
                    + ",deps=" + rounded(report.dependenciesAverage())
                    + ",tiles=" + rounded(report.tilesAverage())
                    + ",projectable=" + rounded(report.projectableTilesAverage())
                    + ",windows=" + rounded(report.sideWindowsAverage())
                    + ",quads=" + rounded(report.sideQuadsAverage());
        }
    }

    private record DirectionMatrixComparison(
            DirectionMatrixResult baseline,
            DirectionMatrixResult current,
            boolean sceneSignatureMatch,
            boolean outputCoverageMatch,
            boolean outputFragmentationMatch,
            boolean workloadMatch
    ) {
        private boolean correctnessMatches() {
            return sceneSignatureMatch && outputCoverageMatch;
        }

        private boolean allMetricsMatch() {
            return correctnessMatches() && outputFragmentationMatch && workloadMatch;
        }

        private OutputSurfaceDifference firstOutputDifference() {
            List<OutputSurface> baselineSurfaces = baseline.outputFingerprint().surfaces();
            List<OutputSurface> currentSurfaces = current.outputFingerprint().surfaces();
            int baselineIndex = 0;
            int currentIndex = 0;
            while (baselineIndex < baselineSurfaces.size() || currentIndex < currentSurfaces.size()) {
                OutputSurface baselineSurface = baselineIndex < baselineSurfaces.size()
                        ? baselineSurfaces.get(baselineIndex) : null;
                OutputSurface currentSurface = currentIndex < currentSurfaces.size()
                        ? currentSurfaces.get(currentIndex) : null;
                int comparison;
                if (baselineSurface == null) {
                    comparison = 1;
                } else if (currentSurface == null) {
                    comparison = -1;
                } else {
                    comparison = baselineSurface.key().compareTo(currentSurface.key());
                }
                if (comparison < 0) {
                    return new OutputSurfaceDifference(
                            baselineSurface.key(), baselineSurface, null, false
                    );
                }
                if (comparison > 0) {
                    return new OutputSurfaceDifference(
                            currentSurface.key(), null, currentSurface, false
                    );
                }
                if (!baselineSurface.sameFragmentation(currentSurface)) {
                    return new OutputSurfaceDifference(
                            baselineSurface.key(),
                            baselineSurface,
                            currentSurface,
                            baselineSurface.sameCoverage(currentSurface)
                    );
                }
                baselineIndex++;
                currentIndex++;
            }
            return null;
        }
    }

    private record OutputSurfaceDifference(
            OutputSurfaceKey key,
            OutputSurface baseline,
            OutputSurface current,
            boolean coverageMatch
    ) {
    }

    private record RandomStressSummary(
            int cases,
            double elapsedAverageUs,
            double elapsedP50Us,
            double elapsedP95Us,
            double responseAverageUs,
            double responseP95Us,
            double spotsAverage
    ) {
    }

    private record DebugConfigSnapshot(
            boolean debugLog,
            boolean verbose,
            boolean colorDebug,
            boolean faceCenters
    ) {
        private static DebugConfigSnapshot capture() {
            return new DebugConfigSnapshot(
                    SpectralizationConfig.opticalCompilerDebugLog(),
                    SpectralizationConfig.opticalCompilerDebugVerbose(),
                    SpectralizationConfig.spotColorDebug(),
                    VoxelSpotProjector.debugFaceCentersEnabled()
            );
        }

        private void restore() {
            SpectralizationConfig.setOpticalCompilerDebugLog(debugLog);
            SpectralizationConfig.setOpticalCompilerDebugVerbose(verbose);
            SpectralizationConfig.setSpotColorDebug(colorDebug);
            VoxelSpotProjector.setDebugFaceCentersEnabled(faceCenters);
        }
    }

    private static final class ItemSuiteRun {
        private final UUID runId;
        private final UUID owner;
        private final ResourceKey<Level> dimension;
        private final SpotTestLayout layout;
        private final SpotTestMode mode;
        private final List<SuiteCase> cases;
        private final DebugConfigSnapshot previousDebugConfig;
        private final List<DirectionMatrixResult> directionMatrixResults = new ArrayList<>();
        private final List<Double> randomStressElapsedUs = new ArrayList<>();
        private final List<Double> randomStressResponseUs = new ArrayList<>();
        private final List<Double> randomStressSpots = new ArrayList<>();
        private int caseIndex;
        private int passedCases;
        private int sceneSignatureMismatches;
        private int outputCoverageMismatches;
        private int outputFragmentationMismatches;
        private int workloadMismatches;
        private long currentSceneSignature;

        private ItemSuiteRun(
                UUID runId,
                UUID owner,
                ResourceKey<Level> dimension,
                SpotTestLayout layout,
                SpotTestMode mode,
                List<SuiteCase> cases,
                DebugConfigSnapshot previousDebugConfig
        ) {
            this.runId = runId;
            this.owner = owner;
            this.dimension = dimension;
            this.layout = layout;
            this.mode = mode;
            this.cases = cases;
            this.previousDebugConfig = previousDebugConfig;
        }

        private SuiteCase currentCase() {
            return cases.get(caseIndex);
        }

        private SpotTestLayout currentLayout() {
            return layout.withDirection(currentCase().directionOr(layout.direction()));
        }

        private DirectionMatrixComparison recordDirectionMatrixResult(
                SuiteCase testCase,
                SpotTestLayout caseLayout,
                SpotProjectionPerformanceTracker.Report report,
                OutputFingerprint outputFingerprint
        ) {
            if (mode != SpotTestMode.DIRECTION_MATRIX) {
                return null;
            }
            if (testCase.repeatIndex() < 0) {
                return null;
            }
            DirectionMatrixResult result = new DirectionMatrixResult(
                    testCase.repeatIndex(),
                    testCase.seed(),
                    caseLayout.direction(),
                    currentSceneSignature,
                    report,
                    outputFingerprint
            );
            DirectionMatrixResult baseline = directionMatrixResults.stream()
                    .filter(existing -> existing.repeatIndex() == result.repeatIndex())
                    .findFirst()
                    .orElse(null);
            DirectionMatrixComparison comparison = null;
            if (baseline != null) {
                boolean signatureMatch = baseline.sceneSignature() == result.sceneSignature();
                boolean outputCoverageMatch = baseline.outputFingerprint().coverageSignature()
                        == result.outputFingerprint().coverageSignature();
                boolean outputFragmentationMatch = baseline.outputFingerprint().fragmentationSignature()
                        == result.outputFingerprint().fragmentationSignature();
                boolean workloadMatch = baseline.sameWorkload(result);
                if (!signatureMatch) {
                    sceneSignatureMismatches++;
                }
                if (!workloadMatch) {
                    workloadMismatches++;
                }
                if (!outputCoverageMatch) {
                    outputCoverageMismatches++;
                }
                if (!outputFragmentationMatch) {
                    outputFragmentationMismatches++;
                }
                comparison = new DirectionMatrixComparison(
                        baseline,
                        result,
                        signatureMatch,
                        outputCoverageMatch,
                        outputFragmentationMatch,
                        workloadMatch
                );
            }
            directionMatrixResults.add(result);
            return comparison;
        }

        private boolean directionMatrixPassed() {
            return mode != SpotTestMode.DIRECTION_MATRIX
                    || (directionMatrixResults.size() == DIRECTION_MATRIX_SEEDS.length * 4
                    && sceneSignatureMismatches == 0
                    && outputCoverageMismatches == 0);
        }

        private boolean directionMatrixHasAsymmetry() {
            return mode == SpotTestMode.DIRECTION_MATRIX
                    && (outputFragmentationMismatches > 0 || workloadMismatches > 0);
        }

        private double directionAverageUs(Direction direction) {
            return directionMatrixResults.stream()
                    .filter(result -> result.direction() == direction)
                    .mapToDouble(result -> result.report().elapsedAverageUs())
                    .average()
                    .orElse(0.0D);
        }

        private void recordRandomStressResult(
                SpotProjectionPerformanceTracker.Report report,
                RequestLatencyReport latency
        ) {
            if (mode != SpotTestMode.RANDOM_STRESS) {
                return;
            }
            randomStressElapsedUs.add(report.elapsedAverageUs());
            randomStressResponseUs.add(latency.averageUs());
            randomStressSpots.add(report.spotsAverage());
        }

        private RandomStressSummary randomStressSummary() {
            return new RandomStressSummary(
                    randomStressElapsedUs.size(),
                    average(randomStressElapsedUs),
                    percentile(randomStressElapsedUs, 0.50D),
                    percentile(randomStressElapsedUs, 0.95D),
                    average(randomStressResponseUs),
                    percentile(randomStressResponseUs, 0.95D),
                    average(randomStressSpots)
            );
        }

        private static double average(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
        }

        private static double percentile(List<Double> values, double quantile) {
            if (values.isEmpty()) {
                return 0.0D;
            }
            List<Double> sorted = new ArrayList<>(values);
            sorted.sort(Double::compareTo);
            int index = Math.max(0, Math.min(
                    sorted.size() - 1,
                    (int) Math.ceil(quantile * sorted.size()) - 1
            ));
            return sorted.get(index);
        }
    }

    private static final class BenchmarkRun {
        private final GeneratedTest test;
        private final int targetSamples;
        private int observedSamples;
        private long nextRetryTick;
        private final long deadlineTick;
        private final UUID suiteRunId;
        private final String suiteCaseId;
        private final BenchmarkMode benchmarkMode;
        private int warmupPhase;
        private boolean forceInitialGeometryWarmup;
        private int appearanceStep;
        private final List<Long> responseLatenciesNanos = new ArrayList<>();
        private long requestStartNanos;

        private BenchmarkRun(GeneratedTest test, int targetSamples, long nextRetryTick, long deadlineTick) {
            this(test, targetSamples, nextRetryTick, deadlineTick, null, "manual", BenchmarkMode.FULL_REBUILD);
        }

        private BenchmarkRun(
                GeneratedTest test,
                int targetSamples,
                long nextRetryTick,
                long deadlineTick,
                UUID suiteRunId,
                String suiteCaseId,
                BenchmarkMode benchmarkMode
        ) {
            this.test = test;
            this.targetSamples = targetSamples;
            this.nextRetryTick = nextRetryTick;
            this.deadlineTick = deadlineTick;
            this.suiteRunId = suiteRunId;
            this.suiteCaseId = suiteCaseId;
            this.benchmarkMode = benchmarkMode == null ? BenchmarkMode.FULL_REBUILD : benchmarkMode;
            this.warmupPhase = this.benchmarkMode == BenchmarkMode.CACHE_REUSE ? 2 : 0;
            this.forceInitialGeometryWarmup = this.benchmarkMode == BenchmarkMode.CACHE_REUSE;
        }

        private void startLatency() {
            requestStartNanos = System.nanoTime();
        }

        private void completePendingLatency() {
            if (requestStartNanos <= 0L) {
                return;
            }
            responseLatenciesNanos.add(Math.max(0L, System.nanoTime() - requestStartNanos));
            requestStartNanos = 0L;
        }

        private void discardPendingLatency() {
            requestStartNanos = 0L;
        }

        private RequestLatencyReport latencyReport() {
            if (responseLatenciesNanos.isEmpty()) {
                return RequestLatencyReport.EMPTY;
            }
            long[] sorted = responseLatenciesNanos.stream().mapToLong(Long::longValue).sorted().toArray();
            long sum = 0L;
            for (long value : sorted) {
                sum += value;
            }
            int p95Index = Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95D) - 1));
            return new RequestLatencyReport(
                    sorted.length,
                    (sum / (double) sorted.length) / 1_000.0D,
                    sorted[p95Index] / 1_000.0D,
                    sorted[sorted.length - 1] / 1_000.0D
            );
        }
    }

    private record RequestLatencyReport(int samples, double averageUs, double p95Us, double maxUs) {
        private static final RequestLatencyReport EMPTY = new RequestLatencyReport(0, 0.0D, 0.0D, 0.0D);
    }

    private record SuiteBenchmarkCompletion(
            UUID owner,
            UUID runId,
            SpotProjectionPerformanceTracker.Report report,
            RequestLatencyReport latency,
            boolean completed,
            String reason
    ) {
    }

    private SpotProjectionTestCommand() {
    }
}
