# Spot projection algorithm

This document is the authority note for projected light spots, large spot shadows,
side spots, and `VoxelSpotProjector` changes. Read it before changing spot
projection code.

The implementation and performance checkpoint for the current branch is
[SPOT_PROJECTION_CHECKPOINT_2026-07-11.md](SPOT_PROJECTION_CHECKPOINT_2026-07-11.md).
It records the test entry points, measured baseline, cache state, and the next
single-cuboid sweep milestone followed by depth-suffix invalidation. This document
remains the semantic authority.

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

Side rendering is visual. Downstream occlusion is handled by conservative cuboid
sweeps, not by exact side polygons.

### 3.4 Projection surface descriptor

Future non-full-block support should enter through a projection-surface
descriptor, not through scattered special cases in the depth loop.

The descriptor should answer these questions:

```text
ProjectionSurfaceDescriptor:
  source face origin / offset
  receiving visual faces
  optical collision boxes
  dependency positions
  surface-to-texture mapping rule
```

This is the extension point for:

- source faces that can move parallel to their block face,
- source faces that do not share the block-center origin,
- slabs, stairs, fences, panes, and other partial blocks,
- optical blocks whose visual aperture differs from their Minecraft collision
  shape.

The descriptor belongs to the projection layer unless it changes gameplay power.
If a partial block changes aperture loss or coupling power, that part belongs in
the optical/data layer and the projection descriptor should only mirror the
readout geometry.

The first optical collision box implementation represents selected vanilla
partial blocks as a finite list of local axis-aligned boxes:

```text
OpticalCollisionBox:
  minTravel, maxTravel
  minU, maxU
  minV, maxV
```

For slabs, stairs, and fences, these boxes are derived from the block state's
model/outline `VoxelShape`. This makes an isolated fence post and a connected
fence produce different projection geometry without hard-coding each connection
case. Existing mod optical elements, optical sources, full blocks, and lens
holders with lenses retain the historical full-block projection box unless a
separate descriptor is added for that block.

The derived model boxes are immutable for the selected vanilla block-state
families. Runtime caches their transformed, sorted box list by immutable
`BlockState` and `(travel,u,v)` frame. Cache hits must return the same cuboid order
and coordinates as a fresh `VoxelShape.toAabbs()` conversion. The depth tile scan
also computes its target `BlockPos` directly from the depth origin and frame steps,
avoiding intermediate relative-position objects without changing dependency keys.

The first correctness target for partial geometry is a single arbitrary
axis-aligned cuboid contained in one Minecraft block. Cross-block cuboids may be
split at block boundaries, and stairs/fences should be treated later as unions of
this primitive. The cuboid is the projection primitive; a full block is only the
special cuboid `[0,1] x [0,1] x [0,1]`.

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

`SpotProjectionFormalProof` currently proves local side-patch orientation,
local clipping properties, cuboid-sweep prefix properties, and retains the old
four-way plane classification as a migration regression. It does not prove the
complete multi-event cursor transaction, global area conservation, dependency
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
6. If the block is projectable, collect its cuboid front candidates and construct
   exactly one `CuboidSweep` for each optical cuboid.
7. Sort the front candidates by cuboid-local `minTravel`; index the sweeps by
   start travel and their cached full canonical hull.
8. For each coplanar front group, subtract `prefixHull(groupTravel)` for volumes
   that started earlier, emit every face in the group from the same incoming
   region, then allow volumes starting at that travel to affect later groups.
9. Emit side visual patches for open side faces.
10. Union one cached `fullHull()` per cuboid and subtract those blockers from the
    global remaining texture region.
11. Stop if the remaining texture domain is empty.

Projection geometry prepares one radius evaluator from the source envelope and
uses absolute `depth + localTravel` coordinates throughout the cone. Per-depth
envelopes remain available for appearance, but must not be re-prepared as the
geometry authority. In particular, the back plane of depth `d` and the front
plane of depth `d + 1` share the exact evaluator call at `d + 1`; edge-touching
blocks must not acquire a canonical light sliver from independently reconstructed
beam moments or divergence floors.

The implementation may keep a depth-local tile cache for the world block checks
performed in steps 3-8. This cache is not a gameplay authority and is discarded
after the depth slice. Its only purpose is to let side visual projection reuse
the front scan's `loaded/state/projectable` facts instead of asking the world for
the same tile again. The runtime cache should be a dense array over the current
side scan bounds, not a hash map, because side projection touches a compact
integer tile rectangle.

The front and side passes may share a unified depth scan over
`frontBounds union sideBounds`. This scan records dependencies for all tiles that
could affect front or side projection and fills the depth-local tile cache. Side
projection should then enumerate exposed grid boundaries from that cached slice,
not blindly revisit every projectable tile face. This is just a reordering of the
side predicate:

```text
sideVisible = inSideBounds && projectable && openFace && coneSweepsFace && remainingIntersects
```

The runtime side candidate source is:

```text
projectable tile in the front/side scan range
-> exposed side boundary
-> source-side receiving boundary
-> cone sweep interval exists
-> side window intersect remaining
```

Filtering `projectable` and `openFace` before the side travel calculation
preserves the visible set while avoiding repeated work in air and hidden interior
faces.

For model-based multi-box blocks, the side candidate range must include the
ordinary front scan bounds as well as the old side boundary bounds. Internal
x/z or y/z faces live inside tiles that may already be in the front scan and are
not necessarily on the cone's outer side boundary. Treating slabs, stairs, and
fences as a set of smaller cuboids requires enumerating those cuboid side faces
wherever the cuboid itself was a projected tile candidate.

Projectable tiles that are only in the side scan range still contribute their
cuboid sweeps for the current depth. A side-visible cuboid can block
another side or internal face even when its front face is not an ordinary
front-tile visual candidate at that exact depth.

### 5.3 Projectable surfaces

A block is projectable when it is not air-like and one of these is true:

- its collision shape is a full block,
- it is an `OpticalElement`,
- it is an `OpticalSource`,
- it is a `LensHolderBlock` with an installed lens,
- it is a slab, stair, or fence block whose model/outline shape produces at
  least one optical collision box.

An empty lens holder is transparent for projection and should not create a new
visible spot.

For a block with several optical collision boxes, the boxes are sorted by
`minTravel` in the beam frame. Surface exposure is still resolved as a union of
the block's boxes, so internal decomposition contacts do not become receiving
faces. Optical ordering, however, is depth-wide: once a prefix from an earlier
volume becomes active, it may clip a later receiving face in either the
same block or a different block. Block ownership is diagnostic identity, not an
occlusion partition.

The cuboid is now the projection primitive. A visible receiving face is not
automatically the whole face of the cuboid; it is the cuboid face after sibling
cuboids in the same block have removed their contact rectangles. This applies to
front faces and side faces. The full cuboid sweep is used for depth-local and
downstream remaining, because the solid volume
exists even where its receiving face is covered by another cuboid. In short:

