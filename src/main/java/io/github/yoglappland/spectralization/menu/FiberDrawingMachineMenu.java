package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.FiberDrawingMachineBlockEntity;
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

public class FiberDrawingMachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_MOLD_RULE = 0;

    private static final int MACHINE_SLOT_COUNT = FiberDrawingMachineBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final FiberDrawingMachineBlockEntity machine;
    private final ContainerData data;

    public FiberDrawingMachineMenu(int containerId, Inventory inventory) {
        this(
                containerId,
                inventory,
                null,
                new ItemStackHandler(FiberDrawingMachineBlockEntity.SLOT_COUNT),
                new SimpleContainerData(FiberDrawingMachineBlockEntity.DATA_COUNT)
        );
    }

    public FiberDrawingMachineMenu(int containerId, Inventory inventory, FiberDrawingMachineBlockEntity machine) {
        this(containerId, inventory, machine, machine.items(), machine.createDataAccess());
    }

    private FiberDrawingMachineMenu(
            int containerId,
            Inventory inventory,
            FiberDrawingMachineBlockEntity machine,
            ItemStackHandler items,
            ContainerData data
    ) {
        super(SpectralMenus.FIBER_DRAWING_MACHINE.get(), containerId);
        this.machine = machine;
        this.data = data;

        addSlot(new SlotItemHandler(
                items,
                FiberDrawingMachineBlockEntity.SLOT_MATERIAL_INPUT,
                FiberDrawingMachineLayout.ITEM_MATERIAL_X,
                FiberDrawingMachineLayout.ITEM_MATERIAL_Y
        ));
        addSlot(new SlotItemHandler(
                items,
                FiberDrawingMachineBlockEntity.SLOT_OUTPUT,
                FiberDrawingMachineLayout.ITEM_OUTPUT_X,
                FiberDrawingMachineLayout.ITEM_OUTPUT_Y
        ));
        addSlot(new SlotItemHandler(
                items,
                FiberDrawingMachineBlockEntity.SLOT_MOLD_INPUT,
                FiberDrawingMachineLayout.ITEM_MOLD_INPUT_X,
                FiberDrawingMachineLayout.ITEM_MOLD_INPUT_Y
        ));
        addSlot(new SlotItemHandler(
                items,
                FiberDrawingMachineBlockEntity.SLOT_MOLD_OUTPUT,
                FiberDrawingMachineLayout.ITEM_MOLD_OUTPUT_X,
                FiberDrawingMachineLayout.ITEM_MOLD_OUTPUT_Y
        ));

        addPlayerInventory(inventory, FiberDrawingMachineLayout.PLAYER_INVENTORY_ITEM_X,
                FiberDrawingMachineLayout.PLAYER_INVENTORY_ITEM_Y);
        addDataSlots(data);
    }

    public int data(int index) {
        return data.get(index);
    }

    public boolean ejectMold() {
        return data(FiberDrawingMachineBlockEntity.DATA_MOLD_RULE) == FiberDrawingMachineBlockEntity.MOLD_RULE_EJECT;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) {
            return false;
        }

        if (id == BUTTON_TOGGLE_MOLD_RULE) {
            machine.toggleMoldRule();
            return true;
        }

        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (!slot.hasItem()) {
            return moved;
        }

        ItemStack stack = slot.getItem();
        moved = stack.copy();

        if (index < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (FiberDrawingMachineBlockEntity.isFiberMold(stack)) {
            if (!moveItemStackTo(stack, FiberDrawingMachineBlockEntity.SLOT_MOLD_INPUT,
                    FiberDrawingMachineBlockEntity.SLOT_MOLD_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (FiberDrawingMachineBlockEntity.isDrawableMaterial(stack)) {
            if (!moveItemStackTo(stack, FiberDrawingMachineBlockEntity.SLOT_MATERIAL_INPUT,
                    FiberDrawingMachineBlockEntity.SLOT_MATERIAL_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
            if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= HOTBAR_START && index < HOTBAR_END
                && !moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
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
        if (machine == null || machine.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(machine.getBlockPos())) <= 64.0
                && machine.getLevel() != null
                && machine.getLevel().getBlockState(machine.getBlockPos()).is(Spectralization.FIBER_DRAWING_MACHINE.get());
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
