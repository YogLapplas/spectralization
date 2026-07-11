package io.github.yoglappland.spectralization.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalSpotTracker;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.compiler.CompiledSpotLayer;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPerformanceTracker;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionResult;
import io.github.yoglappland.spectralization.optics.projection.VoxelSpotProjector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

public final class SpotProjectionTestCommand {
    private static final int DEFAULT_BENCHMARK_SAMPLES = 5;
    private static final int MAX_BENCHMARK_SAMPLES = 16;
    private static final long BENCHMARK_RETRY_TICKS = 40L;
    private static final long BENCHMARK_TIMEOUT_TICKS = 400L;
    private static final int MIN_ALONG = -1;
    private static final int MAX_ALONG = 17;
    private static final int MIN_SIDE = -6;
    private static final int MAX_SIDE = 6;
    private static final int MIN_VERTICAL = -3;
    private static final int MAX_VERTICAL = 5;
    private static final int FIRST_RANDOM_DEPTH = 3;
    private static final int LAST_RANDOM_DEPTH = 13;
    private static final int SCREEN_DEPTH = 16;
    private static final int SOURCE_POWER_CENTI = 30_000;
    private static final int SOURCE_RADIUS_MILLI = 500;
    private static final int SOURCE_DIVERGENCE_MILLI = 500;
    private static final int[] CACHE_POWER_SEQUENCE_CENTI = {
            7_500, 15_000, 30_000, 45_000, 22_500
    };
    private static final int[] CACHE_COLOR_SEQUENCE_BINS = {
            SpectralColorMap.VISIBLE_RED_BIN,
            SpectralColorMap.VISIBLE_GREEN_BIN,
            SpectralColorMap.VISIBLE_BLUE_BIN,
            SpectralColorMap.VISIBLE_MAGENTA_BIN,
            SpectralColorMap.VISIBLE_CYAN_BIN
    };
    private static final CoherenceKind SOURCE_COHERENCE = CoherenceKind.INCOHERENT;
    private static final double RANDOM_OCCUPANCY = 0.17D;
    private static final Map<UUID, GeneratedTest> LAST_TEST_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, BenchmarkRun> BENCHMARKS_BY_PLAYER = new HashMap<>();
    private static ItemSuiteRun activeItemSuite;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

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
        return Component.translatable(
                "item.spectralization.spot_test.message.status",
                activeItemSuite.caseIndex + 1,
                activeItemSuite.cases.size(),
                Component.translatable(testCase.translationKey())
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

        Direction direction = player.getDirection();
        if (!direction.getAxis().isHorizontal()) {
            direction = Direction.SOUTH;
        }
        TestLayout layout = new TestLayout(
                player.blockPosition().relative(direction, 4).above(4),
                direction
        );
        if (!validateVolume(source, player.serverLevel(), layout)) {
            return 0;
        }

        GeneratedTest previous = LAST_TEST_BY_PLAYER.remove(player.getUUID());
        if (previous != null) {
            clearGeneratedTest(source, previous);
        }

        List<SuiteCase> cases = suiteCases(mode);
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
        SpectralDiagnostics.event(player.serverLevel(), "spot_projection_test", "suite_started")
                .field("run_id", run.runId)
                .field("suite", mode.serializedName())
                .field("cases", cases.size())
                .pos("source", layout.source())
                .field("direction", layout.direction())
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
        applyDebugPolicy(run, testCase.verboseValidation());
        GeneratedTest generatedTest = new GeneratedTest(
                run.dimension,
                run.layout,
                testCase
        );
        int placed = build(player.serverLevel(), generatedTest);
        LAST_TEST_BY_PLAYER.put(player.getUUID(), generatedTest);
        SpotProjectionPerformanceTracker.reset(
                player.serverLevel(),
                run.layout.source(),
                run.layout.direction()
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
        player.sendSystemMessage(Component.translatable(
                "item.spectralization.spot_test.message.case_started",
                run.caseIndex + 1,
                run.cases.size(),
                Component.translatable(testCase.translationKey()),
                Component.translatable(testCase.benchmarkDescriptionKey()),
                testCase.samples()
        ).withStyle(ChatFormatting.AQUA));
        SpectralDiagnostics.event(player.serverLevel(), "spot_projection_test", "case_started")
                .field("run_id", run.runId)
                .field("suite", run.mode.serializedName())
                .field("case_id", testCase.id())
                .field("case_index", run.caseIndex)
                .field("seed", testCase.seed())
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

    private static void applyDebugPolicy(ItemSuiteRun run, boolean verboseValidation) {
        SpectralizationConfig.setOpticalCompilerDebugLog(true);
        SpectralizationConfig.setOpticalCompilerDebugVerbose(false);
        SpectralizationConfig.setSpotColorDebug(false);
        VoxelSpotProjector.setDebugFaceCentersEnabled(false);
        if (verboseValidation) {
            VoxelSpotProjector.setTargetedValidation(
                    run.dimension,
                    run.layout.source(),
                    run.layout.direction()
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

        Direction direction = player.getDirection();
        if (!direction.getAxis().isHorizontal()) {
            direction = Direction.SOUTH;
        }

        TestLayout layout = new TestLayout(
                player.blockPosition().relative(direction, 4).above(4),
                direction
        );
        if (!validateVolume(source, player.serverLevel(), layout)) {
            return 0;
        }

        GeneratedTest previous = LAST_TEST_BY_PLAYER.remove(player.getUUID());
        BENCHMARKS_BY_PLAYER.remove(player.getUUID());
        if (previous != null) {
            clearGeneratedTest(source, previous);
        }

        GeneratedTest generatedTest = new GeneratedTest(player.serverLevel().dimension(), layout, seed);
        int placed = build(player.serverLevel(), generatedTest);
        LAST_TEST_BY_PLAYER.put(player.getUUID(), generatedTest);
        source.sendSuccess(() -> Component.literal(String.format(
                "Generated spot test seed=%d source=%s direction=%s coherence=%s divergence=%.3f placed=%d. Use /spectralization spottest rerun or clear.",
                seed,
                layout.source().toShortString(),
                layout.direction().getSerializedName(),
                SOURCE_COHERENCE.name().toLowerCase(java.util.Locale.ROOT),
                SOURCE_DIVERGENCE_MILLI / 1000.0D,
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
        if (level == null || !validateVolume(source, level, generatedTest.layout())) {
            return 0;
        }

        int placed = build(level, generatedTest);
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
        if (level == null || !validateVolume(source, level, test.layout())) {
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
                    SpotProjectionPerformanceTracker.log(level, report);
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
                        Component.translatable(run.currentCase().translationKey()),
                        completion.reason()
                ).withStyle(ChatFormatting.RED));
            }
            finishItemSuite(player, level, false, completion.reason());
            return;
        }

        SuiteCase testCase = run.currentCase();
        SpotProjectionResult.Stats stats = SpotProjectionPerformanceTracker.latestStats(
                level,
                run.layout.source(),
                run.layout.direction()
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

        SpectralDiagnostics.event(level, "spot_projection_test", "case_complete")
                .field("run_id", run.runId)
                .field("suite", run.mode.serializedName())
                .field("case_id", testCase.id())
                .field("case_index", run.caseIndex)
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
        if (run.caseIndex + 1 >= run.cases.size()) {
            finishItemSuite(player, level, true, "complete");
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
                Component.translatable(testCase.translationKey()),
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
            player.sendSystemMessage(Component.translatable(
                    passed
                            ? "item.spectralization.spot_test.message.complete"
                            : "item.spectralization.spot_test.message.failed",
                    Component.translatable(run.mode.translationKey()),
                    run.passedCases,
                    run.cases.size()
            ).withStyle(passed ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
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
        source.sendSuccess(() -> Component.literal(String.format(
                "Cleared spot test seed=%d blocks=%d",
                generatedTest.seed(),
                cleared
        )), true);
        return Math.max(1, cleared);
    }

    private static int build(ServerLevel level, GeneratedTest generatedTest) {
        TestLayout layout = generatedTest.layout();
        clearVolume(level, layout);
        Random random = new Random(generatedTest.seed());
        int randomObstacles = placeRandomObstacles(
                level,
                layout,
                random,
                generatedTest.testCase().occupancy()
        );
        int fixtures = generatedTest.testCase().fixtures()
                ? placeRegressionFixtures(level, layout)
                : 0;
        int screenBlocks = placeScreen(level, layout);
        int sourceBlocks = placeSource(level, layout, generatedTest.testCase());
        int placed = randomObstacles + fixtures + screenBlocks + sourceBlocks;
        refreshProjection(level);
        SpectralDiagnostics.event(level, "spot_projection_test", "generated")
                .pos("source", layout.source())
                .field("direction", layout.direction())
                .field("case_id", generatedTest.testCase().id())
                .field("seed", generatedTest.seed())
                .field("power", SOURCE_POWER_CENTI / (double) CreativeLightSourceBlockEntity.POWER_SCALE)
                .field("coherence", SOURCE_COHERENCE)
                .field("radius", SOURCE_RADIUS_MILLI / 1000.0D)
                .field("divergence", generatedTest.testCase().divergenceMilli() / 1000.0D)
                .field("occupancy", generatedTest.testCase().occupancy())
                .field("random_obstacles", randomObstacles)
                .field("fixtures", fixtures)
                .field("screen_blocks", screenBlocks)
                .field("placed", placed)
                .write();
        return placed;
    }

    private static int placeRandomObstacles(
            ServerLevel level,
            TestLayout layout,
            Random random,
            double occupancy
    ) {
        int placed = 0;

        for (int along = FIRST_RANDOM_DEPTH; along <= LAST_RANDOM_DEPTH; along++) {
            for (int side = -4; side <= 4; side++) {
                for (int vertical = -2; vertical <= 3; vertical++) {
                    if (random.nextDouble() >= occupancy) {
                        continue;
                    }

                    level.setBlock(layout.at(along, side, vertical), randomProjectionState(random), 3);
                    placed++;
                }
            }
        }

        return placed;
    }

    private static int placeRegressionFixtures(ServerLevel level, TestLayout layout) {
        int placed = 0;
        Direction lateral = layout.lateral();
        Direction oppositeLateral = lateral.getOpposite();

        placed += place(level, layout.at(5, -2, -1), stairState(lateral, Half.BOTTOM));
        placed += place(level, layout.at(5, -1, -1), stairState(oppositeLateral, Half.TOP));
        placed += place(level, layout.at(5, 0, -1), stairState(lateral, Half.BOTTOM));
        placed += place(level, layout.at(5, 1, -1), stairState(oppositeLateral, Half.TOP));

        placed += place(level, layout.at(8, -2, 1), slabState(SlabType.BOTTOM));
        placed += place(level, layout.at(8, -1, 1), slabState(SlabType.TOP));
        placed += place(level, layout.at(8, 1, 0), slabState(SlabType.BOTTOM));
        placed += place(level, layout.at(8, 2, 0), slabState(SlabType.TOP));

        placed += place(level, layout.at(11, -1, 0), Blocks.OAK_FENCE.defaultBlockState());
        placed += place(level, layout.at(11, 0, 0), Blocks.OAK_FENCE.defaultBlockState());
        placed += place(level, layout.at(11, 1, 0), Blocks.OAK_FENCE.defaultBlockState());
        placed += place(level, layout.at(11, 0, 1), Blocks.OAK_FENCE.defaultBlockState());

        placed += place(level, layout.at(13, -1, -1), stairState(layout.direction(), Half.BOTTOM));
        placed += place(level, layout.at(13, 0, 0), stairState(layout.direction().getOpposite(), Half.TOP));
        placed += place(level, layout.at(13, 1, 1), stairState(lateral, Half.BOTTOM));

        // Reproductions captured by boundary-missing diagnostics on 2026-07-11.
        // Keep the source-relative coordinates stable so the structured event can be compared directly.
        placed += place(level, layout.at(4, 3, 0), Blocks.OAK_FENCE.defaultBlockState());
        placed += place(level, layout.at(6, -4, 0), Blocks.NETHER_BRICK_FENCE.defaultBlockState());
        placed += place(level, layout.at(6, 4, -1), stairState(layout.direction(), Half.TOP));
        return placed;
    }

    private static int placeScreen(ServerLevel level, TestLayout layout) {
        int placed = 0;

        for (int side = -5; side <= 5; side++) {
            for (int vertical = MIN_VERTICAL; vertical <= MAX_VERTICAL; vertical++) {
                level.setBlock(
                        layout.at(SCREEN_DEPTH, side, vertical),
                        Blocks.WHITE_CONCRETE.defaultBlockState(),
                        3
                );
                placed++;
            }
        }

        return placed;
    }

    private static int placeSource(ServerLevel level, TestLayout layout, SuiteCase testCase) {
        level.setBlock(
                layout.source(),
                Spectralization.CREATIVE_LIGHT_SOURCE.get()
                        .defaultBlockState()
                        .setValue(CreativeLightSourceBlock.FACING, layout.direction()),
                3
        );

        if (level.getBlockEntity(layout.source()) instanceof CreativeLightSourceBlockEntity source) {
            ContainerData data = source.createDataAccess();
            data.set(CreativeLightSourceBlockEntity.DATA_REGION, FrequencyKey.DEBUG_VISIBLE.region().ordinal());
            data.set(CreativeLightSourceBlockEntity.DATA_BIN, FrequencyKey.DEBUG_VISIBLE.bin());
            data.set(CreativeLightSourceBlockEntity.DATA_POWER, SOURCE_POWER_CENTI);
            data.set(CreativeLightSourceBlockEntity.DATA_COHERENCE, SOURCE_COHERENCE.ordinal());
            data.set(CreativeLightSourceBlockEntity.DATA_BEAM_MODEL, BeamModel.DIVERGING.ordinal());
            data.set(CreativeLightSourceBlockEntity.DATA_RADIUS_MILLI, SOURCE_RADIUS_MILLI);
            data.set(CreativeLightSourceBlockEntity.DATA_DIVERGENCE_MILLI, testCase.divergenceMilli());
            data.set(CreativeLightSourceBlockEntity.DATA_FOCUS_DISTANCE_MILLI, 0);
            data.set(CreativeLightSourceBlockEntity.DATA_MODE_M, 0);
            data.set(CreativeLightSourceBlockEntity.DATA_MODE_N, 0);

            for (int index = CreativeLightSourceBlockEntity.DATA_SPECTRUM_START;
                 index < CreativeLightSourceBlockEntity.DATA_COUNT;
                 index++) {
                data.set(index, 0);
            }
        }

        return 1;
    }

    private static BlockState randomProjectionState(Random random) {
        int roll = random.nextInt(100);

        if (roll < 45) {
            return stairState(
                    HORIZONTAL_DIRECTIONS[random.nextInt(HORIZONTAL_DIRECTIONS.length)],
                    random.nextBoolean() ? Half.BOTTOM : Half.TOP
            );
        }

        if (roll < 72) {
            return slabState(random.nextBoolean() ? SlabType.BOTTOM : SlabType.TOP);
        }

        if (roll < 88) {
            return random.nextBoolean()
                    ? Blocks.OAK_FENCE.defaultBlockState()
                    : Blocks.NETHER_BRICK_FENCE.defaultBlockState();
        }

        return switch (random.nextInt(3)) {
            case 0 -> Blocks.STONE_BRICKS.defaultBlockState();
            case 1 -> Blocks.SMOOTH_QUARTZ.defaultBlockState();
            default -> Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        };
    }

    private static BlockState stairState(Direction facing, Half half) {
        return Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                .setValue(BlockStateProperties.HALF, half);
    }

    private static BlockState slabState(SlabType type) {
        return Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, type);
    }

    private static int place(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 3);
        return 1;
    }

    private static int clearGeneratedTest(CommandSourceStack source, GeneratedTest generatedTest) {
        ServerLevel level = source.getServer().getLevel(generatedTest.dimension());
        if (level == null) {
            return 0;
        }

        int cleared = clearVolume(level, generatedTest.layout());
        refreshProjection(level);
        SpectralDiagnostics.event(level, "spot_projection_test", "cleared")
                .pos("source", generatedTest.layout().source())
                .field("direction", generatedTest.layout().direction())
                .field("seed", generatedTest.seed())
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

    private static int clearVolume(ServerLevel level, TestLayout layout) {
        level.setBlock(layout.source(), Blocks.AIR.defaultBlockState(), 3);
        int cleared = 0;

        for (int along = MIN_ALONG; along <= MAX_ALONG; along++) {
            for (int side = MIN_SIDE; side <= MAX_SIDE; side++) {
                for (int vertical = MIN_VERTICAL; vertical <= MAX_VERTICAL; vertical++) {
                    BlockPos pos = layout.at(along, side, vertical);
                    if (!level.getBlockState(pos).isAir()) {
                        cleared++;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        return cleared;
    }

    private static boolean validateVolume(CommandSourceStack source, ServerLevel level, TestLayout layout) {
        int minY = layout.source().getY() + MIN_VERTICAL;
        int maxY = layout.source().getY() + MAX_VERTICAL;
        if (minY < level.getMinBuildHeight() || maxY >= level.getMaxBuildHeight()) {
            source.sendFailure(Component.literal("Spot test volume would leave the world's build height."));
            return false;
        }

        for (int along = MIN_ALONG; along <= MAX_ALONG; along++) {
            for (int side = MIN_SIDE; side <= MAX_SIDE; side++) {
                if (!level.isLoaded(layout.at(along, side, 0))) {
                    source.sendFailure(Component.literal(
                            "Spot test volume crosses unloaded chunks. Move closer to the intended test area."
                    ));
                    return false;
                }
            }
        }

        return true;
    }

    private static void refreshProjection(ServerLevel level) {
        OpticalSpotTracker.clear(level);
        OpticalTraceCache.clear(level);
        OpticalNetworkIndex.markDirty(level);
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
        refreshProjection(level);
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
        if (!(level.getBlockEntity(run.test.layout().source())
                instanceof CreativeLightSourceBlockEntity source)) {
            return false;
        }

        ContainerData data = source.createDataAccess();
        int step = run.appearanceStep++;
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
                int powerCenti = CACHE_POWER_SEQUENCE_CENTI[step % CACHE_POWER_SEQUENCE_CENTI.length];
                data.set(CreativeLightSourceBlockEntity.DATA_POWER, powerCenti);
                event.field("power_centi", powerCenti)
                        .field("power", powerCenti / (double) CreativeLightSourceBlockEntity.POWER_SCALE);
            }
            case COLOR_SEQUENCE -> {
                int bin = CACHE_COLOR_SEQUENCE_BINS[step % CACHE_COLOR_SEQUENCE_BINS.length];
                data.set(CreativeLightSourceBlockEntity.DATA_REGION, SpectralRegion.VISIBLE.ordinal());
                data.set(CreativeLightSourceBlockEntity.DATA_BIN, bin);
                event.field("region", SpectralRegion.VISIBLE.id())
                        .field("bin", bin);
            }
            case NONE -> {
                return true;
            }
        }
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
                SOURCE_DIVERGENCE_MILLI, 3, false, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
        );
        SuiteCase partial = new SuiteCase(
                "partial_geometry", 42L, 0.0D, true,
                SOURCE_DIVERGENCE_MILLI, 3, true, BenchmarkMode.FULL_REBUILD, AppearanceMutation.NONE
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
            case FULL_SUITE -> {
                List<SuiteCase> full = new ArrayList<>(performance.size() + 1);
                full.add(partial);
                full.addAll(performance);
                yield List.copyOf(full);
            }
        };
    }

    private record TestLayout(BlockPos source, Direction direction) {
        private Direction lateral() {
            return direction.getClockWise();
        }

        private BlockPos at(int along, int side, int vertical) {
            return source.relative(direction, along).relative(lateral(), side).above(vertical);
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
            AppearanceMutation appearanceMutation
    ) {
        private SuiteCase {
            occupancy = Math.max(0.0D, Math.min(1.0D, occupancy));
            divergenceMilli = Math.max(0, divergenceMilli);
            samples = Math.max(1, Math.min(MAX_BENCHMARK_SAMPLES, samples));
            benchmarkMode = benchmarkMode == null ? BenchmarkMode.FULL_REBUILD : benchmarkMode;
            appearanceMutation = appearanceMutation == null ? AppearanceMutation.NONE : appearanceMutation;
        }

        private static SuiteCase manual(long seed) {
            return new SuiteCase(
                    "manual",
                    seed,
                    RANDOM_OCCUPANCY,
                    true,
                    SOURCE_DIVERGENCE_MILLI,
                    DEFAULT_BENCHMARK_SAMPLES,
                    SpectralizationConfig.opticalCompilerDebugVerbose(),
                    BenchmarkMode.FULL_REBUILD,
                    AppearanceMutation.NONE
            );
        }

        private String translationKey() {
            return "item.spectralization.spot_test.case." + id;
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

    private record GeneratedTest(ResourceKey<Level> dimension, TestLayout layout, SuiteCase testCase) {
        private GeneratedTest(ResourceKey<Level> dimension, TestLayout layout, long seed) {
            this(dimension, layout, SuiteCase.manual(seed));
        }

        private long seed() {
            return testCase.seed();
        }
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
        private final TestLayout layout;
        private final SpotTestMode mode;
        private final List<SuiteCase> cases;
        private final DebugConfigSnapshot previousDebugConfig;
        private int caseIndex;
        private int passedCases;

        private ItemSuiteRun(
                UUID runId,
                UUID owner,
                ResourceKey<Level> dimension,
                TestLayout layout,
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
