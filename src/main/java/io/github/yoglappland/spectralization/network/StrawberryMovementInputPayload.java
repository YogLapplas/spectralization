package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StrawberryMovementInputPayload(
        boolean holdingRod,
        boolean jumpDown,
        boolean jumpPressed,
        boolean dashPressed,
        float leftImpulse,
        float forwardImpulse
) implements CustomPacketPayload {
    public static final Type<StrawberryMovementInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "strawberry_movement_input")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, StrawberryMovementInputPayload> STREAM_CODEC =
            CustomPacketPayload.codec(StrawberryMovementInputPayload::write, StrawberryMovementInputPayload::read);

    private static StrawberryMovementInputPayload read(RegistryFriendlyByteBuf buffer) {
        return new StrawberryMovementInputPayload(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(holdingRod);
        buffer.writeBoolean(jumpDown);
        buffer.writeBoolean(jumpPressed);
        buffer.writeBoolean(dashPressed);
        buffer.writeFloat(leftImpulse);
        buffer.writeFloat(forwardImpulse);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
