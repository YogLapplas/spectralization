package io.github.yoglappland.spectralization.optics.fiber;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

public final class FiberLineOfSight {
    private static final double SAMPLES_PER_BLOCK = 4.0;

    public static boolean clearBetween(LevelAccessor level, BlockPos from, BlockPos to) {
        return clearBetween(level, from, to, FiberPassagePolicy.AIR_ONLY);
    }

    public static boolean clearBetween(LevelAccessor level, BlockPos from, BlockPos to, FiberPassagePolicy policy) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(policy, "policy");

        if (from.equals(to)) {
            return true;
        }

        double length = FiberDistances.segmentLength(from, to);
        int steps = Math.max(1, (int) Math.ceil(length * SAMPLES_PER_BLOCK));
        LongSet checked = new LongOpenHashSet();

        for (int step = 1; step < steps; step++) {
            double t = step / (double) steps;
            BlockPos samplePos = new BlockPos(
                    floor(lerp(from.getX() + 0.5D, to.getX() + 0.5D, t)),
                    floor(lerp(from.getY() + 0.5D, to.getY() + 0.5D, t)),
                    floor(lerp(from.getZ() + 0.5D, to.getZ() + 0.5D, t))
            );

            if (samplePos.equals(from) || samplePos.equals(to) || !checked.add(samplePos.asLong())) {
                continue;
            }

            if (!policy.canPassThrough(level, samplePos, level.getBlockState(samplePos))) {
                return false;
            }
        }

        return true;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private FiberLineOfSight() {
    }
}
