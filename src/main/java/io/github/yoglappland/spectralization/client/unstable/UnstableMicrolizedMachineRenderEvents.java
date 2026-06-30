package io.github.yoglappland.spectralization.client.unstable;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class UnstableMicrolizedMachineRenderEvents {
    private static final int SCAN_RADIUS = 16;
    private static final int MAX_RENDERED = 96;
    private static final double MAX_DISTANCE_SQUARED = SCAN_RADIUS * SCAN_RADIUS;

    @SubscribeEvent
    public static void renderUnstableMachines(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || level == null) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        BlockPos center = BlockPos.containing(cameraPosition);
        float time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int rendered = 0;

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        PoseStack.Pose pose = poseStack.last();

        scan:
        for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++) {
            for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
                    if (rendered >= MAX_RENDERED) {
                        break scan;
                    }

                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!nearCamera(cursor, cameraPosition)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Spectralization.UNSTABLE_MICROLIZED_MACHINE.get())) {
                        continue;
                    }

                    BlockPos pos = cursor.immutable();
                    renderVariantFaces(bufferSource, pose, pos, time);
                    rendered++;
                }
            }
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static boolean nearCamera(BlockPos pos, Vec3 cameraPosition) {
        double dx = pos.getX() + 0.5D - cameraPosition.x;
        double dy = pos.getY() + 0.5D - cameraPosition.y;
        double dz = pos.getZ() + 0.5D - cameraPosition.z;
        return dx * dx + dy * dy + dz * dz <= MAX_DISTANCE_SQUARED;
    }

    private static void renderVariantFaces(
            MultiBufferSource bufferSource,
            PoseStack.Pose pose,
            BlockPos pos,
            float time
    ) {
        int seed = UnstableMicrolizedMachineVisuals.seed(pos);
        UnstableMicrolizedMachineFaceRenderer.renderVariantFaces(
                bufferSource,
                pose,
                seed,
                time,
                pos.getX(),
                pos.getY(),
                pos.getZ()
        );
    }

    private UnstableMicrolizedMachineRenderEvents() {
    }
}
