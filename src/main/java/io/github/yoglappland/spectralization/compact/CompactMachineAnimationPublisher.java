package io.github.yoglappland.spectralization.compact;

import io.github.yoglappland.spectralization.network.CompactMachineAnimationPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CompactMachineAnimationPublisher {
    private static final double VIEW_DISTANCE = 96.0D;
    private static final double VIEW_DISTANCE_SQR = VIEW_DISTANCE * VIEW_DISTANCE;

    public static void publishStart(ServerLevel level, BlockPos corePos, BlockPos min, BlockPos max) {
        List<BlockPos> projectedBlocks = projectedBlocks(level, min, max);
        CompactMachineAnimationPayload payload = new CompactMachineAnimationPayload(
                corePos,
                min,
                max,
                CompactMachineAnimationPayload.DEFAULT_DURATION_TICKS,
                projectedBlocks
        );
        double centerX = (min.getX() + max.getX() + 1.0D) * 0.5D;
        double centerY = (min.getY() + max.getY() + 1.0D) * 0.5D;
        double centerZ = (min.getZ() + max.getZ() + 1.0D) * 0.5D;

        for (var player : level.players()) {
            if (player.distanceToSqr(centerX, centerY, centerZ) <= VIEW_DISTANCE_SQR) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static List<BlockPos> projectedBlocks(ServerLevel level, BlockPos min, BlockPos max) {
        List<BlockPos> blocks = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).isAir()) {
                blocks.add(pos.immutable());
            }
        }

        return List.copyOf(blocks);
    }

    private CompactMachineAnimationPublisher() {
    }
}
