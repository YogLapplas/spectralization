#!/usr/bin/env python3
"""
Enumerate small 1D solid laser cavities.

This is an external balance probe, not a gameplay runtime path. It mirrors the
current Spectralization gain-medium model closely enough for material design:

  - each slot is a local two-port optical block;
  - passive TRA is frequency dependent;
  - only transmission through a gain medium is active;
  - active transmission uses O = min(I * T * G, I * T + extra);
  - contiguous blocks of the same material share pump density;
  - each excited gain block injects coherent seed power in both axial directions.

The script is intentionally compact and standard-library only.
"""

from __future__ import annotations

import argparse
import csv
import itertools
import math
import random
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable


EPS = 1.0e-9
MAX_TOTAL_POWER = 1.0e12
MAX_ACTIVE_SET_ITERS = 64
MAX_RELAXATION_ITERS = 16384


@dataclass(frozen=True)
class Response:
    t: float
    r: float
    a: float


@dataclass(frozen=True)
class Material:
    key: str
    line: str | None
    max_pump: float
    gain_per_pu: float
    saturation: float
    handling: float
    seed_per_exciter: float
    coupling_half_width: float
    response: Callable[[str, bool], Response]

    @property
    def gain_medium(self) -> bool:
        return self.line is not None


@dataclass(frozen=True)
class Block:
    material: Material
    pump: float = 0.0
    excited: bool = True


@dataclass(frozen=True)
class Transition:
    source: int
    target: int
    gain: float
    saturated_slope: float | None = None
    saturated_extra: float = 0.0

    def saturating(self) -> bool:
        return self.saturated_slope is not None and self.saturated_extra > 0.0

    def output_for(self, value: float) -> float:
        if value <= 0.0:
            return 0.0
        unsaturated = value * self.gain
        if not self.saturating():
            return unsaturated
        saturated = value * self.saturated_slope + self.saturated_extra
        return min(unsaturated, saturated)

    def saturated_selected(self, value: float) -> bool:
        if value <= 0.0 or not self.saturating():
            return False
        return value * self.saturated_slope + self.saturated_extra <= value * self.gain + 1.0e-6

    def slope_for(self, saturated: bool) -> float:
        if saturated and self.saturating():
            return self.saturated_slope
        return self.gain

    def intercept_for(self, saturated: bool) -> float:
        if saturated and self.saturating():
            return self.saturated_extra
        return 0.0


@dataclass(frozen=True)
class SolveResult:
    ok: bool
    sensor_right: float
    sensor_left: float
    max_port_power: float
    total_port_power: float
    absorbed_by_slot: tuple[float, ...]
    burned_slots: tuple[int, ...]
    per_line_output: tuple[tuple[str, float], ...]


@dataclass(frozen=True)
class HistogramRow:
    low: float
    high: float
    count: int
    burned: int


@dataclass(frozen=True)
class UpperRow:
    length: int
    cases_scanned: int
    exact: bool
    slots: tuple[Block, ...]
    result: SolveResult


def constant_response(response: Response) -> Callable[[str, bool], Response]:
    return lambda _line, _pumped: response


def parse_frequency(freq: str) -> tuple[str, int]:
    region, bin_text = freq.split(":", 1)
    return region, int(bin_text)


def frequency_distance(left: str, right: str) -> int | None:
    left_region, left_bin = parse_frequency(left)
    right_region, right_bin = parse_frequency(right)
    if left_region != right_region:
        return None
    return abs(left_bin - right_bin)


def emission_coupling(material: Material, frequency: str) -> float:
    if material.line is None:
        return 0.0
    distance = frequency_distance(material.line, frequency)
    if distance is None or distance >= material.coupling_half_width:
        return 0.0
    return max(0.0, 1.0 - distance / material.coupling_half_width)


def rare_earth_response(yag: bool, line: str) -> Callable[[str, bool], Response]:
    def response(freq: str, pumped: bool) -> Response:
        distance = frequency_distance(line, freq)
        on_line = distance == 0
        region, _bin = parse_frequency(freq)
        if yag:
            if on_line:
                return Response(0.972 if pumped else 0.955, 0.012 if pumped else 0.015, 0.004 if pumped else 0.012)
            if region == "visible":
                return Response(0.740 if pumped else 0.780, 0.025, 0.090 if pumped else 0.070)
            if region == "ultraviolet":
                return Response(0.480, 0.030, 0.420)
            return Response(0.900, 0.025, 0.035)
        if on_line:
            return Response(0.985 if pumped else 0.975, 0.010 if pumped else 0.012, 0.002 if pumped else 0.006)
        if region == "visible":
            return Response(0.890 if pumped else 0.910, 0.018, 0.040 if pumped else 0.030)
        if region == "ultraviolet":
            return Response(0.880, 0.025, 0.055)
        return Response(0.940, 0.020, 0.025)

    return response


