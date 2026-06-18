package io.github.yoglappland.spectralization.client.hud;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.ClientHudState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class SpectralHudOverlayEvents {
    @SubscribeEvent
    public static void renderHudPanels(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (!ClientHudState.visible()
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.screen != null) {
            return;
        }

        SpectralHudPanelManager.render(event.getGuiGraphics(), -1, -1, false);
    }

    private SpectralHudOverlayEvents() {
    }
}
