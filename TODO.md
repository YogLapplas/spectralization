# Spectralization TODO

## Optical Runtime Stabilization

- Keep legacy trace outputs as the gameplay source while the compiler is being
  validated.
- Use `system_receiver_outputs` as the main multi-source validation metric.
- Treat feedback systems as debug-only until the SCC/chord solver is in place.
- Promote no-feedback compiled systems to gameplay output only after repeated
  log validation.

## Compiler Roadmap

- Replace the fixed-point diagnostic solver with an SCC/chord solver for
  feedback components.
- Split optical dirty state into structure, parameter, source, field, and config
  epochs.
- Move from source-seeded system grouping toward an authoritative network-level
  cache.
- Cache path templates separately from transfer coefficients so parameter
  changes do not rebuild topology.
- Extend the scalar power solver toward profile-valued vectors by frequency bin.

## Gameplay Roadmap

- Keep the current `Emitter -> Mirror/Splitter -> Sensor` loop as the primary
  validation playground.
- Add a debug tool or block that reads beam profile data in-world.
- Start coating experiments as face-level optical parameters once the runtime is
  stable.
- Keep feedback/gain gameplay conservative until instability handling, heating,
  and damage policies are defined.

## Technical Hygiene

- Keep compiler debug logs timestamped per game launch.
- Keep field-source effects configurable.
- Avoid entity-heavy visualizations; use particles or cached client overlays.
- Do not commit local scratch data from `work/`.
