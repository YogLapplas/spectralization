#!/usr/bin/env python3
"""
Search FE-to-SP frontier candidates for 1D solid laser cavities.

This is a balance tool, not gameplay authority. It keeps the optical solve close
to laser_material_sweep.py, but adds an outer economy/search layer:

  FE source choices -> side resources -> pump density and seed strength
  axial cavity structure -> saturated optical solve -> output SP
  candidate set -> FE/SP Pareto frontier and target galleries

Important: this probe models the newer design direction where gain media do not
have a maximum accepted pump. Contiguous same-material domains still share pump
density, but the density is not clamped by material.max_pump.
"""

from __future__ import annotations

import argparse
import csv
import itertools
import math
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import laser_material_sweep as sweep


EPS = 1.0e-9
SIDE_LIMIT = 4
DEFAULT_TARGETS = (20.0, 100.0, 300.0, 1000.0, 3000.0, 10000.0)


@dataclass(frozen=True)
class MirrorPair:
    key: str
    left_r: float
    left_a: float
    right_r: float
    right_a: float


@dataclass(frozen=True)
class PumpTier:
    key: str
    scale_fe: float
    density_at_scale: float
    exponent: float

    def pump_for_fe(self, fe_per_source: float) -> float:
        if fe_per_source <= 0.0:
            return 0.0
        return self.density_at_scale * (fe_per_source / self.scale_fe) ** self.exponent


@dataclass(frozen=True)
class SeedTier:
    key: str
    scale_fe: float
    multiplier_at_scale: float
    exponent: float
    free_multiplier: float = 0.0

    def multiplier_for_fe(self, fe_per_source: float) -> float:
        if fe_per_source <= 0.0:
            return max(0.0, self.free_multiplier)
        return self.multiplier_at_scale * (fe_per_source / self.scale_fe) ** self.exponent


@dataclass(frozen=True)
class SourceLevel:
    key: str
    tier_key: str
    fe_per_source: float
    value: float


@dataclass(frozen=True)
class SideRecipe:
    key: str
    pump_count: int
    seed_count: int
    pump_level: SourceLevel
    seed_level: SourceLevel

    @property
    def fe_per_gain_block(self) -> float:
        return self.pump_count * self.pump_level.fe_per_source + self.seed_count * self.seed_level.fe_per_source


@dataclass(frozen=True)
class Candidate:
    epoch: str
    config_key: str
    length: int
    gain_blocks: int
    complexity: int
    mirror_key: str
    side_key: str
    slots: tuple[str, ...]
    pump_values: tuple[float, ...]
    seed_values: tuple[float, ...]
    fe_per_tick: float
    output_sp: float
    left_leak_sp: float
    max_port_power: float
    total_port_power: float
    burned_slots: tuple[int, ...]
    ok: bool
    per_line_output: tuple[tuple[str, float], ...]

    @property
    def safe(self) -> bool:
        return self.ok and not self.burned_slots and self.output_sp > 0.0 and self.fe_per_tick >= 0.0

    @property
    def energy_driven(self) -> bool:
        return self.safe and self.fe_per_tick > 0.0

    @property
    def dominant_material(self) -> str:
        counts: dict[str, int] = {}
        for key in self.slots:
            if key == "air":
                continue
            counts[key] = counts.get(key, 0) + 1
        if not counts:
            return "none"
        return max(counts.items(), key=lambda item: item[1])[0]


@dataclass(frozen=True)
class MaterialTuning:
    gain_per_pu: float | None = None
    saturation: float | None = None
    handling: float | None = None
    seed_per_exciter: float | None = None


MIRRORS_RUBY = (
    MirrorPair("open/open", 0.0, 0.0, 0.0, 0.0),
    MirrorPair("silver/open", 0.90, 0.01, 0.0, 0.0),
    MirrorPair("silver/output", 0.90, 0.01, 0.50, 0.01),
    MirrorPair("silver/silver", 0.90, 0.01, 0.90, 0.01),
)

MIRRORS_POST = (
    *MIRRORS_RUBY,
    MirrorPair("polished/output", 0.97, 0.01, 0.65, 0.01),
    MirrorPair("polished/polished", 0.97, 0.01, 0.97, 0.01),
)

PUMP_TIERS_RUBY = (
    PumpTier("magma", 100.0, 1.0, 0.72),
)

PUMP_TIERS_POST = (
    PumpTier("magma", 100.0, 1.0, 0.72),
    PumpTier("dense_magma", 900.0, 4.5, 0.82),
    PumpTier("diode_pump", 6000.0, 14.0, 0.95),
)

SEED_TIERS_RUBY = (
    SeedTier("glowstone", 1.0, 0.0, 1.0, free_multiplier=1.0),
)

SEED_TIERS_POST = (
    SeedTier("glowstone", 1.0, 0.0, 1.0, free_multiplier=1.0),
    SeedTier("basic_seed_diode", 160.0, 2.5, 0.62),
    SeedTier("tuned_seed_diode", 1400.0, 7.0, 0.78),
)

PUMP_FE_RUBY = (20.0, 40.0, 80.0, 140.0, 240.0, 420.0, 760.0)
PUMP_FE_POST = (80.0, 160.0, 320.0, 640.0, 1200.0, 2400.0, 4800.0, 9600.0)
SEED_FE_POST = (80.0, 160.0, 360.0, 900.0, 2200.0, 5200.0)

POST_MATERIALS = (
    "ce_yag",
    "nd_yag",
    "yb_yag",
    "er_yag",
    "ce_caf2",
    "nd_caf2",
    "yb_caf2",
    "er_caf2",
)

