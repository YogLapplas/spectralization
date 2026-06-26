package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.fiber.FiberConnection;
import io.github.yoglappland.spectralization.optics.fiber.FiberMaterialProfile;
import io.github.yoglappland.spectralization.optics.fiber.FiberOverlayPublisher;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkData;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.fiber.FiberNode;
import io.github.yoglappland.spectralization.optics.fiber.FiberNodeKind;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkSnapshot;
import io.github.yoglappland.spectralization.optics.fiber.FiberRoute;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

public class OpticalFiberCoilItem extends Item {
    private static final String ANCHOR_POS_KEY = "spectralization_fiber_anchor_pos";
    private static final String ANCHOR_DIMENSION_KEY = "spectralization_fiber_anchor_dimension";

    private final boolean defaultSingleMode;

    public OpticalFiberCoilItem(Properties properties) {
        this(properties, false);
    }

    public OpticalFiberCoilItem(Properties properties, boolean defaultSingleMode) {
        super(properties);
        this.defaultSingleMode = defaultSingleMode;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos targetPos = context.getClickedPos().immutable();

        if (player != null && player.isShiftKeyDown()) {
            clearAnchor(stack);
            player.displayClientMessage(Component.translatable("item.spectralization.optical_fiber_coil.message.cleared"), true);
            return InteractionResult.SUCCESS;
        }

        Optional<FiberNode> targetNode = FiberNetworkIndex.nodeAt(level, targetPos);

        if (targetNode.isEmpty()) {
            message(player, Component.translatable("item.spectralization.optical_fiber_coil.message.not_node"));
            return InteractionResult.FAIL;
        }

        if (targetNode.get().kind() != FiberNodeKind.INTERFACE) {
            message(player, Component.translatable("item.spectralization.optical_fiber_coil.message.endpoint_required"));
            return InteractionResult.FAIL;
        }

        Optional<Anchor> maybeAnchor = anchor(stack);
        ResourceLocation dimension = level.dimension().location();
        FiberNetworkSnapshot snapshot = FiberNetworkIndex.snapshot(level);

        if (maybeAnchor.isEmpty() || !Objects.equals(maybeAnchor.get().dimension(), dimension)) {
            if (!snapshot.canUseNode(targetPos, 1)) {
                messageNodeFull(player, snapshot, targetPos);
                return InteractionResult.FAIL;
            }

            setAnchor(stack, targetPos, dimension);
            message(player, Component.translatable(
                    "item.spectralization.optical_fiber_coil.message.anchor",
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
            message(player, Component.translatable("item.spectralization.optical_fiber_coil.message.cleared"));
            return InteractionResult.SUCCESS;
        }

        if (!snapshot.canUseNode(targetPos, 1)) {
            messageNodeFull(player, snapshot, targetPos);
            return InteractionResult.FAIL;
        }

        Optional<FiberNode> startNode = FiberNetworkIndex.nodeAt(level, anchor.pos());

        if (startNode.isEmpty() || startNode.get().kind() != FiberNodeKind.INTERFACE) {
            setAnchor(stack, targetPos, dimension);
            message(player, Component.translatable("item.spectralization.optical_fiber_coil.message.anchor_missing"));
            return InteractionResult.SUCCESS;
        }

        if (!snapshot.canUseNode(anchor.pos(), 1)) {
            clearAnchor(stack);
            messageNodeFull(player, snapshot, anchor.pos());
            return InteractionResult.FAIL;
        }

        Optional<FiberRoute> maybeRoute = FiberNetworkIndex.findRoute(level, anchor.pos(), targetPos);

        if (maybeRoute.isEmpty()) {
            message(player, Component.translatable("item.spectralization.optical_fiber_coil.message.no_route"));
            return InteractionResult.FAIL;
        }

        FiberRoute route = maybeRoute.get();
        FiberMaterialProfile profile = fiberProfile(stack);
        Optional<FiberConnection> connection = FiberNetworkData.addConnection(level, route, profile);
        clearAnchor(stack);

        if (connection.isEmpty()) {
            message(player, Component.translatable("item.spectralization.optical_fiber_coil.message.failed"));
            return InteractionResult.FAIL;
        }

        message(player, Component.translatable(
                "item.spectralization.optical_fiber_coil.message.connected",
                route.materialLength(),
                route.segments().size()
        ));
        FiberOverlayPublisher.publishNow(level);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        Optional<Anchor> maybeAnchor = anchor(stack);
        FiberMaterialProfile profile = fiberProfile(stack);

        tooltipComponents.add(Component.translatable(
                "item.spectralization.optical_fiber_coil.tooltip.material",
                Component.translatable(profile.material().translationKey())
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.optical_fiber_coil.tooltip.core_diameter",
                profile.coreDiameterText()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.optical_fiber_coil.tooltip.capacity",
                profile.maxPowerText()
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.optical_fiber_coil.tooltip.loss",
                profile.portLossText()
        ).withStyle(ChatFormatting.GRAY));

        if (maybeAnchor.isEmpty()) {
            return;
        }

        Anchor anchor = maybeAnchor.get();
        tooltipComponents.add(Component.translatable(
                "item.spectralization.optical_fiber_coil.tooltip.anchor",
                anchor.pos().getX(),
                anchor.pos().getY(),
                anchor.pos().getZ()
        ).withStyle(ChatFormatting.GRAY));
    }

    private FiberMaterialProfile fiberProfile(ItemStack stack) {
        return FiberMaterialProfile.fromStack(stack, defaultSingleMode);
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

    private static void message(Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    private static void messageNodeFull(Player player, FiberNetworkSnapshot snapshot, BlockPos pos) {
        message(player, Component.translatable(
                "item.spectralization.optical_fiber_coil.message.node_full",
                snapshot.nodeUsage(pos),
                snapshot.nodeCapacity(pos)
        ));
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
}
