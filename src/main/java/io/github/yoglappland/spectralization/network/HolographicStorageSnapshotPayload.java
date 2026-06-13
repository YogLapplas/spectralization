package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.storage.HolographicStorageBlockEntry;
import io.github.yoglappland.spectralization.storage.HolographicStorageEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record HolographicStorageSnapshotPayload(
        int containerId,
        long storedItems,
        long maxItems,
        int storedTypes,
        int maxTypes,
        int crystals,
        int exposedFaces,
        int channelMultiplier,
        boolean overCapacity,
        boolean interactionLocked,
        boolean structureError,
        List<HolographicStorageBlockEntry> blockEntries,
        List<HolographicStorageEntry> entries
) implements CustomPacketPayload {
    public static final Type<HolographicStorageSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "holographic_storage_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HolographicStorageSnapshotPayload> STREAM_CODEC =
            CustomPacketPayload.codec(HolographicStorageSnapshotPayload::write, HolographicStorageSnapshotPayload::read);

    public HolographicStorageSnapshotPayload {
        blockEntries = List.copyOf(blockEntries);
        entries = List.copyOf(entries);
    }

    private static HolographicStorageSnapshotPayload read(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        long storedItems = buffer.readVarLong();
        long maxItems = buffer.readVarLong();
        int storedTypes = buffer.readVarInt();
        int maxTypes = buffer.readVarInt();
        int crystals = buffer.readVarInt();
        int exposedFaces = buffer.readVarInt();
        int channelMultiplier = buffer.readVarInt();
        boolean overCapacity = buffer.readBoolean();
        boolean interactionLocked = buffer.readBoolean();
        boolean structureError = buffer.readBoolean();
        int blockEntryCount = buffer.readVarInt();
        List<HolographicStorageBlockEntry> blockEntries = new ArrayList<>(blockEntryCount);

        for (int index = 0; index < blockEntryCount; index++) {
            String descriptionId = buffer.readUtf();
            int count = buffer.readVarInt();
            if (!descriptionId.isBlank() && count > 0) {
                blockEntries.add(new HolographicStorageBlockEntry(descriptionId, count));
            }
        }

        int entryCount = buffer.readVarInt();
        List<HolographicStorageEntry> entries = new ArrayList<>(entryCount);

        for (int index = 0; index < entryCount; index++) {
            ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
            long count = buffer.readVarLong();
            if (!stack.isEmpty() && count > 0) {
                entries.add(new HolographicStorageEntry(stack, count));
            }
        }

        return new HolographicStorageSnapshotPayload(
                containerId,
                storedItems,
                maxItems,
                storedTypes,
                maxTypes,
                crystals,
                exposedFaces,
                channelMultiplier,
                overCapacity,
                interactionLocked,
                structureError,
                blockEntries,
                entries
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarLong(storedItems);
        buffer.writeVarLong(maxItems);
        buffer.writeVarInt(storedTypes);
        buffer.writeVarInt(maxTypes);
        buffer.writeVarInt(crystals);
        buffer.writeVarInt(exposedFaces);
        buffer.writeVarInt(channelMultiplier);
        buffer.writeBoolean(overCapacity);
        buffer.writeBoolean(interactionLocked);
        buffer.writeBoolean(structureError);
        buffer.writeVarInt(blockEntries.size());

        for (HolographicStorageBlockEntry entry : blockEntries) {
            buffer.writeUtf(entry.descriptionId());
            buffer.writeVarInt(entry.count());
        }

        buffer.writeVarInt(entries.size());

        for (HolographicStorageEntry entry : entries) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.stack());
            buffer.writeVarLong(entry.count());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
