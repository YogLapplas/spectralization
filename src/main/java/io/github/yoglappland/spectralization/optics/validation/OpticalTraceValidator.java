package io.github.yoglappland.spectralization.optics.validation;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.CompiledOpticalTrace;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalInteractionKind;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalPort;
import io.github.yoglappland.spectralization.optics.OpticalTraceStep;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldEffectType;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;

public final class OpticalTraceValidator {
    private static final int LOG_INTERVAL_TICKS = 100;
    private static final int VALIDATION_INTERVAL_TICKS = 10;
    private static final int MAX_REPORTED_DIFFERENCES = 64;
    private static final double ABSOLUTE_TOLERANCE = 0.05;
    private static final double RELATIVE_TOLERANCE = 0.005;
    private static final String LOG_RELATIVE_PATH = "logs/spectralization/optical_compare.log";
    private static final Map<ValidationKey, Long> LAST_LOGGED_MISMATCH = new HashMap<>();

    public static void validate(Level level, BlockPos sourcePos, OutputBeam sourceOutput, CompiledOpticalTrace legacyTrace) {
        if (level.isClientSide) {
            return;
        }

        if (!shouldValidateThisTick(level, sourcePos, sourceOutput)) {
            return;
        }

        try {
            validateUnsafe(level, sourcePos, sourceOutput, legacyTrace);
        } catch (RuntimeException exception) {
            Spectralization.LOGGER.warn("Optical solver validation failed at {}", sourcePos, exception);
        }
    }

    private static void validateUnsafe(
            Level level,
            BlockPos sourcePos,
            OutputBeam sourceOutput,
            CompiledOpticalTrace legacyTrace
    ) {
        TopologyOpticalTrace topologyTrace = TopologyOpticalSolver.solve(level, sourcePos, sourceOutput);
        ObservedTrace legacyObserved = observeLegacyTrace(level, legacyTrace);
        ObservedTrace topologyObserved = new ObservedTrace(
                topologyTrace.incidentPowerByPort(),
                topologyTrace.affectedAirPowerByPos()
        );
        List<String> differences = differences(legacyObserved, topologyObserved);

        if (differences.isEmpty()) {
            return;
        }

        ValidationKey key = new ValidationKey(
                level.dimension().location(),
                sourcePos,
                sourceOutput.outgoingDirection()
        );
        long gameTime = level.getGameTime();
        long lastLogged = LAST_LOGGED_MISMATCH.getOrDefault(key, Long.MIN_VALUE);

        if (gameTime - lastLogged < LOG_INTERVAL_TICKS) {
            return;
        }

        LAST_LOGGED_MISMATCH.put(key, gameTime);
        writeMismatchLog(level, sourcePos, sourceOutput, legacyTrace, topologyTrace, differences);
    }

    private static boolean shouldValidateThisTick(Level level, BlockPos sourcePos, OutputBeam sourceOutput) {
        long offset = sourcePos.asLong() + sourceOutput.outgoingDirection().get3DDataValue();
        return Math.floorMod(offset, VALIDATION_INTERVAL_TICKS)
                == level.getGameTime() % VALIDATION_INTERVAL_TICKS;
    }

    private static ObservedTrace observeLegacyTrace(Level level, CompiledOpticalTrace legacyTrace) {
        Map<OpticalPort, Double> incidentPowerByPort = new HashMap<>();
        Map<BlockPos, Double> affectedAirPowerByPos = new HashMap<>();

        for (OpticalTraceStep step : legacyTrace.steps()) {
            if (!level.isLoaded(step.pos())) {
                continue;
            }

            BlockState state = level.getBlockState(step.pos());

            if (state.isAir()
                    && OpticalFieldSources.hasEffect(level, step.pos(), OpticalFieldEffectType.SCATTERING)) {
                affectedAirPowerByPos.merge(step.pos(), step.incidentBeam().totalPower(), Double::sum);
                continue;
            }

            if (isReadableBlock(state, step.interactionKind())) {
                incidentPowerByPort.merge(step.inputPort(), step.incidentBeam().totalPower(), Double::sum);
            }
        }

        return new ObservedTrace(incidentPowerByPort, affectedAirPowerByPos);
    }

    private static boolean isReadableBlock(BlockState state, OpticalInteractionKind interactionKind) {
        Block block = state.getBlock();

        return interactionKind == OpticalInteractionKind.OPTICAL_ELEMENT
                || block instanceof OpticalElement
                || OpticalMaterialProfiles.isExplicitOpticalMaterial(state)
                || OpticalFieldSources.isScatteringFieldSource(state);
    }