AIR = Material("air", None, 0.0, 0.0, math.inf, math.inf, 0.0, 1.0, constant_response(Response(1.0, 0.0, 0.0)))
MIRROR = Material("mirror", None, 0.0, 0.0, math.inf, math.inf, 0.0, 1.0, constant_response(Response(0.09, 0.90, 0.01)))
SENSOR = Material("sensor", None, 0.0, 0.0, math.inf, math.inf, 0.0, 1.0, constant_response(Response(0.0, 0.0, 0.0)))

MATERIALS: dict[str, Material] = {
    "ruby": Material(
        "ruby", "visible:26", 4.0, 0.070, 120.0, 320.0, 7.5, 12.0,
        lambda freq, pumped: Response(0.929, 0.031, 0.040)
        if freq == "visible:26"
        else (Response(0.38 if pumped else 0.35, 0.04, 0.46 if pumped else 0.50)
              if parse_frequency(freq)[0] == "visible"
              else Response(0.64 if pumped else 0.60, 0.03, 0.20 if pumped else 0.24)),
    ),
    "ce_yag": Material("ce_yag", "visible:14", 6.0, 0.055, 650.0, 2000.0, 20.0, 4.0, rare_earth_response(True, "visible:14")),
    "nd_yag": Material("nd_yag", "visible:26", 8.0, 0.045, 1200.0, 3200.0, 18.0, 4.0, rare_earth_response(True, "visible:26")),
    "yb_yag": Material("yb_yag", "visible:5", 10.0, 0.040, 2200.0, 5600.0, 12.0, 4.0, rare_earth_response(True, "visible:5")),
    "er_yag": Material("er_yag", "infrared:18", 8.0, 0.038, 1500.0, 4500.0, 10.0, 4.0, rare_earth_response(True, "infrared:18")),
    "ce_caf2": Material("ce_caf2", "visible:14", 6.0, 0.040, 450.0, 1600.0, 18.0, 3.0, rare_earth_response(False, "visible:14")),
    "nd_caf2": Material("nd_caf2", "visible:26", 8.0, 0.035, 900.0, 2800.0, 16.0, 3.0, rare_earth_response(False, "visible:26")),
    "yb_caf2": Material("yb_caf2", "visible:5", 10.0, 0.032, 1500.0, 4600.0, 10.0, 3.0, rare_earth_response(False, "visible:5")),
    "er_caf2": Material("er_caf2", "infrared:18", 8.0, 0.030, 1200.0, 4000.0, 8.0, 3.0, rare_earth_response(False, "infrared:18")),
    "air": AIR,
}


def domain_pump_density(slots: list[Block]) -> list[float]:
    densities = [0.0] * len(slots)
    index = 0
    while index < len(slots):
        material = slots[index].material
        end = index + 1
        while end < len(slots) and slots[end].material.key == material.key:
            end += 1
        if material.gain_medium:
            total = sum(block.pump for block in slots[index:end])
            density = min(material.max_pump, max(0.0, total / (end - index)))
            for slot in range(index, end):
                densities[slot] = density
        index = end
    return densities


def linear_solve(matrix: list[list[float]], rhs: list[float]) -> list[float] | None:
    n = len(rhs)
    a = [[(1.0 if row == col else 0.0) - matrix[row][col] for col in range(n)] + [rhs[row]] for row in range(n)]
    for col in range(n):
        pivot = max(range(col, n), key=lambda row: abs(a[row][col]))
        if abs(a[pivot][col]) <= 1.0e-12 or not math.isfinite(a[pivot][col]):
            return None
        if pivot != col:
            a[col], a[pivot] = a[pivot], a[col]
        divisor = a[col][col]
        for j in range(col, n + 1):
            a[col][j] /= divisor
        for row in range(n):
            if row == col:
                continue
            factor = a[row][col]
            if abs(factor) <= 1.0e-16:
                continue
            for j in range(col, n + 1):
                a[row][j] -= factor * a[col][j]
    return [max(0.0, a[row][n]) for row in range(n)]


def affine_solve(transitions: list[Transition], rhs: list[float], saturated: list[bool]) -> list[float] | None:
    n = len(rhs)
    matrix = [[0.0] * n for _ in range(n)]
    intercept = list(rhs)
    for selected, transition in zip(saturated, transitions):
        matrix[transition.target][transition.source] += transition.slope_for(selected)
        intercept[transition.target] += transition.intercept_for(selected)
    solution = linear_solve(matrix, intercept)
    if solution is None or any(not math.isfinite(value) for value in solution):
        return None
    return solution


