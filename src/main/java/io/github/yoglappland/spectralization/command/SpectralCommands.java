package io.github.yoglappland.spectralization.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.yoglappland.spectralization.optics.OpticalPathVisualization;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class SpectralCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spectralization")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("lightpaths")
                        .executes(context -> reportState(context.getSource()))
                        .then(Commands.literal("on")
                                .executes(context -> setLightPaths(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setLightPaths(context.getSource(), false)))
                        .then(Commands.literal("toggle")
                                .executes(context -> setLightPaths(
                                        context.getSource(),
                                        !OpticalPathVisualization.isEnabled()
                                )))));
    }

    private static int setLightPaths(CommandSourceStack source, boolean enabled) {
        OpticalPathVisualization.setEnabled(enabled);
        return reportState(source);
    }

    private static int reportState(CommandSourceStack source) {
        String state = OpticalPathVisualization.isEnabled() ? "on" : "off";
        source.sendSuccess(() -> Component.literal("Spectralization light path visualization: " + state), true);
        return 1;
    }

    private SpectralCommands() {
    }
}
