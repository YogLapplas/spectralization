package io.github.yoglappland.spectralization.client.movement;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.movement.StrawberryMovementController;
import io.github.yoglappland.spectralization.network.StrawberryMovementInputPayload;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class StrawberryMovementClientEvents {
    private static boolean wasHoldingRod;
    private static boolean wasJumpDown;

    @SubscribeEvent
    public static void movementInputUpdated(MovementInputUpdateEvent event) {
        boolean holdingRod = event.getEntity().getMainHandItem().is(Spectralization.ROD_OF_STRAWBERRY.get())
                || event.getEntity().getOffhandItem().is(Spectralization.ROD_OF_STRAWBERRY.get());
        Input input = event.getInput();
        boolean jumpDown = input.jumping;
        boolean jumpPressed = holdingRod && jumpDown && !wasJumpDown;

        if (jumpPressed) {
            StrawberryMovementController.predictJump(event.getEntity());
        }

        if (holdingRod || wasHoldingRod) {
            PacketDistributor.sendToServer(new StrawberryMovementInputPayload(
                    holdingRod,
                    jumpDown,
                    jumpPressed,
                    false,
                    input.leftImpulse,
                    input.forwardImpulse
            ));
        }

        wasHoldingRod = holdingRod;
        wasJumpDown = holdingRod && jumpDown;
    }

    private StrawberryMovementClientEvents() {
    }
}
