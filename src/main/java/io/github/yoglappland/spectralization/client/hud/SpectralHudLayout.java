package io.github.yoglappland.spectralization.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

public final class SpectralHudLayout {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, WindowState> WINDOWS = new LinkedHashMap<>();
    private static boolean loaded;

    public static WindowState state(String id, int defaultX, int defaultY, int defaultWidth, int defaultHeight) {
        load();
        return WINDOWS.computeIfAbsent(id, ignored -> {
            WindowState state = new WindowState();
            state.id = id;
            state.x = defaultX;
            state.y = defaultY;
            state.w = defaultWidth;
            state.h = defaultHeight;
            state.minimized = false;
            return state;
        });
    }

    public static void save() {
        load();

        try {
            Path path = path();
            Files.createDirectories(path.getParent());

            LayoutFile file = new LayoutFile();
            file.windows = new ArrayList<>(WINDOWS.values());

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(file, writer);
            }
        } catch (IOException ignored) {
            // HUD layout is local cosmetic state; failure to save should not interrupt gameplay.
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }

        loaded = true;
        Path path = path();

        if (!Files.isRegularFile(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            LayoutFile file = GSON.fromJson(reader, LayoutFile.class);

            if (file == null || file.windows == null) {
                return;
            }

            for (WindowState state : file.windows) {
                if (state != null && state.id != null && !state.id.isBlank()) {
                    WINDOWS.put(state.id, state);
                }
            }
        } catch (Exception ignored) {
            WINDOWS.clear();
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("spectralization")
                .resolve("hud_layouts")
                .resolve("windows.json");
    }

    private static final class LayoutFile {
        List<WindowState> windows = new ArrayList<>();
    }

    public static final class WindowState {
        public String id;
        public int x;
        public int y;
        public int w;
        public int h;
        public boolean minimized;
    }

    private SpectralHudLayout() {
    }
}
