package io.github.yoglappland.spectralization.client.item;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.item.SpotTestItem;
import io.github.yoglappland.spectralization.network.SpotTestLoadTogglePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class SpotTestItemClientEvents {
    @SubscribeEvent
    public static void leftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getItemStack().getItem() instanceof SpotTestItem) {
            PacketDistributor.sendToServer(new SpotTestLoadTogglePayload());
        }
    }

    private SpotTestItemClientEvents() {
    }
}
