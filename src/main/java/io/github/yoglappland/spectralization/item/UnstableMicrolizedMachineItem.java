package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class UnstableMicrolizedMachineItem extends BlockItem {
    public UnstableMicrolizedMachineItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static boolean detonateOneFromInventory(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        if (!consumeOne(player)) {
            return false;
        }

        level.explode(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                4.0F,
                Level.ExplosionInteraction.TNT
        );
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.spectralization.unstable_microlized_machine.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static boolean consumeOne(ServerPlayer player) {
        if (consumeOne(player.getInventory().items)) {
            player.getInventory().setChanged();
            return true;
        }
        if (consumeOne(player.getInventory().offhand)) {
            player.getInventory().setChanged();
            return true;
        }
        if (consumeOne(player.getInventory().armor)) {
            player.getInventory().setChanged();
            return true;
        }

        return false;
    }

    private static boolean consumeOne(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!stack.is(Spectralization.UNSTABLE_MICROLIZED_MACHINE_ITEM.get())) {
                continue;
            }

            stack.shrink(1);
            return true;
        }

        return false;
    }
}
