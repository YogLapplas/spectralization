package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.OpticalMaterialResponse;
import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SurfaceInspectionResponsePayload(
        BlockPos pos,
        Direction side,
        boolean present,
        int treatmentOrdinal,
        int transmittancePermille,
        int reflectancePermille,
        int absorptionPermille
) implements CustomPacketPayload {
    public static final Type<SurfaceInspectionResponsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "surface_inspection_response")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SurfaceInspectionResponsePayload> STREAM_CODEC =
            CustomPacketPayload.codec(SurfaceInspectionResponsePayload::write, SurfaceInspectionResponsePayload::read);

    public static SurfaceInspectionResponsePayload empty(BlockPos pos, Direction side) {
        return new SurfaceInspectionResponsePayload(pos, side, false, SurfaceTreatmentKind.NONE.ordinal(), 0, 0, 0);
    }

    public static SurfaceInspectionResponsePayload of(BlockPos pos, Direction side, SurfaceTreatmentKind treatmentKind) {
        OpticalMaterialResponse response = SurfaceTreatments.profileFor(treatmentKind)
                .materialProfile()
                .responseAt(FrequencyKey.DEBUG_VISIBLE);

        return new SurfaceInspectionResponsePayload(
                pos,
                side,
                true,
                treatmentKind.ordinal(),
                permille(response.transmittance()),
                permille(response.reflectance()),
                permille(response.absorption())
        );
    }

    public SurfaceTreatmentKind treatmentKind() {
        SurfaceTreatmentKind[] values = SurfaceTreatmentKind.values();

        if (treatmentOrdinal < 0 || treatmentOrdinal >= values.length) {
            return SurfaceTreatmentKind.NONE;
        }

        return values[treatmentOrdinal];
    }

    public static SurfaceInspectionResponsePayload readFrom(RegistryFriendlyByteBuf buffer) {
        return new SurfaceInspectionResponsePayload(
                buffer.readBlockPos(),
                buffer.readEnum(Direction.class),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    public void writeTo(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeEnum(side);
        buffer.writeBoolean(present);
        buffer.writeVarInt(treatmentOrdinal);
        buffer.writeVarInt(transmittancePermille);
        buffer.writeVarInt(reflectancePermille);
        buffer.writeVarInt(absorptionPermille);
    }

    private static SurfaceInspectionResponsePayload read(RegistryFriendlyByteBuf buffer) {
        return readFrom(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        writeTo(buffer);
    }

    private static int permille(double value) {
        return (int) Math.round(Math.max(0.0D, Math.min(1.0D, value)) * 1000.0D);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
