package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class CreativeBrushItem extends Item {
    public CreativeBrushItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();

        if (player == null || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            CoatingBrushItem.openBrushMenu(level, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        SurfaceKey key = new SurfaceKey(context.getClickedPos(), context.getClickedFace());
        ItemStack brush = context.getItemInHand();
        return BrushPaintSelection.selectedTreatment(brush)
                .map(treatment -> SurfaceCoatingInteraction.applyCreativePaint(level, player, key, treatment))
                .orElse(InteractionResult.PASS);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (usedHand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        CoatingBrushItem.openBrushMenu(level, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
