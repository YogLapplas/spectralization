# Spectralization

Spectralization is a NeoForge 1.21 mod experiment about modern spectral industry:
light, channels, coatings, holographic storage, and large-scale aperture systems.

## Current Prototype

The current prototype is focused on the first optical runtime experiments:

- Breaking glowstone drops one extra amethyst shard as spectral residue.
- Basic optical parts are registered: lens, lens holder, mirror, beam splitter,
  creative light source, CMOS sensor, pass-through sensor, phosphor dust, and
  phosphor tube.
- Creative light sources emit simple single-frequency beams.
- Mirrors, beam splitters, glass-like materials, scattering fields, and sensors
  participate in the prototype beam path simulation.
- CMOS and pass-through sensors can receive cached optical outputs.
- The experimental optical compiler builds port graphs, groups source graphs
  into optical systems, emits readout-layer diagnostics, and writes timestamped
  debug logs when enabled.

This is still a prototype. Feedback systems are intentionally kept in debug mode
until the SCC/chord solver replaces the current fixed-point diagnostic solver.

## Development Notes

The optical debug log can be enabled in the common config or with the in-game
compiler debug command. Logs are written under:

```text
logs/spectralization/optical_compiler_<timestamp>_UTC.log
```

Useful validation lines:

```text
network_cache system_id=... structurally_fresh=... usable_for_gameplay=...
receiver_outputs ...
system_receiver_outputs ...
```

Short version:

- `receiver_outputs` compares one source's legacy trace with its direct graph.
- `system_receiver_outputs` compares all cached source traces in the optical
  system with the compiled network result.
- `usable_for_gameplay=true` currently only means a fresh, converged,
  no-feedback system.

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