def solve_piecewise(transitions: list[Transition], rhs: list[float]) -> list[float] | None:
    saturated = [transition.saturated_selected(rhs[transition.source]) for transition in transitions]
    for _iteration in range(MAX_ACTIVE_SET_ITERS):
        solution = affine_solve(transitions, rhs, saturated)
        if solution is None:
            return solve_by_relaxation(transitions, rhs)
        changed = False
        for index, transition in enumerate(transitions):
            selected = transition.saturated_selected(solution[transition.source])
            if saturated[index] != selected:
                saturated[index] = selected
                changed = True
        if changed:
            continue
        reconstructed = list(rhs)
        for transition in transitions:
            reconstructed[transition.target] += transition.output_for(solution[transition.source])
        residual = max(abs(left - right) for left, right in zip(reconstructed, solution))
        total = sum(solution)
        if total > MAX_TOTAL_POWER or residual > 1.0e-5 * max(1.0, total):
            return solve_by_relaxation(transitions, rhs)
        return solution
    return solve_by_relaxation(transitions, rhs)


def solve_by_relaxation(transitions: list[Transition], rhs: list[float]) -> list[float] | None:
    current = [max(0.0, value) for value in rhs]
    for _iteration in range(MAX_RELAXATION_ITERS):
        next_values = [max(0.0, value) for value in rhs]
        for transition in transitions:
            output = transition.output_for(current[transition.source])
            if not math.isfinite(output) or output < -1.0e-6:
                return None
            next_values[transition.target] += max(0.0, output)

        residual = max(abs(left - right) for left, right in zip(next_values, current))
        total = sum(next_values)
        if not math.isfinite(total) or total > MAX_TOTAL_POWER:
            return None
        current = next_values
        if residual <= 1.0e-6 or residual <= 1.0e-7 * max(1.0, total):
            return current
    return None


def axial_line_frequencies(slots: list[Block]) -> list[str]:
    lines = sorted({block.material.line for block in slots if block.material.line is not None})
    return [line for line in lines if line is not None]


def solve_line(slots: list[Block], frequency: str, left_r: float, right_r: float, mirror_a: float) -> tuple[bool, float, float, float, float, tuple[float, ...]]:
    components = [
        Block(Material("left_mirror", None, 0.0, 0.0, math.inf, math.inf, 0.0, 1.0, constant_response(Response(max(0.0, 1.0 - left_r - mirror_a), left_r, mirror_a)))),
        *slots,
        Block(Material("right_mirror", None, 0.0, 0.0, math.inf, math.inf, 0.0, 1.0, constant_response(Response(max(0.0, 1.0 - right_r - mirror_a), right_r, mirror_a)))),
        Block(SENSOR),
    ]
    slot_offset = 1
    node_count = len(components) * 2
    lhs = lambda i: i * 2
    rhs_node = lambda i: i * 2 + 1
    transitions: list[Transition] = []
    source = [0.0] * node_count
    densities = domain_pump_density(slots)

    def add_transition(source_node: int, target_node: int, gain: float, slope: float | None = None, extra: float = 0.0) -> None:
        if gain > 0.0:
            transitions.append(Transition(source_node, target_node, gain, slope, extra))

    for i, block in enumerate(components[:-1]):
        material = block.material
        slot_index = i - slot_offset
        pumped = slot_index >= 0 and slot_index < len(slots) and densities[slot_index] > 0.0
        response = material.response(frequency, pumped)

        active_gain = 1.0
        extra = 0.0
        if slot_index >= 0 and slot_index < len(slots) and material.gain_medium:
            coupling = emission_coupling(material, frequency)
            if coupling > 0.0 and densities[slot_index] > 0.0:
                active_gain = 1.0 + material.gain_per_pu * densities[slot_index] * coupling
                if active_gain > 1.0 + EPS:
                    extra = response.t * material.saturation * (1.0 - 1.0 / active_gain)
            if block.excited and material.line == frequency:
                axial_seed = material.seed_per_exciter / 6.0
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

    powers = solve_piecewise(transitions, source)
    if powers is None:
        return False, 0.0, 0.0, 0.0, 0.0, tuple(0.0 for _ in slots)

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


