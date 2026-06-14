package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CompactMachineOverlayPayload(List<Segment> segments) implements CustomPacketPayload {
    private static final int MAX_SEGMENTS = 512;
    public static final Type<CompactMachineOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "compact_machine_overlay")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CompactMachineOverlayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CompactMachineOverlayPayload::write, CompactMachineOverlayPayload::read);

    public CompactMachineOverlayPayload {
        segments = List.copyOf(segments);
    }

    private static CompactMachineOverlayPayload read(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(MAX_SEGMENTS, buffer.readVarInt());
        List<Segment> segments = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            segments.add(Segment.read(buffer));
        }

        return new CompactMachineOverlayPayload(segments);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(MAX_SEGMENTS, segments.size());
        buffer.writeVarInt(count);

        for (int index = 0; index < count; index++) {
            segments.get(index).write(buffer);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Segment(BlockPos from, BlockPos to, Direction direction) {
        public Segment {
            from = from.immutable();
            to = to.immutable();
        }

        private static Segment read(RegistryFriendlyByteBuf buffer) {
            return new Segment(
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readEnum(Direction.class)
            );
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(from);
            buffer.writeBlockPos(to);
            buffer.writeEnum(direction);
        }
    }
}
