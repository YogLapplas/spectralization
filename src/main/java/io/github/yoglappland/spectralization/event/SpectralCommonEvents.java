package io.github.yoglappland.spectralization.event;

import io.github.yoglappland.spectralization.command.SpectralCommands;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
import io.github.yoglappland.spectralization.item.UnstableMicrolizedMachineItem;
import io.github.yoglappland.spectralization.movement.StrawberryMovementController;
import io.github.yoglappland.spectralization.optics.cache.OpticalRuntimeCaches;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionExecutor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
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
        SpotProjectionExecutor.shutdownAll();
        OpticalRuntimeCaches.clearAll();
        SpotProjectionExecutor.start(event.getServer());
        SpectralBlockChangeHandlers.clearPendingPistonRescans();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        SpotProjectionExecutor.shutdown(event.getServer());
        OpticalRuntimeCaches.clearAll();
        SpectralBlockChangeHandlers.clearPendingPistonRescans();
        StrawberryMovementController.clearAll();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        OpticalRuntimeCaches.clear(event.getLevel());
        SpectralBlockChangeHandlers.clearPendingPistonRescans(event.getLevel());
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        OpticalTraceCache.markProjectionChunkChanged(
                event.getLevel(), event.getChunk().getPos()
        );
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        OpticalTraceCache.markProjectionChunkChanged(
                event.getLevel(), event.getChunk().getPos()
        );
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
    public void onPistonMoved(PistonEvent.Post event) {
        SpectralBlockChangeHandlers.pistonMoved(event.getLevel(), event.getPos(), event.getDirection());
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
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (UnstableMicrolizedMachineItem.detonateOneFromInventory(player)) {
            SpectralDiagnostics.event(player.level(), SpectralDiagnostics.Subsystem.MICROLIZER, "unstable_microlized_machine_detonated")
                    .pos("player", player.blockPosition())
                    .field("damage_source", event.getSource().typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("unknown"))
                    .field("damage", event.getNewDamage())
                    .field("explosion_power", 4.0F)
                    .write();
        }
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
