package io.github.yoglappland.spectralization.optics.surface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class SurfaceCoatingData extends SavedData implements SurfaceProfileSource {
    private static final String DATA_NAME = "spectralization_surface_coatings";
    private static final SavedData.Factory<SurfaceCoatingData> FACTORY = new SavedData.Factory<>(
            SurfaceCoatingData::new,
            SurfaceCoatingData::load,
            null
    );

    private final Map<SurfaceKey, SurfaceTreatmentKind> treatmentsByKey = new HashMap<>();

    public static Optional<SurfaceCoatingData> maybeGet(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return Optional.of(serverLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME));
        }

        return Optional.empty();
    }

    public static Optional<SurfaceProfile> profileFor(Level level, SurfaceKey key) {
        return maybeGet(level).flatMap(data -> data.profileFor(key));
    }

    public static Optional<SurfaceTreatmentKind> treatmentFor(Level level, SurfaceKey key) {
        return maybeGet(level).flatMap(data -> data.treatmentFor(key));
    }

    public static List<SurfaceCoatingEntry> entriesNear(Level level, BlockPos center, int radius, int maxEntries) {
        Optional<SurfaceCoatingData> maybeData = maybeGet(level);

        if (maybeData.isEmpty() || radius < 0 || maxEntries <= 0) {
            return List.of();
        }

        SurfaceCoatingData data = maybeData.get();
        List<SurfaceCoatingEntry> entries = new ArrayList<>();

        for (Map.Entry<SurfaceKey, SurfaceTreatmentKind> entry : data.treatmentsByKey.entrySet()) {
            BlockPos pos = entry.getKey().pos();

            if (Math.abs(pos.getX() - center.getX()) <= radius
                    && Math.abs(pos.getY() - center.getY()) <= radius
                    && Math.abs(pos.getZ() - center.getZ()) <= radius) {
                entries.add(new SurfaceCoatingEntry(entry.getKey(), entry.getValue()));

                if (entries.size() >= maxEntries) {
                    break;
                }
            }
        }

        return entries;
    }

    public static boolean set(Level level, SurfaceKey key, SurfaceTreatmentKind treatmentKind) {
        Optional<SurfaceCoatingData> maybeData = maybeGet(level);

        if (maybeData.isEmpty()) {
            return false;
        }

        SurfaceCoatingData data = maybeData.get();
        SurfaceTreatmentKind previous = data.treatmentsByKey.put(key, treatmentKind);

        if (previous == treatmentKind) {
            return false;
        }

        data.setDirty();
        return true;
    }

    public static boolean remove(Level level, SurfaceKey key) {
        Optional<SurfaceCoatingData> maybeData = maybeGet(level);

        if (maybeData.isEmpty()) {
            return false;
        }

        SurfaceCoatingData data = maybeData.get();

        if (data.treatmentsByKey.remove(key) == null) {
            return false;
        }

        data.setDirty();
        return true;
    }

    public static boolean removeAll(Level level, BlockPos pos) {
        boolean changed = false;

        for (Direction side : Direction.values()) {
            changed |= remove(level, new SurfaceKey(pos, side));
        }

        return changed;
    }

    @Override
    public Optional<SurfaceProfile> profileFor(SurfaceKey key) {
        return treatmentFor(key).map(SurfaceTreatments::profileFor);
    }

    public Optional<SurfaceTreatmentKind> treatmentFor(SurfaceKey key) {
        return Optional.ofNullable(treatmentsByKey.get(key));
    }

    public record SurfaceCoatingEntry(SurfaceKey key, SurfaceTreatmentKind treatmentKind) {
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag layers = new ListTag();

        for (Map.Entry<SurfaceKey, SurfaceTreatmentKind> entry : treatmentsByKey.entrySet()) {
            CompoundTag layer = new CompoundTag();
            layer.putLong("pos", entry.getKey().pos().asLong());
            layer.putInt("side", entry.getKey().side().ordinal());
            layer.putString("kind", entry.getValue().name());
            layers.add(layer);
        }

        tag.put("layers", layers);
        return tag;
    }

    private static SurfaceCoatingData load(CompoundTag tag, HolderLookup.Provider registries) {
        SurfaceCoatingData data = new SurfaceCoatingData();
        ListTag layers = tag.getList("layers", Tag.TAG_COMPOUND);
        Map<String, SurfaceTreatmentKind> kindsByName = kindsByName();

        for (int index = 0; index < layers.size(); index++) {
            CompoundTag layer = layers.getCompound(index);
            Direction side = sideByOrdinal(layer.getInt("side"));
            SurfaceTreatmentKind kind = kindsByName.get(layer.getString("kind"));

            if (side != null && kind != null) {
                data.treatmentsByKey.put(new SurfaceKey(BlockPos.of(layer.getLong("pos")), side), kind);
            }
        }

        return data;
    }

    private static Direction sideByOrdinal(int ordinal) {
        Direction[] values = Direction.values();

        if (ordinal < 0 || ordinal >= values.length) {
            return null;
        }

        return values[ordinal];
    }

    private static Map<String, SurfaceTreatmentKind> kindsByName() {
        Map<String, SurfaceTreatmentKind> kindsByName = new HashMap<>();

        for (SurfaceTreatmentKind kind : SurfaceTreatmentKind.values()) {
            kindsByName.put(kind.name(), kind);
        }

        return kindsByName;
    }
}
