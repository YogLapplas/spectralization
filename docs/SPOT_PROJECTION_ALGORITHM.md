# Spot projection algorithm

This document is the authority note for projected light spots, large spot shadows,
side spots, and `VoxelSpotProjector` changes. Read it before changing spot
projection code.

The projection layer is a visual/readout export layer. It helps the player infer
where light would land. It does not drive the optical power solve, and it must not
write power back into the port graph.

## 1. Purpose

Large beams should still be readable in a block world. A player should be able to
place a wall, a lens, a screen, or a partial obstruction and understand the result
from the visible spot pattern.

The target is not full physical ray tracing. The target is a conservative
voxel-native projection model with these properties:

- The spot texture is the base object. Surfaces receive regions of that texture.
- Earlier surfaces remove regions from later surfaces.
- Geometry updates can be localized by dependency sets.
- Rendering cost stays bounded and mostly independent of beam power.
- The algorithm explains gameplay better than a hidden continuous renderer would.

## 2. Layer boundary

Spot projection belongs after the optical compiler:

```text
world event
-> port graph compile
-> power/profile solve
-> spot projection export
-> client render
```

It consumes solved outgoing power, coherent power, frequency components, beam
envelope, source position, and source direction. It exports `SpotRecord` values
and projection dependencies.

It must not:

- create or destroy gameplay power,
- decide receiver output,
- change topology,
- replace optical aperture or fiber coupling logic,
- turn a visual clipping result into a solver edge gain.

If aperture loss or divergence loss affects gameplay power, it belongs in the
optical/data layer. If it only changes what the player sees, it belongs here.

## 3. Mathematical objects

### 3.1 Texture domain

The canonical spot texture domain is:

```text
K = [0, 1] x [0, 1]
```

A light spot texture is not just an image. It is the common coordinate system used
to decide which portion of the spot belongs to which world face.

The current implementation mostly represents regions of `K` as axis-aligned
rectangles:

```text
CanonicalRect = [u0, u1] x [v0, v1]
```

This is why rectangular front-face projection is stable and cheap, and why exact
diagonal side silhouettes are not currently represented exactly.

### 3.2 World frame

For a projected beam:

- `sourcePos` is the block that emits the visible beam.
- `travelDirection` is the optical direction.
- `displayFace = travelDirection.getOpposite()` is the face on a receiving block
  that faces the source.
- `uDirection` and `vDirection` are the two transverse axes on `displayFace`.
- `depth` is integer travel distance from the source face.
- `du` and `dv` are integer transverse tile coordinates.

At each depth, the beam envelope gives a radius. The algorithm intersects the
cross-section of that radius with block-sized tiles.

### 3.3 Projection rectangle

For a front face at tile `(du, dv)`, the current algorithm computes:

```text
world tile square intersect beam cross-section
-> CanonicalRect in K
-> local face rectangle
-> SpotRecord.FOOTPRINT_SLICE
```

For side faces, the algorithm computes a swept side patch inside one block:

```text
entry cross-section at travel t0
exit cross-section at travel t1
-> side window in K
-> four local side vertices
-> SpotRecord.FOOTPRINT_QUAD
```

Side rendering is visual. Downstream occlusion is currently handled by parallel
occlusion planes, not by exact side polygons.

## 4. Invariants

### 4.1 Readout-only invariant

Projection output is readout. It may be beautiful, approximate, or disabled, but
it must not become gameplay authority.

### 4.2 Texture assignment invariant

For front surfaces, a region of `K` should be assigned at most once along a single
beam projection pass.

In current code this is implemented by carrying the remaining texture region:

```text
remaining_0 = K
visible = candidateRect intersect remaining_d
remaining_{d+1} = remaining_d - union(blockerRectsAtDepth)
```

### 4.3 Monotone occlusion invariant

The consumed set in texture domain only grows as the algorithm walks away from
the source. Runtime code stores this as the complementary `remaining` region.
Once a texture region is consumed by an opaque/projectable surface, later
surfaces should not receive that same region in the same projection pass.

### 4.4 Local continuity invariant

If two emitted patches share a world edge and both patches represent adjacent
pieces of the same spot texture, their texture coordinates should also be
continuous along that edge.

`SpotProjectionContinuity` checks this after export. It is a diagnostic tool, not
a gameplay authority path.

### 4.5 Brightness invariant

Patch alpha should depend on solved power, coherent power, frequency/color, and a
simple distance factor. It should not depend on how small a surface fragment is.

Small fragments must be allowed to render. A tiny corner of a block can still be a
valid piece of the spot.

### 4.6 Proof boundary

`SpotProjectionFormalProof` currently proves local side-patch orientation and
local clipping properties. It does not prove global area conservation, dependency
correctness, or player-facing shadow correctness.