```text
visible face = cuboid face - same-block sibling contacts
occluding body = full cuboid volume
```

This distinction is what makes slabs, stairs, fences, and future model-derived
partial blocks behave as a union of axis-aligned cuboids while keeping a complete
block equal to the special cuboid `[0,1] x [0,1] x [0,1]`.

For a single non-full cuboid, its front plane becomes active only after its
coplanar front receiving face has read the incoming region. Side clipping also
excludes the receiving cuboid's own planes. These two rules prevent a cuboid from
erasing its own front or side surface while still allowing its volume to block
surfaces farther along the beam.

#### Depth-wide front ordering through cuboid prefixes

Front-face ordering is global across one Minecraft depth slice. The depth scan
first collects every cuboid front candidate without emitting it. After the scan,
the candidates are sorted by beam-frame `minTravel` and processed as coplanar
travel groups. This replaces the earlier block-local `tileRemainingRayWindows`
path.

Grouping only the visible cuboid front rectangles is insufficient. A ray may
enter an earlier cuboid through its side as the beam envelope changes inside the
depth slice, so a continuous cuboid prefix can cover a later front receiver even
when its minimum-travel front rectangle did not. For a front group at travel
`tau`, the event order is:

```text
remaining at integer depth d
-> collect every cuboid front candidate in that depth
-> construct one sweep for every cuboid in that depth
-> for front travel tau, subtract prefixHull(tau) for sweeps with start < tau
-> every coplanar face in group tau reads the same incoming region
-> sweeps starting at tau become eligible for later groups
-> continue with the next travel group
-> publish the union of fullHull() values as remaining at depth d + 1
```

The start-travel distinction is essential. A volume that started earlier is
already occupied at `tau` and must block the front group. A volume whose front is
coplanar with the group becomes active only after all coplanar receivers have read
the region.

The transaction boundary is the whole depth slice, not one block. Sorting must
use the beam-frame `minTravel` carried by each cuboid. World block position may
identify ownership and dependencies, but it must not partition occlusion
authority. A full block remains the special case whose only front travel is
zero, so coplanar full-block faces still read the same depth-entry region. The
original stair-row A/B scene has confirmed this repair: a tread in a different
stair block now clips the internal riser, while removing that tread restores the
visible patch.

### 5.4 Single-cuboid sweeps

Each optical collision box contributes exactly one immutable `CuboidSweep` over
its local travel interval and local `u/v` footprint:

```text
CuboidSweep:
  cached fullHull()
  prefixHull(travel)
```

`fullHull()` is constructed once during the depth scan and is the downstream
shadow authority. It conservatively bounds every continuous cross-section of the
cuboid, including a beam waist inside the interval. `prefixHull(travel)` uses the
same evaluator and supplies only the occupied prefix before a same-depth
receiver.

The same-depth index bins each sweep by its cached full hull and sorts candidates
by start travel. A side query visits only intersecting bins, excludes the
receiving cuboid, and materializes prefix hulls for the surviving local set. The
index also exposes start, end, and in-range waist boundaries for side travel
segmentation. This avoids the rejected candidate-by-all-cuboids adaptive scan.

`spot_projection_occlusion_planes` remains a compatibility/debug configuration
during migration, but it no longer changes production occlusion authority.

### 5.5 Side spots

Side spots are emitted only on open side faces. They are generated from side
cross-sections of the same texture domain.

Current side spots:

- help players see that the beam intersects a side wall,
- enumerate exposed side faces from optical collision boxes when a block has a
  model-based projection shape,
- use `FOOTPRINT_QUAD`,
- have a separate visual factor,
- are clipped by the current remaining texture region and owner-aware same-depth
  occlusion windows,
- do not currently act as the primary downstream shadow authority.

For model-based multi-box blocks, such as stairs and fences, side spots are
clipped by same-depth occlusion with identity and travel order. A stair tread or
riser is a real receiving face inside the block's unit cube; the candidate
cuboid's own volume must not erase its side face, but earlier sibling cuboids and
other blocks at the same depth may occlude it.

A side patch segment reads:

```text
incoming = previous_depth_remaining
incoming -= indexed_sweep_prefixes_before(segment_end)
incoming excludes only the receiving cuboid itself
visible = side_window intersect incoming
```

The segment-end prefix conservatively contains every earlier prefix inside that
segment. Sweep start/end/waist boundaries and the existing side subdivision keep
the interval local; using the midpoint would permit false light on the latter
half of a changing prefix.

In verbose mode, internal side allocation records include an `occlusion_audit`
field. It compares the cuboid probes collected in the depth slice against the
indexed sweep prefixes that reached the side clipper. Compatibility field names
such as `sampled_hits` and `sample_gaps` are retained temporarily; under sweep
authority a nonzero `sample_gaps` value is an index/query correctness alarm.

When two boxes in the same block touch a side plane, that contact is resolved as
a rectangle difference on the side face itself before the optical-order clipping
above. A sibling box may cover part of an internal side face, but partial contact
must not delete the whole face.
Internal side faces whose fixed local coordinate lies inside the unit cube are
not automatically accepted merely because they are internal. Every cuboid face
carries its real polarity: the negative face is the cuboid's own minimum on the
fixed axis, and the positive face is its own maximum. This identity must not be
reconstructed from `fixedLocal < 0.5`; both faces of an offset cuboid may lie on
the same side of the block-local midpoint.

For each travel segment, the beam radius change and the face polarity determine
whether rays cross that face into the cuboid. Only this entrance face may enter
texture-domain allocation. The opposite longitudinal face is an exit/back face
and is rejected before canonical-window construction, clipping, or quad
emission. A constant-radius segment has no longitudinal entrance face. This
rule is identical for full-block exterior faces and model-derived internal
faces; internal/exterior status affects geometric reachability and diagnostics,
not illumination authority.

After entrance classification, the face is reduced to the part actually
reachable from the source side. Same-block sibling contacts remove coplanar
pieces, and same-block solid volume that covers the same source-side ray removes
the travel/cross region behind it. The remaining internal x/z or y/z surface is
a real receiving surface and proceeds through the ordinary texture-domain
difference pipeline.

For these model-derived side faces, `Direction` is only the face normal. It must
not be used as the plane position. A side patch on an internal cuboid face must
validate against the real fixed block-local axis coordinate of that face, such as
`x = 0.5` or `y = 0.25`, not against the full-block exterior plane `0` or `1`.
The side patch emitter therefore carries both the normal direction and the
fixed-axis coordinate.

Runtime side candidates carry a `ProjectionFace`-like descriptor. It records the
face normal, its cuboid-relative minimum/maximum polarity, the real fixed local
coordinate, and the travel/cross interval of the receiving face. This descriptor
is the service-side geometry authority for a side patch; scattered values such
as `Direction`, `fixedLocal < 0.5`, or `opticalBoxes.size()` must not be used to
infer where the face lies.

