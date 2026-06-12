package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.PhotothermalGeneratorBlockEntity;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class PhotothermalGeneratorMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = 1;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final PhotothermalGeneratorBlockEntity generator;
    private final ContainerData data;

    public PhotothermalGeneratorMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, new ItemStackHandler(1), new SimpleContainerData(PhotothermalGeneratorBlockEntity.DATA_COUNT));
    }

    public PhotothermalGeneratorMenu(int containerId, Inventory inventory, PhotothermalGeneratorBlockEntity generator) {
        this(containerId, inventory, generator, generator.fuelItems(), generator.createDataAccess());
    }

    private PhotothermalGeneratorMenu(
            int containerId,
            Inventory inventory,
            PhotothermalGeneratorBlockEntity generator,
            ItemStackHandler fuelItems,
            ContainerData data
    ) {
        super(SpectralMenus.PHOTOTHERMAL_GENERATOR.get(), containerId);
        this.generator = generator;
        this.data = data;

        addSlot(new SlotItemHandler(fuelItems, 0, 67, 55));
        addPlayerInventory(inventory, 48, 112);
        addDataSlots(data);
    }

    public int getData(int index) {
        return data.get(index);
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
        } else if (PhotothermalGeneratorBlockEntity.isFuel(stack)) {
            if (!moveItemStackTo(stack, 0, MACHINE_SLOT_COUNT, false)) {
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
        if (generator == null || generator.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(generator.getBlockPos())) <= 64.0
                && generator.getLevel() != null
                && generator.getLevel().getBlockState(generator.getBlockPos()).is(Spectralization.PHOTOTHERMAL_GENERATOR.get());
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
