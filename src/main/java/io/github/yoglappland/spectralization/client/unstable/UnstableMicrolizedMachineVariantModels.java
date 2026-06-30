package io.github.yoglappland.spectralization.client.unstable;

import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class UnstableMicrolizedMachineVariantModels {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[]{
            texture("unstable_microlized_machine_inverted"),
            texture("unstable_microlized_machine_rgb_a"),
            texture("unstable_microlized_machine_rgb_b"),
            texture("unstable_microlized_machine_rgb_c")
    };
    private static final RenderType[] FACE_RENDER_TYPES = new RenderType[]{
            RenderType.entityCutoutNoCull(TEXTURES[0]),
            RenderType.entityCutoutNoCull(TEXTURES[1]),
            RenderType.entityCutoutNoCull(TEXTURES[2]),
            RenderType.entityCutoutNoCull(TEXTURES[3])
    };
    private static final ModelResourceLocation[] MODELS = new ModelResourceLocation[]{
            standalone("block/unstable_microlized_machine_inverted"),
            standalone("block/unstable_microlized_machine_rgb_a"),
            standalone("block/unstable_microlized_machine_rgb_b"),
            standalone("block/unstable_microlized_machine_rgb_c")
    };

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation model : MODELS) {
            event.register(model);
        }
    }

    public static ModelResourceLocation activeModel(float time, int seed) {
        if (!active(time, seed)) {
            return null;
        }

        return MODELS[index(time, seed)];
    }

    public static RenderType activeFaceRenderType(float time, int seed, Direction face) {
        int faceSeed = mix(seed, face.ordinal(), 0x119de1f3);
        float shiftedTime = time + Math.floorMod(faceSeed, 37);
        int slot = (int) Math.floor(shiftedTime / 20.0F);
        float local = shiftedTime - slot * 20.0F;
        int slotSeed = mix(faceSeed, slot, 0x45d9f3b);
        int durationTicks = 4 + Math.floorMod(slotSeed, 17);
        if (local >= durationTicks) {
            return null;
        }

        return FACE_RENDER_TYPES[Math.floorMod(slotSeed >>> 7, FACE_RENDER_TYPES.length)];
    }

    private static boolean active(float time, int seed) {
        float slowWindow = UnstableMicrolizedMachineVisuals.fract(time * 0.018F + ((seed >>> 4) & 4095) / 4096.0F);
        float snapWindow = UnstableMicrolizedMachineVisuals.fract(time * 0.068F + ((seed >>> 15) & 1023) / 1024.0F);
        return slowWindow < 0.180F || snapWindow < 0.075F;
    }

    private static int index(float time, int seed) {
        int window = (int) Math.floor(time / 10.0F);
        return Math.floorMod(seed ^ window * 0x45d9f3b, MODELS.length);
    }

    private static ModelResourceLocation standalone(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, path));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/block/" + name + ".png");
    }

    private static int mix(int a, int b, int salt) {
        int hash = a ^ salt;
        hash ^= b * 0x7feb352d;
        hash ^= hash >>> 16;
        hash *= 0x846ca68b;
        hash ^= hash >>> 15;
        return hash;
    }

    private UnstableMicrolizedMachineVariantModels() {
    }
}
