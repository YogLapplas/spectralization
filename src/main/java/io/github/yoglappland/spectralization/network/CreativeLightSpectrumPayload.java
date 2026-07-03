package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreativeLightSpectrumPayload(int containerId, int bin, int weight, boolean exclusive) implements CustomPacketPayload {
    public static final Type<CreativeLightSpectrumPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "creative_light_spectrum")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeLightSpectrumPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CreativeLightSpectrumPayload::write, CreativeLightSpectrumPayload::read);

    public CreativeLightSpectrumPayload(int containerId, int bin, int weight) {
        this(containerId, bin, weight, false);
    }

    private static CreativeLightSpectrumPayload read(RegistryFriendlyByteBuf buffer) {
        return new CreativeLightSpectrumPayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(bin);
        buffer.writeVarInt(weight);
        buffer.writeBoolean(exclusive);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
