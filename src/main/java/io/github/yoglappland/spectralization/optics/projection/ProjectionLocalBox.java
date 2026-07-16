package io.github.yoglappland.spectralization.optics.projection;

import net.minecraft.world.phys.AABB;

/** Immutable block-local, world-axis-aligned projection geometry. */
public record ProjectionLocalBox(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
) {
    private static final double EPSILON = 1.0E-9D;
    public static final ProjectionLocalBox FULL = new ProjectionLocalBox(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    public ProjectionLocalBox {
        minX = clamp01(minX);
        minY = clamp01(minY);
        minZ = clamp01(minZ);
        maxX = clamp01(maxX);
        maxY = clamp01(maxY);
        maxZ = clamp01(maxZ);
        if (maxX - minX <= EPSILON || maxY - minY <= EPSILON || maxZ - minZ <= EPSILON) {
            throw new IllegalArgumentException("Projection local box must have positive volume");
        }
    }

    public static ProjectionLocalBox fromAabb(AABB box) {
        return new ProjectionLocalBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Projection local box coordinates must be finite");
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
