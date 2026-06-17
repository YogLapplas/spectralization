package io.github.yoglappland.spectralization.optics.metasurface;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MetamaterialDesignRules {
    public static DesignEnvelope envelope(ItemStack xBudget, ItemStack yBudget, ItemStack zBudget) {
        return new DesignEnvelope(
                axisRange(xBudget),
                axisRange(yBudget),
                axisRange(zBudget)
        );
    }

    public static boolean isDesignMaterial(ItemStack stack) {
        return materialTier(stack) >= 0;
    }

    private static AxisRange axisRange(ItemStack stack) {
        int tier = materialTier(stack);
        if (stack.isEmpty() || tier < 0) {
            return AxisRange.empty();
        }

        int center = centerFromCount(stack.getCount());
        int halfWidth = 2 + tier;
        return new AxisRange(
                MetamaterialVector.clamp(center - halfWidth),
                MetamaterialVector.clamp(center + halfWidth)
        );
    }

    private static int centerFromCount(int count) {
        int clamped = Math.max(1, Math.min(64, count));
        int offset = Math.round((clamped - 1) * (MetamaterialVector.VALUE_COUNT - 1) / 63.0F);
        return MetamaterialVector.MIN_VALUE + offset;
    }

    private static int materialTier(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }

        if (stack.is(Items.GLASS)
                || stack.is(Items.GLASS_PANE)
                || stack.is(Items.QUARTZ)
                || stack.is(Items.REDSTONE)
                || stack.is(Items.COPPER_INGOT)
                || stack.is(Items.IRON_INGOT)
                || stack.is(Spectralization.RAW_SILVER.get())
                || stack.is(Spectralization.RUTILE.get())
                || stack.is(Spectralization.CORUNDUM.get())
                || stack.is(Spectralization.FLUORITE.get())) {
            return 0;
        }

        if (stack.is(Items.GOLD_INGOT)
                || stack.is(Spectralization.SILVER_INGOT.get())
                || stack.is(Spectralization.TITANIUM_DIOXIDE_DUST.get())
                || stack.is(Spectralization.ALUMINA_DUST.get())
                || stack.is(Spectralization.YTTRIUM_OXIDE.get())) {
            return 1;
        }

        if (stack.is(Spectralization.SILVER_GLASS_ITEM.get())
                || stack.is(Spectralization.RUBY.get())
                || stack.is(Spectralization.RUTILE_BLOCK_ITEM.get())
                || stack.is(Spectralization.CORUNDUM_BLOCK_ITEM.get())
                || stack.is(Spectralization.FLUORITE_BLOCK_ITEM.get())) {
            return 2;
        }

        if (stack.is(Spectralization.YAG_CRYSTAL.get())) {
            return 3;
        }

        return -1;
    }

    public record AxisRange(int min, int max) {
        public static AxisRange empty() {
            return new AxisRange(1, 0);
        }

        public boolean complete() {
            return min <= max;
        }

        public boolean contains(int value) {
            return complete() && value >= min && value <= max;
        }

        public int random(RandomSource random) {
            if (!complete()) {
                return 0;
            }

            return min + random.nextInt(max - min + 1);
        }
    }

    public record DesignEnvelope(AxisRange x, AxisRange y, AxisRange z) {
        public boolean complete() {
            return x.complete() && y.complete() && z.complete();
        }

        public boolean contains(MetamaterialVector vector) {
            return complete()
                    && x.contains(vector.x())
                    && y.contains(vector.y())
                    && z.contains(vector.z());
        }

        public MetamaterialVector random(RandomSource random) {
            return new MetamaterialVector(x.random(random), y.random(random), z.random(random));
        }
    }

    private MetamaterialDesignRules() {
    }
}
