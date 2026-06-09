package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.SpotRecord;
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
        int ringAlphaLevel
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
                spot.ringAlphaLevel()
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
                ringAlphaLevel
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
                    buffer.readUnsignedByte()
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
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
