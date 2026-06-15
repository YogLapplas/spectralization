package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.CompactedMachineBlockEntity;
import io.github.yoglappland.spectralization.compact.CompactedMachineFaceColor;
import io.github.yoglappland.spectralization.compact.CompactedMachineItemData;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class CompactedMachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_ROTATE_COUNTERCLOCKWISE = 0;
    public static final int BUTTON_ROTATE_CLOCKWISE = 1;
    public static final int BUTTON_FACE_COLOR_BASE = 16;

    private static final int SIZE_X = 0;
    private static final int SIZE_Y = 1;
    private static final int SIZE_Z = 2;
    private static final int BLOCKS = 3;
    private static final int TYPES = 4;
    private static final int IO_FACES = 5;
    private static final int TRANSFERS = 6;
    private static final int SOURCES = 7;
    private static final int FACING = 8;
    private static final int FACE_COLOR_BASE = 9;
    private static final int DATA_COUNT = FACE_COLOR_BASE + Direction.values().length;

    private final ServerLevel level;
    private final BlockPos machinePos;
    private final ContainerData data;
    private final CompoundTag snapshotData;
    private final List<CompactedMachineItemData.BlockEntry> snapshotBlocks;
    private final List<CompactedMachineItemData.IoPortEntry> snapshotIoPorts;

    public CompactedMachineMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, BlockPos.ZERO, new SimpleContainerData(DATA_COUNT), new CompoundTag(), null);
    }

    public CompactedMachineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(
                containerId,
                inventory,
                null,
                BlockPos.ZERO,
                new SimpleContainerData(DATA_COUNT),
                readSnapshot(buffer),
                buffer.registryAccess()
        );
    }

    public CompactedMachineMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos machinePos) {
        this(
                containerId,
                inventory,
                level,
                machinePos,
                new LiveData(level, machinePos),
                snapshotData(level, machinePos),
                level.registryAccess()
        );
    }

    private CompactedMachineMenu(
            int containerId,
            Inventory inventory,
            ServerLevel level,
            BlockPos machinePos,
            ContainerData data,
            CompoundTag snapshotData,
            HolderLookup.Provider registries
    ) {
        super(SpectralMenus.COMPACTED_MACHINE.get(), containerId);
        this.level = level;
        this.machinePos = machinePos.immutable();
        this.data = data;
        this.snapshotData = snapshotData.copy();
        this.snapshotBlocks = CompactedMachineItemData.blockEntries(this.snapshotData, registries);
        this.snapshotIoPorts = CompactedMachineItemData.ioPortEntries(this.snapshotData, registries);
        addDataSlots(data);
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

    public int blockCount() {
        return data.get(BLOCKS);
    }

    public int typeCount() {
        return data.get(TYPES);
    }

    public int ioFaceCount() {
        return data.get(IO_FACES);
    }

    public int transferCount() {
        return data.get(TRANSFERS);
    }

    public int sourceCount() {
        return data.get(SOURCES);
    }

    public Direction facing() {
        int ordinal = data.get(FACING);
        Direction[] directions = Direction.values();
        if (ordinal < 0 || ordinal >= directions.length || directions[ordinal].getAxis().isVertical()) {
            return Direction.NORTH;
        }

        return directions[ordinal];
    }

    public CompactedMachineFaceColor faceColor(Direction face) {
        return CompactedMachineFaceColor.byIndex(data.get(FACE_COLOR_BASE + face.ordinal()));
    }

    public CompoundTag snapshotData() {
        return snapshotData.copy();
    }

    public List<CompactedMachineItemData.BlockEntry> snapshotBlocks() {
        return snapshotBlocks;
    }

    public List<CompactedMachineItemData.IoPortEntry> snapshotIoPorts() {
        return snapshotIoPorts;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level == null) {
            return false;
        }

        if (!(level.getBlockEntity(machinePos) instanceof CompactedMachineBlockEntity compactedMachine)) {
            return false;
        }

        if (id == BUTTON_ROTATE_COUNTERCLOCKWISE) {
            compactedMachine.rotateHorizontal(false);
            return true;
        }

        if (id == BUTTON_ROTATE_CLOCKWISE) {
            compactedMachine.rotateHorizontal(true);
            return true;
        }

        int faceOrdinal = id - BUTTON_FACE_COLOR_BASE;
        Direction[] directions = Direction.values();
        if (faceOrdinal >= 0 && faceOrdinal < directions.length) {
            compactedMachine.cycleFaceColor(directions[faceOrdinal]);
            return true;
        }

        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null) {
            return true;
        }

        return player.distanceToSqr(Vec3.atCenterOf(machinePos)) <= 64.0
                && level.getBlockState(machinePos).is(Spectralization.COMPACTED_MACHINE.get());
    }

    public static void writeSnapshot(RegistryFriendlyByteBuf buffer, ServerLevel level, BlockPos machinePos) {
        buffer.writeNbt(snapshotData(level, machinePos));
    }

    private static CompoundTag readSnapshot(RegistryFriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return tag == null ? new CompoundTag() : tag;
    }

    private static CompoundTag snapshotData(ServerLevel level, BlockPos machinePos) {
        if (level != null && level.getBlockEntity(machinePos) instanceof CompactedMachineBlockEntity compactedMachine) {
            return compactedMachine.compactedData();
        }

        return new CompoundTag();
    }

    private static final class LiveData implements ContainerData {
        private final ServerLevel level;
        private final BlockPos machinePos;

        private LiveData(ServerLevel level, BlockPos machinePos) {
            this.level = level;
            this.machinePos = machinePos.immutable();
        }

        @Override
        public int get(int index) {
            if (!(level.getBlockEntity(machinePos) instanceof CompactedMachineBlockEntity compactedMachine)) {
                return 0;
            }

            CompoundTag data = compactedMachine.compactedData();
            return switch (index) {
                case SIZE_X -> CompactedMachineItemData.sizeX(data);
                case SIZE_Y -> CompactedMachineItemData.sizeY(data);
                case SIZE_Z -> CompactedMachineItemData.sizeZ(data);
                case BLOCKS -> CompactedMachineItemData.blockCount(data);
                case TYPES -> CompactedMachineItemData.blockTypeCount(data);
                case IO_FACES -> CompactedMachineItemData.ioFaces(data).size();
                case TRANSFERS -> CompactedMachineItemData.transferCount(data);
                case SOURCES -> CompactedMachineItemData.sourceCount(data);
                case FACING -> compactedMachine.facing().ordinal();
                default -> faceColor(index, compactedMachine);
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }

        private int faceColor(int index, CompactedMachineBlockEntity compactedMachine) {
            int faceOrdinal = index - FACE_COLOR_BASE;
            Direction[] directions = Direction.values();
            if (faceOrdinal < 0 || faceOrdinal >= directions.length) {
                return 0;
            }

            return compactedMachine.faceColor(directions[faceOrdinal]).ordinal();
        }
    }
}
