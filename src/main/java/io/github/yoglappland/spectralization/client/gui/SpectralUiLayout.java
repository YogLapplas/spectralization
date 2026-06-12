package io.github.yoglappland.spectralization.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class SpectralUiLayout {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, LayoutFile> CACHE = new HashMap<>();

    public static SpectralUiRect rect(
            String key,
            String id,
            String type,
            String label,
            int x,
            int y,
            int width,
            int height,
            int screenWidth,
            int screenHeight
    ) {
        LayoutFile layout = load(key, screenWidth, screenHeight);
        LayoutComponent component = layout.componentsById.get(id);

        if (component == null) {
            return new SpectralUiRect(x, y, width, height);
        }

        return new SpectralUiRect(component.x, component.y, component.w, component.h);
    }

    public static void updateRect(
            String key,
            String id,
            String type,
            String label,
            int x,
            int y,
            int width,
            int height,
            int screenWidth,
            int screenHeight,
            boolean save
    ) {
        LayoutFile layout = load(key, screenWidth, screenHeight);
        layout.width = screenWidth;
        layout.height = screenHeight;
        LayoutComponent component = layout.componentsById.computeIfAbsent(id, ignored -> {
            LayoutComponent created = new LayoutComponent();
            created.id = id;
            layout.components.add(created);
            return created;
        });

        component.type = type;
        component.label = label;
        component.x = x;
        component.y = y;
        component.w = width;
        component.h = height;
        component.color = component.color == null ? "#66CCFF" : component.color;
        component.data = component.data == null ? "" : component.data;

        if (save) {
            save(key);
        }
    }

    public static void save(String key) {
        LayoutFile layout = CACHE.get(normalizeKey(key));

        if (layout == null) {
            return;
        }

        try {
            Path path = path(key);
            Files.createDirectories(path.getParent());

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(layout.toSerializable(), writer);
            }
        } catch (IOException ignored) {
            // UI layout editing is a development aid; failing to save should not break gameplay screens.
        }
    }

    private static LayoutFile load(String key, int screenWidth, int screenHeight) {
        String normalized = normalizeKey(key);
        LayoutFile cached = CACHE.get(normalized);

        if (cached != null) {
            return cached;
        }

        LayoutFile layout = read(key);
        layout.width = layout.width <= 0 ? screenWidth : layout.width;
        layout.height = layout.height <= 0 ? screenHeight : layout.height;
        CACHE.put(normalized, layout);
        return layout;
    }

    private static LayoutFile read(String key) {
        Path path = path(key);

        if (!Files.isRegularFile(path)) {
            return new LayoutFile();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement element = JsonParser.parseReader(reader);
            SerializableLayout serializable;

            if (element.isJsonArray()) {
                serializable = new SerializableLayout();
                serializable.components = GSON.fromJson(element, ComponentList.class);
            } else {
                serializable = GSON.fromJson(element, SerializableLayout.class);
            }

            return LayoutFile.fromSerializable(serializable);
        } catch (Exception ignored) {
            return new LayoutFile();
        }
    }

    private static Path path(String key) {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("spectralization")
                .resolve("ui_layouts")
                .resolve(normalizeKey(key) + ".json");
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-.]+", "_");
    }

    private static final class LayoutFile {
        int width;
        int height;
        final List<LayoutComponent> components = new ArrayList<>();
        final Map<String, LayoutComponent> componentsById = new LinkedHashMap<>();

        static LayoutFile fromSerializable(SerializableLayout serializable) {
            LayoutFile file = new LayoutFile();

            if (serializable == null) {
                return file;
            }

            file.width = serializable.width;
            file.height = serializable.height;

            if (serializable.components != null) {
                for (LayoutComponent component : serializable.components) {
                    if (component == null || component.id == null || component.id.isBlank()) {
                        continue;
                    }

                    file.components.add(component);
                    file.componentsById.put(component.id, component);
                }
            }

            return file;
        }

        SerializableLayout toSerializable() {
            SerializableLayout serializable = new SerializableLayout();
            serializable.width = width;
            serializable.height = height;
            serializable.components = components;
            return serializable;
        }
    }

    private static final class SerializableLayout {
        int width;
        int height;
        List<LayoutComponent> components = new ArrayList<>();
    }

    private static final class LayoutComponent {
        String id;
        String type = "custom";
        String label = "";
        int x;
        int y;
        int w;
        int h;
        String color = "#66CCFF";
        String data = "";
    }

    private static final class ComponentList extends ArrayList<LayoutComponent> {
    }

    private SpectralUiLayout() {
    }
}
