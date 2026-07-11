# Spectralization development tools

`tools/` contains generators, numerical probes, interactive design labs, and UI
layout fixtures. These files support development; they are not mod runtime
resources.

## Generators

- `generate_metamaterial_item_models.ps1`: synchronize generated metamaterial
  item layers and fallback models.
- `generate_metamaterial_layers.ps1`: generate the metamaterial texture layers
  and previews.
- `generate_spot_textures.ps1`: generate the spot core, halo, and ring textures.

## Numerical probes

- `laser_fe_frontier_optimizer.py`: explore the laser FE/PU design frontier.
- `laser_material_sweep.py`: sweep laser material combinations.
- `metamaterial_balance_probe.py`: inspect the bounded metamaterial design space.
- `solid_laser_seed_pump_maps.py`: inspect solid-laser seed and pump maps.

## Interactive labs

- `ruby_laser_lab.html`: ruby-laser and gain-scheduling lab.
- `metamaterial_texture_demo_regular.html`: regular metamaterial texture preview.
- `singular_material_texture_demo_unified.html`: canonical singular-material
  solver lab.
- `singular_material_texture_demo_unified_16x16.html`: canonical compact 16x16
  singular-material output lab.
- `spot_projection_area_demo.html`: texture-domain area and edge model lab.
- `spot_projection_scanline_demo.html`: scanline interval-allocation lab.
- `spectralization_ui_layout_editor_v2.html`: current machine UI layout editor.

## Layout fixtures

- `ui_layouts/photothermal_generator.json`
- `ui_layouts/thermal_smelter.json`

## Retention rules

- Keep one canonical lab plus a separately useful resolution or model variant.
  Do not retain a complete `v2`, `v3`, ... experiment ladder in the working tree;
  Git history is the archive.
- A superseded prototype should be deleted once its useful controls and model
  have been incorporated into the canonical lab.
- Generated images, exports, reports, and temporary comparison files do not
  belong in `tools/` unless they are deliberate checked-in fixtures.
- When adding or replacing a tool, update this file so its current role is
  explicit.
