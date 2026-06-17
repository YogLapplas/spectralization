package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.LensGrindingBenchBlockEntity;
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

public class LensGrindingBenchMenu extends AbstractContainerMenu {
    public static final int BUTTON_KIND_DOWN = 0;
    public static final int BUTTON_KIND_UP = 1;
    public static final int BUTTON_TARGET_DOWN = 2;
    public static final int BUTTON_TARGET_UP = 3;
    public static final int BUTTON_GRIND = 4;

    public static final int BLANK_SLOT_X = 34;
    public static final int BLANK_SLOT_Y = 38;
    public static final int TOOL_SLOT_X = 34;
    public static final int TOOL_SLOT_Y = 64;
    public static final int REFERENCE_SLOT_X = 34;
    public static final int REFERENCE_SLOT_Y = 90;
    public static final int OUTPUT_SLOT_X = 84;
    public static final int OUTPUT_SLOT_Y = 64;
    public static final int INVENTORY_X = 48;
    public static final int INVENTORY_Y = 140;

    private static final int MACHINE_SLOT_COUNT = LensGrindingBenchBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final LensGrindingBenchBlockEntity bench;
    private final ContainerData data;

    public LensGrindingBenchMenu(int containerId, Inventory inventory) {
        this(
                containerId,
                inventory,
                null,
                new ItemStackHandler(LensGrindingBenchBlockEntity.SLOT_COUNT),
                new SimpleContainerData(LensGrindingBenchBlockEntity.DATA_COUNT)
        );
    }

    public LensGrindingBenchMenu(int containerId, Inventory inventory, LensGrindingBenchBlockEntity bench) {
        this(containerId, inventory, bench, bench.items(), bench.createDataAccess());
    }

    private LensGrindingBenchMenu(
            int containerId,
            Inventory inventory,
            LensGrindingBenchBlockEntity bench,
            ItemStackHandler items,
            ContainerData data
    ) {
        super(SpectralMenus.LENS_GRINDING_BENCH.get(), containerId);
        this.bench = bench;
        this.data = data;

        addSlot(new SlotItemHandler(items, LensGrindingBenchBlockEntity.SLOT_BLANK, BLANK_SLOT_X, BLANK_SLOT_Y));
        addSlot(new SlotItemHandler(items, LensGrindingBenchBlockEntity.SLOT_TOOL, TOOL_SLOT_X, TOOL_SLOT_Y));
        addSlot(new SlotItemHandler(items, LensGrindingBenchBlockEntity.SLOT_REFERENCE, REFERENCE_SLOT_X, REFERENCE_SLOT_Y));
        addSlot(new SlotItemHandler(items, LensGrindingBenchBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
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
        return getData(LensGrindingBenchBlockEntity.DATA_READY) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (bench == null) {
            return false;
        }

        return switch (id) {
            case BUTTON_KIND_DOWN -> {
                bench.adjustLensKind(-1);
                yield true;
            }
            case BUTTON_KIND_UP -> {
                bench.adjustLensKind(1);
                yield true;
            }
            case BUTTON_TARGET_DOWN -> {
                bench.adjustTarget(-1);
                yield true;
            }
            case BUTTON_TARGET_UP -> {
                bench.adjustTarget(1);
                yield true;
            }
            case BUTTON_GRIND -> bench.grind();
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
        } else if (LensGrindingBenchBlockEntity.isBlank(stack)) {
            if (!moveItemStackTo(stack, LensGrindingBenchBlockEntity.SLOT_BLANK, LensGrindingBenchBlockEntity.SLOT_BLANK + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (LensGrindingBenchBlockEntity.toolTier(stack) >= 0) {
            if (!moveItemStackTo(stack, LensGrindingBenchBlockEntity.SLOT_TOOL, LensGrindingBenchBlockEntity.SLOT_TOOL + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(Spectralization.LENS.get())) {
            if (!moveItemStackTo(stack, LensGrindingBenchBlockEntity.SLOT_REFERENCE, LensGrindingBenchBlockEntity.SLOT_REFERENCE + 1, false)) {
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
        if (bench == null || bench.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(bench.getBlockPos())) <= 64.0
                && bench.getLevel() != null
                && bench.getLevel().getBlockState(bench.getBlockPos()).is(Spectralization.LENS_GRINDING_BENCH.get());
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
