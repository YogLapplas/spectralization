from __future__ import annotations

from dataclasses import dataclass
from itertools import combinations, combinations_with_replacement, product
from math import comb


MIN_VALUE = -7
MAX_VALUE = 8
RECIPE_SIZE = 6


@dataclass(frozen=True)
class Material:
    key: str
    name: str
    center: tuple[int, int, int]


MATERIALS = [
    # Temporary solvable profile draft. These are deliberately spread through
    # the 3D design space so six unordered materials can cover every vector.
    Material("minecraft:iron_ingot", "Iron", (1, -4, -2)),
    Material("minecraft:gold_ingot", "Gold", (2, 4, 3)),
    Material("minecraft:diamond", "Diamond", (2, 4, -1)),
    Material("minecraft:emerald", "Emerald", (-2, -2, 1)),
    Material("minecraft:lapis_lazuli", "Lapis", (3, 0, 4)),
    Material("minecraft:quartz", "Quartz", (4, 1, 1)),
    Material("minecraft:redstone", "Redstone", (-3, 2, -4)),
    Material("minecraft:copper_ingot", "Copper", (3, 4, -1)),
    Material("spectralization:silver_ingot", "Silver Ingot", (4, -1, -2)),
    Material("spectralization:rutile", "Rutile", (4, -2, 2)),
    Material("spectralization:titanium_dioxide_dust", "Titanium Dioxide Dust", (-2, -2, -1)),
    Material("spectralization:corundum", "Corundum", (3, 1, -4)),
    Material("spectralization:alumina_dust", "Alumina Dust", (-1, 2, -2)),
    Material("spectralization:fluorite", "Fluorite", (-3, -1, -1)),
    Material("spectralization:yttrium_oxide", "Yttrium Oxide", (0, -2, 1)),
    Material("spectralization:yag_crystal", "YAG Crystal", (-4, 2, -4)),
    Material("spectralization:ruby", "Ruby", (-2, 2, 2)),
]


POINTS = tuple(product(range(MIN_VALUE, MAX_VALUE + 1), repeat=3))


def clamp(value: int) -> int:
    return max(MIN_VALUE, min(MAX_VALUE, value))


def clamp_point(point: tuple[int, int, int]) -> tuple[int, int, int]:
    return tuple(clamp(v) for v in point)


def add_points(points: list[tuple[int, int, int]]) -> tuple[int, int, int]:
    return (
        sum(point[0] for point in points),
        sum(point[1] for point in points),
        sum(point[2] for point in points),
    )


def stable_recipe_hash(combo: tuple[int, ...]) -> int:
    value = 0x9E3779B9
    for index in combo:
        value ^= (index + 1) * 0x85EBCA6B
        value &= 0xFFFFFFFF
        value = ((value << 13) | (value >> 19)) & 0xFFFFFFFF
    return value


def deterministic_1x2x2_points(
        center: tuple[int, int, int],
        combo: tuple[int, ...]
) -> set[tuple[int, int, int]]:
    """The single <=1x2x2 envelope the machine would display for this recipe."""
    covered: set[tuple[int, int, int]] = set()
    hash_value = stable_recipe_hash(tuple(sorted(combo)))
    exact_axis = hash_value % 3
    hash_value >>= 2
    flexible_axes = [axis for axis in range(3) if axis != exact_axis]
    ranges: list[range] = []
    for axis, coordinate in enumerate(center):
        if axis == exact_axis:
            low = high = coordinate
        else:
            bit = flexible_axes.index(axis)
            low = coordinate + (-1 if ((hash_value >> bit) & 1) else 0)
            high = low + 1
        low = clamp(low)
        high = clamp(high)
        ranges.append(range(low, high + 1))
    covered.update(product(*ranges))
    return covered


def summarize(mode: str, allow_repeats: bool) -> None:
    index_iterable = range(len(MATERIALS))
    combo_iter = (
        combinations_with_replacement(index_iterable, RECIPE_SIZE)
        if allow_repeats
        else combinations(index_iterable, RECIPE_SIZE)
    )

    exact_covered: dict[tuple[int, int, int], tuple[int, ...]] = {}
    tight_covered: dict[tuple[int, int, int], tuple[int, ...]] = {}
    hit_count = {point: 0 for point in POINTS}

    recipe_count = 0
    clamped_center_count = 0
    for combo in combo_iter:
        recipe_count += 1
        raw_center = add_points([MATERIALS[index].center for index in combo])
        center = clamp_point(raw_center)
        if center != raw_center:
            clamped_center_count += 1
        exact_covered.setdefault(center, combo)
        for point in deterministic_1x2x2_points(center, combo):
            tight_covered.setdefault(point, combo)
            hit_count[point] += 1

    missing_exact = [point for point in POINTS if point not in exact_covered]
    missing_tight = [point for point in POINTS if point not in tight_covered]
    weakest_tight = sorted(
        ((count, point) for point, count in hit_count.items() if count > 0),
        key=lambda item: (item[0], item[1]),
    )[:12]

    print(f"== {mode} ==")
    print(f"materials: {len(MATERIALS)}")
    print(f"recipes: {recipe_count}")
    print(f"recipes with clamped center: {clamped_center_count}")
    print(f"exact centers covered: {len(exact_covered)}/{len(POINTS)}")
    print(f"missing exact centers: {len(missing_exact)}")
    print(f"covered by <=1x2x2 envelopes: {len(tight_covered)}/{len(POINTS)}")
    print(f"missing after <=1x2x2 envelopes: {len(missing_tight)}")
    if missing_tight:
        print("first missing:", ", ".join(str(point) for point in missing_tight[:20]))
    print("weakest covered points:")
    for count, point in weakest_tight:
        combo = tight_covered[point]
        names = ", ".join(MATERIALS[index].name for index in combo)
        print(f"  {point}: {count} recipe(s), e.g. {names}")
    print()


def main() -> None:
    print("Metamaterial 6-material balance probe")
    print("Target space: [-7, 8]^3 = 4096 points")
    print("Acceptable envelope: dimensions sorted <= 1x2x2")
    print()
    summarize(f"distinct materials C({len(MATERIALS)}, {RECIPE_SIZE})", allow_repeats=False)
    summarize(
        f"materials may repeat C({len(MATERIALS)}+{RECIPE_SIZE}-1, {RECIPE_SIZE})",
        allow_repeats=True,
    )
    print("Material centers:")
    for material in MATERIALS:
        print(f"  {material.key}: {material.center}")
    print()
    print(f"distinct recipe count check: {comb(len(MATERIALS), RECIPE_SIZE)}")


if __name__ == "__main__":
    main()
