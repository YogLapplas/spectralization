package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.block.ThermalSmelterBlock;
import io.github.yoglappland.spectralization.heat.PhotothermalAbsorberProfile;
import io.github.yoglappland.spectralization.heat.PhotothermalCouplingModel;
import io.github.yoglappland.spectralization.heat.PhotothermalCouplingResult;
import io.github.yoglappland.spectralization.heat.PhotothermalReadoutSample;
import io.github.yoglappland.spectralization.heat.PhotothermalReceiver;
import io.github.yoglappland.spectralization.heat.SimpleSpectralHeatStorage;
import io.github.yoglappland.spectralization.machine.ThermalSmelterBalance;
import io.github.yoglappland.spectralization.machine.ThermalSmelterRecipe;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ThermalSmelterBlockEntity extends BlockEntity implements PhotothermalReceiver, DropsContentsOnRemove {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_ADDITIVE = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_OUTPUT_SECOND = 3;
    public static final int SLOT_COUNT = 4;

    public static final int DATA_TEMPERATURE = 0;
    public static final int DATA_HEAT = 1;
    public static final int DATA_MAX_HEAT = 2;
    public static final int DATA_PROGRESS = 3;
    public static final int DATA_PROGRESS_REQUIRED = 4;
    public static final int DATA_HEAT_POWER_X100 = 5;
    public static final int DATA_PARALLEL_COUNT = 6;
    public static final int DATA_COUNT = 7;

    private static final long SAMPLE_HOLD_TICKS = 1L;
    private static final String ITEMS_TAG = "items";
    private static final String HEAT_TAG = "heat";
    private static final String PROGRESS_TAG = "progress";
    private static final String OPTICAL_POWER_TAG = "optical_power";
    private static final String PARALLEL_COUNT_TAG = "parallel_count";
    public static final int FULL_SP_HEAT_POWER_X100 = ThermalSmelterBalance.FULL_SP_HEAT_POWER_X100;

    private final SimpleSpectralHeatStorage heat = new SimpleSpectralHeatStorage(
            ThermalSmelterBalance.HEAT_CAPACITY,
            ThermalSmelterBalance.AMBIENT_TEMPERATURE,
            ThermalSmelterBalance.MAX_TEMPERATURE,
            this::setChanged
    );
    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_INPUT -> ThermalSmelterRecipe.isProcessable(stack);
                case SLOT_ADDITIVE -> ThermalSmelterRecipe.isPotentialAdditive(stack);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return getData(index);
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    private long lastReceivedGameTime = Long.MIN_VALUE;
    private long lastReceivedSampleStep = Long.MIN_VALUE;
    private long lastObservedSampleStep = Long.MIN_VALUE;
    private PhotothermalCouplingResult receivedCouplingThisStep = PhotothermalCouplingResult.zero();
    private boolean receivedReliableThisStep = false;
    private PhotothermalCouplingResult committedCoupling = PhotothermalCouplingResult.zero();
    private int progress = 0;
    private int progressRequired = 0;
    private int parallelCount = 1;

    public ThermalSmelterBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.THERMAL_SMELTER.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, ThermalSmelterBlockEntity smelter) {
        if (level.isClientSide) {
            return;
        }

        smelter.tickOpticalSample(level);
        smelter.applyOpticalHeat();
        smelter.tickRecipe();
        smelter.applyPassiveCooling();
    }

    public ItemStackHandler items() {
        return items;
    }

    @Nullable
    public ItemStackHandler getItems(@Nullable Direction side) {
        return side == null || side == ThermalSmelterBlock.ITEM_PORT_SIDE ? items : null;
    }

    public ContainerData createDataAccess() {
        return data;
    }

    public int dataValue(int index) {
        return getData(index);
    }

    public Optional<ThermalSmelterRecipe> activeRecipe() {
        return ThermalSmelterRecipe.find(items.getStackInSlot(SLOT_INPUT), items.getStackInSlot(SLOT_ADDITIVE));
    }

    public boolean outputBlocked() {
        return activeRecipe()
                .map(recipe -> !canAccept(recipe.resultStack()))
                .orElse(false);
    }

    public int parallelCount() {
        return parallelCount;
    }

    public boolean upgradeParallelCount() {
        if (parallelCount >= ThermalSmelterBalance.MAX_PARALLEL_COUNT) {
            return false;
        }

        parallelCount++;
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }

        return true;
    }

    @Override
    public PhotothermalAbsorberProfile photothermalAbsorberProfile() {
        return PhotothermalAbsorberProfile.EARLY_ABSORBING_COATING;
    }

    @Override
    public PhotothermalCouplingResult photothermalCoupling() {
        return committedCoupling;
    }

    @Override
    public void receivePhotothermalSample(PhotothermalReadoutSample sample) {
        if (this.level == null || this.level.isClientSide) {
            return;
        }

        long gameTime = this.level.getGameTime();
        this.lastReceivedGameTime = gameTime;
        PhotothermalCouplingResult coupling = PhotothermalCouplingModel.calculate(
                sample,
                photothermalAbsorberProfile()
        );

        if (this.lastReceivedSampleStep == sample.step()) {
            this.receivedCouplingThisStep = combine(receivedCouplingThisStep, coupling);
            this.receivedReliableThisStep &= sample.reliable();
        } else {
            this.lastReceivedSampleStep = sample.step();
            this.receivedCouplingThisStep = coupling;
            this.receivedReliableThisStep = sample.reliable();
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        MachineContentsDropper.dropItemHandler(level, pos, items);
    }

    private void tickOpticalSample(Level level) {
        if (level.getGameTime() - this.lastReceivedGameTime > SAMPLE_HOLD_TICKS) {
            commitCoupling(PhotothermalCouplingResult.zero());
            return;
        }

        if (this.lastReceivedSampleStep == this.lastObservedSampleStep) {
            return;
        }

        this.lastObservedSampleStep = this.lastReceivedSampleStep;
        commitCoupling(receivedReliableThisStep ? receivedCouplingThisStep : PhotothermalCouplingResult.zero());
    }

    private void commitCoupling(PhotothermalCouplingResult coupling) {
        if (Math.abs(committedCoupling.heatPower() - coupling.heatPower()) > 1.0E-6
                || Math.abs(committedCoupling.totalEfficiency() - coupling.totalEfficiency()) > 1.0E-6) {
            committedCoupling = coupling;
            setChanged();
        }
    }

    private void applyOpticalHeat() {
        double heatPower = acceptedCompatibleHeatPower(committedCoupling.heatPower());

        if (heatPower > 0.0) {
            heat.insertHeat(heatPower, false);
        }
    }

    private void applyPassiveCooling() {
        double cooling = ThermalSmelterBalance.passiveCooling(
                heat.temperature(),
                heat.ambientTemperature(),
                heat.heatStored()
        );

        if (cooling > 0.0) {
            heat.extractHeat(cooling, false);
        }
    }

    private void tickRecipe() {
        ItemStack input = items.getStackInSlot(SLOT_INPUT);
        ItemStack additive = items.getStackInSlot(SLOT_ADDITIVE);
        var recipe = ThermalSmelterRecipe.find(input, additive);

        if (recipe.isEmpty()) {
            resetProgress();
            return;
        }

        ThermalSmelterRecipe active = recipe.get();
        progressRequired = active.processTicks();

        int batchSize = processBatchSize(active, input, additive);

        if (batchSize <= 0) {
            progress = Math.max(0, progress - 1);
            setChanged();
            return;
        }

        double heatPerTick = active.heatCost() * batchSize / active.processTicks();

        if (heat.temperature() < active.minimumTemperature()
                || heat.extractHeat(heatPerTick, true) + 1.0E-9 < heatPerTick) {
            progress = Math.max(0, progress - 1);
            setChanged();
            return;
        }

        heat.extractHeat(heatPerTick, false);
        progress++;

        if (progress >= active.processTicks()) {
            completeRecipe(active, input, additive, batchSize);
            progress = 0;
        }

        setChanged();
    }

    private void completeRecipe(ThermalSmelterRecipe recipe, ItemStack input, ItemStack additive, int batchSize) {
        ItemStack result = recipe.resultStack();

        input.shrink(batchSize);
        items.setStackInSlot(SLOT_INPUT, input);

        if (recipe.consumesAdditive()) {
            additive.shrink(recipe.additiveCost() * batchSize);
            items.setStackInSlot(SLOT_ADDITIVE, additive);
        }

        result.setCount(result.getCount() * batchSize);
        insertResult(result);
    }

    private int processBatchSize(ThermalSmelterRecipe recipe, ItemStack input, ItemStack additive) {
        int byInput = Math.min(parallelCount, input.getCount());
        int byAdditive = byInput;

        if (recipe.consumesAdditive()) {
            byAdditive = additive.getCount() / recipe.additiveCost();
        }

        return Math.max(0, Math.min(Math.min(byInput, byAdditive), acceptedBatchSize(recipe.resultStack())));
    }

    private int acceptedBatchSize(ItemStack result) {
        int accepted = 0;

        for (int slot = SLOT_OUTPUT; slot <= SLOT_OUTPUT_SECOND; slot++) {
            accepted += outputCapacityFor(items.getStackInSlot(slot), result, slot);
        }

        return Math.min(parallelCount, accepted);
    }

    private int outputCapacityFor(ItemStack output, ItemStack result, int slot) {
        int resultCount = Math.max(1, result.getCount());
        int slotLimit = Math.min(result.getMaxStackSize(), items.getSlotLimit(slot));

        if (output.isEmpty()) {
            return slotLimit / resultCount;
        }

        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return 0;
        }

        return Math.max(0, slotLimit - output.getCount()) / resultCount;
    }

    private boolean canAccept(ItemStack result) {
        return acceptedBatchSize(result) > 0;
    }

    private void insertResult(ItemStack result) {
        int remaining = result.getCount();

        for (int slot = SLOT_OUTPUT; slot <= SLOT_OUTPUT_SECOND && remaining > 0; slot++) {
            ItemStack output = items.getStackInSlot(slot);
            int slotLimit = Math.min(result.getMaxStackSize(), items.getSlotLimit(slot));

            if (output.isEmpty()) {
                ItemStack inserted = result.copy();
                inserted.setCount(Math.min(remaining, slotLimit));
                items.setStackInSlot(slot, inserted);
                remaining -= inserted.getCount();
            } else if (ItemStack.isSameItemSameComponents(output, result)) {
                int moved = Math.min(remaining, slotLimit - output.getCount());

                if (moved > 0) {
                    output.grow(moved);
                    items.setStackInSlot(slot, output);
                    remaining -= moved;
                }
            }
        }
    }

    private void resetProgress() {
        if (progress != 0 || progressRequired != 0) {
            progress = 0;
            progressRequired = 0;
            setChanged();
        }
    }

    private int getData(int index) {
        return switch (index) {
            case DATA_TEMPERATURE -> (int) Math.round(heat.temperature());
            case DATA_HEAT -> (int) Math.round(heat.heatStored());
            case DATA_MAX_HEAT -> (int) Math.round(heat.maxHeatStored());
            case DATA_PROGRESS -> progress;
            case DATA_PROGRESS_REQUIRED -> progressRequired;
            case DATA_HEAT_POWER_X100 -> (int) Math.round(acceptedCompatibleHeatPower(committedCoupling.heatPower()) * 100.0);
            case DATA_PARALLEL_COUNT -> parallelCount;
            default -> 0;
        };
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heat.setHeatStored(tag.getDouble(HEAT_TAG));
        progress = tag.getInt(PROGRESS_TAG);
        parallelCount = clampParallelCount(tag.contains(PARALLEL_COUNT_TAG) ? tag.getInt(PARALLEL_COUNT_TAG) : 1);
        committedCoupling = restoredCoupling(tag.getDouble(OPTICAL_POWER_TAG));

        if (tag.contains(ITEMS_TAG)) {
            items.deserializeNBT(registries, tag.getCompound(ITEMS_TAG));
            normalizeItemSlots();
        }

    }

    private void normalizeItemSlots() {
        if (items.getSlots() == SLOT_COUNT) {
            return;
        }

        ItemStack[] savedStacks = new ItemStack[Math.min(items.getSlots(), SLOT_COUNT)];

        for (int slot = 0; slot < savedStacks.length; slot++) {
            savedStacks[slot] = items.getStackInSlot(slot).copy();
        }

        items.setSize(SLOT_COUNT);

        for (int slot = 0; slot < savedStacks.length; slot++) {
            items.setStackInSlot(slot, savedStacks[slot]);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ITEMS_TAG, items.serializeNBT(registries));
        tag.putDouble(HEAT_TAG, heat.heatStored());
        tag.putInt(PROGRESS_TAG, progress);
        tag.putInt(PARALLEL_COUNT_TAG, parallelCount);
        tag.putDouble(OPTICAL_POWER_TAG, committedCoupling.heatPower());
    }

    private static int clampParallelCount(int value) {
        return Math.max(1, Math.min(ThermalSmelterBalance.MAX_PARALLEL_COUNT, value));
    }

    public static double acceptedCompatibleHeatPower(double opticalHeatPower) {
        return ThermalSmelterBalance.acceptedCompatibleHeatPower(opticalHeatPower);
    }

    private static PhotothermalCouplingResult combine(
            PhotothermalCouplingResult left,
            PhotothermalCouplingResult right
    ) {
        if (left.inputPower() <= 0.0) {
            return right;
        }

        if (right.inputPower() <= 0.0) {
            return left;
        }

        double inputPower = left.inputPower() + right.inputPower();
        double heatPower = left.heatPower() + right.heatPower();
        double absorbedPower = left.absorbedOpticalPower() + right.absorbedOpticalPower();

        return new PhotothermalCouplingResult(
                inputPower,
                absorbedPower,
                heatPower,
                weightedAverage(left.spectralEfficiency(), left.inputPower(), right.spectralEfficiency(), right.inputPower()),
                weightedAverage(left.radiusEfficiency(), left.inputPower(), right.radiusEfficiency(), right.inputPower()),
                weightedAverage(left.uniformityEfficiency(), left.inputPower(), right.uniformityEfficiency(), right.inputPower()),
                inputPower <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, heatPower / inputPower)),
                weightedAverage(left.beamRadius(), left.inputPower(), right.beamRadius(), right.inputPower()),
                Math.max(left.irradiance(), right.irradiance()),
                left.state().ordinal() >= right.state().ordinal() ? left.state() : right.state()
        );
    }

    private static double weightedAverage(double left, double leftWeight, double right, double rightWeight) {
        double totalWeight = leftWeight + rightWeight;
        return totalWeight <= 0.0 ? 0.0 : (left * leftWeight + right * rightWeight) / totalWeight;
    }

    private static PhotothermalCouplingResult restoredCoupling(double heatPower) {
        double power = Double.isFinite(heatPower) && heatPower > 0.0 ? heatPower : 0.0;

        if (power <= 0.0) {
            return PhotothermalCouplingResult.zero();
        }

        return new PhotothermalCouplingResult(
                power,
                power,
                power,
                1.0,
                1.0,
                1.0,
                1.0,
                PhotothermalAbsorberProfile.EARLY_ABSORBING_COATING.absorptionRadius(),
                0.0,
                io.github.yoglappland.spectralization.heat.PhotothermalCouplingState.MATCHED
        );
    }
}
