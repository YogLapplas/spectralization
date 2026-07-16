package io.github.yoglappland.spectralization.diagnostics;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
            + "# \u5982\u679c\u4f60\u6b63\u5728\u9605\u8bfb\u8fd9\u4e2a\u65e5\u5fd7\uff0c\u8bf7\u5148\u53c2\u9605 docs/LOGGING.md\u3002\n"
            + "# Logs are organized around Spectralization's geometry -> topology -> data pipeline.\n\n";
    private static final int MAX_PENDING_LOG_WRITES = 1024;
    private static final Object WRITE_STATS_LOCK = new Object();
    private static long writeCount;
    private static long writeBytes;
    private static long writeNanos;
    private static long maxWriteNanos;
    private static final AtomicLong ENQUEUED_WRITES = new AtomicLong();
    private static final AtomicLong DROPPED_WRITES = new AtomicLong();
    private static final AtomicLong FAILED_WRITES = new AtomicLong();
    private static final AtomicInteger PENDING_WRITES = new AtomicInteger();
    private static final AtomicInteger MAX_PENDING_WRITES = new AtomicInteger();
    private static final Map<Path, OutputStream> OPEN_LOGS = new HashMap<>();
    private static final ThreadPoolExecutor LOG_WRITER = createLogWriter();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
                SpectralDiagnostics::shutdownLogWriter,
                "spectralization-log-close"
        ));
    }

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
        public static final String MICROLIZER = "microlizer";
        public static final String FIBER = "fiber";
        public static final String HOLOGRAPHIC_STORAGE = "holographic_storage";
        public static final String LITHOGRAPHY = "lithography";

        private Subsystem() {
        }
    }

    public static WriteStats appendLog(String relativePath, String content, String failureLabel) {
        int pending = PENDING_WRITES.incrementAndGet();
        MAX_PENDING_WRITES.accumulateAndGet(pending, Math::max);
        try {
            LOG_WRITER.execute(() -> {
                try {
                    appendLogNow(relativePath, content, failureLabel);
                } finally {
                    PENDING_WRITES.decrementAndGet();
                }
            });
            ENQUEUED_WRITES.incrementAndGet();
        } catch (RejectedExecutionException rejected) {
            PENDING_WRITES.decrementAndGet();
            long dropped = DROPPED_WRITES.incrementAndGet();
            if (!LOG_WRITER.isShutdown() && (dropped == 1L || (dropped & (dropped - 1L)) == 0L)) {
                Spectralization.LOGGER.warn(
                        "Dropped {} Spectralization log writes because the bounded diagnostics queue is full",
                        dropped
                );
            }
        }
        return writeStats();
    }

    private static void appendLogNow(String relativePath, String content, String failureLabel) {
        Path logPath = FMLPaths.GAMEDIR.get().resolve(relativePath);

        try {
            long startNanos = System.nanoTime();
            OutputStream output = OPEN_LOGS.get(logPath);
            long bytesWritten = 0L;
            if (output == null) {
                Files.createDirectories(logPath.getParent());
                boolean needsHeader = !Files.exists(logPath) || Files.size(logPath) == 0L;
                output = Files.newOutputStream(
                        logPath,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
                OPEN_LOGS.put(logPath, output);
                if (needsHeader) {
                    byte[] headerBytes = HEADER.getBytes(StandardCharsets.UTF_8);
                    output.write(headerBytes);
                    bytesWritten += headerBytes.length;
                }
            }
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            output.write(contentBytes);
            bytesWritten += contentBytes.length;
            long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
            synchronized (WRITE_STATS_LOCK) {
                writeCount++;
                writeBytes += bytesWritten;
                writeNanos += elapsedNanos;
                maxWriteNanos = Math.max(maxWriteNanos, elapsedNanos);
            }
        } catch (IOException | RuntimeException exception) {
            FAILED_WRITES.incrementAndGet();
            closeLog(logPath);
            Spectralization.LOGGER.warn("Failed to write Spectralization {} log", failureLabel, exception);
        }
    }

    private static ThreadPoolExecutor createLogWriter() {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "spectralization-log-writer");
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_LOG_WRITES),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.prestartAllCoreThreads();
        return executor;
    }

    private static void shutdownLogWriter() {
        LOG_WRITER.shutdown();
        boolean terminated = false;
        try {
            terminated = LOG_WRITER.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (terminated) {
            closeOpenLogs();
        }
    }

    private static void closeOpenLogs() {
        for (OutputStream output : OPEN_LOGS.values()) {
            try {
                output.close();
            } catch (IOException ignored) {
            }
        }
        OPEN_LOGS.clear();
    }

    private static void closeLog(Path path) {
        OutputStream output = OPEN_LOGS.remove(path);
        if (output == null) {
            return;
        }
        try {
            output.close();
        } catch (IOException ignored) {
        }
    }

    public static WriteStats writeStats() {
        synchronized (WRITE_STATS_LOCK) {
            return new WriteStats(
                    writeCount,
                    writeBytes,
                    writeNanos,
                    maxWriteNanos,
                    ENQUEUED_WRITES.get(),
                    DROPPED_WRITES.get(),
                    FAILED_WRITES.get(),
                    PENDING_WRITES.get(),
                    MAX_PENDING_WRITES.get()
            );
        }
    }

    private static WriteStats write(Event event) {
        if (!SpectralizationConfig.diagnosticsEventLog()) {
            return writeStats();
        }

        return appendLog(DIAGNOSTICS_LOG_RELATIVE_PATH, event.format() + '\n', "diagnostics");
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

        public WriteStats write() {
            return SpectralDiagnostics.write(this);
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

    public record WriteStats(
            long count,
            long bytes,
            long totalNanos,
            long maxNanos,
            long enqueuedCount,
            long droppedCount,
            long failedCount,
            int pendingWrites,
            int maxPendingWrites
    ) {
        public WriteStats {
            count = Math.max(0L, count);
            bytes = Math.max(0L, bytes);
            totalNanos = Math.max(0L, totalNanos);
            maxNanos = Math.max(0L, maxNanos);
            enqueuedCount = Math.max(0L, enqueuedCount);
            droppedCount = Math.max(0L, droppedCount);
            failedCount = Math.max(0L, failedCount);
            pendingWrites = Math.max(0, pendingWrites);
            maxPendingWrites = Math.max(0, maxPendingWrites);
        }

        public double averageNanos() {
            return count <= 0L ? 0.0D : totalNanos / (double) count;
        }
    }

    private SpectralDiagnostics() {
    }
}
