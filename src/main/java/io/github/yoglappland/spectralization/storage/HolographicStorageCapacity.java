package io.github.yoglappland.spectralization.storage;

public record HolographicStorageCapacity(
        int maxTypes,
        long maxItems,
        int crystals,
        int exposedFaces,
        int channelMultiplier,
        boolean structureError
) {
    private static final int TYPES_PER_EXPOSED_FACE = 8;
    private static final long ITEMS_PER_CRYSTAL = 4096L;

    public static final HolographicStorageCapacity EMPTY = new HolographicStorageCapacity(0, 0, 0, 0, 1, true);

    public static HolographicStorageCapacity fromStructure(
            int crystals,
            int exposedFaces,
            int channelMultiplier,
            boolean structureError
    ) {
        int boundedCrystals = Math.max(0, crystals);
        int boundedFaces = Math.max(0, exposedFaces);
        int boundedMultiplier = Math.max(1, channelMultiplier);

        if (structureError) {
            return new HolographicStorageCapacity(0, 0, 0, 0, boundedMultiplier, true);
        }

        return new HolographicStorageCapacity(
                saturatedIntProduct(boundedFaces, TYPES_PER_EXPOSED_FACE, boundedMultiplier),
                saturatedLongProduct(boundedCrystals, ITEMS_PER_CRYSTAL, boundedMultiplier),
                boundedCrystals,
                boundedFaces,
                boundedMultiplier,
                false
        );
    }

    private static int saturatedIntProduct(int first, int second, int third) {
        long value = (long) first * second * third;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static long saturatedLongProduct(int first, long second, int third) {
        if (first <= 0 || second <= 0 || third <= 0) {
            return 0;
        }

        long firstProduct = first;
        if (firstProduct > Long.MAX_VALUE / second) {
            return Long.MAX_VALUE;
        }

        firstProduct *= second;
        if (firstProduct > Long.MAX_VALUE / third) {
            return Long.MAX_VALUE;
        }

        return firstProduct * third;
    }
}
