package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HolographicStorageMainCoreBlockEntity extends BlockEntity {
    private static final String STORAGE_ID_TAG = "StorageId";
    private static final String STACK_STORAGE_ID_TAG = "spectralization_holographic_storage_id";
    private static final String REGISTERED_CHANNELS_TAG = "RegisteredChannels";
    private static final String STACK_REGISTERED_CHANNELS_TAG = "spectralization_holographic_storage_channels";
    private static final int MAX_CHANNELS = 4096;

    private UUID storageId;
    private final Set<Integer> registeredChannels = new HashSet<>();

    public HolographicStorageMainCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.HOLOGRAPHIC_STORAGE_MAIN_CORE.get(), pos, blockState);
    }

    public UUID storageId() {
        if (storageId == null) {
            storageId = UUID.randomUUID();
            setChanged();
        }

        return storageId;
    }

    public int registeredChannelMultiplier() {
        return Math.min(MAX_CHANNELS, 1 + registeredChannels.size());
    }

    public int registeredChannelCount() {
        return registeredChannels.size();
    }

    public boolean hasRegisteredChannel(int channelIndex) {
        return registeredChannels.contains(normalizeChannelIndex(channelIndex));
    }

    public boolean registerChannel(int channelIndex) {
        int normalized = normalizeChannelIndex(channelIndex);
        if (!registeredChannels.add(normalized)) {
            return false;
        }

        setChangedAndSync();
        return true;
    }

    public void loadFromStack(ItemStack stack) {
        storageIdFromStack(stack).ifPresent(id -> {
            storageId = id;
            setChanged();
        });
        channelsFromStack(stack).ifPresent(channels -> {
            registeredChannels.clear();
            for (int channel : channels) {
                registeredChannels.add(normalizeChannelIndex(channel));
            }
            setChanged();
        });
    }

    public void saveToStack(ItemStack stack) {
        UUID id = storageId();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putUUID(STACK_STORAGE_ID_TAG, id);
            tag.putIntArray(STACK_REGISTERED_CHANNELS_TAG, registeredChannelArray());
        });
    }

    public static Optional<UUID> storageIdFromStack(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        if (!tag.hasUUID(STACK_STORAGE_ID_TAG)) {
            return Optional.empty();
        }

        return Optional.of(tag.getUUID(STACK_STORAGE_ID_TAG));
    }

    public static Optional<int[]> channelsFromStack(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        if (!tag.contains(STACK_REGISTERED_CHANNELS_TAG)) {
            return Optional.empty();
        }

        return Optional.of(tag.getIntArray(STACK_REGISTERED_CHANNELS_TAG));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(STORAGE_ID_TAG)) {
            storageId = tag.getUUID(STORAGE_ID_TAG);
        }

        registeredChannels.clear();
        if (tag.contains(REGISTERED_CHANNELS_TAG)) {
            for (int channel : tag.getIntArray(REGISTERED_CHANNELS_TAG)) {
                registeredChannels.add(normalizeChannelIndex(channel));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (storageId != null) {
            tag.putUUID(STORAGE_ID_TAG, storageId);
        }
        tag.putIntArray(REGISTERED_CHANNELS_TAG, registeredChannelArray());
    }

    private int[] registeredChannelArray() {
        int[] channels = registeredChannels.stream()
                .mapToInt(Integer::intValue)
                .toArray();
        Arrays.sort(channels);
        return channels;
    }

    private static int normalizeChannelIndex(int channelIndex) {
        return Math.floorMod(channelIndex, MAX_CHANNELS);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }
}
