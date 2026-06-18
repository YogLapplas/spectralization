package io.github.yoglappland.spectralization.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.yoglappland.spectralization.client.ClientHudState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SpectralHudEditScreen extends Screen {
    public SpectralHudEditScreen() {
        super(Component.empty());
    }

    @Override
    public void tick() {
        syncMovementKeys();

        if (!ClientHudState.visible() || !ClientHudState.editModeDown()) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        SpectralHudPanelManager.render(graphics, mouseX, mouseY, true);
        SpectralHudEntityOverlayEvents.renderTrackingButton(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return SpectralHudEntityOverlayEvents.mouseClickedTrackingButton(mouseX, mouseY, button)
                || SpectralHudPanelManager.mouseClicked(mouseX, mouseY, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return SpectralHudPanelManager.mouseDragged(mouseX, mouseY, button)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return SpectralHudPanelManager.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isMovementKey(keyCode, scanCode)) {
            return false;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (isMovementKey(keyCode, scanCode)) {
            return false;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private static boolean isMovementKey(int keyCode, int scanCode) {
        Minecraft minecraft = Minecraft.getInstance();
        return matches(minecraft.options.keyUp, keyCode, scanCode)
                || matches(minecraft.options.keyDown, keyCode, scanCode)
                || matches(minecraft.options.keyLeft, keyCode, scanCode)
                || matches(minecraft.options.keyRight, keyCode, scanCode)
                || matches(minecraft.options.keyJump, keyCode, scanCode)
                || matches(minecraft.options.keyShift, keyCode, scanCode)
                || matches(minecraft.options.keySprint, keyCode, scanCode);
    }

    private static boolean matches(KeyMapping key, int keyCode, int scanCode) {
        return key.matches(keyCode, scanCode);
    }

    private static void syncMovementKeys() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.getWindow() == null) {
            return;
        }

        long window = minecraft.getWindow().getWindow();
        syncKey(minecraft.options.keyUp, window);
        syncKey(minecraft.options.keyDown, window);
        syncKey(minecraft.options.keyLeft, window);
        syncKey(minecraft.options.keyRight, window);
        syncKey(minecraft.options.keyJump, window);
        syncKey(minecraft.options.keyShift, window);
        syncKey(minecraft.options.keySprint, window);
    }

    private static void syncKey(KeyMapping key, long window) {
        key.setDown(InputConstants.isKeyDown(window, key.getKey().getValue()));
    }
}
