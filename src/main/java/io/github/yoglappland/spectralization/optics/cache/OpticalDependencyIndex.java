package io.github.yoglappland.spectralization.optics.cache;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;

public final class OpticalDependencyIndex {
    private final Long2ObjectMap<IntSet> networksByDependencyPos = new Long2ObjectOpenHashMap<>();
    private final Int2ObjectMap<LongSet> dependencyPositionsByNetwork = new Int2ObjectOpenHashMap<>();
    private final IntSet dirtyNetworks = new IntOpenHashSet();

    /**
     * Replaces one network's immutable dependency snapshot.
     *
     * @return true when the reverse index changed, false when the new snapshot was set-equal
     */
    public boolean replaceDependencies(int networkId, LongSet dependencies) {
        LongSet oldDependencies = dependencyPositionsByNetwork.get(networkId);
        if (oldDependencies == dependencies
                || (oldDependencies != null && oldDependencies.equals(dependencies))) {
            dirtyNetworks.remove(networkId);
            return false;
        }
        removeNetwork(networkId);
        dependencyPositionsByNetwork.put(networkId, dependencies);

        for (long dependencyPos : dependencies) {
            networksByDependencyPos
                    .computeIfAbsent(dependencyPos, ignored -> new IntOpenHashSet())
                    .add(networkId);
        }
        return true;
    }

    public void removeNetwork(int networkId) {
        LongSet oldDependencies = dependencyPositionsByNetwork.remove(networkId);

        if (oldDependencies == null) {
            dirtyNetworks.remove(networkId);
            return;
        }

        for (long dependencyPos : oldDependencies) {
            IntSet networks = networksByDependencyPos.get(dependencyPos);

            if (networks == null) {
                continue;
            }

            networks.remove(networkId);

            if (networks.isEmpty()) {
                networksByDependencyPos.remove(dependencyPos);
            }
        }

        dirtyNetworks.remove(networkId);
    }

    public boolean markChanged(BlockPos pos) {
        return !markChangedAndGet(pos).isEmpty();
    }

    public boolean markChanged(long dependencyPos) {
        return !markChangedAndGet(dependencyPos).isEmpty();
    }

    public IntSet markChangedAndGet(BlockPos pos) {
        return markChangedAndGet(pos.asLong());
    }

    public IntSet markChangedAndGet(long dependencyPos) {
        IntSet affectedNetworks = networksByDependencyPos.get(dependencyPos);
        IntSet affectedCopy = new IntOpenHashSet();

        if (affectedNetworks == null || affectedNetworks.isEmpty()) {
            return affectedCopy;
        }

        affectedCopy.addAll(affectedNetworks);
        dirtyNetworks.addAll(affectedCopy);
        return affectedCopy;
    }

    public void markDirty(int networkId) {
        dirtyNetworks.add(networkId);
    }

    public boolean isDirty(int networkId) {
        return dirtyNetworks.contains(networkId);
    }

    public void clearDirty(int networkId) {
        dirtyNetworks.remove(networkId);
    }

    public boolean hasDependencies(int networkId) {
        return dependencyPositionsByNetwork.containsKey(networkId);
    }

    public LongSet dependenciesFor(int networkId) {
        return dependencyPositionsByNetwork.get(networkId);
    }
}
