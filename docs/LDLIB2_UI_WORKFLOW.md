# LDLib2 UI Workflow

This document records how Spectralization uses LDLib2 UI templates during development.

AI agents must read [AI_UI_DESIGN_SPEC.md](AI_UI_DESIGN_SPEC.md) before designing or rewriting a machine UI. This workflow explains the LDLib2 template mechanics; the AI UI spec defines the state contract, low-text rules, pending behavior, and audit checklist.

## Thermal Smelter

The thermal smelter can load a visual LDLib2 UI template before falling back to the code-built layout.

Editable template file:

```text
D:\Release 2.3.0\sins&tec\.minecraft\versions\1.21.1-NeoForge_21.1.233\ldlib2\assets\ldlib2\resources\global\thermal_smelter.ui.nbt
```

LDLib2 stores UI Template resources as `.ui.nbt` files.

The file is created lazily when the thermal smelter UI is opened, or with this command:

```mcfunction
/spectralization ldlib2ui thermal_smelter starter
```

Do not create or load `UITemplate` resources during mod construction or menu screen registration. LDLib2's UI template classes require client font state that is not ready that early.

## Official LDLib2 Examples

LDLib2 ships official editor examples inside its own jar:

```text
assets/ldlib2/resources/examples/example_layout.ui.nbt
assets/ldlib2/resources/examples/button.ui.nbt
```

Spectralization installs local editable copies during LDLib2 plugin loading and with this command:

```mcfunction
/spectralization ldlib2ui examples install
```

Installed resource paths:

```text
.minecraft/ldlib2/assets/ldlib2/resources/examples/example_layout.ui.nbt
.minecraft/ldlib2/assets/ldlib2/resources/examples/button.ui.nbt
.minecraft/ldlib2/assets/ldlib2/resources/global/official_example_layout.ui.nbt
.minecraft/ldlib2/assets/ldlib2/resources/global/official_button.ui.nbt
```

The `examples` copies preserve LDLib2's official resource path. The `global` copies are there because the editor's resource panel is easiest to use from the global file provider.

Spectralization also installs LDLib2's built-in stylesheet files:

```text
.minecraft/ldlib2/assets/ldlib2/lss/gdp.lss
.minecraft/ldlib2/assets/ldlib2/lss/mc.lss
.minecraft/ldlib2/assets/ldlib2/lss/modern.lss
```

Open the LDLib2 editor in a single-player world:

```mcfunction
/ldlib2_ui_editor
```

Then open the UI resource category and look for the `global` provider. These templates should be visible:

```text
thermal_smelter
official_example_layout
official_button
```

The Java side loads this file and attaches runtime behavior by element id.

## Minecraft Machine UI Rule

The default state of a Minecraft machine UI should contain almost no text.

Allowed by default:

- The machine name at the top.
- Item stacks.
- Icons.
- Slots.
- Progress bars, heat bars, energy bars, spectrum bars, and other visual meters.

Avoid by default:

- Inline numeric readouts.
- Explanatory labels.
- Long button text.
- Text blocks that explain what the machine does.

Precise data should appear in tooltips, debug views, Jade, or deliberate inspection panels.

## Required Element IDs

Keep these ids stable if the template defines the corresponding element:

```text
slot_input
slot_additive
slot_output
player_inventory
progress_bar
heat_bar
optical_bar
```

The three machine slots must be LDLib2 `ItemSlot` elements. `player_inventory` should be an `InventorySlots` element.

These label ids are optional. If you add them, the Java bridge will bind live text to them, but the starter template does not include them because normal machine UI should be icon-first:

```text
temperature_label
heat_label
optical_label
progress_label
```

If a required slot or inventory element is missing, the Java bridge adds a fallback functional element at the legacy audited position. This keeps the machine usable while a template is unfinished, but the fallback may not match the visual design.

## Slot Rule

For LDLib2-backed UIs, the visible `ItemSlot` element is also the menu click slot source. Do not draw decorative slot art separately unless the actual `ItemSlot` shares the same rectangle.

## Current Audited Fallback Rects

```text
slot_input      x=16  y=23   w=18   h=18
slot_additive   x=16  y=45   w=18   h=18
slot_output     x=213 y=34   w=18   h=18
player_inventory x=49 y=125  w=162  h=76
heat_bar        x=38  y=18   w=8    h=68
optical_bar     x=78  y=20   w=48   h=6
progress_bar    x=104 y=48   w=48   h=8
```
