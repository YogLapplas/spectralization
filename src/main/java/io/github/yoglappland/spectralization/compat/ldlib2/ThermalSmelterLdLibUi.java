package io.github.yoglappland.spectralization.compat.ldlib2;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.FilePath;
import com.lowdragmc.lowdraglib2.editor.resource.UIResource;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.appliedenergistics.yoga.YogaPositionType;

public final class ThermalSmelterLdLibUi {
    public static final String TEMPLATE_FILE_NAME = "thermal_smelter.ui.nbt";
    private static final String GLOBAL_EXAMPLE_LAYOUT = "official_example_layout.ui.nbt";
    private static final String GLOBAL_EXAMPLE_BUTTON = "official_button.ui.nbt";
    private static final List<AssetCopy> OFFICIAL_EDITOR_ASSETS = List.of(
            new AssetCopy(
                    "assets/ldlib2/resources/examples/example_layout.ui.nbt",
                    "ldlib2/resources/examples/example_layout.ui.nbt"
            ),
            new AssetCopy(
                    "assets/ldlib2/resources/examples/button.ui.nbt",
                    "ldlib2/resources/examples/button.ui.nbt"
            ),
            new AssetCopy(
                    "assets/ldlib2/resources/examples/example_layout.ui.nbt",
                    "ldlib2/resources/global/" + GLOBAL_EXAMPLE_LAYOUT
            ),
            new AssetCopy(
                    "assets/ldlib2/resources/examples/button.ui.nbt",
                    "ldlib2/resources/global/" + GLOBAL_EXAMPLE_BUTTON
            ),
            new AssetCopy("assets/ldlib2/lss/gdp.lss", "ldlib2/lss/gdp.lss"),
            new AssetCopy("assets/ldlib2/lss/mc.lss", "ldlib2/lss/mc.lss"),
            new AssetCopy("assets/ldlib2/lss/modern.lss", "ldlib2/lss/modern.lss"),
            new AssetCopy("assets/ldlib2/textures/gui/icon/global.png", "ldlib2/textures/gui/icon/global.png")
    );

    private static final int WIDTH = 256;
    private static final int HEIGHT = 216;
    private static final int TEXT_SUB = 0xFF6A706B;
    private static final int GUI_BG = 0xFFEDEAE3;
    private static final int MACHINE_BG = 0xFFE3E0D9;
    private static final int PANEL = 0xFFD4D1C8;
    private static final int CHAMBER_BG = 0xFFEEECEA;
    private static final int CHAMBER_CORE = 0xC8FF7A3D;
    private static final int HEAT = 0xFFFF7A3D;
    private static final int OPTICAL = 0xFF42C7C7;
    private static final int PROGRESS = 0xFFF2B84B;
    private static final int BAR_BG = 0xFFBDB9AF;

    private ThermalSmelterLdLibUi() {
    }

    public static ModularUI create(ThermalSmelterBlockEntity smelter, net.minecraft.world.entity.player.Player player) {
        UI ui = loadTemplate().orElseGet(() -> createFallbackUi(smelter));
        bindOrAddFunctionalElements(ui, smelter);
        return ModularUI.of(ui, player);
    }

    public static void ensureEditorResources() {
        ensureOfficialEditorAssets();
        ensureEditableStarterTemplate();
    }

    public static List<File> ensureOfficialEditorAssets() {
        List<File> targets = new ArrayList<>();

        for (AssetCopy asset : OFFICIAL_EDITOR_ASSETS) {
            File target = new File(LDLib2.getAssetsDir(), asset.targetPath());
            targets.add(target);
            copyOfficialAsset(asset.sourcePath(), target);
        }

        return targets;
    }

    public static File ensureEditableStarterTemplate() {
        File templateFile = templateFile();

        if (templateFile.isFile()) {
            return templateFile;
        }

        try {
            FileResourceProvider<UITemplate> provider = new FileResourceProvider<>(
                    UIResource.INSTANCE.getResourceInstance(),
                    templateFile.getParentFile()
            );
            provider.setName("global");
            if (!provider.addResource(new FilePath(templateFile), createStarterTemplate())) {
                Spectralization.LOGGER.warn("LDLib2 rejected thermal smelter UI starter path {}", templateFile.getPath());
                return templateFile;
            }

            UIResource.INSTANCE.getResourceInstance().clearCache();
            Spectralization.LOGGER.info("Created editable LDLib2 thermal smelter UI starter at {}", templateFile.getPath());
        } catch (RuntimeException exception) {
            Spectralization.LOGGER.warn("Failed to create editable LDLib2 thermal smelter UI starter", exception);
        }

        return templateFile;
    }

    private static Optional<UI> loadTemplate() {
        ensureEditableStarterTemplate();

        try {
            UITemplate template = UIResource.INSTANCE.getResourceInstance().getResource(new FilePath(templateFile()));

            if (template != null) {
                return Optional.of(template.createUI());
            }
        } catch (RuntimeException exception) {
            Spectralization.LOGGER.warn("Failed to load LDLib2 thermal smelter UI template {}", templateFile().getPath(), exception);
        }

        return Optional.empty();
    }

