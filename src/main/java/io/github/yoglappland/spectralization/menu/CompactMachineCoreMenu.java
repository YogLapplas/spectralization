package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.compact.CompactedMachineItemData;
import io.github.yoglappland.spectralization.compact.CompactMachineFrameInfo;
import io.github.yoglappland.spectralization.compact.CompactMachineNetworkData;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.world.OpticalWorldIndex;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class CompactMachineCoreMenu extends AbstractContainerMenu {
    public static final int BUTTON_START_COMPACTING = 0;
    public static final int OUTPUT_SLOT_INDEX = 0;
    public static final int OUTPUT_SLOT_X = 38;
    public static final int OUTPUT_SLOT_Y = 58;

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
    private static final int COMPACT_BLOCKS = 25;
    private static final int COMPACT_TYPES = 26;
    private static final int IO_PORTS = 27;
    private static final int PAYLOAD_BLOCKS = 28;
    private static final int PAYLOAD_TYPES = 29;
    private static final int DATA_COUNT = 30;

    private final ContainerData data;
    private final Container output = new SimpleContainer(1);
    private final ServerLevel level;
    private final BlockPos corePos;

    public CompactMachineCoreMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, BlockPos.ZERO, new SimpleContainerData(DATA_COUNT));
    }

    public CompactMachineCoreMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos corePos) {
        this(containerId, inventory, level, corePos, new LiveData(level, corePos));
    }

    private CompactMachineCoreMenu(
            int containerId,
            Inventory inventory,
            ServerLevel level,
            BlockPos corePos,
            ContainerData data
    ) {
        super(SpectralMenus.COMPACT_MACHINE_CORE.get(), containerId);
        this.level = level;
        this.corePos = corePos.immutable();
        this.data = data;
        addSlot(new Slot(output, 0, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addDataSlots(data);
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

    public int compactBlocks() {
        return data.get(COMPACT_BLOCKS);
    }

    public int compactTypes() {
        return data.get(COMPACT_TYPES);
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
        return output.getItem(0).isEmpty();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_START_COMPACTING) {
            return startCompacting(player);
        }

        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == OUTPUT_SLOT_INDEX && button == 1 && clickType == ClickType.PICKUP) {
            ItemStack stack = output.getItem(0);
            if (!stack.isEmpty() && !player.level().isClientSide) {
                CompactedMachineItemData.describeTo(player, stack);
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index != OUTPUT_SLOT_INDEX) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = output.getItem(0);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        if (!player.level().isClientSide) {
            player.getInventory().placeItemBackInInventory(copy.copy());
        }
        output.setItem(0, ItemStack.EMPTY);
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            ItemStack stack = output.removeItemNoUpdate(0);
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(corePos)) <= 64.0
                && level.getBlockState(corePos).is(Spectralization.COMPACT_MACHINE_CORE.get());
    }

    private BlockPos pos(int xIndex, int yIndex, int zIndex) {
        return new BlockPos(data.get(xIndex), data.get(yIndex), data.get(zIndex));
    }

    private boolean startCompacting(Player player) {
        if (level == null || !output.getItem(0).isEmpty()) {
            return false;
        }

        CompactMachineFrameInfo info = CompactMachineNetworkData.frameInfoAt(level, corePos);
        if (!info.valid() || info.workSizeX() <= 0 || info.workSizeY() <= 0 || info.workSizeZ() <= 0) {
            Spectralization.LOGGER.warn(
                    "Compact machine start rejected in {} at {}: present={}, valid={}, reason={}, shell={}x{}x{}, work={}x{}x{}, frame_parts={}, io={}, payload={} type(s) {}",
                    level.dimension().location(),
                    corePos,
                    info.present(),
                    info.valid(),
                    info.reason(),
                    info.sizeX(),
                    info.sizeY(),
                    info.sizeZ(),
                    info.workSizeX(),
                    info.workSizeY(),
                    info.workSizeZ(),
                    info.framePartCount(),
                    info.ioPortCount(),
                    info.payloadBlockCount(),
                    info.payloadTypeCount()
            );
            player.displayClientMessage(Component.translatable("screen.spectralization.compact_machine_core.compact_not_ready"), false);
            return false;
        }

        ItemStack stack = CompactedMachineItemData.createStack(level, info.min(), info.max(), info.workMin(), info.workMax());
        clearWorkArea(info.workMin(), info.workMax());
        output.setItem(0, stack);
        player.displayClientMessage(Component.translatable("screen.spectralization.compact_machine_core.compact_started"), false);
        return true;
    }

    private void clearWorkArea(BlockPos min, BlockPos max) {
        boolean changedOptics = false;

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).isAir()) {
                continue;
            }

            BlockPos immutablePos = pos.immutable();
            OpticalWorldIndex.onBlockBroken(level, immutablePos);
            FiberNetworkIndex.onBlockBroken(level, immutablePos);
            OpticalTraceCache.forgetDormantSource(level, immutablePos);
            OpticalTraceCache.markChanged(level, immutablePos, OpticalDirtyKind.STRUCTURE);
            OpticalTraceCache.requestIntrinsicSourcesNear(level, immutablePos);
            level.setBlockAndUpdate(immutablePos, Blocks.AIR.defaultBlockState());
            changedOptics = true;
        }

        if (changedOptics) {
            OpticalFieldSources.invalidate(level);
            OpticalNetworkIndex.markDirty(level);
            CompactMachineNetworkData.scheduleRefresh(level, corePos, "compact machine started");
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
            CompactMachineFrameInfo info = CompactMachineNetworkData.frameInfoAt(level, corePos);
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
                case COMPACT_BLOCKS -> info.compactBlockCount();
                case COMPACT_TYPES -> info.compactTypeCount();
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
