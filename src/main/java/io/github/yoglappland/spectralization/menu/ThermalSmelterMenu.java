package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import io.github.yoglappland.spectralization.machine.ThermalSmelterRecipe;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import java.util.Optional;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class ThermalSmelterMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = ThermalSmelterBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final ThermalSmelterBlockEntity smelter;
    private final ContainerData data;

    public ThermalSmelterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new ItemStackHandler(ThermalSmelterBlockEntity.SLOT_COUNT),
                new SimpleContainerData(ThermalSmelterBlockEntity.DATA_COUNT));
    }

    public ThermalSmelterMenu(int containerId, Inventory inventory, ThermalSmelterBlockEntity smelter) {
        this(containerId, inventory, smelter, smelter.items(), smelter.createDataAccess());
    }

    private ThermalSmelterMenu(
            int containerId,
            Inventory inventory,
            ThermalSmelterBlockEntity smelter,
            ItemStackHandler items,
            ContainerData data
    ) {
        super(SpectralMenus.THERMAL_SMELTER.get(), containerId);
        this.smelter = smelter;
        this.data = data;

        addSlot(new SlotItemHandler(items, ThermalSmelterBlockEntity.SLOT_INPUT,
                ThermalSmelterLayout.ITEM_INPUT_X, ThermalSmelterLayout.ITEM_INPUT_Y));
        addSlot(new SlotItemHandler(items, ThermalSmelterBlockEntity.SLOT_ADDITIVE,
                ThermalSmelterLayout.ITEM_ADDITIVE_X, ThermalSmelterLayout.ITEM_ADDITIVE_Y));
        addSlot(new SlotItemHandler(items, ThermalSmelterBlockEntity.SLOT_OUTPUT,
                ThermalSmelterLayout.ITEM_OUTPUT_X, ThermalSmelterLayout.ITEM_OUTPUT_Y));
        addSlot(new SlotItemHandler(items, ThermalSmelterBlockEntity.SLOT_OUTPUT_SECOND,
                ThermalSmelterLayout.ITEM_OUTPUT_SECOND_X, ThermalSmelterLayout.ITEM_OUTPUT_SECOND_Y));
        addPlayerInventory(inventory, ThermalSmelterLayout.PLAYER_INVENTORY_ITEM_X, ThermalSmelterLayout.PLAYER_INVENTORY_ITEM_Y);
        addDataSlots(data);
    }

    public int getData(int index) {
        return data.get(index);
    }

    public ItemStack inputStack() {
        return getSlot(ThermalSmelterBlockEntity.SLOT_INPUT).getItem();
    }

    public ItemStack additiveStack() {
        return getSlot(ThermalSmelterBlockEntity.SLOT_ADDITIVE).getItem();
    }

    public ItemStack outputStack() {
        return getSlot(ThermalSmelterBlockEntity.SLOT_OUTPUT).getItem();
    }

    public ItemStack outputStackSecond() {
        return getSlot(ThermalSmelterBlockEntity.SLOT_OUTPUT_SECOND).getItem();
    }

    public Optional<ThermalSmelterRecipe> activeRecipe() {
        return ThermalSmelterRecipe.find(inputStack(), additiveStack());
    }

    public boolean outputBlocked() {
        return activeRecipe()
                .map(recipe -> !canAcceptResult(recipe.resultStack()))
                .orElse(false);
    }

    public boolean canAcceptResult(ItemStack result) {
        return canAcceptResult(outputStack(), result) || canAcceptResult(outputStackSecond(), result);
    }

    private boolean canAcceptResult(ItemStack output, ItemStack result) {
        if (output.isEmpty()) {
            return true;
        }

        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        var slot = slots.get(index);

        if (!slot.hasItem()) {
            return moved;
        }

        ItemStack stack = slot.getItem();
        moved = stack.copy();

        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (ThermalSmelterRecipe.isProcessable(stack)) {
            if (!moveItemStackTo(stack, ThermalSmelterBlockEntity.SLOT_INPUT, ThermalSmelterBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (ThermalSmelterRecipe.isPotentialAdditive(stack)) {
            if (!moveItemStackTo(stack, ThermalSmelterBlockEntity.SLOT_ADDITIVE, ThermalSmelterBlockEntity.SLOT_ADDITIVE + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
            if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= HOTBAR_START && index < HOTBAR_END && !moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        if (smelter == null || smelter.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(smelter.getBlockPos())) <= 64.0
                && smelter.getLevel() != null
                && smelter.getLevel().getBlockState(smelter.getBlockPos()).is(Spectralization.THERMAL_SMELTER.get());
    }

    private void addPlayerInventory(Inventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(
                        inventory,
                        column + row * 9 + 9,
                        left + column * 18,
                        top + row * 18
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, column, left + column * 18, top + 58));
        }
    }
}