POST_MATERIAL_TUNING = {
    # Proposal-only balance values. The goal is to strengthen non-Yb routes
    # without reducing the current Yb:YAG high-end ceiling.
    "ce_yag": MaterialTuning(gain_per_pu=0.062, saturation=820.0, handling=2600.0, seed_per_exciter=28.0),
    "nd_yag": MaterialTuning(gain_per_pu=0.053, saturation=1550.0, handling=4200.0, seed_per_exciter=22.0),
    "yb_yag": MaterialTuning(gain_per_pu=0.044, saturation=2700.0, handling=6400.0, seed_per_exciter=12.0),
    "er_yag": MaterialTuning(gain_per_pu=0.046, saturation=2100.0, handling=6200.0, seed_per_exciter=15.0),
    "ce_caf2": MaterialTuning(gain_per_pu=0.050, saturation=800.0, handling=2800.0, seed_per_exciter=26.0),
    "nd_caf2": MaterialTuning(gain_per_pu=0.044, saturation=1400.0, handling=3800.0, seed_per_exciter=20.0),
    "yb_caf2": MaterialTuning(gain_per_pu=0.038, saturation=2300.0, handling=6100.0, seed_per_exciter=15.0),
    "er_caf2": MaterialTuning(gain_per_pu=0.038, saturation=1900.0, handling=5400.0, seed_per_exciter=13.0),
}

PUMP_AFFINITY = {
    "ce_yag": {"magma": 1.30, "dense_magma": 1.18, "diode_pump": 1.05},
    "nd_yag": {"magma": 1.18, "dense_magma": 1.32, "diode_pump": 1.12},
    "yb_yag": {"magma": 0.96, "dense_magma": 1.05, "diode_pump": 1.16},
    "er_yag": {"magma": 1.10, "dense_magma": 1.22, "diode_pump": 1.36},
    "ce_caf2": {"magma": 1.35, "dense_magma": 1.20, "diode_pump": 1.06},
    "nd_caf2": {"magma": 1.22, "dense_magma": 1.34, "diode_pump": 1.14},
    "yb_caf2": {"magma": 1.08, "dense_magma": 1.12, "diode_pump": 1.12},
    "er_caf2": {"magma": 1.12, "dense_magma": 1.25, "diode_pump": 1.42},
}

SEED_AFFINITY = {
    "ce_yag": {"glowstone": 1.45, "basic_seed_diode": 1.18, "tuned_seed_diode": 1.04},
    "nd_yag": {"glowstone": 1.16, "basic_seed_diode": 1.35, "tuned_seed_diode": 1.18},
    "yb_yag": {"glowstone": 0.98, "basic_seed_diode": 1.04, "tuned_seed_diode": 1.08},
    "er_yag": {"glowstone": 1.06, "basic_seed_diode": 1.22, "tuned_seed_diode": 1.45},
    "ce_caf2": {"glowstone": 1.50, "basic_seed_diode": 1.22, "tuned_seed_diode": 1.08},
    "nd_caf2": {"glowstone": 1.22, "basic_seed_diode": 1.40, "tuned_seed_diode": 1.20},
    "yb_caf2": {"glowstone": 1.10, "basic_seed_diode": 1.16, "tuned_seed_diode": 1.12},
    "er_caf2": {"glowstone": 1.08, "basic_seed_diode": 1.25, "tuned_seed_diode": 1.52},
}


def tuned_material(key: str, tuning: MaterialTuning) -> sweep.Material:
    base = sweep.MATERIALS[key]
    return sweep.Material(
        base.key,
        base.line,
        base.max_pump,
        tuning.gain_per_pu if tuning.gain_per_pu is not None else base.gain_per_pu,
        tuning.saturation if tuning.saturation is not None else base.saturation,
        tuning.handling if tuning.handling is not None else base.handling,
        tuning.seed_per_exciter if tuning.seed_per_exciter is not None else base.seed_per_exciter,
        base.coupling_half_width,
        base.response,
    )


POST_TUNED_MATERIALS = {
    key: tuned_material(key, tuning)
    for key, tuning in POST_MATERIAL_TUNING.items()
}


def material_for_key(epoch: str, key: str) -> sweep.Material:
    if epoch == "post" and key in POST_TUNED_MATERIALS:
        return POST_TUNED_MATERIALS[key]
    return sweep.MATERIALS[key]


def source_affinity(table: dict[str, dict[str, float]], material_key: str, tier_key: str) -> float:
    if tier_key == "none":
        return 1.0
    return table.get(material_key, {}).get(tier_key, 1.0)


def response_for_mirror(reflectance: float, absorption: float) -> sweep.Response:
    return sweep.Response(max(0.0, 1.0 - reflectance - absorption), reflectance, absorption)


def domain_pump_density_unclamped(slots: list[sweep.Block]) -> list[float]:
    densities = [0.0] * len(slots)
    index = 0
    while index < len(slots):
        material = slots[index].material
        end = index + 1
        while end < len(slots) and slots[end].material.key == material.key:
            end += 1

        if material.gain_medium:
            total = sum(block.pump for block in slots[index:end])
            density = max(0.0, total / (end - index))
            for slot in range(index, end):
                densities[slot] = density

        index = end
    return densities