def solve_cavity(slots: list[Block], left_r: float, right_r: float, mirror_a: float) -> SolveResult:
    per_line = []
    total_right = 0.0
    total_left = 0.0
    max_port = 0.0
    total_port = 0.0
    absorbed_by_slot = [0.0] * len(slots)
    for frequency in axial_line_frequencies(slots):
        ok, right, left, line_max, line_total, absorbed = solve_line(slots, frequency, left_r, right_r, mirror_a)
        if not ok:
            return SolveResult(False, 0.0, 0.0, 0.0, 0.0, tuple(absorbed_by_slot), tuple(), tuple(per_line))
        per_line.append((frequency, right))
        total_right += right
        total_left += left
        max_port = max(max_port, line_max)
        total_port += line_total
        for index, value in enumerate(absorbed):
            absorbed_by_slot[index] += value
    burned = tuple(index for index, block in enumerate(slots) if absorbed_by_slot[index] > block.material.handling)
    return SolveResult(True, total_right, total_left, max_port, total_port, tuple(absorbed_by_slot), burned, tuple(per_line))


def pump_values(material: Material, steps: int, full_only: bool) -> tuple[float, ...]:
    if not material.gain_medium:
        return (0.0,)
    if full_only:
        return (material.max_pump,)
    if steps <= 1:
        return (0.0, material.max_pump)
    return tuple(material.max_pump * i / steps for i in range(steps + 1))


def enumerate_cases(args: argparse.Namespace) -> Iterable[tuple[list[Block], SolveResult]]:
    material_keys = [key.strip() for key in args.materials.split(",") if key.strip()]
    materials = [MATERIALS[key] for key in material_keys]
    for material_combo in itertools.product(materials, repeat=args.n):
        if args.require_gain and not any(material.gain_medium for material in material_combo):
            continue
        pump_options = [pump_values(material, args.pump_steps, args.full_pump_only) for material in material_combo]
        for pumps in itertools.product(*pump_options):
            slots = [Block(material, pump, args.excited) for material, pump in zip(material_combo, pumps)]
            yield slots, solve_cavity(slots, args.left_r, args.right_r, args.mirror_a)


def sample_cases(args: argparse.Namespace) -> Iterable[tuple[list[Block], SolveResult]]:
    material_keys = [key.strip() for key in args.materials.split(",") if key.strip()]
    materials = [MATERIALS[key] for key in material_keys]
    rng = random.Random(args.sample_seed)
    emitted = 0

    while emitted < args.sample_cases:
        selected = [rng.choice(materials) for _index in range(args.n)]
        if args.require_gain and not any(material.gain_medium for material in selected):
            continue
        slots = [
            Block(material, rng.choice(pump_values(material, args.pump_steps, args.full_pump_only)), args.excited)
            for material in selected
        ]
        emitted += 1
        yield slots, solve_cavity(slots, args.left_r, args.right_r, args.mirror_a)


def material_states(args: argparse.Namespace) -> tuple[Block, ...]:
    material_keys = [key.strip() for key in args.materials.split(",") if key.strip()]
    states = []
    for key in material_keys:
        material = MATERIALS[key]
        for pump in pump_values(material, args.pump_steps, args.full_pump_only):
            states.append(Block(material, pump, args.excited))
    return tuple(states)


def parse_lengths(text: str) -> tuple[int, ...]:
    lengths = []
    for part in text.split(","):
        part = part.strip()
        if not part:
            continue
        if "-" in part:
            left, right = part.split("-", 1)
            start = int(left)
            end = int(right)
            step = 1 if end >= start else -1
            lengths.extend(range(start, end + step, step))
        else:
            lengths.append(int(part))
    return tuple(length for length in lengths if length > 0)


def estimate_upper_cases(args: argparse.Namespace, length: int) -> int:
    states = material_states(args)
    if not states:
        return 0
    return len(states) ** length


def best_upper_exhaustive(args: argparse.Namespace, length: int) -> UpperRow:
    states = material_states(args)
    best_slots: tuple[Block, ...] = tuple()
    best_result = SolveResult(False, 0.0, 0.0, 0.0, 0.0, tuple(), tuple(), tuple())
    scanned = 0

    for combo in itertools.product(states, repeat=length):
        if args.require_gain and not any(block.material.gain_medium for block in combo):
            continue
        result = solve_cavity(list(combo), args.left_r, args.right_r, args.mirror_a)
        scanned += 1
        if result.ok and result_metric(result, args.upper_metric) > result_metric(best_result, args.upper_metric):
            best_slots = tuple(combo)
            best_result = result

    return UpperRow(length, scanned, True, best_slots, best_result)


