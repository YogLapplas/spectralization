package io.github.yoglappland.spectralization.item;

import io.github.yoglappland.spectralization.optics.surface.SurfaceTreatmentKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class BrushPaintSelection {
    private static final String SELECTED_PAINT_KEY = "selected_paint";
    private static final String STORED_PAINTS_KEY = "stored_paints";
    public static final List<SurfaceTreatmentKind> SELECTABLE_TREATMENTS = List.of(
            SurfaceTreatmentKind.SILVERING,
            SurfaceTreatmentKind.GOLDING
    );

    public static Optional<SurfaceTreatmentKind> selectedTreatment(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();

        if (!tag.contains(SELECTED_PAINT_KEY)) {
            return firstAvailableTreatment(stack);
        }

        String selectedName = tag.getString(SELECTED_PAINT_KEY);

        for (SurfaceTreatmentKind kind : SELECTABLE_TREATMENTS) {
            if (kind.name().equals(selectedName) && isAvailable(stack, kind)) {
                return Optional.of(kind);
            }
        }

        return firstAvailableTreatment(stack);
    }

    public static void setSelectedTreatment(ItemStack stack, SurfaceTreatmentKind treatmentKind) {
        if (!SELECTABLE_TREATMENTS.contains(treatmentKind) || !isAvailable(stack, treatmentKind)) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(SELECTED_PAINT_KEY, treatmentKind.name()));
    }

    public static int selectedIndex(ItemStack stack) {
        return selectedTreatment(stack)
                .map(SELECTABLE_TREATMENTS::indexOf)
                .orElse(-1);
    }

    public static SurfaceTreatmentKind treatmentByIndex(int index) {
        if (index < 0 || index >= SELECTABLE_TREATMENTS.size()) {
            return SELECTABLE_TREATMENTS.getFirst();
        }

        return SELECTABLE_TREATMENTS.get(index);
    }

    public static List<SurfaceTreatmentKind> availableTreatments(ItemStack stack) {
        List<SurfaceTreatmentKind> available = new ArrayList<>();

        for (SurfaceTreatmentKind kind : SELECTABLE_TREATMENTS) {
            if (isAvailable(stack, kind)) {
                available.add(kind);
            }
        }

        return available;
    }

    public static boolean isAvailable(ItemStack stack, SurfaceTreatmentKind treatmentKind) {
        return isCreativeBrush(stack) || storedUses(stack, treatmentKind) > 0;
    }

    public static int storedUses(ItemStack stack, SurfaceTreatmentKind treatmentKind) {
        if (!SELECTABLE_TREATMENTS.contains(treatmentKind)) {
            return 0;
        }

        CompoundTag paints = storedPaints(stack);
        return Math.max(0, paints.getInt(treatmentKind.name()));
    }

    public static void addPaint(ItemStack stack, SurfaceTreatmentKind treatmentKind, int uses) {
        if (uses <= 0 || !SELECTABLE_TREATMENTS.contains(treatmentKind)) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag paints = tag.getCompound(STORED_PAINTS_KEY);
            paints.putInt(treatmentKind.name(), Math.max(0, paints.getInt(treatmentKind.name())) + uses);
            tag.put(STORED_PAINTS_KEY, paints);
            tag.putString(SELECTED_PAINT_KEY, treatmentKind.name());
        });
    }

    public static void consumeSelectedPaint(ItemStack stack) {
        if (isCreativeBrush(stack)) {
            return;
        }

        Optional<SurfaceTreatmentKind> maybeTreatment = selectedTreatment(stack);

        if (maybeTreatment.isEmpty()) {
            return;
        }

        SurfaceTreatmentKind treatmentKind = maybeTreatment.get();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag paints = tag.getCompound(STORED_PAINTS_KEY);
            int nextUses = Math.max(0, paints.getInt(treatmentKind.name()) - 1);

            if (nextUses <= 0) {
                paints.remove(treatmentKind.name());
            } else {
                paints.putInt(treatmentKind.name(), nextUses);
            }

            if (paints.isEmpty()) {
                tag.remove(STORED_PAINTS_KEY);
                tag.remove(SELECTED_PAINT_KEY);
            } else {
                tag.put(STORED_PAINTS_KEY, paints);

                if (nextUses <= 0) {
                    for (SurfaceTreatmentKind candidate : SELECTABLE_TREATMENTS) {
                        if (paints.getInt(candidate.name()) > 0) {
                            tag.putString(SELECTED_PAINT_KEY, candidate.name());
                            break;
                        }
                    }
                }
            }
        });
    }

    private static Optional<SurfaceTreatmentKind> firstAvailableTreatment(ItemStack stack) {
        for (SurfaceTreatmentKind kind : SELECTABLE_TREATMENTS) {
            if (isAvailable(stack, kind)) {
                return Optional.of(kind);
            }
        }

        return Optional.empty();
    }

    private static CompoundTag storedPaints(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return data.copyTag().getCompound(STORED_PAINTS_KEY);
    }

    private static boolean isCreativeBrush(ItemStack stack) {
        return stack.is(io.github.yoglappland.spectralization.Spectralization.CREATIVE_BRUSH.get());
    }

    private BrushPaintSelection() {
    }
}