    private static List<String> differences(ObservedTrace legacyTrace, ObservedTrace topologyTrace) {
        List<String> differences = new ArrayList<>();
        Set<OpticalPort> ports = new HashSet<>();
        ports.addAll(legacyTrace.incidentPowerByPort().keySet());
        ports.addAll(topologyTrace.incidentPowerByPort().keySet());

        ports.stream()
                .sorted(Comparator.comparing(OpticalTraceValidator::portSortKey))
                .forEach(port -> addDifference(
                        differences,
                        "port " + formatPort(port),
                        legacyTrace.incidentPowerByPort().getOrDefault(port, 0.0),
                        topologyTrace.incidentPowerByPort().getOrDefault(port, 0.0)
                ));

        Set<BlockPos> affectedAirPositions = new HashSet<>();
        affectedAirPositions.addAll(legacyTrace.affectedAirPowerByPos().keySet());
        affectedAirPositions.addAll(topologyTrace.affectedAirPowerByPos().keySet());

        affectedAirPositions.stream()
                .sorted(Comparator.comparing(BlockPos::asLong))
                .forEach(pos -> addDifference(
                        differences,
                        "affected_air " + formatPos(pos),
                        legacyTrace.affectedAirPowerByPos().getOrDefault(pos, 0.0),
                        topologyTrace.affectedAirPowerByPos().getOrDefault(pos, 0.0)
                ));

        return differences;
    }

    private static void addDifference(List<String> differences, String label, double legacyPower, double topologyPower) {
        double absoluteDifference = Math.abs(legacyPower - topologyPower);
        double relativeScale = Math.max(Math.max(Math.abs(legacyPower), Math.abs(topologyPower)), 1.0);

        if (absoluteDifference <= ABSOLUTE_TOLERANCE
                || absoluteDifference / relativeScale <= RELATIVE_TOLERANCE) {
            return;
        }

        differences.add(String.format(
                "%s legacy=%.6f topology=%.6f delta=%.6f",
                label,
                legacyPower,
                topologyPower,
                topologyPower - legacyPower
        ));
    }

    private static void writeMismatchLog(
            Level level,
            BlockPos sourcePos,
            OutputBeam sourceOutput,
            CompiledOpticalTrace legacyTrace,
            TopologyOpticalTrace topologyTrace,
            List<String> differences
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("=== Spectralization optical solver mismatch ===\n");
        builder.append("time=").append(Instant.now()).append('\n');
        builder.append("dimension=").append(level.dimension().location()).append('\n');
        builder.append("game_time=").append(level.getGameTime()).append('\n');
        builder.append("source=").append(formatPos(sourcePos)).append('\n');
        builder.append("source_direction=").append(sourceOutput.outgoingDirection()).append('\n');
        builder.append("source_power=").append(String.format("%.6f", sourceOutput.beam().totalPower())).append('\n');
        builder.append("legacy_steps=").append(legacyTrace.steps().size()).append('\n');
        builder.append("legacy_terminations=").append(legacyTrace.terminations().size()).append('\n');
        builder.append("topology_processed_states=").append(topologyTrace.processedStates()).append('\n');
        builder.append("difference_count=").append(differences.size()).append('\n');

        int reported = Math.min(MAX_REPORTED_DIFFERENCES, differences.size());

        for (int index = 0; index < reported; index++) {
            builder.append("  ").append(differences.get(index)).append('\n');
        }

        if (differences.size() > reported) {
            builder.append("  ... ").append(differences.size() - reported).append(" more differences\n");
        }

        builder.append('\n');

        Path logPath = FMLPaths.GAMEDIR.get().resolve(LOG_RELATIVE_PATH);

        try {
            Files.createDirectories(logPath.getParent());
            Files.writeString(
                    logPath,
                    builder.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
            Spectralization.LOGGER.warn(
                    "Optical solver mismatch at {} in {}; wrote {} differences to {}",
                    sourcePos,
                    level.dimension().location(),
                    differences.size(),
                    LOG_RELATIVE_PATH
            );
        } catch (IOException exception) {
            Spectralization.LOGGER.warn("Failed to write optical solver comparison log", exception);
        }
    }

    private static String formatPort(OpticalPort port) {
        return formatPos(port.pos()) + " side=" + port.side();
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")";
    }

    private static String portSortKey(OpticalPort port) {
        return port.pos().asLong() + ":" + port.side().get3DDataValue();
    }

    private record ObservedTrace(
            Map<OpticalPort, Double> incidentPowerByPort,
            Map<BlockPos, Double> affectedAirPowerByPos
    ) {
        private ObservedTrace {
            incidentPowerByPort = Map.copyOf(incidentPowerByPort);
            affectedAirPowerByPos = Map.copyOf(affectedAirPowerByPos);
        }
    }

    private record ValidationKey(ResourceLocation dimension, BlockPos sourcePos, Direction direction) {
        private ValidationKey {
            sourcePos = sourcePos.immutable();
        }
    }

    private OpticalTraceValidator() {
    }
}
