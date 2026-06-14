package io.github.yoglappland.spectralization.client.compact;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientCompactMachineWorkAreaOverlayCache {
    private static WorkArea activeWorkArea;
    private static Object lastLevel;

    public static void toggle(BlockPos corePos, BlockPos min, BlockPos max) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }

        clearIfLevelChanged(minecraft.level);
        if (activeWorkArea != null && activeWorkArea.corePos().equals(corePos)) {
            activeWorkArea = null;
            return;
        }

        activeWorkArea = new WorkArea(corePos.immutable(), min.immutable(), max.immutable());
    }

    public static Optional<WorkArea> activeWorkArea() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return Optional.empty();
        }

        clearIfLevelChanged(minecraft.level);
        return Optional.ofNullable(activeWorkArea);
    }

    public static void clear() {
        activeWorkArea = null;
        lastLevel = null;
    }

    private static void clearIfLevelChanged(Object level) {
        if (lastLevel == level) {
            return;
        }

        activeWorkArea = null;
        lastLevel = level;
    }

    public record WorkArea(BlockPos corePos, BlockPos min, BlockPos max) {
    }

    private ClientCompactMachineWorkAreaOverlayCache() {
    }
}
