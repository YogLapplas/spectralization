package io.github.yoglappland.spectralization.optics.lens;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.registry.SpectralEnchantments;
import io.github.yoglappland.spectralization.tag.SpectralItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

public record LensGrindingToolProfile(
        int tier,
        int targetStepUnits,
        boolean targetAdjustable,
        int grindPerClick,
        int errorUnits,
        int finishPermille,
        int coarseChance,
        int clearChance,
        int preciseChance
) {
    public static final int GRIND_PROGRESS_MAX = 360;
    public static final LensGrindingToolProfile NONE =
            new LensGrindingToolProfile(-1, 0, false, 0, 0, LensProfile.MIN_FINISH_PERMILLE, 100, 0, 0);
    public static final LensGrindingToolProfile BASIC =
            new LensGrindingToolProfile(0, 640, false, 36, 120, 860, 70, 30, 0);
    public static final LensGrindingToolProfile DIAMOND =
            new LensGrindingToolProfile(1, 80, true, 60, 30, 930, 35, 55, 10);
    public static final LensGrindingToolProfile OBSIDIAN =
            new LensGrindingToolProfile(2, 40, true, 72, 20, 945, 25, 60, 15);
    public static final LensGrindingToolProfile CERAMIC =
            new LensGrindingToolProfile(3, 20, true, 90, 10, 975, 0, 65, 35);
    public static final LensGrindingToolProfile CLEAR =
            new LensGrindingToolProfile(4, 10, true, 120, 5, 990, 0, 40, 60);

    public boolean valid() {
        return tier >= 0;
    }

    public int qualityChance(int quality) {
        return switch (Mth.clamp(quality, LensProfile.MIN_QUALITY, LensProfile.MAX_QUALITY)) {
            case 1 -> coarseChance;
            case 3 -> preciseChance;
            default -> clearChance;
        };
    }

    public static LensGrindingToolProfile fromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return NONE;
        }

        LensGrindingToolProfile base;
        if (stack.is(Spectralization.CLEAR_GRINDING_KNIFE.get())) {
            base = CLEAR;
        } else if (stack.is(Spectralization.CERAMIC_GRINDING_KNIFE.get())) {
            base = CERAMIC;
        } else if (stack.is(Spectralization.OBSIDIAN_GRINDING_KNIFE.get())) {
            base = OBSIDIAN;
        } else if (stack.is(Spectralization.DIAMOND_GRINDING_KNIFE.get())) {
            base = DIAMOND;
        } else {
            base = stack.is(SpectralItemTags.GRINDING_KNIVES) ? BASIC : NONE;
        }

        return SpectralEnchantments.level(stack, SpectralEnchantments.FINESSE) > 0 ? base.withFinesse() : base;
    }

    private LensGrindingToolProfile withFinesse() {
        if (!valid()) {
            return this;
        }

        return new LensGrindingToolProfile(
                tier,
                Math.max(1, targetStepUnits / 2),
                targetAdjustable,
                grindPerClick,
                errorUnits,
                finishPermille,
                coarseChance,
                clearChance,
                preciseChance
        );
    }
}
