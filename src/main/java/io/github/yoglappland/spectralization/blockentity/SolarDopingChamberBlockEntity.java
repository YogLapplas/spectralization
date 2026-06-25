package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.SolarDopingChamberBlock;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.machine.SolarDopingChanceLut;
import io.github.yoglappland.spectralization.machine.SolarDopingRecipe;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class SolarDopingChamberBlockEntity extends BlockEntity {
    public static final int SLOT_PROCESS = 0;
    public static final int SLOT_FILTER = 1;
    public static final int SLOT_COUNT = 2;

    public static final int STATE_EMPTY = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_RUNNING = 2;
    public static final int STATE_NO_POWER = 3;
    public static final int STATE_COMPLETE = 4;
    public static final int STATE_INVALID = 5;

    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_MAX = 1;
    public static final int DATA_EXPOSURE = 2;
    public static final int DATA_EXPECTED_TICKS = 3;
    public static final int DATA_MAX_TICKS = 4;
    public static final int DATA_CHANCE_PPM = 5;
    public static final int DATA_HEIGHT_PPM = 6;
    public static final int DATA_HEIGHT_MULTIPLIER_PPM = 7;
    public static final int DATA_ARRAY_COUNT = 8;
    public static final int DATA_ARRAY_MULTIPLIER_PPM = 9;
    public static final int DATA_STATE = 10;
    public static final int DATA_RECIPE_COLOR = 11;
    public static final int DATA_DOPING_ENVIRONMENT = 12;
    public static final int DATA_COUNT = 13;

    public static final int ENERGY_PER_TICK = 64;
    private static final int ENERGY_CAPACITY = 32_000;
    private static final int MAX_ENERGY_INPUT = 1_024;
    private static final int ARRAY_SCAN_INTERVAL = 20;
    private static final int ARRAY_SCAN_LIMIT = 16;
    private static final String SUBSYSTEM = "solar_doping";
    private static final String ITEMS_TAG = "items";
    private static final String ENERGY_TAG = "energy";
    private static final String EXPOSURE_TAG = "exposure";

    private int exposureTicks = 0;
    private boolean internalItemMutation = false;
    private int cachedArrayCount = 1;
    private long lastArrayScanTick = Long.MIN_VALUE;

    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(
            ENERGY_CAPACITY,
            MAX_ENERGY_INPUT,
            0,
            this::setChanged
    );

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_PROCESS -> SolarDopingRecipe.isPotentialInput(stack);
                case SLOT_FILTER -> !stack.isEmpty();
                default -> false;
            };
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (!internalItemMutation) {
                exposureTicks = 0;
            }

            syncChanged();
            logSlotChanged(slot);
        }
    };

    private final IItemHandler sidedItems = new SidedItems();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return getData(index);
        }

        @Override
        public void set(int index, int value) {
            setData(index, value);
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SolarDopingChamberBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.SOLAR_DOPING_CHAMBER.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, SolarDopingChamberBlockEntity chamber) {
        if (level.isClientSide) {
            return;
        }

        chamber.tickServer(level);
    }

    public ItemStackHandler items() {
        return items;
    }

    public ItemStack getStackForDisplay() {
        return items.getStackInSlot(SLOT_PROCESS);
    }

    @Nullable
    public IItemHandler getItems(@Nullable Direction side) {
        return side == null ? items : sidedItems;
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energy;
    }

    public ContainerData createDataAccess() {
        return data;
    }

    public void dropContents(Level level, BlockPos pos) {
        SpectralDiagnostics.event(level, SUBSYSTEM, "contents_dropped")
                .pos("machine", pos)
                .field("non_empty_slots", nonEmptySlotCount())
                .write();

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.getStackInSlot(slot);

            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    public static double heightMultiplier(Level level, BlockPos pos) {
        double normalized = heightNormalized(level, pos);
        return 0.35 + Math.pow(normalized, 1.35) * 1.65;
    }

    public static double heightNormalized(Level level, BlockPos pos) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        if (maxY <= minY) {
            return 0.0;
        }

        return Mth.clamp((pos.getY() - minY) / (double) (maxY - minY), 0.0, 1.0);
    }

    public static double arrayMultiplier(int chamberCount) {
        int count = Math.max(1, chamberCount);
        return Math.min(5.0, 1.0 + count * count / 64.0);
    }

    private void tickServer(Level level) {
        refreshArrayCache(level);
        Optional<SolarDopingRecipe> match = activeRecipe();

        if (match.isEmpty()) {
            setActiveState(false);
            if (exposureTicks != 0) {
                exposureTicks = 0;
                setChanged();
            }
            return;
        }

        SolarDopingRecipe recipe = match.get();
        if (!consumeRecipeEnergy(true)) {
            setActiveState(false);
            return;
        }

        consumeRecipeEnergy(false);
        exposureTicks++;
        setActiveState(true);

        double effectiveBaseChance = effectiveBaseChance(recipe);
        double chance = Math.min(1.0, effectiveBaseChance * exposureTicks);
        if (level.random.nextDouble() < chance) {
            completeRecipe(recipe);
        } else {
            setChanged();
        }
    }

    private Optional<SolarDopingRecipe> activeRecipe() {
        return SolarDopingRecipe.find(
                items.getStackInSlot(SLOT_PROCESS),
                items.getStackInSlot(SLOT_FILTER),
                dopingEnvironment()
        );
    }

    private void completeRecipe(SolarDopingRecipe recipe) {
        ItemStack result = recipe.rollResult(level == null ? net.minecraft.util.RandomSource.create() : level.random);

        internalItemMutation = true;
        try {
            items.setStackInSlot(SLOT_PROCESS, result);
        } finally {
            internalItemMutation = false;
        }

        exposureTicks = 0;
        setActiveState(false);
        syncChanged();

        if (level != null && !level.isClientSide) {
            SpectralDiagnostics.event(level, SUBSYSTEM, "recipe_completed")
                    .pos("machine", worldPosition)
                    .field("recipe", recipe.id())
                    .field("result", BuiltInRegistries.ITEM.getKey(result.getItem()))
                    .field("environment", dopingEnvironment().name().toLowerCase(java.util.Locale.ROOT))
                    .field("array_count", cachedArrayCount)
                    .field("height_multiplier", heightMultiplier(level, worldPosition))
                    .write();
        }
    }

    private boolean consumeRecipeEnergy(boolean simulate) {
        if (energy.getEnergyStored() < ENERGY_PER_TICK) {
            return false;
        }

        if (!simulate) {
            energy.setEnergyStored(energy.getEnergyStored() - ENERGY_PER_TICK);
        }

        return true;
    }

    private double effectiveBaseChance(SolarDopingRecipe recipe) {
        if (level == null) {
            return recipe.effectiveBaseChance(1.0, 1.0);
        }

        return recipe.effectiveBaseChance(heightMultiplier(level, worldPosition), arrayMultiplier(cachedArrayCount));
    }

    private int getData(int index) {
        Optional<SolarDopingRecipe> match = activeRecipe();
        SolarDopingRecipe recipe = match.orElse(null);
        double heightMultiplier = level == null ? 1.0 : heightMultiplier(level, worldPosition);
        double heightNormalized = level == null ? 0.0 : heightNormalized(level, worldPosition);
        double arrayMultiplier = arrayMultiplier(cachedArrayCount);
        double effectiveBaseChance = recipe == null ? 0.0 : recipe.effectiveBaseChance(heightMultiplier, arrayMultiplier);
        SolarDopingChanceLut.Estimate estimate = SolarDopingChanceLut.estimate(effectiveBaseChance);

        return switch (index) {
            case DATA_ENERGY -> energy.getEnergyStored();
            case DATA_ENERGY_MAX -> energy.getMaxEnergyStored();
            case DATA_EXPOSURE -> exposureTicks;
            case DATA_EXPECTED_TICKS -> estimate.expectedTicks();
            case DATA_MAX_TICKS -> estimate.maxTicks();
            case DATA_CHANCE_PPM -> SolarDopingChanceLut.chancePpm(effectiveBaseChance, Math.max(1, exposureTicks + 1));
            case DATA_HEIGHT_PPM -> (int) Math.round(heightNormalized * SolarDopingChanceLut.PPM);
            case DATA_HEIGHT_MULTIPLIER_PPM -> (int) Math.round(heightMultiplier * 1_000.0);
            case DATA_ARRAY_COUNT -> cachedArrayCount;
            case DATA_ARRAY_MULTIPLIER_PPM -> (int) Math.round(arrayMultiplier * 1_000.0);
            case DATA_STATE -> stateCode(match.isPresent());
            case DATA_RECIPE_COLOR -> recipe == null ? 0xFF9FE7DF : recipe.accentColor();
            case DATA_DOPING_ENVIRONMENT -> dopingEnvironment().dataId();
            default -> 0;
        };
    }

    private int stateCode(boolean hasRecipe) {
        ItemStack process = items.getStackInSlot(SLOT_PROCESS);

        if (process.isEmpty()) {
            return STATE_EMPTY;
        }

        if (!hasRecipe && SolarDopingRecipe.isKnownResult(process)) {
            return STATE_COMPLETE;
        }

        if (!hasRecipe) {
            return STATE_INVALID;
        }

        return energy.getEnergyStored() >= ENERGY_PER_TICK ? (exposureTicks > 0 ? STATE_RUNNING : STATE_READY) : STATE_NO_POWER;
    }

    private void setData(int index, int value) {
        if (index == DATA_ENERGY) {
            energy.setEnergyStored(value);
        }
    }

    private void refreshArrayCache(Level level) {
        long gameTime = level.getGameTime();
        if (lastArrayScanTick != Long.MIN_VALUE && gameTime - lastArrayScanTick < ARRAY_SCAN_INTERVAL) {
            return;
        }

        lastArrayScanTick = gameTime;
        cachedArrayCount = scanConnectedChambers(level);
    }

    private int scanConnectedChambers(Level level) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);

        while (!queue.isEmpty() && visited.size() < ARRAY_SCAN_LIMIT) {
            BlockPos current = queue.removeFirst();

            if (!visited.add(current.immutable())) {
                continue;
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (!visited.contains(next) && level.getBlockState(next).is(getBlockState().getBlock())) {
                    queue.addLast(next);
                }
            }
        }

        return Math.max(1, visited.size());
    }

    private void setActiveState(boolean active) {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof SolarDopingChamberBlock)) {
            return;
        }

        if (getBlockState().getValue(SolarDopingChamberBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(SolarDopingChamberBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private SolarDopingRecipe.DopingEnvironment dopingEnvironment() {
        if (level == null) {
            return SolarDopingRecipe.DopingEnvironment.OVERWORLD;
        }

        if (getBlockState().is(Spectralization.DIMENSION_DOPING_CHAMBER.get())) {
            return SolarDopingRecipe.DopingEnvironment.fromLevel(level);
        }

        return SolarDopingRecipe.DopingEnvironment.solarFromLevel(level);
    }

    private void logSlotChanged(int slot) {
        if (level == null || level.isClientSide) {
            return;
        }

        ItemStack stack = items.getStackInSlot(slot);
        SpectralDiagnostics.event(level, SUBSYSTEM, "slot_changed")
                .pos("machine", worldPosition)
                .field("slot", slot)
                .field("role", slot == SLOT_PROCESS ? "process" : "filter")
                .field("item", stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .field("count", stack.getCount())
                .write();
    }

    private int nonEmptySlotCount() {
        int count = 0;

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                count++;
            }
        }

        return count;
    }

    private void syncChanged() {
        setChanged();

        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        exposureTicks = Math.max(0, tag.getInt(EXPOSURE_TAG));

        if (tag.contains(ITEMS_TAG)) {
            items.deserializeNBT(registries, tag.getCompound(ITEMS_TAG));
        }

        if (items.getStackInSlot(SLOT_PROCESS).isEmpty()) {
            exposureTicks = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ITEMS_TAG, items.serializeNBT(registries));
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.putInt(EXPOSURE_TAG, exposureTicks);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    private final class SidedItems implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= SLOT_COUNT || !items.isItemValid(slot, stack)) {
                return stack;
            }

            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == SLOT_PROCESS && !canExtractProcessSlot()) {
                return ItemStack.EMPTY;
            }

            if (slot < 0 || slot >= SLOT_COUNT) {
                return ItemStack.EMPTY;
            }

            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < SLOT_COUNT && items.isItemValid(slot, stack);
        }

        private boolean canExtractProcessSlot() {
            ItemStack process = items.getStackInSlot(SLOT_PROCESS);
            return process.isEmpty() || activeRecipe().isEmpty();
        }
    }
}