def solve_line_custom(
        slots: list[sweep.Block],
        seed_power_by_slot: tuple[float, ...],
        frequency: str,
        mirror: MirrorPair,
) -> tuple[bool, float, float, float, float, tuple[float, ...]]:
    components = [
        sweep.Block(sweep.Material(
            "left_mirror",
            None,
            0.0,
            0.0,
            math.inf,
            math.inf,
            0.0,
            1.0,
            sweep.constant_response(response_for_mirror(mirror.left_r, mirror.left_a)),
        )),
        *slots,
        sweep.Block(sweep.Material(
            "right_mirror",
            None,
            0.0,
            0.0,
            math.inf,
            math.inf,
            0.0,
            1.0,
            sweep.constant_response(response_for_mirror(mirror.right_r, mirror.right_a)),
        )),
        sweep.Block(sweep.SENSOR),
    ]
    slot_offset = 1
    node_count = len(components) * 2
    lhs = lambda i: i * 2
    rhs_node = lambda i: i * 2 + 1
    transitions: list[sweep.Transition] = []
    source = [0.0] * node_count
    densities = domain_pump_density_unclamped(slots)

    def add_transition(
            source_node: int,
            target_node: int,
            gain: float,
            slope: float | None = None,
            extra: float = 0.0,
    ) -> None:
        if gain > 0.0:
            transitions.append(sweep.Transition(source_node, target_node, gain, slope, extra))

    for i, block in enumerate(components[:-1]):
        material = block.material
        slot_index = i - slot_offset
        pumped = 0 <= slot_index < len(slots) and densities[slot_index] > 0.0
        response = material.response(frequency, pumped)

        active_gain = 1.0
        extra = 0.0
        if 0 <= slot_index < len(slots) and material.gain_medium:
            coupling = sweep.emission_coupling(material, frequency)
            if coupling > 0.0 and densities[slot_index] > 0.0:
                active_gain = 1.0 + material.gain_per_pu * densities[slot_index] * coupling
                if active_gain > 1.0 + EPS:
                    extra = response.t * material.saturation * (1.0 - 1.0 / active_gain)

            if material.line == frequency:
                axial_seed = max(0.0, seed_power_by_slot[slot_index]) / 6.0
                if axial_seed > 0.0:
                    if i + 1 < len(components):
                        source[lhs(i + 1)] += axial_seed
                    if i - 1 >= 0:
                        source[rhs_node(i - 1)] += axial_seed

        transmit_gain = response.t * active_gain
        transmit_slope = response.t if extra > 0.0 else None

        if i + 1 < len(components):
            add_transition(lhs(i), lhs(i + 1), transmit_gain, transmit_slope, extra)
            add_transition(rhs_node(i), lhs(i + 1), response.r)
        if i - 1 >= 0:
            add_transition(lhs(i), rhs_node(i - 1), response.r)
            add_transition(rhs_node(i), rhs_node(i - 1), transmit_gain, transmit_slope, extra)

    powers = sweep.solve_piecewise(transitions, source)
    if powers is None:
        return False, 0.0, 0.0, 0.0, 0.0, tuple(0.0 for _slot in slots)

    absorbed = []
    for slot_index, block in enumerate(slots):
        component_index = slot_index + slot_offset
        pumped = densities[slot_index] > 0.0
        response = block.material.response(frequency, pumped)
        incoming = powers[lhs(component_index)] + powers[rhs_node(component_index)]
        absorbed.append(incoming * response.a)

    sensor_index = len(components) - 1
    left_leak = powers[rhs_node(0)]
    right_leak = powers[lhs(sensor_index)]
    return True, right_leak, left_leak, max(powers), sum(powers), tuple(absorbed)


def solve_cavity_custom(
        slots: list[sweep.Block],
        seed_power_by_slot: tuple[float, ...],
        mirror: MirrorPair,
) -> sweep.SolveResult:
    per_line = []
    total_right = 0.0
    total_left = 0.0
    max_port = 0.0
    total_port = 0.0
    absorbed_by_slot = [0.0] * len(slots)

    for frequency in sweep.axial_line_frequencies(slots):
        ok, right, left, line_max, line_total, absorbed = solve_line_custom(
            slots,
            seed_power_by_slot,
            frequency,
            mirror,
        )
        if not ok:
            return sweep.SolveResult(False, 0.0, 0.0, 0.0, 0.0, tuple(absorbed_by_slot), tuple(), tuple(per_line))
        per_line.append((frequency, right))
        total_right += right
        total_left += left
        max_port = max(max_port, line_max)
        total_port += line_total
        for index, value in enumerate(absorbed):
            absorbed_by_slot[index] += value

    burned = tuple(index for index, block in enumerate(slots) if absorbed_by_slot[index] > block.material.handling)
    return sweep.SolveResult(True, total_right, total_left, max_port, total_port, tuple(absorbed_by_slot), burned, tuple(per_line))


def source_levels_for_pump(tiers: Iterable[PumpTier], fe_values: Iterable[float]) -> tuple[SourceLevel, ...]:
    levels = [SourceLevel("pump_none", "none", 0.0, 0.0)]
    for tier in tiers:
        for fe in fe_values:
            value = tier.pump_for_fe(fe)
            levels.append(SourceLevel(f"{tier.key}@{fe:g}", tier.key, fe, value))
    return tuple(levels)


def source_levels_for_seed(tiers: Iterable[SeedTier], fe_values: Iterable[float]) -> tuple[SourceLevel, ...]:
    levels = [SourceLevel("seed_none", "none", 0.0, 0.0)]
    for tier in tiers:
        if tier.free_multiplier > 0.0:
            levels.append(SourceLevel(tier.key, tier.key, 0.0, tier.free_multiplier))
        for fe in fe_values:
            value = tier.multiplier_for_fe(fe)
            if value > 0.0:
                levels.append(SourceLevel(f"{tier.key}@{fe:g}", tier.key, fe, value))
    return tuple(levels)


