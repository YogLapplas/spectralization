#!/usr/bin/env python3
"""
Draw seed-vs-pump maps for homogeneous 1D solid laser cavities.

This is a balance probe only. It deliberately treats seed strength and pump
density as separate input axes, because material pump sensitivity is the local
single-pass gain increment per PU, while seed strength controls the pre-saturation
output scale.
"""

from __future__ import annotations

import argparse
import csv
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import laser_material_sweep as sweep


EPS = 1.0e-9
DEFAULT_MATERIALS = (
    "ruby",
    "ce_yag",
    "nd_yag",
    "yb_yag",
    "er_yag",
    "ce_caf2",
    "nd_caf2",
    "yb_caf2",
    "er_caf2",
)


@dataclass(frozen=True)
class MapSample:
    material_key: str
    length: int
    seed_per_block: float
    pump_density: float
    single_pass_gain: float
    output_sp: float
    left_leak_sp: float
    max_port_power: float
    total_port_power: float
    burned_slots: tuple[int, ...]
    ok: bool


def solve_line_with_seed(
        slots: list[sweep.Block],
        frequency: str,
        left_r: float,
        right_r: float,
        mirror_a: float,
        seed_per_block: float,
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
            sweep.constant_response(sweep.Response(max(0.0, 1.0 - left_r - mirror_a), left_r, mirror_a)),
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
            sweep.constant_response(sweep.Response(max(0.0, 1.0 - right_r - mirror_a), right_r, mirror_a)),
        )),
        sweep.Block(sweep.SENSOR),
    ]
    slot_offset = 1
    node_count = len(components) * 2
    lhs = lambda i: i * 2
    rhs_node = lambda i: i * 2 + 1
    transitions: list[sweep.Transition] = []
    source = [0.0] * node_count
    densities = sweep.domain_pump_density(slots)

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

            if seed_per_block > 0.0 and material.line == frequency:
                axial_seed = seed_per_block / 6.0
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


