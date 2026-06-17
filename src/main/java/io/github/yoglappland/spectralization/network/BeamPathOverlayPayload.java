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

public record BeamPathOverlayPayload(int ownerId, List<Segment> segments) implements CustomPacketPayload {
    private static final int MAX_SEGMENTS = 512;
    public static final Type<BeamPathOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "beam_path_overlay")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BeamPathOverlayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BeamPathOverlayPayload::write, BeamPathOverlayPayload::read);

    public BeamPathOverlayPayload {
        segments = List.copyOf(segments);
    }

    private static BeamPathOverlayPayload read(RegistryFriendlyByteBuf buffer) {
        int ownerId = buffer.readVarInt();
        int count = Math.min(MAX_SEGMENTS, buffer.readVarInt());
        List<Segment> segments = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            segments.add(Segment.read(buffer));
        }

        return new BeamPathOverlayPayload(ownerId, segments);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(ownerId);
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

    public record Segment(
            BlockPos from,
            BlockPos to,
            Direction direction,
            boolean coherent,
            int colorRgb,
            int widthLevel,
            int visualLevel,
            double startRadius,
            double endRadius
    ) {
        private static Segment read(RegistryFriendlyByteBuf buffer) {
            return new Segment(
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readEnum(Direction.class),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readDouble()
            );
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeBlockPos(from);
            buffer.writeBlockPos(to);
            buffer.writeEnum(direction);
            buffer.writeBoolean(coherent);
            buffer.writeVarInt(colorRgb);
            buffer.writeVarInt(widthLevel);
            buffer.writeVarInt(visualLevel);
            buffer.writeDouble(startRadius);
            buffer.writeDouble(endRadius);
        }
    }
}
