# Spot projection optimization closeout — 2026-07-16

## Status

The current spot-projection correctness and performance round is complete. The
implementation is being frozen so development can return to Spectralization's
gameplay systems. This is a release checkpoint, not a claim that projection has no
remaining optimization or rendering work.

Projection remains a visual/readout export layer. It does not participate in the
optical power solve and does not write power back into the port graph. The semantic
authority remains
[SPOT_PROJECTION_ALGORITHM.md](SPOT_PROJECTION_ALGORITHM.md); detailed measurements
remain in
[SPOT_PROJECTION_PERFORMANCE.md](SPOT_PROJECTION_PERFORMANCE.md).

## Accepted evidence

The final in-game evidence is the newest timestamp-matched pair:

- `diagnostics_20260716_174728_UTC.log`
- `optical_compiler_20260716_174728_UTC.log`

Both stress and lightweight parallel suites passed 90/90 cases and produced 225
steady full-rebuild profiles. Within both suites there were no fingerprint
mismatches, missing boundaries, structural mismatches, stale results, submission
rejections, worker failures, diagnostic write drops/failures, or compiler
overflow/fallback/non-convergence/instability/unreliability alarms. Stress reported
only warm-up drift between its early and late cycles.

| Load | Worker P50 | Worker P95 | Response P50 | Response P95 | Raw worker average |
| --- | ---: | ---: | ---: | ---: | ---: |
| Lightweight | 5.494 ms | 8.051 ms | 99.137 ms | 150.010 ms | 5.791 ms |
| Stress | 27.202 ms | 38.512 ms | 104.226 ms | 202.630 ms | 29.577 ms |

The response values include Minecraft tick scheduling, worker-wave dispatch, and
commit budgeting; they are not equivalent to projector core time. In the final
nine-source scenes, about 3,168 lightweight active quads rendered in roughly
1.1–1.3 ms/frame, while about 14,485 stress active quads rendered in roughly
5.2 ms/frame.

The final source tree is additionally protected by the Gradle continuity checks,
the formal projection proof, compilation, and the production build. The two
boundary fixes found during the final code audit postdate the in-game log pair:
effective worker-width clamping and chunk-change geometry invalidation. They do not
change the measured default 4-worker/8-in-flight path, so no new performance gain is
claimed for them.

## Completed in this round

### Projection semantics and correctness

- Preserved the separation between visual projection and optical gameplay power.
- Kept polygon subtraction as the final visibility authority.
- Added continuous analytic cuboid sweep coverage without longitudinal slice
  tessellation for ordinary axis-aligned cuboids.
- Preserved slanted cone silhouettes while removing the diagonal seam that appeared
  between edge-connected, same-depth cuboids.
- Made front ordering depth-wide across block boundaries, fixing the stair case in
  which a tread from another block at the same world Z failed to occlude a riser.
- Retained correct handling of full cubes, slabs, stairs, fences, and other
  axis-aligned multi-box voxel shapes through the cuboid path.
- Added formal checks for winding, clipping, front ordering, same-depth indices,
  cuboid and polygon sweeps, analytic side travel, remaining-region scans, and
  snapshot capture.

### Runtime performance

- Replaced repeated cuboid slices with one analytic sweep per exposed cuboid face.
- Added depth-local front and side indices, prefix/suffix remaining-region reuse,
  conservative pre-hull sweep bounds, and final-polygon-only rectangle clipping.
- Reused depth-scoped polygon workspaces and reduced temporary allocation in the
  hot clipping/subtraction paths.
- Split geometry and appearance caching and added exact equality fast paths for
  dependencies and owner publication.
- Added immutable copy-on-write section snapshots so projection workers do not read
  live `Level` state.
- Added a bounded projection executor, batched outgoing-node jobs, freshness and
  generation validation, atomic owner replacement, and bounded main-thread commit.
- Changed scheduler wave width and commit width to the executor's effective worker
  count, including legal `max_in_flight < workers` configurations.
- Invalidated affected owner geometry on chunk load/unload before scheduling a
  refresh, preventing stale geometry from entering an appearance-only rebuild.
- Raised the visible spot quota to 32,768 and kept transport/render diagnostics for
  quota and active-quad pressure.
- Moved diagnostics file I/O to a bounded ordered writer and separated deterministic
  commit assembly timing from diagnostics overhead.

### Test and diagnostic tooling

- Added the `spot_test` item and command-backed automatic scene generation.
- Added lightweight/stress load selection, directional suites, random seeds,
  1,000-case single-source measurement, and multi-source parallel suites.
- Added clearer chat progress, completion summaries, failure signatures, and simple
  performance reports.
- Added logs for snapshot reuse, worker/response/commit timing, wave width,
  freshness, structural signatures, missing faces, active quads, and diagnostic
  writer health.
- Documented the exact test procedures and field meanings in
  [TESTING_PLAN.md](TESTING_PLAN.md) and [LOGGING.md](LOGGING.md).

## Deliberately unfinished

The following items are deferred rather than accidentally omitted:

- Movable light sources and the update policy they require.
- Shared retained multi-source scene state, worker-side final assembly, update
  epochs, and delta publication.
- Client retained geometry, persistent GPU buffers, spatial buckets, and incremental
  owner updates. These become important when many thousands of spots are visible.
- A more general solution for internal longitudinal faces in model-based or
  overlapping multi-box shapes. The original stair cross-block front-order bug is
  fixed, but the broader limitation remains.
- Arbitrarily rotated or non-voxel obstruction meshes; the optimized authority is
  intentionally axis-aligned and voxel-native.
- Boundary feathering of internal cut edges and a complete visual model for
  intensity-dependent opacity/color. Existing projection geometry is ready to feed
  such rendering work, but it is not implemented here.
- A texture-region spatial tree beyond the current depth/prefix structures.
- Elimination of Minecraft's tick-quantized response steps. Core work can be faster
  without making every end-to-end response sub-tick.

The retained-scene and moving-source direction is designed, but intentionally not
implemented, in
[SPOT_PROJECTION_ARCHITECTURE_V2.md](SPOT_PROJECTION_ARCHITECTURE_V2.md).

## When to reopen projection work

Do not reopen this optimization round merely because a synthetic number can be made
smaller. Reopen it when one of these conditions is observed:

1. ordinary gameplay produces a repeatable server or client stall attributable to
   spot projection;
2. movable sources become the active gameplay milestone;
3. normal content regularly approaches the current high-active-quad rendering
   range;
4. an unresolved geometry case blocks a real machine, block, or visual feature;
5. freshness, structural, quota, or compiler diagnostics show a regression.

When work resumes, first reproduce the issue with the existing test item and record
one new timestamp-matched diagnostics/compiler pair. Any architecture change must
preserve the projection invariants and use the staged migration in the V2 document
rather than replacing the working system wholesale.

## Next project focus

Return to Spectralization itself: machines, blocks, optical gameplay, content, and
the player-facing loop. The next projection-specific milestone is movable light
sources, not another round of isolated serial micro-optimization.