Side travel should be adaptively subdivided when the beam radius changes. A
single endpoint rectangle is not a safe approximation for a side face when the
beam converges, diverges, or has a waist inside the cuboid.

The downstream conservative approximation is handled by one full cuboid sweep.

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

When a projectable block is broken, the first safe visual action is to remove any
already-published spot records whose `pos` is that block. This removes the
impossible floating surface immediately. It does not pretend that downstream
shadows have been recomputed; downstream projection still follows the normal
dirty/recompile path.

The long-term target is local projection-tree updates, but the current stable
path still exports a dependency set from each projection pass.

## 6. Current render path

Server-side projection emits `SpotRecord` values.

Client-side rendering in `SpotRenderEvents`:

- iterates the cache's stable order directly while the client render budget is not
  exceeded,
- if the budget is exceeded, culls by distance first and keeps the nearest spots,
- culls by render distance,
- renders core, halo, and ring textures through an additive light pass,
- uses `FOOTPRINT_SLICE` for front rectangular slices,
- uses `FOOTPRINT_QUAD` for side/patch quads,
- uses one fixed surface offset per receiving face.

The texture sampler uses linear filtering without mipmaps. Core, halo, and ring
alpha are zero-origin power curves: alpha level zero is invisible, and low positive
levels no longer inherit a fixed opaque floor. Generated spot textures also reach
zero support at their outer envelope. This feathers the projected beam envelope;
it does not yet feather internal occlusion cut edges, which require explicit
boundary-mask data rather than a different sampler.

Client-side spot cache merging should not pre-compose contributions during
appearance-layer validation. Owner snapshots still use `SpotKey` to replace an
owner's own records, but records from different owners are kept as separate light
contributions. If future code reintroduces an exact duplicate merge, the key must
include world geometry, texture-domain mapping, texture kind, and color function,
and the merge must accumulate linear light energy without alpha-over
composition.

Overlapping spots are emitted-light contributions, not stacked translucent
paint layers. Each contribution keeps its own UV mapping, and the render pass
uses additive blending so fragment output is accumulated as light intensity. The
result should not depend on owner order, spot order, or small camera-distance
sorting changes inside one physical face.

The earlier incomplete-quad flat display experiment was rejected and removed. A
`FOOTPRINT_QUAD` must always use its own four texture coordinates. Sampling the
center of the assigned texture-domain quad for all four vertices changes the
display rule and hides the actual texture mapping problem instead of solving
overlap.

Spot overlays are owner snapshots, not loose quad streams. When an owner's
snapshot changes, players near either the old snapshot or the new snapshot must
receive the replacement payload; otherwise old quads can remain on the client and
produce false color-overlap artifacts. When a source/network owner is retired,
the server must send an empty spot overlay for that owner, just as HUD beam
owners are cleared.

Render cost is currently much lower than projection generation cost. Recent logs
showed hundreds of quads rendering in roughly sub-millisecond CPU-side time, while
some wide projection scans took hundreds of milliseconds to generate.

## 7. Known limitations

### 7.1 Exact six-sided silhouettes are not solved

A real cube under oblique projection can create a five- or six-sided silhouette on
a later screen. The current rectangle-window model does not represent exact
diagonal polygon boundaries.

The current implementation uses a conservative axis-aligned sweep hull. It
preserves cuboid connectivity but can add bounded extra shadow at correlated
`u/v` corners; it still does not represent an exact diagonal polygon silhouette.

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

### 7.5 Unresolved: internal longitudinal faces of model-based blocks

Model-based optical collision boxes are still not fully equivalent to treating a
partial block as a set of normal cuboids.

The known failure case is an internal side face parallel to the beam direction,
for example an x/z or y/z face inside a stair when the beam travels along z. The
same physical model feature can receive a spot when rotated into the transverse
front plane, so front-face projection is working, but longitudinal side-face
projection is still incomplete.

Do not treat the current slab, stair, and fence path as solved. The intended
invariant is:

```text
partial block shape
  -> decompose into axis-aligned optical boxes
  -> expose each visible cuboid face as a receiving surface
  -> allocate texture regions with the same area and continuity rules as full
     blocks
```

Future fixes should log the following stages independently:

```text
face candidate collected
side travel interval generated
side window survives remaining-region clipping
FOOTPRINT_QUAD emitted
client render receives the patch
```

This bug should stay documented until an in-game test confirms that internal
x/z and y/z faces receive spots continuously and without floating patches.

### 7.6 Implemented, awaiting in-game confirmation: edge-connected cuboids

Two axis-aligned cuboids that share only one world edge can still produce a thin
light seam when their occlusion is represented by independent parallel planes.
The failure is easiest to expose when the shared edge has the same world z and
the cuboids occupy successive travel ranges. Increasing floating-point precision,
welding final render quads, or rebuilding adjacent depth radii from one evaluator
does not solve it. Those changes operate after, or below, the real loss of
topology:

```text
continuous cuboid volume
  -> finite isolated cross-sections
  -> union of sampled canonical rectangles
  -> a gap may remain between samples
```

Production now uses one continuous conservative sweep per optical cuboid:

```text
CuboidSweep:
  fullHull()
  prefixHull(travel)
```

- `fullHull()` is the downstream blocker for the cuboid's complete travel range.
- `prefixHull(travel)` supplies same-depth front/side ordering without activating
  volume that lies after the receiver.
- One cuboid produces one sweep object. Intermediate planes are not production
  occlusion authority and are not required for correctness.
- The hull is conservative: false light gaps are forbidden, while bounded extra
  shadow at correlated u/v corners is accepted for this voxel-native readout.
- Sweep construction occurs once per cuboid and must be indexed/reused. The
  rejected July 10 experiment recomputed continuous prefixes per side candidate;
  that candidate-by-cuboid multiplication must not return.

The formal verifier checks prefix monotonicity, containment of continuous sampled
sections, front-before-activation, owner exclusion, indexed/serial equivalence,
and canonical connectivity for a pair of edge-connected cuboids. The original
in-game same-world-z reproduction must still be rerun before this limitation is
marked fully closed.

## 8. Debug and diagnostics

Useful commands and logs:

```text
/spectralization compilerdebug on
/spectralization compilerdebug verbose on
/spectralization compilerdebug verbose off
/spectralization spotdebug centers on
/spectralization spotdebug centers off
/spectralization spotdebug colors on
/spectralization spotdebug colors off
/spectralization spotdebug planes
/spectralization spotdebug planes <count>
/spectralization spottest random
/spectralization spottest random <seed>
/spectralization spottest rerun
/spectralization spottest report [count]
/spectralization spottest benchmark [count]
/spectralization spottest clear
/spectralization spotperf report
/spectralization spotperf report <count>
/spectralization spotperf reset
```

The `spotdebug planes` command is retained for configuration and old comparison
workflows. Its value no longer changes production cuboid-sweep occlusion.

