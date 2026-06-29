package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.compact.CompactMachineAnimationPublisher;
import io.github.yoglappland.spectralization.compact.CompactMachineFrameInfo;
import io.github.yoglappland.spectralization.compact.CompactMachineNetworkData;
import io.github.yoglappland.spectralization.compact.CompactedMachineItemData;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.network.CompactMachineAnimationPayload;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.world.OpticalWorldIndex;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CompactMachineCoreBlockEntity extends BlockEntity implements DropsContentsOnRemove {
    public static final int SLOT_OUTPUT = 0;
    public static final int SLOT_COUNT = 1;

    public static final int DATA_COMPACTING = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_DURATION = 2;
    public static final int DATA_REDSTONE_MODE = 3;
    public static final int DATA_COUNT = 4;

    private static final String OUTPUT_ITEMS_TAG = "output_items";
    private static final String PENDING_RESULT_TAG = "pending_result";
    private static final String PENDING_MIN_TAG = "pending_work_min";
    private static final String PENDING_MAX_TAG = "pending_work_max";
    private static final String PENDING_AGE_TAG = "pending_age";
    private static final String PENDING_CLEARED_TAG = "pending_cleared";
    private static final String REDSTONE_MODE_TAG = "redstone_mode";
    private static final String LAST_REDSTONE_POWERED_TAG = "last_redstone_powered";

    private final ItemStackHandler outputItems = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_COMPACTING -> isCompacting() ? 1 : 0;
                case DATA_PROGRESS -> compactingAge;
                case DATA_DURATION -> CompactMachineAnimationPayload.DEFAULT_DURATION_TICKS;
                case DATA_REDSTONE_MODE -> redstoneStartMode.ordinal();
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
    };

    private ItemStack pendingResult = ItemStack.EMPTY;
    private BlockPos pendingWorkMin = BlockPos.ZERO;
    private BlockPos pendingWorkMax = BlockPos.ZERO;
    private int compactingAge = 0;
    private boolean workAreaCleared = false;
    private RedstoneStartMode redstoneStartMode = RedstoneStartMode.IGNORED;
    private boolean lastRedstonePowered = false;

    public CompactMachineCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.COMPACT_MACHINE_CORE.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, CompactMachineCoreBlockEntity core) {
        if (level instanceof ServerLevel serverLevel) {
            core.tickCompacting(serverLevel, pos);
        }
    }

    public ItemStackHandler outputItems() {
        return outputItems;
    }

    @Nullable
    public ItemStackHandler getOutputItems(@Nullable net.minecraft.core.Direction side) {
        return outputItems;
    }

    public ContainerData createDataAccess() {
        return data;
    }

    public boolean outputEmpty() {
        return outputItems.getStackInSlot(SLOT_OUTPUT).isEmpty();
    }

    public boolean isCompacting() {
        return !pendingResult.isEmpty();
    }

    public int compactingAge() {
        return compactingAge;
    }

    public boolean startCompacting(ServerLevel level, CompactMachineFrameInfo info) {
        if (isCompacting() || !outputEmpty()) {
            return false;
        }

        pendingResult = CompactedMachineItemData.createStack(level, info.min(), info.max(), info.workMin(), info.workMax());
        pendingWorkMin = info.workMin().immutable();
        pendingWorkMax = info.workMax().immutable();
        compactingAge = 0;
        workAreaCleared = false;
        CompactMachineAnimationPublisher.publishStart(level, worldPosition, pendingWorkMin, pendingWorkMax);
        setChanged();
        Spectralization.LOGGER.info(
                "Compact machine animation started in {} at {}: work_area {}..{}, payload {} block(s)",
                level.dimension().location(),
                worldPosition,
                pendingWorkMin,
                pendingWorkMax,
                info.payloadBlockCount()
        );
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.COMPACT_MACHINE, "compression_started")
                .pos("core", worldPosition)
                .pos("work_min", pendingWorkMin)
                .pos("work_max", pendingWorkMax)
                .field("payload_blocks", info.payloadBlockCount())
                .field("duration_ticks", CompactMachineAnimationPayload.DEFAULT_DURATION_TICKS)
                .field("clear_at_tick", CompactMachineAnimationPayload.CLEAR_WORK_AREA_AT_TICKS)
                .write();
        return true;
    }

    public boolean tryStartCompacting(ServerLevel level, @Nullable Player player, String trigger) {
        if (isCompacting() || !outputEmpty()) {
            return false;
        }

        CompactMachineFrameInfo info = CompactMachineNetworkData.frameInfoAt(level, worldPosition);
        if (!info.valid() || info.workSizeX() <= 0 || info.workSizeY() <= 0 || info.workSizeZ() <= 0) {
            Spectralization.LOGGER.warn(
                    "Compact machine start rejected in {} at {}: trigger={}, present={}, valid={}, reason={}, shell={}x{}x{}, work={}x{}x{}, frame_parts={}, io={}, payload={} type(s) {}",
                    level.dimension().location(),
                    worldPosition,
                    trigger,
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
            if (player != null) {
                player.displayClientMessage(Component.translatable("screen.spectralization.compact_machine_core.compact_not_ready"), false);
            }
            return false;
        }

        if (!startCompacting(level, info)) {
            return false;
        }

        if (player != null) {
            player.displayClientMessage(Component.translatable("screen.spectralization.compact_machine_core.compact_started"), false);
        }
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.COMPACT_MACHINE, "compression_triggered")
                .pos("core", worldPosition)
                .field("trigger", trigger)
                .field("redstone_mode", redstoneStartMode.id())
                .write();
        return true;
    }

    public RedstoneStartMode redstoneStartMode() {
        return redstoneStartMode;
    }

    public void cycleRedstoneStartMode() {
        redstoneStartMode = redstoneStartMode == RedstoneStartMode.IGNORED
                ? RedstoneStartMode.PULSE
                : RedstoneStartMode.IGNORED;
        if (level != null) {
            lastRedstonePowered = level.hasNeighborSignal(worldPosition);
        }
        setChanged();
        logRedstoneModeChanged();
    }

    public void updateRedstoneSignal(ServerLevel level) {
        boolean powered = level.hasNeighborSignal(worldPosition);
        boolean risingEdge = powered && !lastRedstonePowered;
        lastRedstonePowered = powered;

        if (risingEdge && redstoneStartMode == RedstoneStartMode.PULSE) {
            tryStartCompacting(level, null, "redstone_pulse");
        }
        setChanged();
    }

    public void dropContents(Level level, BlockPos pos) {
        ItemStack output = outputItems.getStackInSlot(SLOT_OUTPUT);
        if (!output.isEmpty()) {
            Block.popResource(level, pos, output.copy());
            outputItems.setStackInSlot(SLOT_OUTPUT, ItemStack.EMPTY);
        }

        if (!pendingResult.isEmpty()) {
            Block.popResource(level, pos, pendingResult.copy());
            pendingResult = ItemStack.EMPTY;
            setChanged();
        }
    }

    private void tickCompacting(ServerLevel level, BlockPos pos) {
        if (pendingResult.isEmpty()) {
            compactingAge = 0;
            workAreaCleared = false;
            return;
        }

        compactingAge++;

        if (!workAreaCleared && compactingAge >= CompactMachineAnimationPayload.CLEAR_WORK_AREA_AT_TICKS) {
            clearWorkArea(level, pendingWorkMin, pendingWorkMax);
            workAreaCleared = true;
        }

        if (compactingAge >= CompactMachineAnimationPayload.DEFAULT_DURATION_TICKS) {
            finishCompacting(level, pos);
        } else {
            setChanged();
        }
    }

    private void finishCompacting(ServerLevel level, BlockPos pos) {
        if (!workAreaCleared) {
            clearWorkArea(level, pendingWorkMin, pendingWorkMax);
            workAreaCleared = true;
        }

        ItemStack result = pendingResult.copy();
        pendingResult = ItemStack.EMPTY;
        compactingAge = 0;
        workAreaCleared = false;

        if (outputItems.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            outputItems.setStackInSlot(SLOT_OUTPUT, result);
        } else {
            Block.popResource(level, pos, result);
            Spectralization.LOGGER.warn(
                    "Compact machine output at {} was occupied when animation finished; dropping result",
                    pos
            );
            SpectralDiagnostics.anomaly(level, SpectralDiagnostics.Subsystem.COMPACT_MACHINE, "compression_output_occupied")
                    .pos("core", pos)
                    .field("result_dropped", true)
                    .write();
        }

        Spectralization.LOGGER.info(
                "Compact machine animation finished in {} at {}",
                level.dimension().location(),
                pos
        );
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.COMPACT_MACHINE, "compression_finished")
                .pos("core", pos)
                .field("output_slot_empty", outputItems.getStackInSlot(SLOT_OUTPUT).isEmpty())
                .write();
        setChanged();
    }

    private void clearWorkArea(ServerLevel level, BlockPos min, BlockPos max) {
        boolean changedOptics = false;
        int clearedBlocks = 0;

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
            clearedBlocks++;
        }

        if (changedOptics) {
            OpticalFieldSources.invalidate(level);
            OpticalNetworkIndex.markDirty(level);
            CompactMachineNetworkData.scheduleRefresh(level, worldPosition, "compact machine work area cleared");
        }

        Spectralization.LOGGER.info(
                "Compact machine cleared {} work-area block(s) in {} at {}",
                clearedBlocks,
                level.dimension().location(),
                worldPosition
        );
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.COMPACT_MACHINE, "work_area_cleared")
                .pos("core", worldPosition)
                .pos("work_min", min)
                .pos("work_max", max)
                .field("cleared_blocks", clearedBlocks)
                .field("optical_dirty", changedOptics)
                .field("fiber_dirty", changedOptics)
                .write();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(OUTPUT_ITEMS_TAG)) {
            outputItems.deserializeNBT(registries, tag.getCompound(OUTPUT_ITEMS_TAG));
        }

        if (tag.contains(PENDING_RESULT_TAG, Tag.TAG_COMPOUND)) {
            pendingResult = ItemStack.parseOptional(registries, tag.getCompound(PENDING_RESULT_TAG));
        } else {
            pendingResult = ItemStack.EMPTY;
        }

        pendingWorkMin = readPos(tag.getCompound(PENDING_MIN_TAG));
        pendingWorkMax = readPos(tag.getCompound(PENDING_MAX_TAG));
        compactingAge = Math.max(0, tag.getInt(PENDING_AGE_TAG));
        workAreaCleared = tag.getBoolean(PENDING_CLEARED_TAG);
        redstoneStartMode = RedstoneStartMode.byId(tag.getString(REDSTONE_MODE_TAG));
        lastRedstonePowered = tag.getBoolean(LAST_REDSTONE_POWERED_TAG);

        if (pendingResult.isEmpty()) {
            compactingAge = 0;
            workAreaCleared = false;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(OUTPUT_ITEMS_TAG, outputItems.serializeNBT(registries));
        if (!pendingResult.isEmpty()) {
            tag.put(PENDING_RESULT_TAG, pendingResult.saveOptional(registries));
        }
        tag.put(PENDING_MIN_TAG, writePos(pendingWorkMin));
        tag.put(PENDING_MAX_TAG, writePos(pendingWorkMax));
        tag.putInt(PENDING_AGE_TAG, compactingAge);
        tag.putBoolean(PENDING_CLEARED_TAG, workAreaCleared);
        tag.putString(REDSTONE_MODE_TAG, redstoneStartMode.id());
        tag.putBoolean(LAST_REDSTONE_POWERED_TAG, lastRedstonePowered);
    }

    private void logRedstoneModeChanged() {
        if (level == null || level.isClientSide) {
            return;
        }

        SpectralDiagnostics.transition(level, SpectralDiagnostics.Subsystem.COMPACT_MACHINE, "redstone_start_mode_changed")
                .pos("core", worldPosition)
                .field("redstone_mode", redstoneStartMode.id())
                .write();
    }

    private static CompoundTag writePos(BlockPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    private static BlockPos readPos(CompoundTag tag) {
        if (tag.isEmpty()) {
            return BlockPos.ZERO;
        }

        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    public enum RedstoneStartMode {
        IGNORED,
        PULSE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static RedstoneStartMode byOrdinal(int ordinal) {
            RedstoneStartMode[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IGNORED;
        }

        public static RedstoneStartMode byId(String id) {
            for (RedstoneStartMode mode : values()) {
                if (mode.id().equals(id)) {
                    return mode;
                }
            }

            return IGNORED;
        }
    }
}