def best_upper_beam(args: argparse.Namespace, length: int) -> UpperRow:
    states = material_states(args)
    width = max(1, args.upper_beam)
    beam: list[tuple[float, tuple[Block, ...], SolveResult]] = [
        (0.0, tuple(), SolveResult(True, 0.0, 0.0, 0.0, 0.0, tuple(), tuple(), tuple()))
    ]
    scanned = 0

    for current_length in range(1, length + 1):
        candidates: list[tuple[float, tuple[Block, ...], SolveResult]] = []
        for _score, prefix, _prefix_result in beam:
            for state in states:
                slots = prefix + (state,)
                if current_length == length and args.require_gain and not any(block.material.gain_medium for block in slots):
                    continue
                result = solve_cavity(list(slots), args.left_r, args.right_r, args.mirror_a)
                scanned += 1
                value = result_metric(result, args.upper_metric) if result.ok else -1.0
                candidates.append((value, slots, result))
        candidates.sort(key=lambda item: item[0], reverse=True)
        beam = candidates[:width]

    best_value, best_slots, best_result = beam[0]
    return UpperRow(length, scanned, False, best_slots, best_result)


def upper_rows(args: argparse.Namespace) -> list[UpperRow]:
    rows = []
    for length in parse_lengths(args.lengths):
        estimated = estimate_upper_cases(args, length)
        exhaustive = args.upper_mode == "exhaustive" or (
            args.upper_mode == "auto" and estimated <= args.upper_exhaustive_limit
        )

        if exhaustive:
            row = best_upper_exhaustive(args, length)
        else:
            row = best_upper_beam(args, length)

        rows.append(row)
        marker = "exact" if row.exact else f"beam{args.upper_beam}"
        print(
            f"n={row.length} {marker} scanned={row.cases_scanned} "
            f"{args.upper_metric}={result_metric(row.result, args.upper_metric):.6g} "
            f"slots=[{format_slots(list(row.slots))}] pumps=[{format_pumps(list(row.slots))}]"
        )
    return rows


def percentile(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * q)))
    return ordered[index]


def format_slots(slots: list[Block]) -> str:
    return " ".join(block.material.key for block in slots)


def format_pumps(slots: list[Block]) -> str:
    return " ".join(f"{block.pump:.2f}" for block in slots)


def result_metric(result: SolveResult, metric: str) -> float:
    if metric == "max_port":
        return result.max_port_power
    if metric == "left_leak":
        return result.sensor_left
    if metric == "total_port":
        return result.total_port_power
    return result.sensor_right


def histogram_label(row: HistogramRow) -> str:
    return f"[{row.low:.6g}, {row.high:.6g})"


def histogram(
        rows: list[tuple[list[Block], SolveResult]],
        metric: str,
        bucket_count: int,
        scale: str
) -> list[HistogramRow]:
    values = [
        (max(0.0, result_metric(result, metric)), bool(result.burned_slots))
        for _slots, result in rows
        if result.ok
    ]
    if not values:
        return []

    bucket_count = max(1, bucket_count)
    raw_values = [value for value, _burned in values]
    minimum = min(raw_values)
    maximum = max(raw_values)

    if scale == "log" and maximum > 0.0:
        positive = [value for value in raw_values if value > 0.0]
        min_positive = min(positive) if positive else 1.0
        log_min = math.log10(min_positive)
        log_max = math.log10(maximum)
        if abs(log_max - log_min) <= EPS:
            log_min -= 0.5
            log_max += 0.5
        edges = [10.0 ** (log_min + (log_max - log_min) * i / bucket_count) for i in range(bucket_count + 1)]
        edges[0] = 0.0 if minimum <= 0.0 else edges[0]
    else:
        if abs(maximum - minimum) <= EPS:
            minimum = max(0.0, minimum - 0.5)
            maximum += 0.5
        edges = [minimum + (maximum - minimum) * i / bucket_count for i in range(bucket_count + 1)]

    counts = [0] * bucket_count
    burned_counts = [0] * bucket_count

    for value, burned in values:
        if value >= edges[-1]:
            index = bucket_count - 1
        else:
            index = 0
            while index + 1 < len(edges) and value >= edges[index + 1]:
                index += 1
            index = min(bucket_count - 1, index)
        counts[index] += 1
        if burned:
            burned_counts[index] += 1

    return [
        HistogramRow(edges[index], edges[index + 1], counts[index], burned_counts[index])
        for index in range(bucket_count)
    ]


def print_histogram(rows: list[HistogramRow], metric: str, scale: str) -> None:
    if not rows:
        return

    max_count = max(row.count for row in rows)
    width = 42
    print(f"\nhistogram metric={metric} scale={scale}")
    for row in rows:
        bar_width = 0 if max_count <= 0 else round(width * row.count / max_count)
        bar = "#" * bar_width
        burned_text = "" if row.burned <= 0 else f" burned={row.burned}"
        print(f"{histogram_label(row):>24} {row.count:>6} {bar}{burned_text}")


