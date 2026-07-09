package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.SpotRecord.ProjectionMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpotOverlayPayload(int ownerId, List<SpotRecord> spots) implements CustomPacketPayload {
    private static final int MAX_SPOTS = 8192;
    public static final Type<SpotOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "spot_overlay")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SpotOverlayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpotOverlayPayload::write, SpotOverlayPayload::read);

    public SpotOverlayPayload {
        spots = spots.size() > MAX_SPOTS ? List.copyOf(spots.subList(0, MAX_SPOTS)) : List.copyOf(spots);
    }

    private static SpotOverlayPayload read(RegistryFriendlyByteBuf buffer) {
        int ownerId = buffer.readVarInt();
        int count = Math.min(MAX_SPOTS, buffer.readVarInt());
        List<SpotRecord> spots = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            spots.add(readSpot(buffer));
        }

        return new SpotOverlayPayload(ownerId, spots);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(ownerId);
        int count = Math.min(MAX_SPOTS, spots.size());
        buffer.writeVarInt(count);

        for (int index = 0; index < count; index++) {
            writeSpot(buffer, spots.get(index));
        }
    }

    private static SpotRecord readSpot(RegistryFriendlyByteBuf buffer) {
        return new SpotRecord(
                buffer.readBlockPos(),
                buffer.readEnum(Direction.class),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readEnum(ProjectionMode.class),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private static void writeSpot(RegistryFriendlyByteBuf buffer, SpotRecord spot) {
        buffer.writeBlockPos(spot.pos());
        buffer.writeEnum(spot.face());
        buffer.writeByte(spot.coherentAlphaLevel());
        buffer.writeByte(spot.coherentRadiusLevel());
        buffer.writeByte(spot.coherentRed());
        buffer.writeByte(spot.coherentGreen());
        buffer.writeByte(spot.coherentBlue());
        buffer.writeByte(spot.strayAlphaLevel());
        buffer.writeByte(spot.strayRadiusLevel());
        buffer.writeByte(spot.strayRed());
        buffer.writeByte(spot.strayGreen());
        buffer.writeByte(spot.strayBlue());
        buffer.writeByte(spot.ringAlphaLevel());
        buffer.writeEnum(spot.projectionMode());
        buffer.writeByte(spot.clipMinU());
        buffer.writeByte(spot.clipMinV());
        buffer.writeByte(spot.clipMaxU());
        buffer.writeByte(spot.clipMaxV());
        buffer.writeByte(spot.textureMinU());
        buffer.writeByte(spot.textureMinV());
        buffer.writeByte(spot.textureMaxU());
        buffer.writeByte(spot.textureMaxV());
        buffer.writeVarInt(spot.quadX0());
        buffer.writeVarInt(spot.quadY0());
        buffer.writeVarInt(spot.quadZ0());
        buffer.writeVarInt(spot.quadTextureU0());
        buffer.writeVarInt(spot.quadTextureV0());
        buffer.writeVarInt(spot.quadX1());
        buffer.writeVarInt(spot.quadY1());
        buffer.writeVarInt(spot.quadZ1());
        buffer.writeVarInt(spot.quadTextureU1());
        buffer.writeVarInt(spot.quadTextureV1());
        buffer.writeVarInt(spot.quadX2());
        buffer.writeVarInt(spot.quadY2());
        buffer.writeVarInt(spot.quadZ2());
        buffer.writeVarInt(spot.quadTextureU2());
        buffer.writeVarInt(spot.quadTextureV2());
        buffer.writeVarInt(spot.quadX3());
        buffer.writeVarInt(spot.quadY3());
        buffer.writeVarInt(spot.quadZ3());
        buffer.writeVarInt(spot.quadTextureU3());
        buffer.writeVarInt(spot.quadTextureV3());
        buffer.writeVarInt(spot.debugMarker());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
