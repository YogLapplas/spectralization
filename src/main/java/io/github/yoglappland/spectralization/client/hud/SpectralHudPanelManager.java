package io.github.yoglappland.spectralization.client.hud;

import io.github.yoglappland.spectralization.client.beam.ClientBeamPathCache;
import io.github.yoglappland.spectralization.client.networkoverlay.ClientNetworkOverlayCache;
import io.github.yoglappland.spectralization.client.spot.ClientSpotCache;
import io.github.yoglappland.spectralization.client.surface.ClientSurfaceInspectionCache;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class SpectralHudPanelManager {
    private static final int PRIMARY = 0xFF66CCFF;
    private static final int PRIMARY_DIM = 0xAA66CCFF;
    private static final int PRIMARY_FAINT = 0x3366CCFF;
    private static final int PRIMARY_SOFT = 0x6666CCFF;
    private static final int SECONDARY = 0xFF7CEAD9;
    private static final int TEXT = 0xFFE8FBFF;
    private static final int MUTED = 0xB8BFEFFF;
    private static final int MINIMIZED_WIDTH = 34;
    private static final int MINIMIZED_HEIGHT = 18;
    private static final int TITLE_HEIGHT = 18;
    private static final int CUT = 10;
    private static final int DOCK_MARGIN = 6;
    private static final int DOCK_GAP = 5;
    private static final int TOGGLE_WIDTH = 66;
    private static final int TOGGLE_HEIGHT = 16;

    private static final List<PanelDefinition> PANELS = List.of(
            new PanelDefinition("world_info", "hud.spectralization.panel.world", "hud.spectralization.panel.world.button", 172, 72, Anchor.TOP_LEFT, SpectralHudPanelManager::renderWorldInfo),
            new PanelDefinition("optical_net", "hud.spectralization.panel.optical", "hud.spectralization.panel.optical.button", 184, 78, Anchor.BOTTOM_LEFT, SpectralHudPanelManager::renderOpticalNet)
    );

    private static DragState dragState;

    public static void render(GuiGraphics graphics, int mouseX, int mouseY, boolean editMode) {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        renderOuterFrame(graphics, screenWidth, screenHeight);

        for (int index = 0; index < PANELS.size(); index++) {
            PanelDefinition panel = PANELS.get(index);
            SpectralHudLayout.WindowState state = state(panel, screenWidth, screenHeight);
            clampToScreen(state, screenWidth, screenHeight);
            renderPanel(graphics, panel, state, index, screenWidth, screenHeight, mouseX, mouseY, editMode);
        }

        if (editMode) {
            Component text = Component.translatable("hud.spectralization.edit_mode");
            int width = minecraft.font.width(text);
            int x = (screenWidth - width) / 2;
            graphics.drawString(minecraft.font, text, x, 10, PRIMARY, false);
            graphics.fill(x - 12, 15, x - 4, 16, PRIMARY_DIM);
            graphics.fill(x + width + 4, 15, x + width + 12, 16, PRIMARY_DIM);
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        for (int index = PANELS.size() - 1; index >= 0; index--) {
            PanelDefinition panel = PANELS.get(index);
            SpectralHudLayout.WindowState state = state(panel, screenWidth, screenHeight);
            int x = renderedX(state, index, screenWidth);
            int y = renderedY(state, index, screenHeight);
            int renderedWidth = renderedWidth(state);
            int renderedHeight = renderedHeight(state);

            if (!inside(mouseX, mouseY, x, y, renderedWidth, renderedHeight)) {
                continue;
            }

            if (state.minimized) {
                state.minimized = false;
                state.x = clamp(state.x, 0, Math.max(0, screenWidth - state.w));
                state.y = clamp(state.y, 0, Math.max(0, screenHeight - state.h));
                SpectralHudLayout.save();
                return true;
            }

            if (inside(mouseX, mouseY, state.x + state.w - 17, state.y + 4, 11, 10)) {
                state.minimized = true;
                SpectralHudLayout.save();
                return true;
            }

            if (handlePanelControlClick(panel, state, mouseX, mouseY)) {
                return true;
            }

            dragState = new DragState(
                    state,
                    (int) Math.round(mouseX) - state.x,
                    (int) Math.round(mouseY) - state.y,
                    screenWidth,
                    screenHeight
            );
            return true;
        }

        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (dragState == null || button != 0) {
            return false;
        }

        SpectralHudLayout.WindowState state = dragState.state();
        state.x = clamp((int) Math.round(mouseX) - dragState.offsetX(), 0, Math.max(0, dragState.screenWidth() - state.w));
        state.y = clamp((int) Math.round(mouseY) - dragState.offsetY(), 0, Math.max(0, dragState.screenHeight() - renderedHeight(state)));
        return true;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragState == null || button != 0) {
            return false;
        }

        dragState = null;
        SpectralHudLayout.save();
        return true;
    }

    private static void renderPanel(
            GuiGraphics graphics,
            PanelDefinition panel,
            SpectralHudLayout.WindowState state,
            int index,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            boolean editMode
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        int x = renderedX(state, index, screenWidth);
        int y = renderedY(state, index, screenHeight);
        int renderedWidth = renderedWidth(state);
        int renderedHeight = renderedHeight(state);
        boolean hovered = editMode && inside(mouseX, mouseY, x, y, renderedWidth, renderedHeight);

        if (state.minimized) {
            drawFrame(graphics, x, y, renderedWidth, renderedHeight, hovered);
            Component label = Component.translatable(panel.buttonKey());
            int labelWidth = minecraft.font.width(label);
            graphics.drawString(
                    minecraft.font,
                    label,
                    x + (renderedWidth - labelWidth) / 2,
                    y + 6,
                    hovered ? TEXT : PRIMARY,
                    false
            );
            return;
        }

        graphics.fill(state.x + 8, state.y + TITLE_HEIGHT, state.x + state.w - 8, state.y + TITLE_HEIGHT + 1, PRIMARY_FAINT);
        drawFrame(graphics, state.x, state.y, renderedWidth, renderedHeight, hovered);
        graphics.drawString(minecraft.font, Component.translatable(panel.titleKey()), state.x + 10, state.y + 6, TEXT, false);
        drawMinimizeButton(graphics, state.x + state.w - 17, state.y + 4, state.minimized, hovered);

        panel.renderer().render(graphics, state.x + 10, state.y + 24, state.w - 20, state.h - 28);
    }

    private static void renderOuterFrame(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int left = 6;
        int top = 6;
        int right = screenWidth - 6;
        int bottom = screenHeight - 6;
        int arm = Math.max(58, Math.min(128, Math.min(screenWidth, screenHeight) / 4));
        int cut = 18;

        drawOuterCorner(graphics, left, top, 1, 1, arm, cut);
        drawOuterCorner(graphics, right, top, -1, 1, arm, cut);
        drawOuterCorner(graphics, left, bottom, 1, -1, arm, cut);
        drawOuterCorner(graphics, right, bottom, -1, -1, arm, cut);

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        drawHorizontal(graphics, centerX - 72, centerX - 32, top + 3, PRIMARY_SOFT);
        drawHorizontal(graphics, centerX + 32, centerX + 72, top + 3, PRIMARY_SOFT);
        drawDiagonal(graphics, centerX - 32, top + 3, centerX - 22, top + 11, PRIMARY_SOFT);
        drawDiagonal(graphics, centerX + 22, top + 11, centerX + 32, top + 3, PRIMARY_SOFT);
        drawHorizontal(graphics, centerX - 18, centerX + 18, top + 11, PRIMARY_FAINT);

        drawHorizontal(graphics, centerX - 56, centerX - 18, bottom - 4, PRIMARY_SOFT);
        drawHorizontal(graphics, centerX + 18, centerX + 56, bottom - 4, PRIMARY_SOFT);
        drawDiagonal(graphics, centerX - 18, bottom - 4, centerX - 10, bottom - 12, PRIMARY_FAINT);
        drawDiagonal(graphics, centerX + 10, bottom - 12, centerX + 18, bottom - 4, PRIMARY_FAINT);

        drawVertical(graphics, left + 3, centerY - 48, centerY - 18, PRIMARY_SOFT);
        drawVertical(graphics, left + 3, centerY + 18, centerY + 48, PRIMARY_SOFT);
        drawDiagonal(graphics, left + 3, centerY - 18, left + 11, centerY - 10, PRIMARY_FAINT);
        drawDiagonal(graphics, left + 11, centerY + 10, left + 3, centerY + 18, PRIMARY_FAINT);
        drawVertical(graphics, right - 4, centerY - 48, centerY - 18, PRIMARY_SOFT);
        drawVertical(graphics, right - 4, centerY + 18, centerY + 48, PRIMARY_SOFT);
        drawDiagonal(graphics, right - 4, centerY - 18, right - 12, centerY - 10, PRIMARY_FAINT);
        drawDiagonal(graphics, right - 12, centerY + 10, right - 4, centerY + 18, PRIMARY_FAINT);
    }

    private static void drawOuterCorner(GuiGraphics graphics, int cornerX, int cornerY, int sx, int sy, int arm, int cut) {
        int x0 = cornerX;
        int y0 = cornerY;
        int xCut = cornerX + sx * cut;
        int yCut = cornerY + sy * cut;
        int xArm = cornerX + sx * arm;
        int yArm = cornerY + sy * arm;

        drawDiagonal(graphics, x0, yCut, xCut, y0, PRIMARY_DIM);
        drawHorizontal(graphics, xCut, xArm, y0, PRIMARY_DIM);
        drawVertical(graphics, x0, yCut, yArm, PRIMARY_DIM);

        drawDiagonal(graphics, cornerX + sx * 7, cornerY + sy * (cut + 10), cornerX + sx * (cut + 10), cornerY + sy * 7, PRIMARY_SOFT);
        drawHorizontal(graphics, cornerX + sx * (cut + 14), cornerX + sx * (arm - 20), cornerY + sy * 7, PRIMARY_FAINT);
        drawVertical(graphics, cornerX + sx * 7, cornerY + sy * (cut + 14), cornerY + sy * (arm - 20), PRIMARY_FAINT);

        for (int i = 0; i < 4; i++) {
            int offset = cut + 18 + i * 13;
            drawHorizontal(graphics, cornerX, cornerX + sx * 6, cornerY + sy * offset, i % 2 == 0 ? PRIMARY_SOFT : PRIMARY_FAINT);
            drawVertical(graphics, cornerX + sx * offset, cornerY, cornerY + sy * 6, i % 2 == 0 ? PRIMARY_SOFT : PRIMARY_FAINT);
        }
    }

    private static void drawHorizontal(GuiGraphics graphics, int x1, int x2, int y, int color) {
        int min = Math.min(x1, x2);
        int max = Math.max(x1, x2);
        graphics.fill(min, y, max + 1, y + 1, color);
    }

    private static void drawVertical(GuiGraphics graphics, int x, int y1, int y2, int color) {
        int min = Math.min(y1, y2);
        int max = Math.max(y1, y2);
        graphics.fill(x, min, x + 1, max + 1, color);
    }

    private static void drawDiagonal(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int steps = Math.max(dx, dy);

        if (steps == 0) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }

        for (int step = 0; step <= steps; step++) {
            int x = x1 + (x2 - x1) * step / steps;
            int y = y1 + (y2 - y1) * step / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, boolean hovered) {
        int border = hovered ? PRIMARY : PRIMARY_DIM;
        graphics.fill(x + CUT, y, x + width - CUT, y + 1, border);
        graphics.fill(x + CUT, y + height - 1, x + width - CUT, y + height, border);
        graphics.fill(x, y + CUT, x + 1, y + height - CUT, border);
        graphics.fill(x + width - 1, y + CUT, x + width, y + height - CUT, border);
        drawCorner(graphics, x, y, 1, 1, border);
        drawCorner(graphics, x + width - CUT, y, -1, 1, border);
        drawCorner(graphics, x, y + height - CUT, 1, -1, border);
        drawCorner(graphics, x + width - CUT, y + height - CUT, -1, -1, border);
        graphics.fill(x + 8, y + 3, x + 34, y + 4, PRIMARY_FAINT);
        graphics.fill(x + width - 34, y + height - 4, x + width - 8, y + height - 3, PRIMARY_FAINT);
    }

    private static void drawCorner(GuiGraphics graphics, int x, int y, int dx, int dy, int color) {
        for (int i = 0; i < CUT; i++) {
            int px = dx > 0 ? x + i : x + CUT - i - 1;
            int py = dy > 0 ? y + CUT - i - 1 : y + i;
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void drawMinimizeButton(GuiGraphics graphics, int x, int y, boolean minimized, boolean hovered) {
        int color = hovered ? PRIMARY : PRIMARY_DIM;
        graphics.fill(x, y, x + 11, y + 10, 0x22000000);
        graphics.fill(x + 2, y + 5, x + 9, y + 6, color);

        if (minimized) {
            graphics.fill(x + 5, y + 2, x + 6, y + 9, color);
        }
    }

    private static void renderWorldInfo(GuiGraphics graphics, int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        String position = minecraft.player == null
                ? "-- / -- / --"
                : minecraft.player.getBlockX() + " / " + minecraft.player.getBlockY() + " / " + minecraft.player.getBlockZ();
        String dayTime = minecraft.level == null ? "--:--" : clock(minecraft.level.getDayTime());
        String day = minecraft.level == null ? "--" : Long.toString(minecraft.level.getDayTime() / 24000L + 1L);
        drawLine(graphics, x, y, "hud.spectralization.label.time", dayTime);
        drawLine(graphics, x, y + 13, "hud.spectralization.label.day", day);
        drawLine(graphics, x, y + 26, "hud.spectralization.label.pos", position);
    }

    private static void renderEntityScan(GuiGraphics graphics, int x, int y, int width, int height) {
        int nearby = SpectralHudEntityOverlayEvents.nearbyEntityCount();
        int highlighted = SpectralHudEntityOverlayEvents.highlightedEntityCount();
        drawLine(graphics, x, y, "hud.spectralization.label.entities", Integer.toString(nearby));
        drawLine(graphics, x, y + 13, "hud.spectralization.label.boxes", Integer.toString(highlighted));
        drawLine(graphics, x, y + 26, "hud.spectralization.label.ore_scan", Component.translatable("hud.spectralization.value.standby"));
        drawBar(graphics, x, y + 43, width, "hud.spectralization.label.scan", Math.min(1.0D, highlighted / 32.0D), SECONDARY);
    }

    private static void renderOpticalNet(GuiGraphics graphics, int x, int y, int width, int height) {
        int beams = ClientBeamPathCache.activeSegments().size();
        int spots = ClientSpotCache.activeSpots().size();
        int marked = ClientNetworkOverlayCache.positions().size();
        drawLine(graphics, x, y, "hud.spectralization.label.beams", Integer.toString(beams));
        drawLine(graphics, x, y + 13, "hud.spectralization.label.spots", Integer.toString(spots));
        drawLine(graphics, x, y + 26, "hud.spectralization.label.marks", Integer.toString(marked));
        drawBar(graphics, x, y + 43, width, "hud.spectralization.label.load", Math.min(1.0D, (beams + spots) / 96.0D), PRIMARY);
    }

    private static void renderBeamDisplay(GuiGraphics graphics, int x, int y, int width, int height) {
        drawToggle(
                graphics,
                x,
                y,
                "hud.spectralization.label.coherent",
                SpectralBeamHudSettings.coherentVisible()
        );
        drawToggle(
                graphics,
                x,
                y + 22,
                "hud.spectralization.label.stray",
                SpectralBeamHudSettings.strayVisible()
        );
        drawLine(graphics, x, y + 44, "hud.spectralization.label.width", Component.translatable("hud.spectralization.value.radius_overlay"));
    }

    private static void renderTargeting(GuiGraphics graphics, int x, int y, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        Component target = Component.translatable("hud.spectralization.value.none");

        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            target = Component.literal(hit.getBlockPos().getX() + "," + hit.getBlockPos().getY() + "," + hit.getBlockPos().getZ());
        }

        boolean hasSurface = ClientSurfaceInspectionCache.active() != null;
        drawLine(graphics, x, y, "hud.spectralization.label.target", target);
        drawLine(graphics, x, y + 13, "hud.spectralization.label.surface", Component.translatable(hasSurface ? "hud.spectralization.value.sampled" : "hud.spectralization.value.idle"));
        drawReticle(graphics, x + width - 26, y + 7);
    }

    private static boolean handlePanelControlClick(PanelDefinition panel, SpectralHudLayout.WindowState state, double mouseX, double mouseY) {
        if (!"beam_display".equals(panel.id())) {
            return false;
        }

        int localX = (int) Math.round(mouseX) - state.x - 10;
        int localY = (int) Math.round(mouseY) - state.y - 24;

        if (inside(localX, localY, 0, 0, TOGGLE_WIDTH, TOGGLE_HEIGHT)) {
            SpectralBeamHudSettings.toggleCoherentVisible();
            return true;
        }

        if (inside(localX, localY, 0, 22, TOGGLE_WIDTH, TOGGLE_HEIGHT)) {
            SpectralBeamHudSettings.toggleStrayVisible();
            return true;
        }

        return false;
    }

    private static void drawLine(GuiGraphics graphics, int x, int y, String labelKey, String value) {
        drawLine(graphics, x, y, labelKey, Component.literal(value));
    }

    private static void drawLine(GuiGraphics graphics, int x, int y, String labelKey, Component value) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, Component.translatable(labelKey), x, y, MUTED, false);
        graphics.drawString(minecraft.font, value, x + 54, y, TEXT, false);
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int width, String labelKey, double ratio, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        int barX = x + 54;
        int barWidth = Math.max(24, width - 58);
        int filled = (int) Math.round((barWidth - 2) * Math.max(0.0D, Math.min(1.0D, ratio)));
        graphics.drawString(minecraft.font, Component.translatable(labelKey), x, y - 1, MUTED, false);
        graphics.fill(barX, y, barX + barWidth, y + 1, PRIMARY_FAINT);
        graphics.fill(barX, y + 5, barX + barWidth, y + 6, PRIMARY_FAINT);
        graphics.fill(barX, y, barX + 1, y + 6, PRIMARY_FAINT);
        graphics.fill(barX + barWidth - 1, y, barX + barWidth, y + 6, PRIMARY_FAINT);
        graphics.fill(barX + 1, y + 1, barX + 1 + filled, y + 5, color);
    }

    private static void drawToggle(GuiGraphics graphics, int x, int y, String labelKey, boolean enabled) {
        Minecraft minecraft = Minecraft.getInstance();
        int color = enabled ? SECONDARY : PRIMARY_FAINT;
        int textColor = enabled ? TEXT : MUTED;
        graphics.fill(x, y, x + TOGGLE_WIDTH, y + 1, color);
        graphics.fill(x, y + TOGGLE_HEIGHT - 1, x + TOGGLE_WIDTH, y + TOGGLE_HEIGHT, color);
        graphics.fill(x, y, x + 1, y + TOGGLE_HEIGHT, color);
        graphics.fill(x + TOGGLE_WIDTH - 1, y, x + TOGGLE_WIDTH, y + TOGGLE_HEIGHT, color);
        graphics.drawString(minecraft.font, Component.translatable(labelKey), x + 5, y + 5, textColor, false);
        graphics.drawString(
                minecraft.font,
                Component.translatable(enabled ? "hud.spectralization.value.on" : "hud.spectralization.value.off"),
                x + TOGGLE_WIDTH + 10,
                y + 5,
                textColor,
                false
        );
    }

    private static String clock(long dayTime) {
        long time = dayTime % 24000L;
        long hour = (time / 1000L + 6L) % 24L;
        long minute = (time % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hour, minute);
    }

    private static void drawReticle(GuiGraphics graphics, int centerX, int centerY) {
        graphics.fill(centerX - 12, centerY, centerX - 4, centerY + 1, PRIMARY_DIM);
        graphics.fill(centerX + 4, centerY, centerX + 12, centerY + 1, PRIMARY_DIM);
        graphics.fill(centerX, centerY - 12, centerX + 1, centerY - 4, PRIMARY_DIM);
        graphics.fill(centerX, centerY + 4, centerX + 1, centerY + 12, PRIMARY_DIM);
        graphics.fill(centerX - 5, centerY - 5, centerX + 6, centerY - 4, PRIMARY_FAINT);
        graphics.fill(centerX - 5, centerY + 5, centerX + 6, centerY + 6, PRIMARY_FAINT);
        graphics.fill(centerX - 5, centerY - 5, centerX - 4, centerY + 6, PRIMARY_FAINT);
        graphics.fill(centerX + 5, centerY - 5, centerX + 6, centerY + 6, PRIMARY_FAINT);
    }

    private static SpectralHudLayout.WindowState state(PanelDefinition panel, int screenWidth, int screenHeight) {
        return SpectralHudLayout.state(
                panel.id(),
                panel.anchor().defaultX(screenWidth, panel.width()),
                panel.anchor().defaultY(screenHeight, panel.height()),
                panel.width(),
                panel.height()
        );
    }

    private static void clampToScreen(SpectralHudLayout.WindowState state, int screenWidth, int screenHeight) {
        state.w = Math.max(96, Math.min(state.w, Math.max(96, screenWidth)));
        state.h = Math.max(32, Math.min(state.h, Math.max(32, screenHeight)));
        state.x = clamp(state.x, 0, Math.max(0, screenWidth - renderedWidth(state)));
        state.y = clamp(state.y, 0, Math.max(0, screenHeight - renderedHeight(state)));
    }

    private static int renderedX(SpectralHudLayout.WindowState state, int index, int screenWidth) {
        return state.minimized ? screenWidth - MINIMIZED_WIDTH - DOCK_MARGIN : state.x;
    }

    private static int renderedY(SpectralHudLayout.WindowState state, int index, int screenHeight) {
        if (!state.minimized) {
            return state.y;
        }

        int top = 28 + index * (MINIMIZED_HEIGHT + DOCK_GAP);
        return clamp(top, DOCK_MARGIN, Math.max(DOCK_MARGIN, screenHeight - MINIMIZED_HEIGHT - DOCK_MARGIN));
    }

    private static int renderedWidth(SpectralHudLayout.WindowState state) {
        return state.minimized ? MINIMIZED_WIDTH : state.w;
    }

    private static int renderedHeight(SpectralHudLayout.WindowState state) {
        return state.minimized ? MINIMIZED_HEIGHT : state.h;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Anchor {
        TOP_LEFT {
            @Override
            int defaultX(int screenWidth, int width) {
                return 18;
            }

            @Override
            int defaultY(int screenHeight, int height) {
                return 18;
            }
        },
        TOP_RIGHT {
            @Override
            int defaultX(int screenWidth, int width) {
                return Math.max(18, screenWidth - width - 18);
            }

            @Override
            int defaultY(int screenHeight, int height) {
                return 18;
            }
        },
        BOTTOM_LEFT {
            @Override
            int defaultX(int screenWidth, int width) {
                return 18;
            }

            @Override
            int defaultY(int screenHeight, int height) {
                return Math.max(18, screenHeight - height - 18);
            }
        },
        BOTTOM_RIGHT {
            @Override
            int defaultX(int screenWidth, int width) {
                return Math.max(18, screenWidth - width - 18);
            }

            @Override
            int defaultY(int screenHeight, int height) {
                return Math.max(18, screenHeight - height - 18);
            }
        },
        CENTER_RIGHT {
            @Override
            int defaultX(int screenWidth, int width) {
                return Math.max(18, screenWidth - width - 18);
            }

            @Override
            int defaultY(int screenHeight, int height) {
                return Math.max(18, (screenHeight - height) / 2);
            }
        };

        abstract int defaultX(int screenWidth, int width);

        abstract int defaultY(int screenHeight, int height);
    }

    private record PanelDefinition(String id, String titleKey, String buttonKey, int width, int height, Anchor anchor, PanelRenderer renderer) {
    }

    @FunctionalInterface
    private interface PanelRenderer {
        void render(GuiGraphics graphics, int x, int y, int width, int height);
    }

    private record DragState(SpectralHudLayout.WindowState state, int offsetX, int offsetY, int screenWidth, int screenHeight) {
    }

    private SpectralHudPanelManager() {
    }
}