Do not treat it as proof that the world projection is correct.

## 5. Current stable algorithm

The current stable implementation is in `VoxelSpotProjector.projectLightConeSpots`.
It is a depth-first voxel projection pass with rectangular texture-domain
occlusion.

### 5.1 Main pass

For each source output node, `CompiledSpotLayer` calls:

```text
VoxelSpotProjector.projectLightConeSpots(...)
```

The source profile is taken from the solved beam profile layer when available.
For mirrors and lens holders with a lens, the projected source radius is capped to
`0.5` block so the visual source has a block-sized aperture.

### 5.2 Depth scan

For each integer depth from `1` to `MAX_PROJECTED_DEPTH`:

1. Propagate the beam envelope to that depth.
2. Compute the projected radius and tile radius.
3. Scan transverse tiles `(du, dv)` that may intersect the beam.
4. Convert each intersecting front tile into a `ProjectionRect`.
5. Check the world block at that tile.
6. If the block is projectable, intersect its candidate window with the current
   remaining texture region.
7. Emit visible front patches.
8. Emit side visual patches for open side faces.
9. Collect the block's occlusion planes for the current depth.
10. Union the current depth blockers and subtract them from the remaining
    texture region.
11. Stop if the remaining texture domain is empty.

The implementation may keep a depth-local tile cache for the world block checks
performed in steps 3-8. This cache is not a gameplay authority and is discarded
after the depth slice. Its only purpose is to let side visual projection reuse
the front scan's `loaded/state/projectable` facts instead of asking the world for
the same tile again. The runtime cache should be a dense array over the current
side scan bounds, not a hash map, because side projection touches a compact
integer tile rectangle.

### 5.3 Projectable surfaces

A block is projectable when it is not air-like and one of these is true:

- its collision shape is a full block,
- it is an `OpticalElement`,
- it is an `OpticalSource`,
- it is a `LensHolderBlock` with an installed lens.

An empty lens holder is transparent for projection and should not create a new
visible spot.

### 5.4 Parallel occlusion planes

Each projectable block contributes several occlusion planes between its front and
back faces along the travel direction. The count is controlled by:

```text
spot_projection_occlusion_planes
```

The default is `5`.

These planes are an intentional approximation. They make a block behave more like
a volume without requiring exact polygonal swept-side clipping. Increasing the
plane count improves side-like shadow behavior but increases projection work.

The important point: these planes are occlusion-only. They are not extra rendered
surfaces.

### 5.5 Side spots

Side spots are emitted only on open side faces. They are generated from side
cross-sections of the same texture domain.

Current side spots:

- help players see that the beam intersects a side wall,
- use `FOOTPRINT_QUAD`,
- have a separate visual factor,
- are clipped by the current remaining texture region and same-depth front
  visual windows,
- do not currently act as the primary downstream shadow authority.

The downstream approximation is handled by parallel occlusion planes.

Do not name side visual windows as blockers unless they are actually inserted
into the downstream occupancy authority. Misleading names such as
`sideBlockersAtDepth` imply a false algorithm where side patches participate in
main occlusion. If side window diagnostics are needed, name them as visual or
debug windows.

### 5.6 Dependencies

The projection pass records block positions it inspected. These dependencies are
used so world changes in the projected cone can dirty the spot layer.

Dependency collection must include:

- blocks whose front faces may receive spots,
- blocks whose internal planes may occlude later faces,
- neighboring blocks checked for side openness.

The long-term target is local projection-tree updates, but the current stable
path still exports a dependency set from each projection pass.

## 6. Current render path

Server-side projection emits `SpotRecord` values.

Client-side rendering in `SpotRenderEvents`:

- sorts spots back-to-front,
- culls by render distance,
- renders core, halo, and ring textures,
- uses `FOOTPRINT_SLICE` for front rectangular slices,
- uses `FOOTPRINT_QUAD` for side/patch quads,
- uses tiny deterministic offsets to avoid z-fighting.

Render cost is currently much lower than projection generation cost. Recent logs
showed hundreds of quads rendering in roughly sub-millisecond CPU-side time, while
some wide projection scans took hundreds of milliseconds to generate.

## 7. Known limitations

### 7.1 Exact six-sided silhouettes are not solved

A real cube under oblique projection can create a five- or six-sided silhouette on
a later screen. The current rectangle-window model does not represent exact
diagonal polygon boundaries.

The accepted current simplification is the parallel-plane volume approximation.
It can create stepped side-like shadows and is stable inside the existing
rectangular subtraction model.

### 7.2 Side visual patches are not full occluders

Earlier experiments that made side faces equal to front faces in a unified
polygon/cell allocator caused fragmentation, asymmetry, and performance problems.
Do not revive that approach without a separate demo and a clear invariant proof.

