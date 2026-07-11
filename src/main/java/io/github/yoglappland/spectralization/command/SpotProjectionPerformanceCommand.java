package io.github.yoglappland.spectralization.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPerformanceTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

final class SpotProjectionPerformanceCommand {
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("spotperf")
                .executes(context -> report(
                        context.getSource(),
                        SpotProjectionPerformanceTracker.DEFAULT_REPORT_SAMPLES
                ))
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
                .then(Commands.literal("reset")
                        .executes(context -> reset(context.getSource())));
    }

    private static int report(CommandSourceStack source, int count) {
        ServerLevel level = source.getLevel();
        SpotProjectionPerformanceTracker.Report report = SpotProjectionPerformanceTracker.report(level, count);

        for (String line : report.lines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }

        SpotProjectionPerformanceTracker.log(level, report);
        return Math.max(1, report.samples());
    }

    private static int reset(CommandSourceStack source) {
        SpotProjectionPerformanceTracker.reset(source.getLevel());
        source.sendSuccess(() -> Component.literal("Cleared spot projection performance samples for this dimension."), false);
        return 1;
    }

    private SpotProjectionPerformanceCommand() {
    }
}
