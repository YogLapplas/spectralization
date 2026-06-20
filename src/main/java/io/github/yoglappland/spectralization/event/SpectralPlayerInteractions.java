package io.github.yoglappland.spectralization.event;

import io.github.yoglappland.spectralization.item.PaintBucketItem;
import io.github.yoglappland.spectralization.item.SurfaceCoatingInteraction;
import io.github.yoglappland.spectralization.optics.fiber.FiberShearsInteraction;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SpectralPlayerInteractions {
    private SpectralPlayerInteractions() {
    }

    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND
                && event.getItemStack().is(Items.SHEARS)
                && FiberShearsInteraction.isFiberNodeTarget(event.getLevel(), event.getPos())) {
            InteractionResult result = event.getLevel() instanceof ServerLevel level
                    ? FiberShearsInteraction.useOn(level, event.getEntity(), event.getItemStack(), event.getPos().immutable())
                    : InteractionResult.SUCCESS;
            event.setCancellationResult(result);
            event.setCanceled(true);
            return;
        }

        if (event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().is(Items.BRUSH)
                || !(event.getEntity().getOffhandItem().getItem() instanceof PaintBucketItem)) {
            return;
        }

        SurfaceKey key = new SurfaceKey(event.getPos(), event.getFace());
        InteractionResult result = SurfaceCoatingInteraction.applyPaint(
                event.getLevel(),
                event.getEntity(),
                key,
                event.getEntity().getOffhandItem()
        );
        event.setCancellationResult(result.consumesAction() ? result : InteractionResult.FAIL);
        event.setCanceled(true);
    }
}
