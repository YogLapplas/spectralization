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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CompactMachineCoreBlockEntity extends BlockEntity {
    public static final int SLOT_OUTPUT = 0;
    public static final int SLOT_COUNT = 1;

    public static final int DATA_COMPACTING = 0;
    public static final int DATA_PROGRESS = 1;
    public static final int DATA_DURATION = 2;
    public static final int DATA_COUNT = 3;

    private static final String OUTPUT_ITEMS_TAG = "output_items";
    private static final String PENDING_RESULT_TAG = "pending_result";
    private static final String PENDING_MIN_TAG = "pending_work_min";
    private static final String PENDING_MAX_TAG = "pending_work_max";
    private static final String PENDING_AGE_TAG = "pending_age";
    private static final String PENDING_CLEARED_TAG = "pending_cleared";

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
}
