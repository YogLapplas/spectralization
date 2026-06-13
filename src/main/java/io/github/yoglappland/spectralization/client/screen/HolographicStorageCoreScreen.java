package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.client.gui.SpectralBarKind;
import io.github.yoglappland.spectralization.client.gui.SpectralGuiTheme;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.menu.HolographicStorageCoreMenu;
import io.github.yoglappland.spectralization.storage.HolographicStorageBlockEntry;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class HolographicStorageCoreScreen extends SpectralMachineScreen<HolographicStorageCoreMenu> {
    private static final int LOCKED_COLOR = 0xFF7A1E2B;

    public HolographicStorageCoreScreen(HolographicStorageCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "holographic_storage_core", 256, 170, 0, 160);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawMachineBackground(graphics);
        panel(graphics, "machine_panel", 18, 22, 220, 132);
        horizontalBar(graphics, "type_bar", 36, 50, 44, 6, menu.storedTypes(), menu.maxTypes(), SpectralBarKind.OPTICAL);
        horizontalBar(graphics, "item_bar", 36, 74, 44, 6, menu.storedItems(), menu.maxItems(), SpectralBarKind.ENERGY);
        horizontalBar(graphics, "status_bar", 36, 98, 44, 6, menu.interactionLocked() ? 1 : 0, 1, SpectralBarKind.SIGNAL);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);
        int statusColor = menu.interactionLocked() ? LOCKED_COLOR : SpectralGuiTheme.STABLE;
        graphics.drawString(font, statusText(), 96, 34, statusColor, false);
        text(graphics, "types_text", label("screen.spectralization.holographic_storage.types")
                + ": " + menu.storedTypes() + "/" + menu.maxTypes(), 96, 50, 128);
        text(graphics, "items_text", label("screen.spectralization.holographic_storage.items")
                + ": " + formatCount(menu.storedItems()) + "/" + formatCount(menu.maxItems()), 96, 64, 128);
        text(graphics, "crystals_text", label("screen.spectralization.holographic_storage.crystals")
                + ": " + menu.crystals(), 96, 78, 128);
        text(graphics, "faces_text", label("screen.spectralization.holographic_storage.exposed_faces")
                + ": " + menu.exposedFaces(), 96, 92, 128);
        text(graphics, "channels_text", label("screen.spectralization.holographic_storage.channels")
                + ": x" + menu.channelMultiplier(), 96, 106, 128);
        renderBlockEntries(graphics);
    }

    private void renderBlockEntries(GuiGraphics graphics) {
        List<HolographicStorageBlockEntry> entries = menu.blockEntries();
        mutedText(graphics, "blocks_title", label("screen.spectralization.holographic_storage.blocks"), 24, 116, 70);

        int visibleEntries = Math.min(4, entries.size());
        for (int index = 0; index < visibleEntries; index++) {
            HolographicStorageBlockEntry entry = entries.get(index);
            String line = Component.translatable(entry.descriptionId()).getString() + ": " + entry.count();
            mutedText(graphics, "block_entry_" + index, line, 96, 120 + index * 10, 132);
        }
    }

    private Component statusText() {
        if (menu.structureError()) {
            return Component.translatable("screen.spectralization.holographic_storage.status_error");
        }

        if (menu.overCapacity()) {
            return Component.translatable("screen.spectralization.holographic_storage.status_over_capacity");
        }

        return Component.translatable("screen.spectralization.holographic_storage.status_normal");
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    private static String formatCount(long count) {
        if (count < 1000) {
            return Long.toString(count);
        }

        if (count < 1_000_000) {
            return count / 1000 + "k";
        }

        return count / 1_000_000 + "m";
    }
}
