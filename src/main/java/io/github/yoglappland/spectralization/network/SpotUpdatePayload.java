package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.SpotRecord.ProjectionMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpotUpdatePayload(
        BlockPos pos,
        Direction face,
        int coherentAlphaLevel,
        int coherentRadiusLevel,
        int coherentRed,
        int coherentGreen,
        int coherentBlue,
        int strayAlphaLevel,
        int strayRadiusLevel,
        int strayRed,
        int strayGreen,
        int strayBlue,
        int ringAlphaLevel,
        ProjectionMode projectionMode,
        int clipMinU,
        int clipMinV,
        int clipMaxU,
        int clipMaxV,
        int textureMinU,
        int textureMinV,
        int textureMaxU,
        int textureMaxV,
        int quadX0,
        int quadY0,
        int quadZ0,
        int quadTextureU0,
        int quadTextureV0,
        int quadX1,
        int quadY1,
        int quadZ1,
        int quadTextureU1,
        int quadTextureV1,
        int quadX2,
        int quadY2,
        int quadZ2,
        int quadTextureU2,
        int quadTextureV2,
        int quadX3,
        int quadY3,
        int quadZ3,
        int quadTextureU3,
        int quadTextureV3,
        int debugMarker
) implements CustomPacketPayload {
    public static final Type<SpotUpdatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "spot_update")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SpotUpdatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpotUpdatePayload::write, SpotUpdatePayload::read);

    public static SpotUpdatePayload fromSpot(SpotRecord spot) {
        return new SpotUpdatePayload(
                spot.pos(),
                spot.face(),
                spot.coherentAlphaLevel(),
                spot.coherentRadiusLevel(),
                spot.coherentRed(),
                spot.coherentGreen(),
                spot.coherentBlue(),
                spot.strayAlphaLevel(),
                spot.strayRadiusLevel(),
                spot.strayRed(),
                spot.strayGreen(),
                spot.strayBlue(),
                spot.ringAlphaLevel(),
                spot.projectionMode(),
                spot.clipMinU(),
                spot.clipMinV(),
                spot.clipMaxU(),
                spot.clipMaxV(),
                spot.textureMinU(),
                spot.textureMinV(),
                spot.textureMaxU(),
                spot.textureMaxV(),
                spot.quadX0(),
                spot.quadY0(),
                spot.quadZ0(),
                spot.quadTextureU0(),
                spot.quadTextureV0(),
                spot.quadX1(),
                spot.quadY1(),
                spot.quadZ1(),
                spot.quadTextureU1(),
                spot.quadTextureV1(),
                spot.quadX2(),
                spot.quadY2(),
                spot.quadZ2(),
                spot.quadTextureU2(),
                spot.quadTextureV2(),
                spot.quadX3(),
                spot.quadY3(),
                spot.quadZ3(),
                spot.quadTextureU3(),
                spot.quadTextureV3(),
                spot.debugMarker()
        );
    }

    public SpotRecord toSpotRecord() {
        return new SpotRecord(
                pos,
                face,
                coherentAlphaLevel,
                coherentRadiusLevel,
                coherentRed,
                coherentGreen,
                coherentBlue,
                strayAlphaLevel,
                strayRadiusLevel,
                strayRed,
                strayGreen,
                strayBlue,
                ringAlphaLevel,
                projectionMode,
                clipMinU,
                clipMinV,
                clipMaxU,
                clipMaxV,
                textureMinU,
                textureMinV,
                textureMaxU,
                textureMaxV,
                quadX0,
                quadY0,
                quadZ0,
                quadTextureU0,
                quadTextureV0,
                quadX1,
                quadY1,
                quadZ1,
                quadTextureU1,
                quadTextureV1,
                quadX2,
                quadY2,
                quadZ2,
                quadTextureU2,
                quadTextureV2,
                quadX3,
                quadY3,
                quadZ3,
                quadTextureU3,
                quadTextureV3,
                debugMarker
        );
    }

    private static SpotUpdatePayload read(RegistryFriendlyByteBuf buffer) {
        return new SpotUpdatePayload(
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

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(face);
        buffer.writeByte(coherentAlphaLevel);
        buffer.writeByte(coherentRadiusLevel);
        buffer.writeByte(coherentRed);
        buffer.writeByte(coherentGreen);
        buffer.writeByte(coherentBlue);
        buffer.writeByte(strayAlphaLevel);
        buffer.writeByte(strayRadiusLevel);
        buffer.writeByte(strayRed);
        buffer.writeByte(strayGreen);
        buffer.writeByte(strayBlue);
        buffer.writeByte(ringAlphaLevel);
        buffer.writeEnum(projectionMode);
        buffer.writeByte(clipMinU);
        buffer.writeByte(clipMinV);
        buffer.writeByte(clipMaxU);
        buffer.writeByte(clipMaxV);
        buffer.writeByte(textureMinU);
        buffer.writeByte(textureMinV);
        buffer.writeByte(textureMaxU);
        buffer.writeByte(textureMaxV);
        buffer.writeVarInt(quadX0);
        buffer.writeVarInt(quadY0);
        buffer.writeVarInt(quadZ0);
        buffer.writeVarInt(quadTextureU0);
        buffer.writeVarInt(quadTextureV0);
        buffer.writeVarInt(quadX1);
        buffer.writeVarInt(quadY1);
        buffer.writeVarInt(quadZ1);
        buffer.writeVarInt(quadTextureU1);
        buffer.writeVarInt(quadTextureV1);
        buffer.writeVarInt(quadX2);
        buffer.writeVarInt(quadY2);
        buffer.writeVarInt(quadZ2);
        buffer.writeVarInt(quadTextureU2);
        buffer.writeVarInt(quadTextureV2);
        buffer.writeVarInt(quadX3);
        buffer.writeVarInt(quadY3);
        buffer.writeVarInt(quadZ3);
        buffer.writeVarInt(quadTextureU3);
        buffer.writeVarInt(quadTextureV3);
        buffer.writeVarInt(debugMarker);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