The creative inventory also contains the operator-only `spot_test` instrument.
Right-click starts its selected automatic suite or reports the current case;
Shift-right-click cycles through quick, partial-geometry, performance,
direction-matrix, and anonymous 1,000-scene random-stress modes. The selected mode is stored on the item,
while the active run is
server-owned and keyed by player UUID. Only one automatic suite may hold the
global debug-configuration lease at a time. Ordinary cases build a deterministic
scattered-light scene, gather source-bound samples, and write `suite_started`,
`case_started`, `case_complete`, and `suite_complete` diagnostics, and restores
the previous compiler/spot debug settings on completion or failure. The item has
no recipe and requires operator permission level 2 because it clears and rebuilds
a reserved world volume.

The direction matrix begins with four unreported 16-sample warmup cases, one for
each horizontal direction, then runs four
different deterministic seeds through all four horizontal travel directions.
Its four direction orders are balanced, so every direction occupies the first,
second, third, and fourth execution position once. Random stair facings are
generated relative to beam forward/right/back/left, not from absolute world
directions. Before every case, the union of all four rotated test volumes is
cleared. The terminal screen and cleared transverse/vertical bounds cover the
complete default radius at depth 16, so terrain outside the reserved volume
cannot enter through the edge of the divergent cone. Each generated scene writes a rotation-normalized `scene_signature`
using fixed 16x16x16 shape occupancy rather than the axis-order-dependent AABB
partition returned by `VoxelShape.toAabbs()`;
the matrix compares that signature, a rotation-normalized final-output coverage
signature, a final-output fragmentation signature, and the complete workload
tuple `(spots, dependencies, tiles, projectable tiles, side windows, side quads)`
within each seed. The coverage signature groups visible output by source-relative
block and face and hashes additive world/texture coverage measures. Quad coverage
is normalized from its 16-bit vertex representation to the same 8-bit grid used
by slice footprints before comparison; raw 16-bit quad areas remain in difference
details. The fragmentation signature separately hashes record and projection-mode counts.
Reference/debug comparison remains diagnostic and never drives projection authority.

Each difference writes `event=direction_matrix_difference` with the repeat, seed,
baseline/current directions, input/coverage/fragmentation/workload comparisons,
both signatures, and both workload tuples. When final output differs, it also
writes the first differing source-relative block/face, current block state, shape
box count, and baseline/current surface summaries. If output matches and only raw
work differs, the event class is `intermediate_workload_only`.

Input-scene or final-output-coverage mismatch is a red correctness failure.
Fragmentation or raw workload asymmetry with matching coverage is a yellow
performance warning and does not fail the suite. The final
`event=direction_matrix_complete` reports all four counters and direction averages
with `result=pass|pass_with_asymmetry|fail`.

The random-stress mode replaces the former redundant full-suite mode. It builds
1,000 independently randomized scenes at the standard occupancy, without fixed
regression fixtures, and measures one forced full rebuild per scene. Seeds remain
internal only: they are not written to chat, generated/case diagnostics, cleanup
messages, or the final report. Detailed optical compiler logging and per-case chat
are disabled for this mode. Chat and diagnostics receive aggregate progress every
100 scenes and a final `event=random_stress_complete` summary with core and
response percentiles.

Automatic structural validation is source-scoped by dimension, source position,
and travel direction. It must not turn global compiler verbose mode on for every
other active source. A boundary candidate discrepancy writes one structured
`subsystem=spot_projection event=boundary_missing_face` entry containing the
source, direction, depth, target block position, source-relative offset, receiving
face, full block state, candidate path, and rejection-stage detail. Rejection
detail includes the cuboid axis/index, fixed local coordinate, polarity, and
exposed-region count, so an internal stair/fence face can be identified without
parsing the aggregate profile line.

The verbose legacy candidate oracle is shape-aware. For model-derived partial
blocks it enumerates actual optical cuboids, sibling-exposed regions, and stable
side windows. A whole-block envelope is not a valid oracle for an isolated fence
or a stair: reporting a full-block boundary that contains no optical cuboid face
would be a validator false positive, not evidence that production omitted a face.

The performance suite separates forced geometry rebuilds from cache reuse.
Sparse, mixed, and dense cases invalidate the selected source geometry
before every measured sample. Two cached mixed-scene cases perform one forced
unmeasured geometry rebuild followed by one unmeasured appearance-only refresh,
reset their source-bound sample windows, and then step through either source
power or visible-light color before each measured sample. All measured samples
in these cases must report `appearance_only`; one
unexpected full rebuild fails the case. Retries do not advance the appearance
sequence. Reports include `full_rebuild_samples` and `appearance_only_samples`;
a mixed report must not be interpreted without those counts. Automatic suites
show one concise localized chat line for case start and one for the result. The
result distinguishes projection-core average/P95 from request-to-observation
response average/P95; the latter includes the test runner's server-tick boundary
and therefore better matches player-visible delay. Detailed stage timing remains
in the structured diagnostics/performance log.

`spottest random` builds a complete reproducible projection stress scene in the
loaded area in front of and above the executing player. It contains a 300 SP
incoherent stray-like creative source with radius 0.5 and divergence 0.5,
seeded random stairs/slabs/fences/full blocks,
fixed same-depth partial-block regression fixtures, and a terminal white receiver
wall. The command reports its seed and source position. `rerun` rebuilds the last
scene at the same position with the same seed, while `clear` removes its reserved
test volume. This is a destructive debug-volume command and requires the normal
operator permission inherited from `/spectralization`.

Generation writes `subsystem=spot_projection_test event=generated` with the seed,
source geometry, and block counts so a performance profile can be tied to the exact
world fixture. The test command does not enable compiler logging automatically.

`spotperf` keeps a bounded in-memory window of the most recent projection passes in
the current dimension. `report` prints total latency percentiles, average projection
work, emitted side geometry, appearance-cache counts, footprint-integral counts,
and detailed stage averages when compiler debug timing is enabled. It also writes a
single `subsystem=spot_projection event=performance_report` diagnostic entry.
`reset` clears the window before a controlled rerun. Total latency and structural
counts are collected even when compiler debug logging is disabled, so the command
can produce a low-instrumentation release-path baseline.

`spottest report` filters that window by the recorded test source position and
direction, so unrelated active optical sources cannot replace the intended test
sample. `spottest benchmark` clears only that source's samples and schedules one
reprojection at a time from the server post-tick path. It waits for each matching
sample before requesting the next pass and prints the source-bound report when the
requested count is reached. The default is 5 samples and the command maximum is 16.

Relevant diagnostics:

- `subsystem=spot_projection event=profile`
- `subsystem=spot_projection event=budget`
- `subsystem=client_spot_render event=profile`
- `subsystem=client_spot_color_debug event=profile`
- `spot_projection_counts`
- `spot_projection_allocation_summary`
- `spot_projection_continuity`