### 7.3 Rectangular subtraction can grow many fragments

Subtracting many rectangles from many candidate rectangles can create many small
rectangular pieces. This is correct for the current representation, but it can
become a performance cost.

### 7.4 Diagnostics are limited

Continuity and formal proof tools are useful, but they do not replace in-game
visual validation. A proof that only checks patch winding does not prove global
shadow correctness.

## 8. Debug and diagnostics

Useful commands and logs:

```text
/spectralization compilerdebug on
/spectralization compilerdebug verbose on
/spectralization compilerdebug verbose off
/spectralization spotdebug centers on
/spectralization spotdebug centers off
/spectralization spotdebug planes
/spectralization spotdebug planes <count>
```

Relevant diagnostics:

- `subsystem=spot_projection event=profile`
- `subsystem=client_spot_render event=profile`
- `spot_projection_counts`
- `spot_projection_allocation_summary`
- `spot_projection_continuity`

`spot_projection/profile` is the lightweight performance log. It records tile,
surface, occlusion-plane, and rectangle-subtraction counts even when verbose
allocation tracing is off. Per-face `SpotProjectionAllocation` probes are heavy
and should be gated by `compilerdebug verbose`.

The profile line also includes phase timings and hot-spot counters:

- `*_us` timing fields split projection generation into tile range setup,
  rectangle creation, block lookup, projectability checks, occlusion-plane
  creation, front remaining-intersection queries, side scanning, side
  remaining-intersection queries, spot emission, and remaining-region updates.
- `subtract_*` fields measure rectangle subtraction work after candidate windows
  have been found. In the current stable path these mostly describe debug or
  same-depth clipping rather than the main historical occlusion authority.
- `remaining_*` fields measure the recursive texture-domain state:
  slab/interval counts, remaining area, query counts, blocker input count,
  blocker hits after clipping to the current remaining region, and update work.
  `remaining_slabs=0` is the exact early-stop condition.
- `side_*` fields measure side visual candidate enumeration. Side spots are still
  visual readout objects, but the counters show how many side tiles, open-face
  checks, travel intervals, and side texture windows were considered.
- `side_range_culled_tiles` counts side-scan tile positions skipped by the
  conservative `remaining`-bounds prefilter. This is a candidate-enumeration
  optimization only; accepted side windows still pass through
  `candidate intersect remaining`.
- `side_boundary_*` fields compare the stable side scan with an experimental
  boundary-based candidate enumerator. The stable renderer still uses the legacy
  side scan. `side_boundary_missing_faces` must be zero before the boundary
  enumerator can become authoritative. `side_boundary_extra_faces` is acceptable
  during superset validation and measures how much later filtering would remain.
- `side_candidate_verify_us` is the verbose-debug-only cost of the boundary
  candidate comparison. It must not be counted as production side-scan cost.
- `log_write_*_before` fields are rolling diagnostics-log write statistics
  sampled before the current profile line is written. They measure synchronous
  log-write cost without recursively writing a second timing event.
- `hot_depth_*` fields describe the single depth slice that took the most time
  in that projection pass.

When checking logs, inspect the newest diagnostics and optical compiler logs
first. Do not begin with global searches over all historical logs.

## 9. Optimization plan

The current algorithm should be faster than normal ray tracing because it works
with texture-domain regions and voxel tiles, not per-pixel or per-ray samples.
The main remaining costs are avoidable.

### 9.1 Optimize the light-cone tree first

The first optimization target is the light-cone tree, not rendering and not the
number of occlusion planes.

The conceptual tree is:

```text
source output
-> depth slice
-> candidate tile / face
-> texture-domain candidate window
-> visible assigned window
-> emitted spot patch
-> downstream remaining texture windows
```

Each node must know:

- which world positions and faces it inspected,
- which texture-domain region it owns or removes,
- which downstream nodes depend on that region,
- whether the node emitted a visible patch,
- whether the node can terminate a subtree because the texture domain is fully
  occupied or visually invisible.

This is the right first optimization because Minecraft updates are local. Placing
or breaking one block should invalidate the smallest affected cone subtree, not
force every source to rebuild every depth slice when the old tree is still valid.

The current stable implementation exports a flat dependency set from each
projection pass. The long-term optimized implementation should keep a structured
projection tree internally and derive the flat dependency set from that tree for
compatibility with the existing dirty system.

Do this before tuning plane count. More planes make single-pass shadows better,
but they also increase the amount of work per tree node. If the tree update is
wrong, extra planes only make the wrong computation larger.

### 9.2 Cull visually irrelevant sources earlier

Some logs show wide scans with thousands of dependencies and zero or very few
visible spots. Add an early visual threshold based on:

```text
power * visualDistanceFactor * color/alpha scale
```

