package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.LensGrindingBenchBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralGui;
import io.github.yoglappland.spectralization.client.gui.SpectralGuiTheme;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.SpectralSlotKind;
import io.github.yoglappland.spectralization.menu.LensGrindingBenchMenu;
import io.github.yoglappland.spectralization.optics.lens.LensKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class LensGrindingBenchScreen extends SpectralMachineScreen<LensGrindingBenchMenu> {
    private static final int PREVIEW_LEFT = 128;
    private static final int PREVIEW_TOP = 82;
    private static final int PREVIEW_WIDTH = 92;
    private static final int PREVIEW_HEIGHT = 42;
    private static final int COLOR_COARSE = 0xFFFF9F43;
    private static final int COLOR_CLEAR = 0xFF7CEAD9;
    private static final int COLOR_PRECISE = 0xFF9EFFF1;

    private Button grindButton;

    public LensGrindingBenchScreen(LensGrindingBenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "lens_grinding_bench", 256, 232, LensGrindingBenchMenu.INVENTORY_X, 126);
    }

    @Override
    protected void init() {
        super.init();
        addSmallButton(132, 36, LensGrindingBenchMenu.BUTTON_KIND_DOWN, Component.literal("-"));
        addSmallButton(214, 36, LensGrindingBenchMenu.BUTTON_KIND_UP, Component.literal("+"));
        addSmallButton(132, 58, LensGrindingBenchMenu.BUTTON_TARGET_DOWN, Component.literal("-"));
        addSmallButton(214, 58, LensGrindingBenchMenu.BUTTON_TARGET_UP, Component.literal("+"));
        grindButton = Button.builder(Component.translatable("screen.spectralization.lens_grinding_bench.grind"), button -> click(LensGrindingBenchMenu.BUTTON_GRIND))
                .bounds(leftPos + 74, topPos + 92, 44, 18)
                .build();
        grindButton.active = menu.ready();
        addRenderableWidget(grindButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (grindButton != null) {
            grindButton.active = menu.ready();
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawMachineBackground(graphics);
        panel(graphics, "machine_panel", 18, 22, 220, 104);
        panel(graphics, "inventory_panel", 42, 132, 172, 90);
        slot(graphics, "slot_blank", LensGrindingBenchMenu.BLANK_SLOT_X, LensGrindingBenchMenu.BLANK_SLOT_Y, SpectralSlotKind.INPUT);
        slot(graphics, "slot_tool", LensGrindingBenchMenu.TOOL_SLOT_X, LensGrindingBenchMenu.TOOL_SLOT_Y, SpectralSlotKind.FUEL);
        slot(graphics, "slot_reference", LensGrindingBenchMenu.REFERENCE_SLOT_X, LensGrindingBenchMenu.REFERENCE_SLOT_Y, SpectralSlotKind.OPTICAL);
        slot(graphics, "slot_output", LensGrindingBenchMenu.OUTPUT_SLOT_X, LensGrindingBenchMenu.OUTPUT_SLOT_Y, SpectralSlotKind.OUTPUT);
        playerInventorySlots(graphics, LensGrindingBenchMenu.INVENTORY_X, LensGrindingBenchMenu.INVENTORY_Y);
        renderGhostItems(graphics);
        renderWorkbenchMarks(graphics);
        renderLensGlyph(graphics, leftPos + 123, topPos + 43, lensKind());
        renderPreviewPanel(graphics);
        renderQualityBars(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);
        clippedText(graphics, lensKindName(), 150, 39, 58, SpectralGuiTheme.TEXT_PRIMARY);
        clippedText(graphics, parameterSymbol() + "=" + data(LensGrindingBenchBlockEntity.DATA_TARGET), 150, 61, 58, SpectralGuiTheme.TEXT_PRIMARY);
        clippedText(graphics, "+/-" + data(LensGrindingBenchBlockEntity.DATA_ERROR), 132, 76, 30, SpectralGuiTheme.TEXT_MUTED);
        clippedText(graphics, data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MIN)
                + "-" + data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MAX), 174, 76, 42, SpectralGuiTheme.TEXT_MUTED);

        int last = data(LensGrindingBenchBlockEntity.DATA_LAST_RESULT);
        if (last > 0) {
            clippedText(graphics, last + " / " + qualityName(data(LensGrindingBenchBlockEntity.DATA_LAST_QUALITY)),
                    24, 116, 94, SpectralGuiTheme.TEXT_MUTED);
        }
    }

    private void renderGhostItems(GuiGraphics graphics) {
        renderGhostItem(graphics, LensGrindingBenchBlockEntity.SLOT_BLANK, new ItemStack(Items.GLASS), LensGrindingBenchMenu.BLANK_SLOT_X, LensGrindingBenchMenu.BLANK_SLOT_Y);
        renderGhostItem(graphics, LensGrindingBenchBlockEntity.SLOT_TOOL, new ItemStack(Items.DIAMOND_PICKAXE), LensGrindingBenchMenu.TOOL_SLOT_X, LensGrindingBenchMenu.TOOL_SLOT_Y);
        renderGhostItem(graphics, LensGrindingBenchBlockEntity.SLOT_REFERENCE, new ItemStack(Spectralization.LENS.get()), LensGrindingBenchMenu.REFERENCE_SLOT_X, LensGrindingBenchMenu.REFERENCE_SLOT_Y);
    }

    private void renderGhostItem(GuiGraphics graphics, int slot, ItemStack stack, int x, int y) {
        if (menu.getSlot(slot).hasItem()) {
            return;
        }

        int left = leftPos + x;
        int top = topPos + y;
        graphics.renderItem(stack, left, top);
        graphics.fill(left, top, left + 16, top + 16, 0xAA080A09);
    }

    private void renderWorkbenchMarks(GuiGraphics graphics) {
        int y = topPos + LensGrindingBenchMenu.TOOL_SLOT_Y + 8;
        int left = leftPos + LensGrindingBenchMenu.TOOL_SLOT_X + 20;
        int right = leftPos + LensGrindingBenchMenu.OUTPUT_SLOT_X - 4;
        SpectralGui.drawRightArrow(graphics, left, y, right - left + 6, SpectralGuiTheme.OPTICAL);
    }

    private void renderLensGlyph(GuiGraphics graphics, int centerX, int centerY, LensKind kind) {
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
                Spectralization.MODID,
                "textures/gui/lens/" + kind.id() + ".png"
        );
        if (minecraft != null && minecraft.getResourceManager().getResource(icon).isPresent()) {
            graphics.blit(icon, centerX - 8, centerY - 8, 0, 0, 16, 16, 16, 16);
            return;
        }

        int color = switch (kind) {
            case ENDER -> 0xFF8CE7FF;
            case MAGMA -> 0xFFFF8A3D;
            case ECHO -> 0xFFAED9FF;
            default -> SpectralGuiTheme.OPTICAL;
        };

        graphics.fill(centerX - 1, centerY - 8, centerX + 1, centerY + 9, 0xAAE8FFF8);

        switch (kind) {
            case CONCAVE -> {
                graphics.fill(centerX - 5, centerY - 7, centerX - 3, centerY + 8, color);
                graphics.fill(centerX + 3, centerY - 7, centerX + 5, centerY + 8, color);
                graphics.fill(centerX - 3, centerY - 8, centerX + 3, centerY - 6, color);
                graphics.fill(centerX - 3, centerY + 6, centerX + 3, centerY + 8, color);
            }
            case ENDER -> {
                graphics.fill(centerX - 6, centerY - 6, centerX + 7, centerY - 4, color);
                graphics.fill(centerX - 6, centerY + 4, centerX + 7, centerY + 6, color);
                graphics.fill(centerX - 6, centerY - 4, centerX - 4, centerY + 4, color);
                graphics.fill(centerX + 5, centerY - 4, centerX + 7, centerY + 4, color);
                graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xCC2B162F);
            }
            case MAGMA -> {
                graphics.fill(centerX - 1, centerY - 8, centerX + 2, centerY - 5, color);
                graphics.fill(centerX - 3, centerY - 5, centerX + 4, centerY - 2, color);
                graphics.fill(centerX - 5, centerY - 2, centerX + 6, centerY + 3, color);
                graphics.fill(centerX - 3, centerY + 3, centerX + 4, centerY + 6, color);
                graphics.fill(centerX - 1, centerY + 6, centerX + 2, centerY + 9, color);
                graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFFFFE28A);
            }
            case ECHO -> {
                graphics.fill(centerX - 6, centerY - 7, centerX + 7, centerY - 5, color);
                graphics.fill(centerX - 6, centerY + 5, centerX + 7, centerY + 7, color);
                graphics.fill(centerX - 8, centerY - 3, centerX - 6, centerY + 3, color);
                graphics.fill(centerX + 6, centerY - 3, centerX + 8, centerY + 3, color);
                graphics.fill(centerX - 3, centerY - 4, centerX + 4, centerY + 5, 0x557CEAD9);
            }
            default -> {
                graphics.fill(centerX - 1, centerY - 8, centerX + 2, centerY + 9, color);
                graphics.fill(centerX - 3, centerY - 6, centerX + 4, centerY + 7, 0x887CEAD9);
                graphics.fill(centerX - 5, centerY - 3, centerX + 6, centerY + 4, 0x447CEAD9);
            }
        }
    }

    private void renderPreviewPanel(GuiGraphics graphics) {
        SpectralGui.drawInset(graphics, leftPos + PREVIEW_LEFT, topPos + PREVIEW_TOP, PREVIEW_WIDTH, PREVIEW_HEIGHT);
        int min = data(LensGrindingBenchBlockEntity.DATA_TARGET_MIN);
        int max = Math.max(min + 1, data(LensGrindingBenchBlockEntity.DATA_TARGET_MAX));
        int target = data(LensGrindingBenchBlockEntity.DATA_TARGET);
        int previewMin = data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MIN);
        int previewMax = data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MAX);
        int barLeft = leftPos + PREVIEW_LEFT + 8;
        int barRight = leftPos + PREVIEW_LEFT + PREVIEW_WIDTH - 8;
        int barY = topPos + PREVIEW_TOP + 30;

        graphics.fill(barLeft, barY, barRight, barY + 3, 0xFF26372F);
        int rollLeft = interpolate(barLeft, barRight, previewMin, min, max);
        int rollRight = interpolate(barLeft, barRight, previewMax, min, max);
        graphics.fill(rollLeft, barY - 1, rollRight + 1, barY + 4, 0xAA7CEAD9);
        int targetX = interpolate(barLeft, barRight, target, min, max);
        graphics.fill(targetX, barY - 5, targetX + 1, barY + 8, 0xFFFFE28A);
    }

    private void renderQualityBars(GuiGraphics graphics) {
        int left = leftPos + PREVIEW_LEFT + 8;
        int top = topPos + PREVIEW_TOP + 10;
        int width = PREVIEW_WIDTH - 16;
        int height = 5;
        int coarse = data(LensGrindingBenchBlockEntity.DATA_COARSE_CHANCE);
        int clear = data(LensGrindingBenchBlockEntity.DATA_CLEAR_CHANCE);
        int precise = data(LensGrindingBenchBlockEntity.DATA_PRECISE_CHANCE);
        int coarseWidth = Math.round(width * coarse / 100.0F);
        int clearWidth = Math.round(width * clear / 100.0F);
        int preciseWidth = Math.max(0, width - coarseWidth - clearWidth);

        SpectralGui.drawInset(graphics, left - 1, top - 1, width + 2, height + 2);
        graphics.fill(left, top, left + coarseWidth, top + height, COLOR_COARSE);
        graphics.fill(left + coarseWidth, top, left + coarseWidth + clearWidth, top + height, COLOR_CLEAR);
        graphics.fill(left + coarseWidth + clearWidth, top, left + coarseWidth + clearWidth + preciseWidth, top + height, COLOR_PRECISE);
        if (precise > 0 && preciseWidth == 0) {
            graphics.fill(left + width - 1, top, left + width, top + height, COLOR_PRECISE);
        }
    }

    private void addSmallButton(int x, int y, int id, Component text) {
        addRenderableWidget(Button.builder(text, button -> click(id))
                .bounds(leftPos + x, topPos + y, 14, 14)
                .build());
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private String lensKindName() {
        return Component.translatable(lensKind().translationKey()).getString();
    }

    private String parameterSymbol() {
        return switch (lensKind()) {
            case CONVEX -> "f";
            case CONCAVE -> "d";
            case ENDER -> "r";
            case MAGMA -> "h";
            case ECHO -> "q";
        };
    }

    private LensKind lensKind() {
        return LensKind.byIndex(data(LensGrindingBenchBlockEntity.DATA_LENS_KIND));
    }

    private String qualityName(int quality) {
        return Component.translatable(switch (quality) {
            case 1 -> "item.spectralization.lens.quality.coarse";
            case 3 -> "item.spectralization.lens.quality.precise";
            default -> "item.spectralization.lens.quality.clear";
        }).getString();
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private void clippedText(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        String clipped = font.plainSubstrByWidth(text, width);
        graphics.drawString(font, clipped, x, y, color, false);
    }

    private static int interpolate(int left, int right, int value, int min, int max) {
        double ratio = (value - min) / (double) (max - min);
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        return left + (int) Math.round((right - left) * ratio);
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }
}