`spot_projection/profile` is the lightweight performance log. It records tile,
surface, blocker-hull, and rectangle-subtraction counts even when verbose
allocation tracing is off. Per-face `SpotProjectionAllocation` probes are heavy
and should be gated by `compilerdebug verbose`.

The periodic `spot_overlay` entry always writes its active/sent summary. It only
computes and formats continuity, face, allocation, and per-spot detail when verbose
logging is enabled and at least one spot was sent in that update. A zero-send update
records `spot_overlay_detail=skipped_no_sent_spots`; non-verbose updates record
`spot_overlay_detail=skipped_non_verbose`.

Verbose allocation output gives `front-prefix-probe` records first, followed by
internal model-derived `u-side` and `v-side` records. The row budget reserves
space for both categories before other side records, failed allocations, and
ordinary front probes. The allocation summary reports each category count so a
row cap cannot silently hide the relevant cuboid evidence.

Each `front-prefix-probe` reports the target front travel, active/intersecting
prefix counts, cross-block and same-block sibling hits, coplanar continuing-
volume hits, the bounded prefix-window list, and raw/visible areas. Occlusion
windows include both `/travel=` and `/start=` so the event phase can be checked
directly. The summary and row-budget lines expose `front_prefix_allocations` and
`front_prefix_reserved` respectively.

Internal side records also include `occlusion_audit` with:

- `probes`, `own`, `after`, and `prior` cuboid counts for the depth slice,
- `cross_block_prior` and `nearby_cross_block_prior` for non-owner cuboids
  geometrically before the segment,
- `conservative_hits` and compatibility field `sampled_hits` for candidate-window overlap,
- `sample_gaps` for conservative-prefix hits absent from the indexed sweep result;
  this must remain zero,
- bounded per-cuboid entries containing block position, tile, box geometry,
  ownership relation, prefix endpoint, and indexed-hit result.

The audit is constructed only for verbose internal-side allocations. Ordinary
profile mode does not collect the cuboid probe list or run this comparison.

`client_spot_color_debug/profile` is emitted when `spotdebug colors` is enabled.
It belongs to the appearance layer. It does not change texture ownership or
optical readout. It marks incomplete footprint quads in the world and summarizes
whether color artifacts are happening inside one merged geometry key or across
separate neighboring fragments:

- `partial_quad_avg` counts rendered `FOOTPRINT_QUAD` patches whose texture area
  is smaller than a full footprint.
- `small_partial_quad_avg` counts very small partial quads near the texture
  boundary.
- `merged_contribution_spots_avg` counts spots whose client cache entry merged
  multiple incoming spot records. It should stay zero while additive rendering is
  being validated.
- `multicolor_merged_spots_avg` counts merged spots whose inputs had more than
  one color bucket.
- `partial_multicolor_faces_avg` counts block faces where incomplete quads from
  multiple color buckets coexist without necessarily sharing the same exact
  geometry key.
- `partial_geometry_*` fields diagnose incomplete `FOOTPRINT_QUAD` records with
  identical world-space quad vertices. `partial_geometry_merged_*` should stay
  zero in the additive render path; identical-world-quad groups are no longer
  merged unless their full `SpotKey`, including texture mapping, is identical.
- `worst_face` points to the face with the densest incomplete-quad color debug
  cluster in the current profile window.
- `worst_geometry` points to the exact render-geometry key with the densest
  incomplete-quad geometry group.

The profile line also includes phase timings and hot-spot counters:

- `occlusion_authority=cuboid_sweep` identifies the production representation;
  `plane_count` is the retained compatibility setting and
  `plane_count_effective=1` records one authoritative blocker object per cuboid.
- `*_us` timing fields split projection generation into tile range setup,
  rectangle creation, block lookup, projectability checks, cuboid-sweep
  creation, front remaining-intersection queries, side scanning, side
  remaining-intersection queries, spot emission, and remaining-region updates.
- `subtract_*` fields measure rectangle subtraction work after candidate windows
  have been found. In the current stable path these mostly describe debug or
  same-depth clipping rather than the main historical occlusion authority.
- `remaining_*` fields measure the recursive texture-domain state:
  slab/interval counts, remaining area, query counts, blocker input count,
  blocker hits after clipping to the current remaining region, and update work.
  `remaining_slabs=0` is the exact early-stop condition.
- `depth_boundary_radius_*` verifies that adjacent depth slices evaluate their
  shared physical plane with bit-identical radii. Any mismatch is a correctness
  alarm because touching blocker windows can otherwise leave a false light seam.
- Compatibility counters `plane_window_candidates`,
  `plane_window_remaining_culled`, `plane_windows`, and
  `plane_windows_effective` now count cuboid sweep full hulls before/after the
  exact remaining-region prefilter. One accepted cuboid contributes one, not the
  configured historical plane count.
- `remaining_prefilter_*` fields measure the cheap boolean intersection tests
  used by the blocker-hull prefilter. They are separate from
  `remaining_intersection_*`, which measures visible fragment generation.
- `side_*` fields measure side visual candidate enumeration. Side spots are still
  visual readout objects, but the counters show how many side tiles, open-face
  checks, travel intervals, and side texture windows were considered.
- `side_internal_*` and `side_external_*` fields split the side visual pipeline
  by the receiving face's fixed local coordinate. Internal means the side plane
  lies inside the block unit cube, such as a stair or fence model cuboid face;
  external means it lies on the full block boundary. The failure counters mark
  the stage where a side attempt stopped: degenerate travel, not-renderable side,
  missing cross-section, missing side window, empty visible region, low power,
  patch-null, or invisible spot.
- `side_*_tiny_texture_patches`, `side_*_large_stretch_patches`, and
  `side_max_stretch_ratio` are geometry-health diagnostics. They identify
  patches whose texture-domain area is extremely small or whose world/texture
  stretch is large. They do not alter assignment or rendering.
- `side_candidate_us` measures exposed-boundary candidate construction.
- `side_emit_us` measures the side candidate to `SpotRecord` emission path.
- `side_travel_split_us` measures side travel interval splitting and subdivision
  selection inside the side emit path.
- `side_same_depth_split_us` isolates clipping a side travel interval at same-depth
  blocker boundaries.
- `side_occlusion_index_build_us` measures construction of the per-depth travel
  and canonical-bin index used by side receivers.
- `side_prefix_query_us` isolates the same-depth front-order prefix query used for
  side visibility at the segment midpoint.
- `side_debug_audit_us` isolates verbose cuboid/rectangle audit construction. It is
  diagnostic cost and must be zero or negligible without verbose allocation detail.
- `surface_appearance_build_us` measures cache misses that construct the shared
  surface appearance record; `patch_clip_us` measures visible-window-to-quad
  clipping; `spot_record_pack_us` measures side quad quantization and immutable
  `SpotRecord` construction.
- `side_window_us` measures side cross-section and canonical side-window
  construction.
- `side_remaining_intersect_us` measures `sideWindow intersect remaining` and
  same-depth front-window clipping.
