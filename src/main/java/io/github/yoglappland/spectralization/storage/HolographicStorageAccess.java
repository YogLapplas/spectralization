package io.github.yoglappland.spectralization.storage;

import io.github.yoglappland.spectralization.block.HolographicStorageMultiblock;
import io.github.yoglappland.spectralization.blockentity.HolographicStorageShellBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class HolographicStorageAccess {
    private static final Comparator<HolographicStorageEntry> ENTRY_COMPARATOR =
            Comparator.comparing(entry -> entry.stack().getHoverName().getString());
    private static final Comparator<BlockPos> BLOCK_POS_COMPARATOR = Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .thenComparingInt(pos -> pos.getZ())
            .thenComparingInt(pos -> pos.getX());

    private HolographicStorageAccess() {
    }

    public static HolographicStorageData.Snapshot snapshot(
            Level level,
            UUID storageId,
            HolographicStorageCapacity capacity,
            HolographicStorageMultiblock.StructureReport report
    ) {
        HolographicStorageData.Snapshot general = HolographicStorageData.snapshot(level, storageId, capacity);
        List<HolographicStorageShellBlockEntity> shells = shells(level, report);
        Map<HolographicItemKey, Long> amounts = new LinkedHashMap<>();

        for (HolographicStorageEntry entry : general.entries()) {
            amounts.merge(HolographicItemKey.of(entry.stack()), entry.count(), Long::sum);
        }

        for (HolographicStorageShellBlockEntity shell : shells) {
            if (shell.hasStoredItem()) {
                amounts.merge(HolographicItemKey.of(shell.templateStack()), (long) shell.storedCount(), Long::sum);
            }
        }

        List<HolographicStorageEntry> entries = amounts.entrySet().stream()
                .map(entry -> new HolographicStorageEntry(entry.getKey().stack(), entry.getValue()))
                .sorted(ENTRY_COMPARATOR)
                .toList();
        long storedItems = entries.stream().mapToLong(HolographicStorageEntry::count).sum();
        long shellCapacity = saturatedShellCapacity(shells.size());
        long maxItems = saturatedAdd(general.maxItems(), shellCapacity);
        int maxTypes = saturatedAdd(general.maxTypes(), shells.size());

        return new HolographicStorageData.Snapshot(
                entries,
                storedItems,
                maxItems,
                entries.size(),
                maxTypes,
                saturatedAdd(general.crystals(), shells.size()),
                general.exposedFaces(),
                general.channelMultiplier(),
                general.overCapacity(),
                general.interactionLocked(),
                general.structureError()
        );
    }

    public static int insert(
            Level level,
            UUID storageId,
            ItemStack stack,
            HolographicStorageCapacity capacity,
            HolographicStorageMultiblock.StructureReport report
    ) {
        if (stack.isEmpty() || stack.getCount() <= 0) {
            return 0;
        }

        if (snapshot(level, storageId, capacity, report).interactionLocked()) {
            return 0;
        }

        int remaining = stack.getCount();
        List<HolographicStorageShellBlockEntity> shells = shells(level, report);
        remaining = insertIntoOccupiedShells(shells, stack, remaining);

        if (remaining > 0) {
            ItemStack generalOffer = stack.copyWithCount(remaining);
            remaining -= HolographicStorageData.insert(level, storageId, generalOffer, capacity);
        }

        if (remaining > 0) {
            remaining = insertIntoEmptyShells(shells, stack, remaining);
        }

        return stack.getCount() - remaining;
    }

    public static ItemStack extract(
            Level level,
            UUID storageId,
            int entryIndex,
            int maxAmount,
            HolographicStorageCapacity capacity,
            HolographicStorageMultiblock.StructureReport report
    ) {
        if (maxAmount <= 0) {
            return ItemStack.EMPTY;
        }

        HolographicStorageData.Snapshot snapshot = snapshot(level, storageId, capacity, report);
        if (snapshot.interactionLocked() || entryIndex < 0 || entryIndex >= snapshot.entries().size()) {
            return ItemStack.EMPTY;
        }

        ItemStack target = snapshot.entries().get(entryIndex).stack();
        HolographicItemKey key = HolographicItemKey.of(target);
        int remaining = Math.min(maxAmount, target.getMaxStackSize());
        ItemStack extracted = HolographicStorageData.extract(level, storageId, key, remaining, capacity);
        remaining -= extracted.getCount();

        if (remaining > 0) {
            for (HolographicStorageShellBlockEntity shell : shells(level, report)) {
                if (!shell.hasStoredItem() || !ItemStack.isSameItemSameComponents(shell.templateStack(), target)) {
                    continue;
                }

                ItemStack shellExtracted = shell.extract(remaining, false);
                if (!shellExtracted.isEmpty()) {
                    extracted = mergeExtracted(extracted, shellExtracted);
                    remaining -= shellExtracted.getCount();
                    if (remaining <= 0) {
                        break;
                    }
                }
            }
        }

        return extracted;
    }

    private static int insertIntoOccupiedShells(
            List<HolographicStorageShellBlockEntity> shells,
            ItemStack stack,
            int remaining
    ) {
        for (HolographicStorageShellBlockEntity shell : shells) {
            if (remaining <= 0) {
                break;
            }

            if (!shell.hasStoredItem() || !shell.canAccept(stack)) {
                continue;
            }

            remaining -= shell.insert(stack, remaining, false);
        }

        return remaining;
    }

    private static int insertIntoEmptyShells(
            List<HolographicStorageShellBlockEntity> shells,
            ItemStack stack,
            int remaining
    ) {
        for (HolographicStorageShellBlockEntity shell : shells) {
            if (remaining <= 0) {
                break;
            }

            if (!shell.isEmpty()) {
                continue;
            }

            remaining -= shell.insert(stack, remaining, false);
        }

        return remaining;
    }

    private static List<HolographicStorageShellBlockEntity> shells(
            Level level,
            HolographicStorageMultiblock.StructureReport report
    ) {
        List<BlockPos> positions = new ArrayList<>(report.positions());
        positions.sort(BLOCK_POS_COMPARATOR);
        List<HolographicStorageShellBlockEntity> shells = new ArrayList<>();

        for (BlockPos pos : positions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof HolographicStorageShellBlockEntity shell) {
                shells.add(shell);
            }
        }

        return shells;
    }

    private static ItemStack mergeExtracted(ItemStack first, ItemStack second) {
        if (first.isEmpty()) {
            return second;
        }

        first.grow(second.getCount());
        return first;
    }

    private static long saturatedShellCapacity(int shellCount) {
        if (shellCount <= 0) {
            return 0L;
        }

        long value = (long) shellCount * HolographicStorageShellBlockEntity.CAPACITY;
        return value < 0L ? Long.MAX_VALUE : value;
    }

    private static int saturatedAdd(int first, int second) {
        long value = (long) first + second;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static long saturatedAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }

        return first + second;
    }
}
