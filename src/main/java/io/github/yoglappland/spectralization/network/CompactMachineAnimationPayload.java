package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompactMachineAnimationPayload(
        BlockPos corePos,
        BlockPos min,
        BlockPos max,
        int durationTicks,
        List<BlockPos> projectedBlocks
)
        implements CustomPacketPayload {
    public static final int DEFAULT_DURATION_TICKS = 88;
    public static final int SLOW_ORBIT_TICKS = 30;
    public static final int CLEAR_WORK_AREA_AT_TICKS = 41;
    public static final int MERGE_AT_TICKS = 60;
    private static final int MIN_DURATION_TICKS = 24;
    private static final int MAX_DURATION_TICKS = 240;
    private static final int MAX_PROJECTED_BLOCKS = 1024;
    public static final Type<CompactMachineAnimationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "compact_machine_animation")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CompactMachineAnimationPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CompactMachineAnimationPayload::write, CompactMachineAnimationPayload::read);

    public CompactMachineAnimationPayload {
        corePos = corePos.immutable();
        min = min.immutable();
        max = max.immutable();
        durationTicks = Math.max(MIN_DURATION_TICKS, Math.min(MAX_DURATION_TICKS, durationTicks));
        projectedBlocks = projectedBlocks.stream()
                .limit(MAX_PROJECTED_BLOCKS)
                .map(BlockPos::immutable)
                .toList();
    }

    private static CompactMachineAnimationPayload read(RegistryFriendlyByteBuf buffer) {
        BlockPos corePos = buffer.readBlockPos();
        BlockPos min = buffer.readBlockPos();
        BlockPos max = buffer.readBlockPos();
        int durationTicks = buffer.readVarInt();
        int count = Math.min(MAX_PROJECTED_BLOCKS, Math.max(0, buffer.readVarInt()));
        List<BlockPos> projectedBlocks = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            projectedBlocks.add(buffer.readBlockPos());
        }

        return new CompactMachineAnimationPayload(
                corePos,
                min,
                max,
                durationTicks,
                projectedBlocks
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(corePos);
        buffer.writeBlockPos(min);
        buffer.writeBlockPos(max);
        buffer.writeVarInt(durationTicks);
        buffer.writeVarInt(projectedBlocks.size());
        for (BlockPos block : projectedBlocks) {
            buffer.writeBlockPos(block);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