- `side_region_intersect_us` isolates the direct V-slab/U-interval intersection
  with `remaining`; `side_canonical_normalize_us` measures same-depth blocker
  union, slab subtraction, adjacent-slab coalescing, and final rectangle export.
- `side_canonical_validation_*` is the verbose-only exact-region comparison
  between the former rectangle-fragment path and the production slab path. Every
  mismatch is a correctness alarm and validation never drives production output.
- `side_patch_emit_us` measures conversion from visible side windows to emitted
  `SpotRecord` quad patches.
- `side_fast_path_patches` counts side patches emitted through the full-travel
  side fast path. These are side windows whose texture-domain region already
  spans the whole side travel axis, so they can be emitted as one coherent quad
  instead of being split into several local patches.
- `side_fast_path_skipped` counts side windows that would have matched that
  full-travel fast path but are deliberately routed through clipped side
  patches. The fast path is disabled because x/y boundary-entry cases need the
  same endpoint and cut-point handling as ordinary clipped side windows to avoid
  barb-like texture discontinuities.
- `side_range_culled_tiles` counts side-scan tile positions skipped by the
  conservative `remaining`-bounds prefilter. This is a candidate-enumeration
  optimization only; accepted side windows still pass through
  `candidate intersect remaining`.
- `side_boundary_*` fields describe the boundary-based candidate enumerator.
  When verbose validation is enabled, they also compare it against the legacy
  projectable-tile face scan. `side_boundary_missing_faces` must stay zero;
  extra faces are acceptable only when later side-window filtering discards them.
  When it is nonzero, `side_boundary_missing_examples` records at most eight
  `depth/pos/face/state/reject` examples. Rejection stages currently distinguish
  unreachable-or-closed faces, absent exposed regions, absent beam travel, and the
  fallback case where no boundary candidate was observed. These examples are
  validation evidence only and never affect production candidate authority.
- `front_fragments_before_merge` and `front_fragments_after_merge` count visible
  front-face texture fragments before and after exact texture-domain union on the
  same block face. The merge changes render granularity only; it does not change
  the assigned texture region.
- `remaining_union_input_rects` counts raw same-depth occlusion windows entering
  the remaining-region update. `remaining_union_merged_rects` counts the exact
  canonical rectangle/slab intervals after those windows are unioned in texture
  domain. A large ratio means many blocker windows were redundant or overlapping.
- `same_depth_*_index_*` fields count indexed split/prefix queries, travel groups,
  spatial candidates, and accepted hits. The index preserves owner-cuboid
  exclusion and restores original window order before sequential clipping.
- `remaining_subtract_validation_*`, `same_depth_split_validation_*`, and
  `same_depth_prefix_validation_*` are verbose-only comparisons against the former
  serial scans. Every mismatch is a correctness alarm; bounded details are written
  to `structural_validation_examples`. Validation never drives production output.
- `side_candidate_verify_us` is the verbose-debug-only cost of the boundary
  candidate comparison. It must not be counted as production side-scan cost.
- `log_write_*_before` fields are rolling diagnostics-log write statistics
  sampled before the current profile line is written. They measure synchronous
  log-write cost without recursively writing a second timing event.
- `hot_depth_*` fields describe the single depth slice that took the most time
  in that projection pass.
- `front_pass_us` measures the complete depth-wide front event transaction,
  including ordering and prefix updates. `projection_attributed_us` sums mutually
  exclusive top-level stages; `projection_residual_us` is the measured projection
  time not covered by those stages. The performance report exposes corresponding
  front-pass and residual averages.
- `surface_appearance_builds` and `surface_appearance_cache_hits` show how many
  material/spectrum appearance records were built or reused before quad geometry
  was attached.
- `footprint_integral_calls` counts the 64x64 footprint-kernel integrals retained
  for verbose allocation diagnostics. It must be zero in the production path;
  diagnostic integrals use an exact constant-time prefix antiderivative.
- `side_candidate_tiles_visited` counts projectable cached tiles visited by the
  sparse side candidate enumerator. It can be compared with `side_tiles_scanned`
  to measure the avoided second dense bounds traversal.

When checking logs, inspect the newest diagnostics and optical compiler logs
first. Do not begin with global searches over all historical logs.

## 9. Optimization plan

The current algorithm should be faster than normal ray tracing because it works
with texture-domain regions and voxel tiles, not per-pixel or per-ray samples.
The main remaining costs are avoidable.

The current reproducible workload, steady-state timing baseline, phase breakdown,
and comparison protocol are recorded in
[SPOT_PROJECTION_PERFORMANCE.md](SPOT_PROJECTION_PERFORMANCE.md). Optimization
claims must preserve its correctness gates and workload counters; reducing emitted
faces, dependencies, or validation coverage is not a valid speedup.

### 9.0 Optimization architecture

Optimization must preserve the projection semantics before it chases local
micro-costs. The stable architecture should be split into three layers:

```text
geometry layer
  source position, direction, radius, divergence, aperture, cuboid shapes, blocks
  -> candidate faces, texture-domain windows, dependencies

occlusion layer
  remaining_0 = K
  remaining_{d+1} = remaining_d - union(blockers_d)
  -> visible texture regions assigned to faces

appearance layer
  solved power, coherent power, frequency, distance, debug mode
  -> alpha, color, texture choice, render visibility
```

These layers have different invalidation rules:

- A block placement or removal invalidates geometry and downstream occlusion for
  the cone subtree that depends on that block.
- A radius, divergence, aperture, or direction change invalidates
  geometry for the affected source output.
- A power, coherent-power, or frequency-only change should normally reuse the
  same geometry and occlusion assignment, then update appearance.
- A debug-center or diagnostic-visibility toggle should not force a world rescan
  unless it changes which records are exported.

This separation is the main coordination point for all optimizations. A local
optimization that reduces `side_scan_us` but makes power changes rebuild geometry
is not actually an architectural win.

The long-term cache keys should reflect the split:

```text
ProjectionGeometryKey:
  dimension
  source block position
  source face / travel direction
  source-face origin
  beam envelope signature: radius, divergence, aperture clamp
  projection quality: max depth, occlusion plane rule/count
  geometry dependency epoch/signature

ProjectionAppearanceKey:
  solved power
  coherent power
  frequency/color summary
  visual distance rule
  debug render mode
```

`ProjectionGeometryKey` must not include power merely because alpha changes.
`ProjectionAppearanceKey` must not be allowed to change texture-domain ownership.

The depth pass should converge on a reusable immutable object:

```text
DepthSliceSnapshot:
  depth
  beam envelope at this depth
  scanned tile bounds
  tile facts: loaded, air-like, projectable, block position
  exposed side boundaries
  front candidate windows
  cuboid sweep candidates and cached full hulls
  dependency positions
```

Front spots, side spots, and cuboid sweeps should be derived from the same
snapshot. They should not independently rediscover the same block state, side
openness, or cone intersection facts.

