package io.github.yoglappland.spectralization.microlizer;

import io.github.yoglappland.spectralization.Spectralization;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class MicrolizerShapeRecipes {
    private static final List<Recipe> RECIPES = List.of(
            Recipe.solid(
                    id("coal_cube_to_diamond_block"),
                    2,
                    2,
                    2,
                    Blocks.COAL_BLOCK,
                    new ItemStack(Items.DIAMOND_BLOCK),
                    false
            )
    );

    public static Optional<Match> findMatch(ServerLevel level, BlockPos workMin, BlockPos workMax) {
        if (workMin.getX() > workMax.getX()
                || workMin.getY() > workMax.getY()
                || workMin.getZ() > workMax.getZ()) {
            return Optional.empty();
        }

        Optional<OccupiedShape> occupiedShape = scanOccupiedShape(level, workMin, workMax);
        if (occupiedShape.isEmpty()) {
            return Optional.empty();
        }

        OccupiedShape shape = occupiedShape.get();
        for (Recipe recipe : RECIPES) {
            Optional<Match> match = recipe.match(level, shape);
            if (match.isPresent()) {
                return match;
            }
        }

        return Optional.empty();
    }

    private static Optional<OccupiedShape> scanOccupiedShape(ServerLevel level, BlockPos workMin, BlockPos workMax) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(workMin, workMax)) {
            if (level.getBlockState(pos).isAir()) {
                continue;
            }

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        if (maxX == Integer.MIN_VALUE) {
            return Optional.empty();
        }

        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        return Optional.of(new OccupiedShape(
                min,
                max,
                maxX - minX + 1,
                maxY - minY + 1,
                maxZ - minZ + 1
        ));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, path);
    }

    public record Match(ResourceLocation id, ItemStack result, int yRotationQuarterTurns, BlockPos min, BlockPos max) {
        public Match {
            result = result.copy();
            min = min.immutable();
            max = max.immutable();
        }

        public ItemStack result() {
            return result.copy();
        }
    }

    private record OccupiedShape(BlockPos min, BlockPos max, int sizeX, int sizeY, int sizeZ) {
        private OccupiedShape {
            min = min.immutable();
            max = max.immutable();
        }
    }

    private record Recipe(
            ResourceLocation id,
            int sizeX,
            int sizeY,
            int sizeZ,
            List<Block> cells,
            ItemStack result,
            boolean rotateY
    ) {
        private Recipe {
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("Microlizer shape recipe dimensions must be positive.");
            }
            if (cells.size() != sizeX * sizeY * sizeZ) {
                throw new IllegalArgumentException("Microlizer shape recipe cell count does not match dimensions.");
            }
            result = result.copy();
        }

        private static Recipe solid(
                ResourceLocation id,
                int sizeX,
                int sizeY,
                int sizeZ,
                Block block,
                ItemStack result,
                boolean rotateY
        ) {
            return new Recipe(
                    id,
                    sizeX,
                    sizeY,
                    sizeZ,
                    Collections.nCopies(sizeX * sizeY * sizeZ, block),
                    result,
                    rotateY
            );
        }

        private Optional<Match> match(ServerLevel level, OccupiedShape shape) {
            int rotationCount = rotateY ? 4 : 1;
            for (int rotation = 0; rotation < rotationCount; rotation++) {
                if (!fits(shape, rotation)) {
                    continue;
                }

                if (matches(level, shape, rotation)) {
                    return Optional.of(new Match(id, result, rotation, shape.min(), shape.max()));
                }
            }

            return Optional.empty();
        }

        private boolean fits(OccupiedShape shape, int rotation) {
            return shape.sizeX() == rotatedSizeX(rotation)
                    && shape.sizeY() == sizeY
                    && shape.sizeZ() == rotatedSizeZ(rotation);
        }

        private int rotatedSizeX(int rotation) {
            return (rotation & 1) == 0 ? sizeX : sizeZ;
        }

        private int rotatedSizeZ(int rotation) {
            return (rotation & 1) == 0 ? sizeZ : sizeX;
        }

        private boolean matches(ServerLevel level, OccupiedShape shape, int rotation) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int y = 0; y < shape.sizeY(); y++) {
                for (int z = 0; z < shape.sizeZ(); z++) {
                    for (int x = 0; x < shape.sizeX(); x++) {
                        Block expected = expectedBlock(x, y, z, rotation);
                        cursor.set(shape.min().getX() + x, shape.min().getY() + y, shape.min().getZ() + z);
                        if (!matchesCell(level.getBlockState(cursor), expected)) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }

        private Block expectedBlock(int worldX, int y, int worldZ, int rotation) {
            int recipeX;
            int recipeZ;
            switch (rotation & 3) {
                case 1 -> {
                    recipeX = worldZ;
                    recipeZ = sizeZ - 1 - worldX;
                }
                case 2 -> {
                    recipeX = sizeX - 1 - worldX;
                    recipeZ = sizeZ - 1 - worldZ;
                }
                case 3 -> {
                    recipeX = sizeX - 1 - worldZ;
                    recipeZ = worldX;
                }
                default -> {
                    recipeX = worldX;
                    recipeZ = worldZ;
                }
            }

            return cells.get((y * sizeZ + recipeZ) * sizeX + recipeX);
        }

        private boolean matchesCell(BlockState state, Block expected) {
            if (expected == Blocks.AIR) {
                return state.isAir();
            }

            return state.is(expected);
        }
    }

    private MicrolizerShapeRecipes() {
    }
}