If the result cannot be visible, skip projection for that source.

This must stay in the projection layer. It must not change optical readout power.

### 9.3 Use tighter tile ranges

The current depth loop scans a square around the beam. Even though it skips tiles
whose projection rectangle is null, it still iterates them.

For each depth, compute exact integer ranges for `du` and `dv` that can intersect
the radius. This is a simple geometric intersection problem and should reduce the
number of checked tiles for circular spots.

### 9.4 Adaptive plane count

The occlusion plane count should not always be fixed. A beam with almost constant
radius does not need many internal planes. A rapidly diverging beam may need more.

A deterministic rule can use:

```text
radiusDeltaInsideBlock
radiusRatioInsideBlock
distanceFromSource
configuredQualityCap
```

The result should be clamped by `spot_projection_occlusion_planes`.

### 9.5 Maintain the remaining region

The runtime authority is the remaining texture region, represented as
non-overlapping v-slabs containing non-overlapping u-intervals. Candidate faces
receive:

```text
visible = candidate intersect remaining
```

Current-depth occlusion planes are applied only after all faces in that depth
have read the old remaining region:

```text
remaining_{d+1} = remaining_d - union(blockers_d)
```

The runtime may compute this as an ordered sequence of blocker subtractions:

```text
remaining' = (((remaining_d - b_1) - b_2) ... - b_n)
```

This is exact because repeated set difference is equivalent to subtracting the
union. The important implementation rule is that the sequence must run after all
same-depth visible patches have been emitted, not during face scanning.

After each update, adjacent slabs with identical interval sets are merged. This
keeps the recursive certificate exact while avoiding historical occupied-window
queries.

This is safer than changing to polygon clipping because it preserves the current
rectangle-domain semantics.

The exact full-occupancy test must not be implemented by recomputing
`FULL_FOOTPRINT - occupiedHistory` at every depth. The stable invariant is the
recursive remaining certificate:

```text
remaining_0 = {K}
remaining_{d+1} = remaining_d - newOcclusionWindows_d
fully occupied <=> remaining_d is empty
```

This keeps the early-stop proof exact while charging each new occlusion window
only once.

### 9.6 Gate probe allocations

`SpotProjectionAllocation` records are valuable when diagnosing texture
assignment, but probe allocations can dominate debug logs. Split normal profiling
from verbose allocation tracing.

Recommended levels:

```text
profile: timings, counts, dependencies
summary: allocation result totals
verbose: per-face probes and clipping detail
```

The stable runtime should keep `profile` available under ordinary compiler debug
logging and reserve per-face allocation construction for verbose debug logging.

### 9.7 Cache empty or unchanged cones

Projection output can be cached by source, direction, envelope class, plane count,
and dependency epochs.

If the dependency set has not changed, reuse the previous projection result.
This fits the existing event-driven architecture better than per-frame tracing.

### 9.8 Stop when visual alpha is gone

The current projection horizon is fixed by `MAX_PROJECTED_DEPTH`. A beam whose
visual alpha has already faded below display threshold does not need later spot
projection.

This is a visual cutoff only.

### 9.9 Keep side rendering cheap

Side spots should remain a visualization aid. They should not force full polygon
CSG or per-cell ray casting in production.

Side candidate enumeration may use the current `remaining` region's texture-domain
bounding box to conservatively shrink the scanned tile range. This is allowed
because it only rejects side faces whose texture-domain footprint cannot receive
any remaining texture. It must not replace the exact assignment step:

```text
visibleSide = sideCandidate intersect remaining
```

An experimental boundary-based side candidate enumerator may be run in parallel
with the stable side scan for validation. In that mode, the stable side scan
continues to render. The boundary enumerator is only a candidate-source probe:

```text
legacyCandidates = side faces that produced a stable sideWindow
boundaryCandidates = side faces whose grid boundary is swept by the cone
missing = legacyCandidates - boundaryCandidates
extra = boundaryCandidates - legacyCandidates
```

The first invariant is `missing = 0`. The boundary set is allowed to be a
superset while it is still being tightened.

If side shadows need improvement, prefer:

```text
more/better chosen parallel occlusion planes
-> adaptive plane placement
-> local rectangle-window merging
```

over:

```text
full side polygon clipping
per-cell ray casts
one render quad per texture cell
```

## 10. Change rules

Before changing `VoxelSpotProjector`, answer:

1. Which object is being assigned: texture region, world face, or render quad?
2. Does this change affect visual export only, or gameplay optical power?
3. Which invariant above protects the change?
4. Which dependency set changes when a block is placed or broken?
5. Which diagnostic will show the result?

Do not make large spot projection changes by copying ordinary ray tracing,
triangle tessellation, or full-resolution image sampling patterns into the
runtime path. Build a small demo first when the representation changes.
