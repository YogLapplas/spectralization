# Spectral Parameters UI

This document records the current player-facing spectral parameter layer. It is a maintenance note, not a player manual. Its job is to keep JEI, the spectrometer, material gain data, and spectrum colors aligned with the same ontology.

## Core Invariants

- `FrequencyKey` is the authority for region/bin identity.
- `SpectralColorMap` is the authority for display color and named visible bins.
- JEI charts, the creative light source editor, the spectrometer, stained glass profiles, coatings, and gain media must not carry private red/green/blue bin numbers.
- Frequency labels are intentionally coarse. The player should see `V-23`, `IR-18`, or a translated band name, not raw implementation details like "bin 23 of 32" as the primary concept.
- Numerical UI should compare materials visually first and reveal exact numbers on hover.

This means that a future change to visible bin ordering, region size, or non-visible color mapping must pass through `SpectralColorMap` and the architecture checks instead of being patched separately in JEI or GUI code.

## Gain Material JEI Category

The old JEI "Laser" category now represents gain media, not light sources.

Current category identity:

```text
RecipeType: spectralization:gain_material
Displayed title key: jei.spectralization.laser -> Gain Materials / 增益材料
Icon: ruby block
Entries: ruby, Ce/Nd/Yb/Er YAG, Ce/Nd/Yb/Er fluorite
```

Diode lasers and LEDs are sources, not gain materials. They should not appear in this category unless their gameplay role changes.

Each gain material page displays:

| Row | Meaning | Source |
| --- | --- | --- |
| Max Pump Response | Peak single-pass gain increase per PU at the material gain line | `OpticalMaterialProfiles.gainPerPumpUnitFor` |
| Saturation | Material-local saturated coherent extra output scale | `OpticalMaterialProfiles.saturationPowerFor` |
| Handling | Absorbed-power carrying limit used by overload/burning logic | `OpticalMaterialProfiles.handlingLimitFor` |
| Gain Curve | Frequency response LUT over infrared, visible, and ultraviolet | `OpticalMaterialProfiles.spectralGainPerPumpUnitFor` |

The three top rows use segmented bars for relative comparison. Exact values are tooltip-only. This keeps JEI useful for choosing a material while avoiding a wall of numbers.

## Gain Curves

Gain curves are LUT-backed. The current implementation builds a 3-region LUT:

```text
infrared:    32 bins
visible:     32 bins
ultraviolet: 32 bins
```

The plotted value is `gain per PU` at each frequency. The curve is drawn over a spectral background band using the shared display colors. The visible region is bounded by darker separators so players can distinguish infrared, visible, and ultraviolet at a glance.

The curve is smoothed in the material data with `smoothGainLut`, then interpolated in JEI with a clamped Catmull-Rom interpolation. The interpolation is only visual. Gameplay reads the LUT value through `spectralGainPerPumpUnitFor`.

Current gain-line intent:

| Material | Gain line | Peak gain/PU | Saturation SP | Handling SP | LUT half-width |
| --- | --- | ---: | ---: | ---: | ---: |
| Ruby | red visible line | 0.070 | 120 | 320 | 12 |
| Ce:YAG | lime visible line | 0.062 | 820 | 2600 | 4 |
| Nd:YAG | red visible line | 0.053 | 1550 | 4200 | 4 |
| Yb:YAG | blue visible line | 0.044 | 2700 | 6400 | 4 |
| Er:YAG | infrared line | 0.046 | 2100 | 6200 | 4 |
| Ce:CaF2 | lime visible line | 0.050 | 800 | 2800 | 3 |
| Nd:CaF2 | red visible line | 0.044 | 1400 | 3800 | 3 |
| Yb:CaF2 | blue visible line | 0.038 | 2300 | 6100 | 3 |
| Er:CaF2 | infrared line | 0.038 | 1900 | 5400 | 3 |

These values are gameplay parameters. They are allowed to deviate from real materials. The design goal is that materials differ by pump sensitivity, saturation, seed strength, spectral line, and overload tolerance.

## Spectrometer UI

The spectrometer now displays the selected region as a bar chart instead of a line chart. The bar chart is the correct shape because the optical compiler currently exposes discrete frequency bins, not a continuous sampled spectrum.

The block entity exposes scaled SP values through menu data:

```text
POWER_SCALE = 100
displayedSP = menuValue / POWER_SCALE
```

Data fields:

| Field | Meaning |
| --- | --- |
| `DATA_REGION` | Selected `SpectralRegion` ordinal |
| `DATA_RELIABLE` | Whether the current readout is gameplay-reliable |
| `DATA_TOTAL_POWER` | Total sampled power, scaled by `POWER_SCALE` |
| `DATA_PEAK_BIN` | Peak bin within the selected region |
| `DATA_REGION_POWER` | Total power inside the selected region, scaled by `POWER_SCALE` |
| `DATA_SPECTRUM_START + bin` | Per-bin power inside the selected region, scaled by `POWER_SCALE` |

The chart normalizes bar height by the maximum bin inside the selected region. Tooltips reveal absolute SP and the bin's share of the selected region and total spectrum.

## Light Source And Material Spectrum Alignment

The creative light source editor and environmental light spectra now use `SpectralColorMap.displayRgbFor` and named visible bins. This prevents the old failure mode where red, violet, infrared, and ultraviolet could be visually corrected in one screen but remain semantically reversed in material profiles or JEI.

Stained glass and metal/coating profiles should use named visible bins:

```text
VISIBLE_RED_BIN
VISIBLE_ORANGE_BIN
VISIBLE_YELLOW_BIN
VISIBLE_GREEN_BIN
VISIBLE_CYAN_BIN
VISIBLE_BLUE_BIN
VISIBLE_PURPLE_BIN
```

Do not write raw visible bin constants in new material code unless the value is deliberately not a named color center.

## Crystal Cultivator Rendering

The crystal cultivator model contains translucent parts. Its blockstate is split into multipart layers:

```text
crystal_cultivator_base: solid base
crystal_cultivator_body: translucent body
```

The split keeps the base stable while allowing the crystal body to render with `minecraft:translucent`. If the model is regenerated from Blockbench, preserve this split or verify that the single generated model still renders translucent geometry correctly in item and block form.

## Verification Checklist

Before committing changes that touch this layer:

```powershell
.\gradlew.bat check
.\gradlew.bat build
```

In game, verify:

- JEI has a "Gain Materials" category with a ruby icon.
- Each gain material page shows three segmented comparison rows and one spectral gain curve.
- Hovering the gain curve reports a band/frequency and exact gain per PU.
- The spectrometer uses the same color ordering as JEI and the creative light source editor.
- Red light is adjacent to infrared, violet light is adjacent to ultraviolet.
- Crystal cultivator translucent parts render without missing model or black fallback texture.