def side_recipes(
        pump_levels: tuple[SourceLevel, ...],
        seed_levels: tuple[SourceLevel, ...],
        side_pairs: tuple[tuple[int, int], ...] | None,
) -> tuple[SideRecipe, ...]:
    recipes: list[SideRecipe] = []
    pairs = side_pairs or tuple(
        (pump_count, seed_count)
        for pump_count in range(SIDE_LIMIT + 1)
        for seed_count in range(SIDE_LIMIT + 1 - pump_count)
    )

    for pump_count, seed_count in pairs:
        if pump_count + seed_count > SIDE_LIMIT:
            continue
        for pump_level in pump_levels:
            if pump_count == 0 and pump_level.value > 0.0:
                continue
            if pump_count > 0 and pump_level.value <= 0.0:
                continue
            for seed_level in seed_levels:
                if seed_count == 0 and seed_level.value > 0.0:
                    continue
                if seed_count > 0 and seed_level.value <= 0.0:
                    continue
                key = f"p{pump_count}x{pump_level.key}+s{seed_count}x{seed_level.key}"
                recipes.append(SideRecipe(key, pump_count, seed_count, pump_level, seed_level))

    unique = {}
    for recipe in recipes:
        unique[recipe.key] = recipe
    return tuple(unique.values())


def binary_slot_patterns(material_key: str, max_length: int, min_length: int = 1) -> Iterable[tuple[str, ...]]:
    for length in range(min_length, max_length + 1):
        for mask in range(1, 1 << length):
            yield tuple(material_key if (mask & (1 << index)) else "air" for index in range(length))


def homogeneous_patterns(material_keys: tuple[str, ...], max_length: int, min_length: int = 2) -> Iterable[tuple[str, ...]]:
    for key in material_keys:
        yield from binary_slot_patterns(key, max_length, min_length)


def structured_homogeneous_patterns(
        material_keys: tuple[str, ...],
        max_length: int,
        min_length: int = 2,
) -> Iterable[tuple[str, ...]]:
    emitted: set[tuple[str, ...]] = set()
    for key in material_keys:
        for length in range(min_length, max_length + 1):
            patterns = [
                tuple(key for _index in range(length)),
                tuple(key if index % 2 == 0 else "air" for index in range(length)),
                tuple("air" if index % 2 == 0 else key for index in range(length)),
                tuple("air" if index == 0 else key for index in range(length)),
                tuple("air" if index == length - 1 else key for index in range(length)),
                tuple(key if index in (0, length - 1) else "air" for index in range(length)),
            ]
            for pattern in patterns:
                if any(slot != "air" for slot in pattern) and pattern not in emitted:
                    emitted.add(pattern)
                    yield pattern


def random_mixed_patterns(
        material_keys: tuple[str, ...],
        max_length: int,
        sample_count: int,
        seed: int,
) -> Iterable[tuple[str, ...]]:
    rng = random.Random(seed)
    options = (*material_keys, "air")
    emitted: set[tuple[str, ...]] = set()
    attempts = 0
    while len(emitted) < sample_count and attempts < sample_count * 20:
        attempts += 1
        length = rng.randint(2, max_length)
        slots = tuple(rng.choice(options) for _index in range(length))
        if all(key == "air" for key in slots):
            continue
        if slots in emitted:
            continue
        emitted.add(slots)
        yield slots


def dedupe_patterns(patterns: Iterable[tuple[str, ...]]) -> list[tuple[str, ...]]:
    unique: dict[tuple[str, ...], None] = {}
    for pattern in patterns:
        unique.setdefault(pattern, None)
    return list(unique)


