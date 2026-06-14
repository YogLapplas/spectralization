package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.client.compact.ClientCompactMachineWorkAreaOverlayCache;
import io.github.yoglappland.spectralization.client.gui.SpectralGuiTheme;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.menu.CompactMachineCoreMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class CompactMachineCoreScreen extends SpectralMachineScreen<CompactMachineCoreMenu> {
    private static final int PAGE_STATUS = 0;
    private static final int PAGE_COMPACTING = 1;
    private static final int ERROR_COLOR = 0xFF7A1E2B;
    private int page = PAGE_STATUS;
    private Button workAreaButton;
    private Button startCompactingButton;

    public CompactMachineCoreScreen(CompactMachineCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "compact_machine_core", 256, 170, 0, 160);
    }

    @Override
    protected void init() {
        super.init();
        workAreaButton = null;
        startCompactingButton = null;

        addRenderableWidget(Button.builder(
                        Component.translatable("screen.spectralization.compact_machine_core.page_status"),
                        button -> switchPage(PAGE_STATUS)
                )
                .bounds(leftPos + 162, topPos + 6, 36, 16)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("screen.spectralization.compact_machine_core.page_compacting"),
                        button -> switchPage(PAGE_COMPACTING)
                )
                .bounds(leftPos + 202, topPos + 6, 36, 16)
                .build());

        if (page == PAGE_STATUS) {
            workAreaButton = Button.builder(
                        Component.translatable("screen.spectralization.compact_machine_core.show_work_area"),
                        button -> toggleWorkArea()
                )
                .bounds(leftPos + 24, topPos + 126, 76, 20)
                .build();
            workAreaButton.active = menu.hasWorkArea();
            addRenderableWidget(workAreaButton);
        } else {
            startCompactingButton = Button.builder(
                            Component.translatable("screen.spectralization.compact_machine_core.start_compacting"),
                            button -> click(CompactMachineCoreMenu.BUTTON_START_COMPACTING)
                    )
                    .bounds(leftPos + 24, topPos + 126, 76, 20)
                    .build();
            startCompactingButton.active = menu.valid() && menu.outputEmpty();
            addRenderableWidget(startCompactingButton);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (workAreaButton != null) {
            workAreaButton.active = menu.hasWorkArea();
        }
        if (startCompactingButton != null) {
            startCompactingButton.active = menu.valid() && menu.outputEmpty();
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawMachineBackground(graphics);
        panel(graphics, "machine_panel", 18, 22, 220, 132);
        if (page == PAGE_COMPACTING) {
            slot(graphics, "compacted_output_slot", CompactMachineCoreMenu.OUTPUT_SLOT_X, CompactMachineCoreMenu.OUTPUT_SLOT_Y, io.github.yoglappland.spectralization.client.gui.SpectralSlotKind.OUTPUT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);
        if (page == PAGE_COMPACTING) {
            renderCompactingLabels(graphics);
            return;
        }

        int statusColor = menu.valid() ? SpectralGuiTheme.STABLE : ERROR_COLOR;
        graphics.drawString(font, statusText(), 96, 34, statusColor, false);

        text(graphics, "shell_text", label("screen.spectralization.compact_machine_core.shell")
                + ": " + dimensions(menu.sizeX(), menu.sizeY(), menu.sizeZ()), 96, 50, 132);
        text(graphics, "work_text", label("screen.spectralization.compact_machine_core.work_area")
                + ": " + dimensions(menu.workSizeX(), menu.workSizeY(), menu.workSizeZ()), 96, 64, 132);
        text(graphics, "parts_text", label("screen.spectralization.compact_machine_core.compact_parts")
                + ": " + countAndTypes(menu.compactBlocks(), menu.compactTypes()), 96, 78, 132);
        text(graphics, "payload_text", label("screen.spectralization.compact_machine_core.payload")
                + ": " + countAndTypes(menu.payloadBlocks(), menu.payloadTypes()), 96, 92, 132);
        text(graphics, "connection_text", label("screen.spectralization.compact_machine_core.connections")
                + ": " + menu.connections() + "/12", 96, 106, 132);
        text(graphics, "io_text", label("screen.spectralization.compact_machine_core.io_ports")
                + ": " + menu.ioPorts() + "/6", 96, 120, 132);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (page != PAGE_COMPACTING && slot.index == CompactMachineCoreMenu.OUTPUT_SLOT_INDEX) {
            return;
        }

        super.renderSlot(graphics, slot);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page != PAGE_COMPACTING && isOutputSlotArea(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleWorkArea() {
        if (!menu.hasWorkArea()) {
            return;
        }

        ClientCompactMachineWorkAreaOverlayCache.toggle(menu.corePos(), menu.workMin(), menu.workMax());
    }

    private void switchPage(int nextPage) {
        if (page == nextPage) {
            return;
        }

        page = nextPage;
        rebuildWidgets();
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void renderCompactingLabels(GuiGraphics graphics) {
        text(graphics, "compacting_output_text", label("screen.spectralization.compact_machine_core.output_slot"), 96, 50, 132);
        mutedText(graphics, "compacting_hint_text", label("screen.spectralization.compact_machine_core.output_hint"), 96, 64, 132);
        text(graphics, "compacting_work_text", label("screen.spectralization.compact_machine_core.work_area")
                + ": " + dimensions(menu.workSizeX(), menu.workSizeY(), menu.workSizeZ()), 96, 88, 132);
        text(graphics, "compacting_payload_text", label("screen.spectralization.compact_machine_core.payload")
                + ": " + countAndTypes(menu.payloadBlocks(), menu.payloadTypes()), 96, 102, 132);
    }

    private boolean isOutputSlotArea(double mouseX, double mouseY) {
        int left = leftPos + CompactMachineCoreMenu.OUTPUT_SLOT_X - 1;
        int top = topPos + CompactMachineCoreMenu.OUTPUT_SLOT_Y - 1;
        return mouseX >= left
                && mouseX < left + 18
                && mouseY >= top
                && mouseY < top + 18;
    }

    private Component statusText() {
        if (!menu.present()) {
            return Component.translatable("screen.spectralization.compact_machine_core.status_missing");
        }

        if (!menu.valid()) {
            return Component.translatable("screen.spectralization.compact_machine_core.status_error");
        }

        return Component.translatable("screen.spectralization.compact_machine_core.status_ready");
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    private static String dimensions(int x, int y, int z) {
        return x + "x" + y + "x" + z;
    }

    private static String countAndTypes(int count, int types) {
        return Component.translatable("screen.spectralization.compact_machine_core.count_types", count, types).getString();
    }
}
