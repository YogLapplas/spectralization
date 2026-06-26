package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.lens.LensGrindingToolProfile;
import io.github.yoglappland.spectralization.optics.lens.LensKind;
import io.github.yoglappland.spectralization.optics.lens.LensMaterial;
import io.github.yoglappland.spectralization.optics.lens.LensParameterSpec;
import io.github.yoglappland.spectralization.optics.lens.LensProfile;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class LensGrindingBenchBlockEntity extends BlockEntity {
    public static final int SLOT_BLANK = 0;
    public static final int SLOT_TOOL = 1;
    public static final int SLOT_REFERENCE = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int DATA_LENS_KIND = 0;
    public static final int DATA_TARGET = 1;
    public static final int DATA_ALLOWED_KINDS = 2;
    public static final int DATA_TARGET_MIN = 3;
    public static final int DATA_TARGET_MAX = 4;
    public static final int DATA_TARGET_STEP = 5;
    public static final int DATA_ERROR = 6;
    public static final int DATA_PREVIEW_MIN = 7;
    public static final int DATA_PREVIEW_MAX = 8;
    public static final int DATA_COARSE_CHANCE = 9;
    public static final int DATA_CLEAR_CHANCE = 10;
    public static final int DATA_PRECISE_CHANCE = 11;
    public static final int DATA_READY = 12;
    public static final int DATA_LAST_RESULT = 13;
    public static final int DATA_LAST_QUALITY = 14;
    public static final int DATA_GRIND_PROGRESS = 15;
    public static final int DATA_GRIND_PROGRESS_MAX = 16;
    public static final int DATA_TARGET_LOCKED = 17;
    public static final int DATA_COUNT = 18;

    private static final String ITEMS_TAG = "items";
    private static final String KIND_TAG = "kind";
    private static final String TARGET_TAG = "target";
    private static final String TARGET_TENTHS_TAG = "target_tenths";
    private static final String TARGET_UNITS_TAG = "target_units";
    private static final String LAST_RESULT_TAG = "last_result";
    private static final String LAST_RESULT_TENTHS_TAG = "last_result_tenths";
    private static final String LAST_RESULT_UNITS_TAG = "last_result_units";
    private static final String LAST_QUALITY_TAG = "last_quality";
    private static final String GRIND_PROGRESS_TAG = "grind_progress";

    private int lensKind = LensKind.CONVEX.ordinal();
    private int targetUnits = LensKind.CONVEX.parameter().defaultValue() * LensProfile.FOCAL_LENGTH_SCALE;
    private int lastResultUnits = 0;
    private int lastQuality = 0;
    private int grindProgress = 0;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_BLANK -> isBlank(stack);
                case SLOT_TOOL -> toolTier(stack) >= 0;
                case SLOT_REFERENCE -> stack.is(Spectralization.LENS.get());
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (slot == SLOT_BLANK || slot == SLOT_TOOL || slot == SLOT_REFERENCE) {
                resetGrindProgress();
                clampSelectionToTool();
            }

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
            setData(index, value);
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public LensGrindingBenchBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.LENS_GRINDING_BENCH.get(), pos, blockState);
    }

    public ItemStackHandler items() {
        return items;
    }

    public ContainerData createDataAccess() {
        return data;
    }

    public void adjustLensKind(int amount) {
        int previousKind = lensKind;
        int previousTarget = targetUnits;
        lensKind = Math.floorMod(lensKind + amount, LensKind.values().length);
        clampSelectionToTool();

        if (lensKind != previousKind || targetUnits != previousTarget) {
            resetGrindProgress();
        }

        setChanged();
    }

    public void adjustTarget(int amount) {
        if (targetLocked()) {
            clampSelectionToTool();
            setChanged();
            return;
        }

        int step = targetStep();
        int min = targetMin();
        int max = targetMax();
        int previous = targetUnits;
        int next = targetUnits + amount * step;
        targetUnits = snap(Mth.clamp(next, min, max), step, min, max);

        if (targetUnits != previous) {
            resetGrindProgress();
        }

        setChanged();
    }

    public boolean grind() {
        if (level == null || level.isClientSide || !canGrind()) {
            return false;
        }

        LensGrindingToolProfile tool = toolProfile();
        grindProgress = Math.min(LensGrindingToolProfile.GRIND_PROGRESS_MAX, grindProgress + tool.grindPerClick());

        if (grindProgress < LensGrindingToolProfile.GRIND_PROGRESS_MAX) {
            setChanged();
            return true;
        }

        finishGrinding(tool);
        return true;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.getStackInSlot(slot);

            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    public static boolean isBlank(ItemStack stack) {
        return LensMaterial.fromBlank(stack).isPresent();
    }

    public static int toolTier(ItemStack stack) {
        return LensGrindingToolProfile.fromStack(stack).tier();
    }

    private void finishGrinding(LensGrindingToolProfile tool) {
        int error = previewError();
        int actual = Mth.clamp(targetUnits + level.random.nextInt(error * 2 + 1) - error, targetMin(), targetMax());
        int quality = rollQuality();
        LensMaterial material = blankMaterial();
        ItemStack output = LensProfile.withUnits(
                LensKind.byIndex(lensKind).id(),
                actual,
                100,
                quality,
                material.id(),
                tool.finishPermille()
        ).createStack();

        items.extractItem(SLOT_BLANK, 1, false);
        damageTool();
        items.setStackInSlot(SLOT_OUTPUT, output);
        lastResultUnits = actual;
        lastQuality = quality;
        resetGrindProgress();
        setChanged();
    }

    private void setData(int index, int value) {
        switch (index) {
            case DATA_LENS_KIND -> {
                lensKind = Mth.clamp(value, 0, LensKind.values().length - 1);
                clampSelectionToTool();
                resetGrindProgress();
                setChanged();
            }
            case DATA_TARGET -> {
                int previous = targetUnits;
                if (targetLocked()) {
                    targetUnits = lockedTargetUnits(targetMin(), targetMax());
                } else {
                    targetUnits = snap(Mth.clamp(value, targetMin(), targetMax()), targetStep(), targetMin(), targetMax());
                }
                if (targetUnits != previous) {
                    resetGrindProgress();
                }
                setChanged();
            }
            default -> {
            }
        }
    }

    private int getData(int index) {
        return switch (index) {
            case DATA_LENS_KIND -> Mth.clamp(lensKind, 0, LensKind.values().length - 1);
            case DATA_TARGET -> targetUnits;
            case DATA_ALLOWED_KINDS -> craftableKindCount();
            case DATA_TARGET_MIN -> targetMin();
            case DATA_TARGET_MAX -> targetMax();
            case DATA_TARGET_STEP -> targetStep();
            case DATA_ERROR -> previewError();
            case DATA_PREVIEW_MIN -> Math.max(targetMin(), targetUnits - previewError());
            case DATA_PREVIEW_MAX -> Math.min(targetMax(), targetUnits + previewError());
            case DATA_COARSE_CHANCE -> qualityChance(1);
            case DATA_CLEAR_CHANCE -> qualityChance(2);
            case DATA_PRECISE_CHANCE -> qualityChance(3);
            case DATA_READY -> canGrind() ? 1 : 0;
            case DATA_LAST_RESULT -> lastResultUnits;
            case DATA_LAST_QUALITY -> lastQuality;
            case DATA_GRIND_PROGRESS -> grindProgress;
            case DATA_GRIND_PROGRESS_MAX -> LensGrindingToolProfile.GRIND_PROGRESS_MAX;
            case DATA_TARGET_LOCKED -> targetLocked() ? 1 : 0;
            default -> 0;
        };
    }

    private boolean canGrind() {
        return isBlank(items.getStackInSlot(SLOT_BLANK))
                && toolProfile().valid()
                && selectedKindCraftable()
                && items.getStackInSlot(SLOT_OUTPUT).isEmpty();
    }

    private void clampSelectionToTool() {
        lensKind = Mth.clamp(lensKind, 0, LensKind.values().length - 1);
        int min = targetMin();
        int max = targetMax();

        if (targetLocked()) {
            targetUnits = lockedTargetUnits(min, max);
            return;
        }

        targetUnits = snap(Mth.clamp(targetUnits, min, max), targetStep(), min, max);
    }

    private boolean selectedKindCraftable() {
        return lensKind < craftableKindCount();
    }

    private int craftableKindCount() {
        return toolProfile().valid() ? LensKind.values().length : 0;
    }

    private int targetMin() {
        return Math.max(LensProfile.MIN_FOCAL_LENGTH_UNITS, blankMaterial().minFocalLengthUnits());
    }

    private int targetMax() {
        int min = targetMin();
        int parameterMax = parameter().maxValue() * LensProfile.FOCAL_LENGTH_SCALE;
        return Math.max(
                min,
                Math.min(
                        LensProfile.MAX_FOCAL_LENGTH_UNITS,
                        Math.min(parameterMax, blankMaterial().maxFocalLengthUnits())
                )
        );
    }

    private int targetStep() {
        return Math.max(1, toolProfile().targetStepUnits());
    }

    private int previewError() {
        int error = toolProfile().errorUnits();

        return sameKindReference() ? Math.max(0, error - LensProfile.FOCAL_LENGTH_SCALE) : error;
    }

    private boolean targetLocked() {
        return !toolProfile().targetAdjustable();
    }

    private int lockedTargetUnits(int min, int max) {
        return Mth.clamp(parameter().defaultValue() * LensProfile.FOCAL_LENGTH_SCALE, min, max);
    }

    private LensParameterSpec parameter() {
        return LensKind.byIndex(lensKind).parameter();
    }

    private LensMaterial blankMaterial() {
        return LensMaterial.fromBlank(items.getStackInSlot(SLOT_BLANK)).orElse(LensMaterial.ORDINARY);
    }

    private LensGrindingToolProfile toolProfile() {
        return LensGrindingToolProfile.fromStack(items.getStackInSlot(SLOT_TOOL));
    }

    private int qualityChance(int quality) {
        LensGrindingToolProfile tool = toolProfile();
        int coarse = tool.qualityChance(1);
        int clear = tool.qualityChance(2);
        int precise = tool.qualityChance(3);

        if (sameKindReference()) {
            coarse = Math.max(0, coarse - 10);
            precise = Math.min(100, precise + 10);
            clear = Math.max(0, 100 - coarse - precise);
        }

        return switch (Mth.clamp(quality, LensProfile.MIN_QUALITY, LensProfile.MAX_QUALITY)) {
            case 1 -> coarse;
            case 3 -> precise;
            default -> clear;
        };
    }

    private boolean sameKindReference() {
        ItemStack reference = items.getStackInSlot(SLOT_REFERENCE);

        return reference.is(Spectralization.LENS.get())
                && LensKind.byId(LensProfile.fromStack(reference).tag()) == LensKind.byIndex(lensKind);
    }

    private int rollQuality() {
        int roll = level == null ? 0 : level.random.nextInt(100);
        int coarse = qualityChance(1);
        int clear = qualityChance(2);

        if (roll < coarse) {
            return 1;
        }

        return roll < coarse + clear ? 2 : 3;
    }

    private void resetGrindProgress() {
        grindProgress = 0;
    }

    private void damageTool() {
        ItemStack tool = items.getStackInSlot(SLOT_TOOL);

        if (!tool.isDamageableItem()) {
            return;
        }

        tool.setDamageValue(tool.getDamageValue() + 1);

        if (tool.getDamageValue() >= tool.getMaxDamage()) {
            items.setStackInSlot(SLOT_TOOL, ItemStack.EMPTY);
        } else {
            items.setStackInSlot(SLOT_TOOL, tool);
        }
    }

    private static int snap(int value, int step, int min, int max) {
        if (step <= 1) {
            return Mth.clamp(value, min, max);
        }

        int snapped = min + Math.round((value - min) / (float) step) * step;
        return Mth.clamp(snapped, min, max);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lensKind = tag.getInt(KIND_TAG);
        targetUnits = tag.contains(TARGET_UNITS_TAG)
                ? tag.getInt(TARGET_UNITS_TAG)
                : (tag.contains(TARGET_TENTHS_TAG)
                ? tag.getInt(TARGET_TENTHS_TAG) * 2
                : tag.getInt(TARGET_TAG) * LensProfile.FOCAL_LENGTH_SCALE);
        lastResultUnits = tag.contains(LAST_RESULT_UNITS_TAG)
                ? tag.getInt(LAST_RESULT_UNITS_TAG)
                : (tag.contains(LAST_RESULT_TENTHS_TAG)
                ? tag.getInt(LAST_RESULT_TENTHS_TAG) * 2
                : tag.getInt(LAST_RESULT_TAG) * LensProfile.FOCAL_LENGTH_SCALE);
        targetUnits = LensProfile.clampFocalLengthUnits(targetUnits);
        if (lastResultUnits > 0) {
            lastResultUnits = LensProfile.clampFocalLengthUnits(lastResultUnits);
        }
        lastQuality = tag.getInt(LAST_QUALITY_TAG);

        if (tag.contains(ITEMS_TAG)) {
            items.deserializeNBT(registries, tag.getCompound(ITEMS_TAG));
        }

        grindProgress = Mth.clamp(tag.getInt(GRIND_PROGRESS_TAG), 0, LensGrindingToolProfile.GRIND_PROGRESS_MAX);
        clampSelectionToTool();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ITEMS_TAG, items.serializeNBT(registries));
        tag.putInt(KIND_TAG, lensKind);
        tag.putInt(TARGET_TAG, Math.round(targetUnits / (float) LensProfile.FOCAL_LENGTH_SCALE));
        tag.putInt(TARGET_TENTHS_TAG, Math.round(targetUnits * 10.0F / LensProfile.FOCAL_LENGTH_SCALE));
        tag.putInt(TARGET_UNITS_TAG, targetUnits);
        tag.putInt(LAST_RESULT_TAG, Math.round(lastResultUnits / (float) LensProfile.FOCAL_LENGTH_SCALE));
        tag.putInt(LAST_RESULT_TENTHS_TAG, Math.round(lastResultUnits * 10.0F / LensProfile.FOCAL_LENGTH_SCALE));
        tag.putInt(LAST_RESULT_UNITS_TAG, lastResultUnits);
        tag.putInt(LAST_QUALITY_TAG, lastQuality);
        tag.putInt(GRIND_PROGRESS_TAG, grindProgress);
    }
}
