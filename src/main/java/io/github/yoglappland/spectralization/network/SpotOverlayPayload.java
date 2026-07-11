package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.SpotRecord.ProjectionMode;
import io.github.yoglappland.spectralization.optics.SpotProjectionLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpotOverlayPayload(
        int ownerId,
        long snapshotToken,
        int chunkIndex,
        int chunkCount,
        int totalSpots,
        List<SpotRecord> spots
) implements CustomPacketPayload {
    public static final Type<SpotOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "spot_overlay")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SpotOverlayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpotOverlayPayload::write, SpotOverlayPayload::read);

    public SpotOverlayPayload {
        spots = List.copyOf(spots);
        if (totalSpots < 0 || totalSpots > SpotProjectionLimits.MAX_SPOTS_PER_OWNER) {
            throw new IllegalArgumentException("Spot snapshot total is outside the protocol limit: " + totalSpots);
        }
        int expectedChunkCount = Math.max(1, (totalSpots + SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK - 1)
                / SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK);
        if (chunkCount != expectedChunkCount
                || chunkCount > SpotProjectionLimits.MAX_PAYLOAD_CHUNKS
                || chunkIndex < 0
                || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("Invalid spot snapshot chunk coordinates");
        }
        int expectedChunkSize = totalSpots == 0
                ? 0
                : Math.min(
                        SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK,
                        totalSpots - chunkIndex * SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK
                );
        if (spots.size() != expectedChunkSize) {
            throw new IllegalArgumentException("Invalid spot snapshot chunk size");
        }
    }

    public static List<SpotOverlayPayload> chunks(int ownerId, long snapshotToken, List<SpotRecord> spots) {
        if (spots.size() > SpotProjectionLimits.MAX_SPOTS_PER_OWNER) {
            throw new IllegalArgumentException("Spot snapshot exceeds the protocol limit: " + spots.size());
        }
        int total = spots.size();
        int chunkCount = Math.max(1, (total + SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK - 1)
                / SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK);
        List<SpotOverlayPayload> payloads = new ArrayList<>(chunkCount);
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int from = chunkIndex * SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK;
            int to = Math.min(total, from + SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK);
            payloads.add(new SpotOverlayPayload(
                    ownerId,
                    snapshotToken,
                    chunkIndex,
                    chunkCount,
                    total,
                    spots.subList(from, to)
            ));
        }
        return List.copyOf(payloads);
    }

    public static SpotOverlayPayload clear(int ownerId, long snapshotToken) {
        return new SpotOverlayPayload(ownerId, snapshotToken, 0, 1, 0, List.of());
    }

    private static SpotOverlayPayload read(RegistryFriendlyByteBuf buffer) {
        int ownerId = buffer.readVarInt();
        long snapshotToken = buffer.readVarLong();
        int chunkIndex = buffer.readVarInt();
        int chunkCount = buffer.readVarInt();
        int totalSpots = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK) {
            throw new IllegalArgumentException("Spot payload chunk exceeds protocol limit: " + count);
        }
        List<SpotRecord> spots = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            spots.add(readSpot(buffer));
        }

        return new SpotOverlayPayload(ownerId, snapshotToken, chunkIndex, chunkCount, totalSpots, spots);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(ownerId);
        buffer.writeVarLong(snapshotToken);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        buffer.writeVarInt(totalSpots);
        int count = spots.size();
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
