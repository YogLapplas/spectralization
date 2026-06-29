package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.MicrolizerCoreBlockEntity;
import io.github.yoglappland.spectralization.microlizer.MicrolizerFrameInfo;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineItemData;
import io.github.yoglappland.spectralization.microlizer.MicrolizerNetworkData;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class MicrolizerCoreMenu extends AbstractContainerMenu {
    public static final int BUTTON_START_MICROLIZING = 0;
    public static final int BUTTON_TOGGLE_REDSTONE_MODE = 1;
    public static final int OUTPUT_SLOT_INDEX = 0;
    public static final int IMAGE_WIDTH = 256;
    public static final int IMAGE_HEIGHT = 232;
    public static final int SLOT_SIZE = 18;
    public static final int ITEM_SLOT_INSET = 1;
    public static final int OUTPUT_SLOT_FRAME_X = 203;
    public static final int OUTPUT_SLOT_FRAME_Y = 48;
    public static final int OUTPUT_SLOT_X = OUTPUT_SLOT_FRAME_X + ITEM_SLOT_INSET;
    public static final int OUTPUT_SLOT_Y = OUTPUT_SLOT_FRAME_Y + ITEM_SLOT_INSET;
    public static final int PLAYER_INVENTORY_X = 47;
    public static final int PLAYER_INVENTORY_Y = 140;
    public static final int PLAYER_INVENTORY_ITEM_X = PLAYER_INVENTORY_X + ITEM_SLOT_INSET;
    public static final int PLAYER_INVENTORY_ITEM_Y = PLAYER_INVENTORY_Y + ITEM_SLOT_INSET;

    private static final int PRESENT = 0;
    private static final int VALID = 1;
    private static final int CORE_X = 2;
    private static final int CORE_Y = 3;
    private static final int CORE_Z = 4;
    private static final int MIN_X = 5;
    private static final int MIN_Y = 6;
    private static final int MIN_Z = 7;
    private static final int MAX_X = 8;
    private static final int MAX_Y = 9;
    private static final int MAX_Z = 10;
    private static final int WORK_MIN_X = 11;
    private static final int WORK_MIN_Y = 12;
    private static final int WORK_MIN_Z = 13;
    private static final int WORK_MAX_X = 14;
    private static final int WORK_MAX_Y = 15;
    private static final int WORK_MAX_Z = 16;
    private static final int SIZE_X = 17;
    private static final int SIZE_Y = 18;
    private static final int SIZE_Z = 19;
    private static final int WORK_SIZE_X = 20;
    private static final int WORK_SIZE_Y = 21;
    private static final int WORK_SIZE_Z = 22;
    private static final int CONNECTIONS = 23;
    private static final int FRAME_PARTS = 24;
    private static final int MICROLIZER_BLOCKS = 25;
    private static final int MICROLIZER_TYPES = 26;
    private static final int IO_PORTS = 27;
    private static final int PAYLOAD_BLOCKS = 28;
    private static final int PAYLOAD_TYPES = 29;
    private static final int DATA_COUNT = 30;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final ContainerData data;
    private final ContainerData coreData;
    private final MicrolizerCoreBlockEntity core;
    private final ItemStackHandler outputItems;
    private final ServerLevel level;
    private final BlockPos corePos;

    public MicrolizerCoreMenu(int containerId, Inventory inventory) {
        this(
                containerId,
                inventory,
                null,
                null,
                BlockPos.ZERO,
                new SimpleContainerData(DATA_COUNT),
                new ItemStackHandler(MicrolizerCoreBlockEntity.SLOT_COUNT),
                new SimpleContainerData(MicrolizerCoreBlockEntity.DATA_COUNT)
        );
    }

    public MicrolizerCoreMenu(int containerId, Inventory inventory, MicrolizerCoreBlockEntity core) {
        this(
                containerId,
                inventory,
                core,
                serverLevel(core),
                core.getBlockPos(),
                frameData(core),
                core.outputItems(),
                core.createDataAccess()
        );
    }

    private MicrolizerCoreMenu(
            int containerId,
            Inventory inventory,
            MicrolizerCoreBlockEntity core,
            ServerLevel level,
            BlockPos corePos,
            ContainerData data,
            ItemStackHandler outputItems,
            ContainerData coreData
    ) {
        super(SpectralMenus.MICROLIZER_CORE.get(), containerId);
        this.core = core;
        this.level = level;
        this.corePos = corePos.immutable();
        this.data = data;
        this.outputItems = outputItems;
        this.coreData = coreData;
        addSlot(new SlotItemHandler(outputItems, MicrolizerCoreBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addPlayerInventory(inventory, PLAYER_INVENTORY_ITEM_X, PLAYER_INVENTORY_ITEM_Y);
        addDataSlots(data);
        addDataSlots(coreData);
    }

    public boolean present() {
        return data.get(PRESENT) != 0;
    }

    public boolean valid() {
        return data.get(VALID) != 0;
    }

    public BlockPos corePos() {
        return pos(CORE_X, CORE_Y, CORE_Z);
    }

    public BlockPos min() {
        return pos(MIN_X, MIN_Y, MIN_Z);
    }

    public BlockPos max() {
        return pos(MAX_X, MAX_Y, MAX_Z);
    }

    public BlockPos workMin() {
        return pos(WORK_MIN_X, WORK_MIN_Y, WORK_MIN_Z);
    }

    public BlockPos workMax() {
        return pos(WORK_MAX_X, WORK_MAX_Y, WORK_MAX_Z);
    }

    public int sizeX() {
        return data.get(SIZE_X);
    }

    public int sizeY() {
        return data.get(SIZE_Y);
    }

    public int sizeZ() {
        return data.get(SIZE_Z);
    }

    public int workSizeX() {
        return data.get(WORK_SIZE_X);
    }

    public int workSizeY() {
        return data.get(WORK_SIZE_Y);
    }

    public int workSizeZ() {
        return data.get(WORK_SIZE_Z);
    }

    public int connections() {
        return data.get(CONNECTIONS);
    }

    public int frameParts() {
        return data.get(FRAME_PARTS);
    }

    public int microlizerBlocks() {
        return data.get(MICROLIZER_BLOCKS);
    }

    public int microlizerTypes() {
        return data.get(MICROLIZER_TYPES);
    }

    public int ioPorts() {
        return data.get(IO_PORTS);
    }

    public int payloadBlocks() {
        return data.get(PAYLOAD_BLOCKS);
    }

    public int payloadTypes() {
        return data.get(PAYLOAD_TYPES);
    }

    public boolean hasWorkArea() {
        return present() && workSizeX() > 0 && workSizeY() > 0 && workSizeZ() > 0;
    }

    public boolean outputEmpty() {
        return outputItems.getStackInSlot(MicrolizerCoreBlockEntity.SLOT_OUTPUT).isEmpty() && !microlizing();
    }

    public boolean microlizing() {
        return coreData.get(MicrolizerCoreBlockEntity.DATA_MICROLIZING) != 0;
    }

    public boolean redstonePulseMode() {
        return MicrolizerCoreBlockEntity.RedstoneStartMode.byOrdinal(
                coreData.get(MicrolizerCoreBlockEntity.DATA_REDSTONE_MODE)
        ) == MicrolizerCoreBlockEntity.RedstoneStartMode.PULSE;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_START_MICROLIZING) {
            return startMicrolizing(player);
        }

        if (id == BUTTON_TOGGLE_REDSTONE_MODE && core != null) {
            core.cycleRedstoneStartMode();
            return true;
        }

        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == OUTPUT_SLOT_INDEX && button == 1 && clickType == ClickType.PICKUP) {
            ItemStack stack = outputItems.getStackInSlot(MicrolizerCoreBlockEntity.SLOT_OUTPUT);
            if (!stack.isEmpty() && !player.level().isClientSide) {
                MicrolizedMachineItemData.describeTo(player, stack);
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index == OUTPUT_SLOT_INDEX) {
            if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
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

        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
    }

    @Override
    public boolean stillValid(Player player) {
        if (core == null || level == null || core.isRemoved()) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(corePos)) <= 64.0
                && level.getBlockState(corePos).is(Spectralization.MICROLIZER_CORE.get());
    }

    private BlockPos pos(int xIndex, int yIndex, int zIndex) {
        return new BlockPos(data.get(xIndex), data.get(yIndex), data.get(zIndex));
    }

    private static ServerLevel serverLevel(MicrolizerCoreBlockEntity core) {
        return core.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    private static ContainerData frameData(MicrolizerCoreBlockEntity core) {
        ServerLevel serverLevel = serverLevel(core);
        return serverLevel == null ? new SimpleContainerData(DATA_COUNT) : new LiveData(serverLevel, core.getBlockPos());
    }

    private boolean startMicrolizing(Player player) {
        if (level == null || core == null) {
            return false;
        }

        return core.tryStartMicrolizing(level, player, "button");
    }

    private void addPlayerInventory(Inventory inventory, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        left + column * SLOT_SIZE,
                        top + row * SLOT_SIZE
                ));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, left + column * SLOT_SIZE, top + 58));
        }
    }

    private static final class LiveData implements ContainerData {
        private final ServerLevel level;
        private final BlockPos corePos;

        private LiveData(ServerLevel level, BlockPos corePos) {
            this.level = level;
            this.corePos = corePos.immutable();
        }

        @Override
        public int get(int index) {
            MicrolizerFrameInfo info = MicrolizerNetworkData.frameInfoAt(level, corePos);
            return switch (index) {
                case PRESENT -> info.present() ? 1 : 0;
                case VALID -> info.valid() ? 1 : 0;
                case CORE_X -> corePos.getX();
                case CORE_Y -> corePos.getY();
                case CORE_Z -> corePos.getZ();
                case MIN_X -> info.min().getX();
                case MIN_Y -> info.min().getY();
                case MIN_Z -> info.min().getZ();
                case MAX_X -> info.max().getX();
                case MAX_Y -> info.max().getY();
                case MAX_Z -> info.max().getZ();
                case WORK_MIN_X -> info.workMin().getX();
                case WORK_MIN_Y -> info.workMin().getY();
                case WORK_MIN_Z -> info.workMin().getZ();
                case WORK_MAX_X -> info.workMax().getX();
                case WORK_MAX_Y -> info.workMax().getY();
                case WORK_MAX_Z -> info.workMax().getZ();
                case SIZE_X -> info.sizeX();
                case SIZE_Y -> info.sizeY();
                case SIZE_Z -> info.sizeZ();
                case WORK_SIZE_X -> info.workSizeX();
                case WORK_SIZE_Y -> info.workSizeY();
                case WORK_SIZE_Z -> info.workSizeZ();
                case CONNECTIONS -> info.connectionCount();
                case FRAME_PARTS -> info.framePartCount();
                case MICROLIZER_BLOCKS -> info.microlizerBlockCount();
                case MICROLIZER_TYPES -> info.microlizerTypeCount();
                case IO_PORTS -> info.ioPortCount();
                case PAYLOAD_BLOCKS -> info.payloadBlockCount();
                case PAYLOAD_TYPES -> info.payloadTypeCount();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
