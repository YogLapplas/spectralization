package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.HolographicStorageMultiblock;
import io.github.yoglappland.spectralization.blockentity.HolographicStorageMainCoreBlockEntity;
import io.github.yoglappland.spectralization.network.HolographicStorageActionPayload;
import io.github.yoglappland.spectralization.network.HolographicStorageSnapshotPayload;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import io.github.yoglappland.spectralization.storage.HolographicStorageBlockEntry;
import io.github.yoglappland.spectralization.storage.HolographicStorageAccess;
import io.github.yoglappland.spectralization.storage.HolographicStorageCapacity;
import io.github.yoglappland.spectralization.storage.HolographicStorageData;
import io.github.yoglappland.spectralization.storage.HolographicStorageEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class HolographicStorageMenu extends RecipeBookMenu<CraftingInput, CraftingRecipe> {
    public static final int RESULT_SLOT = 0;
    private static final int CRAFT_SLOT_START = 1;
    private static final int CRAFT_SLOT_END = 10;
    private static final int PLAYER_INVENTORY_START = 10;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;
    private static final int CRAFTING_GRID_X = 80;
    private static final int CRAFTING_GRID_Y = 132;
    private static final int CRAFTING_RESULT_X = 204;
    private static final int CRAFTING_RESULT_Y = 150;
    private static final int PLAYER_INVENTORY_X = 62;
    private static final int PLAYER_INVENTORY_Y = 210;

    private final HolographicStorageMainCoreBlockEntity core;
    private final Player player;
    private final ServerPlayer viewer;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
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
    private long lastPeriodicSnapshotTick = Long.MIN_VALUE;
    private boolean placingRecipe;

    public HolographicStorageMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null);
    }

    public HolographicStorageMenu(
            int containerId,
            Inventory inventory,
            HolographicStorageMainCoreBlockEntity core
    ) {
        super(SpectralMenus.HOLOGRAPHIC_STORAGE.get(), containerId);
        this.core = core;
        this.player = inventory.player;
        this.viewer = inventory.player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        addSlot(new ResultSlot(inventory.player, craftSlots, resultSlots, 0, CRAFTING_RESULT_X, CRAFTING_RESULT_Y));
        addCraftingGrid();
        addPlayerInventory(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);

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

    public static int craftingGridX() {
        return CRAFTING_GRID_X;
    }

    public static int craftingGridY() {
        return CRAFTING_GRID_Y;
    }

    public static int craftingResultX() {
        return CRAFTING_RESULT_X;
    }

    public static int craftingResultY() {
        return CRAFTING_RESULT_Y;
    }

    public static int playerInventoryX() {
        return PLAYER_INVENTORY_X;
    }

    public static int playerInventoryY() {
        return PLAYER_INVENTORY_Y;
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
    }

    public void handleAction(HolographicStorageActionPayload payload, ServerPlayer player) {
        if (payload.containerId() != containerId) {
            return;
        }

        if (payload.action() == HolographicStorageActionPayload.ACTION_EXTRACT_TO_CARRIED) {
            extractToCarried(payload.entryIndex(), payload.amount());
        } else if (payload.action() == HolographicStorageActionPayload.ACTION_INSERT_CARRIED) {
            insertCarried(payload.amount());
        } else if (payload.action() == HolographicStorageActionPayload.ACTION_EXTRACT_TO_INVENTORY) {
            extractToInventory(payload.entryIndex(), payload.amount(), player);
        }

        super.broadcastChanges();
        sendSnapshot();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player instanceof ServerPlayer) || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        if (index == RESULT_SLOT) {
            if (core != null && core.getLevel() != null) {
                stack.getItem().onCraftedBy(stack, core.getLevel(), player);
            }

            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }

            slot.onQuickCraft(stack, moved);
        } else if (index >= CRAFT_SLOT_START && index < CRAFT_SLOT_END) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= PLAYER_INVENTORY_START && index < HOTBAR_END) {
            int inserted = insert(stack);
            if (inserted <= 0) {
                return ItemStack.EMPTY;
            }

            stack.shrink(inserted);
        } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        if (index == RESULT_SLOT) {
            player.drop(stack, false);
        }

        sendSnapshot();
        return moved;
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == craftSlots && !placingRecipe) {
            updateCraftingResult(null);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        sendPeriodicSnapshot();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (core != null && core.getLevel() != null) {
            clearContainer(player, craftSlots);
        }
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

    @Override
    protected void beginPlacingRecipe() {
        placingRecipe = true;
    }

    @Override
    protected void finishPlacingRecipe(RecipeHolder<CraftingRecipe> recipe) {
        placingRecipe = false;
        updateCraftingResult(recipe);
    }

    @Override
    public void fillCraftSlotsStackedContents(StackedContents itemHelper) {
        craftSlots.fillStackedContents(itemHelper);
    }

    @Override
    public void clearCraftingContent() {
        craftSlots.clearContent();
        resultSlots.clearContent();
    }

    @Override
    public boolean recipeMatches(RecipeHolder<CraftingRecipe> recipe) {
        return recipe.value().matches(craftSlots.asCraftInput(), player.level());
    }

    @Override
    public int getResultSlotIndex() {
        return RESULT_SLOT;
    }

    @Override
    public int getGridWidth() {
        return craftSlots.getWidth();
    }

    @Override
    public int getGridHeight() {
        return craftSlots.getHeight();
    }

    @Override
    public int getSize() {
        return CRAFT_SLOT_END;
    }

    @Override
    public RecipeBookType getRecipeBookType() {
        return RecipeBookType.CRAFTING;
    }

    @Override
    public boolean shouldMoveToInventory(int slotIndex) {
        return slotIndex != getResultSlotIndex();
    }

    private int insert(ItemStack stack) {
        if (core == null || core.getLevel() == null) {
            return 0;
        }

        HolographicStorageMultiblock.StructureReport report =
                HolographicStorageMultiblock.scan(core.getLevel(), core.getBlockPos());
        return HolographicStorageAccess.insert(core.getLevel(), storageId(), stack, capacity(report), report);
    }

    private ItemStack extract(int entryIndex, int amount) {
        if (core == null || core.getLevel() == null) {
            return ItemStack.EMPTY;
        }

        HolographicStorageMultiblock.StructureReport report =
                HolographicStorageMultiblock.scan(core.getLevel(), core.getBlockPos());
        return HolographicStorageAccess.extract(core.getLevel(), storageId(), entryIndex, amount, capacity(report), report);
    }

    private void extractToCarried(int entryIndex, int amount) {
        if (core == null || core.getLevel() == null || amount <= 0) {
            return;
        }

        HolographicStorageMultiblock.StructureReport report =
                HolographicStorageMultiblock.scan(core.getLevel(), core.getBlockPos());
        HolographicStorageData.Snapshot snapshot =
                HolographicStorageAccess.snapshot(core.getLevel(), storageId(), capacity(report), report);
        if (snapshot.interactionLocked() || entryIndex < 0 || entryIndex >= snapshot.entries().size()) {
            return;
        }

        HolographicStorageEntry entry = snapshot.entries().get(entryIndex);
        ItemStack carried = getCarried();
        int extractLimit;
        if (carried.isEmpty()) {
            extractLimit = Math.min(amount, entry.stack().getMaxStackSize());
        } else {
            if (!ItemStack.isSameItemSameComponents(carried, entry.stack())) {
                return;
            }

            extractLimit = Math.min(amount, carried.getMaxStackSize() - carried.getCount());
        }

        if (extractLimit <= 0) {
            return;
        }

        ItemStack extracted = extract(entryIndex, extractLimit);
        if (extracted.isEmpty()) {
            return;
        }

        if (carried.isEmpty()) {
            setCarried(extracted);
        } else {
            carried.grow(extracted.getCount());
            setCarried(carried);
        }
    }

    private void extractToInventory(int entryIndex, int amount, ServerPlayer player) {
        if (core == null || core.getLevel() == null || amount <= 0) {
            return;
        }

        ItemStack extracted = extract(entryIndex, amount);
        if (extracted.isEmpty()) {
            return;
        }

        if (!player.addItem(extracted)) {
            player.drop(extracted, false);
        }
    }

    private void insertCarried(int amount) {
        if (amount <= 0) {
            return;
        }

        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }

        ItemStack offered = carried.copyWithCount(Math.min(amount, carried.getCount()));
        int inserted = insert(offered);
        if (inserted <= 0) {
            return;
        }

        carried.shrink(inserted);
        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
    }

    private void updateCraftingResult(@Nullable RecipeHolder<CraftingRecipe> recipe) {
        if (core == null || core.getLevel() == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Level level = core.getLevel();
        if (level.isClientSide) {
            return;
        }

        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> maybeRecipe = level.getServer()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level, recipe);

        if (maybeRecipe.isPresent()) {
            RecipeHolder<CraftingRecipe> recipeHolder = maybeRecipe.get();
            CraftingRecipe craftingRecipe = recipeHolder.value();
            if (resultSlots.setRecipeUsed(level, serverPlayer, recipeHolder)) {
                ItemStack assembled = craftingRecipe.assemble(input, level.registryAccess());
                if (assembled.isItemEnabled(level.enabledFeatures())) {
                    result = assembled;
                }
            }
        }

        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT_SLOT, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT_SLOT, result));
    }

    private void sendSnapshot() {
        if (viewer == null || core == null || core.getLevel() == null) {
            return;
        }

        HolographicStorageMultiblock.StructureReport report =
                HolographicStorageMultiblock.scan(core.getLevel(), core.getBlockPos());
        HolographicStorageCapacity capacity = capacity(report);
        HolographicStorageData.Snapshot snapshot =
                HolographicStorageAccess.snapshot(core.getLevel(), storageId(), capacity, report);
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
                .map(HolographicStorageMenu::blockEntry)
                .toList();
    }

    private static HolographicStorageBlockEntry blockEntry(Map.Entry<Block, Integer> entry) {
        return new HolographicStorageBlockEntry(entry.getKey().getDescriptionId(), entry.getValue());
    }

    private void addCraftingGrid() {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(
                        craftSlots,
                        column + row * 3,
                        CRAFTING_GRID_X + column * 18,
                        CRAFTING_GRID_Y + row * 18
                ));
            }
        }
    }

    private void addPlayerInventory(Inventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        left + column * 18,
                        top + row * 18
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, left + column * 18, top + 58));
        }
    }
}
