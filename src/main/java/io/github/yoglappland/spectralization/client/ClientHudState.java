package io.github.yoglappland.spectralization.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.beam.ClientBeamPathCache;
import io.github.yoglappland.spectralization.client.microlizer.ClientMicrolizerAnimationCache;
import io.github.yoglappland.spectralization.client.microlizer.ClientMicrolizerOverlayCache;
import io.github.yoglappland.spectralization.client.microlizer.ClientMicrolizerWorkAreaOverlayCache;
import io.github.yoglappland.spectralization.client.hud.SpectralHudEditScreen;
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
    private static final String EDIT_KEY = "key.spectralization.edit_hud";
    private static final KeyMapping TOGGLE_HUD = new KeyMapping(
            TOGGLE_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY_KEY
    );
    private static final KeyMapping EDIT_HUD = new KeyMapping(
            EDIT_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY_KEY
    );

    private static boolean visible = true;

    public static boolean visible() {
        return visible;
    }

    public static boolean editModeDown() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return EDIT_HUD.isDown();
        }

        return EDIT_HUD.isDown()
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_ALT);
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
        ClientMicrolizerOverlayCache.clear();
        ClientMicrolizerAnimationCache.clear();
        ClientMicrolizerWorkAreaOverlayCache.clear();
    }

    @EventBusSubscriber(modid = Spectralization.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBusEvents {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_HUD);
            event.register(EDIT_HUD);
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

            Minecraft minecraft = Minecraft.getInstance();
            if (visible
                    && minecraft.level != null
                    && minecraft.player != null
                    && minecraft.screen == null
                    && editModeDown()) {
                minecraft.setScreen(new SpectralHudEditScreen());
            }
        }

        private ClientBusEvents() {
        }
    }

    private ClientHudState() {
    }
}
