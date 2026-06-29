package io.github.yoglappland.spectralization.client.compact;

import io.github.yoglappland.spectralization.network.CompactMachineAnimationPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public final class ClientCompactMachineAnimationCache {
    private static final int MAX_ACTIVE_ANIMATIONS = 16;
    private static final double END_GRACE_TICKS = 3.0D;
    private static List<Animation> animations = List.of();
    private static Object lastLevel;

    public static void accept(CompactMachineAnimationPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }

        clearIfLevelChanged(minecraft.level);
        long startTick = minecraft.level.getGameTime();
        List<ProjectedBlockSnapshot> projectedBlockSnapshots = captureProjectedBlockSnapshots(
                minecraft.level,
                payload.projectedBlocks()
        );
        List<Animation> updated = new ArrayList<>(animations.size() + 1);

        for (Animation animation : animations) {
            if (!animation.corePos().equals(payload.corePos())) {
                updated.add(animation);
            }
        }

        updated.add(new Animation(
                payload.corePos(),
                payload.min(),
                payload.max(),
                startTick,
                payload.durationTicks(),
                payload.projectedBlocks(),
                projectedBlockSnapshots
        ));

        if (updated.size() > MAX_ACTIVE_ANIMATIONS) {
            updated = new ArrayList<>(updated.subList(updated.size() - MAX_ACTIVE_ANIMATIONS, updated.size()));
        }

        animations = List.copyOf(updated);
    }

    public static List<ActiveAnimation> activeAnimations(long gameTime, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return List.of();
        }

        clearIfLevelChanged(minecraft.level);
        List<Animation> kept = new ArrayList<>();
        List<ActiveAnimation> active = new ArrayList<>();

        for (Animation animation : animations) {
            double age = Math.max(0.0D, gameTime - animation.startTick() + partialTick);
            if (age <= animation.durationTicks() + END_GRACE_TICKS) {
                kept.add(animation);
            }
            if (age <= animation.durationTicks()) {
                active.add(animation.active(age));
            }
        }

        if (kept.size() != animations.size()) {
            animations = List.copyOf(kept);
        }

        return List.copyOf(active);
    }

    public static void clear() {
        animations = List.of();
        lastLevel = null;
    }

    private static void clearIfLevelChanged(Object level) {
        if (lastLevel == level) {
            return;
        }

        animations = List.of();
        lastLevel = level;
    }

    private static List<ProjectedBlockSnapshot> captureProjectedBlockSnapshots(Level level, List<BlockPos> projectedBlocks) {
        List<ProjectedBlockSnapshot> snapshots = new ArrayList<>(projectedBlocks.size());

        for (BlockPos block : projectedBlocks) {
            BlockState state = level.getBlockState(block);
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }

            snapshots.add(new ProjectedBlockSnapshot(block, state));
        }

        return List.copyOf(snapshots);
    }

    public record ActiveAnimation(
            BlockPos corePos,
            BlockPos min,
            BlockPos max,
            double age,
            int durationTicks,
            double rawProgress,
            double progress,
            double pulse,
            List<BlockPos> projectedBlocks,
            List<ProjectedBlockSnapshot> projectedBlockSnapshots
    ) {
        public ActiveAnimation {
            corePos = corePos.immutable();
            min = min.immutable();
            max = max.immutable();
            age = Math.max(0.0D, age);
            durationTicks = Math.max(1, durationTicks);
            progress = clamp(progress);
            rawProgress = clamp(rawProgress);
            pulse = clamp(pulse);
            projectedBlocks = projectedBlocks.stream().map(BlockPos::immutable).toList();
            projectedBlockSnapshots = List.copyOf(projectedBlockSnapshots);
        }
    }

    public record ProjectedBlockSnapshot(BlockPos pos, BlockState state) {
        public ProjectedBlockSnapshot {
            pos = pos.immutable();
        }
    }

    private record Animation(
            BlockPos corePos,
            BlockPos min,
            BlockPos max,
            long startTick,
            int durationTicks,
            List<BlockPos> projectedBlocks,
            List<ProjectedBlockSnapshot> projectedBlockSnapshots
    ) {
        private Animation {
            projectedBlocks = List.copyOf(projectedBlocks);
            projectedBlockSnapshots = List.copyOf(projectedBlockSnapshots);
        }

        private ActiveAnimation active(double age) {
            double rawProgress = clamp(age / Math.max(1, durationTicks));
            double easedProgress = smoothStep(rawProgress);
            double pulse = Math.sin(rawProgress * Math.PI);
            return new ActiveAnimation(
                    corePos,
                    min,
                    max,
                    age,
                    durationTicks,
                    rawProgress,
                    easedProgress,
                    Math.max(0.0D, pulse),
                    projectedBlocks,
                    projectedBlockSnapshots
            );
        }
    }

    private static double smoothStep(double value) {
        double clamped = clamp(value);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private ClientCompactMachineAnimationCache() {
    }
}
