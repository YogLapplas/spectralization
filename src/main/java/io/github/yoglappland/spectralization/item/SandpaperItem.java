package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingData;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingRules;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class SandpaperItem extends Item {
    public SandpaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        SurfaceKey key = new SurfaceKey(context.getClickedPos(), context.getClickedFace());

        if (!SurfaceCoatingRules.canApplyWorldCoating(level, key)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide && SurfaceCoatingData.remove(level, key)) {
            OpticalTraceCache.markSurfaceChanged(level, key);
            damageSandpaper(context);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private static void damageSandpaper(UseOnContext context) {
        Player player = context.getPlayer();

        if (player == null || player.getAbilities().instabuild) {
            return;
        }

        ItemStack stack = context.getItemInHand();
        EquipmentSlot slot = context.getHand() == InteractionHand.OFF_HAND
                ? EquipmentSlot.OFFHAND
                : EquipmentSlot.MAINHAND;
        stack.hurtAndBreak(1, player, slot);
    }
}