def write_histogram_csv(path: Path, rows: list[HistogramRow]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["low", "high", "count", "burned"])
        for row in rows:
            writer.writerow([row.low, row.high, row.count, row.burned])
    print(f"wrote histogram csv {path}")


def write_histogram_svg(path: Path, rows: list[HistogramRow], metric: str, scale: str) -> None:
    if not rows:
        return

    path.parent.mkdir(parents=True, exist_ok=True)
    width = 960
    height = 420
    left = 72
    right = 24
    top = 52
    bottom = 72
    plot_width = width - left - right
    plot_height = height - top - bottom
    max_count = max(1, max(row.count for row in rows))
    bar_gap = 2
    bar_width = max(1, plot_width / len(rows) - bar_gap)

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#0b1016"/>',
        f'<text x="{left}" y="28" fill="#dbe7ee" font-family="Consolas, monospace" font-size="18">Laser sweep histogram: {metric} ({scale})</text>',
        f'<line x1="{left}" y1="{top + plot_height}" x2="{left + plot_width}" y2="{top + plot_height}" stroke="#6c7a86"/>',
        f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top + plot_height}" stroke="#6c7a86"/>',
    ]

    for index, row in enumerate(rows):
        x = left + index * plot_width / len(rows)
        bar_height = plot_height * row.count / max_count
        y = top + plot_height - bar_height
        color = "#e5bd60" if row.burned <= 0 else "#d66767"
        parts.append(
            f'<rect x="{x + bar_gap / 2:.2f}" y="{y:.2f}" width="{bar_width:.2f}" '
            f'height="{bar_height:.2f}" fill="{color}"/>'
        )
        if index == 0 or index == len(rows) - 1 or index % max(1, len(rows) // 6) == 0:
            label = f"{row.low:.3g}"
            parts.append(
                f'<text x="{x:.2f}" y="{height - 38}" fill="#9fb1bd" '
                f'font-family="Consolas, monospace" font-size="11" transform="rotate(35 {x:.2f},{height - 38})">{label}</text>'
            )

    for tick in range(5):
        count = max_count * tick / 4
        y = top + plot_height - plot_height * tick / 4
        parts.append(f'<line x1="{left - 4}" y1="{y:.2f}" x2="{left}" y2="{y:.2f}" stroke="#6c7a86"/>')
        parts.append(
            f'<text x="{left - 8}" y="{y + 4:.2f}" fill="#9fb1bd" text-anchor="end" '
            f'font-family="Consolas, monospace" font-size="11">{count:.0f}</text>'
        )

    parts.append(f'<text x="{left + plot_width / 2}" y="{height - 12}" fill="#9fb1bd" text-anchor="middle" font-family="Consolas, monospace" font-size="12">SP bucket lower bound</text>')
    parts.append('</svg>')

    path.write_text("\n".join(parts), encoding="utf-8")
    print(f"wrote histogram svg {path}")


def write_histogram_png(path: Path, rows: list[HistogramRow], metric: str, scale: str) -> None:
    if not rows:
        return

    path.parent.mkdir(parents=True, exist_ok=True)

    try:
        import matplotlib.pyplot as plt
    except Exception as exc:
        raise RuntimeError("matplotlib is required for --hist-png") from exc

    x_values = list(range(len(rows)))
    normal_counts = [max(0, row.count - row.burned) for row in rows]
    burned_counts = [row.burned for row in rows]
    labels = [f"{row.low:.3g}" for row in rows]

    figure, axis = plt.subplots(figsize=(11, 5.8), dpi=160)
    figure.patch.set_facecolor("#0b1016")
    axis.set_facecolor("#101923")
    axis.bar(x_values, normal_counts, color="#e5bd60", width=0.82, label="valid")
    if any(count > 0 for count in burned_counts):
        axis.bar(x_values, burned_counts, bottom=normal_counts, color="#d66767", width=0.82, label="burns")

    tick_step = max(1, len(rows) // 8)
    ticks = [index for index in x_values if index % tick_step == 0 or index == x_values[-1]]
    axis.set_xticks(ticks)
    axis.set_xticklabels([labels[index] for index in ticks], rotation=35, ha="right")
    axis.set_title(f"Laser output distribution, {metric} ({scale} buckets)", color="#dbe7ee", pad=14)
    axis.set_xlabel("SP bucket lower bound", color="#c7d3dc")
    axis.set_ylabel("case count", color="#c7d3dc")
    axis.tick_params(colors="#c7d3dc")
    axis.grid(True, axis="y", color="#33424e", alpha=0.65)

    for spine in axis.spines.values():
        spine.set_color("#6c7a86")

    if any(count > 0 for count in burned_counts):
        legend = axis.legend(facecolor="#101923", edgecolor="#6c7a86")
        for text in legend.get_texts():
            text.set_color("#dbe7ee")

    figure.tight_layout()
    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)
    print(f"wrote histogram png {path}")


def write_upper_csv(path: Path, rows: list[UpperRow], metric: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow([
            "length",
            "metric",
            "metric_value",
            "output_sp",
            "left_leak_sp",
            "max_port_power",
            "total_port_power",
            "cases_scanned",
            "exact",
            "burned_slots",
            "slots",
            "pumps",
            "per_line_output",
            "absorbed_by_slot",
            "ok",
        ])
        for row in rows:
            result = row.result
            writer.writerow([
                row.length,
                metric,
                result_metric(result, metric),
                result.sensor_right,
                result.sensor_left,
                result.max_port_power,
                result.total_port_power,
                row.cases_scanned,
                row.exact,
                " ".join(map(str, result.burned_slots)),
                format_slots(list(row.slots)),
                format_pumps(list(row.slots)),
                " ".join(f"{line}:{power:.9g}" for line, power in result.per_line_output),
                " ".join(f"{value:.9g}" for value in result.absorbed_by_slot),
                result.ok,
            ])
    print(f"wrote upper csv {path}")


def write_upper_png(path: Path, rows: list[UpperRow], metric: str, scale: str) -> None:
    if not rows:
        return

    path.parent.mkdir(parents=True, exist_ok=True)

    try:
        import matplotlib.pyplot as plt
    except Exception as exc:
        raise RuntimeError("matplotlib is required for --upper-png") from exc

    lengths = [row.length for row in rows]
    values = [max(0.0, result_metric(row.result, metric)) for row in rows]
    colors = ["#d66767" if row.result.burned_slots else "#e5bd60" for row in rows]

    figure, axis = plt.subplots(figsize=(10, 5.5), dpi=160)
    figure.patch.set_facecolor("#0b1016")
    axis.set_facecolor("#101923")
    axis.bar(lengths, values, color=colors, width=0.58, edgecolor="#dbe7ee", linewidth=0.8)
    axis.plot(lengths, values, color="#62c7d5", marker="o", linewidth=2.0, markersize=5)

    if scale == "log" and max(values) > 0.0:
        axis.set_yscale("log")

    axis.set_title(f"Laser upper envelope by cavity length ({metric})", color="#dbe7ee", pad=14)
    axis.set_xlabel("material slot count", color="#c7d3dc")
    axis.set_ylabel("SP", color="#c7d3dc")
    axis.set_xticks(lengths)
    axis.tick_params(colors="#c7d3dc")
    axis.grid(True, axis="y", color="#33424e", alpha=0.65)

    for spine in axis.spines.values():
        spine.set_color("#6c7a86")

    for x, value, row in zip(lengths, values, rows):
        label = f"{value:.3g}"
        axis.annotate(
            label,
            xy=(x, value),
            xytext=(0, 8),
            textcoords="offset points",
            ha="center",
            va="bottom",
            color="#dbe7ee",
            fontsize=8,
        )

    subtitle = "red bars mean the best case burns at least one gain block"
    figure.text(0.5, 0.015, subtitle, ha="center", color="#9fb1bd", fontsize=9)
    figure.tight_layout(rect=(0, 0.04, 1, 1))
    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)
    print(f"wrote upper png {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Sweep 1D solid laser material arrangements.")
    parser.add_argument("--n", type=int, default=3, help="number of material slots")
    parser.add_argument("--materials", default="ruby,ce_yag,nd_yag,yb_yag,air")
    parser.add_argument("--pump-steps", type=int, default=1, help="per-material pump subdivision; 1 means 0/full, 2 means 0/half/full")
    parser.add_argument("--full-pump-only", action="store_true", help="only test max pump for gain media")
    parser.add_argument("--not-excited", dest="excited", action="store_false", help="disable internal seed excitation")
    parser.add_argument("--left-r", type=float, default=0.90)
    parser.add_argument("--right-r", type=float, default=0.90)
    parser.add_argument("--mirror-a", type=float, default=0.01)
    parser.add_argument("--top", type=int, default=20)
    parser.add_argument("--csv", type=Path, default=None)
    parser.add_argument("--no-hist", action="store_true", help="do not print the output distribution histogram")
    parser.add_argument("--hist-bins", type=int, default=24)
    parser.add_argument("--hist-scale", choices=("log", "linear"), default="log")
    parser.add_argument("--hist-metric", choices=("output", "max_port", "left_leak", "total_port"), default="output")
    parser.add_argument("--hist-csv", type=Path, default=None)
    parser.add_argument("--hist-svg", type=Path, default=None)
    parser.add_argument("--hist-png", type=Path, default=None)
    parser.add_argument("--sample-cases", type=int, default=0, help="sample this many discrete cases instead of exhaustive enumeration")
    parser.add_argument("--sample-seed", type=int, default=1)
    parser.add_argument("--lengths", default=None, help="comma/range list for upper envelope, for example 2-8")
    parser.add_argument("--upper-mode", choices=("auto", "exhaustive", "beam"), default="auto")
    parser.add_argument("--upper-exhaustive-limit", type=int, default=500_000)
    parser.add_argument("--upper-beam", type=int, default=2048)
    parser.add_argument("--upper-metric", choices=("output", "max_port", "left_leak", "total_port"), default="output")
    parser.add_argument("--upper-scale", choices=("log", "linear"), default="log")
    parser.add_argument("--upper-csv", type=Path, default=None)
    parser.add_argument("--upper-png", type=Path, default=None)
    parser.add_argument("--require-gain", action="store_true", default=True)
    args = parser.parse_args()

    if args.lengths is not None:
        rows = upper_rows(args)

        if args.upper_csv is not None:
            write_upper_csv(args.upper_csv, rows, args.upper_metric)

        if args.upper_png is not None:
            write_upper_png(args.upper_png, rows, args.upper_metric, args.upper_scale)

        return 0

    rows = []
    case_source = sample_cases(args) if args.sample_cases > 0 else enumerate_cases(args)
    for slots, result in case_source:
        rows.append((slots, result))

    values = [result.sensor_right for _slots, result in rows if result.ok]
    burned = sum(1 for _slots, result in rows if result.burned_slots)
    failed = sum(1 for _slots, result in rows if not result.ok)
    case_mode = f"sampled={args.sample_cases}" if args.sample_cases > 0 else "exhaustive"
    print(f"cases={len(rows)} mode={case_mode} ok={len(values)} failed={failed} burned={burned}")
    if values:
        print(
            "output_SP "
            f"min={min(values):.6g} p25={percentile(values, 0.25):.6g} "
            f"median={percentile(values, 0.50):.6g} p75={percentile(values, 0.75):.6g} "
            f"p95={percentile(values, 0.95):.6g} max={max(values):.6g}"
        )

    hist_rows = histogram(rows, args.hist_metric, args.hist_bins, args.hist_scale)

    if not args.no_hist:
        print_histogram(hist_rows, args.hist_metric, args.hist_scale)

    if args.hist_csv is not None:
        write_histogram_csv(args.hist_csv, hist_rows)

    if args.hist_svg is not None:
        write_histogram_svg(args.hist_svg, hist_rows, args.hist_metric, args.hist_scale)

    if args.hist_png is not None:
        write_histogram_png(args.hist_png, hist_rows, args.hist_metric, args.hist_scale)

    best = sorted(rows, key=lambda item: item[1].sensor_right if item[1].ok else -1.0, reverse=True)[:args.top]
    print("\ntop cases:")
    for rank, (slots, result) in enumerate(best, 1):
        line_text = ",".join(f"{line}:{power:.3g}" for line, power in result.per_line_output)
        burn_text = "-" if not result.burned_slots else ",".join(str(index) for index in result.burned_slots)
        print(
            f"{rank:>3}. out={result.sensor_right:>12.6g} left={result.sensor_left:>10.6g} "
            f"max_port={result.max_port_power:>12.6g} burn={burn_text:>5} "
            f"slots=[{format_slots(slots)}] pumps=[{format_pumps(slots)}] lines=[{line_text}]"
        )

    if args.csv is not None:
        args.csv.parent.mkdir(parents=True, exist_ok=True)
        with args.csv.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.writer(handle)
            writer.writerow([
                "output_sp",
                "left_leak_sp",
                "max_port_power",
                "total_port_power",
                "burned_slots",
                "slots",
                "pumps",
                "per_line_output",
                "absorbed_by_slot",
                "ok",
            ])
            for slots, result in rows:
                writer.writerow([
                    result.sensor_right,
                    result.sensor_left,
                    result.max_port_power,
                    result.total_port_power,
                    " ".join(map(str, result.burned_slots)),
                    format_slots(slots),
                    format_pumps(slots),
                    " ".join(f"{line}:{power:.9g}" for line, power in result.per_line_output),
                    " ".join(f"{value:.9g}" for value in result.absorbed_by_slot),
                    result.ok,
                ])
        print(f"\nwrote {args.csv}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
