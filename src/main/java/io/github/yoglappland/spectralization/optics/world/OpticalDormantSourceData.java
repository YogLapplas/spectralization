package io.github.yoglappland.spectralization.optics.world;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class OpticalDormantSourceData extends SavedData {
    private static final String DATA_NAME = "spectralization_dormant_optical_sources";
    private static final SavedData.Factory<OpticalDormantSourceData> FACTORY = new SavedData.Factory<>(
            OpticalDormantSourceData::new,
            OpticalDormantSourceData::load,
            null
    );

    private final LongSet sourcePositions = new LongOpenHashSet();

    public static Optional<OpticalDormantSourceData> maybeGet(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return Optional.of(serverLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME));
        }

        return Optional.empty();
    }

    public static void recordSource(Level level, BlockPos pos) {
        maybeGet(level).ifPresent(data -> {
            if (data.sourcePositions.add(pos.asLong())) {
                data.setDirty();
            }
        });
    }

    public static void removeSource(Level level, BlockPos pos) {
        maybeGet(level).ifPresent(data -> {
            if (data.sourcePositions.remove(pos.asLong())) {
                data.setDirty();
            }
        });
    }

    public LongSet sourcePositions() {
        return new LongOpenHashSet(sourcePositions);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag sources = new ListTag();

        for (long encodedPos : sourcePositions) {
            CompoundTag source = new CompoundTag();
            source.putLong("pos", encodedPos);
            sources.add(source);
        }

        tag.put("sources", sources);
        return tag;
    }

    private static OpticalDormantSourceData load(CompoundTag tag, HolderLookup.Provider registries) {
        OpticalDormantSourceData data = new OpticalDormantSourceData();
        ListTag sources = tag.getList("sources", Tag.TAG_COMPOUND);

        for (int index = 0; index < sources.size(); index++) {
            data.sourcePositions.add(sources.getCompound(index).getLong("pos"));
        }

        return data;
    }
}
