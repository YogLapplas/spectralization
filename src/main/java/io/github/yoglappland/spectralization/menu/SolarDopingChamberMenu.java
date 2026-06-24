package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.SolarDopingChamberBlockEntity;
import io.github.yoglappland.spectralization.machine.SolarDopingRecipe;
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

public class SolarDopingChamberMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = SolarDopingChamberBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final SolarDopingChamberBlockEntity chamber;
    private final ContainerData data;

    public SolarDopingChamberMenu(int containerId, Inventory inventory) {
        this(
                containerId,
                inventory,
                null,
                new ItemStackHandler(SolarDopingChamberBlockEntity.SLOT_COUNT),
                new SimpleContainerData(SolarDopingChamberBlockEntity.DATA_COUNT)
        );
    }

    public SolarDopingChamberMenu(int containerId, Inventory inventory, SolarDopingChamberBlockEntity chamber) {
        this(containerId, inventory, chamber, chamber.items(), chamber.createDataAccess());
    }

    private SolarDopingChamberMenu(
            int containerId,
            Inventory inventory,
            SolarDopingChamberBlockEntity chamber,
            ItemStackHandler items,
            ContainerData data
    ) {
        super(SpectralMenus.SOLAR_DOPING_CHAMBER.get(), containerId);
        this.chamber = chamber;
        this.data = data;

        addSlot(new SlotItemHandler(
                items,
                SolarDopingChamberBlockEntity.SLOT_PROCESS,
                SolarDopingChamberLayout.ITEM_PROCESS_X,
                SolarDopingChamberLayout.ITEM_PROCESS_Y
        ));
        addSlot(new SlotItemHandler(
                items,
                SolarDopingChamberBlockEntity.SLOT_FILTER,
                SolarDopingChamberLayout.ITEM_FILTER_X,
                SolarDopingChamberLayout.ITEM_FILTER_Y
        ));

        addPlayerInventory(inventory, SolarDopingChamberLayout.PLAYER_INVENTORY_ITEM_X,
                SolarDopingChamberLayout.PLAYER_INVENTORY_ITEM_Y);
        addDataSlots(data);
    }

    public int getData(int index) {
        return data.get(index);
    }

    public int state() {
        return getData(SolarDopingChamberBlockEntity.DATA_STATE);
    }

    public boolean running() {
        return state() == SolarDopingChamberBlockEntity.STATE_RUNNING;
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
        } else if (SolarDopingRecipe.isPotentialInput(stack)) {
            if (!moveItemStackTo(stack, SolarDopingChamberBlockEntity.SLOT_PROCESS,
                    SolarDopingChamberBlockEntity.SLOT_PROCESS + 1, false)) {
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
        if (chamber == null || chamber.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(chamber.getBlockPos())) <= 64.0
                && chamber.getLevel() != null
                && chamber.getLevel().getBlockState(chamber.getBlockPos()).is(Spectralization.SOLAR_DOPING_CHAMBER.get());
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
