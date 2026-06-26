package io.github.yoglappland.spectralization.event;

import io.github.yoglappland.spectralization.command.SpectralCommands;
import io.github.yoglappland.spectralization.movement.StrawberryMovementController;
import io.github.yoglappland.spectralization.optics.cache.OpticalRuntimeCaches;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class SpectralCommonEvents {
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SpectralCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        OpticalRuntimeCaches.clearAll();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        OpticalRuntimeCaches.clearAll();
        StrawberryMovementController.clearAll();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        OpticalRuntimeCaches.clear(event.getLevel());
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        SpectralBlockChangeHandlers.placed(event.getLevel(), event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        SpectralBlockChangeHandlers.broken(event.getLevel(), event.getPlayer().level(), event.getPos(), event.getState());
    }

    @SubscribeEvent
    public void onNeighborNotified(BlockEvent.NeighborNotifyEvent event) {
        SpectralBlockChangeHandlers.neighborNotified(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        SpectralPlayerInteractions.rightClickBlock(event);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        SpectralPlayerInteractions.leftClickBlock(event);
    }

    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            StrawberryMovementController.tick(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        SpectralServerTickTasks.runPostTick(event.getServer());
    }
}
