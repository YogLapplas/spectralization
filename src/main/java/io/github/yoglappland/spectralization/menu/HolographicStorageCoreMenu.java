package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.HolographicStorageMultiblock;
import io.github.yoglappland.spectralization.blockentity.HolographicStorageMainCoreBlockEntity;
import io.github.yoglappland.spectralization.network.HolographicStorageSnapshotPayload;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import io.github.yoglappland.spectralization.storage.HolographicStorageBlockEntry;
import io.github.yoglappland.spectralization.storage.HolographicStorageCapacity;
import io.github.yoglappland.spectralization.storage.HolographicStorageData;
import io.github.yoglappland.spectralization.storage.HolographicStorageEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class HolographicStorageCoreMenu extends AbstractContainerMenu {
    private final HolographicStorageMainCoreBlockEntity core;
    private final ServerPlayer viewer;
    private List<HolographicStorageEntry> clientEntries = List.of();
    private long clientStoredItems;
    private long clientMaxItems;
    private int clientStoredTypes;
    private int clientMaxTypes;
    private int clientCrystals;
    private int clientExposedFaces;
    private int clientChannelMultiplier = 1;
    private boolean clientOverCapacity;
    private boolean clientInteractionLocked;
    private boolean clientStructureError;
    private List<HolographicStorageBlockEntry> clientBlockEntries = List.of();
    private long lastPeriodicSnapshotTick = Long.MIN_VALUE;

    public HolographicStorageCoreMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public HolographicStorageCoreMenu(
            int containerId,
            Inventory inventory,
            HolographicStorageMainCoreBlockEntity core
    ) {
        super(SpectralMenus.HOLOGRAPHIC_STORAGE_CORE.get(), containerId);
        this.core = core;
        this.viewer = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;

        if (viewer != null) {
            sendSnapshot();
        }
    }

    public List<HolographicStorageEntry> entries() {
        return clientEntries;
    }

    public long storedItems() {
        return clientStoredItems;
    }

    public long maxItems() {
        return clientMaxItems;
    }

    public int storedTypes() {
        return clientStoredTypes;
    }

    public int maxTypes() {
        return clientMaxTypes;
    }

    public int crystals() {
        return clientCrystals;
    }

    public int exposedFaces() {
        return clientExposedFaces;
    }

    public int channelMultiplier() {
        return clientChannelMultiplier;
    }

    public boolean overCapacity() {
        return clientOverCapacity;
    }

    public boolean interactionLocked() {
        return clientInteractionLocked;
    }

    public boolean structureError() {
        return clientStructureError;
    }

    public List<HolographicStorageBlockEntry> blockEntries() {
        return clientBlockEntries;
    }

    public void applySnapshot(HolographicStorageSnapshotPayload payload) {
        if (payload.containerId() != containerId) {
            return;
        }

        clientEntries = payload.entries();
        clientStoredItems = payload.storedItems();
        clientMaxItems = payload.maxItems();
        clientStoredTypes = payload.storedTypes();
        clientMaxTypes = payload.maxTypes();
        clientCrystals = payload.crystals();
        clientExposedFaces = payload.exposedFaces();
        clientChannelMultiplier = payload.channelMultiplier();
        clientOverCapacity = payload.overCapacity();
        clientInteractionLocked = payload.interactionLocked();
        clientStructureError = payload.structureError();
        clientBlockEntries = payload.blockEntries();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        sendPeriodicSnapshot();
    }

    @Override
    public boolean stillValid(Player player) {
        if (core == null || core.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(core.getBlockPos())) <= 64.0
                && core.getLevel() != null
                && core.getLevel().getBlockState(core.getBlockPos()).is(Spectralization.HOLOGRAPHIC_STORAGE_MAIN_CORE.get());
    }

    private void sendSnapshot() {
        if (viewer == null || core == null || core.getLevel() == null) {
            return;
        }

        HolographicStorageMultiblock.StructureReport report =
                HolographicStorageMultiblock.scan(core.getLevel(), core.getBlockPos());
        HolographicStorageCapacity capacity = capacity(report);
        HolographicStorageData.Snapshot snapshot =
                HolographicStorageData.snapshot(core.getLevel(), storageId(), capacity);
        PacketDistributor.sendToPlayer(viewer, new HolographicStorageSnapshotPayload(
                containerId,
                snapshot.storedItems(),
                snapshot.maxItems(),
                snapshot.storedTypes(),
                snapshot.maxTypes(),
                snapshot.crystals(),
                snapshot.exposedFaces(),
                snapshot.channelMultiplier(),
                snapshot.overCapacity(),
                snapshot.interactionLocked(),
                snapshot.structureError(),
                blockEntries(report),
                snapshot.entries()
        ));
    }

    private void sendPeriodicSnapshot() {
        if (core == null || core.getLevel() == null) {
            return;
        }

        long gameTime = core.getLevel().getGameTime();
        if (lastPeriodicSnapshotTick != Long.MIN_VALUE
                && (gameTime == lastPeriodicSnapshotTick || gameTime % 10L != 0L)) {
            return;
        }

        lastPeriodicSnapshotTick = gameTime;
        sendSnapshot();
    }

    private UUID storageId() {
        return core.storageId();
    }

    private HolographicStorageCapacity capacity() {
        if (core == null || core.getLevel() == null) {
            return HolographicStorageCapacity.EMPTY;
        }

        HolographicStorageMultiblock.StructureReport report =
                HolographicStorageMultiblock.scan(core.getLevel(), core.getBlockPos());
        return capacity(report);
    }

    private HolographicStorageCapacity capacity(HolographicStorageMultiblock.StructureReport report) {
        return HolographicStorageCapacity.fromStructure(
                report.storageCrystalCount(),
                report.exposedCrystalFaces(),
                core.registeredChannelMultiplier(),
                report.error()
        );
    }

    private static List<HolographicStorageBlockEntry> blockEntries(
            HolographicStorageMultiblock.StructureReport report
    ) {
        return report.blockCounts().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getDescriptionId()))
                .map(HolographicStorageCoreMenu::blockEntry)
                .toList();
    }

    private static HolographicStorageBlockEntry blockEntry(Map.Entry<Block, Integer> entry) {
        return new HolographicStorageBlockEntry(entry.getKey().getDescriptionId(), entry.getValue());
    }
}
