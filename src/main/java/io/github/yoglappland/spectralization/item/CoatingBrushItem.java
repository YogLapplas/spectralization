package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.menu.CoatingBrushMenu;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class CoatingBrushItem extends Item {
    public CoatingBrushItem(Properties properties) {
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
            openBrushMenu(level, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        SurfaceKey key = new SurfaceKey(context.getClickedPos(), context.getClickedFace());
        return SurfaceCoatingInteraction.applyBrushPaint(level, player, key, context.getItemInHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (usedHand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        openBrushMenu(level, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void openBrushMenu(Level level, Player player) {
        if (level.isClientSide) {
            return;
        }

        MenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new CoatingBrushMenu(containerId, inventory),
                Component.translatable("container.spectralization.coating_brush")
        );
        player.openMenu(provider);
    }
}