def select_recipes(
        recipes: tuple[SideRecipe, ...],
        count: int,
        rng: random.Random,
        slots_keys: tuple[str, ...] | None = None,
) -> tuple[SideRecipe, ...]:
    if count >= len(recipes):
        return recipes

    def power_score(recipe: SideRecipe) -> float:
        gain_keys = tuple(key for key in (slots_keys or ()) if key != "air")
        if not gain_keys:
            pump_affinity = 1.0
            seed_affinity = 1.0
        else:
            pump_affinity = sum(
                source_affinity(PUMP_AFFINITY, key, recipe.pump_level.tier_key)
                for key in gain_keys
            ) / len(gain_keys)
            seed_affinity = sum(
                source_affinity(SEED_AFFINITY, key, recipe.seed_level.tier_key)
                for key in gain_keys
            ) / len(gain_keys)
        return (
            recipe.pump_count * recipe.pump_level.value * pump_affinity
            + 0.35 * recipe.seed_count * recipe.seed_level.value * seed_affinity
        )

    selected: dict[str, SideRecipe] = {}
    by_power = sorted(recipes, key=power_score)
    by_cost = sorted(recipes, key=lambda recipe: (recipe.fe_per_gain_block, -power_score(recipe)))

    quantile_count = max(4, count // 2)
    for index in range(quantile_count):
        position = round(index * (len(by_power) - 1) / max(1, quantile_count - 1))
        selected[by_power[position].key] = by_power[position]

    cost_count = max(3, count // 4)
    for index in range(cost_count):
        position = round(index * (len(by_cost) - 1) / max(1, cost_count - 1))
        selected[by_cost[position].key] = by_cost[position]

    remaining = [recipe for recipe in recipes if recipe.key not in selected]
    while len(selected) < count and remaining:
        recipe = rng.choice(remaining)
        selected[recipe.key] = recipe
        remaining = [item for item in remaining if item.key != recipe.key]

    return tuple(selected.values())


def evaluate_pattern(
        epoch: str,
        slots_keys: tuple[str, ...],
        mirror: MirrorPair,
        side: SideRecipe,
) -> Candidate:
    blocks: list[sweep.Block] = []
    seed_power_by_slot: list[float] = []
    pump_values: list[float] = []
    seed_values: list[float] = []
    gain_blocks = 0
    total_fe = 0.0

    for key in slots_keys:
        material = material_for_key(epoch, key)
        if material.gain_medium:
            gain_blocks += 1
            pump_value = (
                side.pump_count
                * side.pump_level.value
                * source_affinity(PUMP_AFFINITY, material.key, side.pump_level.tier_key)
            )
            seed_value = (
                side.seed_count
                * material.seed_per_exciter
                * side.seed_level.value
                * source_affinity(SEED_AFFINITY, material.key, side.seed_level.tier_key)
            )
            total_fe += side.fe_per_gain_block
        else:
            pump_value = 0.0
            seed_value = 0.0
        blocks.append(sweep.Block(material, pump_value, False))
        seed_power_by_slot.append(seed_value)
        pump_values.append(pump_value)
        seed_values.append(seed_value)

    result = solve_cavity_custom(blocks, tuple(seed_power_by_slot), mirror)
    complexity = len(slots_keys) + gain_blocks + side.pump_count * gain_blocks + side.seed_count * gain_blocks
    config_key = f"{mirror.key}|{side.key}|{'-'.join(slots_keys)}"
    return Candidate(
        epoch,
        config_key,
        len(slots_keys),
        gain_blocks,
        complexity,
        mirror.key,
        side.key,
        slots_keys,
        tuple(pump_values),
        tuple(seed_values),
        total_fe,
        result.sensor_right,
        result.sensor_left,
        result.max_port_power,
        result.total_port_power,
        result.burned_slots,
        result.ok,
        result.per_line_output,
    )


def primary_frontier(candidates: Iterable[Candidate]) -> list[Candidate]:
    safe = [candidate for candidate in candidates if candidate.energy_driven]
    safe.sort(key=lambda candidate: (candidate.fe_per_tick, candidate.complexity, -candidate.output_sp))
    frontier: list[Candidate] = []
    best_output = -1.0
    for candidate in safe:
        if candidate.output_sp > best_output * (1.0 + 1.0e-7) + 1.0e-9:
            frontier.append(candidate)
            best_output = candidate.output_sp
    return frontier


def archive_by_log_bucket(candidates: Iterable[Candidate], bucket_count: int = 90) -> list[Candidate]:
    safe = [candidate for candidate in candidates if candidate.safe and candidate.fe_per_tick > 0.0]
    if not safe:
        return []
    min_fe = min(candidate.fe_per_tick for candidate in safe)
    max_fe = max(candidate.fe_per_tick for candidate in safe)
    if max_fe <= min_fe:
        return primary_frontier(safe)

    low = math.log10(min_fe)
    high = math.log10(max_fe)
    buckets: dict[tuple[int, int], Candidate] = {}
    for candidate in safe:
        bucket = int((math.log10(candidate.fe_per_tick) - low) / max(EPS, high - low) * bucket_count)
        bucket = max(0, min(bucket_count - 1, bucket))
        complexity_band = min(6, candidate.complexity // 4)
        key = (bucket, complexity_band)
        current = buckets.get(key)
        if current is None or candidate.output_sp > current.output_sp:
            buckets[key] = candidate
    return primary_frontier(buckets.values())


def run_ruby(args: argparse.Namespace) -> list[Candidate]:
    pump_levels = source_levels_for_pump(PUMP_TIERS_RUBY, PUMP_FE_RUBY)
    seed_levels = source_levels_for_seed(SEED_TIERS_RUBY, ())
    recipes = side_recipes(pump_levels, seed_levels, None)
    patterns = list(binary_slot_patterns("ruby", args.ruby_max_length, 1))
    candidates: list[Candidate] = []

    total = len(patterns) * len(MIRRORS_RUBY) * len(recipes)
    print(f"ruby patterns={len(patterns)} mirrors={len(MIRRORS_RUBY)} side_recipes={len(recipes)} cases={total}")
    for index, pattern in enumerate(patterns, 1):
        if index % 64 == 0:
            print(f"  ruby pattern {index}/{len(patterns)}")
        for mirror in MIRRORS_RUBY:
            for recipe in recipes:
                candidates.append(evaluate_pattern("ruby", pattern, mirror, recipe))

    return candidates


def run_post(args: argparse.Namespace) -> list[Candidate]:
    pump_levels = source_levels_for_pump(PUMP_TIERS_POST, PUMP_FE_POST)
    seed_levels = source_levels_for_seed(SEED_TIERS_POST, SEED_FE_POST)
    curated_pairs = ((4, 0), (3, 1), (2, 2), (1, 3), (2, 1), (1, 1), (1, 2))
    recipes = side_recipes(pump_levels, seed_levels, curated_pairs)

    patterns = dedupe_patterns(itertools.chain(
        structured_homogeneous_patterns(POST_MATERIALS, args.post_max_length, 2),
        random_mixed_patterns(POST_MATERIALS, args.post_max_length, args.post_samples, args.seed),
    ))

    rng = random.Random(args.seed + 91)
    candidates: list[Candidate] = []
    print(f"post patterns={len(patterns)} mirrors={len(MIRRORS_POST)} side_recipes={len(recipes)}")

    for index, pattern in enumerate(patterns, 1):
        if index % 512 == 0:
            print(f"  post pattern {index}/{len(patterns)}")

        sample_size = min(args.post_recipe_samples, len(recipes))
        recipe_sample = select_recipes(recipes, sample_size, rng, pattern)

        for mirror in MIRRORS_POST:
            for recipe in recipe_sample:
                candidates.append(evaluate_pattern("post", pattern, mirror, recipe))

    return candidates


def write_candidates_csv(path: Path, candidates: list[Candidate], frontier: list[Candidate]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    frontier_ids = {id(candidate) for candidate in frontier}
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow([
            "frontier",
            "epoch",
            "fe_per_tick",
            "output_sp",
            "left_leak_sp",
            "max_port_power",
            "total_port_power",
            "safe",
            "ok",
            "burned_slots",
            "dominant_material",
            "length",
            "gain_blocks",
            "complexity",
            "mirror",
            "side_recipe",
            "slots",
            "pump_values",
            "seed_values",
            "per_line_output",
        ])
        for candidate in sorted(candidates, key=lambda item: (item.fe_per_tick, -item.output_sp)):
            writer.writerow([
                id(candidate) in frontier_ids,
                candidate.epoch,
                candidate.fe_per_tick,
                candidate.output_sp,
                candidate.left_leak_sp,
                candidate.max_port_power,
                candidate.total_port_power,
                candidate.safe,
                candidate.ok,
                " ".join(str(index) for index in candidate.burned_slots),
                candidate.dominant_material,
                candidate.length,
                candidate.gain_blocks,
                candidate.complexity,
                candidate.mirror_key,
                candidate.side_key,
                " ".join(candidate.slots),
                " ".join(f"{value:.6g}" for value in candidate.pump_values),
                " ".join(f"{value:.6g}" for value in candidate.seed_values),
                " ".join(f"{line}:{power:.9g}" for line, power in candidate.per_line_output),
            ])


def write_gallery(path: Path, candidates: list[Candidate], frontier: list[Candidate], targets: tuple[float, ...]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    safe = [candidate for candidate in candidates if candidate.energy_driven]

    lines.append("Lowest FE candidates by target output")
    lines.append("")
    for target in targets:
        reachable = [candidate for candidate in safe if candidate.output_sp >= target]
        if not reachable:
            lines.append(f"target {target:g} SP: not reached")
            continue
        best = min(reachable, key=lambda candidate: (candidate.fe_per_tick, candidate.complexity, -candidate.output_sp))
        lines.append(format_candidate(f"target {target:g} SP", best))

    lines.append("")
    lines.append("Primary FE/SP frontier")
    lines.append("")
    for candidate in frontier:
        lines.append(format_candidate("frontier", candidate))

    path.write_text("\n".join(lines), encoding="utf-8")


def format_candidate(label: str, candidate: Candidate) -> str:
    burned = "-" if not candidate.burned_slots else ",".join(str(index) for index in candidate.burned_slots)
    lines = [
        (
            f"{label}: FE/t={candidate.fe_per_tick:.6g} out={candidate.output_sp:.6g} "
            f"max_port={candidate.max_port_power:.6g} burn={burned}"
        ),
        f"  mirror={candidate.mirror_key} side={candidate.side_key}",
        f"  slots={' '.join(candidate.slots)}",
        f"  pump={' '.join(f'{value:.4g}' for value in candidate.pump_values)}",
        f"  seed={' '.join(f'{value:.4g}' for value in candidate.seed_values)}",
    ]
    return "\n".join(lines)


def write_frontier_png(path: Path, candidates: list[Candidate], frontier: list[Candidate], title: str) -> None:
    import matplotlib.pyplot as plt

    path.parent.mkdir(parents=True, exist_ok=True)
    safe = [candidate for candidate in candidates if candidate.safe and candidate.fe_per_tick > 0.0 and candidate.output_sp > 0.0]
    burned = [
        candidate for candidate in candidates
        if candidate.ok and candidate.burned_slots and candidate.fe_per_tick > 0.0 and candidate.output_sp > 0.0
    ]

    figure, axis = plt.subplots(figsize=(12.2, 7.0), dpi=165)
    figure.patch.set_facecolor("#0b1016")
    axis.set_facecolor("#101923")

    material_colors = {
        "ruby": "#d66767",
        "ce_yag": "#9bd66f",
        "nd_yag": "#f06d6d",
        "yb_yag": "#62c7d5",
        "er_yag": "#b18bd9",
        "ce_caf2": "#b7e98f",
        "nd_caf2": "#ff9a8a",
        "yb_caf2": "#7ab8ff",
        "er_caf2": "#d4a8ff",
        "none": "#9fb1bd",
    }

    for material, color in material_colors.items():
        rows = [candidate for candidate in safe if candidate.dominant_material == material]
        if not rows:
            continue
        axis.scatter(
            [candidate.fe_per_tick for candidate in rows],
            [candidate.output_sp for candidate in rows],
            s=9,
            color=color,
            alpha=0.23,
            edgecolors="none",
            label=material,
        )

    if burned:
        axis.scatter(
            [candidate.fe_per_tick for candidate in burned],
            [candidate.output_sp for candidate in burned],
            s=8,
            color="#1f1a24",
            alpha=0.18,
            edgecolors="none",
            label="burned",
        )

    if frontier:
        axis.plot(
            [candidate.fe_per_tick for candidate in frontier],
            [candidate.output_sp for candidate in frontier],
            color="#f8f2d8",
            linewidth=2.2,
            marker="o",
            markersize=3.2,
            label="primary frontier",
        )

    axis.set_xscale("log")
    axis.set_yscale("log")
    axis.set_title(title, color="#dbe7ee", pad=12)
    axis.set_xlabel("FE/t", color="#c7d3dc")
    axis.set_ylabel("coherent SP at right sensor", color="#c7d3dc")
    axis.tick_params(colors="#c7d3dc")
    axis.grid(True, which="both", color="#33424e", alpha=0.50)
    for spine in axis.spines.values():
        spine.set_color("#6c7a86")

    legend = axis.legend(facecolor="#101923", edgecolor="#6c7a86", fontsize=8, ncols=2)
    for text in legend.get_texts():
        text.set_color("#dbe7ee")

    figure.tight_layout()
    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)


def write_distribution_hexbin_png(
        path: Path,
        candidates: list[Candidate],
        frontier: list[Candidate],
        title: str,
) -> None:
    import matplotlib.pyplot as plt

    path.parent.mkdir(parents=True, exist_ok=True)
    safe = [
        candidate for candidate in candidates
        if candidate.safe and candidate.fe_per_tick > 0.0 and candidate.output_sp > 0.0
    ]
    if not safe:
        return

    x_values = [math.log10(candidate.fe_per_tick) for candidate in safe]
    y_values = [math.log10(candidate.output_sp) for candidate in safe]

    figure, axis = plt.subplots(figsize=(12.2, 7.0), dpi=165)
    figure.patch.set_facecolor("#0b1016")
    axis.set_facecolor("#101923")

    hexes = axis.hexbin(
        x_values,
        y_values,
        gridsize=72,
        mincnt=1,
        bins="log",
        cmap="magma",
        linewidths=0.0,
        alpha=0.92,
    )
    colorbar = figure.colorbar(hexes, ax=axis)
    colorbar.set_label("candidate count, log scale", color="#c7d3dc")
    colorbar.ax.yaxis.set_tick_params(color="#c7d3dc")
    for text in colorbar.ax.get_yticklabels():
        text.set_color("#c7d3dc")

    if frontier:
        usable = [
            candidate for candidate in frontier
            if candidate.fe_per_tick > 0.0 and candidate.output_sp > 0.0
        ]
        axis.plot(
            [math.log10(candidate.fe_per_tick) for candidate in usable],
            [math.log10(candidate.output_sp) for candidate in usable],
            color="#eaf7f2",
            linewidth=2.2,
            marker="o",
            markersize=3.0,
            label="frontier",
        )
        legend = axis.legend(facecolor="#101923", edgecolor="#6c7a86")
        for text in legend.get_texts():
            text.set_color("#dbe7ee")

    axis.set_title(title, color="#dbe7ee", pad=12)
    axis.set_xlabel("log10(FE/t)", color="#c7d3dc")
    axis.set_ylabel("log10(coherent SP)", color="#c7d3dc")
    axis.tick_params(colors="#c7d3dc")
    axis.grid(True, color="#33424e", alpha=0.45)
    for spine in axis.spines.values():
        spine.set_color("#6c7a86")

    figure.tight_layout()
    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)


def write_material_counts_png(path: Path, candidates: list[Candidate], frontier: list[Candidate], title: str) -> None:
    import matplotlib.pyplot as plt

    path.parent.mkdir(parents=True, exist_ok=True)
    materials = [
        "ce_yag",
        "nd_yag",
        "yb_yag",
        "er_yag",
        "ce_caf2",
        "nd_caf2",
        "yb_caf2",
        "er_caf2",
        "ruby",
    ]
    safe = [candidate for candidate in candidates if candidate.energy_driven]
    if not safe:
        return
    safe_counts = {material: 0 for material in materials}
    frontier_counts = {material: 0 for material in materials}
    for candidate in safe:
        material = candidate.dominant_material
        if material in safe_counts:
            safe_counts[material] += 1
    for candidate in frontier:
        material = candidate.dominant_material
        if material in frontier_counts:
            frontier_counts[material] += 1

    materials = [material for material in materials if safe_counts[material] or frontier_counts[material]]
    x_values = list(range(len(materials)))
    width = 0.38

    figure, axis = plt.subplots(figsize=(12.2, 6.2), dpi=165)
    figure.patch.set_facecolor("#0b1016")
    axis.set_facecolor("#101923")
    axis.bar(
        [x - width / 2 for x in x_values],
        [safe_counts[material] for material in materials],
        width=width,
        color="#62c7d5",
        alpha=0.72,
        label="safe candidates",
    )
    axis.bar(
        [x + width / 2 for x in x_values],
        [frontier_counts[material] for material in materials],
        width=width,
        color="#f8f2d8",
        alpha=0.92,
        label="frontier points",
    )
    axis.set_yscale("log")
    axis.set_title(title, color="#dbe7ee", pad=12)
    axis.set_ylabel("count, log scale", color="#c7d3dc")
    axis.set_xticks(x_values)
    axis.set_xticklabels(materials, rotation=25, ha="right", color="#c7d3dc")
    axis.tick_params(colors="#c7d3dc")
    axis.grid(True, axis="y", color="#33424e", alpha=0.55)
    for spine in axis.spines.values():
        spine.set_color("#6c7a86")
    legend = axis.legend(facecolor="#101923", edgecolor="#6c7a86")
    for text in legend.get_texts():
        text.set_color("#dbe7ee")

    figure.tight_layout()
    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)


def percentile(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = round((len(ordered) - 1) * q)
    index = max(0, min(len(ordered) - 1, index))
    return ordered[index]


def write_material_summary(
        path: Path,
        candidates: list[Candidate],
        frontier: list[Candidate],
        targets: tuple[float, ...],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    safe = [candidate for candidate in candidates if candidate.energy_driven]
    frontier_counts: dict[str, int] = {}
    for candidate in frontier:
        frontier_counts[candidate.dominant_material] = frontier_counts.get(candidate.dominant_material, 0) + 1

    materials = sorted({candidate.dominant_material for candidate in safe})
    lines: list[str] = [
        "Material distribution summary",
        "",
        "These numbers are search-envelope diagnostics, not gameplay authority.",
        "",
    ]

    if POST_TUNED_MATERIALS:
        lines.append("Post-ruby proposal material constants")
        lines.append("")
        for key in POST_MATERIALS:
            material = POST_TUNED_MATERIALS[key]
            lines.append(
                f"{key}: k={material.gain_per_pu:g} S={material.saturation:g} "
                f"H={material.handling:g} seed={material.seed_per_exciter:g}"
            )
        lines.append("")

    for material in materials:
        rows = [candidate for candidate in safe if candidate.dominant_material == material]
        outputs = [candidate.output_sp for candidate in rows]
        lines.append(
            f"{material}: count={len(rows)} frontier={frontier_counts.get(material, 0)} "
            f"p50={percentile(outputs, 0.50):.6g} p90={percentile(outputs, 0.90):.6g} "
            f"p99={percentile(outputs, 0.99):.6g} max={max(outputs):.6g}"
        )
        for target in targets:
            reachable = [candidate for candidate in rows if candidate.output_sp >= target]
            if not reachable:
                lines.append(f"  target {target:g} SP: not reached")
                continue
            best = min(reachable, key=lambda candidate: (candidate.fe_per_tick, candidate.complexity, -candidate.output_sp))
            lines.append(
                f"  target {target:g} SP: FE/t={best.fe_per_tick:.6g} out={best.output_sp:.6g} "
                f"mirror={best.mirror_key} side={best.side_key} slots={' '.join(best.slots)}"
            )
        lines.append("")

    path.write_text("\n".join(lines), encoding="utf-8")


def write_summary(path: Path, candidates: list[Candidate], frontier: list[Candidate]) -> None:
    safe = [candidate for candidate in candidates if candidate.safe]
    energy_driven = [candidate for candidate in candidates if candidate.energy_driven]
    burned = [candidate for candidate in candidates if candidate.ok and candidate.burned_slots]
    lines = [
        f"cases={len(candidates)}",
        f"safe={len(safe)}",
        f"burned={len(burned)}",
        f"frontier={len(frontier)}",
    ]
    if energy_driven:
        best = max(energy_driven, key=lambda candidate: candidate.output_sp)
        cheapest = min(energy_driven, key=lambda candidate: candidate.fe_per_tick)
        lines.append("")
        lines.append(format_candidate("best output", best))
        lines.append("")
        lines.append(format_candidate("cheapest positive", cheapest))
    path.write_text("\n".join(lines), encoding="utf-8")


def targets_from_text(text: str) -> tuple[float, ...]:
    values = []
    for part in text.split(","):
        part = part.strip()
        if part:
            values.append(float(part))
    return tuple(values) if values else DEFAULT_TARGETS


def run_epoch(name: str, candidates: list[Candidate], args: argparse.Namespace) -> None:
    frontier = archive_by_log_bucket(candidates, args.frontier_buckets)
    out_dir = args.out_dir
    prefix = f"{name}_fe_frontier"
    targets = targets_from_text(args.targets)

    write_candidates_csv(out_dir / f"{prefix}.csv", candidates, frontier)
    write_gallery(out_dir / f"{prefix}_gallery.txt", candidates, frontier, targets)
    write_material_summary(out_dir / f"{prefix}_material_summary.txt", candidates, frontier, targets)
    write_summary(out_dir / f"{prefix}_summary.txt", candidates, frontier)
    write_frontier_png(out_dir / f"{prefix}.png", candidates, frontier, f"{name} FE -> SP local frontier candidates")
    write_distribution_hexbin_png(
        out_dir / f"{prefix}_distribution_hexbin.png",
        candidates,
        frontier,
        f"{name} FE -> SP candidate density",
    )
    write_material_counts_png(
        out_dir / f"{prefix}_material_counts.png",
        candidates,
        frontier,
        f"{name} dominant material counts",
    )

    safe = [candidate for candidate in candidates if candidate.safe]
    best = max(safe, key=lambda candidate: candidate.output_sp) if safe else None
    print(
        f"{name}: cases={len(candidates)} safe={len(safe)} frontier={len(frontier)} "
        f"best={best.output_sp:.6g}SP at {best.fe_per_tick:.6g}FE/t" if best else f"{name}: no safe candidates"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Search FE-to-SP frontier candidates for solid laser cavities.")
    parser.add_argument("--epoch", choices=("both", "ruby", "post"), default="both")
    parser.add_argument("--ruby-max-length", type=int, default=8)
    parser.add_argument("--post-max-length", type=int, default=8)
    parser.add_argument("--post-samples", type=int, default=450)
    parser.add_argument("--post-recipe-samples", type=int, default=16)
    parser.add_argument("--frontier-buckets", type=int, default=110)
    parser.add_argument("--seed", type=int, default=17)
    parser.add_argument("--targets", default=",".join(f"{target:g}" for target in DEFAULT_TARGETS))
    parser.add_argument("--out-dir", type=Path, default=Path("tmp") / "fe_frontiers")
    args = parser.parse_args()

    args.out_dir.mkdir(parents=True, exist_ok=True)

    if args.epoch in ("both", "ruby"):
        ruby_candidates = run_ruby(args)
        run_epoch("ruby", ruby_candidates, args)

    if args.epoch in ("both", "post"):
        post_candidates = run_post(args)
        run_epoch("postruby", post_candidates, args)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
