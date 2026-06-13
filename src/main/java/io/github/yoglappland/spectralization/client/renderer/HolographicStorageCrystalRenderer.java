package io.github.yoglappland.spectralization.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.HolographicStorageCrystalBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HolographicStorageCrystalRenderer implements BlockEntityRenderer<HolographicStorageCrystalBlockEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/holographic_white.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(TEXTURE, false);
    private static final Uv FULL_UV = new Uv(0.0D, 0.0D, 1.0D, 1.0D);
    private static final FaceColor SHELL_COLOR = new FaceColor(176, 220, 210, 44);
    private static final Box INNER_BOX = new Box(2.0D, 2.0D, 2.0D, 14.0D, 14.0D, 14.0D);
    private static final ShellPanel[] SHELL_PANELS = {
            new ShellPanel(new Box(1.0D, 15.0D, 1.0D, 15.0D, 16.0D, 15.0D), Direction.UP),
            new ShellPanel(new Box(1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 1.0D), Direction.NORTH),
            new ShellPanel(new Box(1.0D, 1.0D, 15.0D, 15.0D, 15.0D, 16.0D), Direction.SOUTH),
            new ShellPanel(new Box(0.0D, 1.0D, 1.0D, 1.0D, 15.0D, 15.0D), Direction.WEST),
            new ShellPanel(new Box(15.0D, 1.0D, 1.0D, 16.0D, 15.0D, 15.0D), Direction.EAST),
            new ShellPanel(new Box(1.0D, 0.0D, 1.0D, 15.0D, 1.0D, 15.0D), Direction.DOWN)
    };

    public HolographicStorageCrystalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            HolographicStorageCrystalBlockEntity blockEntity,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        Level level = blockEntity.getLevel();

        if (level == null) {
            return;
        }

        Camera camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
        BlockPos pos = blockEntity.getBlockPos();
        Vec3 cameraPosition = camera.getPosition();
        List<RenderFace> faces = new ArrayList<>(12);
        FaceColor coreColor = FaceColor.fromArgb(blockEntity.getCoreRenderColor());

        addBoxFaces(faces, INNER_BOX, pos, coreColor);
        addShellFaces(faces, pos, level);

        faces.sort(Comparator.comparingDouble((RenderFace face) -> face.center().distanceToSqr(cameraPosition)).reversed());

        VertexConsumer consumer = bufferSource.getBuffer(RENDER_TYPE);
        PoseStack.Pose pose = poseStack.last();

        for (RenderFace face : faces) {
            face.render(consumer, pose);
        }
    }

    private static void addBoxFaces(List<RenderFace> faces, Box box, BlockPos pos, FaceColor color) {
        for (Direction direction : Direction.values()) {
            faces.add(createFace(box, direction, FULL_UV, pos, color));
        }
    }

    private static void addShellFaces(List<RenderFace> faces, BlockPos pos, Level level) {
        for (ShellPanel panel : SHELL_PANELS) {
            boolean skipOutwardFace = level.getBlockState(pos.relative(panel.outwardDirection()))
                    .is(Spectralization.HOLOGRAPHIC_STORAGE_CRYSTAL.get());

            for (Direction direction : Direction.values()) {
                if (direction != panel.outwardDirection() || !skipOutwardFace) {
                    faces.add(createFace(panel.box(), direction, FULL_UV, pos, SHELL_COLOR));
                }
            }
        }
    }

    private static RenderFace createFace(Box box, Direction direction, Uv uv, BlockPos pos, FaceColor color) {
        return switch (direction) {
            case DOWN -> new RenderFace(
                    vertex(box.minX(), box.minY(), box.minZ(), uv.u0(), uv.v1()),
                    vertex(box.maxX(), box.minY(), box.minZ(), uv.u1(), uv.v1()),
                    vertex(box.maxX(), box.minY(), box.maxZ(), uv.u1(), uv.v0()),
                    vertex(box.minX(), box.minY(), box.maxZ(), uv.u0(), uv.v0()),
                    Direction.DOWN,
                    center(pos, box.centerX(), box.minY(), box.centerZ()),
                    color
            );
            case UP -> new RenderFace(
                    vertex(box.minX(), box.maxY(), box.minZ(), uv.u0(), uv.v1()),
                    vertex(box.minX(), box.maxY(), box.maxZ(), uv.u0(), uv.v0()),
                    vertex(box.maxX(), box.maxY(), box.maxZ(), uv.u1(), uv.v0()),
                    vertex(box.maxX(), box.maxY(), box.minZ(), uv.u1(), uv.v1()),
                    Direction.UP,
                    center(pos, box.centerX(), box.maxY(), box.centerZ()),
                    color
            );
            case NORTH -> new RenderFace(
                    vertex(box.minX(), box.minY(), box.minZ(), uv.u0(), uv.v1()),
                    vertex(box.minX(), box.maxY(), box.minZ(), uv.u0(), uv.v0()),
                    vertex(box.maxX(), box.maxY(), box.minZ(), uv.u1(), uv.v0()),
                    vertex(box.maxX(), box.minY(), box.minZ(), uv.u1(), uv.v1()),
                    Direction.NORTH,
                    center(pos, box.centerX(), box.centerY(), box.minZ()),
                    color
            );
            case SOUTH -> new RenderFace(
                    vertex(box.minX(), box.minY(), box.maxZ(), uv.u0(), uv.v1()),
                    vertex(box.maxX(), box.minY(), box.maxZ(), uv.u1(), uv.v1()),
                    vertex(box.maxX(), box.maxY(), box.maxZ(), uv.u1(), uv.v0()),
                    vertex(box.minX(), box.maxY(), box.maxZ(), uv.u0(), uv.v0()),
                    Direction.SOUTH,
                    center(pos, box.centerX(), box.centerY(), box.maxZ()),
                    color
            );
            case WEST -> new RenderFace(
                    vertex(box.minX(), box.minY(), box.minZ(), uv.u0(), uv.v1()),
                    vertex(box.minX(), box.minY(), box.maxZ(), uv.u1(), uv.v1()),
                    vertex(box.minX(), box.maxY(), box.maxZ(), uv.u1(), uv.v0()),
                    vertex(box.minX(), box.maxY(), box.minZ(), uv.u0(), uv.v0()),
                    Direction.WEST,
                    center(pos, box.minX(), box.centerY(), box.centerZ()),
                    color
            );
            case EAST -> new RenderFace(
                    vertex(box.maxX(), box.minY(), box.minZ(), uv.u0(), uv.v1()),
                    vertex(box.maxX(), box.maxY(), box.minZ(), uv.u0(), uv.v0()),
                    vertex(box.maxX(), box.maxY(), box.maxZ(), uv.u1(), uv.v0()),
                    vertex(box.maxX(), box.minY(), box.maxZ(), uv.u1(), uv.v1()),
                    Direction.EAST,
                    center(pos, box.maxX(), box.centerY(), box.centerZ()),
                    color
            );
        };
    }

    private static Vertex vertex(double x, double y, double z, double u, double v) {
        return new Vertex((float) (x / 16.0D), (float) (y / 16.0D), (float) (z / 16.0D), (float) u, (float) v);
    }

    private static Vec3 center(BlockPos pos, double x, double y, double z) {
        return new Vec3(pos.getX() + x / 16.0D, pos.getY() + y / 16.0D, pos.getZ() + z / 16.0D);
    }

    private record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double centerX() {
            return (minX + maxX) * 0.5D;
        }

        double centerY() {
            return (minY + maxY) * 0.5D;
        }

        double centerZ() {
            return (minZ + maxZ) * 0.5D;
        }
    }

    private record ShellPanel(Box box, Direction outwardDirection) {
    }

    private record Uv(double u0, double v0, double u1, double v1) {
    }

    private record Vertex(float x, float y, float z, float u, float v) {
    }

    private record FaceColor(int red, int green, int blue, int alpha) {
        static FaceColor fromArgb(int color) {
            return new FaceColor(
                    color >> 16 & 0xFF,
                    color >> 8 & 0xFF,
                    color & 0xFF,
                    color >>> 24 & 0xFF
            );
        }
    }

    private record RenderFace(Vertex a, Vertex b, Vertex c, Vertex d, Direction normal, Vec3 center, FaceColor color) {
        void render(VertexConsumer consumer, PoseStack.Pose pose) {
            renderVertex(consumer, pose, a);
            renderVertex(consumer, pose, b);
            renderVertex(consumer, pose, c);
            renderVertex(consumer, pose, d);
        }

        private void renderVertex(VertexConsumer consumer, PoseStack.Pose pose, Vertex vertex) {
            consumer.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                    .setColor(color.red(), color.green(), color.blue(), color.alpha())
                    .setUv(vertex.u(), vertex.v())
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(
                            pose,
                            (float) normal.getStepX(),
                            (float) normal.getStepY(),
                            (float) normal.getStepZ()
                    );
        }
    }
}
