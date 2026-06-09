package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SurfaceInspectionRequestPayload(
        boolean hasTarget,
        BlockPos pos,
        Direction side
) implements CustomPacketPayload {
    public static final Type<SurfaceInspectionRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "surface_inspection_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SurfaceInspectionRequestPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SurfaceInspectionRequestPayload::write, SurfaceInspectionRequestPayload::read);

    private static SurfaceInspectionRequestPayload read(RegistryFriendlyByteBuf buffer) {
        return new SurfaceInspectionRequestPayload(buffer.readBoolean(), buffer.readBlockPos(), buffer.readEnum(Direction.class));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(hasTarget);
        buffer.writeBlockPos(pos);
        buffer.writeEnum(side);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
