package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingData;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingRules;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SurfaceCoatingInteraction {
    public static InteractionResult applyPaint(
            Level level,
            Player player,
            SurfaceKey key,
            ItemStack paintStack
    ) {
        if (!(paintStack.getItem() instanceof PaintBucketItem paintBucket)) {
            return InteractionResult.PASS;
        }

        if (!SurfaceCoatingRules.canApplyWorldCoating(level, key)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide && SurfaceCoatingData.set(level, key, paintBucket.treatmentKind())) {
            OpticalTraceCache.markSurfaceChanged(level, key);
            consumePaint(player, paintStack, paintBucket);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static InteractionResult applyCreativePaint(
            Level level,
            Player player,
            SurfaceKey key,
            SurfaceTreatmentKind treatmentKind
    ) {
        if (!SurfaceCoatingRules.canApplyWorldCoating(level, key)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide && SurfaceCoatingData.set(level, key, treatmentKind)) {
            OpticalTraceCache.markSurfaceChanged(level, key);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static InteractionResult applyBrushPaint(
            Level level,
            Player player,
            SurfaceKey key,
            ItemStack brushStack
    ) {
        var maybeTreatment = BrushPaintSelection.selectedTreatment(brushStack);

        if (maybeTreatment.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!SurfaceCoatingRules.canApplyWorldCoating(level, key)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide && SurfaceCoatingData.set(level, key, maybeTreatment.get())) {
            OpticalTraceCache.markSurfaceChanged(level, key);
            BrushPaintSelection.consumeSelectedPaint(brushStack);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void consumePaint(Player player, ItemStack paintStack, PaintBucketItem paintBucket) {
        if (player.getAbilities().instabuild) {
            return;
        }

        int nextDamage = paintStack.getDamageValue() + 1;

        if (nextDamage >= paintStack.getMaxDamage()) {
            player.setItemInHand(InteractionHand.OFF_HAND, paintBucket.emptyBucketStack());
        } else {
            paintStack.setDamageValue(nextDamage);
        }
    }

    private SurfaceCoatingInteraction() {
    }
}