    private static UI createFallbackUi(ThermalSmelterBlockEntity smelter) {
        return createStarterTemplate().createUI();
    }

    private static UITemplate createStarterTemplate() {
        UIElement root = rect("thermal_smelter_root", 0, 0, WIDTH, HEIGHT, GUI_BG);
        root.addChildren(
                rect("machine_band", 0, 0, WIDTH, 108, MACHINE_BG),
                rect("left_panel", 6, 10, 44, 86, PANEL),
                rect("right_panel", 206, 10, 44, 86, PANEL),
                rect("chamber", 54, 8, 132, 88, CHAMBER_BG),
                rect("chamber_core", 102, 38, 36, 28, CHAMBER_CORE),
                rect("heat_icon", 24, 76, 8, 8, HEAT),
                rect("optical_icon", 64, 18, 8, 8, OPTICAL),
                rect("progress_icon", 142, 60, 8, 8, PROGRESS),
                progressBar("heat_bar", 38, 18, 8, 68, FillDirection.DOWN_TO_UP, BAR_BG, HEAT),
                progressBar("optical_bar", 78, 20, 48, 6, FillDirection.LEFT_TO_RIGHT, BAR_BG, OPTICAL),
                progressBar("progress_bar", 104, 48, 48, 8, FillDirection.LEFT_TO_RIGHT, BAR_BG, PROGRESS),
                label("title_label", "THERMAL SMELTER", 78, 101, 100, 9, TEXT_SUB)
        );

        return UITemplate.of(root, StylesheetManager.GDP);
    }

    private static void bindOrAddFunctionalElements(UI ui, ThermalSmelterBlockEntity smelter) {
        bindSlotOrAdd(ui, "slot_input", smelter, ThermalSmelterBlockEntity.SLOT_INPUT, 16, 23);
        bindSlotOrAdd(ui, "slot_additive", smelter, ThermalSmelterBlockEntity.SLOT_ADDITIVE, 16, 45);
        bindSlotOrAdd(ui, "slot_output", smelter, ThermalSmelterBlockEntity.SLOT_OUTPUT, 213, 34);
        bindInventoryOrAdd(ui);
        bindLabels(ui, smelter);
        bindProgressBarsOrAdd(ui, smelter);
    }

    private static void bindSlotOrAdd(UI ui, String id, ThermalSmelterBlockEntity smelter, int slot, int x, int y) {
        Optional<ItemSlot> existing = ui.selectId(id, ItemSlot.class).findFirst();

        if (existing.isPresent()) {
            existing.get().bind(smelter.items(), slot);
            return;
        }

        ui.rootElement.addChild(itemSlot(id, smelter, slot, x, y));
    }

    private static ItemSlot itemSlot(String id, ThermalSmelterBlockEntity smelter, int slot, int x, int y) {
        ItemSlot itemSlot = new ItemSlot().bind(smelter.items(), slot);
        itemSlot.setId(id);
        applyRect(itemSlot, x, y, 18, 18);
        return itemSlot;
    }

    private static void bindInventoryOrAdd(UI ui) {
        if (ui.selectId("player_inventory", InventorySlots.class).findFirst().isPresent()) {
            return;
        }

        InventorySlots inventorySlots = new InventorySlots();
        inventorySlots.setId("player_inventory");
        applyRect(inventorySlots, 49, 125, 162, 76);
        ui.rootElement.addChild(inventorySlots);
    }

    private static void bindLabels(UI ui, ThermalSmelterBlockEntity smelter) {
        bindLabel(ui, "temperature_label", () -> Component.literal(data(smelter, ThermalSmelterBlockEntity.DATA_TEMPERATURE) + " K"));
        bindLabel(ui, "heat_label", () -> Component.literal("Heat " + heatPercent(smelter) + "%"));
        bindLabel(ui, "optical_label", () -> Component.literal("OPT " + opticalPercent(smelter) + "%"));
        bindLabel(ui, "progress_label", () -> Component.literal("Progress " + progressPercent(smelter) + "%"));
    }

    private static void bindLabel(UI ui, String id, java.util.function.Supplier<Component> value) {
        ui.selectId(id, Label.class)
                .forEach(label -> label.bind(DataBindingBuilder.componentS2C(value).build()));
    }

    private static void bindProgressBarsOrAdd(UI ui, ThermalSmelterBlockEntity smelter) {
        bindProgressBar(ui, "heat_bar", () -> (float) heatRatio(smelter), 38, 18, 8, 68, FillDirection.DOWN_TO_UP, HEAT);
        bindProgressBar(ui, "optical_bar", () -> (float) opticalRatio(smelter), 78, 20, 48, 6, FillDirection.LEFT_TO_RIGHT, OPTICAL);
        bindProgressBar(ui, "progress_bar", () -> (float) progressRatio(smelter), 104, 48, 48, 8, FillDirection.LEFT_TO_RIGHT, PROGRESS);
    }

