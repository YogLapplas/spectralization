package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    public enum EndpointPlacement {
        BLOCK_CENTER,
        BLOCK_FACE
    }

    public record Segment(
            BlockPos from,
            BlockPos to,
            Direction direction,
            EndpointPlacement startPlacement,
            Direction startSide,
            EndpointPlacement endPlacement,
            Direction endSide,
            boolean coherent,
            int colorRgb,
            int widthLevel,
            int visualLevel,
            double startRadius,
            double endRadius
    ) {
        public Segment(
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
            this(
                    from,
                    to,
                    direction,
                    EndpointPlacement.BLOCK_CENTER,
                    direction,
                    EndpointPlacement.BLOCK_CENTER,
                    direction.getOpposite(),
                    coherent,
                    colorRgb,
                    widthLevel,
                    visualLevel,
                    startRadius,
                    endRadius
            );
        }

        public Segment(
                BlockPos from,
                BlockPos to,
                Direction direction,
                EndpointPlacement startPlacement,
                EndpointPlacement endPlacement,
                boolean coherent,
                int colorRgb,
                int widthLevel,
                int visualLevel,
                double startRadius,
                double endRadius
        ) {
            this(
                    from,
                    to,
                    direction,
                    startPlacement,
                    direction,
                    endPlacement,
                    direction.getOpposite(),
                    coherent,
                    colorRgb,
                    widthLevel,
                    visualLevel,
                    startRadius,
                    endRadius
            );
        }

        public Segment {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(startPlacement, "startPlacement");
            Objects.requireNonNull(startSide, "startSide");
            Objects.requireNonNull(endPlacement, "endPlacement");
            Objects.requireNonNull(endSide, "endSide");

            from = from.immutable();
            to = to.immutable();
        }

        private static Segment read(RegistryFriendlyByteBuf buffer) {
            return new Segment(
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readEnum(Direction.class),
                    buffer.readEnum(EndpointPlacement.class),
                    buffer.readEnum(Direction.class),
                    buffer.readEnum(EndpointPlacement.class),
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
            buffer.writeEnum(startPlacement);
            buffer.writeEnum(startSide);
            buffer.writeEnum(endPlacement);
            buffer.writeEnum(endSide);
            buffer.writeBoolean(coherent);
            buffer.writeVarInt(colorRgb);
            buffer.writeVarInt(widthLevel);
            buffer.writeVarInt(visualLevel);
            buffer.writeDouble(startRadius);
            buffer.writeDouble(endRadius);
        }
    }
}
