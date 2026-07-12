package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.command.SpotProjectionTestCommand;
import io.github.yoglappland.spectralization.command.SpotTestLoad;
import io.github.yoglappland.spectralization.command.SpotTestMode;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class SpotTestItem extends Item {
    private static final String MODE_KEY = "spectralization_spot_test_mode";
    private static final String LOAD_KEY = "spectralization_spot_test_load";

    public SpotTestItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (usedHand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        InteractionResult result = use(level, player, stack);
        return new InteractionResultHolder<>(result, stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getHand() != InteractionHand.MAIN_HAND || context.getPlayer() == null) {
            return InteractionResult.PASS;
        }
        return use(context.getLevel(), context.getPlayer(), context.getItemInHand());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag
    ) {
        SpotTestMode mode = mode(stack);
        tooltipComponents.add(Component.translatable(
                "item.spectralization.spot_test.tooltip.mode",
                Component.translatable(mode.translationKey())
        ).withStyle(ChatFormatting.AQUA));
        SpotTestLoad load = load(stack);
        tooltipComponents.add(Component.translatable(
                "item.spectralization.spot_test.tooltip.load",
                Component.translatable(load.translationKey())
        ).withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.spot_test.tooltip.use"
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.spot_test.tooltip.toggle"
        ).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.spot_test.tooltip.switch"
        ).withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable(
                "item.spectralization.spot_test.tooltip.warning"
        ).withStyle(ChatFormatting.RED));
    }

    public static SpotTestMode mode(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return SpotTestMode.byName(tag.getString(MODE_KEY));
    }

    private static void setMode(ItemStack stack, SpotTestMode mode) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putString(MODE_KEY, mode.serializedName())
        );
    }

    public static SpotTestLoad load(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return SpotTestLoad.byName(tag.getString(LOAD_KEY));
    }

    private static void setLoad(ItemStack stack, SpotTestLoad load) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putString(LOAD_KEY, load.serializedName())
        );
    }

    public static void toggleLoad(ServerPlayer serverPlayer) {
        ItemStack stack = serverPlayer.getMainHandItem();
        if (!(stack.getItem() instanceof SpotTestItem)) {
            return;
        }
        if (!serverPlayer.createCommandSourceStack().hasPermission(2)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.spectralization.spot_test.message.permission"),
                    true
            );
            return;
        }
        if (SpotProjectionTestCommand.isItemSuiteRunning(serverPlayer)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.spectralization.spot_test.message.busy"),
                    true
            );
            return;
        }

        SpotTestLoad next = load(stack).toggle();
        setLoad(stack, next);
        serverPlayer.displayClientMessage(Component.translatable(
                "item.spectralization.spot_test.message.load",
                Component.translatable(next.translationKey())
        ), true);
    }

    private static InteractionResult use(Level level, Player player, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!serverPlayer.createCommandSourceStack().hasPermission(2)) {
            serverPlayer.displayClientMessage(
                    Component.translatable("item.spectralization.spot_test.message.permission"),
                    true
            );
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown()) {
            if (SpotProjectionTestCommand.isItemSuiteRunning(serverPlayer)) {
                serverPlayer.displayClientMessage(
                        Component.translatable("item.spectralization.spot_test.message.busy"),
                        true
                );
                return InteractionResult.FAIL;
            }
            SpotTestMode next = mode(stack).next();
            setMode(stack, next);
            serverPlayer.displayClientMessage(Component.translatable(
                    "item.spectralization.spot_test.message.mode",
                    Component.translatable(next.translationKey())
            ), true);
            return InteractionResult.SUCCESS;
        }

        if (SpotProjectionTestCommand.isItemSuiteRunning(serverPlayer)) {
            serverPlayer.displayClientMessage(SpotProjectionTestCommand.itemSuiteStatus(serverPlayer), true);
            return InteractionResult.SUCCESS;
        }

        return SpotProjectionTestCommand.startItemSuite(serverPlayer, mode(stack), load(stack)) > 0
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL;
    }
}
