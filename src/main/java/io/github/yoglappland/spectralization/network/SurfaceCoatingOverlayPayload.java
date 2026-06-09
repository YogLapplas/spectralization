package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SurfaceCoatingOverlayPayload(
        List<SurfaceInspectionResponsePayload> surfaces
) implements CustomPacketPayload {
    private static final int MAX_SURFACES = 512;
    public static final Type<SurfaceCoatingOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "surface_coating_overlay")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SurfaceCoatingOverlayPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SurfaceCoatingOverlayPayload::write, SurfaceCoatingOverlayPayload::read);

    public SurfaceCoatingOverlayPayload {
        surfaces = List.copyOf(surfaces);
    }

    private static SurfaceCoatingOverlayPayload read(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(MAX_SURFACES, buffer.readVarInt());
        List<SurfaceInspectionResponsePayload> surfaces = new ArrayList<>(count);

        for (int index = 0; index < count; index++) {
            surfaces.add(SurfaceInspectionResponsePayload.readFrom(buffer));
        }

        return new SurfaceCoatingOverlayPayload(surfaces);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(MAX_SURFACES, surfaces.size());
        buffer.writeVarInt(count);

        for (int index = 0; index < count; index++) {
            surfaces.get(index).writeTo(buffer);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