    private static void bindProgressBar(
            UI ui,
            String id,
            java.util.function.Supplier<Float> value,
            int x,
            int y,
            int width,
            int height,
            FillDirection fillDirection,
            int color
    ) {
        Optional<ProgressBar> existing = ui.selectId(id, ProgressBar.class).findFirst();
        ProgressBar progressBar = existing.orElseGet(() -> {
            ProgressBar added = progressBar(id, x, y, width, height, fillDirection, BAR_BG, color);
            ui.rootElement.addChild(added);
            return added;
        });
        progressBar.setRange(0.0F, 1.0F);
        progressBar.bind(DataBindingBuilder.floatValS2C(value).build());
    }

    private static ProgressBar progressBar(
            String id,
            int x,
            int y,
            int width,
            int height,
            FillDirection fillDirection,
            int background,
            int fill
    ) {
        ProgressBar progressBar = new ProgressBar();
        progressBar.setId(id);
        applyRect(progressBar, x, y, width, height);
        progressBar.setRange(0.0F, 1.0F);
        progressBar.progressBarStyle(style -> style.fillDirection(fillDirection).interpolate(true));
        progressBar.label(label -> label.setVisible(false));
        progressBar.barBackground.style(style -> style.background(new ColorRectTexture(background)));
        progressBar.bar.style(style -> style.background(new ColorRectTexture(fill)));
        return progressBar;
    }

    private static UIElement rect(String id, int x, int y, int width, int height, int color) {
        UIElement element = new UIElement().setId(id);
        applyRect(element, x, y, width, height);
        element.style(style -> style.background(new ColorRectTexture(color)));
        return element;
    }

    private static Label label(String id, String text, int x, int y, int width, int height, int color) {
        Label label = new Label();
        label.setText(text);
        label.setId(id);
        applyRect(label, x, y, width, height);
        label.style(style -> style.color(color));
        label.textStyle(style -> style.textColor(color).textShadow(false).fontSize(0.75F));
        return label;
    }

    private static void applyRect(UIElement element, int x, int y, int width, int height) {
        element.layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(x)
                .top(y)
                .width(width)
                .height(height));
    }

    private static int data(ThermalSmelterBlockEntity smelter, int index) {
        return smelter.dataValue(index);
    }

    private static int heatPercent(ThermalSmelterBlockEntity smelter) {
        return (int) Math.round(heatRatio(smelter) * 100.0);
    }

    private static int opticalPercent(ThermalSmelterBlockEntity smelter) {
        return (int) Math.round(opticalRatio(smelter) * 100.0);
    }

    private static int progressPercent(ThermalSmelterBlockEntity smelter) {
        return (int) Math.round(progressRatio(smelter) * 100.0);
    }

    private static double progressRatio(ThermalSmelterBlockEntity smelter) {
        return ratio(
                data(smelter, ThermalSmelterBlockEntity.DATA_PROGRESS),
                data(smelter, ThermalSmelterBlockEntity.DATA_PROGRESS_REQUIRED)
        );
    }

    private static double heatRatio(ThermalSmelterBlockEntity smelter) {
        return ratio(
                data(smelter, ThermalSmelterBlockEntity.DATA_HEAT),
                Math.max(1, data(smelter, ThermalSmelterBlockEntity.DATA_MAX_HEAT))
        );
    }

    private static double opticalRatio(ThermalSmelterBlockEntity smelter) {
        return ratio(data(smelter, ThermalSmelterBlockEntity.DATA_HEAT_POWER_X100), 1000.0);
    }

    private static File templateFile() {
        return new File(new File(LDLib2.getAssetsDir(), "ldlib2/resources/global"), TEMPLATE_FILE_NAME);
    }

    private static void copyOfficialAsset(String sourcePath, File targetFile) {
        Path targetPath = targetFile.toPath();

        if (Files.isRegularFile(targetPath)) {
            return;
        }

        try {
            Files.createDirectories(targetPath.getParent());

            try (InputStream stream = officialAssetStream(sourcePath)) {
                if (stream == null) {
                    Spectralization.LOGGER.warn("LDLib2 official editor asset is missing from classpath: {}", sourcePath);
                    return;
                }

                Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Spectralization.LOGGER.info("Installed LDLib2 official editor asset {}", targetFile.getPath());
        } catch (IOException exception) {
            Spectralization.LOGGER.warn("Failed to install LDLib2 official editor asset {}", targetFile.getPath(), exception);
        }
    }

    private static InputStream officialAssetStream(String path) {
        InputStream stream = LDLib2.class.getClassLoader().getResourceAsStream(path);

        if (stream != null) {
            return stream;
        }

        return ThermalSmelterLdLibUi.class.getClassLoader().getResourceAsStream(path);
    }

    private static double ratio(double value, double max) {
        if (!Double.isFinite(value) || !Double.isFinite(max) || max <= 0.0) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value / max));
    }

    private record AssetCopy(String sourcePath, String targetPath) {
    }
}
