package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.block.BasicLithographyMachineBlock;
import io.github.yoglappland.spectralization.blockentity.BasicLithographyMachineBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BasicLithographyMachineRenderer implements BlockEntityRenderer<BasicLithographyMachineBlockEntity> {
    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/beam.png");
    private static final RenderType BEAM_RENDER_TYPE = RenderType.entityTranslucent(BEAM_TEXTURE);

    private static final float CENTER = 0.5F;
    private static final float TOP_EMITTER_Y = 14.75F / 16.0F;
    private static final float LENS_Y = 10.95F / 16.0F;
    private static final float PLATFORM_Y = 3.18F / 16.0F;
    private static final float FOCUS_SPOT_Y = 3.10F / 16.0F;
    private static final float MATERIAL_Y = 3.28F / 16.0F;

    private static final SlotRenderInfo[] MATERIAL_SLOTS = {
            new SlotRenderInfo(BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_0, 6.25F, 5.60F, -7.0F),
            new SlotRenderInfo(BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_1, 9.75F, 5.60F, 7.0F),
            new SlotRenderInfo(BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_2, 6.25F, 9.25F, 5.0F),
            new SlotRenderInfo(BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_3, 9.75F, 9.25F, -5.0F)
    };

    private final ItemRenderer itemRenderer;

    public BasicLithographyMachineRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            BasicLithographyMachineBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        rotateToModelFacing(blockEntity.getBlockState().getValue(BasicLithographyMachineBlock.FACING), poseStack);

        renderMaterialStacks(blockEntity, poseStack, bufferSource, packedLight, packedOverlay);

        if (blockEntity.getBlockState().getValue(BasicLithographyMachineBlock.ACTIVE)) {
            renderOpticalPath(blockEntity, partialTick, poseStack, bufferSource);
        }

        poseStack.popPose();
    }

    private void renderMaterialStacks(
            BasicLithographyMachineBlockEntity blockEntity,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        for (SlotRenderInfo slot : MATERIAL_SLOTS) {
            ItemStack stack = blockEntity.items().getStackInSlot(slot.slot());

            if (stack.isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(slot.x() / 16.0F, MATERIAL_Y, slot.z() / 16.0F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(slot.rotation()));
            poseStack.scale(0.23F, 0.23F, 0.23F);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, null, slot.slot());
            poseStack.popPose();
        }
    }

    private void renderOpticalPath(
            BasicLithographyMachineBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        Level level = blockEntity.getLevel();
        float time = level == null ? partialTick : level.getGameTime() + partialTick;
        float pulse = 0.72F + 0.28F * (float) Math.sin(time * 0.42F);
        VertexConsumer consumer = bufferSource.getBuffer(BEAM_RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();

        renderStraightBeam(consumer, pose, TOP_EMITTER_Y, LENS_Y, 0.050F, alpha(54, pulse), 76, 220, 255);
        renderStraightBeam(consumer, pose, TOP_EMITTER_Y, LENS_Y, 0.018F, alpha(168, pulse), 220, 252, 255);
        renderLensGlow(consumer, pose, alpha(70, pulse), 78, 226, 255);
        renderFocusedBeam(consumer, pose, 0.180F, 0.055F, alpha(58, pulse), 72, 216, 255);
        renderFocusedBeam(consumer, pose, 0.062F, 0.017F, alpha(152, pulse), 220, 252, 255);
        renderFocusSpot(consumer, pose, 0.130F, alpha(118, pulse), 90, 232, 255);
        renderFocusSpot(consumer, pose, 0.055F, alpha(210, pulse), 235, 255, 255);
    }

    private static void rotateToModelFacing(Direction facing, PoseStack poseStack) {
        poseStack.translate(CENTER, 0.0F, CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotation(facing)));
        poseStack.translate(-CENTER, 0.0F, -CENTER);
    }

    private static float yRotation(Direction facing) {
        return switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    private static void renderStraightBeam(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float topY,
            float bottomY,
            float radius,
            int alpha,
            int red,
            int green,
            int blue
    ) {
        addQuad(
                consumer,
                pose,
                CENTER - radius, topY, CENTER,
                CENTER - radius, bottomY, CENTER,
                CENTER + radius, bottomY, CENTER,
                CENTER + radius, topY, CENTER,
                red,
                green,
                blue,
                alpha,
                0.0F,
                0.0F,
                1.0F
        );
        addQuad(
                consumer,
                pose,
                CENTER, topY, CENTER - radius,
                CENTER, bottomY, CENTER - radius,
                CENTER, bottomY, CENTER + radius,
                CENTER, topY, CENTER + radius,
                red,
                green,
                blue,
                alpha,
                1.0F,
                0.0F,
                0.0F
        );
    }

    private static void renderFocusedBeam(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float lensRadius,
            float focusRadius,
            int alpha,
            int red,
            int green,
            int blue
    ) {
        addQuad(
                consumer,
                pose,
                CENTER - lensRadius, LENS_Y, CENTER,
                CENTER - focusRadius, PLATFORM_Y, CENTER,
                CENTER + focusRadius, PLATFORM_Y, CENTER,
                CENTER + lensRadius, LENS_Y, CENTER,
                red,
                green,
                blue,
                alpha,
                0.0F,
                0.0F,
                1.0F
        );
        addQuad(
                consumer,
                pose,
                CENTER, LENS_Y, CENTER - lensRadius,
                CENTER, PLATFORM_Y, CENTER - focusRadius,
                CENTER, PLATFORM_Y, CENTER + focusRadius,
                CENTER, LENS_Y, CENTER + lensRadius,
                red,
                green,
                blue,
                alpha,
                1.0F,
                0.0F,
                0.0F
        );
    }

    private static void renderLensGlow(VertexConsumer consumer, PoseStack.Pose pose, int alpha, int red, int green, int blue) {
        float radius = 0.205F;
        addHorizontalQuad(consumer, pose, LENS_Y + 0.002F, radius, red, green, blue, alpha);
        addHorizontalQuad(consumer, pose, LENS_Y - 0.002F, radius * 0.72F, 225, 255, 255, Math.min(255, alpha + 48));
    }

    private static void renderFocusSpot(VertexConsumer consumer, PoseStack.Pose pose, float radius, int alpha, int red, int green, int blue) {
        addHorizontalQuad(consumer, pose, FOCUS_SPOT_Y, radius, red, green, blue, alpha);
    }

    private static void addHorizontalQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float y,
            float radius,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addQuad(
                consumer,
                pose,
                CENTER - radius, y, CENTER - radius,
                CENTER - radius, y, CENTER + radius,
                CENTER + radius, y, CENTER + radius,
                CENTER + radius, y, CENTER - radius,
                red,
                green,
                blue,
                alpha,
                0.0F,
                1.0F,
                0.0F
        );
    }

    private static void addQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            int red,
            int green,
            int blue,
            int alpha,
            float normalX,
            float normalY,
            float normalZ
    ) {
        addVertex(consumer, pose, x1, y1, z1, 0.0F, 1.0F, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x2, y2, z2, 0.0F, 0.0F, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x3, y3, z3, 1.0F, 0.0F, red, green, blue, alpha, normalX, normalY, normalZ);
        addVertex(consumer, pose, x4, y4, z4, 1.0F, 1.0F, red, green, blue, alpha, normalX, normalY, normalZ);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            float normalX,
            float normalY,
            float normalZ
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static int alpha(int baseAlpha, float pulse) {
        return Math.max(0, Math.min(255, Math.round(baseAlpha * pulse)));
    }

    private record SlotRenderInfo(int slot, float x, float z, float rotation) {
    }
}
