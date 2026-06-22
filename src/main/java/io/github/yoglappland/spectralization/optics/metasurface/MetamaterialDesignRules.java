package io.github.yoglappland.spectralization.optics.metasurface;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.Arrays;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MetamaterialDesignRules {
    public static final int REQUIRED_MATERIAL_COUNT = 6;

    public static DesignEnvelope envelope(ItemStack... materials) {
        int x = 0;
        int y = 0;
        int z = 0;
        int[] profileIds = new int[materials.length];
        int materialCount = 0;

        for (ItemStack stack : materials) {
            MaterialProfile profile = profile(stack);
            if (profile == null) {
                continue;
            }

            profileIds[materialCount] = profile.id();
            materialCount++;
            x += profile.x();
            y += profile.y();
            z += profile.z();
        }

        if (materialCount < REQUIRED_MATERIAL_COUNT) {
            return DesignEnvelope.empty(materialCount);
        }

        Arrays.sort(profileIds, 0, materialCount);
        return envelopeFromCenter(
                MetamaterialVector.clamp(x),
                MetamaterialVector.clamp(y),
                MetamaterialVector.clamp(z),
                profileIds,
                materialCount
        );
    }

    public static boolean isDesignMaterial(ItemStack stack) {
        return profile(stack) != null;
    }

    private static MaterialProfile profile(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        if (stack.is(Items.IRON_INGOT)) {
            return MaterialProfile.IRON;
        }
        if (stack.is(Items.GOLD_INGOT)) {
            return MaterialProfile.GOLD;
        }
        if (stack.is(Items.DIAMOND)) {
            return MaterialProfile.DIAMOND;
        }
        if (stack.is(Items.EMERALD)) {
            return MaterialProfile.EMERALD;
        }
        if (stack.is(Items.LAPIS_LAZULI)) {
            return MaterialProfile.LAPIS;
        }
        if (stack.is(Items.QUARTZ)) {
            return MaterialProfile.QUARTZ;
        }
        if (stack.is(Items.COPPER_INGOT)) {
            return MaterialProfile.COPPER;
        }
        if (stack.is(Items.REDSTONE)) {
            return MaterialProfile.REDSTONE;
        }
        if (stack.is(Spectralization.SILVER_INGOT.get())) {
            return MaterialProfile.SILVER_INGOT;
        }
        if (stack.is(Spectralization.RUTILE.get())) {
            return MaterialProfile.RUTILE;
        }
        if (stack.is(Spectralization.TITANIUM_DIOXIDE_DUST.get())) {
            return MaterialProfile.TITANIUM_DIOXIDE_DUST;
        }
        if (stack.is(Spectralization.CORUNDUM.get())) {
            return MaterialProfile.CORUNDUM;
        }
        if (stack.is(Spectralization.ALUMINA_DUST.get())) {
            return MaterialProfile.ALUMINA_DUST;
        }
        if (stack.is(Spectralization.RUBY.get())) {
            return MaterialProfile.RUBY;
        }
        if (stack.is(Spectralization.FLUORITE.get())) {
            return MaterialProfile.FLUORITE;
        }
        if (stack.is(Spectralization.YTTRIUM_OXIDE.get())) {
            return MaterialProfile.YTTRIUM_OXIDE;
        }
        if (stack.is(Spectralization.YAG_CRYSTAL.get())) {
            return MaterialProfile.YAG_CRYSTAL;
        }

        return null;
    }

    private static DesignEnvelope envelopeFromCenter(
            int x,
            int y,
            int z,
            int[] profileIds,
            int materialCount
    ) {
        int hash = stableRecipeHash(profileIds, materialCount);
        int exactAxis = Math.floorMod(hash, 3);
        int rangeSeed = hash >>> 2;
        return new DesignEnvelope(
                rangeForAxis(x, 0, exactAxis, rangeSeed),
                rangeForAxis(y, 1, exactAxis, rangeSeed),
                rangeForAxis(z, 2, exactAxis, rangeSeed),
                materialCount
        );
    }

    private static int stableRecipeHash(int[] profileIds, int materialCount) {
        int hash = 0x9E3779B9;
        for (int index = 0; index < materialCount; index++) {
            hash ^= (profileIds[index] + 1) * 0x85EBCA6B;
            hash = Integer.rotateLeft(hash, 13);
        }
        return hash;
    }

    private static AxisRange rangeForAxis(int coordinate, int axis, int exactAxis, int rangeSeed) {
        if (axis == exactAxis) {
            return new AxisRange(coordinate, coordinate);
        }

        int flexibleBit = flexibleBit(axis, exactAxis);
        int offset = ((rangeSeed >>> flexibleBit) & 1) == 1 ? -1 : 0;
        int min = MetamaterialVector.clamp(coordinate + offset);
        int max = MetamaterialVector.clamp(coordinate + offset + 1);
        return new AxisRange(Math.min(min, max), Math.max(min, max));
    }

    private static int flexibleBit(int axis, int exactAxis) {
        int bit = 0;
        for (int candidate = 0; candidate < 3; candidate++) {
            if (candidate == exactAxis) {
                continue;
            }
            if (candidate == axis) {
                return bit;
            }
            bit++;
        }
        throw new IllegalArgumentException("Axis " + axis + " is not flexible when exact axis is " + exactAxis);
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

    public record DesignEnvelope(AxisRange x, AxisRange y, AxisRange z, int materialCount) {
        public static DesignEnvelope empty(int materialCount) {
            return new DesignEnvelope(AxisRange.empty(), AxisRange.empty(), AxisRange.empty(), materialCount);
        }

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

    private record MaterialProfile(
            int id,
            int x,
            int y,
            int z
    ) {
        private static final MaterialProfile IRON = new MaterialProfile(0, 1, -4, -2);
        private static final MaterialProfile GOLD = new MaterialProfile(1, 2, 4, 3);
        private static final MaterialProfile DIAMOND = new MaterialProfile(2, 2, 4, -1);
        private static final MaterialProfile EMERALD = new MaterialProfile(3, -2, -2, 1);
        private static final MaterialProfile LAPIS = new MaterialProfile(4, 3, 0, 4);
        private static final MaterialProfile QUARTZ = new MaterialProfile(5, 4, 1, 1);
        private static final MaterialProfile REDSTONE = new MaterialProfile(6, -3, 2, -4);
        private static final MaterialProfile COPPER = new MaterialProfile(7, 3, 4, -1);
        private static final MaterialProfile SILVER_INGOT = new MaterialProfile(8, 4, -1, -2);
        private static final MaterialProfile RUTILE = new MaterialProfile(9, 4, -2, 2);
        private static final MaterialProfile TITANIUM_DIOXIDE_DUST = new MaterialProfile(10, -2, -2, -1);
        private static final MaterialProfile CORUNDUM = new MaterialProfile(11, 3, 1, -4);
        private static final MaterialProfile ALUMINA_DUST = new MaterialProfile(12, -1, 2, -2);
        private static final MaterialProfile FLUORITE = new MaterialProfile(13, -3, -1, -1);
        private static final MaterialProfile YTTRIUM_OXIDE = new MaterialProfile(14, 0, -2, 1);
        private static final MaterialProfile YAG_CRYSTAL = new MaterialProfile(15, -4, 2, -4);
        private static final MaterialProfile RUBY = new MaterialProfile(16, -2, 2, 2);
    }

    private MetamaterialDesignRules() {
    }
}