Recent profiling shows the important coordination problem. Render cost is much
lower than projection generation cost; hundreds of emitted quads are usually
sub-millisecond on the client, while projection generation spends milliseconds in
`side_scan_us`, `side_emit_us`, `front_emit_us`, and `remaining_update_us`.
Therefore the first optimizations should reduce candidate generation and
geometry/occlusion recomputation, not simplify the renderer.

The July 10 partial-block test makes this gap concrete. A wide 300 SP source
scanned 8,544 tiles and exported 8,880 dependencies per rebuild. Typical rebuilds
took about 190-333 ms, with a cold/debug-heavy peak near 801 ms. `side_scan_us`
was about 51-96 ms and `side_emit_us` about 42-81 ms, while client rendering of
roughly 1,350-1,585 active quads stayed near 2-4 ms per frame. The bottleneck is
server-side projection generation plus verbose allocation construction, not GPU
quad submission.

The same test produced about 549-862 spots for the wide source, below the old 1024
quota. That observed shortage was therefore not caused by the quota; correct front
ordering and exact small-fragment assignment still came first. After the ordering
bug was fixed, the publication path was eventually raised to one shared limit of
`2^15 = 32768` spots per owner. Reaching the quota does not abort graph traversal:
every eligible outgoing node is projected and contributes its dependency positions.
The exporter still publishes primary records before side records.

The compiler, wire protocol, client cache, and renderer use the same 32768 limit.
A snapshot is split into ordered chunks of 2048 records, with a token, chunk index,
chunk count, and total record count on every payload. The client keeps the previous
owner snapshot visible while chunks arrive and replaces it only after a complete
snapshot has been reassembled. A clear is a zero-record one-chunk snapshot. This
atomic replacement is part of the visual contract; partial network delivery must
not temporarily remove valid faces.

Before quota publication, records are keyed by their already-quantized render
geometry. Exact duplicate geometry uses the same key on the server and client and
is reduced with the existing last-appearance-wins behavior. The
`spot_projection/budget` event reports raw generated, unique generated,
deduplicated, exported, dropped, and payload chunk counts.

Side visible windows are canonicalized before patch construction, but only inside
one side chart and one occlusion event. The production path intersects the side
window directly with the remaining V-slab/U-interval region, exact-unions the
same-depth prefix occluders, subtracts that union in slab form, and materializes
rectangles only after adjacent equal slabs have coalesced. This computes exactly:

```text
(remaining intersect sideWindow) - union(prefixOccluders)
```

It must not merge across depth/front-order boundaries or across distinct chart
mappings. In verbose validation, the former rectangle-fragment path is exact-
unioned and compared with the direct slab result, but never drives production
output. `side_visible_windows_before_merge` counts raw slab interval fragments
before adjacent-V coalescing; `side_visible_windows_after_merge` counts emitted
canonical rectangles.

Verbose side diagnostics should reserve full geometry strings for internal
model-derived faces. Ordinary external side allocations may retain numeric
totals without formatting every occluder and patch. Incidence rejection should
be performed before same-depth occluder splitting. Because radius squared is a
quadratic propagation invariant, splitting at the single beam waist makes each
travel interval monotone and permits this early rejection without sampling.

### 9.1 Optimize the light-cone tree first

The first optimization target is the light-cone tree, not rendering.

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

Do this before tuning local sweep-index details. If the tree update is wrong,
faster hull queries only make the wrong invalidation boundary harder to see.

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

### 9.4 Keep sweep construction and prefix queries local

One cuboid creates one cached full hull. Same-depth prefix hulls are evaluated
only after the full-hull spatial bins and start-travel ordering have rejected
irrelevant sweeps. Do not trade the removed plane count for adaptive sweep
segments or a candidate-by-all-cuboids scan.

### 9.5 Maintain the remaining region

The runtime authority is the remaining texture region, represented as
non-overlapping v-slabs containing non-overlapping u-intervals. Candidate faces
receive:

```text
visible = candidate intersect remaining
```

Current-depth sweep full hulls are applied only after all faces in that depth
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

The optimized representation first constructs the exact canonical
`blockerUnion`, then subtracts it from `remaining` with one synchronized V-slab
sweep. At each common V band it subtracts the normalized U interval lists once.
This is representation-level acceleration only and preserves the equation above.
Verbose validation may also run the former blocker-slab loop and compare the
normalized result, but the legacy result never drives production authority.

After each update, adjacent slabs with identical interval sets are merged. This
keeps the recursive certificate exact while avoiding historical occupied-window
queries.

This is safer than changing to polygon clipping because it preserves the current
rectangle-domain semantics.

Before applying same-depth blockers, the runtime may discard blocker windows that
do not intersect the current remaining region:

```text
R_d - union(B_d)
= R_d - union({ b in B_d | b intersects R_d })
```

This is an exact set identity, not a visual approximation. It is especially
important when model-derived cuboids generate blocker hulls after most of the
texture domain has already been consumed. The prefilter should be a cheap boolean
query against the remaining slabs and intervals; it must not allocate visible
rectangle fragments or modify `R_d`.

The slab representation is ordered by `v`, and each slab's intervals are ordered
by `u`. Boolean remaining queries may binary-search to the first potentially
intersecting slab or interval and stop once the query range is passed. This
preserves the same region invariant while making the query local to the relevant
texture-domain stripe.

Before subtracting same-depth blockers, the runtime may replace the raw blocker
list by its exact texture-domain union:

```text
R_d - union(B_d)
```

instead of repeatedly applying every raw blocker. This is exact set algebra, not
a visual approximation. The implementation builds the union by slicing the
texture domain at all blocker `v` boundaries, merging covered `u` intervals on
each slab, then subtracting those disjoint slabs from `R_d`.

On a single front face, visible fragments may similarly be exact-unioned before
render emission:

```text
render(A) + render(B) == render(A union B)
```

when `A` and `B` live on the same world face with the same texture-to-face map.
This reduces `SpotRecord` count without changing texture assignment.

For one side candidate, same-depth prefix occluders are now prefiltered by exact
canonical-rectangle intersection before visible-window subtraction. Rejecting a
non-intersecting occluder is an exact set identity, so this reduces allocation and
split work without changing travel order, cuboid ownership, or the resulting
visible region.

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

Projection output can be cached by source, direction, envelope class, cuboid-sweep
rule, and dependency epochs.

If the dependency set has not changed, reuse the previous projection result.
This fits the existing event-driven architecture better than per-frame tracing.

The cache should distinguish the geometry result from the appearance result:

```text
cached geometry:
  spot texture ownership
  world face mappings
  dependencies
  projection tree / depth snapshots

recomputed appearance:
  alpha
  color
  optional debug decoration
```

This lets frequent optical-power updates remain cheap when the beam envelope and
world geometry are unchanged.

