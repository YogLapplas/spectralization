package io.github.yoglappland.spectralization.menu;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.MicrolizedMachineBlockEntity;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineFaceColor;
import io.github.yoglappland.spectralization.microlizer.MicrolizedMachineItemData;
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

public class MicrolizedMachineMenu extends AbstractContainerMenu {
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
    private final List<MicrolizedMachineItemData.BlockEntry> snapshotBlocks;
    private final List<MicrolizedMachineItemData.IoPortEntry> snapshotIoPorts;

    public MicrolizedMachineMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, BlockPos.ZERO, new SimpleContainerData(DATA_COUNT), new CompoundTag(), null);
    }

    public MicrolizedMachineMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
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

    public MicrolizedMachineMenu(int containerId, Inventory inventory, ServerLevel level, BlockPos machinePos) {
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

    private MicrolizedMachineMenu(
            int containerId,
            Inventory inventory,
            ServerLevel level,
            BlockPos machinePos,
            ContainerData data,
            CompoundTag snapshotData,
            HolderLookup.Provider registries
    ) {
        super(SpectralMenus.MICROLIZED_MACHINE.get(), containerId);
        this.level = level;
        this.machinePos = machinePos.immutable();
        this.data = data;
        this.snapshotData = snapshotData.copy();
        this.snapshotBlocks = MicrolizedMachineItemData.blockEntries(this.snapshotData, registries);
        this.snapshotIoPorts = MicrolizedMachineItemData.ioPortEntries(this.snapshotData, registries);
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

    public MicrolizedMachineFaceColor faceColor(Direction face) {
        return MicrolizedMachineFaceColor.byIndex(data.get(FACE_COLOR_BASE + face.ordinal()));
    }

    public CompoundTag snapshotData() {
        return snapshotData.copy();
    }

    public List<MicrolizedMachineItemData.BlockEntry> snapshotBlocks() {
        return snapshotBlocks;
    }

    public List<MicrolizedMachineItemData.IoPortEntry> snapshotIoPorts() {
        return snapshotIoPorts;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level == null) {
            return false;
        }

        if (!(level.getBlockEntity(machinePos) instanceof MicrolizedMachineBlockEntity microlizedMachine)) {
            return false;
        }

        if (id == BUTTON_ROTATE_COUNTERCLOCKWISE) {
            microlizedMachine.rotateHorizontal(false);
            return true;
        }

        if (id == BUTTON_ROTATE_CLOCKWISE) {
            microlizedMachine.rotateHorizontal(true);
            return true;
        }

        int faceOrdinal = id - BUTTON_FACE_COLOR_BASE;
        Direction[] directions = Direction.values();
        if (faceOrdinal >= 0 && faceOrdinal < directions.length) {
            microlizedMachine.cycleFaceColor(directions[faceOrdinal]);
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
                && level.getBlockState(machinePos).is(Spectralization.MICROLIZED_MACHINE.get());
    }

    public static void writeSnapshot(RegistryFriendlyByteBuf buffer, ServerLevel level, BlockPos machinePos) {
        buffer.writeNbt(snapshotData(level, machinePos));
    }

    private static CompoundTag readSnapshot(RegistryFriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return tag == null ? new CompoundTag() : tag;
    }

    private static CompoundTag snapshotData(ServerLevel level, BlockPos machinePos) {
        if (level != null && level.getBlockEntity(machinePos) instanceof MicrolizedMachineBlockEntity microlizedMachine) {
            return microlizedMachine.microlizedData();
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
            if (!(level.getBlockEntity(machinePos) instanceof MicrolizedMachineBlockEntity microlizedMachine)) {
                return 0;
            }

            CompoundTag data = microlizedMachine.microlizedData();
            return switch (index) {
                case SIZE_X -> MicrolizedMachineItemData.sizeX(data);
                case SIZE_Y -> MicrolizedMachineItemData.sizeY(data);
                case SIZE_Z -> MicrolizedMachineItemData.sizeZ(data);
                case BLOCKS -> MicrolizedMachineItemData.blockCount(data);
                case TYPES -> MicrolizedMachineItemData.blockTypeCount(data);
                case IO_FACES -> MicrolizedMachineItemData.ioFaces(data).size();
                case TRANSFERS -> MicrolizedMachineItemData.transferCount(data);
                case SOURCES -> MicrolizedMachineItemData.sourceCount(data);
                case FACING -> microlizedMachine.facing().ordinal();
                default -> faceColor(index, microlizedMachine);
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }

        private int faceColor(int index, MicrolizedMachineBlockEntity microlizedMachine) {
            int faceOrdinal = index - FACE_COLOR_BASE;
            Direction[] directions = Direction.values();
            if (faceOrdinal < 0 || faceOrdinal >= directions.length) {
                return 0;
            }

            return microlizedMachine.faceColor(directions[faceOrdinal]).ordinal();
        }
    }
}
