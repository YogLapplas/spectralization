package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class PaintBucketItem extends Item {
    public static final int MAX_USES = 10;

    private final SurfaceTreatmentKind treatmentKind;
    private final Supplier<Item> emptyBucket;

    public PaintBucketItem(
            SurfaceTreatmentKind treatmentKind,
            Supplier<Item> emptyBucket,
            Properties properties
    ) {
        super(properties);
        this.treatmentKind = Objects.requireNonNull(treatmentKind, "treatmentKind");
        this.emptyBucket = Objects.requireNonNull(emptyBucket, "emptyBucket");
    }

    public SurfaceTreatmentKind treatmentKind() {
        return treatmentKind;
    }

    public ItemStack emptyBucketStack() {
        return new ItemStack(emptyBucket.get());
    }
}