The first production cache stage stores the complete assigned patch geometry for
one network source output. Its key contains network id, source position,
direction and exact beam envelope; it deliberately omits
power, coherent power, frequency/color weights, and material appearance. A cache
hit rebuilds those appearance fields from the current solved packet and current
receiving material, while copying only the cached patch mapping. Geometry
templates include patches that quantized to invisible at the previous power, so a
later power increase cannot reveal geometry that the cache discarded.

Each cached geometry result also stores an immutable appearance-application plan.
The plan maps every geometry template, in original publication order, to a unique
receiving `(position, face)` surface and precomputes the envelope and visual power
scale for that surface. An appearance refresh builds one current appearance record
per unique surface, then applies it to templates through the integer mapping. The
plan may cache geometry-derived values, but must not cache power, coherent power,
frequency/color, or receiving-material appearance. It must not regroup the output
iteration order, because ordering still participates in deterministic quota and
duplicate reduction behavior.

Projection dependencies are stored as an immutable snapshot owned by the cached
geometry result. Appearance-only results share that snapshot instead of copying
the complete dependency set on every power/color refresh. Consumers receive an
unmodifiable `LongSet`; any path that needs to mutate dependencies must create its
own accumulator. Full geometry rebuilds still construct a fresh snapshot.

Detailed appearance timing uses one timer per phase, not one timer per template:
prepare all unique surfaces, build all current surface appearances, then update
all templates in original order. Per-template `System.nanoTime()` calls would be
measurement overhead inside the hot path and are therefore forbidden.

Projection dependency dirties remove matching geometry entries before a refresh.
The invalidation event records `earliest_invalidated_depth`, but this first stage
still removes the complete source-output entry. Reusing prefix depth snapshots
and recomputing only the invalidated suffix remains the next cache stage. Verbose
validation and debug face-center projection bypass the production cache. Cache
storage is bounded both by entries and by total retained geometry templates.

Useful cache fields and events are:

- profile `cache_mode=full_rebuild|appearance_only`,
- budget `geometry_cache_hits`, `geometry_cache_misses`,
  `appearance_only_updates`, and `full_geometry_rebuilds`,
- performance report `full_rebuild_samples` and `appearance_only_samples`,
- performance report `geometry_timed_samples`, `appearance_timed_samples`, and
  `appearance_*_avg_us` fields. Appearance-only per-sample profiles use a compact
  field set because geometry, side-scan, and remaining-region counters are zero,
- compact appearance profile `appearance_plan_reused`, template/surface counts,
  `dependency_snapshot_reused`, and prepare/surface-build/record-update timing,
- `subsystem=spot_projection event=geometry_cache_invalidated` with the changed
  position, reason, removed entry count, and earliest projected depth.

### 9.8 Stop when visual alpha is gone

The current projection horizon is fixed by `MAX_PROJECTED_DEPTH`. A beam whose
visual alpha has already faded below display threshold does not need later spot
projection.

This is a visual cutoff only.

### 9.9 Keep side rendering cheap

Side spots should remain a visualization aid. They should not force full polygon
CSG or per-cell ray casting in production.

Side travel sampling frequently needs only the propagated beam radius. Each source
output prepares one `BeamGeometryOps.RadiusPropagation`; a depth slice receives a
lightweight offset view of the same invariant `xx`, `xt`, and `tt` coefficients.
Candidate enumeration, interval-boundary searches, endpoint nudging, adaptive
subdivision and cuboid sweep hulls share that evaluator. Each lookup uses
the same absolute-travel second-moment expression and clamps to the same radius
range as `BeamGeometryOps.propagate(...).radius()`, without constructing a complete
propagated envelope or rebuilding moments per depth. The formal beam-profile
verifier compares the one-shot scalar path, the prepared evaluator, and adjacent
offset views against full-envelope propagation across plane-wave, collimated,
diverging, and focused envelopes. This is an exact representation optimization:
it must not alter sample counts, interval boundary search, side candidate
acceptance, or emitted geometry.

The side intersection and endpoint searches pass their sampling parameters
directly rather than allocating capturing predicates. A receiving candidate also
resolves its cached surface appearance at most once, and only after it produces a
visible window. These lifetime reductions must preserve publication order and
the same `(position, face)` appearance key.

Side candidate enumeration may use the current `remaining` region's texture-domain
bounding box to conservatively shrink the scanned tile range. This is allowed
because it only rejects side faces whose texture-domain footprint cannot receive
any remaining texture. It must not replace the exact assignment step:

```text
visibleSide = sideCandidate intersect remaining
```

A boundary-based side candidate enumerator is the stable side candidate source.
In verbose validation mode it can still be compared against the old projectable
tile face scan:

```text
legacyCandidates = side faces that produced a stable sideWindow
boundaryCandidates = side faces whose grid boundary is swept by the cone
missing = legacyCandidates - boundaryCandidates
extra = boundaryCandidates - legacyCandidates
```

The first invariant is `missing = 0`. The boundary set is allowed to be a
superset only when later side-window filtering discards the extra candidates.

If side shadows need improvement, prefer:

```text
the existing full-hull spatial index
-> tighter prefix candidate rejection
-> local rectangle-window merging
```

over:

```text
full side polygon clipping
per-cell ray casts
one render quad per texture cell
```

### 9.10 Parallel projection jobs

Parallelism is allowed only across a clean thread boundary. Minecraft world reads
and mutable compiler state must stay on the main/server thread. Pure projection
math may run on worker threads after the required facts have been snapshotted.

The required sequence is:

```text
main/server thread:
  collect immutable projection input
  collect block/projectability/dependency snapshots needed by the job
  assign source epoch and dependency epoch

worker thread:
  compute texture-domain geometry
  compute remaining-region occlusion
  build immutable spot records and dependency output

main/server thread:
  publish the result only if source epoch and dependency epoch still match
  otherwise discard the stale worker result
```

The first parallelization unit should be one source output direction. Different
source outputs are independent in the projection layer. A single source output's
depth slices are not initially good parallel units because they are connected by
the recurrence:

```text
remaining_{d+1} = remaining_d - union(blockers_d)
```

Depth parallelism would require a prefix-union or staged scan over blockers. That
may become useful later, but it should not be the first implementation.

The worker input must not contain live `Level` access. If block states, collision
categories, lens-holder state, optical element flags, or air-like checks are
needed, the main thread should convert them into compact immutable tile facts
before dispatching the job. This keeps thread safety and mathematical ownership
aligned: workers operate on a frozen cone, not on a changing world.

Parallelization should also preserve deterministic publication. If two worker
jobs finish out of order, the newer epoch wins and older results are discarded.
Do not merge partial stale results into the live spot layer.

The first useful diagnostics for this phase are:

- source-output job count,
- snapshot build time on the main thread,
- worker projection time,
- stale result count,
- published result count,
- geometry-cache hit count,
- appearance-only update count.

These counters should be separate from `client_spot_render`; render time is not
the current bottleneck.

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
