package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.MetamaterialDesignTableBlockEntity;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialStandardTemplate;
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

public class MetamaterialDesignTableMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_MODE = 0;
    public static final int BUTTON_STANDARD_PREV = 1;
    public static final int BUTTON_STANDARD_NEXT = 2;
    public static final int BUTTON_DESIGN = 3;

    public static final int X_SLOT_X = 34;
    public static final int X_SLOT_Y = 38;
    public static final int Y_SLOT_X = 34;
    public static final int Y_SLOT_Y = 64;
    public static final int Z_SLOT_X = 34;
    public static final int Z_SLOT_Y = 90;
    public static final int OUTPUT_SLOT_X = 88;
    public static final int OUTPUT_SLOT_Y = 64;
    public static final int INVENTORY_X = 48;
    public static final int INVENTORY_Y = 140;

    private static final int MACHINE_SLOT_COUNT = MetamaterialDesignTableBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final MetamaterialDesignTableBlockEntity table;
    private final ContainerData data;

    public MetamaterialDesignTableMenu(int containerId, Inventory inventory) {
        this(
                containerId,
                inventory,
                null,
                new ItemStackHandler(MetamaterialDesignTableBlockEntity.SLOT_COUNT),
                new SimpleContainerData(MetamaterialDesignTableBlockEntity.DATA_COUNT)
        );
    }

    public MetamaterialDesignTableMenu(int containerId, Inventory inventory, MetamaterialDesignTableBlockEntity table) {
        this(containerId, inventory, table, table.items(), table.createDataAccess());
    }

    private MetamaterialDesignTableMenu(
            int containerId,
            Inventory inventory,
            MetamaterialDesignTableBlockEntity table,
            ItemStackHandler items,
            ContainerData data
    ) {
        super(SpectralMenus.METAMATERIAL_DESIGN_TABLE.get(), containerId);
        this.table = table;
        this.data = data;

        addSlot(new SlotItemHandler(items, MetamaterialDesignTableBlockEntity.SLOT_X_BUDGET, X_SLOT_X, X_SLOT_Y));
        addSlot(new SlotItemHandler(items, MetamaterialDesignTableBlockEntity.SLOT_Y_BUDGET, Y_SLOT_X, Y_SLOT_Y));
        addSlot(new SlotItemHandler(items, MetamaterialDesignTableBlockEntity.SLOT_Z_BUDGET, Z_SLOT_X, Z_SLOT_Y));
        addSlot(new SlotItemHandler(items, MetamaterialDesignTableBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addPlayerInventory(inventory, INVENTORY_X, INVENTORY_Y);
        addDataSlots(data);
    }

    public int getData(int index) {
        return data.get(index);
    }

    public boolean ready() {
        return getData(MetamaterialDesignTableBlockEntity.DATA_READY) != 0;
    }

    public boolean customMode() {
        return getData(MetamaterialDesignTableBlockEntity.DATA_MODE) == MetamaterialDesignTableBlockEntity.MODE_CUSTOM;
    }

    public MetamaterialStandardTemplate selectedStandard() {
        return MetamaterialStandardTemplate.byIndex(getData(MetamaterialDesignTableBlockEntity.DATA_STANDARD));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (table == null) {
            return false;
        }

        return switch (id) {
            case BUTTON_TOGGLE_MODE -> {
                table.toggleMode();
                yield true;
            }
            case BUTTON_STANDARD_PREV -> {
                table.adjustStandard(-1);
                yield true;
            }
            case BUTTON_STANDARD_NEXT -> {
                table.adjustStandard(1);
                yield true;
            }
            case BUTTON_DESIGN -> table.design();
            default -> false;
        };
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
        } else if (MetamaterialDesignTableBlockEntity.isDesignMaterial(stack)) {
            if (!moveItemStackTo(stack, MetamaterialDesignTableBlockEntity.SLOT_X_BUDGET,
                    MetamaterialDesignTableBlockEntity.SLOT_Z_BUDGET + 1, false)) {
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
        if (table == null || table.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(table.getBlockPos())) <= 64.0
                && table.getLevel() != null
                && table.getLevel().getBlockState(table.getBlockPos()).is(Spectralization.METAMATERIAL_DESIGN_TABLE.get());
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
