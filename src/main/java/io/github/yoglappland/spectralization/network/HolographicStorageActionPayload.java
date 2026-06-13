package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HolographicStorageActionPayload(
        int containerId,
        int action,
        int entryIndex,
        int amount
) implements CustomPacketPayload {
    public static final int ACTION_EXTRACT_TO_CARRIED = 0;
    public static final int ACTION_INSERT_CARRIED = 1;
    public static final int ACTION_EXTRACT_TO_INVENTORY = 2;

    public static final Type<HolographicStorageActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "holographic_storage_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HolographicStorageActionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(HolographicStorageActionPayload::write, HolographicStorageActionPayload::read);

    private static HolographicStorageActionPayload read(RegistryFriendlyByteBuf buffer) {
        return new HolographicStorageActionPayload(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(action);
        buffer.writeVarInt(entryIndex);
        buffer.writeVarInt(amount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
