package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpotTestLoadTogglePayload() implements CustomPacketPayload {
    public static final Type<SpotTestLoadTogglePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "spot_test_load_toggle")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SpotTestLoadTogglePayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpotTestLoadTogglePayload::write, SpotTestLoadTogglePayload::read);

    private static SpotTestLoadTogglePayload read(RegistryFriendlyByteBuf buffer) {
        return new SpotTestLoadTogglePayload();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
