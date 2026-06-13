package io.github.yoglappland.spectralization.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class HolographicStorageData extends SavedData {
    private static final String DATA_NAME = "spectralization_holographic_storage";
    private static final SavedData.Factory<HolographicStorageData> FACTORY = new SavedData.Factory<>(
            HolographicStorageData::new,
            HolographicStorageData::load,
            null
    );

    private final Map<UUID, Record> recordsById = new LinkedHashMap<>();

    public static Optional<HolographicStorageData> maybeGet(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return Optional.of(serverLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME));
        }

        return Optional.empty();
    }

    public static Snapshot snapshot(Level level, UUID storageId, HolographicStorageCapacity capacity) {
        return maybeGet(level)
                .map(data -> data.snapshot(storageId, capacity))
                .orElseGet(() -> Snapshot.empty(capacity));
    }

    public static int insert(Level level, UUID storageId, ItemStack stack, HolographicStorageCapacity capacity) {
        Optional<HolographicStorageData> maybeData = maybeGet(level);
        if (maybeData.isEmpty()) {
            return 0;
        }

        return maybeData.get().insert(storageId, stack, capacity);
    }

    public static ItemStack extract(
            Level level,
            UUID storageId,
            int entryIndex,
            int maxAmount,
            HolographicStorageCapacity capacity
    ) {
        Optional<HolographicStorageData> maybeData = maybeGet(level);
        if (maybeData.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return maybeData.get().extract(storageId, entryIndex, maxAmount, capacity);
    }

    private Snapshot snapshot(UUID storageId, HolographicStorageCapacity capacity) {
        Record record = recordsById.computeIfAbsent(storageId, ignored -> new Record());
        return record.snapshot(capacity);
    }

    private int insert(UUID storageId, ItemStack stack, HolographicStorageCapacity capacity) {
        if (stack.isEmpty() || stack.getCount() <= 0 || capacity.maxItems() <= 0 || capacity.maxTypes() <= 0) {
            return 0;
        }

        Record record = recordsById.computeIfAbsent(storageId, ignored -> new Record());
        int inserted = record.insert(stack, capacity);
        if (inserted > 0) {
            setDirty();
        }

        return inserted;
    }

    private ItemStack extract(
            UUID storageId,
            int entryIndex,
            int maxAmount,
            HolographicStorageCapacity capacity
    ) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }

        Record record = recordsById.get(storageId);
        if (record == null) {
            return ItemStack.EMPTY;
        }

        if (record.snapshot(capacity).interactionLocked()) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = record.extract(entryIndex, maxAmount);
        if (!extracted.isEmpty()) {
            setDirty();
        }

        return extracted;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag records = new ListTag();

        for (Map.Entry<UUID, Record> entry : recordsById.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            CompoundTag recordTag = new CompoundTag();
            recordTag.putString("id", entry.getKey().toString());
            entry.getValue().save(recordTag, registries);
            records.add(recordTag);
        }

        tag.put("records", records);
        return tag;
    }

    private static HolographicStorageData load(CompoundTag tag, HolderLookup.Provider registries) {
        HolographicStorageData data = new HolographicStorageData();
        ListTag records = tag.getList("records", Tag.TAG_COMPOUND);

        for (int index = 0; index < records.size(); index++) {
            CompoundTag recordTag = records.getCompound(index);
            try {
                UUID id = UUID.fromString(recordTag.getString("id"));
                Record record = Record.load(recordTag, registries);
                if (!record.isEmpty()) {
                    data.recordsById.put(id, record);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return data;
    }

    public record Snapshot(
            List<HolographicStorageEntry> entries,
            long storedItems,
            long maxItems,
            int storedTypes,
            int maxTypes,
            int crystals,
            int exposedFaces,
            int channelMultiplier,
            boolean overCapacity,
            boolean interactionLocked,
            boolean structureError
    ) {
        public static Snapshot empty(HolographicStorageCapacity capacity) {
            return new Snapshot(
                    List.of(),
                    0,
                    capacity.maxItems(),
                    0,
                    capacity.maxTypes(),
                    capacity.crystals(),
                    capacity.exposedFaces(),
                    capacity.channelMultiplier(),
                    false,
                    capacity.structureError(),
                    capacity.structureError()
            );
        }
    }

    private static final class Record {
        private static final Comparator<HolographicStorageEntry> ENTRY_COMPARATOR =
                Comparator.comparing(entry -> entry.stack().getHoverName().getString());

        private final Map<HolographicItemKey, Long> amountsByKey = new LinkedHashMap<>();

        private boolean isEmpty() {
            return amountsByKey.isEmpty();
        }

        private Snapshot snapshot(HolographicStorageCapacity capacity) {
            List<HolographicStorageEntry> entries = entries();
            long storedItems = entries.stream().mapToLong(HolographicStorageEntry::count).sum();
            boolean overCapacity = storedItems > capacity.maxItems() || entries.size() > capacity.maxTypes();
            boolean interactionLocked = capacity.structureError() || overCapacity;
            return new Snapshot(
                    entries,
                    storedItems,
                    capacity.maxItems(),
                    entries.size(),
                    capacity.maxTypes(),
                    capacity.crystals(),
                    capacity.exposedFaces(),
                    capacity.channelMultiplier(),
                    overCapacity,
                    interactionLocked,
                    capacity.structureError()
            );
        }

        private int insert(ItemStack stack, HolographicStorageCapacity capacity) {
            if (snapshot(capacity).interactionLocked()) {
                return 0;
            }

            HolographicItemKey key = HolographicItemKey.of(stack);
            long current = amountsByKey.getOrDefault(key, 0L);
            if (current <= 0 && amountsByKey.size() >= capacity.maxTypes()) {
                return 0;
            }

            long storedItems = amountsByKey.values().stream().mapToLong(Long::longValue).sum();
            long remaining = capacity.maxItems() - storedItems;
            if (remaining <= 0) {
                return 0;
            }

            int inserted = (int) Math.min(stack.getCount(), Math.min(Integer.MAX_VALUE, remaining));
            if (inserted <= 0) {
                return 0;
            }

            amountsByKey.put(key, current + inserted);
            return inserted;
        }

        private ItemStack extract(int entryIndex, int maxAmount) {
            List<Map.Entry<HolographicItemKey, Long>> entries = sortedMapEntries();
            if (entryIndex < 0 || entryIndex >= entries.size()) {
                return ItemStack.EMPTY;
            }

            Map.Entry<HolographicItemKey, Long> entry = entries.get(entryIndex);
            long stored = entry.getValue();
            if (stored <= 0) {
                return ItemStack.EMPTY;
            }

            ItemStack template = entry.getKey().stack();
            int extractedCount = (int) Math.min(stored, Math.min(maxAmount, template.getMaxStackSize()));
            if (extractedCount <= 0) {
                return ItemStack.EMPTY;
            }

            long remaining = stored - extractedCount;
            if (remaining <= 0) {
                amountsByKey.remove(entry.getKey());
            } else {
                amountsByKey.put(entry.getKey(), remaining);
            }

            return template.copyWithCount(extractedCount);
        }

        private void save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag entries = new ListTag();

            for (Map.Entry<HolographicItemKey, Long> entry : amountsByKey.entrySet()) {
                long count = entry.getValue();
                if (count <= 0) {
                    continue;
                }

                CompoundTag entryTag = new CompoundTag();
                entryTag.put("stack", entry.getKey().stack().saveOptional(registries));
                entryTag.putLong("count", count);
                entries.add(entryTag);
            }

            tag.put("entries", entries);
        }

        private static Record load(CompoundTag tag, HolderLookup.Provider registries) {
            Record record = new Record();
            ListTag entries = tag.getList("entries", Tag.TAG_COMPOUND);

            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entryTag = entries.getCompound(index);
                ItemStack stack = ItemStack.parseOptional(registries, entryTag.getCompound("stack"));
                long count = entryTag.getLong("count");

                if (!stack.isEmpty() && count > 0) {
                    HolographicItemKey key = HolographicItemKey.of(stack);
                    record.amountsByKey.merge(key, count, Long::sum);
                }
            }

            return record;
        }

        private List<HolographicStorageEntry> entries() {
            return sortedMapEntries().stream()
                    .map(entry -> new HolographicStorageEntry(entry.getKey().stack(), entry.getValue()))
                    .toList();
        }

        private List<Map.Entry<HolographicItemKey, Long>> sortedMapEntries() {
            List<Map.Entry<HolographicItemKey, Long>> entries = new ArrayList<>(amountsByKey.entrySet());
            entries.sort(Comparator.comparing(entry -> new HolographicStorageEntry(
                    entry.getKey().stack(),
                    entry.getValue()
            ), ENTRY_COMPARATOR));
            return entries;
        }
    }
}
