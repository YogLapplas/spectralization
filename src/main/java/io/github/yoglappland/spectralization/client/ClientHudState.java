package io.github.yoglappland.spectralization.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.beam.ClientBeamPathCache;
import io.github.yoglappland.spectralization.client.compact.ClientCompactMachineAnimationCache;
import io.github.yoglappland.spectralization.client.compact.ClientCompactMachineOverlayCache;
import io.github.yoglappland.spectralization.client.compact.ClientCompactMachineWorkAreaOverlayCache;
import io.github.yoglappland.spectralization.client.networkoverlay.ClientNetworkOverlayCache;
import io.github.yoglappland.spectralization.client.spot.ClientSpotCache;
import io.github.yoglappland.spectralization.client.surface.ClientSurfaceInspectionCache;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientHudState {
    private static final String CATEGORY_KEY = "key.categories.spectralization";
    private static final String TOGGLE_KEY = "key.spectralization.toggle_hud";
    private static final KeyMapping TOGGLE_HUD = new KeyMapping(
            TOGGLE_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY_KEY
    );

    private static boolean visible = true;

    public static boolean visible() {
        return visible;
    }

    private static void toggle() {
        visible = !visible;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    visible ? "hud.spectralization.enabled" : "hud.spectralization.disabled"
            ), true);
        }
    }

    public static void clearWorldHudCaches() {
        ClientBeamPathCache.clear();
        ClientSpotCache.clear();
        ClientNetworkOverlayCache.clear();
        ClientSurfaceInspectionCache.clear();
        ClientCompactMachineOverlayCache.clear();
        ClientCompactMachineAnimationCache.clear();
        ClientCompactMachineWorkAreaOverlayCache.clear();
    }

    @EventBusSubscriber(modid = Spectralization.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_HUD);
        }

        private ModBusEvents() {
        }
    }

    @EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
    public static final class ClientBusEvents {
        @SubscribeEvent
        public static void clientTick(ClientTickEvent.Post event) {
            while (TOGGLE_HUD.consumeClick()) {
                toggle();
            }
        }

        private ClientBusEvents() {
        }
    }

    private ClientHudState() {
    }
}
