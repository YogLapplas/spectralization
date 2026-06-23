package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.BasicLithographyMachineBlockEntity;
import io.github.yoglappland.spectralization.blockentity.BasicLithographyMachineBlockEntity.MachineRelativeSide;
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

public class BasicLithographyMachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_TOGGLE_TEMPLATE_RULE = 0;
    public static final int BUTTON_CYCLE_REDSTONE_MODE = 1;
    public static final int BUTTON_CYCLE_FACE_MODE_BASE = 2;
    public static final int BUTTON_CYCLE_FACE_MODE_REVERSE_BASE = BUTTON_CYCLE_FACE_MODE_BASE + 6;

    private static final int MACHINE_SLOT_COUNT = BasicLithographyMachineBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final BasicLithographyMachineBlockEntity machine;
    private final ContainerData data;

    public BasicLithographyMachineMenu(int containerId, Inventory inventory) {
        this(
                containerId,
                inventory,
                null,
                new ItemStackHandler(BasicLithographyMachineBlockEntity.SLOT_COUNT),
                new SimpleContainerData(BasicLithographyMachineBlockEntity.DATA_COUNT)
        );
    }

    public BasicLithographyMachineMenu(int containerId, Inventory inventory, BasicLithographyMachineBlockEntity machine) {
        this(containerId, inventory, machine, machine.items(), machine.createDataAccess());
    }

    private BasicLithographyMachineMenu(
            int containerId,
            Inventory inventory,
            BasicLithographyMachineBlockEntity machine,
            ItemStackHandler items,
            ContainerData data
    ) {
        super(SpectralMenus.BASIC_LITHOGRAPHY_MACHINE.get(), containerId);
        this.machine = machine;
        this.data = data;

        addSlot(slot(items, BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_INPUT_A,
                BasicLithographyMachineLayout.ITEM_TEMPLATE_INPUT_A_X, BasicLithographyMachineLayout.ITEM_TEMPLATE_INPUT_A_Y));
        addSlot(slot(items, BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_INPUT_B,
                BasicLithographyMachineLayout.ITEM_TEMPLATE_INPUT_B_X, BasicLithographyMachineLayout.ITEM_TEMPLATE_INPUT_B_Y));
        addSlot(slot(items, BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_0,
                BasicLithographyMachineLayout.ITEM_INPUT_0_X, BasicLithographyMachineLayout.ITEM_INPUT_0_Y));
        addSlot(slot(items, BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_1,
                BasicLithographyMachineLayout.ITEM_INPUT_1_X, BasicLithographyMachineLayout.ITEM_INPUT_1_Y));
        addSlot(slot(items, BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_2,
                BasicLithographyMachineLayout.ITEM_INPUT_2_X, BasicLithographyMachineLayout.ITEM_INPUT_2_Y));
        addSlot(slot(items, BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_3,
                BasicLithographyMachineLayout.ITEM_INPUT_3_X, BasicLithographyMachineLayout.ITEM_INPUT_3_Y));
        addOutputSlot(items, BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_OUTPUT_A,
                BasicLithographyMachineLayout.ITEM_TEMPLATE_OUTPUT_A_X, BasicLithographyMachineLayout.ITEM_TEMPLATE_OUTPUT_A_Y);
        addOutputSlot(items, BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_OUTPUT_B,
                BasicLithographyMachineLayout.ITEM_TEMPLATE_OUTPUT_B_X, BasicLithographyMachineLayout.ITEM_TEMPLATE_OUTPUT_B_Y);
        addOutputSlot(items, BasicLithographyMachineBlockEntity.SLOT_ITEM_OUTPUT_0,
                BasicLithographyMachineLayout.ITEM_OUTPUT_0_X, BasicLithographyMachineLayout.ITEM_OUTPUT_0_Y);
        addOutputSlot(items, BasicLithographyMachineBlockEntity.SLOT_ITEM_OUTPUT_1,
                BasicLithographyMachineLayout.ITEM_OUTPUT_1_X, BasicLithographyMachineLayout.ITEM_OUTPUT_1_Y);
        addOutputSlot(items, BasicLithographyMachineBlockEntity.SLOT_ITEM_OUTPUT_2,
                BasicLithographyMachineLayout.ITEM_OUTPUT_2_X, BasicLithographyMachineLayout.ITEM_OUTPUT_2_Y);
        addOutputSlot(items, BasicLithographyMachineBlockEntity.SLOT_ITEM_OUTPUT_3,
                BasicLithographyMachineLayout.ITEM_OUTPUT_3_X, BasicLithographyMachineLayout.ITEM_OUTPUT_3_Y);

        addPlayerInventory(inventory, BasicLithographyMachineLayout.PLAYER_INVENTORY_ITEM_X, BasicLithographyMachineLayout.PLAYER_INVENTORY_ITEM_Y);
        addDataSlots(data);
    }

    public int getData(int index) {
        return data.get(index);
    }

    public int templateRule() {
        return getData(BasicLithographyMachineBlockEntity.DATA_TEMPLATE_RULE);
    }

    public boolean moveUsedTemplates() {
        return templateRule() == BasicLithographyMachineBlockEntity.TEMPLATE_RULE_MOVE_USED;
    }

    public boolean ready() {
        return getData(BasicLithographyMachineBlockEntity.DATA_READY) != 0;
    }

    public boolean outputBlocked() {
        return getData(BasicLithographyMachineBlockEntity.DATA_OUTPUT_BLOCKED) != 0;
    }

    public int redstoneControlMode() {
        return getData(BasicLithographyMachineBlockEntity.DATA_REDSTONE_MODE);
    }

    public int faceMode(MachineRelativeSide side) {
        return getData(switch (side) {
            case FRONT -> BasicLithographyMachineBlockEntity.DATA_FACE_FRONT;
            case LEFT -> BasicLithographyMachineBlockEntity.DATA_FACE_LEFT;
            case RIGHT -> BasicLithographyMachineBlockEntity.DATA_FACE_RIGHT;
            case BACK -> BasicLithographyMachineBlockEntity.DATA_FACE_BACK;
            case TOP -> BasicLithographyMachineBlockEntity.DATA_FACE_TOP;
            case BOTTOM -> BasicLithographyMachineBlockEntity.DATA_FACE_BOTTOM;
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) {
            return false;
        }

        if (id == BUTTON_TOGGLE_TEMPLATE_RULE) {
            machine.toggleTemplateRule();
            return true;
        }

        if (id == BUTTON_CYCLE_REDSTONE_MODE) {
            machine.cycleRedstoneControlMode(false);
            return true;
        }

        int faceIndex = id - BUTTON_CYCLE_FACE_MODE_BASE;
        MachineRelativeSide[] sides = MachineRelativeSide.values();
        if (faceIndex >= 0 && faceIndex < sides.length) {
            machine.cycleFaceMode(sides[faceIndex], false);
            return true;
        }

        int reverseFaceIndex = id - BUTTON_CYCLE_FACE_MODE_REVERSE_BASE;
        if (reverseFaceIndex >= 0 && reverseFaceIndex < sides.length) {
            machine.cycleFaceMode(sides[reverseFaceIndex], true);
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
        } else if (BasicLithographyMachineBlockEntity.isTemplate(stack)) {
            if (!moveItemStackTo(stack, BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_INPUT_A,
                    BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_INPUT_B + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_0,
                BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_3 + 1, false)) {
            if (index >= PLAYER_INVENTORY_START && index < PLAYER_INVENTORY_END) {
                if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= HOTBAR_START && index < HOTBAR_END
                    && !moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
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
                && machine.getLevel().getBlockState(machine.getBlockPos()).is(Spectralization.BASIC_LITHOGRAPHY_MACHINE.get());
    }

    private static SlotItemHandler slot(ItemStackHandler items, int slot, int x, int y) {
        return new SlotItemHandler(items, slot, x, y);
    }

    private void addOutputSlot(ItemStackHandler items, int slot, int x, int y) {
        addSlot(new SlotItemHandler(items, slot, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
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
