package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.lens.LensKind;
import io.github.yoglappland.spectralization.optics.lens.LensParameterSpec;
import io.github.yoglappland.spectralization.optics.lens.LensProfile;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import io.github.yoglappland.spectralization.tag.SpectralItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    public static final int DATA_COUNT = 15;

    private static final String ITEMS_TAG = "items";
    private static final String KIND_TAG = "kind";
    private static final String TARGET_TAG = "target";
    private static final String LAST_RESULT_TAG = "last_result";
    private static final String LAST_QUALITY_TAG = "last_quality";

    private int lensKind = LensKind.CONVEX.ordinal();
    private int target = LensKind.CONVEX.parameter().defaultValue();
    private int lastResult = 0;
    private int lastQuality = 0;

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
            if (slot == SLOT_TOOL) {
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
        lensKind = Math.floorMod(lensKind + amount, LensKind.values().length);
        clampSelectionToTool();
        setChanged();
    }

    public void adjustTarget(int amount) {
        int step = targetStep();
        int min = targetMin();
        int max = targetMax();
        int next = target + amount * step;
        target = snap(Mth.clamp(next, min, max), step, min, max);
        setChanged();
    }

    public boolean grind() {
        if (level == null || level.isClientSide || !canGrind()) {
            return false;
        }

        int error = previewError();
        int actual = Mth.clamp(target + level.random.nextInt(error * 2 + 1) - error, targetMin(), targetMax());
        int quality = rollQuality();
        ItemStack output = new LensProfile(LensKind.byIndex(lensKind).id(), actual, 100, quality).createStack();

        items.extractItem(SLOT_BLANK, 1, false);
        damageTool();
        items.setStackInSlot(SLOT_OUTPUT, output);
        lastResult = actual;
        lastQuality = quality;
        setChanged();
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
        return stack.is(Items.GLASS)
                || stack.is(Items.GLASS_PANE)
                || stack.is(Spectralization.SILVER_GLASS_ITEM.get());
    }

    public static int toolTier(ItemStack stack) {
        return stack.is(SpectralItemTags.GRINDING_KNIVES) ? 0 : -1;
    }

    private void setData(int index, int value) {
        switch (index) {
            case DATA_LENS_KIND -> {
                lensKind = Mth.clamp(value, 0, LensKind.values().length - 1);
                clampSelectionToTool();
                setChanged();
            }
            case DATA_TARGET -> {
                target = Mth.clamp(value, targetMin(), targetMax());
                setChanged();
            }
            default -> {
            }
        }
    }

    private int getData(int index) {
        return switch (index) {
            case DATA_LENS_KIND -> Mth.clamp(lensKind, 0, LensKind.values().length - 1);
            case DATA_TARGET -> target;
            case DATA_ALLOWED_KINDS -> craftableKindCount();
            case DATA_TARGET_MIN -> targetMin();
            case DATA_TARGET_MAX -> targetMax();
            case DATA_TARGET_STEP -> targetStep();
            case DATA_ERROR -> previewError();
            case DATA_PREVIEW_MIN -> Math.max(targetMin(), target - previewError());
            case DATA_PREVIEW_MAX -> Math.min(targetMax(), target + previewError());
            case DATA_COARSE_CHANCE -> qualityChance(1);
            case DATA_CLEAR_CHANCE -> qualityChance(2);
            case DATA_PRECISE_CHANCE -> qualityChance(3);
            case DATA_READY -> canGrind() ? 1 : 0;
            case DATA_LAST_RESULT -> lastResult;
            case DATA_LAST_QUALITY -> lastQuality;
            default -> 0;
        };
    }

    private boolean canGrind() {
        return !items.getStackInSlot(SLOT_BLANK).isEmpty()
                && toolTier(items.getStackInSlot(SLOT_TOOL)) >= 0
                && selectedKindCraftable()
                && items.getStackInSlot(SLOT_OUTPUT).isEmpty();
    }

    private void clampSelectionToTool() {
        lensKind = Mth.clamp(lensKind, 0, LensKind.values().length - 1);
        target = Mth.clamp(target, targetMin(), targetMax());
    }

    private boolean selectedKindCraftable() {
        return lensKind < craftableKindCount();
    }

    private int craftableKindCount() {
        return toolTier(items.getStackInSlot(SLOT_TOOL)) >= 0 ? LensKind.values().length : 0;
    }

    private int targetMin() {
        return parameter().minValue();
    }

    private int targetMax() {
        return parameter().maxForToolTier(toolTier(items.getStackInSlot(SLOT_TOOL)));
    }

    private int targetStep() {
        return parameter().stepForToolTier(toolTier(items.getStackInSlot(SLOT_TOOL)));
    }

    private int previewError() {
        int error = parameter().baseErrorForToolTier(toolTier(items.getStackInSlot(SLOT_TOOL)));

        return sameKindReference() ? Math.max(0, error - 1) : error;
    }

    private LensParameterSpec parameter() {
        return LensKind.byIndex(lensKind).parameter();
    }

    private int qualityChance(int quality) {
        int tier = Math.max(0, toolTier(items.getStackInSlot(SLOT_TOOL)));
        int[] chances = switch (tier) {
            case 0 -> new int[]{70, 30, 0};
            case 1 -> new int[]{45, 50, 5};
            case 2 -> new int[]{20, 60, 20};
            default -> new int[]{5, 55, 40};
        };

        if (sameKindReference()) {
            chances[0] = Math.max(0, chances[0] - 10);
            chances[2] = Math.min(100, chances[2] + 10);
            chances[1] = 100 - chances[0] - chances[2];
        }

        return chances[Mth.clamp(quality, 1, 3) - 1];
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
        target = tag.getInt(TARGET_TAG);
        lastResult = tag.getInt(LAST_RESULT_TAG);
        lastQuality = tag.getInt(LAST_QUALITY_TAG);

        if (tag.contains(ITEMS_TAG)) {
            items.deserializeNBT(registries, tag.getCompound(ITEMS_TAG));
        }

        clampSelectionToTool();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ITEMS_TAG, items.serializeNBT(registries));
        tag.putInt(KIND_TAG, lensKind);
        tag.putInt(TARGET_TAG, target);
        tag.putInt(LAST_RESULT_TAG, lastResult);
        tag.putInt(LAST_QUALITY_TAG, lastQuality);
    }
}
