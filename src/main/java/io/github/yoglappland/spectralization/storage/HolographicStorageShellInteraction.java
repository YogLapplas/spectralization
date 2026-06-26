package io.github.yoglappland.spectralization.storage;

import io.github.yoglappland.spectralization.blockentity.HolographicStorageShellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class HolographicStorageShellInteraction {
    private static final double CENTER_HALF_SIZE = 0.30D;

    private HolographicStorageShellInteraction() {
    }

    public static void leftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }

        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        if (!(level.getBlockEntity(pos) instanceof HolographicStorageShellBlockEntity shell)
                || shell.isEmpty()
                || !isAimingAtStorageDisplay(level, player, pos)) {
            return;
        }

        if (!level.isClientSide) {
            ItemStack extracted = shell.extract(64, false);
            if (!extracted.isEmpty() && !player.addItem(extracted)) {
                player.drop(extracted, false);
            }
        }

        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
    }

    private static boolean isAimingAtStorageDisplay(Level level, Player player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(player.blockInteractionRange() + 1.0D));
        BlockHitResult hit = level.clip(new ClipContext(
                eye,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) {
            return false;
        }

        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return switch (hit.getDirection().getAxis()) {
            case X -> centered(local.y) && centered(local.z);
            case Y -> centered(local.x) && centered(local.z);
            case Z -> centered(local.x) && centered(local.y);
        };
    }

    private static boolean centered(double coordinate) {
        return Math.abs(coordinate - 0.5D) <= CENTER_HALF_SIZE;
    }
}