def solve_cavity_with_seed(
        slots: list[sweep.Block],
        left_r: float,
        right_r: float,
        mirror_a: float,
        seed_per_block: float,
) -> sweep.SolveResult:
    per_line = []
    total_right = 0.0
    total_left = 0.0
    max_port = 0.0
    total_port = 0.0
    absorbed_by_slot = [0.0] * len(slots)

    for frequency in sweep.axial_line_frequencies(slots):
        ok, right, left, line_max, line_total, absorbed = solve_line_with_seed(
            slots,
            frequency,
            left_r,
            right_r,
            mirror_a,
            seed_per_block,
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


def material_keys(text: str) -> tuple[str, ...]:
    keys = tuple(part.strip() for part in text.split(",") if part.strip())
    unknown = [key for key in keys if key not in sweep.MATERIALS or not sweep.MATERIALS[key].gain_medium]
    if unknown:
        raise ValueError(f"unknown or non-gain material keys: {', '.join(unknown)}")
    return keys


def logspace_with_zero(max_value: float, count: int) -> list[float]:
    if count <= 1:
        return [0.0]
    positive = [
        10.0 ** (-3.0 + (math.log10(max_value) + 3.0) * i / (count - 2))
        for i in range(count - 1)
    ]
    return [0.0, *positive]


def logspace_positive(max_value: float, count: int) -> list[float]:
    if count <= 1:
        return [max_value]
    return [
        10.0 ** (-3.0 + (math.log10(max_value) + 3.0) * i / (count - 1))
        for i in range(count)
    ]


def linear_values(max_value: float, count: int) -> list[float]:
    if count <= 1:
        return [max_value]
    return [max_value * i / (count - 1) for i in range(count)]


def sample_material_map(
        material: sweep.Material,
        length: int,
        seeds: Iterable[float],
        pumps: Iterable[float],
        left_r: float,
        right_r: float,
        mirror_a: float,
) -> list[MapSample]:
    rows: list[MapSample] = []
    for pump in pumps:
        slots = [sweep.Block(material, pump, False) for _index in range(length)]
        single_pass_gain = 1.0 + material.gain_per_pu * min(material.max_pump, max(0.0, pump))
        for seed in seeds:
            result = solve_cavity_with_seed(slots, left_r, right_r, mirror_a, seed)
            rows.append(MapSample(
                material.key,
                length,
                seed,
                pump,
                single_pass_gain,
                result.sensor_right,
                result.sensor_left,
                result.max_port_power,
                result.total_port_power,
                result.burned_slots,
                result.ok,
            ))
    return rows


def write_csv(path: Path, rows: list[MapSample]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow([
            "material",
            "length",
            "seed_per_block",
            "pump_density",
            "single_pass_gain",
            "output_sp",
            "left_leak_sp",
            "max_port_power",
            "total_port_power",
            "burned_slots",
            "ok",
        ])
        for row in rows:
            writer.writerow([
                row.material_key,
                row.length,
                row.seed_per_block,
                row.pump_density,
                row.single_pass_gain,
                row.output_sp,
                row.left_leak_sp,
                row.max_port_power,
                row.total_port_power,
                " ".join(str(index) for index in row.burned_slots),
                row.ok,
            ])


def grid_for(rows: list[MapSample], material_key: str, seeds: list[float], pumps: list[float]) -> tuple[list[list[float]], list[list[bool]]]:
    index = {(row.seed_per_block, row.pump_density): row for row in rows if row.material_key == material_key}
    values: list[list[float]] = []
    burned: list[list[bool]] = []
    for pump in pumps:
        value_row = []
        burn_row = []
        for seed in seeds:
            row = index[(seed, pump)]
            value_row.append(row.output_sp if row.ok else math.nan)
            burn_row.append(bool(row.burned_slots))
        values.append(value_row)
        burned.append(burn_row)
    return values, burned


def contour_threshold(axis, seeds: list[float], pumps: list[float], values: list[list[float]], levels: list[float]) -> None:
    try:
        import numpy as np
    except Exception:
        return

    array = np.array(values, dtype=float)
    finite_levels = [level for level in levels if np.nanmin(array) < level < np.nanmax(array)]
    if finite_levels:
        contours = axis.contour(seeds, pumps, array, levels=finite_levels, colors="#f8f2d8", linewidths=0.65, alpha=0.85)
        axis.clabel(contours, inline=True, fontsize=6, fmt=lambda value: f"{value:.0f}")


def write_gain_sensitivity_png(path: Path, keys: tuple[str, ...]) -> None:
    import matplotlib.pyplot as plt

    path.parent.mkdir(parents=True, exist_ok=True)
    figure, (gain_axis, slope_axis) = plt.subplots(1, 2, figsize=(13.5, 5.3), dpi=170)
    figure.patch.set_facecolor("#0b1016")

    colors = plt.cm.tab10.colors
    max_pump = max(sweep.MATERIALS[key].max_pump for key in keys)
    for index, key in enumerate(keys):
        material = sweep.MATERIALS[key]
        xs = linear_values(material.max_pump, 96)
        ys = [1.0 + material.gain_per_pu * x for x in xs]
        color = colors[index % len(colors)]
        gain_axis.plot(xs, ys, color=color, linewidth=2.0, label=f"{key} k={material.gain_per_pu:g}")
        slope_axis.bar(index, material.gain_per_pu, color=color)

    for axis in (gain_axis, slope_axis):
        axis.set_facecolor("#101923")
        axis.tick_params(colors="#c7d3dc")
        axis.grid(True, color="#33424e", alpha=0.55)
        for spine in axis.spines.values():
            spine.set_color("#6c7a86")

    gain_axis.set_xlim(0.0, max_pump)
    gain_axis.set_title("Local single-pass gain G = 1 + k * PU", color="#dbe7ee")
    gain_axis.set_xlabel("actual PU density", color="#c7d3dc")
    gain_axis.set_ylabel("single-pass gain", color="#c7d3dc")
    legend = gain_axis.legend(facecolor="#101923", edgecolor="#6c7a86", fontsize=7)
    for text in legend.get_texts():
        text.set_color("#dbe7ee")

    slope_axis.set_title("Pump sensitivity k = dG / dPU", color="#dbe7ee")
    slope_axis.set_ylabel("gain increment per PU", color="#c7d3dc")
    slope_axis.set_xticks(range(len(keys)))
    slope_axis.set_xticklabels(keys, rotation=35, ha="right")

    figure.tight_layout()
    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)


def write_seed_pump_heatmaps_png(
        path: Path,
        rows: list[MapSample],
        keys: tuple[str, ...],
        length: int,
        seeds: list[float],
        pumps_by_material: dict[str, list[float]],
) -> None:
    import matplotlib.pyplot as plt
    import numpy as np
    from matplotlib.colors import LogNorm

    path.parent.mkdir(parents=True, exist_ok=True)
    cols = 3
    rows_count = math.ceil(len(keys) / cols)
    figure, axes = plt.subplots(
        rows_count,
        cols,
        figsize=(14.2, 4.15 * rows_count),
        dpi=165,
        squeeze=False,
        constrained_layout=True,
    )
    figure.patch.set_facecolor("#0b1016")

    positive_outputs = [row.output_sp for row in rows if row.ok and row.output_sp > 0.0]
    norm = LogNorm(vmin=max(1.0e-3, min(positive_outputs)), vmax=max(positive_outputs)) if positive_outputs else None

    for index, key in enumerate(keys):
        axis = axes[index // cols][index % cols]
        axis.set_facecolor("#101923")
        pumps = pumps_by_material[key]
        values, burned = grid_for(rows, key, seeds, pumps)
        image_values = np.array(values, dtype=float)
        image_values[image_values <= 0.0] = np.nan

        mesh = axis.pcolormesh(seeds, pumps, image_values, shading="auto", cmap="magma", norm=norm)
        contour_threshold(axis, seeds, pumps, values, [20.0, 100.0, 1000.0, 3000.0, 10000.0])

        burn_array = np.array(burned, dtype=float)
        if np.nanmax(burn_array) > 0.0:
            axis.contour(seeds, pumps, burn_array, levels=[0.5], colors="#82f7ff", linewidths=1.25)

        material = sweep.MATERIALS[key]
        title = (
            f"{key} len={length}  "
            f"k={material.gain_per_pu:g}, sat={material.saturation:g}, seed0={material.seed_per_exciter:g}"
        )
        axis.set_title(title, color="#dbe7ee", fontsize=8.5)
        axis.set_xscale("symlog", linthresh=0.01)
        axis.set_xlabel("seed SP per excited block", color="#c7d3dc")
        axis.set_ylabel("actual PU density", color="#c7d3dc")
        axis.tick_params(colors="#c7d3dc", labelsize=7)
        axis.grid(True, color="#33424e", alpha=0.35)
        for spine in axis.spines.values():
            spine.set_color("#6c7a86")

    for index in range(len(keys), rows_count * cols):
        axes[index // cols][index % cols].axis("off")

    colorbar = figure.colorbar(mesh, ax=axes.ravel().tolist(), fraction=0.018, pad=0.014)
    colorbar.set_label("right sensor coherent SP", color="#c7d3dc")
    colorbar.ax.tick_params(colors="#c7d3dc")
    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)


def write_seed_linearity_png(
        path: Path,
        keys: tuple[str, ...],
        length: int,
        seed_max: float,
        left_r: float,
        right_r: float,
        mirror_a: float,
) -> None:
    import matplotlib.pyplot as plt

    path.parent.mkdir(parents=True, exist_ok=True)
    figure, axes = plt.subplots(3, 3, figsize=(14.2, 11.8), dpi=165, squeeze=False)
    figure.patch.set_facecolor("#0b1016")
    seeds = logspace_positive(seed_max, 96)
    pump_fracs = (0.0, 0.1, 0.25, 1.0)
    colors = ("#9fb1bd", "#66d9ef", "#e5bd60", "#d66767")

    for index, key in enumerate(keys):
        axis = axes[index // 3][index % 3]
        axis.set_facecolor("#101923")
        material = sweep.MATERIALS[key]
        for pump_frac, color in zip(pump_fracs, colors):
            pump = material.max_pump * pump_frac
            slots = [sweep.Block(material, pump, False) for _slot in range(length)]
            values = [
                solve_cavity_with_seed(slots, left_r, right_r, mirror_a, seed).sensor_right
                for seed in seeds
            ]
            axis.plot(seeds, values, color=color, linewidth=1.8, label=f"{pump_frac:.0%} max pump")

        axis.axvline(material.seed_per_exciter, color="#f8f2d8", linestyle=":", linewidth=1.0, alpha=0.8)
        axis.set_xscale("log")
        axis.set_yscale("log")
        axis.set_title(f"{key} len={length}", color="#dbe7ee", fontsize=9)
        axis.set_xlabel("seed SP per block", color="#c7d3dc")
        axis.set_ylabel("output SP", color="#c7d3dc")
        axis.tick_params(colors="#c7d3dc", labelsize=7)
        axis.grid(True, color="#33424e", alpha=0.45)
        for spine in axis.spines.values():
            spine.set_color("#6c7a86")
        legend = axis.legend(facecolor="#101923", edgecolor="#6c7a86", fontsize=7)
        for text in legend.get_texts():
            text.set_color("#dbe7ee")

    figure.suptitle(
        "Seed strength remains an output multiplier before material saturation",
        color="#dbe7ee",
        y=0.995,
        fontsize=12,
    )
    figure.tight_layout(rect=(0, 0, 1, 0.975))
    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)


def write_marginal_pump_png(
        path: Path,
        rows: list[MapSample],
        keys: tuple[str, ...],
        seeds: list[float],
        pumps_by_material: dict[str, list[float]],
) -> None:
    import matplotlib.pyplot as plt
    import numpy as np
    from matplotlib.colors import SymLogNorm

    path.parent.mkdir(parents=True, exist_ok=True)
    cols = 3
    rows_count = math.ceil(len(keys) / cols)
    figure, axes = plt.subplots(
        rows_count,
        cols,
        figsize=(14.2, 4.15 * rows_count),
        dpi=165,
        squeeze=False,
        constrained_layout=True,
    )
    figure.patch.set_facecolor("#0b1016")

    derivative_grids: dict[str, np.ndarray] = {}
    maximum = 0.0
    for key in keys:
        pumps = pumps_by_material[key]
        values, _burned = grid_for(rows, key, seeds, pumps)
        array = np.array(values, dtype=float)
        derivative = np.gradient(array, np.array(pumps, dtype=float), axis=0)
        derivative_grids[key] = derivative
        finite = derivative[np.isfinite(derivative)]
        if finite.size:
            maximum = max(maximum, float(np.nanmax(np.abs(finite))))

    norm = SymLogNorm(linthresh=max(1.0e-3, maximum * 0.01), vmin=-maximum, vmax=maximum) if maximum > 0.0 else None
    mesh = None

    for index, key in enumerate(keys):
        axis = axes[index // cols][index % cols]
        axis.set_facecolor("#101923")
        pumps = pumps_by_material[key]
        mesh = axis.pcolormesh(seeds, pumps, derivative_grids[key], shading="auto", cmap="coolwarm", norm=norm)
        material = sweep.MATERIALS[key]
        axis.set_title(f"{key}: d(output)/dPU, k={material.gain_per_pu:g}", color="#dbe7ee", fontsize=8.5)
        axis.set_xscale("symlog", linthresh=0.01)
        axis.set_xlabel("seed SP per excited block", color="#c7d3dc")
        axis.set_ylabel("actual PU density", color="#c7d3dc")
        axis.tick_params(colors="#c7d3dc", labelsize=7)
        axis.grid(True, color="#33424e", alpha=0.35)
        for spine in axis.spines.values():
            spine.set_color("#6c7a86")

    for index in range(len(keys), rows_count * cols):
        axes[index // cols][index % cols].axis("off")

    if mesh is not None:
        colorbar = figure.colorbar(mesh, ax=axes.ravel().tolist(), fraction=0.018, pad=0.014)
        colorbar.set_label("marginal output SP per PU", color="#c7d3dc")
        colorbar.ax.tick_params(colors="#c7d3dc")

    figure.savefig(path, facecolor=figure.get_facecolor())
    plt.close(figure)


def print_summary(rows: list[MapSample], keys: tuple[str, ...], seeds: list[float], pumps_by_material: dict[str, list[float]]) -> None:
    for key in keys:
        material_rows = [row for row in rows if row.material_key == key]
        valid = [row for row in material_rows if row.ok]
        positive = [row.output_sp for row in valid if row.output_sp > 0.0]
        burned = sum(1 for row in valid if row.burned_slots)
        material = sweep.MATERIALS[key]
        print(
            f"{key:8s} k={material.gain_per_pu:.4g} maxPU={material.max_pump:g} "
            f"Gmax={1.0 + material.gain_per_pu * material.max_pump:.4g} "
            f"sat={material.saturation:g} seed0={material.seed_per_exciter:g} "
            f"out=[{min(positive) if positive else 0.0:.4g}, {max(positive) if positive else 0.0:.4g}] "
            f"burned={burned}/{len(valid)}"
        )


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate solid laser seed/pump response maps.")
    parser.add_argument("--materials", default=",".join(DEFAULT_MATERIALS))
    parser.add_argument("--length", type=int, default=6)
    parser.add_argument("--seed-max", type=float, default=120.0)
    parser.add_argument("--seed-steps", type=int, default=30)
    parser.add_argument("--pump-steps", type=int, default=32)
    parser.add_argument("--left-r", type=float, default=0.90)
    parser.add_argument("--right-r", type=float, default=0.90)
    parser.add_argument("--mirror-a", type=float, default=0.01)
    parser.add_argument("--out-dir", type=Path, default=Path("tmp"))
    args = parser.parse_args()

    keys = material_keys(args.materials)
    seeds = logspace_with_zero(args.seed_max, args.seed_steps)
    pumps_by_material = {
        key: linear_values(sweep.MATERIALS[key].max_pump, args.pump_steps)
        for key in keys
    }

    all_rows: list[MapSample] = []
    for key in keys:
        material = sweep.MATERIALS[key]
        all_rows.extend(sample_material_map(
            material,
            args.length,
            seeds,
            pumps_by_material[key],
            args.left_r,
            args.right_r,
            args.mirror_a,
        ))

    args.out_dir.mkdir(parents=True, exist_ok=True)
    prefix = f"solid_laser_seed_pump_len{args.length}"
    write_csv(args.out_dir / f"{prefix}.csv", all_rows)
    write_gain_sensitivity_png(args.out_dir / "solid_laser_material_local_gain_sensitivity.png", keys)
    write_seed_pump_heatmaps_png(
        args.out_dir / f"{prefix}_heatmaps.png",
        all_rows,
        keys,
        args.length,
        seeds,
        pumps_by_material,
    )
    write_marginal_pump_png(
        args.out_dir / f"{prefix}_marginal_pump.png",
        all_rows,
        keys,
        seeds,
        pumps_by_material,
    )
    write_seed_linearity_png(
        args.out_dir / f"{prefix}_seed_linearity.png",
        keys,
        args.length,
        args.seed_max,
        args.left_r,
        args.right_r,
        args.mirror_a,
    )
    print_summary(all_rows, keys, seeds, pumps_by_material)
    print(f"wrote outputs under {args.out_dir.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
