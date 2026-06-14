package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.client.gui.SpectralGui;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.compact.CompactedMachineFaceColor;
import io.github.yoglappland.spectralization.menu.CompactedMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class CompactedMachineScreen extends SpectralMachineScreen<CompactedMachineMenu> {
    private static final Direction[] FACE_ORDER = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST,
            Direction.UP,
            Direction.DOWN
    };

    private final Button[] colorButtons = new Button[FACE_ORDER.length];

    public CompactedMachineScreen(CompactedMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "compacted_machine", 256, 170, 0, 160);
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> click(CompactedMachineMenu.BUTTON_ROTATE_COUNTERCLOCKWISE)
                )
                .bounds(leftPos + 136, topPos + 84, 22, 18)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> click(CompactedMachineMenu.BUTTON_ROTATE_CLOCKWISE)
                )
                .bounds(leftPos + 162, topPos + 84, 22, 18)
                .build());

        for (int index = 0; index < FACE_ORDER.length; index++) {
            Direction face = FACE_ORDER[index];
            Button button = Button.builder(
                            colorButtonText(face),
                            clicked -> click(CompactedMachineMenu.BUTTON_FACE_COLOR_BASE + face.ordinal())
                    )
                    .bounds(leftPos + 124 + (index % 3) * 36, topPos + 122 + (index / 3) * 18, 34, 14)
                    .build();
            colorButtons[index] = button;
            addRenderableWidget(button);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int index = 0; index < FACE_ORDER.length; index++) {
            colorButtons[index].setMessage(colorButtonText(FACE_ORDER[index]));
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawMachineBackground(graphics);
        panel(graphics, "preview_panel", 18, 22, 88, 132);
        panel(graphics, "info_panel", 112, 22, 126, 132);
        SpectralGui.drawInset(graphics, leftPos + 32, topPos + 48, 60, 60);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);
        mutedText(graphics, "snapshot_title", label("screen.spectralization.compacted_machine.snapshot"), 32, 34, 60);
        mutedText(graphics, "snapshot_pending", label("screen.spectralization.compacted_machine.snapshot_pending"), 34, 74, 56);

        text(graphics, "size_text", label("screen.spectralization.compacted_machine.size")
                + ": " + dimensions(menu.sizeX(), menu.sizeY(), menu.sizeZ()), 124, 34, 104);
        text(graphics, "blocks_text", label("screen.spectralization.compacted_machine.blocks")
                + ": " + countAndTypes(menu.blockCount(), menu.typeCount()), 124, 48, 104);
        text(graphics, "io_text", label("screen.spectralization.compacted_machine.io")
                + ": " + menu.ioFaceCount(), 124, 62, 104);
        text(graphics, "mapping_text", label("screen.spectralization.compacted_machine.mapping")
                + ": " + menu.transferCount() + "/" + menu.sourceCount(), 124, 76, 104);
        text(graphics, "facing_text", label("screen.spectralization.compacted_machine.facing")
                + ": " + directionLabel(menu.facing()), 190, 88, 38);

        mutedText(graphics, "rotation_title", label("screen.spectralization.compacted_machine.rotation"), 124, 88, 44);
        mutedText(graphics, "face_colors_title", label("screen.spectralization.compacted_machine.face_colors"), 124, 110, 98);
        renderColorSwatches(graphics);
    }

    private void renderColorSwatches(GuiGraphics graphics) {
        for (int index = 0; index < FACE_ORDER.length; index++) {
            int localX = 126 + (index % 3) * 36;
            int localY = 126 + (index / 3) * 18;
            CompactedMachineFaceColor color = menu.faceColor(FACE_ORDER[index]);
            graphics.fill(localX, localY, localX + 6, localY + 6, 0xFF050705);
            graphics.fill(localX + 1, localY + 1, localX + 5, localY + 5, color.argb());
        }
    }

    private Component colorButtonText(Direction face) {
        return Component.literal(directionLabel(face).substring(0, 1));
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    private static String directionLabel(Direction direction) {
        return Component.translatable("direction.spectralization." + direction.getName()).getString();
    }

    private static String dimensions(int x, int y, int z) {
        return x + "x" + y + "x" + z;
    }

    private static String countAndTypes(int count, int types) {
        return Component.translatable("screen.spectralization.compact_machine_core.count_types", count, types).getString();
    }
}
