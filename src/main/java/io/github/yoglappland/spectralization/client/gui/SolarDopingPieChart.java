package io.github.yoglappland.spectralization.client.gui;

import io.github.yoglappland.spectralization.machine.SolarDopingRecipe;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class SolarDopingPieChart {
    private static final double FULL_TURN = Math.PI * 2.0;
    private static final int[] PALETTE = {
            0xFFE36D6D,
            0xFFF0C84A,
            0xFF56A7E8,
            0xFFB488FF,
            0xFF5FCF91,
            0xFFFF8CC8
    };

    private SolarDopingPieChart() {
    }

    public static void draw(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int radius,
            int innerRadius,
            @Nullable SolarDopingRecipe recipe,
            int fallbackColor,
            int emptyColor
    ) {
        if (recipe == null || recipe.results().isEmpty()) {
            drawEmpty(graphics, centerX, centerY, radius, innerRadius, emptyColor);
            return;
        }

        int totalWeight = recipe.totalResultWeight();
        int sliceCount = recipe.results().size();

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distanceSquared = dx * dx + dy * dy;
                if (distanceSquared > radius * radius || distanceSquared < innerRadius * innerRadius) {
                    continue;
                }

                double normalizedAngle = clockwiseAngleFromTop(dx, dy);
                int slice = sliceAt(recipe, totalWeight, normalizedAngle);
                int color = sliceCount <= 1
                        ? fallbackColor
                        : colorFor(recipe.results().get(slice), slice);
                graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1,
                        ThermalSmelterUiSkin.withAlpha(color, 210));
            }
        }

        if (sliceCount > 1) {
            drawSeparators(graphics, centerX, centerY, radius, innerRadius, recipe, totalWeight);
        }
    }

    public static List<Component> ratioTooltipLines(SolarDopingRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        int totalWeight = recipe.totalResultWeight();

        for (SolarDopingRecipe.WeightedResult result : recipe.results()) {
            ItemStack stack = result.resultStack();
            lines.add(Component.literal(percentText(result.weight() * 100.0 / totalWeight) + " ")
                    .append(stack.getHoverName()));
        }

        return List.copyOf(lines);
    }

    private static void drawEmpty(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int radius,
            int innerRadius,
            int color
    ) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distanceSquared = dx * dx + dy * dy;
                if (distanceSquared > radius * radius || distanceSquared < innerRadius * innerRadius) {
                    continue;
                }

                graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
            }
        }
    }

    private static void drawSeparators(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int radius,
            int innerRadius,
            SolarDopingRecipe recipe,
            int totalWeight
    ) {
        int cumulativeWeight = 0;
        int separatorColor = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 110);

        for (SolarDopingRecipe.WeightedResult result : recipe.results()) {
            cumulativeWeight += result.weight();
            if (cumulativeWeight >= totalWeight) {
                break;
            }

            double angle = cumulativeWeight / (double) totalWeight * FULL_TURN;
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            for (int r = innerRadius; r <= radius; r++) {
                int x = centerX + (int) Math.round(sin * r);
                int y = centerY - (int) Math.round(cos * r);
                graphics.fill(x, y, x + 1, y + 1, separatorColor);
            }
        }
    }

    private static int sliceAt(SolarDopingRecipe recipe, int totalWeight, double normalizedAngle) {
        int cumulativeWeight = 0;
        double scaled = normalizedAngle * totalWeight;

        for (int index = 0; index < recipe.results().size(); index++) {
            cumulativeWeight += recipe.results().get(index).weight();
            if (scaled < cumulativeWeight) {
                return index;
            }
        }

        return Math.max(0, recipe.results().size() - 1);
    }

    private static double clockwiseAngleFromTop(int dx, int dy) {
        double angle = Math.atan2(dx, -dy);
        if (angle < 0.0) {
            angle += FULL_TURN;
        }

        return angle / FULL_TURN;
    }

    private static int colorFor(SolarDopingRecipe.WeightedResult result, int index) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(result.resultStack().getItem());
        String path = key.getPath();

        if (path.contains("red")) {
            return 0xFFE45D52;
        }

        if (path.contains("yellow") || path.contains("ce_")) {
            return 0xFFF0C84A;
        }

        if (path.contains("blue") || path.contains("yb_")) {
            return 0xFF56A7E8;
        }

        if (path.contains("nd_")) {
            return 0xFFB488FF;
        }

        if (path.contains("er") || path.contains("erbium")) {
            return 0xFFFF8CC8;
        }

        if (path.contains("boron")) {
            return 0xFFFF70BB;
        }

        if (path.contains("phosphorus")) {
            return 0xFFD3E85B;
        }

        if (path.contains("arsenic")) {
            return 0xFF5FCF91;
        }

        return PALETTE[Math.floorMod(index, PALETTE.length)];
    }

    private static String percentText(double percent) {
        if (Math.abs(percent - Math.rint(percent)) < 0.01) {
            return String.format(java.util.Locale.ROOT, "%.0f%%", percent);
        }

        return String.format(java.util.Locale.ROOT, "%.1f%%", percent);
    }
}
