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
        int brightnessLevel,
        int radiusLevel,
        int colorBin
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
                spot.brightnessLevel(),
                spot.radiusLevel(),
                spot.colorBin()
        );
    }

    public SpotRecord toSpotRecord() {
        return new SpotRecord(pos, face, brightnessLevel, radiusLevel, colorBin);
    }

    private static SpotUpdatePayload read(RegistryFriendlyByteBuf buffer) {
        return new SpotUpdatePayload(
                buffer.readBlockPos(),
                buffer.readEnum(Direction.class),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte(),
                buffer.readUnsignedByte()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(face);
        buffer.writeByte(brightnessLevel);
        buffer.writeByte(radiusLevel);
        buffer.writeByte(colorBin);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
