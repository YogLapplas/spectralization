package io.github.yoglappland.spectralization.diagnostics;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;

public final class SpectralDiagnostics {
    public static final String DOCUMENT_PATH = "docs/LOGGING.md";
    private static final DateTimeFormatter SESSION_LOG_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd_HHmmss_'UTC'")
            .withZone(ZoneOffset.UTC);
    private static final String DIAGNOSTICS_LOG_FILE_NAME =
            "diagnostics_" + SESSION_LOG_TIMESTAMP.format(Instant.now()) + ".log";
    private static final String DIAGNOSTICS_LOG_RELATIVE_PATH =
            "logs/spectralization/" + DIAGNOSTICS_LOG_FILE_NAME;
    private static final String HEADER = "# If you are reading this log, read docs/LOGGING.md first.\n"
            + "# 如果你正在阅读这个日志，请先参考 docs/LOGGING.md。\n"
            + "# Logs are organized around Spectralization's geometry -> topology -> data pipeline.\n\n";

    public static Event event(Level level, String subsystem, String event) {
        return new Event("event", level, subsystem, event);
    }

    public static Event transition(Level level, String subsystem, String event) {
        return new Event("transition", level, subsystem, event);
    }

    public static Event anomaly(Level level, String subsystem, String event) {
        return new Event("anomaly", level, subsystem, event);
    }

    public static final class Subsystem {
        public static final String COMPACT_MACHINE = "compact_machine";
        public static final String FIBER = "fiber";
        public static final String HOLOGRAPHIC_STORAGE = "holographic_storage";
        public static final String LITHOGRAPHY = "lithography";

        private Subsystem() {
        }
    }

    public static synchronized void appendLog(String relativePath, String content, String failureLabel) {
        Path logPath = FMLPaths.GAMEDIR.get().resolve(relativePath);

        try {
            Files.createDirectories(logPath.getParent());
            boolean needsHeader = !Files.exists(logPath) || Files.size(logPath) == 0L;
            String fullContent = needsHeader ? HEADER + content : content;
            Files.writeString(
                    logPath,
                    fullContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            Spectralization.LOGGER.warn("Failed to write Spectralization {} log", failureLabel, exception);
        }
    }

    private static void write(Event event) {
        if (!SpectralizationConfig.diagnosticsEventLog()) {
            return;
        }

        appendLog(DIAGNOSTICS_LOG_RELATIVE_PATH, event.format() + '\n', "diagnostics");
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }

        String raw;
        if (value instanceof Double doubleValue) {
            raw = String.format(Locale.ROOT, "%.6f", doubleValue);
        } else if (value instanceof Float floatValue) {
            raw = String.format(Locale.ROOT, "%.6f", floatValue);
        } else if (value instanceof BlockPos pos) {
            raw = formatPos(pos);
        } else {
            raw = String.valueOf(value);
        }

        return quoteIfNeeded(raw.replace('\n', ' ').replace('\r', ' '));
    }

    private static String quoteIfNeeded(String raw) {
        if (raw.isEmpty()) {
            return "\"\"";
        }

        boolean safe = true;
        for (int index = 0; index < raw.length(); index++) {
            char c = raw.charAt(index);
            if (Character.isWhitespace(c) || c == '"' || c == '=') {
                safe = false;
                break;
            }
        }

        if (safe) {
            return raw;
        }

        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static final class Event {
        private final String severity;
        private final Level level;
        private final String subsystem;
        private final String event;
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Event(String severity, Level level, String subsystem, String event) {
            this.severity = severity;
            this.level = level;
            this.subsystem = subsystem;
            this.event = event;
        }

        public Event field(String key, Object value) {
            fields.put(key, value);
            return this;
        }

        public Event pos(String key, BlockPos pos) {
            fields.put(key, pos == null ? null : pos.immutable());
            return this;
        }

        public void write() {
            SpectralDiagnostics.write(this);
        }

        private String format() {
            StringBuilder builder = new StringBuilder(256);
            builder.append("time=").append(Instant.now())
                    .append(" severity=").append(severity)
                    .append(" subsystem=").append(value(subsystem))
                    .append(" event=").append(value(event));

            if (level != null) {
                builder.append(" dim=").append(value(level.dimension().location()))
                        .append(" tick=").append(level.getGameTime());
            }

            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                builder.append(' ')
                        .append(entry.getKey())
                        .append('=')
                        .append(value(entry.getValue()));
            }

            return builder.toString();
        }
    }

    private SpectralDiagnostics() {
    }
}
