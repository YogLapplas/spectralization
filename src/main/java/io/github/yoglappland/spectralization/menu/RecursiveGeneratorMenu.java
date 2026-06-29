package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.RecursiveGeneratorBlockEntity;
import io.github.yoglappland.spectralization.item.RecursiveGeneratorBlockItem;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class RecursiveGeneratorMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = 1;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final RecursiveGeneratorBlockEntity generator;
    private final ContainerData data;

    public RecursiveGeneratorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, inputHandler(), new SimpleContainerData(RecursiveGeneratorBlockEntity.DATA_COUNT));
    }

    public RecursiveGeneratorMenu(int containerId, Inventory inventory, RecursiveGeneratorBlockEntity generator) {
        this(containerId, inventory, generator, generator.inputItems(), generator.data());
    }

    private RecursiveGeneratorMenu(
            int containerId,
            Inventory inventory,
            RecursiveGeneratorBlockEntity generator,
            ItemStackHandler inputItems,
            ContainerData data
    ) {
        super(SpectralMenus.RECURSIVE_GENERATOR.get(), containerId);
        this.generator = generator;
        this.data = data;

        addSlot(new SlotItemHandler(inputItems, 0,
                RecursiveGeneratorLayout.ITEM_INPUT_X,
                RecursiveGeneratorLayout.ITEM_INPUT_Y) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return (generator == null || generator.canAcceptUpgradeStack(stack)) && super.mayPlace(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return (generator == null || generator.canPlayerUseInputSlot()) && super.mayPickup(player);
            }
        });
        addPlayerInventory(inventory,
                RecursiveGeneratorLayout.PLAYER_INVENTORY_ITEM_X,
                RecursiveGeneratorLayout.PLAYER_INVENTORY_ITEM_Y);
        addDataSlots(data);
    }

    public int getData(int index) {
        return data.get(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (!slot.hasItem()) {
            return moved;
        }

        ItemStack stack = slot.getItem();
        normalizeEmptyRecursiveGenerator(stack);
        moved = stack.copy();

        if (index < MACHINE_SLOT_COUNT) {
            if (generator != null && !generator.canPlayerUseInputSlot()) {
                return ItemStack.EMPTY;
            }
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (RecursiveGeneratorBlockEntity.isUpgradeItem(stack)) {
            return quickMoveUpgradeFromPlayer(player, slot, stack);
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

        if (stack.getCount() == moved.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return moved;
    }

    private ItemStack quickMoveUpgradeFromPlayer(Player player, Slot sourceSlot, ItemStack source) {
        if ((generator == null || generator.canAcceptUpgradeStack(source)) && moveOneIntoMachineSlot(source)) {
            if (source.isEmpty()) {
                sourceSlot.set(ItemStack.EMPTY);
            } else {
                sourceSlot.setChanged();
            }
            sourceSlot.onTake(player, source);
        }

        return ItemStack.EMPTY;
    }

    private boolean moveOneIntoMachineSlot(ItemStack source) {
        Slot machineSlot = slots.get(0);
        if (source.isEmpty() || machineSlot.hasItem() || !machineSlot.mayPlace(source)) {
            return false;
        }

        ItemStack inserted = source.copyWithCount(1);
        machineSlot.set(inserted);
        machineSlot.setChanged();
        source.shrink(1);
        return true;
    }

    private static void normalizeEmptyRecursiveGenerator(ItemStack stack) {
        if (RecursiveGeneratorBlockEntity.isUpgradeItem(stack)) {
            RecursiveGeneratorBlockItem.normalizeEmptyStack(stack);
        }
    }

    private static ItemStackHandler inputHandler() {
        return new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return RecursiveGeneratorBlockEntity.isUpgradeItem(stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        };
    }

    @Override
    public boolean stillValid(Player player) {
        if (generator == null || generator.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(generator.getBlockPos())) <= 64.0
                && generator.getLevel() != null
                && generator.getLevel().getBlockState(generator.getBlockPos()).is(Spectralization.RECURSIVE_GENERATOR.get());
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
