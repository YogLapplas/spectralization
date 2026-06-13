package io.github.yoglappland.spectralization.optics.fiber;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.LevelAccessor;

public final class FiberShearsInteraction {
    private static final String ANCHOR_POS_KEY = "spectralization_fiber_cut_anchor_pos";
    private static final String ANCHOR_DIMENSION_KEY = "spectralization_fiber_cut_anchor_dimension";

    public static boolean isFiberNodeTarget(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FiberNodeBlock;
    }

    public static InteractionResult useOn(ServerLevel level, Player player, ItemStack stack, BlockPos targetPos) {
        if (player != null && player.isShiftKeyDown()) {
            clearAnchor(stack);
            message(player, Component.translatable("item.spectralization.fiber_shears.message.cleared"));
            return InteractionResult.SUCCESS;
        }

        Optional<FiberNode> targetNode = FiberNetworkIndex.nodeAt(level, targetPos);

        if (targetNode.isEmpty()) {
            message(player, Component.translatable("item.spectralization.fiber_shears.message.not_node"));
            return InteractionResult.FAIL;
        }

        if (targetNode.get().kind() != FiberNodeKind.INTERFACE) {
            message(player, Component.translatable("item.spectralization.fiber_shears.message.endpoint_required"));
            return InteractionResult.FAIL;
        }

        Optional<Anchor> maybeAnchor = anchor(stack);
        ResourceLocation dimension = level.dimension().location();

        if (maybeAnchor.isEmpty() || !Objects.equals(maybeAnchor.get().dimension(), dimension)) {
            setAnchor(stack, targetPos, dimension);
            message(player, Component.translatable(
                    "item.spectralization.fiber_shears.message.anchor",
                    targetPos.getX(),
                    targetPos.getY(),
                    targetPos.getZ()
            ));
            publishOverlay(player);
            return InteractionResult.SUCCESS;
        }

        Anchor anchor = maybeAnchor.get();

        if (anchor.pos().equals(targetPos)) {
            clearAnchor(stack);
            message(player, Component.translatable("item.spectralization.fiber_shears.message.cleared"));
            return InteractionResult.SUCCESS;
        }

        Optional<FiberNode> startNode = FiberNetworkIndex.nodeAt(level, anchor.pos());

        if (startNode.isEmpty() || startNode.get().kind() != FiberNodeKind.INTERFACE) {
            setAnchor(stack, targetPos, dimension);
            message(player, Component.translatable("item.spectralization.fiber_shears.message.anchor_missing"));
            return InteractionResult.SUCCESS;
        }

        Optional<FiberConnection> removed = FiberNetworkData.removeOneConnectionBetween(level, anchor.pos(), targetPos);

        if (removed.isEmpty()) {
            message(player, Component.translatable("item.spectralization.fiber_shears.message.not_connected"));
            return InteractionResult.FAIL;
        }

        clearAnchor(stack);
        damageShears(player, stack);
        message(player, Component.translatable(
                "item.spectralization.fiber_shears.message.cut",
                FiberNetworkData.connectionCountBetween(level, anchor.pos(), targetPos)
        ));
        return InteractionResult.SUCCESS;
    }

    private static Optional<Anchor> anchor(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        if (!tag.contains(ANCHOR_POS_KEY) || !tag.contains(ANCHOR_DIMENSION_KEY)) {
            return Optional.empty();
        }

        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(ANCHOR_DIMENSION_KEY));

        if (dimension == null) {
            return Optional.empty();
        }

        return Optional.of(new Anchor(BlockPos.of(tag.getLong(ANCHOR_POS_KEY)), dimension));
    }

    private static void setAnchor(ItemStack stack, BlockPos pos, ResourceLocation dimension) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong(ANCHOR_POS_KEY, pos.asLong());
            tag.putString(ANCHOR_DIMENSION_KEY, dimension.toString());
        });
    }

    private static void clearAnchor(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(ANCHOR_POS_KEY);
            tag.remove(ANCHOR_DIMENSION_KEY);
        });
    }

    private static void damageShears(Player player, ItemStack stack) {
        if (player == null || player.getAbilities().instabuild) {
            return;
        }

        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

    private static void message(Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    private static void publishOverlay(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            FiberOverlayPublisher.publishToPlayer(serverPlayer);
        }
    }

    private record Anchor(BlockPos pos, ResourceLocation dimension) {
        private Anchor {
            pos = pos.immutable();
        }
    }

    private FiberShearsInteraction() {
    }
}
