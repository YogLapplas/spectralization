package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreativeLightPowerPayload(int containerId, int powerCenti) implements CustomPacketPayload {
    public static final Type<CreativeLightPowerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "creative_light_power")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeLightPowerPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CreativeLightPowerPayload::write, CreativeLightPowerPayload::read);

    private static CreativeLightPowerPayload read(RegistryFriendlyByteBuf buffer) {
        return new CreativeLightPowerPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(powerCenti);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
