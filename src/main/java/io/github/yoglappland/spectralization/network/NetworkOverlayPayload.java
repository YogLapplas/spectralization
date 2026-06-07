package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NetworkOverlayPayload(
        boolean visible,
        List<BlockPos> positions
) implements CustomPacketPayload {
    private static final int MAX_POSITIONS = 16_384;
    public static final Type<NetworkOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "network_overlay")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkOverlayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(NetworkOverlayPayload::write, NetworkOverlayPayload::read);

    public NetworkOverlayPayload {
        positions = List.copyOf(positions);
    }

    private static NetworkOverlayPayload read(RegistryFriendlyByteBuf buffer) {
        boolean visible = buffer.readBoolean();
        int count = Math.min(MAX_POSITIONS, buffer.readVarInt());
        List<BlockPos> positions = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            positions.add(buffer.readBlockPos());
        }

        return new NetworkOverlayPayload(visible, positions);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(MAX_POSITIONS, positions.size());
        buffer.writeBoolean(visible);
        buffer.writeVarInt(count);

        for (int index = 0; index < count; index++) {
            buffer.writeBlockPos(positions.get(index));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
