package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.movement.StrawberryMovementController;
import io.github.yoglappland.spectralization.network.StrawberryMovementInputPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class StrawberryRodItem extends Item {
    public StrawberryRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (level.isClientSide) {
            StrawberryMovementController.predictDash(player);
            PacketDistributor.sendToServer(new StrawberryMovementInputPayload(
                    true,
                    false,
                    false,
                    true,
                    0.0F,
                    0.0F
            ));
        } else if (player instanceof ServerPlayer serverPlayer) {
            StrawberryMovementController.requestDash(serverPlayer);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
