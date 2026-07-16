package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;

/** Dedicated bounded executor. Workers receive only immutable spot projection work. */
public final class SpotProjectionExecutor {
    private static final Map<MinecraftServer, Instance> INSTANCES = new IdentityHashMap<>();

    public static synchronized void start(MinecraftServer server) {
        if (!SpectralizationConfig.spotProjectionParallelEnabled()) {
            return;
        }
        INSTANCES.computeIfAbsent(server, ignored -> new Instance(
                SpectralizationConfig.spotProjectionWorkers(),
                SpectralizationConfig.spotProjectionMaxInFlight()
        ));
    }

    public static boolean submit(MinecraftServer server, Runnable task) {
        if (!SpectralizationConfig.spotProjectionParallelEnabled()) {
            return false;
        }
        Instance instance;
        synchronized (SpotProjectionExecutor.class) {
            start(server);
            instance = INSTANCES.get(server);
        }
        return instance != null && instance.submit(task);
    }

    public static synchronized void shutdown(MinecraftServer server) {
        Instance instance = INSTANCES.remove(server);
        if (instance != null) {
            instance.shutdown();
        }
    }

    public static synchronized void shutdownAll() {
        for (Instance instance : INSTANCES.values()) {
            instance.shutdown();
        }
        INSTANCES.clear();
    }

    public static synchronized int inFlight(MinecraftServer server) {
        Instance instance = INSTANCES.get(server);
        return instance == null ? 0 : instance.inFlight();
    }

    public static synchronized int queueDepth(MinecraftServer server) {
        Instance instance = INSTANCES.get(server);
        return instance == null ? 0 : instance.queueDepth();
    }

    public static synchronized int availableSlots(MinecraftServer server) {
        if (!SpectralizationConfig.spotProjectionParallelEnabled()) {
            return 0;
        }
        start(server);
        Instance instance = INSTANCES.get(server);
        return instance == null ? 0 : instance.availableSlots();
    }

    public static synchronized State state(MinecraftServer server) {
        if (!SpectralizationConfig.spotProjectionParallelEnabled()) {
            return State.STOPPED;
        }
        start(server);
        Instance instance = INSTANCES.get(server);
        return instance == null ? State.STOPPED : instance.state();
    }

    public record State(
            int workers,
            int maxInFlight,
            int queueCapacity,
            int poolSize,
            int activeWorkers,
            int inFlight,
            int queueDepth
    ) {
        private static final State STOPPED = new State(0, 0, 0, 0, 0, 0, 0);
    }

    private static final class Instance {
        private final int maxInFlight;
        private final int workers;
        private final int queueCapacity;
        private final AtomicInteger inFlight = new AtomicInteger();
        private final ThreadPoolExecutor executor;

        private Instance(int requestedWorkers, int requestedMaxInFlight) {
            maxInFlight = Math.max(1, requestedMaxInFlight);
            workers = Math.max(1, Math.min(requestedWorkers, maxInFlight));
            // The atomic in-flight counter is the sole admission bound. The queue is sized to
            // that bound so ThreadPoolExecutor cannot reject an admitted task during startup or
            // a worker/queue handoff race.
            queueCapacity = maxInFlight;
            AtomicInteger threadIds = new AtomicInteger();
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "spectralization-spot-" + threadIds.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
            executor = new ThreadPoolExecutor(
                    workers,
                    workers,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(queueCapacity),
                    factory,
                    new ThreadPoolExecutor.AbortPolicy()
            );
            executor.prestartAllCoreThreads();
        }

        private boolean submit(Runnable task) {
            while (true) {
                int current = inFlight.get();
                if (current >= maxInFlight) {
                    return false;
                }
                if (inFlight.compareAndSet(current, current + 1)) {
                    break;
                }
            }
            try {
                executor.execute(() -> {
                    try {
                        task.run();
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
                return true;
            } catch (RejectedExecutionException rejected) {
                inFlight.decrementAndGet();
                return false;
            }
        }

        private int inFlight() {
            return inFlight.get();
        }

        private int queueDepth() {
            return executor.getQueue().size();
        }

        private int availableSlots() {
            return Math.max(0, maxInFlight - inFlight.get());
        }

        private State state() {
            return new State(
                    workers,
                    maxInFlight,
                    queueCapacity,
                    executor.getPoolSize(),
                    executor.getActiveCount(),
                    inFlight.get(),
                    executor.getQueue().size()
            );
        }

        private void shutdown() {
            executor.shutdownNow();
        }
    }

    private SpotProjectionExecutor() {
    }
}
