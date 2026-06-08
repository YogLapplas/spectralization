# Spectralization

Spectralization is a NeoForge 1.21 mod experiment about modern spectral industry:
light, channels, coatings, holographic storage, and large-scale aperture systems.

## Current Prototype

The current prototype is focused on the first optical runtime experiments:

- Basic optical parts are registered: lens, lens holder, mirror, beam splitter,
  creative light source, CMOS sensor, pass-through sensor, phosphor dust, and
  phosphor tube.
- Early laser materials are registered: ruby block, silver block, and silvered
  glass.
- Creative light sources emit simple single-frequency beams.
- Mirrors, beam splitters, glass-like materials, scattering fields, and sensors
  participate in the prototype beam path simulation.
- CMOS and pass-through sensors can receive cached optical outputs.
- Ruby blocks act as experimental solid-state laser media. Adjacent glowstone
  provides weak incoherent seed light, charged glowstone provides pump rate, and
  pumped ruby converts seed light into coherent ruby-line output with a
  soft-capped pump budget instead of multiplicative matrix gain.
- The experimental optical compiler builds port graphs, groups source graphs
  into optical systems, solves acyclic and feedback regions, emits readout-layer
  diagnostics, and writes timestamped debug logs when enabled.

This is still a prototype. The first-generation compiler is now the gameplay
path for current optical parts, while the older tracer remains useful as a
debug/reference path where it is still comparable.

## Development Notes

The optical debug log can be enabled in the common config or with the in-game
compiler debug command. Logs are written under:

```text
logs/spectralization/optical_compiler_<timestamp>_UTC.log
```

Useful validation lines:

```text
network_cache system_id=... structurally_fresh=... usable_for_gameplay=...
direct_templates=...
network_templates=...
receiver_outputs ...
system_receiver_outputs ...
```

Short version:

- `direct_templates` and `network_templates` show which component templates the
  compiler saw in the port graph.
- `receiver_outputs` compares one source's legacy trace with its direct graph.
- `system_receiver_outputs` compares all cached source traces in the optical
  system with the compiled network result.
- `usable_for_gameplay=true` means the compiled system is fresh, converged, and
  stable enough for cached readouts.

See [TODO.md](TODO.md) for the current compiler roadmap.

## Development

Build the mod with:

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```
