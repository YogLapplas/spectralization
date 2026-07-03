package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.CreativeLightSourceBlockEntity;
import io.github.yoglappland.spectralization.menu.CreativeLightSourceMenu;
import io.github.yoglappland.spectralization.network.CreativeLightPowerPayload;
import io.github.yoglappland.spectralization.network.CreativeLightSpectrumPayload;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class CreativeLightSourceScreen extends AbstractContainerScreen<CreativeLightSourceMenu> {
    private static final int PAGE_PARAMETERS = 0;
    private static final int PAGE_SPECTRUM = 1;
    private static final int CHART_LEFT = 16;
    private static final int CHART_TOP = 40;
    private static final int CHART_WIDTH = 224;
    private static final int CHART_HEIGHT = 116;
    private static final int CHART_BOTTOM = CHART_TOP + CHART_HEIGHT;
    private static final int CHART_SNAP_PIXELS = 2;

    private int page = PAGE_PARAMETERS;
    private int lastSentSpectrumBin = -1;
    private int lastSentSpectrumWeight = -1;
    private EditBox powerBox;
    private int lastPowerBoxCenti = Integer.MIN_VALUE;
    private int lastSentPowerCenti = Integer.MIN_VALUE;

    public CreativeLightSourceScreen(CreativeLightSourceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 210;
        inventoryLabelY = imageHeight + 100;
    }

    @Override
    protected void init() {
        super.init();
        powerBox = null;

        addRenderableWidget(Button.builder(Component.literal(page == PAGE_PARAMETERS ? "Spectrum" : "Parameters"), button -> {
                    page = page == PAGE_PARAMETERS ? PAGE_SPECTRUM : PAGE_PARAMETERS;
                    rebuildWidgets();
                })
                .bounds(leftPos + 176, topPos + 6, 66, 16)
                .build());

        if (page != PAGE_PARAMETERS) {
            return;
        }

        int y = topPos + 24;
        addRow(y, CreativeLightSourceMenu.BUTTON_REGION_DOWN, CreativeLightSourceMenu.BUTTON_REGION_UP);
        addRow(y + 18, CreativeLightSourceMenu.BUTTON_BIN_DOWN, CreativeLightSourceMenu.BUTTON_BIN_UP);
        addRow(y + 36, CreativeLightSourceMenu.BUTTON_POWER_DOWN, CreativeLightSourceMenu.BUTTON_POWER_UP);
        addPowerBox(y + 36);
        addSingleButton(y + 54, CreativeLightSourceMenu.BUTTON_COHERENCE, "Toggle");
        addRow(y + 72, CreativeLightSourceMenu.BUTTON_MODEL_DOWN, CreativeLightSourceMenu.BUTTON_MODEL_UP);
        addRow(y + 90, CreativeLightSourceMenu.BUTTON_RADIUS_DOWN, CreativeLightSourceMenu.BUTTON_RADIUS_UP);
        addRow(y + 108, CreativeLightSourceMenu.BUTTON_DIVERGENCE_DOWN, CreativeLightSourceMenu.BUTTON_DIVERGENCE_UP);
        addRow(y + 126, CreativeLightSourceMenu.BUTTON_FOCUS_DOWN, CreativeLightSourceMenu.BUTTON_FOCUS_UP);
        addRow(y + 144, CreativeLightSourceMenu.BUTTON_MODE_M_DOWN, CreativeLightSourceMenu.BUTTON_MODE_M_UP);
        addRow(y + 162, CreativeLightSourceMenu.BUTTON_MODE_N_DOWN, CreativeLightSourceMenu.BUTTON_MODE_N_UP);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (page != PAGE_PARAMETERS || powerBox == null || powerBox.isFocused()) {
            return;
        }

        int powerCenti = data(CreativeLightSourceBlockEntity.DATA_POWER);

        if (powerCenti == lastPowerBoxCenti) {
            return;
        }

        powerBox.setValue(powerText(powerCenti));
        lastPowerBoxCenti = powerCenti;
        lastSentPowerCenti = powerCenti;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xDD101216);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xDD1B1F27);

        if (page == PAGE_SPECTRUM) {
            renderSpectrumChart(graphics);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EEF8, false);

        if (page == PAGE_SPECTRUM) {
            renderSpectrumLabels(graphics);
            return;
        }

        int y = 24;
        drawValue(graphics, y, "Region", regionName());
        drawValue(graphics, y + 18, "Bin", Integer.toString(data(CreativeLightSourceBlockEntity.DATA_BIN)));
        drawValue(graphics, y + 36, "Power", powerText(data(CreativeLightSourceBlockEntity.DATA_POWER)));
        drawValue(graphics, y + 54, "Coherence", coherenceName());
        drawValue(graphics, y + 72, "Model", beamModelName());
        drawValue(graphics, y + 90, "Radius", milli(CreativeLightSourceBlockEntity.DATA_RADIUS_MILLI));
        drawValue(graphics, y + 108, "Divergence", milli(CreativeLightSourceBlockEntity.DATA_DIVERGENCE_MILLI));
        drawValue(graphics, y + 126, "Focus", milli(CreativeLightSourceBlockEntity.DATA_FOCUS_DISTANCE_MILLI));
        drawValue(graphics, y + 144, "Mode M", Integer.toString(data(CreativeLightSourceBlockEntity.DATA_MODE_M)));
        drawValue(graphics, y + 162, "Mode N", Integer.toString(data(CreativeLightSourceBlockEntity.DATA_MODE_N)));
    }

    private void addRow(int y, int downId, int upId) {
        addSingleButton(y, downId, "-");
        addRenderableWidget(Button.builder(Component.literal("+"), button -> click(upId))
                .bounds(leftPos + 215, y, 26, 16)
                .build());
    }

    private void addPowerBox(int y) {
        int powerCenti = data(CreativeLightSourceBlockEntity.DATA_POWER);
        powerBox = new EditBox(font, leftPos + 116, y, 58, 16, Component.literal("Power"));
        powerBox.setMaxLength(12);
        powerBox.setFilter(CreativeLightSourceScreen::isPowerText);
        powerBox.setValue(powerText(powerCenti));
        lastPowerBoxCenti = powerCenti;
        lastSentPowerCenti = powerCenti;
        powerBox.setResponder(this::sendPowerFromText);
        addRenderableWidget(powerBox);
    }

    private void addSingleButton(int y, int id, String text) {
        int width = text.equals("Toggle") ? 54 : 26;
        addRenderableWidget(Button.builder(Component.literal(text), button -> click(id))
                .bounds(leftPos + 184, y, width, 16)
                .build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page == PAGE_SPECTRUM && button == 0 && updateSpectrumFromMouse(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (page == PAGE_SPECTRUM && button == 0 && updateSpectrumFromMouse(mouseX, mouseY)) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void drawValue(GuiGraphics graphics, int y, String label, String value) {
        graphics.drawString(font, label + ": " + value, 12, y + 4, 0xD8DEE9, false);
    }

    private void renderSpectrumChart(GuiGraphics graphics) {
        int left = leftPos + CHART_LEFT;
        int top = topPos + CHART_TOP;
        int right = left + CHART_WIDTH;
        int bottom = top + CHART_HEIGHT;
        int bins = activeSpectrumBins();

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF303744);
        graphics.fill(left, top, right, bottom, 0xCC080A0F);

        for (int bin = 0; bin < bins; bin++) {
            int slotLeft = left + bin * CHART_WIDTH / bins;
            int slotRight = left + (bin + 1) * CHART_WIDTH / bins;
            int barWidth = Math.max(1, slotRight - slotLeft - 1);
            int weight = spectrumWeight(bin);
            int barHeight = Math.round(CHART_HEIGHT * weight / (float) CreativeLightSourceBlockEntity.MAX_SPECTRUM_WEIGHT);
            int color = barColor(bin);

            if (barHeight <= 0) {
                graphics.fill(slotLeft, bottom - 1, slotLeft + barWidth, bottom, 0xFF2B313A);
                continue;
            }

            graphics.fill(slotLeft, bottom - barHeight, slotLeft + barWidth, bottom, 0xFF000000 | color);
        }
    }

    private void renderSpectrumLabels(GuiGraphics graphics) {
        graphics.drawString(font, "Region: " + regionName(), 12, 22, 0xD8DEE9, false);
        graphics.drawString(font, "Total power: " + powerText(data(CreativeLightSourceBlockEntity.DATA_POWER)), 122, 22, 0xD8DEE9, false);

        int totalWeight = totalSpectrumWeight();
        graphics.drawString(font, "Weight sum: " + totalWeight, 12, 164, 0xD8DEE9, false);
        graphics.drawString(font, "Emitted lanes: " + emittedLaneCount(), 122, 164, 0xD8DEE9, false);
        renderOutputLaneSummary(graphics, totalWeight);
    }

    private void renderOutputLaneSummary(GuiGraphics graphics, int totalWeight) {
        int[] strongestBins = strongestSpectrumBins();
        int y = 180;

        if (strongestBins.length == 0) {
            graphics.drawString(font, "Output: selected bin " + data(CreativeLightSourceBlockEntity.DATA_BIN), 12, y, 0xAEB8C5, false);
            return;
        }

        StringBuilder builder = new StringBuilder("Output:");
        int shown = Math.min(4, strongestBins.length);

        for (int index = 0; index < shown; index++) {
            int bin = strongestBins[index];
            int percent = totalWeight <= 0 ? 0 : Math.round(spectrumWeight(bin) * 100.0F / totalWeight);
            builder.append(' ').append(bin).append('=').append(percent).append('%');
        }

        if (strongestBins.length > shown) {
            builder.append(" ...");
        }

        graphics.drawString(font, builder.toString(), 12, y, 0xAEB8C5, false);
    }

    private boolean updateSpectrumFromMouse(double mouseX, double mouseY) {
        int left = leftPos + CHART_LEFT;
        int top = topPos + CHART_TOP;
        int right = left + CHART_WIDTH;
        int bottom = top + CHART_HEIGHT;

        if (mouseX < left || mouseX >= right || mouseY < top || mouseY > bottom) {
            return false;
        }

        int bins = activeSpectrumBins();
        int bin = Mth.clamp((int) ((mouseX - left) * bins / CHART_WIDTH), 0, bins - 1);
        int weight = spectrumWeightFromMouse(mouseY, top, bottom);
        boolean exclusive = weight == CreativeLightSourceBlockEntity.MAX_SPECTRUM_WEIGHT;

        if (!exclusive && bin == lastSentSpectrumBin && weight == lastSentSpectrumWeight) {
            return true;
        }

        lastSentSpectrumBin = bin;
        lastSentSpectrumWeight = weight;
        PacketDistributor.sendToServer(new CreativeLightSpectrumPayload(menu.containerId, bin, weight, exclusive));
        return true;
    }

    private static int spectrumWeightFromMouse(double mouseY, int top, int bottom) {
        if (mouseY <= top + CHART_SNAP_PIXELS) {
            return CreativeLightSourceBlockEntity.MAX_SPECTRUM_WEIGHT;
        }

        if (mouseY >= bottom - CHART_SNAP_PIXELS) {
            return 0;
        }

        return Mth.clamp(
                Math.round((float) ((bottom - mouseY) / CHART_HEIGHT * CreativeLightSourceBlockEntity.MAX_SPECTRUM_WEIGHT)),
                0,
                CreativeLightSourceBlockEntity.MAX_SPECTRUM_WEIGHT
        );
    }

    private void sendPowerFromText(String value) {
        int powerCenti = parsePowerCenti(value);

        if (powerCenti < 0 || powerCenti == lastSentPowerCenti) {
            return;
        }

        lastSentPowerCenti = powerCenti;
        lastPowerBoxCenti = powerCenti;
        PacketDistributor.sendToServer(new CreativeLightPowerPayload(menu.containerId, powerCenti));
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private String regionName() {
        SpectralRegion[] regions = SpectralRegion.values();
        int index = Math.max(0, Math.min(data(CreativeLightSourceBlockEntity.DATA_REGION), regions.length - 1));
        return regions[index].id();
    }

    private SpectralRegion activeRegion() {
        SpectralRegion[] regions = SpectralRegion.values();
        int index = Mth.clamp(data(CreativeLightSourceBlockEntity.DATA_REGION), 0, regions.length - 1);
        return regions[index];
    }

    private int activeSpectrumBins() {
        return Math.min(activeRegion().defaultBins(), CreativeLightSourceBlockEntity.MAX_SPECTRUM_BINS);
    }

    private int spectrumWeight(int bin) {
        return data(CreativeLightSourceBlockEntity.DATA_SPECTRUM_START + bin);
    }

    private int totalSpectrumWeight() {
        int totalWeight = 0;

        for (int bin = 0; bin < activeSpectrumBins(); bin++) {
            totalWeight += spectrumWeight(bin);
        }

        return totalWeight;
    }

    private int emittedLaneCount() {
        int count = 0;

        for (int bin = 0; bin < activeSpectrumBins(); bin++) {
            if (spectrumWeight(bin) > 0) {
                count++;
            }
        }

        return Math.min(count, BeamPacket.MAX_COMPONENTS);
    }

    private int[] strongestSpectrumBins() {
        int bins = activeSpectrumBins();
        int[] strongest = new int[Math.min(8, bins)];
        int count = 0;

        for (int bin = 0; bin < bins; bin++) {
            int weight = spectrumWeight(bin);

            if (weight <= 0) {
                continue;
            }

            int insertAt = count;
            while (insertAt > 0 && spectrumWeight(strongest[insertAt - 1]) < weight) {
                if (insertAt < strongest.length) {
                    strongest[insertAt] = strongest[insertAt - 1];
                }
                insertAt--;
            }

            if (insertAt < strongest.length) {
                strongest[insertAt] = bin;
            }

            if (count < strongest.length) {
                count++;
            }
        }

        return java.util.Arrays.copyOf(strongest, count);
    }

    private int barColor(int bin) {
        return activeRegion() == SpectralRegion.VISIBLE
                ? SpectralColorMap.visibleRgbForBin(bin)
                : 0x8992A3;
    }

    private String coherenceName() {
        CoherenceKind[] kinds = CoherenceKind.values();
        int index = Math.max(0, Math.min(data(CreativeLightSourceBlockEntity.DATA_COHERENCE), kinds.length - 1));
        return kinds[index].name().toLowerCase();
    }

    private String beamModelName() {
        BeamModel[] models = BeamModel.values();
        int index = Math.max(0, Math.min(data(CreativeLightSourceBlockEntity.DATA_BEAM_MODEL), models.length - 1));
        return models[index].name().toLowerCase();
    }

    private String milli(int index) {
        return String.format("%.3f", data(index) / 1000.0);
    }

    private static boolean isPowerText(String value) {
        if (value.isEmpty()) {
            return true;
        }

        int decimalPoint = -1;
        int decimals = 0;

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);

            if (character >= '0' && character <= '9') {
                if (decimalPoint >= 0) {
                    decimals++;
                }

                if (decimals > 2) {
                    return false;
                }

                continue;
            }

            if (character == '.' && decimalPoint < 0) {
                decimalPoint = index;
                continue;
            }

            return false;
        }

        return true;
    }

    private static int parsePowerCenti(String value) {
        if (value.isEmpty() || ".".equals(value)) {
            return -1;
        }

        try {
            double parsed = Double.parseDouble(value);

            if (!Double.isFinite(parsed)) {
                return -1;
            }

            return Mth.clamp(
                    (int) Math.round(parsed * CreativeLightSourceBlockEntity.POWER_SCALE),
                    0,
                    CreativeLightSourceBlockEntity.MAX_POWER_CENTI
            );
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static String powerText(int powerCenti) {
        return String.format(Locale.ROOT, "%.2f", powerCenti / (double) CreativeLightSourceBlockEntity.POWER_SCALE);
    }
}
