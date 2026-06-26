package io.github.yoglappland.spectralization.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class EnchantableItem extends Item {
    private final int enchantmentValue;

    public EnchantableItem(Properties properties, int enchantmentValue) {
        super(properties);
        this.enchantmentValue = enchantmentValue;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return enchantmentValue;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }
}
