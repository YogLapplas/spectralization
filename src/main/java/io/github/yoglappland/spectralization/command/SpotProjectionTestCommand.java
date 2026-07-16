package io.github.yoglappland.spectralization.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
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
    private static final int RANDOM_STRESS_CASES_PER_TICK = 1;
    private static final int RANDOM_STRESS_PROGRESS_INTERVAL = 100;
    private static final int SMART_WARMUP_SAMPLES = 8;
    private static final int SMART_VALIDATION_SAMPLES = 3;
    private static final int SMART_PERFORMANCE_SAMPLES = 12;
    private static final int SMART_CACHE_SAMPLES = 7;
    private static final int PARALLEL_BENCHMARK_SAMPLES = 1;
    private static final int PARALLEL_CYCLES = 10;
    private static final int PARALLEL_CASE_QUIET_TICKS = 5;
    private static final int PARALLEL_CYCLE_QUIET_TICKS = 20;
    private static final int[] PARALLEL_SOURCE_COUNTS = {1, 2, 3, 4, 5, 6, 7, 8, 9};
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
    private static final Map<SmartBaselineKey, SmartSuiteSummary> LAST_SMART_SUMMARY_BY_PLAYER = new HashMap<>();
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

    public static int startItemSuite(ServerPlayer player, SpotTestMode mode, SpotTestLoad load) {
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
        List<SuiteCase> cases = suiteCases(mode, load);
        for (SuiteCase testCase : cases) {
            SpotTestLayout caseLayout = layout.withDirection(testCase.directionOr(layout.direction()));
            if (testCase.sharedParallelArena()) {
                if (!SpotProjectionTestScene.validateParallelArena(
                        source, player.serverLevel(), caseLayout
                )) {
                    return 0;
                }
            } else {
                for (SpotTestLayout sourceLayout : caseLayout.parallelSources(testCase.sourceCount())) {
                    if (!SpotProjectionTestScene.validateVolume(source, player.serverLevel(), sourceLayout)) {
                        return 0;
                    }
                }
            }
            if (mode == SpotTestMode.PARALLEL) {
                break;
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
                load,
                cases,
                DebugConfigSnapshot.capture()
        );
        activeItemSuite = run;
        SpotTestLayout firstLayout = run.currentLayout();
        SpectralDiagnostics.event(player.serverLevel(), "spot_projection_test", "suite_started")
                .field("run_id", run.runId)
                .field("suite", mode.serializedName())
                .field("load", load.serializedName())
                .field("cases", cases.size())
                .field("max_sources", cases.stream().mapToInt(SuiteCase::sourceCount).max().orElse(1))
                .field("repeats", switch (mode) {
                    case DIRECTION_MATRIX -> DIRECTION_MATRIX_SEEDS.length;
                    case RANDOM_STRESS -> RANDOM_STRESS_CASES;
                    case PARALLEL -> PARALLEL_CYCLES;
                    default -> 1;
                })
                .field("projection_execution", mode == SpotTestMode.RANDOM_STRESS
                        ? "server_tick_serial" : "production_async")
                .pos("source", firstLayout.source())
                .field("direction", firstLayout.direction())
                .write();
        player.sendSystemMessage(Component.translatable(
                "item.spectralization.spot_test.message.started",
                Component.translatable(mode.translationKey()),
                Component.translatable(load.translationKey()),
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
                run.currentLayouts(),
                testCase
        );
        GeneratedTest previous = LAST_TEST_BY_PLAYER.remove(player.getUUID());
        if (previous != null && !previous.arenaLayout().equals(caseLayout)) {
            clearGeneratedTest(player.createCommandSourceStack(), previous);
        }
        if (run.mode == SpotTestMode.DIRECTION_MATRIX) {
            SpotProjectionTestScene.clearAllHorizontalDirections(player.serverLevel(), run.layout);
        }
        boolean reuseParallelArena = run.mode == SpotTestMode.PARALLEL && run.caseIndex > 0;
        SpotProjectionTestScene.BuildResult buildResult = reuseParallelArena
                ? new SpotProjectionTestScene.BuildResult(0, 0, 0, 0, run.currentSceneSignature)
                : build(
                        player.serverLevel(),
                        generatedTest,
                        run.mode != SpotTestMode.RANDOM_STRESS
                );
        if (run.mode == SpotTestMode.PARALLEL && !SpotProjectionTestScene.setActiveParallelSources(
                player.serverLevel(),
                caseLayout.parallelSources(9),
                testCase.sourceCount()
        )) {
            finishItemSuite(player, player.serverLevel(), false, "parallel_source_activation_failed");
            return;
        }
        int placed = buildResult.placed();
        run.currentSceneSignature = buildResult.sceneSignature();
        LAST_TEST_BY_PLAYER.put(player.getUUID(), generatedTest);
        if (run.mode != SpotTestMode.PARALLEL) {
            resetBenchmarkSamples(player.serverLevel(), generatedTest);
        }
        long gameTime = player.serverLevel().getGameTime();
        int quietTicks = run.mode != SpotTestMode.PARALLEL
                ? 0
                : (testCase.sourceCount() == 1
                ? PARALLEL_CYCLE_QUIET_TICKS
                : PARALLEL_CASE_QUIET_TICKS);
        BENCHMARKS_BY_PLAYER.put(player.getUUID(), new BenchmarkRun(
                generatedTest,
                testCase.samples(),
                gameTime + BENCHMARK_RETRY_TICKS,
                gameTime + BENCHMARK_TIMEOUT_TICKS,
                run.runId,
                testCase.id(),
                testCase.benchmarkMode(),
                quietTicks
        ));
        startBenchmarkProjection(player.serverLevel(), BENCHMARKS_BY_PLAYER.get(player.getUUID()));
        if (run.mode == SpotTestMode.RANDOM_STRESS) {
            OpticalTraceCache.cancelSingleSourceSpotProjectionTestWork(
                    player.serverLevel(),
                    projectionSources(generatedTest).getFirst()
            );
        }
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
                    .field("load", run.load.serializedName())
                    .field("case_id", testCase.id())
                    .field("case_index", run.caseIndex)
                    .field("repeat", testCase.repeatIndex())
                    .field("cycle_index", run.mode == SpotTestMode.PARALLEL
                            ? testCase.repeatIndex() + 1 : testCase.repeatIndex())
                    .field("warmup_phase", run.mode == SpotTestMode.PARALLEL
                            ? parallelWarmupPhase(testCase.repeatIndex()) : "not_applicable")
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
                    .field("source_count", testCase.sourceCount())
                    .field("scene_layout", testCase.sharedParallelArena()
                            ? "shared_overlap" : "standard")
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
        if (level == null || !validateGeneratedTest(source, level, generatedTest)) {
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

        List<SpotProjectionPerformanceTracker.Report> reports = test.layouts().stream()
                .map(layout -> SpotProjectionPerformanceTracker.report(
                        level, layout.source(), layout.direction(), count
                ))
                .toList();
        if (reports.size() == 1) {
            sendReport(player, level, reports.get(0));
            return Math.max(1, reports.get(0).samples());
        }
        MultiSourceMetrics metrics = MultiSourceMetrics.from(reports);
        player.sendSystemMessage(Component.literal(String.format(
                Locale.ROOT,
                "Multi-source spot report: sources=%d jobs=%d jobAvg=%.2f ms slowestP95=%.2f ms totalSpots=%.1f",
                metrics.sourceCount(),
                metrics.jobSamples(),
                metrics.jobElapsedAverageUs() / 1_000.0D,
                metrics.slowestSourceP95Us() / 1_000.0D,
                metrics.totalSpotsAverage()
        )));
        logMultiSourceReport(level, metrics, count);
        return Math.max(1, metrics.jobSamples());
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
        if (level == null || !validateGeneratedTest(source, level, test)) {
            return 0;
        }

        resetBenchmarkSamples(level, test);
        BENCHMARKS_BY_PLAYER.put(player.getUUID(), new BenchmarkRun(
                test,
                count,
                level.getGameTime() + BENCHMARK_RETRY_TICKS,
                level.getGameTime() + BENCHMARK_TIMEOUT_TICKS
        ));
        startBenchmarkProjection(level, BENCHMARKS_BY_PLAYER.get(player.getUUID()));
        source.sendSuccess(() -> Component.literal(String.format(
                "Started source-bound spot benchmark: samples=%d sources=%d firstSource=%s direction=%s",
                count,
                test.layouts().size(),
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

            run.observeTick(level);
            if (run.awaitingProjectionStart) {
                if (run.suiteRunId != null
                        && run.test.testCase().id().equals("random_stress")) {
                    SuiteBenchmarkCompletion completion = runRandomStressProjectionTick(
                            entry.getKey(), level, run
                    );
                    if (completion == null) {
                        continue;
                    }
                    suiteCompletions.add(completion);
                    iterator.remove();
                    continue;
                }
                List<OpticalTraceCache.ProjectionSource> projectionSources = projectionSources(run.test);
                OpticalTraceCache.ProjectionWorkState workState = OpticalTraceCache.projectionWorkState(level);
                if (workState.idle() && OpticalTraceCache.projectionSourcesReady(level, projectionSources)) {
                    run.quietTicksObserved++;
                } else {
                    run.quietTicksObserved = 0;
                }
                if (run.quietTicksObserved < run.quietTicksRequired) {
                    continue;
                }
                resetBenchmarkSamples(level, run.test);
                if (!requestBenchmarkProjection(level, run)) {
                    run.quietTicksObserved = 0;
                    continue;
                }
                run.awaitingProjectionStart = false;
                run.startLatency(level);
                run.deadlineTick = level.getGameTime() + BENCHMARK_TIMEOUT_TICKS;
                run.nextRetryTick = level.getGameTime() + BENCHMARK_RETRY_TICKS;
                logProjectionWaveStarted(level, run, workState);
                continue;
            }
            List<SpotProjectionPerformanceTracker.Report> sourceReports = benchmarkReports(level, run);
            SpotProjectionPerformanceTracker.Report report = sourceReports.get(0);
            int completedRounds = completedBenchmarkRounds(sourceReports);
            if (run.warmupPhase > 0 && completedRounds >= 1) {
                run.discardPendingLatency();
                resetBenchmarkSamples(level, run.test);
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
            if (completedRounds >= run.targetSamples) {
                run.completePendingLatency();
                run.captureProjectionBatchObservation(level);
                if (run.suiteRunId == null) {
                    if (sourceReports.size() == 1) {
                        sendReport(player, level, report);
                    } else {
                        MultiSourceMetrics metrics = MultiSourceMetrics.from(sourceReports);
                        player.sendSystemMessage(Component.literal(String.format(
                                Locale.ROOT,
                                "Multi-source benchmark: sources=%d rounds=%d jobAvg=%.2f ms slowestP95=%.2f ms batchP95=%.2f ms",
                                metrics.sourceCount(),
                                run.targetSamples,
                                metrics.jobElapsedAverageUs() / 1_000.0D,
                                metrics.slowestSourceP95Us() / 1_000.0D,
                                run.latencyReport().p95Us() / 1_000.0D
                        )));
                        logMultiSourceReport(level, metrics, run.targetSamples);
                    }
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
                            sourceReports,
                            run.latencyReport(),
                            run.observation(),
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
                            completedRounds,
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

            if (completedRounds > run.observedSamples) {
                run.completePendingLatency();
                run.observedSamples = completedRounds;
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
            } else if (gameTime >= run.nextRetryTick
                    && OpticalTraceCache.projectionWorkState(level).idle()
                    && requestBenchmarkProjection(level, run)) {
                run.nextRetryTick = gameTime + BENCHMARK_RETRY_TICKS;
            }
        }

        for (SuiteBenchmarkCompletion completion : suiteCompletions) {
            completeItemSuiteBenchmark(server, completion);
        }
    }

    /**
     * Runs exactly one anonymous single-source scene in the current server tick. Random stress is
     * a core-throughput test, so it uses the synchronous single-source test lane instead of paying
     * the production async prepare/worker/commit tick boundaries 1,000 times. All other suites,
     * especially the multi-source suite, retain the ordinary asynchronous state machine.
     */
    private static SuiteBenchmarkCompletion runRandomStressProjectionTick(
            UUID owner,
            ServerLevel level,
            BenchmarkRun run
    ) {
        List<OpticalTraceCache.ProjectionSource> sources = projectionSources(run.test);
        if (sources.size() != 1 || run.targetSamples != 1) {
            return new SuiteBenchmarkCompletion(
                    owner,
                    run.suiteRunId,
                    SpotProjectionPerformanceTracker.Report.EMPTY,
                    RequestLatencyReport.EMPTY,
                    false,
                    "random_stress_requires_one_source_one_sample"
            );
        }
        OpticalTraceCache.ProjectionSource source = sources.getFirst();
        if (!OpticalTraceCache.projectionSourcesReady(level, sources)) {
            return null;
        }

        resetBenchmarkSamples(level, run.test);
        run.awaitingProjectionStart = false;
        run.startLatency(level);
        if (!OpticalTraceCache.runSingleSourceSpotProjectionTestNow(
                level, source, "random_stress_tick_full_rebuild"
        )) {
            run.discardPendingLatency();
            run.awaitingProjectionStart = true;
            return null;
        }
        run.completePendingLatency();

        List<SpotProjectionPerformanceTracker.Report> sourceReports = benchmarkReports(level, run);
        SpotProjectionPerformanceTracker.Report report = sourceReports.getFirst();
        boolean complete = completedBenchmarkRounds(sourceReports) >= run.targetSamples;
        return new SuiteBenchmarkCompletion(
                owner,
                run.suiteRunId,
                report,
                sourceReports,
                run.latencyReport(),
                run.observation(),
                complete,
                complete ? "tick_sample_complete" : "tick_sample_missing"
        );
    }

    /**
     * Samples benchmark response latency immediately after the projection queue has had its
     * server-thread commit opportunity for this tick. The main benchmark state machine still
     * advances only from {@link #tickBenchmarks(MinecraftServer)}, so this observation does not
     * shorten quiet intervals or start another projection wave in the same tick.
     */
    public static void observeBenchmarkCompletions(MinecraftServer server) {
        for (Map.Entry<UUID, BenchmarkRun> entry : BENCHMARKS_BY_PLAYER.entrySet()) {
            BenchmarkRun run = entry.getValue();
            ServerLevel level = server.getLevel(run.test.dimension());
            if (level == null || run.requestStartNanos <= 0L) {
                continue;
            }
            int completedRounds = completedBenchmarkRounds(benchmarkReports(level, run));
            if (completedRounds <= run.observedSamples) {
                continue;
            }
            if (run.warmupPhase > 0) {
                run.discardPendingLatency();
            } else {
                run.completePendingLatency();
            }
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
        MultiSourceMetrics multiSource = MultiSourceMetrics.from(completion.sourceReports());
        int expectedJobSamples = testCase.samples() * testCase.sourceCount();
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
        List<OutputFingerprint> sourceFingerprints = run.currentLayouts().stream()
                .map(layout -> SpotProjectionPerformanceTracker.latestOutputFingerprint(
                        level, layout.source(), layout.direction()
                ))
                .toList();
        MultiSourceFingerprint multiSourceFingerprint = MultiSourceFingerprint.from(sourceFingerprints);
        SpotProjectionResult.OptimizationStats optimization = stats.optimization();
        long structuralMismatches = optimization.remainingSubtractValidationMismatches()
                + optimization.sameDepthSplitValidationMismatches()
                + optimization.sameDepthPrefixValidationMismatches()
                + optimization.sideCanonicalValidationMismatches();
        long structuralChecks = optimization.remainingSubtractValidationChecks()
                + optimization.sameDepthSplitValidationChecks()
                + optimization.sameDepthPrefixValidationChecks()
                + optimization.sideCanonicalValidationChecks();
        boolean validationEvidencePresent = run.mode != SpotTestMode.SMART
                || !testCase.verboseValidation()
                || structuralChecks > 0L;
        boolean validationPassed = !testCase.verboseValidation()
                || (stats.sideBoundaryMissingFaces() == 0L
                && structuralMismatches == 0L
                && validationEvidencePresent);
        boolean cachePassed = testCase.benchmarkMode() != BenchmarkMode.CACHE_REUSE
                || (multiSource.appearanceOnlySamples() == expectedJobSamples
                && multiSource.fullRebuildSamples() == 0);
        boolean parallelRebuildPassed = run.mode != SpotTestMode.PARALLEL
                || (multiSource.jobSamples() == expectedJobSamples
                && multiSource.fullRebuildSamples() == expectedJobSamples
                && multiSource.suffixRebuildSamples() == 0
                && multiSource.appearanceOnlySamples() == 0
                && multiSourceFingerprint.complete());
        boolean parallelSubmissionPassed = run.mode != SpotTestMode.PARALLEL
                || completion.benchmark().submitRejected() == 0L;
        boolean parallelFingerprintPassed = run.recordParallelResult(
                testCase,
                multiSource,
                completion.latency(),
                completion.benchmark(),
                multiSourceFingerprint
        );
        boolean passed = validationPassed
                && cachePassed
                && parallelRebuildPassed
                && parallelSubmissionPassed
                && parallelFingerprintPassed;
        String result = passed
                ? (testCase.verboseValidation() ? "pass" : "complete")
                : "fail";

        if (run.mode == SpotTestMode.RANDOM_STRESS) {
            run.recordRandomStressResult(
                    completion.report(), completion.latency(), level.getGameTime()
            );
        } else {
            SpectralDiagnostics.Event completedEvent = SpectralDiagnostics
                    .event(level, "spot_projection_test", "case_complete")
                    .field("run_id", run.runId)
                    .field("suite", run.mode.serializedName())
                    .field("load", run.load.serializedName())
                    .field("case_id", testCase.id())
                    .field("case_index", run.caseIndex)
                    .field("repeat", testCase.repeatIndex())
                    .field("cycle_index", run.mode == SpotTestMode.PARALLEL
                            ? testCase.repeatIndex() + 1 : testCase.repeatIndex())
                    .field("warmup_phase", run.mode == SpotTestMode.PARALLEL
                            ? parallelWarmupPhase(testCase.repeatIndex()) : "not_applicable")
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
                    .field("output_fingerprint_scope", testCase.sourceCount() == 1
                            ? "complete_case" : "primary_source")
                    .field("samples", completion.report().samples())
                    .field("sample_rounds", testCase.samples())
                    .field("source_count", testCase.sourceCount())
                    .field("job_samples", multiSource.jobSamples())
                    .field("elapsed_avg_us", completion.report().elapsedAverageUs())
                    .field("elapsed_p50_us", completion.report().elapsedP50Us())
                    .field("elapsed_p95_us", completion.report().elapsedP95Us())
                    .field("response_samples", completion.latency().samples())
                    .field("response_avg_us", completion.latency().averageUs())
                    .field("response_p50_us", completion.latency().p50Us())
                    .field("response_p95_us", completion.latency().p95Us())
                    .field("response_max_us", completion.latency().maxUs())
                    .field("max_tick_stall_us", completion.latency().maxTickStallUs())
                    .field("job_elapsed_avg_us", multiSource.jobElapsedAverageUs())
                    .field("slowest_source_p50_us", multiSource.slowestSourceP50Us())
                    .field("slowest_source_p95_us", multiSource.slowestSourceP95Us())
                    .field("source_completion_tick_spread", multiSource.completionTickSpread())
                    .field("quiet_ticks_required", completion.benchmark().quietTicksRequired())
                    .field("quiet_ticks_observed", completion.benchmark().quietTicksObserved())
                    .field("projection_max_in_flight", completion.benchmark().maxProjectionInFlight())
                    .field("projection_max_queue_depth", completion.benchmark().maxProjectionQueueDepth())
                    .field("projection_submit_tick_spread", completion.benchmark().submitTickSpread())
                    .field("projection_commit_tick_spread", completion.benchmark().commitTickSpread())
                    .field("projection_dispatch_waves", completion.benchmark().dispatchWaves())
                    .field("projection_dispatch_width_avg", completion.benchmark().averageDispatchWidth())
                    .field("projection_dispatch_width_max", completion.benchmark().maxDispatchWidth())
                    .field("projection_worker_total_us", completion.benchmark().totalWorkerUs())
                    .field("projection_commit_total_us", completion.benchmark().totalCommitUs())
                    .field("projection_commit_assembly_total_us", completion.benchmark().totalAssemblyUs())
                    .field("projection_commit_diagnostics_total_us",
                            completion.benchmark().totalDiagnosticsUs())
                    .field("projection_commit_dependency_index_total_us",
                            completion.benchmark().totalDependencyIndexUs())
                    .field("projection_commit_owner_publish_total_us",
                            completion.benchmark().totalOwnerPublishUs())
                    .field("projection_commit_dependency_index_reused",
                            completion.benchmark().dependencyIndexReused())
                    .field("projection_commit_owner_publish_reused",
                            completion.benchmark().ownerPublishReused())
                    .field("projection_submit_rejected", completion.benchmark().submitRejected())
                    .field("projection_commit_budget_deferred", completion.benchmark().commitBudgetDeferred())
                    .field("spots_avg", multiSource.totalSpotsAverage())
                    .field("benchmark_mode", testCase.benchmarkMode().serializedName)
                    .field("appearance_mutation", testCase.appearanceMutation().serializedName)
                    .field("full_rebuild_samples", multiSource.fullRebuildSamples())
                    .field("suffix_rebuild_samples", multiSource.suffixRebuildSamples())
                    .field("appearance_only_samples", multiSource.appearanceOnlySamples())
                    .field("validation_enabled", testCase.verboseValidation())
                    .field("boundary_missing", stats.sideBoundaryMissingFaces())
                    .field("boundary_missing_details", optimization.sideBoundaryMissingDetails().size())
                    .field("remaining_validation_checks", optimization.remainingSubtractValidationChecks())
                    .field("same_depth_split_validation_checks", optimization.sameDepthSplitValidationChecks())
                    .field("same_depth_prefix_validation_checks", optimization.sameDepthPrefixValidationChecks())
                    .field("side_canonical_validation_checks", optimization.sideCanonicalValidationChecks())
                    .field("structural_validation_checks", structuralChecks)
                    .field("validation_evidence_present", validationEvidencePresent)
                    .field("structural_mismatches", structuralMismatches)
                    .field("cache_expected_appearance_only", testCase.benchmarkMode() == BenchmarkMode.CACHE_REUSE
                            ? expectedJobSamples : 0)
                    .field("cache_validation_passed", cachePassed)
                    .field("parallel_rebuild_validation_passed", parallelRebuildPassed)
                    .field("parallel_submission_validation_passed", parallelSubmissionPassed)
                    .field("parallel_cycle_fingerprint_passed", parallelFingerprintPassed)
                    .field("source_output_fingerprints_complete", multiSourceFingerprint.complete())
                    .field("aggregate_output_coverage_signature", Long.toUnsignedString(
                            multiSourceFingerprint.coverageSignature(), 16
                    ))
                    .field("aggregate_output_fragmentation_signature", Long.toUnsignedString(
                            multiSourceFingerprint.fragmentationSignature(), 16
                    ))
                    .field("result", result)
                    .write();
            if (run.mode == SpotTestMode.PARALLEL) {
                logMultiSourceReport(level, multiSource, testCase.samples());
            }
            if (run.mode != SpotTestMode.SMART || !"smart_warmup".equals(testCase.id())) {
                sendItemSuiteCaseSummary(
                        player, run, testCase, completion.report(), completion.latency(), multiSource, passed
                );
            }
        }
        run.recordSmartResult(testCase, completion.report(), completion.latency(), structuralChecks);

        if (!passed) {
            if (!cachePassed) {
                player.sendSystemMessage(Component.translatable(
                        "item.spectralization.spot_test.message.case_failed_cache",
                        multiSource.appearanceOnlySamples(),
                        expectedJobSamples,
                        multiSource.fullRebuildSamples()
                ).withStyle(ChatFormatting.RED));
            } else if (!parallelSubmissionPassed) {
                player.sendSystemMessage(Component.literal(String.format(
                        Locale.ROOT,
                        "Parallel projection rejected %d executor submission(s); the case is invalid.",
                        completion.benchmark().submitRejected()
                )).withStyle(ChatFormatting.RED));
            } else if (!parallelRebuildPassed) {
                player.sendSystemMessage(Component.translatable(
                        "item.spectralization.spot_test.message.case_failed_parallel",
                        multiSource.fullRebuildSamples(),
                        expectedJobSamples,
                        multiSource.suffixRebuildSamples(),
                        multiSource.appearanceOnlySamples(),
                        multiSourceFingerprint.complete()
                ).withStyle(ChatFormatting.RED));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "item.spectralization.spot_test.message.case_failed",
                        stats.sideBoundaryMissingFaces(),
                        structuralMismatches,
                        structuralChecks
                ).withStyle(ChatFormatting.RED));
            }
            finishItemSuite(
                    player,
                    level,
                    false,
                    !cachePassed
                            ? "cache_validation_failed"
                            : (!parallelRebuildPassed
                            ? "parallel_rebuild_validation_failed"
                            : (!parallelSubmissionPassed
                            ? "parallel_submit_rejected"
                            : (!parallelFingerprintPassed
                            ? "parallel_fingerprint_mismatch" : "validation_failed")))
            );
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
                    Math.round(summary.spotsAverage()),
                    String.format(Locale.ROOT, "%.2f", summary.casesPerSecond())
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
            ParallelSuiteSummary parallelSummary = run.mode == SpotTestMode.PARALLEL
                    ? run.parallelSummary() : null;
            finishItemSuite(
                    player,
                    level,
                    matrixPassed,
                    parallelSummary != null && parallelSummary.warmupUnstable()
                            ? "warmup_unstable"
                            : (matrixPassed
                            ? (matrixAsymmetric ? "direction_matrix_asymmetry" : "complete")
                            : "direction_matrix_correctness_mismatch")
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
            MultiSourceMetrics multiSource,
            boolean passed
    ) {
        if (run.mode == SpotTestMode.PARALLEL) {
            player.sendSystemMessage(Component.translatable(
                    "item.spectralization.spot_test.message.case_summary_parallel",
                    passed ? "PASS" : "FAIL",
                    run.caseIndex + 1,
                    run.cases.size(),
                    testCase.sourceCount(),
                    formatMillis(multiSource.jobElapsedAverageUs()),
                    formatMillis(multiSource.slowestSourceP95Us()),
                    formatMillis(latency.p50Us()),
                    formatMillis(latency.p95Us()),
                    formatMillis(latency.maxTickStallUs()),
                    multiSource.fullRebuildSamples(),
                    testCase.samples() * testCase.sourceCount()
            ).withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
            return;
        }
        String key = testCase.benchmarkMode() == BenchmarkMode.CACHE_REUSE
                ? "item.spectralization.spot_test.message.case_summary_cache"
                : "item.spectralization.spot_test.message.case_summary_rebuild";
        long modeSamples = testCase.benchmarkMode() == BenchmarkMode.CACHE_REUSE
                ? report.appearanceOnlySamples()
                : report.fullRebuildSamples();
        player.sendSystemMessage(Component.translatable(
                key,
                passed ? "PASS" : "FAIL",
                run.caseIndex + 1,
                run.cases.size(),
                testCase.displayName(run.currentLayout().direction()),
                formatMillis(report.elapsedP50Us()),
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

    private static void writeParallelSummary(
            ServerLevel level,
            ItemSuiteRun run,
            ParallelSuiteSummary summary
    ) {
        for (ParallelCountSummary count : summary.bySourceCount()) {
            SpectralDiagnostics.event(level, "spot_projection_test", "parallel_source_summary")
                    .field("run_id", run.runId)
                    .field("source_count", count.sourceCount())
                    .field("steady_cycles", count.steadyCycles())
                    .field("worker_p50_us", count.workerP50Us())
                    .field("worker_p95_us", count.workerP95Us())
                    .field("response_p50_us", count.responseP50Us())
                    .field("response_p95_us", count.responseP95Us())
                    .field("stabilizing_worker_median_us", count.stabilizingWorkerMedianUs())
                    .field("steady_worker_median_us", count.steadyWorkerMedianUs())
                    .field("warmup_worker_drift_ratio", count.warmupWorkerDriftRatio())
                    .field("stabilizing_response_median_us", count.stabilizingResponseMedianUs())
                    .field("steady_response_median_us", count.steadyResponseMedianUs())
                    .field("warmup_drift_ratio", count.warmupDriftRatio())
                    .field("warmup_unstable", count.warmupUnstable())
                    .field("max_in_flight", count.maxInFlight())
                    .field("max_queue_depth", count.maxQueueDepth())
                    .field("dispatch_width_avg", count.averageDispatchWidth())
                    .field("dispatch_width_max", count.maxDispatchWidth())
                    .field("max_commit_tick_spread", count.maxCommitTickSpread())
                    .field("projection_submit_rejected", count.submitRejected())
                    .field("projection_commit_budget_deferred", count.commitBudgetDeferred())
                    .write();
        }
        SpectralDiagnostics.event(level, "spot_projection_test", "parallel_suite_summary")
                .field("run_id", run.runId)
                .field("cycles", PARALLEL_CYCLES)
                .field("source_counts", PARALLEL_SOURCE_COUNTS.length)
                .field("steady_cases", summary.steadyCases())
                .field("formal_cycles", "6-10")
                .field("worker_p50_us", summary.workerP50Us())
                .field("worker_p95_us", summary.workerP95Us())
                .field("response_p50_us", summary.responseP50Us())
                .field("response_p95_us", summary.responseP95Us())
                .field("max_in_flight", summary.maxInFlight())
                .field("max_queue_depth", summary.maxQueueDepth())
                .field("dispatch_width_avg", summary.averageDispatchWidth())
                .field("dispatch_width_max", summary.maxDispatchWidth())
                .field("warmup_unstable", summary.warmupUnstable())
                .field("fingerprint_mismatches", summary.fingerprintMismatches())
                .field("projection_submit_rejected", summary.submitRejected())
                .field("projection_commit_budget_deferred", summary.commitBudgetDeferred())
                .field("aggregate_output_coverage_signature", Long.toUnsignedString(
                        summary.aggregateCoverageSignature(), 16
                ))
                .field("aggregate_output_fragmentation_signature", Long.toUnsignedString(
                        summary.aggregateFragmentationSignature(), 16
                ))
                .write();
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
        SmartSuiteSummary smartSummary = run.mode == SpotTestMode.SMART ? run.smartSummary() : null;
        SmartSuiteSummary previousSmartSummary = run.mode == SpotTestMode.SMART
                ? LAST_SMART_SUMMARY_BY_PLAYER.get(new SmartBaselineKey(run.owner, run.load))
                : null;

        if (level != null) {
            if (run.mode == SpotTestMode.SMART && smartSummary != null) {
                writeSmartSummary(level, run, smartSummary, previousSmartSummary, passed);
            } else if (run.mode == SpotTestMode.DIRECTION_MATRIX) {
                writeDirectionMatrixSummary(level, run);
            } else if (run.mode == SpotTestMode.RANDOM_STRESS) {
                writeRandomStressSummary(level, run, passed);
            } else if (run.mode == SpotTestMode.PARALLEL) {
                writeParallelSummary(level, run, run.parallelSummary());
            }
            SpectralDiagnostics.event(level, "spot_projection_test", "suite_complete")
                    .field("run_id", run.runId)
                    .field("suite", run.mode.serializedName())
                    .field("load", run.load.serializedName())
                    .field("cases", run.cases.size())
                    .field("passed_cases", run.passedCases)
                    .field("result", passed ? "pass" : "fail")
                    .field("reason", reason)
                    .write();
        }
        if (player != null) {
            if (run.mode == SpotTestMode.SMART && smartSummary != null) {
                sendSmartSummary(player, smartSummary, previousSmartSummary, passed);
            } else if (run.mode == SpotTestMode.DIRECTION_MATRIX) {
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
                        Math.round(summary.spotsAverage()),
                        String.format(Locale.ROOT, "%.2f", summary.casesPerSecond()),
                        String.format(Locale.ROOT, "%.2f", summary.wallElapsedSeconds())
                ).withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
            } else if (run.mode == SpotTestMode.PARALLEL) {
                ParallelSuiteSummary summary = run.parallelSummary();
                player.sendSystemMessage(Component.literal(String.format(
                        Locale.ROOT,
                        "Parallel steady-state (cycles 6-10): cases=%d, response P50/P95=%.2f/%.2f ms, worker P50/P95=%.2f/%.2f ms, dispatch avg/max=%.2f/%d, max in-flight=%d, max queue=%d, warmup=%s",
                        summary.steadyCases(),
                        summary.responseP50Us() / 1_000.0D,
                        summary.responseP95Us() / 1_000.0D,
                        summary.workerP50Us() / 1_000.0D,
                        summary.workerP95Us() / 1_000.0D,
                        summary.averageDispatchWidth(),
                        summary.maxDispatchWidth(),
                        summary.maxInFlight(),
                        summary.maxQueueDepth(),
                        summary.warmupUnstable() ? "UNSTABLE" : "stable"
                )).withStyle(summary.warmupUnstable() ? ChatFormatting.YELLOW : ChatFormatting.GREEN));
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
        if (run.mode == SpotTestMode.SMART
                && passed
                && smartSummary != null
                && smartSummary.complete()) {
            LAST_SMART_SUMMARY_BY_PLAYER.put(new SmartBaselineKey(run.owner, run.load), smartSummary);
        }
    }

    private static void sendSmartSummary(
            ServerPlayer player,
            SmartSuiteSummary summary,
            SmartSuiteSummary previous,
            boolean passed
    ) {
        if (!summary.complete()) {
            player.sendSystemMessage(Component.translatable(
                    "item.spectralization.spot_test.message.smart_incomplete"
            ).withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.translatable(
                "item.spectralization.spot_test.message.smart_core",
                formatMillis(summary.sparse().report().elapsedP50Us()),
                formatMillis(summary.sparse().report().elapsedP95Us()),
                formatMillis(summary.mixed().report().elapsedP50Us()),
                formatMillis(summary.mixed().report().elapsedP95Us()),
                formatMillis(summary.dense().report().elapsedP50Us()),
                formatMillis(summary.dense().report().elapsedP95Us())
        ).withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
        SmartHotspot hotspot = summary.hotspot();
        player.sendSystemMessage(Component.translatable(
                "item.spectralization.spot_test.message.smart_hotspot",
                Component.translatable(hotspot.translationKey()),
                formatMillis(hotspot.averageUs()),
                String.format(Locale.ROOT, "%.1f", hotspot.sharePercent()),
                Component.translatable(summary.stabilityTranslationKey()),
                String.format(Locale.ROOT, "%.2f", summary.denseTailRatio())
        ).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable(
                "item.spectralization.spot_test.message.smart_validation",
                summary.validation().structuralChecks(),
                formatMillis(summary.cachedPower().report().elapsedP50Us()),
                formatMillis(summary.cachedColor().report().elapsedP50Us())
        ).withStyle(summary.validation().structuralChecks() > 0L
                ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (previous != null && previous.complete()) {
            player.sendSystemMessage(Component.translatable(
                    "item.spectralization.spot_test.message.smart_comparison",
                    formatSignedPercent(percentDelta(
                            previous.sparse().report().elapsedP50Us(), summary.sparse().report().elapsedP50Us()
                    )),
                    formatSignedPercent(percentDelta(
                            previous.mixed().report().elapsedP50Us(), summary.mixed().report().elapsedP50Us()
                    )),
                    formatSignedPercent(percentDelta(
                            previous.dense().report().elapsedP50Us(), summary.dense().report().elapsedP50Us()
                    ))
            ).withStyle(ChatFormatting.YELLOW));
        } else {
            player.sendSystemMessage(Component.translatable(
                    "item.spectralization.spot_test.message.smart_baseline"
            ).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void writeSmartSummary(
            ServerLevel level,
            ItemSuiteRun run,
            SmartSuiteSummary summary,
            SmartSuiteSummary previous,
            boolean passed
    ) {
        SpectralDiagnostics.Event event = SpectralDiagnostics
                .event(level, "spot_projection_test", "smart_suite_complete")
                .field("run_id", run.runId)
                .field("load", run.load.serializedName())
                .field("complete", summary.complete())
                .field("result", passed ? "pass" : "fail");
        if (!summary.complete()) {
            event.write();
            return;
        }
        SmartHotspot hotspot = summary.hotspot();
        event.field("validation_checks", summary.validation().structuralChecks())
                .field("sparse_p50_us", summary.sparse().report().elapsedP50Us())
                .field("sparse_p95_us", summary.sparse().report().elapsedP95Us())
                .field("mixed_p50_us", summary.mixed().report().elapsedP50Us())
                .field("mixed_p95_us", summary.mixed().report().elapsedP95Us())
                .field("dense_p50_us", summary.dense().report().elapsedP50Us())
                .field("dense_p95_us", summary.dense().report().elapsedP95Us())
                .field("dense_tail_ratio", summary.denseTailRatio())
                .field("hotspot", hotspot.id())
                .field("hotspot_avg_us", hotspot.averageUs())
                .field("hotspot_share_percent", hotspot.sharePercent())
                .field("cached_power_p50_us", summary.cachedPower().report().elapsedP50Us())
                .field("cached_color_p50_us", summary.cachedColor().report().elapsedP50Us());
        if (previous != null && previous.complete()) {
            event.field("previous_available", true)
                    .field("sparse_p50_delta_percent", percentDelta(
                            previous.sparse().report().elapsedP50Us(), summary.sparse().report().elapsedP50Us()
                    ))
                    .field("mixed_p50_delta_percent", percentDelta(
                            previous.mixed().report().elapsedP50Us(), summary.mixed().report().elapsedP50Us()
                    ))
                    .field("dense_p50_delta_percent", percentDelta(
                            previous.dense().report().elapsedP50Us(), summary.dense().report().elapsedP50Us()
                    ));
        } else {
            event.field("previous_available", false);
        }
        event.write();
    }

    private static double percentDelta(double previous, double current) {
        return previous <= 0.0D ? 0.0D : (current - previous) * 100.0D / previous;
    }

    private static String formatSignedPercent(double value) {
        return String.format(Locale.ROOT, "%+.1f%%", value);
    }

    private static void writeDirectionMatrixSummary(ServerLevel level, ItemSuiteRun run) {
        SpectralDiagnostics.event(level, "spot_projection_test", "direction_matrix_complete")
                .field("run_id", run.runId)
                .field("load", run.load.serializedName())
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
                .field("projection_execution", "server_tick_serial")
                .field("target_cases_per_tick", RANDOM_STRESS_CASES_PER_TICK)
                .field("wall_elapsed_seconds", summary.wallElapsedSeconds())
                .field("cases_per_second", summary.casesPerSecond())
                .field("completion_tick_span", summary.completionTickSpan())
                .field("missed_completion_ticks", summary.missedCompletionTicks())
                .write();
    }

    private static void writeRandomStressSummary(ServerLevel level, ItemSuiteRun run, boolean passed) {
        RandomStressSummary summary = run.randomStressSummary();
        SpectralDiagnostics.event(level, "spot_projection_test", "random_stress_complete")
                .field("run_id", run.runId)
                .field("load", run.load.serializedName())
                .field("cases", summary.cases())
                .field("elapsed_avg_us", summary.elapsedAverageUs())
                .field("elapsed_p50_us", summary.elapsedP50Us())
                .field("elapsed_p95_us", summary.elapsedP95Us())
                .field("response_avg_us", summary.responseAverageUs())
                .field("response_p95_us", summary.responseP95Us())
                .field("spots_avg", summary.spotsAverage())
                .field("projection_execution", "server_tick_serial")
                .field("target_cases_per_tick", RANDOM_STRESS_CASES_PER_TICK)
                .field("wall_elapsed_seconds", summary.wallElapsedSeconds())
                .field("cases_per_second", summary.casesPerSecond())
                .field("completion_tick_span", summary.completionTickSpan())
                .field("missed_completion_ticks", summary.missedCompletionTicks())
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

    private static void logMultiSourceReport(
            ServerLevel level,
            MultiSourceMetrics metrics,
            int requestedSamplesPerSource
    ) {
        SpectralDiagnostics.event(level, "spot_projection_test", "multi_source_report")
                .field("source_count", metrics.sourceCount())
                .field("requested_samples_per_source", requestedSamplesPerSource)
                .field("job_samples", metrics.jobSamples())
                .field("full_rebuild_samples", metrics.fullRebuildSamples())
                .field("suffix_rebuild_samples", metrics.suffixRebuildSamples())
                .field("appearance_only_samples", metrics.appearanceOnlySamples())
                .field("job_elapsed_avg_us", metrics.jobElapsedAverageUs())
                .field("slowest_source_p50_us", metrics.slowestSourceP50Us())
                .field("slowest_source_p95_us", metrics.slowestSourceP95Us())
                .field("total_spots_avg", metrics.totalSpotsAverage())
                .field("source_completion_tick_spread", metrics.completionTickSpread())
                .write();
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
        SpotProjectionTestScene.BuildResult result = generatedTest.testCase().sharedParallelArena()
                ? SpotProjectionTestScene.buildParallelArena(
                        level,
                        generatedTest.arenaLayout(),
                        generatedTest.arenaLayout().parallelSources(9),
                        generatedTest.seed(),
                        generatedTest.testCase().occupancy(),
                        generatedTest.testCase().fixtures(),
                        generatedTest.testCase().divergenceMilli(),
                        generatedTest.testCase().obstacleProfile(),
                        logDetails
                )
                : SpotProjectionTestScene.build(
                        level,
                        generatedTest.layouts(),
                        generatedTest.seed(),
                        generatedTest.testCase().occupancy(),
                        generatedTest.testCase().fixtures(),
                        generatedTest.testCase().divergenceMilli(),
                        generatedTest.testCase().obstacleProfile(),
                        logDetails
                );
        if (logDetails) {
            SpectralDiagnostics.Event event = SpectralDiagnostics
                    .event(level, "spot_projection_test", "generated")
                    .pos("source", generatedTest.layout().source())
                    .field("direction", generatedTest.layout().direction())
                    .field("source_count", generatedTest.layouts().size())
                    .field("scene_layout", generatedTest.testCase().sharedParallelArena()
                            ? "shared_overlap" : "standard")
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
                    .field("obstacle_profile", generatedTest.testCase().obstacleProfile().serializedName())
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

        int cleared = generatedTest.testCase().sharedParallelArena()
                ? SpotProjectionTestScene.clearParallelArena(level, generatedTest.arenaLayout())
                : SpotProjectionTestScene.clear(level, generatedTest.layouts());
        SpotProjectionTestScene.refreshProjection(level);
        SpectralDiagnostics.Event event = SpectralDiagnostics.event(level, "spot_projection_test", "cleared")
                .pos("source", generatedTest.layout().source())
                .field("direction", generatedTest.layout().direction())
                .field("source_count", generatedTest.layouts().size())
                .field("scene_layout", generatedTest.testCase().sharedParallelArena()
                        ? "shared_overlap" : "standard");
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

    private static void resetBenchmarkSamples(ServerLevel level, GeneratedTest test) {
        for (SpotTestLayout layout : test.layouts()) {
            SpotProjectionPerformanceTracker.reset(level, layout.source(), layout.direction());
        }
    }

    private static List<SpotProjectionPerformanceTracker.Report> benchmarkReports(
            ServerLevel level,
            BenchmarkRun run
    ) {
        List<SpotProjectionPerformanceTracker.Report> reports = new ArrayList<>(run.test.layouts().size());
        for (SpotTestLayout layout : run.test.layouts()) {
            reports.add(SpotProjectionPerformanceTracker.report(
                    level, layout.source(), layout.direction(), run.targetSamples
            ));
        }
        return List.copyOf(reports);
    }

    private static int completedBenchmarkRounds(List<SpotProjectionPerformanceTracker.Report> reports) {
        return reports.stream()
                .mapToInt(SpotProjectionPerformanceTracker.Report::samples)
                .min()
                .orElse(0);
    }

    private static boolean requestBenchmarkProjection(ServerLevel level, BenchmarkRun run) {
        if (run == null) {
            return false;
        }
        List<OpticalTraceCache.ProjectionSource> sources = projectionSources(run.test);
        if (run.benchmarkMode == BenchmarkMode.FULL_REBUILD) {
            return OpticalTraceCache.requestSpotProjectionRebuild(
                    level, sources, "benchmark_forced_rebuild"
            );
        }
        return OpticalTraceCache.requestSpotProjectionRefresh(level, sources);
    }

    private static void startBenchmarkProjection(ServerLevel level, BenchmarkRun run) {
        if (run == null) {
            return;
        }
        if (run.forceInitialGeometryWarmup) {
            for (SpotTestLayout layout : run.test.layouts()) {
                CompiledSpotLayer.invalidateProjectionGeometry(
                        level,
                        layout.source(),
                        layout.direction(),
                        "benchmark_cache_geometry_warmup"
                );
            }
            run.forceInitialGeometryWarmup = false;
        }
        run.awaitingProjectionStart = true;
        run.quietTicksObserved = 0;
    }

    private static List<OpticalTraceCache.ProjectionSource> projectionSources(GeneratedTest test) {
        return test.layouts().stream()
                .map(layout -> new OpticalTraceCache.ProjectionSource(layout.source(), layout.direction()))
                .toList();
    }

    private static void logProjectionWaveStarted(
            ServerLevel level,
            BenchmarkRun run,
            OpticalTraceCache.ProjectionWorkState workState
    ) {
        if (run.suiteRunId == null) {
            return;
        }
        SuiteCase testCase = run.test.testCase();
        SpectralDiagnostics.event(level, "spot_projection_test", "projection_wave_started")
                .field("run_id", run.suiteRunId)
                .field("case_id", run.suiteCaseId)
                .field("cycle_index", testCase.sharedParallelArena()
                        ? testCase.repeatIndex() + 1 : testCase.repeatIndex())
                .field("source_count", testCase.sourceCount())
                .field("warmup_phase", testCase.sharedParallelArena()
                        ? parallelWarmupPhase(testCase.repeatIndex()) : "not_applicable")
                .field("quiet_ticks_required", run.quietTicksRequired)
                .field("quiet_ticks_observed", run.quietTicksObserved)
                .field("projection_in_flight_before_submit", workState.executorInFlight())
                .field("projection_queue_before_submit", workState.executorQueueDepth())
                .field("request_tick", level.getGameTime())
                .write();
    }

    private static String parallelWarmupPhase(int zeroBasedCycleIndex) {
        int cycle = zeroBasedCycleIndex + 1;
        if (cycle <= 1) {
            return "cold";
        }
        if (cycle <= 3) {
            return "warming";
        }
        if (cycle <= 5) {
            return "stabilizing";
        }
        return "steady";
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
                for (SpotTestLayout layout : run.test.layouts()) {
                    if (!SpotProjectionTestScene.setSourcePower(level, layout, powerCenti)) {
                        return false;
                    }
                }
                event.field("power_centi", powerCenti)
                        .field("power", powerCenti / (double) CreativeLightSourceBlockEntity.POWER_SCALE);
            }
            case COLOR_SEQUENCE -> {
                int bin = SpotProjectionTestScene.cacheColorBin(step);
                for (SpotTestLayout layout : run.test.layouts()) {
                    if (!SpotProjectionTestScene.setSourceColor(level, layout, bin)) {
                        return false;
                    }
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

    private static boolean validateGeneratedTest(
            CommandSourceStack source,
            ServerLevel level,
            GeneratedTest test
    ) {
        if (test.testCase().sharedParallelArena()) {
            return SpotProjectionTestScene.validateParallelArena(source, level, test.arenaLayout());
        }
        for (SpotTestLayout layout : test.layouts()) {
            if (!SpotProjectionTestScene.validateVolume(source, level, layout)) {
                return false;
            }
        }
        return true;
    }

    private static List<SuiteCase> suiteCases(SpotTestMode mode, SpotTestLoad load) {
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
        List<SuiteCase> cases = switch (mode) {
            case SMART -> smartCases();
            case QUICK -> List.of(quick);
            case PARTIAL_GEOMETRY -> List.of(partial);
            case PERFORMANCE -> performance;
            case PARALLEL -> parallelCases();
            case DIRECTION_MATRIX -> directionMatrixCases();
            case RANDOM_STRESS -> randomStressCases();
        };
        if (load == SpotTestLoad.STRESS) {
            return cases;
        }
        return cases.stream().map(SuiteCase::lightweightVariant).toList();
    }

    private static List<SuiteCase> parallelCases() {
        List<SuiteCase> cases = new ArrayList<>(PARALLEL_SOURCE_COUNTS.length * PARALLEL_CYCLES);
        for (int cycleIndex = 0; cycleIndex < PARALLEL_CYCLES; cycleIndex++) {
            for (int sourceCount : PARALLEL_SOURCE_COUNTS) {
                cases.add(new SuiteCase(
                        "parallel_sources_" + sourceCount,
                        -6_495_254_288_592_929_499L,
                        0.30D,
                        true,
                        650,
                        PARALLEL_BENCHMARK_SAMPLES,
                        false,
                        BenchmarkMode.FULL_REBUILD,
                        AppearanceMutation.NONE
                ).withSourceCountAndRepeat(sourceCount, cycleIndex));
            }
        }
        return List.copyOf(cases);
    }

    private static List<SuiteCase> smartCases() {
        return List.of(
                new SuiteCase(
                        "smart_warmup", 20_260_711L, 0.17D, true, 500,
                        SMART_WARMUP_SAMPLES, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
                ),
                new SuiteCase(
                        "smart_validation", 20_260_711L, 0.17D, true, 500,
                        SMART_VALIDATION_SAMPLES, true, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
                ),
                new SuiteCase(
                        "performance_sparse", 42L, 0.08D, true, 500,
                        SMART_PERFORMANCE_SAMPLES, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
                ),
                new SuiteCase(
                        "performance_mixed", 20_260_711L, 0.17D, true, 500,
                        SMART_PERFORMANCE_SAMPLES, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
                ),
                new SuiteCase(
                        "performance_dense", -6_495_254_288_592_929_499L, 0.30D, true, 650,
                        SMART_PERFORMANCE_SAMPLES, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
                ),
                new SuiteCase(
                        "performance_cached_power", 20_260_711L, 0.17D, true, 500,
                        SMART_CACHE_SAMPLES, false, BenchmarkMode.CACHE_REUSE, AppearanceMutation.POWER_SEQUENCE
                ),
                new SuiteCase(
                        "performance_cached_color", 20_260_711L, 0.17D, true, 500,
                        SMART_CACHE_SAMPLES, false, BenchmarkMode.CACHE_REUSE, AppearanceMutation.COLOR_SEQUENCE
                )
        );
    }

    static void validateSmartSuiteDefinition() {
        List<SuiteCase> cases = smartCases();
        if (cases.size() != 7
                || !"smart_warmup".equals(cases.get(0).id())
                || !"smart_validation".equals(cases.get(1).id())
                || !cases.get(1).verboseValidation()
                || cases.stream().filter(testCase -> testCase.samples() == SMART_PERFORMANCE_SAMPLES).count() != 3
                || cases.stream().filter(testCase -> testCase.benchmarkMode() == BenchmarkMode.CACHE_REUSE).count() != 2) {
            throw new IllegalStateException("Smart spot-test suite definition lost warmup, validation, performance, or cache coverage");
        }
    }

    static void validateParallelSuiteDefinition() {
        List<SuiteCase> cases = parallelCases();
        if (cases.size() != PARALLEL_SOURCE_COUNTS.length * PARALLEL_CYCLES) {
            throw new IllegalStateException("Parallel spot-test suite lost a source-count case");
        }
        for (int index = 0; index < cases.size(); index++) {
            SuiteCase testCase = cases.get(index);
            int expectedSources = PARALLEL_SOURCE_COUNTS[index % PARALLEL_SOURCE_COUNTS.length];
            int expectedCycle = index / PARALLEL_SOURCE_COUNTS.length;
            if (testCase.sourceCount() != expectedSources
                    || testCase.repeatIndex() != expectedCycle
                    || testCase.samples() != PARALLEL_BENCHMARK_SAMPLES
                    || testCase.benchmarkMode() != BenchmarkMode.FULL_REBUILD
                    || testCase.appearanceMutation() != AppearanceMutation.NONE
                    || !testCase.sharedParallelArena()) {
                throw new IllegalStateException("Parallel spot-test cases must be ten 1..9 full-rebuild cycles");
            }
        }
    }

    static void validateLoadVariants() {
        for (SpotTestMode mode : SpotTestMode.values()) {
            List<SuiteCase> stressCases = suiteCases(mode, SpotTestLoad.STRESS);
            List<SuiteCase> lightweightCases = suiteCases(mode, SpotTestLoad.LIGHTWEIGHT);
            if (stressCases.size() != lightweightCases.size()) {
                throw new IllegalStateException("Spot-test load changed the selected mode's case count");
            }
            for (int index = 0; index < stressCases.size(); index++) {
                SuiteCase stress = stressCases.get(index);
                SuiteCase lightweight = lightweightCases.get(index);
                if (!stress.id().equals(lightweight.id())
                        || stress.samples() != lightweight.samples()
                        || stress.repeatIndex() != lightweight.repeatIndex()
                        || stress.recordSeed() != lightweight.recordSeed()
                        || stress.sourceCount() != lightweight.sourceCount()) {
                    throw new IllegalStateException("Spot-test load changed mode identity, samples, or repeats");
                }
                boolean keepsPartialGeometry = lightweight.verboseValidation()
                        || "partial_geometry".equals(lightweight.id());
                SpotProjectionTestScene.ObstacleProfile expected = keepsPartialGeometry
                        ? SpotProjectionTestScene.ObstacleProfile.PARTIAL_HEAVY
                        : SpotProjectionTestScene.ObstacleProfile.FULL_BLOCKS_ONLY;
                if (lightweight.obstacleProfile() != expected
                        || (!keepsPartialGeometry && lightweight.fixtures())) {
                    throw new IllegalStateException("Lightweight load did not preserve only required partial geometry");
                }
                if (stress.obstacleProfile() != SpotProjectionTestScene.ObstacleProfile.PARTIAL_HEAVY) {
                    throw new IllegalStateException("Stress load lost the partial-heavy obstacle profile");
                }
            }
        }
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
                    || testCase.sourceCount() != 1
                    || testCase.fixtures()
                    || testCase.repeatIndex() != index) {
                throw new IllegalStateException("Random stress cases must be anonymous, pure-random single samples");
            }
        }
        if (RANDOM_STRESS_CASES_PER_TICK != 1) {
            throw new IllegalStateException("Random stress must advance exactly one case per server tick");
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
            boolean recordSeed,
            SpotProjectionTestScene.ObstacleProfile obstacleProfile,
            int sourceCount
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
                    true,
                    SpotProjectionTestScene.ObstacleProfile.PARTIAL_HEAVY,
                    1
            );
        }

        private SuiteCase(
                String id,
                long seed,
                double occupancy,
                boolean fixtures,
                int divergenceMilli,
                int samples,
                boolean verboseValidation,
                BenchmarkMode benchmarkMode,
                AppearanceMutation appearanceMutation,
                SpotProjectionTestScene.ObstacleProfile obstacleProfile
        ) {
            this(
                    id, seed, occupancy, fixtures, divergenceMilli, samples,
                    verboseValidation, benchmarkMode, appearanceMutation,
                    null, -1, true, obstacleProfile, 1
            );
        }

        private SuiteCase(
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
            this(
                    id, seed, occupancy, fixtures, divergenceMilli, samples,
                    verboseValidation, benchmarkMode, appearanceMutation,
                    direction, repeatIndex, recordSeed,
                    SpotProjectionTestScene.ObstacleProfile.PARTIAL_HEAVY,
                    1
            );
        }

        private SuiteCase {
            occupancy = Math.max(0.0D, Math.min(1.0D, occupancy));
            divergenceMilli = Math.max(0, divergenceMilli);
            samples = Math.max(1, Math.min(MAX_BENCHMARK_SAMPLES, samples));
            benchmarkMode = benchmarkMode == null ? BenchmarkMode.FULL_REBUILD : benchmarkMode;
            appearanceMutation = appearanceMutation == null ? AppearanceMutation.NONE : appearanceMutation;
            obstacleProfile = obstacleProfile == null
                    ? SpotProjectionTestScene.ObstacleProfile.PARTIAL_HEAVY
                    : obstacleProfile;
            sourceCount = Math.max(1, Math.min(9, sourceCount));
            if (direction != null && !direction.getAxis().isHorizontal()) {
                throw new IllegalArgumentException("Spot test direction must be horizontal");
            }
        }

        private SuiteCase lightweightVariant() {
            if (verboseValidation || "partial_geometry".equals(id)) {
                return this;
            }
            return new SuiteCase(
                    id,
                    seed,
                    occupancy,
                    false,
                    divergenceMilli,
                    samples,
                    false,
                    benchmarkMode,
                    appearanceMutation,
                    direction,
                    repeatIndex,
                    recordSeed,
                    SpotProjectionTestScene.ObstacleProfile.FULL_BLOCKS_ONLY,
                    sourceCount
            );
        }

        private SuiteCase withSourceCount(int count) {
            return new SuiteCase(
                    id,
                    seed,
                    occupancy,
                    fixtures,
                    divergenceMilli,
                    samples,
                    verboseValidation,
                    benchmarkMode,
                    appearanceMutation,
                    direction,
                    repeatIndex,
                    recordSeed,
                    obstacleProfile,
                    count
            );
        }

        private SuiteCase withSourceCountAndRepeat(int count, int cycleIndex) {
            return new SuiteCase(
                    id,
                    seed,
                    occupancy,
                    fixtures,
                    divergenceMilli,
                    samples,
                    verboseValidation,
                    benchmarkMode,
                    appearanceMutation,
                    direction,
                    cycleIndex,
                    recordSeed,
                    obstacleProfile,
                    count
            );
        }

        private boolean sharedParallelArena() {
            return id.startsWith("parallel_sources_");
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

    private record GeneratedTest(
            ResourceKey<Level> dimension,
            SpotTestLayout arenaLayout,
            List<SpotTestLayout> layouts,
            SuiteCase testCase
    ) {
        private GeneratedTest(ResourceKey<Level> dimension, SpotTestLayout layout, long seed) {
            this(dimension, layout, List.of(layout), SuiteCase.manual(seed));
        }

        private GeneratedTest {
            if (arenaLayout == null) {
                throw new IllegalArgumentException("Generated spot test requires an arena layout");
            }
            layouts = List.copyOf(layouts);
            if (layouts.isEmpty()) {
                throw new IllegalArgumentException("Generated spot test requires at least one source");
            }
        }

        private SpotTestLayout layout() {
            return layouts.get(0);
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

    private record MultiSourceFingerprint(
            int sourceCount,
            boolean complete,
            long coverageSignature,
            long fragmentationSignature
    ) {
        private static final long OFFSET_BASIS = 0xcbf29ce484222325L;
        private static final long PRIME = 0x100000001b3L;

        private static MultiSourceFingerprint from(List<OutputFingerprint> fingerprints) {
            boolean complete = !fingerprints.isEmpty();
            long coverage = mix(OFFSET_BASIS, fingerprints.size());
            long fragmentation = mix(OFFSET_BASIS, fingerprints.size());
            for (int index = 0; index < fingerprints.size(); index++) {
                OutputFingerprint fingerprint = fingerprints.get(index);
                complete &= !fingerprint.surfaces().isEmpty();
                coverage = mix(mix(coverage, index), fingerprint.coverageSignature());
                fragmentation = mix(
                        mix(fragmentation, index), fingerprint.fragmentationSignature()
                );
            }
            return new MultiSourceFingerprint(
                    fingerprints.size(), complete, coverage, fragmentation
            );
        }

        private static long mix(long hash, long value) {
            for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
                hash ^= (value >>> shift) & 0xffL;
                hash *= PRIME;
            }
            return hash;
        }
    }

    private record MultiSourceMetrics(
            int sourceCount,
            int jobSamples,
            int fullRebuildSamples,
            int suffixRebuildSamples,
            int appearanceOnlySamples,
            double jobElapsedAverageUs,
            double slowestSourceP50Us,
            double slowestSourceP95Us,
            double totalSpotsAverage,
            long completionTickSpread
    ) {
        private static MultiSourceMetrics from(List<SpotProjectionPerformanceTracker.Report> reports) {
            int jobs = 0;
            int fullRebuilds = 0;
            int suffixRebuilds = 0;
            int appearanceOnly = 0;
            double weightedElapsedUs = 0.0D;
            double slowestP50Us = 0.0D;
            double slowestP95Us = 0.0D;
            double totalSpots = 0.0D;
            long minTick = Long.MAX_VALUE;
            long maxTick = Long.MIN_VALUE;
            for (SpotProjectionPerformanceTracker.Report report : reports) {
                jobs += report.samples();
                fullRebuilds += report.fullRebuildSamples();
                suffixRebuilds += report.suffixRebuildSamples();
                appearanceOnly += report.appearanceOnlySamples();
                weightedElapsedUs += report.elapsedAverageUs() * report.samples();
                slowestP50Us = Math.max(slowestP50Us, report.elapsedP50Us());
                slowestP95Us = Math.max(slowestP95Us, report.elapsedP95Us());
                totalSpots += report.spotsAverage();
                if (!report.empty()) {
                    minTick = Math.min(minTick, report.latestTick());
                    maxTick = Math.max(maxTick, report.latestTick());
                }
            }
            return new MultiSourceMetrics(
                    reports.size(),
                    jobs,
                    fullRebuilds,
                    suffixRebuilds,
                    appearanceOnly,
                    jobs == 0 ? 0.0D : weightedElapsedUs / jobs,
                    slowestP50Us,
                    slowestP95Us,
                    totalSpots,
                    minTick == Long.MAX_VALUE ? 0L : Math.max(0L, maxTick - minTick)
            );
        }
    }

    private record ParallelCaseResult(
            int cycleIndex,
            int sourceCount,
            double workerAverageUs,
            double responseUs,
            int maxInFlight,
            int maxQueueDepth,
            int dispatchWaves,
            int maxDispatchWidth,
            long commitTickSpread,
            long submitRejected,
            long commitBudgetDeferred,
            long coverageSignature,
            long fragmentationSignature
    ) {
    }

    private record ParallelCountSummary(
            int sourceCount,
            int steadyCycles,
            double workerP50Us,
            double workerP95Us,
            double responseP50Us,
            double responseP95Us,
            double stabilizingWorkerMedianUs,
            double steadyWorkerMedianUs,
            double warmupWorkerDriftRatio,
            double stabilizingResponseMedianUs,
            double steadyResponseMedianUs,
            double warmupDriftRatio,
            boolean warmupUnstable,
            int maxInFlight,
            int maxQueueDepth,
            double averageDispatchWidth,
            int maxDispatchWidth,
            long maxCommitTickSpread,
            long submitRejected,
            long commitBudgetDeferred
    ) {
    }

    private record ParallelSuiteSummary(
            int steadyCases,
            double workerP50Us,
            double workerP95Us,
            double responseP50Us,
            double responseP95Us,
            int maxInFlight,
            int maxQueueDepth,
            double averageDispatchWidth,
            int maxDispatchWidth,
            boolean warmupUnstable,
            int fingerprintMismatches,
            long submitRejected,
            long commitBudgetDeferred,
            long aggregateCoverageSignature,
            long aggregateFragmentationSignature,
            List<ParallelCountSummary> bySourceCount
    ) {
        private ParallelSuiteSummary {
            bySourceCount = List.copyOf(bySourceCount);
        }
    }

    private record SmartCaseResult(
            SpotProjectionPerformanceTracker.Report report,
            RequestLatencyReport latency,
            long structuralChecks
    ) {
    }

    private record SmartBaselineKey(UUID owner, SpotTestLoad load) {
    }

    private record SmartHotspot(String id, double averageUs, double sharePercent) {
        private String translationKey() {
            return "item.spectralization.spot_test.hotspot." + id;
        }
    }

    private record SmartSuiteSummary(
            SmartCaseResult validation,
            SmartCaseResult sparse,
            SmartCaseResult mixed,
            SmartCaseResult dense,
            SmartCaseResult cachedPower,
            SmartCaseResult cachedColor
    ) {
        private static SmartSuiteSummary from(Map<String, SmartCaseResult> results) {
            return new SmartSuiteSummary(
                    results.get("smart_validation"),
                    results.get("performance_sparse"),
                    results.get("performance_mixed"),
                    results.get("performance_dense"),
                    results.get("performance_cached_power"),
                    results.get("performance_cached_color")
            );
        }

        private boolean complete() {
            return validation != null
                    && sparse != null
                    && mixed != null
                    && dense != null
                    && cachedPower != null
                    && cachedColor != null;
        }

        private SmartHotspot hotspot() {
            if (dense == null) {
                return new SmartHotspot("unavailable", 0.0D, 0.0D);
            }
            SpotProjectionPerformanceTracker.Report report = dense.report();
            String id = "side";
            double value = report.sideScanAverageUs();
            if (report.frontPassAverageUs() > value) {
                id = "front";
                value = report.frontPassAverageUs();
            }
            if (report.remainingAverageUs() > value) {
                id = "remaining";
                value = report.remainingAverageUs();
            }
            if (report.projectionResidualAverageUs() > value) {
                id = "residual";
                value = report.projectionResidualAverageUs();
            }
            double share = report.elapsedAverageUs() <= 0.0D
                    ? 0.0D
                    : value * 100.0D / report.elapsedAverageUs();
            return new SmartHotspot(id, value, share);
        }

        private double denseTailRatio() {
            if (dense == null || dense.report().elapsedP50Us() <= 0.0D) {
                return 0.0D;
            }
            return dense.report().elapsedP95Us() / dense.report().elapsedP50Us();
        }

        private String stabilityTranslationKey() {
            double ratio = denseTailRatio();
            String suffix = ratio <= 1.35D ? "stable" : (ratio <= 1.60D ? "variable" : "noisy");
            return "item.spectralization.spot_test.stability." + suffix;
        }
    }

    private record RandomStressSummary(
            int cases,
            double elapsedAverageUs,
            double elapsedP50Us,
            double elapsedP95Us,
            double responseAverageUs,
            double responseP95Us,
            double spotsAverage,
            double wallElapsedSeconds,
            double casesPerSecond,
            long completionTickSpan,
            long missedCompletionTicks
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
        private final SpotTestLoad load;
        private final List<SuiteCase> cases;
        private final DebugConfigSnapshot previousDebugConfig;
        private final List<DirectionMatrixResult> directionMatrixResults = new ArrayList<>();
        private final List<Double> randomStressElapsedUs = new ArrayList<>();
        private final List<Double> randomStressResponseUs = new ArrayList<>();
        private final List<Double> randomStressSpots = new ArrayList<>();
        private final List<ParallelCaseResult> parallelResults = new ArrayList<>();
        private final Map<String, SmartCaseResult> smartResults = new HashMap<>();
        private final long startedAtNanos = System.nanoTime();
        private int caseIndex;
        private int passedCases;
        private int sceneSignatureMismatches;
        private int outputCoverageMismatches;
        private int outputFragmentationMismatches;
        private int workloadMismatches;
        private long currentSceneSignature;
        private long randomStressFirstCompletionTick = Long.MIN_VALUE;
        private long randomStressLastCompletionTick = Long.MIN_VALUE;
        private long randomStressLastCompletionNanos;

        private ItemSuiteRun(
                UUID runId,
                UUID owner,
                ResourceKey<Level> dimension,
                SpotTestLayout layout,
                SpotTestMode mode,
                SpotTestLoad load,
                List<SuiteCase> cases,
                DebugConfigSnapshot previousDebugConfig
        ) {
            this.runId = runId;
            this.owner = owner;
            this.dimension = dimension;
            this.layout = layout;
            this.mode = mode;
            this.load = load;
            this.cases = cases;
            this.previousDebugConfig = previousDebugConfig;
        }

        private SuiteCase currentCase() {
            return cases.get(caseIndex);
        }

        private SpotTestLayout currentLayout() {
            return layout.withDirection(currentCase().directionOr(layout.direction()));
        }

        private List<SpotTestLayout> currentLayouts() {
            return currentLayout().parallelSources(currentCase().sourceCount());
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
                RequestLatencyReport latency,
                long completionTick
        ) {
            if (mode != SpotTestMode.RANDOM_STRESS) {
                return;
            }
            randomStressElapsedUs.add(report.elapsedAverageUs());
            randomStressResponseUs.add(latency.averageUs());
            randomStressSpots.add(report.spotsAverage());
            if (randomStressFirstCompletionTick == Long.MIN_VALUE) {
                randomStressFirstCompletionTick = completionTick;
            }
            randomStressLastCompletionTick = completionTick;
            randomStressLastCompletionNanos = System.nanoTime();
        }

        private boolean recordParallelResult(
                SuiteCase testCase,
                MultiSourceMetrics metrics,
                RequestLatencyReport latency,
                BenchmarkObservation observation,
                MultiSourceFingerprint fingerprint
        ) {
            if (mode != SpotTestMode.PARALLEL) {
                return true;
            }
            ParallelCaseResult baseline = parallelResults.stream()
                    .filter(result -> result.sourceCount() == testCase.sourceCount())
                    .findFirst()
                    .orElse(null);
            boolean fingerprintMatches = baseline == null
                    || (baseline.coverageSignature() == fingerprint.coverageSignature()
                    && baseline.fragmentationSignature() == fingerprint.fragmentationSignature());
            parallelResults.add(new ParallelCaseResult(
                    testCase.repeatIndex() + 1,
                    testCase.sourceCount(),
                    metrics.jobElapsedAverageUs(),
                    latency.averageUs(),
                    observation.maxProjectionInFlight(),
                    observation.maxProjectionQueueDepth(),
                    observation.dispatchWaves(),
                    observation.maxDispatchWidth(),
                    observation.commitTickSpread(),
                    observation.submitRejected(),
                    observation.commitBudgetDeferred(),
                    fingerprint.coverageSignature(),
                    fingerprint.fragmentationSignature()
            ));
            return fingerprintMatches;
        }

        private ParallelSuiteSummary parallelSummary() {
            List<ParallelCountSummary> counts = new ArrayList<>(PARALLEL_SOURCE_COUNTS.length);
            List<Double> steadyWorker = new ArrayList<>();
            List<Double> steadyResponse = new ArrayList<>();
            boolean warmupUnstable = false;
            int maxInFlight = 0;
            int maxQueue = 0;
            int steadyDispatchWaves = 0;
            int steadyJobs = 0;
            int maxDispatchWidth = 0;
            int fingerprintMismatches = 0;
            long submitRejected = 0L;
            long commitBudgetDeferred = 0L;
            long coverageSignature = 0xcbf29ce484222325L;
            long fragmentationSignature = 0xcbf29ce484222325L;
            for (int sourceCount : PARALLEL_SOURCE_COUNTS) {
                List<ParallelCaseResult> sourceResults = parallelResults.stream()
                        .filter(result -> result.sourceCount() == sourceCount)
                        .toList();
                List<Double> stabilizingResponse = sourceResults.stream()
                        .filter(result -> result.cycleIndex() >= 4 && result.cycleIndex() <= 5)
                        .map(ParallelCaseResult::responseUs)
                        .toList();
                List<Double> stabilizingWorker = sourceResults.stream()
                        .filter(result -> result.cycleIndex() >= 4 && result.cycleIndex() <= 5)
                        .map(ParallelCaseResult::workerAverageUs)
                        .toList();
                List<ParallelCaseResult> steady = sourceResults.stream()
                        .filter(result -> result.cycleIndex() >= 6)
                        .toList();
                List<Double> workerValues = steady.stream().map(ParallelCaseResult::workerAverageUs).toList();
                List<Double> responseValues = steady.stream().map(ParallelCaseResult::responseUs).toList();
                steadyWorker.addAll(workerValues);
                steadyResponse.addAll(responseValues);
                double stabilizingMedian = median(stabilizingResponse);
                double steadyMedian = median(responseValues);
                double stabilizingWorkerMedian = median(stabilizingWorker);
                double steadyWorkerMedian = median(workerValues);
                double drift = steadyMedian <= 0.0D
                        ? 0.0D
                        : Math.abs(stabilizingMedian - steadyMedian) / steadyMedian;
                double workerDrift = steadyWorkerMedian <= 0.0D
                        ? 0.0D
                        : Math.abs(stabilizingWorkerMedian - steadyWorkerMedian) / steadyWorkerMedian;
                boolean sourceWarmupUnstable = stabilizingResponse.size() == 2
                        && stabilizingWorker.size() == 2
                        && responseValues.size() == 5
                        && workerValues.size() == 5
                        && warmupDriftUnstable(drift, workerDrift);
                warmupUnstable |= sourceWarmupUnstable;
                int sourceMaxInFlight = sourceResults.stream()
                        .mapToInt(ParallelCaseResult::maxInFlight).max().orElse(0);
                int sourceMaxQueue = sourceResults.stream()
                        .mapToInt(ParallelCaseResult::maxQueueDepth).max().orElse(0);
                long sourceMaxCommitSpread = sourceResults.stream()
                        .mapToLong(ParallelCaseResult::commitTickSpread).max().orElse(0L);
                int sourceDispatchWaves = steady.stream()
                        .mapToInt(ParallelCaseResult::dispatchWaves).sum();
                long sourceSubmitRejected = sourceResults.stream()
                        .mapToLong(ParallelCaseResult::submitRejected).sum();
                long sourceCommitBudgetDeferred = sourceResults.stream()
                        .mapToLong(ParallelCaseResult::commitBudgetDeferred).sum();
                int sourceMaxDispatchWidth = steady.stream()
                        .mapToInt(ParallelCaseResult::maxDispatchWidth).max().orElse(0);
                steadyDispatchWaves += sourceDispatchWaves;
                steadyJobs += sourceCount * steady.size();
                maxDispatchWidth = Math.max(maxDispatchWidth, sourceMaxDispatchWidth);
                maxInFlight = Math.max(maxInFlight, sourceMaxInFlight);
                maxQueue = Math.max(maxQueue, sourceMaxQueue);
                long baselineCoverage = sourceResults.isEmpty() ? 0L : sourceResults.getFirst().coverageSignature();
                long baselineFragmentation = sourceResults.isEmpty()
                        ? 0L : sourceResults.getFirst().fragmentationSignature();
                fingerprintMismatches += (int) sourceResults.stream()
                        .filter(result -> result.coverageSignature() != baselineCoverage
                                || result.fragmentationSignature() != baselineFragmentation)
                        .count();
                submitRejected += sourceResults.stream()
                        .mapToLong(ParallelCaseResult::submitRejected).sum();
                commitBudgetDeferred += sourceResults.stream()
                        .mapToLong(ParallelCaseResult::commitBudgetDeferred).sum();
                for (ParallelCaseResult result : sourceResults) {
                    coverageSignature = Long.rotateLeft(coverageSignature, 7) ^ result.coverageSignature();
                    fragmentationSignature = Long.rotateLeft(fragmentationSignature, 7)
                            ^ result.fragmentationSignature();
                }
                counts.add(new ParallelCountSummary(
                        sourceCount,
                        steady.size(),
                        percentile(workerValues, 0.50D),
                        percentile(workerValues, 0.95D),
                        percentile(responseValues, 0.50D),
                        percentile(responseValues, 0.95D),
                        stabilizingWorkerMedian,
                        steadyWorkerMedian,
                        workerDrift,
                        stabilizingMedian,
                        steadyMedian,
                        drift,
                        sourceWarmupUnstable,
                        sourceMaxInFlight,
                        sourceMaxQueue,
                        sourceDispatchWaves == 0
                                ? 0.0D : sourceCount * steady.size() / (double) sourceDispatchWaves,
                        sourceMaxDispatchWidth,
                        sourceMaxCommitSpread,
                        sourceSubmitRejected,
                        sourceCommitBudgetDeferred
                ));
            }
            return new ParallelSuiteSummary(
                    steadyResponse.size(),
                    percentile(steadyWorker, 0.50D),
                    percentile(steadyWorker, 0.95D),
                    percentile(steadyResponse, 0.50D),
                    percentile(steadyResponse, 0.95D),
                    maxInFlight,
                    maxQueue,
                    steadyDispatchWaves == 0
                            ? 0.0D : steadyJobs / (double) steadyDispatchWaves,
                    maxDispatchWidth,
                    warmupUnstable,
                    fingerprintMismatches,
                    submitRejected,
                    commitBudgetDeferred,
                    coverageSignature,
                    fragmentationSignature,
                    counts
            );
        }

        private void recordSmartResult(
                SuiteCase testCase,
                SpotProjectionPerformanceTracker.Report report,
                RequestLatencyReport latency,
                long structuralChecks
        ) {
            if (mode != SpotTestMode.SMART || "smart_warmup".equals(testCase.id())) {
                return;
            }
            smartResults.put(testCase.id(), new SmartCaseResult(report, latency, structuralChecks));
        }

        private SmartSuiteSummary smartSummary() {
            return SmartSuiteSummary.from(smartResults);
        }

        private RandomStressSummary randomStressSummary() {
            int completedCases = randomStressElapsedUs.size();
            long completionTickSpan = completedCases <= 1
                    ? 0L
                    : Math.max(0L, randomStressLastCompletionTick - randomStressFirstCompletionTick);
            long expectedTickSpan = Math.max(0, completedCases - 1);
            double wallElapsedSeconds = randomStressLastCompletionNanos <= startedAtNanos
                    ? 0.0D
                    : (randomStressLastCompletionNanos - startedAtNanos) / 1_000_000_000.0D;
            return new RandomStressSummary(
                    completedCases,
                    average(randomStressElapsedUs),
                    percentile(randomStressElapsedUs, 0.50D),
                    percentile(randomStressElapsedUs, 0.95D),
                    average(randomStressResponseUs),
                    percentile(randomStressResponseUs, 0.95D),
                    average(randomStressSpots),
                    wallElapsedSeconds,
                    wallElapsedSeconds <= 0.0D ? 0.0D : completedCases / wallElapsedSeconds,
                    completionTickSpan,
                    Math.max(0L, completionTickSpan - expectedTickSpan)
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

        private static double median(List<Double> values) {
            if (values.isEmpty()) {
                return 0.0D;
            }
            List<Double> sorted = new ArrayList<>(values);
            sorted.sort(Double::compareTo);
            int middle = sorted.size() / 2;
            if ((sorted.size() & 1) != 0) {
                return sorted.get(middle);
            }
            return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0D;
        }
    }

    static double verifyMedian(List<Double> values) {
        return ItemSuiteRun.median(values);
    }

    static boolean warmupDriftUnstable(double responseDrift, double workerDrift) {
        return responseDrift > 0.10D && workerDrift > 0.10D;
    }

    private static final class BenchmarkRun {
        private final GeneratedTest test;
        private final int targetSamples;
        private int observedSamples;
        private long nextRetryTick;
        private long deadlineTick;
        private final UUID suiteRunId;
        private final String suiteCaseId;
        private final BenchmarkMode benchmarkMode;
        private int warmupPhase;
        private boolean forceInitialGeometryWarmup;
        private int appearanceStep;
        private final int quietTicksRequired;
        private int quietTicksObserved;
        private boolean awaitingProjectionStart;
        private int maxProjectionInFlight;
        private int maxProjectionQueueDepth;
        private long submitTickSpread;
        private long commitTickSpread;
        private int dispatchWaves;
        private int maxDispatchWidth;
        private double averageDispatchWidth;
        private double totalWorkerUs;
        private double totalCommitUs;
        private double totalAssemblyUs;
        private double totalDiagnosticsUs;
        private double totalDependencyIndexUs;
        private double totalOwnerPublishUs;
        private int dependencyIndexReused;
        private int ownerPublishReused;
        private long submitRejectedBaseline;
        private long commitBudgetDeferredBaseline;
        private long submitRejected;
        private long commitBudgetDeferred;
        private final List<Long> responseLatenciesNanos = new ArrayList<>();
        private long requestStartNanos;
        private long lastTickObservationNanos;
        private long maxTickIntervalNanos;

        private BenchmarkRun(GeneratedTest test, int targetSamples, long nextRetryTick, long deadlineTick) {
            this(
                    test, targetSamples, nextRetryTick, deadlineTick,
                    null, "manual", BenchmarkMode.FULL_REBUILD, 0
            );
        }

        private BenchmarkRun(
                GeneratedTest test,
                int targetSamples,
                long nextRetryTick,
                long deadlineTick,
                UUID suiteRunId,
                String suiteCaseId,
                BenchmarkMode benchmarkMode,
                int quietTicksRequired
        ) {
            this.test = test;
            this.targetSamples = targetSamples;
            this.nextRetryTick = nextRetryTick;
            this.deadlineTick = deadlineTick;
            this.suiteRunId = suiteRunId;
            this.suiteCaseId = suiteCaseId;
            this.benchmarkMode = benchmarkMode == null ? BenchmarkMode.FULL_REBUILD : benchmarkMode;
            this.quietTicksRequired = Math.max(0, quietTicksRequired);
            this.warmupPhase = this.benchmarkMode == BenchmarkMode.CACHE_REUSE ? 2 : 0;
            this.forceInitialGeometryWarmup = this.benchmarkMode == BenchmarkMode.CACHE_REUSE;
        }

        private void startLatency(ServerLevel level) {
            requestStartNanos = System.nanoTime();
            lastTickObservationNanos = requestStartNanos;
            maxTickIntervalNanos = 0L;
            maxProjectionInFlight = 0;
            maxProjectionQueueDepth = 0;
            submitTickSpread = 0L;
            commitTickSpread = 0L;
            dispatchWaves = 0;
            maxDispatchWidth = 0;
            averageDispatchWidth = 0.0D;
            totalWorkerUs = 0.0D;
            totalCommitUs = 0.0D;
            totalAssemblyUs = 0.0D;
            totalDiagnosticsUs = 0.0D;
            totalDependencyIndexUs = 0.0D;
            totalOwnerPublishUs = 0.0D;
            dependencyIndexReused = 0;
            ownerPublishReused = 0;
            OpticalTraceCache.ProjectionWorkState workState = OpticalTraceCache.projectionWorkState(level);
            submitRejectedBaseline = workState.submitRejected();
            commitBudgetDeferredBaseline = workState.commitBudgetDeferred();
            submitRejected = 0L;
            commitBudgetDeferred = 0L;
        }

        private void observeTick(ServerLevel level) {
            long now = System.nanoTime();
            if (lastTickObservationNanos > 0L) {
                maxTickIntervalNanos = Math.max(maxTickIntervalNanos, now - lastTickObservationNanos);
            }
            lastTickObservationNanos = now;
            OpticalTraceCache.ProjectionWorkState workState = OpticalTraceCache.projectionWorkState(level);
            maxProjectionInFlight = Math.max(maxProjectionInFlight, workState.executorInFlight());
            maxProjectionQueueDepth = Math.max(maxProjectionQueueDepth, workState.executorQueueDepth());
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

        private void captureProjectionBatchObservation(ServerLevel level) {
            OpticalTraceCache.ProjectionBatchObservation observation =
                    OpticalTraceCache.projectionBatchObservation(level, projectionSources(test));
            maxProjectionInFlight = Math.max(maxProjectionInFlight, observation.maxInFlight());
            maxProjectionQueueDepth = Math.max(maxProjectionQueueDepth, observation.maxQueueDepth());
            submitTickSpread = observation.submitTickSpread();
            commitTickSpread = observation.commitTickSpread();
            dispatchWaves = observation.dispatchWaves();
            maxDispatchWidth = observation.maxDispatchWidth();
            averageDispatchWidth = observation.averageDispatchWidth();
            totalWorkerUs = observation.totalWorkerUs();
            totalCommitUs = observation.totalCommitUs();
            totalAssemblyUs = observation.totalAssemblyUs();
            totalDiagnosticsUs = observation.totalDiagnosticsUs();
            totalDependencyIndexUs = observation.totalDependencyIndexUs();
            totalOwnerPublishUs = observation.totalOwnerPublishUs();
            dependencyIndexReused = observation.dependencyIndexReused();
            ownerPublishReused = observation.ownerPublishReused();
            OpticalTraceCache.ProjectionWorkState workState = OpticalTraceCache.projectionWorkState(level);
            submitRejected = Math.max(0L, workState.submitRejected() - submitRejectedBaseline);
            commitBudgetDeferred = Math.max(
                    0L, workState.commitBudgetDeferred() - commitBudgetDeferredBaseline
            );
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
            int p50Index = Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.50D) - 1));
            int p95Index = Math.max(0, Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * 0.95D) - 1));
            return new RequestLatencyReport(
                    sorted.length,
                    (sum / (double) sorted.length) / 1_000.0D,
                    sorted[p50Index] / 1_000.0D,
                    sorted[p95Index] / 1_000.0D,
                    sorted[sorted.length - 1] / 1_000.0D,
                    Math.max(0L, maxTickIntervalNanos - 50_000_000L) / 1_000.0D
            );
        }

        private BenchmarkObservation observation() {
            return new BenchmarkObservation(
                    quietTicksRequired,
                    quietTicksObserved,
                    maxProjectionInFlight,
                    maxProjectionQueueDepth,
                    submitTickSpread,
                    commitTickSpread,
                    dispatchWaves,
                    maxDispatchWidth,
                    averageDispatchWidth,
                    totalWorkerUs,
                    totalCommitUs,
                    totalAssemblyUs,
                    totalDiagnosticsUs,
                    totalDependencyIndexUs,
                    totalOwnerPublishUs,
                    dependencyIndexReused,
                    ownerPublishReused,
                    submitRejected,
                    commitBudgetDeferred
            );
        }
    }

    private record BenchmarkObservation(
            int quietTicksRequired,
            int quietTicksObserved,
            int maxProjectionInFlight,
            int maxProjectionQueueDepth,
            long submitTickSpread,
            long commitTickSpread,
            int dispatchWaves,
            int maxDispatchWidth,
            double averageDispatchWidth,
            double totalWorkerUs,
            double totalCommitUs,
            double totalAssemblyUs,
            double totalDiagnosticsUs,
            double totalDependencyIndexUs,
            double totalOwnerPublishUs,
            int dependencyIndexReused,
            int ownerPublishReused,
            long submitRejected,
            long commitBudgetDeferred
    ) {
        private static final BenchmarkObservation EMPTY = new BenchmarkObservation(
                0, 0, 0, 0, 0L, 0L, 0, 0,
                0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0, 0, 0L, 0L
        );
    }

    private record RequestLatencyReport(
            int samples,
            double averageUs,
            double p50Us,
            double p95Us,
            double maxUs,
            double maxTickStallUs
    ) {
        private static final RequestLatencyReport EMPTY = new RequestLatencyReport(
                0, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D
        );
    }

    private record SuiteBenchmarkCompletion(
            UUID owner,
            UUID runId,
            SpotProjectionPerformanceTracker.Report report,
            List<SpotProjectionPerformanceTracker.Report> sourceReports,
            RequestLatencyReport latency,
            BenchmarkObservation benchmark,
            boolean completed,
            String reason
    ) {
        private SuiteBenchmarkCompletion(
                UUID owner,
                UUID runId,
                SpotProjectionPerformanceTracker.Report report,
                List<SpotProjectionPerformanceTracker.Report> sourceReports,
                RequestLatencyReport latency,
                boolean completed,
                String reason
        ) {
            this(
                    owner, runId, report, sourceReports, latency,
                    BenchmarkObservation.EMPTY, completed, reason
            );
        }

        private SuiteBenchmarkCompletion(
                UUID owner,
                UUID runId,
                SpotProjectionPerformanceTracker.Report report,
                RequestLatencyReport latency,
                boolean completed,
                String reason
        ) {
            this(
                    owner, runId, report, List.of(report), latency,
                    BenchmarkObservation.EMPTY, completed, reason
            );
        }
    }

    private SpotProjectionTestCommand() {
    }
}
