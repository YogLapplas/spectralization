package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.block.BasicLithographyMachineBlock;
import io.github.yoglappland.spectralization.energy.SpectralEnergyStorage;
import io.github.yoglappland.spectralization.heat.PhotothermalAbsorberProfile;
import io.github.yoglappland.spectralization.heat.PhotothermalCouplingModel;
import io.github.yoglappland.spectralization.heat.PhotothermalCouplingResult;
import io.github.yoglappland.spectralization.heat.PhotothermalReadoutSample;
import io.github.yoglappland.spectralization.heat.PhotothermalReceiver;
import io.github.yoglappland.spectralization.machine.BasicLithographyRecipe;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.tag.SpectralItemTags;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public class BasicLithographyMachineBlockEntity extends BlockEntity implements PhotothermalReceiver {
    public static final int SLOT_TEMPLATE_INPUT_A = 0;
    public static final int SLOT_TEMPLATE_INPUT_B = 1;
    public static final int SLOT_ITEM_INPUT_0 = 2;
    public static final int SLOT_ITEM_INPUT_1 = 3;
    public static final int SLOT_ITEM_INPUT_2 = 4;
    public static final int SLOT_ITEM_INPUT_3 = 5;
    public static final int SLOT_TEMPLATE_OUTPUT_A = 6;
    public static final int SLOT_TEMPLATE_OUTPUT_B = 7;
    public static final int SLOT_ITEM_OUTPUT_0 = 8;
    public static final int SLOT_ITEM_OUTPUT_1 = 9;
    public static final int SLOT_ITEM_OUTPUT_2 = 10;
    public static final int SLOT_ITEM_OUTPUT_3 = 11;
    public static final int SLOT_COUNT = 12;

    public static final int TEMPLATE_RULE_KEEP = 0;
    public static final int TEMPLATE_RULE_MOVE_USED = 1;

    public static final int DATA_TEMPLATE_RULE = 0;
    public static final int DATA_ENERGY = 1;
    public static final int DATA_ENERGY_MAX = 2;
    public static final int DATA_OPTICAL_POWER = 3;
    public static final int DATA_OPTICAL_POWER_MAX = 4;
    public static final int DATA_PROGRESS = 5;
    public static final int DATA_PROGRESS_REQUIRED = 6;
    public static final int DATA_READY = 7;
    public static final int DATA_OUTPUT_BLOCKED = 8;
    public static final int DATA_REDSTONE_MODE = 9;
    public static final int DATA_FACE_DOWN = 10;
    public static final int DATA_FACE_UP = 11;
    public static final int DATA_FACE_NORTH = 12;
    public static final int DATA_FACE_SOUTH = 13;
    public static final int DATA_FACE_WEST = 14;
    public static final int DATA_FACE_EAST = 15;
    public static final int DATA_COUNT = 16;

    private static final long SAMPLE_HOLD_TICKS = 1L;
    public static final int ENERGY_PER_TICK = 100;
    private static final int ENERGY_CAPACITY = 20_000;
    private static final int MAX_ENERGY_INPUT = 1_000;
    private static final int FULL_COHERENT_POWER_X100 = 100;
    private static final String ITEMS_TAG = "items";
    private static final String TEMPLATE_RULE_TAG = "template_rule";
    private static final String ENERGY_TAG = "energy";
    private static final String PROGRESS_TAG = "progress";
    private static final String OPTICAL_POWER_TAG = "coherent_power";
    private static final String REDSTONE_MODE_TAG = "redstone_mode";
    private static final String FACE_MODES_TAG = "face_modes";

    private int templateRule = TEMPLATE_RULE_KEEP;
    private RedstoneControlMode redstoneControlMode = RedstoneControlMode.IGNORED;
    private int progress = 0;
    private int progressRequired = 0;
    private long lastReceivedGameTime = Long.MIN_VALUE;
    private long lastReceivedSampleStep = Long.MIN_VALUE;
    private long lastObservedSampleStep = Long.MIN_VALUE;
    private PhotothermalCouplingResult receivedCouplingThisStep = PhotothermalCouplingResult.zero();
    private PhotothermalCouplingResult committedCoupling = PhotothermalCouplingResult.zero();
    private double receivedCoherentPowerThisStep = 0.0;
    private double committedCoherentPower = 0.0;
    private boolean receivedReliableThisStep = false;
    private final SpectralEnergyStorage energy = new SpectralEnergyStorage(
            ENERGY_CAPACITY,
            MAX_ENERGY_INPUT,
            0,
            this::setChanged
    );
    private final EnumMap<Direction, AutomationFaceMode> faceModes = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, IItemHandler> sidedItemHandlers = new EnumMap<>(Direction.class);

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_TEMPLATE_INPUT_A, SLOT_TEMPLATE_INPUT_B -> isTemplate(stack);
                case SLOT_ITEM_INPUT_0, SLOT_ITEM_INPUT_1, SLOT_ITEM_INPUT_2, SLOT_ITEM_INPUT_3 ->
                        BasicLithographyRecipe.isPotentialInput(stack);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            logSlotChanged(slot);
        }
    };

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

    public BasicLithographyMachineBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.BASIC_LITHOGRAPHY_MACHINE.get(), pos, blockState);
        for (Direction direction : Direction.values()) {
            faceModes.put(direction, AutomationFaceMode.INPUT_OUTPUT);
            sidedItemHandlers.put(direction, new SidedItems(direction));
        }
    }

    public static void tick(Level level, BlockPos pos, BasicLithographyMachineBlockEntity machine) {
        if (level.isClientSide) {
            return;
        }

        machine.tickOpticalSample(level);
        machine.tickRecipe();
    }

    public ItemStackHandler items() {
        return items;
    }

    @Nullable
    public IItemHandler getItems(@Nullable Direction side) {
        return side == null ? items : sidedItemHandlers.get(side);
    }

    @Nullable
    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energy;
    }

    public ContainerData createDataAccess() {
        return data;
    }

    public void toggleTemplateRule() {
        templateRule = templateRule == TEMPLATE_RULE_KEEP ? TEMPLATE_RULE_MOVE_USED : TEMPLATE_RULE_KEEP;
        setChanged();
        logTemplateRuleChanged();
    }

    public RedstoneControlMode redstoneControlMode() {
        return redstoneControlMode;
    }

    public void cycleRedstoneControlMode(boolean reverse) {
        redstoneControlMode = redstoneControlMode.next(reverse);
        setChanged();
        logRedstoneModeChanged();
    }

    public AutomationFaceMode faceMode(Direction side) {
        return faceModes.getOrDefault(side, AutomationFaceMode.INPUT_OUTPUT);
    }

    public void cycleFaceMode(Direction side, boolean reverse) {
        faceModes.put(side, faceMode(side).next(reverse));
        setChanged();
        logFaceModeChanged(side);
    }

    @Override
    public PhotothermalAbsorberProfile photothermalAbsorberProfile() {
        return PhotothermalAbsorberProfile.DEFAULT_MACHINE_FACE;
    }

    @Override
    public PhotothermalCouplingResult photothermalCoupling() {
        return committedCoupling;
    }

    @Override
    public void receivePhotothermalSample(PhotothermalReadoutSample sample) {
        if (level == null || level.isClientSide) {
            return;
        }

        lastReceivedGameTime = level.getGameTime();
        PhotothermalCouplingResult coupling = PhotothermalCouplingModel.calculate(sample, photothermalAbsorberProfile());

        if (lastReceivedSampleStep == sample.step()) {
            receivedCouplingThisStep = combine(receivedCouplingThisStep, coupling);
            receivedCoherentPowerThisStep += sample.coherentPower();
            receivedReliableThisStep &= sample.reliable();
        } else {
            lastReceivedSampleStep = sample.step();
            receivedCouplingThisStep = coupling;
            receivedCoherentPowerThisStep = sample.coherentPower();
            receivedReliableThisStep = sample.reliable();
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.LITHOGRAPHY, "contents_dropped")
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

    public static boolean isTemplate(ItemStack stack) {
        return stack.is(SpectralItemTags.METASURFACE_TEMPLATE) || stack.is(SpectralItemTags.LITHOGRAPHY_MASK);
    }

    public static boolean isTemplateInputSlot(int slot) {
        return slot == SLOT_TEMPLATE_INPUT_A || slot == SLOT_TEMPLATE_INPUT_B;
    }

    public static boolean isItemInputSlot(int slot) {
        return slot >= SLOT_ITEM_INPUT_0 && slot <= SLOT_ITEM_INPUT_3;
    }

    public static boolean isOutputSlot(int slot) {
        return slot >= SLOT_TEMPLATE_OUTPUT_A;
    }

    private void tickOpticalSample(Level level) {
        if (level.getGameTime() - lastReceivedGameTime > SAMPLE_HOLD_TICKS) {
            commitOpticalSample(PhotothermalCouplingResult.zero(), 0.0);
            return;
        }

        if (lastReceivedSampleStep == lastObservedSampleStep) {
            return;
        }

        lastObservedSampleStep = lastReceivedSampleStep;

        if (receivedReliableThisStep) {
            commitOpticalSample(receivedCouplingThisStep, receivedCoherentPowerThisStep);
        } else {
            commitOpticalSample(PhotothermalCouplingResult.zero(), 0.0);
        }
    }

    private void commitOpticalSample(PhotothermalCouplingResult coupling, double coherentPower) {
        double safeCoherentPower = Double.isFinite(coherentPower) ? Math.max(0.0, coherentPower) : 0.0;

        if (Math.abs(committedCoherentPower - safeCoherentPower) > 1.0E-6
                || Math.abs(committedCoupling.inputPower() - coupling.inputPower()) > 1.0E-6
                || Math.abs(committedCoupling.totalEfficiency() - coupling.totalEfficiency()) > 1.0E-6) {
            committedCoupling = coupling;
            committedCoherentPower = safeCoherentPower;
            setChanged();
        }
    }

    private void tickRecipe() {
        Optional<BasicLithographyRecipe.Match> match = activeMatch();

        if (match.isEmpty()) {
            setActiveState(false);
            resetProgress();
            return;
        }

        BasicLithographyRecipe active = match.get().recipe();
        progressRequired = active.processTicks();

        if (!redstoneAllowsWork()
                || outputBlocked(match.get())
                || committedCoherentPower + 1.0E-9 < active.requiredCoherentPower()
                || !consumeRecipeEnergy(true)) {
            setActiveState(false);
            progress = Math.max(0, progress - 1);
            setChanged();
            return;
        }

        setActiveState(true);
        consumeRecipeEnergy(false);
        progress++;

        if (progress >= active.processTicks()) {
            completeRecipe(match.get());
            progress = 0;
        }

        setChanged();
    }

    private void setActiveState(boolean active) {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof BasicLithographyMachineBlock)) {
            return;
        }

        if (getBlockState().getValue(BasicLithographyMachineBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(BasicLithographyMachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    private Optional<BasicLithographyRecipe.Match> activeMatch() {
        return BasicLithographyRecipe.find(
                items.getStackInSlot(SLOT_TEMPLATE_INPUT_A),
                items.getStackInSlot(SLOT_TEMPLATE_INPUT_B),
                List.of(
                        items.getStackInSlot(SLOT_ITEM_INPUT_0),
                        items.getStackInSlot(SLOT_ITEM_INPUT_1),
                        items.getStackInSlot(SLOT_ITEM_INPUT_2),
                        items.getStackInSlot(SLOT_ITEM_INPUT_3)
                )
        );
    }

    private void completeRecipe(BasicLithographyRecipe.Match match) {
        BasicLithographyRecipe recipe = match.recipe();

        consumeItemCosts(recipe);

        if (recipe.usesTemplate() && templateRule == TEMPLATE_RULE_MOVE_USED && match.templateSlot() >= 0) {
            moveUsedTemplateToOutput(match.templateSlot());
        }

        insertResult(recipe.resultStack(), resultOutputStart(recipe), resultOutputEnd(recipe));
    }

    private void consumeItemCosts(BasicLithographyRecipe recipe) {
        for (BasicLithographyRecipe.ItemCost cost : recipe.itemCosts()) {
            int remaining = cost.count();

            for (int slot = SLOT_ITEM_INPUT_0; slot <= SLOT_ITEM_INPUT_3 && remaining > 0; slot++) {
                ItemStack stack = items.getStackInSlot(slot);

                if (!stack.is(cost.item())) {
                    continue;
                }

                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                items.setStackInSlot(slot, stack);
                remaining -= consumed;
            }
        }
    }

    private void moveUsedTemplateToOutput(int relativeTemplateSlot) {
        int slot = relativeTemplateSlot == 0 ? SLOT_TEMPLATE_INPUT_A : SLOT_TEMPLATE_INPUT_B;
        ItemStack source = items.getStackInSlot(slot);

        if (source.isEmpty()) {
            return;
        }

        ItemStack moved = source.copy();
        moved.setCount(1);
        source.shrink(1);
        items.setStackInSlot(slot, source);
        insertResult(moved, SLOT_TEMPLATE_OUTPUT_A, SLOT_TEMPLATE_OUTPUT_B);
    }

    private boolean outputBlocked() {
        return activeMatch()
                .map(this::outputBlocked)
                .orElse(false);
    }

    private boolean outputBlocked(BasicLithographyRecipe.Match match) {
        BasicLithographyRecipe recipe = match.recipe();
        ItemStack result = recipe.resultStack();

        if (acceptedCount(result, resultOutputStart(recipe), resultOutputEnd(recipe)) < result.getCount()) {
            return true;
        }

        if (recipe.usesTemplate() && templateRule == TEMPLATE_RULE_MOVE_USED && match.templateSlot() >= 0) {
            ItemStack template = items.getStackInSlot(match.templateSlot() == 0 ? SLOT_TEMPLATE_INPUT_A : SLOT_TEMPLATE_INPUT_B);
            ItemStack moved = template.copy();
            moved.setCount(1);
            return acceptedCount(moved, SLOT_TEMPLATE_OUTPUT_A, SLOT_TEMPLATE_OUTPUT_B) < 1;
        }

        return false;
    }

    private int acceptedCount(ItemStack result, int firstSlot, int lastSlot) {
        if (result.isEmpty()) {
            return 0;
        }

        int accepted = 0;

        for (int slot = firstSlot; slot <= lastSlot; slot++) {
            accepted += outputCapacityFor(items.getStackInSlot(slot), result, slot);
        }

        return accepted;
    }

    private int outputCapacityFor(ItemStack output, ItemStack result, int slot) {
        int slotLimit = Math.min(result.getMaxStackSize(), items.getSlotLimit(slot));

        if (output.isEmpty()) {
            return slotLimit;
        }

        if (!ItemStack.isSameItemSameComponents(output, result)) {
            return 0;
        }

        return Math.max(0, slotLimit - output.getCount());
    }

    private void insertResult(ItemStack result, int firstSlot, int lastSlot) {
        int remaining = result.getCount();

        for (int slot = firstSlot; slot <= lastSlot && remaining > 0; slot++) {
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

    private static int resultOutputStart(BasicLithographyRecipe recipe) {
        return recipe.outputKind() == BasicLithographyRecipe.OutputKind.TEMPLATE
                ? SLOT_TEMPLATE_OUTPUT_A
                : SLOT_ITEM_OUTPUT_0;
    }

    private static int resultOutputEnd(BasicLithographyRecipe recipe) {
        return recipe.outputKind() == BasicLithographyRecipe.OutputKind.TEMPLATE
                ? SLOT_TEMPLATE_OUTPUT_B
                : SLOT_ITEM_OUTPUT_3;
    }

    private void resetProgress() {
        if (progress != 0 || progressRequired != 0) {
            progress = 0;
            progressRequired = 0;
            setChanged();
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

    private boolean redstoneAllowsWork() {
        if (level == null) {
            return true;
        }

        boolean powered = level.hasNeighborSignal(worldPosition);
        return switch (redstoneControlMode) {
            case IGNORED -> true;
            case LOW -> !powered;
            case HIGH -> powered;
        };
    }

    private boolean canPreviewRecipe() {
        return activeMatch()
                .map(match -> redstoneAllowsWork()
                        && !outputBlocked(match)
                        && committedCoherentPower + 1.0E-9 >= match.recipe().requiredCoherentPower()
                        && consumeRecipeEnergy(true))
                .orElse(false);
    }

    private void setData(int index, int value) {
        switch (index) {
            case DATA_TEMPLATE_RULE -> {
                templateRule = value == TEMPLATE_RULE_MOVE_USED ? TEMPLATE_RULE_MOVE_USED : TEMPLATE_RULE_KEEP;
                setChanged();
            }
            case DATA_REDSTONE_MODE -> {
                redstoneControlMode = RedstoneControlMode.byOrdinal(value);
                setChanged();
            }
            case DATA_FACE_DOWN, DATA_FACE_UP, DATA_FACE_NORTH, DATA_FACE_SOUTH, DATA_FACE_WEST, DATA_FACE_EAST -> {
                Direction direction = directionForData(index);
                faceModes.put(direction, AutomationFaceMode.byOrdinal(value));
                setChanged();
            }
            case DATA_ENERGY -> energy.setEnergyStored(value);
            default -> {
            }
        }
    }

    private int getData(int index) {
        return switch (index) {
            case DATA_TEMPLATE_RULE -> templateRule;
            case DATA_ENERGY -> energy.getEnergyStored();
            case DATA_ENERGY_MAX -> energy.getMaxEnergyStored();
            case DATA_OPTICAL_POWER -> (int) Math.round(committedCoherentPower * 100.0);
            case DATA_OPTICAL_POWER_MAX -> FULL_COHERENT_POWER_X100;
            case DATA_PROGRESS -> progress;
            case DATA_PROGRESS_REQUIRED -> progressRequired;
            case DATA_READY -> canPreviewRecipe() ? 1 : 0;
            case DATA_OUTPUT_BLOCKED -> outputBlocked() ? 1 : 0;
            case DATA_REDSTONE_MODE -> redstoneControlMode.ordinal();
            case DATA_FACE_DOWN, DATA_FACE_UP, DATA_FACE_NORTH, DATA_FACE_SOUTH, DATA_FACE_WEST, DATA_FACE_EAST ->
                    faceMode(directionForData(index)).ordinal();
            default -> 0;
        };
    }

    private static Direction directionForData(int index) {
        return switch (index) {
            case DATA_FACE_DOWN -> Direction.DOWN;
            case DATA_FACE_UP -> Direction.UP;
            case DATA_FACE_NORTH -> Direction.NORTH;
            case DATA_FACE_SOUTH -> Direction.SOUTH;
            case DATA_FACE_WEST -> Direction.WEST;
            case DATA_FACE_EAST -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    private void logSlotChanged(int slot) {
        if (level == null || level.isClientSide) {
            return;
        }

        ItemStack stack = items.getStackInSlot(slot);
        SpectralDiagnostics.event(level, SpectralDiagnostics.Subsystem.LITHOGRAPHY, "slot_changed")
                .pos("machine", worldPosition)
                .field("slot", slot)
                .field("role", slotRole(slot))
                .field("item", stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .field("count", stack.getCount())
                .field("template_rule", templateRuleName())
                .field("ready", canPreviewRecipe())
                .field("output_blocked", outputBlocked())
                .write();
    }

    private void logTemplateRuleChanged() {
        if (level == null || level.isClientSide) {
            return;
        }

        SpectralDiagnostics.transition(level, SpectralDiagnostics.Subsystem.LITHOGRAPHY, "template_rule_changed")
                .pos("machine", worldPosition)
                .field("template_rule", templateRuleName())
                .write();
    }

    private void logRedstoneModeChanged() {
        if (level == null || level.isClientSide) {
            return;
        }

        SpectralDiagnostics.transition(level, SpectralDiagnostics.Subsystem.LITHOGRAPHY, "redstone_mode_changed")
                .pos("machine", worldPosition)
                .field("redstone_mode", redstoneControlMode.id())
                .write();
    }

    private void logFaceModeChanged(Direction side) {
        if (level == null || level.isClientSide) {
            return;
        }

        SpectralDiagnostics.transition(level, SpectralDiagnostics.Subsystem.LITHOGRAPHY, "face_mode_changed")
                .pos("machine", worldPosition)
                .field("side", side)
                .field("face_mode", faceMode(side).id())
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

    private String templateRuleName() {
        return templateRule == TEMPLATE_RULE_MOVE_USED ? "move_used" : "keep";
    }

    private static String slotRole(int slot) {
        return switch (slot) {
            case SLOT_TEMPLATE_INPUT_A, SLOT_TEMPLATE_INPUT_B -> "template_input";
            case SLOT_ITEM_INPUT_0, SLOT_ITEM_INPUT_1, SLOT_ITEM_INPUT_2, SLOT_ITEM_INPUT_3 -> "item_input";
            case SLOT_TEMPLATE_OUTPUT_A, SLOT_TEMPLATE_OUTPUT_B -> "template_output";
            case SLOT_ITEM_OUTPUT_0, SLOT_ITEM_OUTPUT_1, SLOT_ITEM_OUTPUT_2, SLOT_ITEM_OUTPUT_3 -> "item_output";
            default -> "unknown";
        };
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

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        templateRule = tag.getInt(TEMPLATE_RULE_TAG) == TEMPLATE_RULE_MOVE_USED ? TEMPLATE_RULE_MOVE_USED : TEMPLATE_RULE_KEEP;
        redstoneControlMode = RedstoneControlMode.byId(tag.getString(REDSTONE_MODE_TAG));
        energy.setEnergyStored(tag.getInt(ENERGY_TAG));
        progress = Math.max(0, tag.getInt(PROGRESS_TAG));
        committedCoherentPower = Math.max(0.0, tag.getDouble(OPTICAL_POWER_TAG));

        if (tag.contains(FACE_MODES_TAG)) {
            CompoundTag faceModesTag = tag.getCompound(FACE_MODES_TAG);
            for (Direction direction : Direction.values()) {
                String key = direction.getSerializedName();
                if (faceModesTag.contains(key)) {
                    faceModes.put(direction, AutomationFaceMode.byId(faceModesTag.getString(key)));
                }
            }
        }

        if (tag.contains(ITEMS_TAG)) {
            items.deserializeNBT(registries, tag.getCompound(ITEMS_TAG));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ITEMS_TAG, items.serializeNBT(registries));
        tag.putInt(TEMPLATE_RULE_TAG, templateRule);
        tag.putString(REDSTONE_MODE_TAG, redstoneControlMode.id());
        tag.putInt(ENERGY_TAG, energy.getEnergyStored());
        tag.putInt(PROGRESS_TAG, progress);
        tag.putDouble(OPTICAL_POWER_TAG, committedCoherentPower);

        CompoundTag faceModesTag = new CompoundTag();
        for (Map.Entry<Direction, AutomationFaceMode> entry : faceModes.entrySet()) {
            faceModesTag.putString(entry.getKey().getSerializedName(), entry.getValue().id());
        }
        tag.put(FACE_MODES_TAG, faceModesTag);
    }

    public enum AutomationFaceMode {
        DISABLED,
        INPUT,
        OUTPUT,
        INPUT_OUTPUT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean allowsInsert() {
            return this == INPUT || this == INPUT_OUTPUT;
        }

        public boolean allowsExtract() {
            return this == OUTPUT || this == INPUT_OUTPUT;
        }

        public AutomationFaceMode next(boolean reverse) {
            AutomationFaceMode[] values = AutomationFaceMode.values();
            int offset = reverse ? values.length - 1 : 1;
            return values[(ordinal() + offset) % values.length];
        }

        public static AutomationFaceMode byOrdinal(int ordinal) {
            AutomationFaceMode[] values = AutomationFaceMode.values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : INPUT_OUTPUT;
        }

        public static AutomationFaceMode byId(String id) {
            for (AutomationFaceMode mode : values()) {
                if (mode.id().equals(id)) {
                    return mode;
                }
            }

            return INPUT_OUTPUT;
        }
    }

    public enum RedstoneControlMode {
        IGNORED,
        LOW,
        HIGH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public RedstoneControlMode next(boolean reverse) {
            RedstoneControlMode[] values = RedstoneControlMode.values();
            int offset = reverse ? values.length - 1 : 1;
            return values[(ordinal() + offset) % values.length];
        }

        public static RedstoneControlMode byOrdinal(int ordinal) {
            RedstoneControlMode[] values = RedstoneControlMode.values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IGNORED;
        }

        public static RedstoneControlMode byId(String id) {
            for (RedstoneControlMode mode : values()) {
                if (mode.id().equals(id)) {
                    return mode;
                }
            }

            return IGNORED;
        }
    }

    private final class SidedItems implements IItemHandler {
        private final Direction side;

        private SidedItems(Direction side) {
            this.side = side;
        }

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
            if (!faceMode(side).allowsInsert() || !isInputSlot(slot)) {
                return stack;
            }

            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!faceMode(side).allowsExtract() || !isOutputSlot(slot)) {
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
            return faceMode(side).allowsInsert() && isInputSlot(slot) && items.isItemValid(slot, stack);
        }

        private boolean isInputSlot(int slot) {
            return isTemplateInputSlot(slot) || isItemInputSlot(slot);
        }
    }
}
