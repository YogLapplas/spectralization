# Spot Projection Architecture V2

Status: deferred design proposal. The July 16 optimization round is closed and
this document is not an active implementation checklist. It does not change
projection authority.

`SPOT_PROJECTION_ALGORITHM.md` remains the semantic authority for texture-domain
assignment, analytic cuboid sweeps, front ordering, side receivers, continuity,
and visual/readout separation. This document defines how those calculations
should be retained, shared, scheduled, published, and rendered when many source
outputs are active or moving.

## 1. Why an architecture change is needed

The current implementation has already removed most avoidable local geometry
costs. It uses analytic cuboid sweeps, depth-local workspaces, immutable section
pages, suffix snapshots, appearance-only refreshes, bounded workers, and atomic
owner snapshots.

The remaining scaling problems come from object lifetime and ownership:

```text
one source output
-> build one cone coverage snapshot
-> run one complete source-specific projection transaction
-> assemble one complete owner result
-> publish one complete owner snapshot
-> rebuild one global client spot list
-> emit every visible quad again on every frame
```

Parallel workers reduce source-specific geometry latency, but they do not remove:

- repeated traversal of highly overlapping source coverage,
- main-thread result assembly and owner publication,
- per-owner reverse-dependency replacement,
- repeated client-wide list reconstruction,
- per-frame CPU vertex emission for unchanged geometry,
- network and client work proportional to every complete owner snapshot,
- obsolete work produced by rapidly changing source states.

The July 16 multi-source measurements show all three scaling layers:

- four projection workers produce visible one-wave/two-wave/three-wave response
  steps as source count crosses `4` and `8`,
- snapshot preparation plus deterministic commit remains serial on the server
  thread,
- approximately 14.5k active stress-scene quads cost about 5.2 ms per client
  frame even though the geometry is static.

These numbers are implementation measurements, not semantic requirements.

## 2. Required invariants

Architecture V2 must preserve all of the following.

### 2.1 Projection remains readout-only

The architecture may change visual latency, caching, batching, transport, and
render storage. It must not write projection results back into optical power,
graph topology, receiver authority, or gameplay coupling.

### 2.2 Texture assignment remains source-specific

The following state belongs to one source output direction and cannot be shared
between unrelated sources:

- canonical texture domain `K`,
- depth-entry and depth-exit `remaining` regions,
- same-depth front ordering,
- cuboid-owner exclusion,
- texture-to-world-face mapping,
- geometry template order,
- source-specific dependencies.

World facts may be shared. Final texture ownership may not.

### 2.3 Owner snapshots remain atomic

The old snapshot for an owner remains visible until the complete replacement for
that owner is available. Network chunks, client bucket updates, or buffer uploads
must never expose a half-installed owner.

Architecture V2 does not require several owners to become visible atomically as
one global transaction. Per-owner atomicity is the authority boundary.

### 2.4 Different owners remain additive contributions

Client storage may group contributions for rendering, but it must not apply
last-wins or alpha-over composition across owners. An exact cross-owner merge is
allowed only when the complete geometry, UV mapping, texture function, and color
function are equal and the merge accumulates linear light energy.

### 2.5 Worker inputs remain Minecraft-free

Workers may read immutable projection facts, source plans, cached geometry, and
advisory cancellation state. They may not access a live `Level`, mutable optical
compiler state, network state, client state, or gameplay authority.

### 2.6 Validation paths never become authority

Flat-list client rendering, full rebuilds, legacy candidate scans, and V1/V2
comparison may remain as bounded validation paths. They must never replace a
failed production result or silently decide gameplay/readout authority.

## 3. Design goals and non-goals

### 3.1 Goals

1. Make overlapping sources share world-fact preparation.
2. Make each source output a retained state, not a disposable result.
3. Move complete owner assembly out of the server-thread commit path.
4. Bound obsolete work for moving sources.
5. Make client owner replacement proportional to the changed owner, not every
   active owner.
6. Make static rendering proportional to visible render buckets and draw calls,
   not Java-side reconstruction of every quad.
7. Preserve deterministic quota, ordering, dependency, and freshness behavior.
8. Permit incremental migration from the current implementation.

### 3.2 Non-goals

- no ordinary per-pixel or per-ray tracing,
- no global multi-source shadow map as projection authority,
- no initial parallelism between depths of one source,
- no GPU compute as geometry authority,
- no server-side removal of distant sources from gameplay/readout state,
- no mandatory network delta protocol in the first migration stage,
- no assumption that a moving source can reuse an unchanged texture assignment.

## 4. Ownership model

The architecture is split into four retained layers.

```text
ProjectionWorldScene
  shared immutable world facts and section revisions
             |
             v
SourceProjectionState x N
  independent texture assignment and projection tree
             |
             v
ProjectionUpdateEpoch
  scheduling, pure worker assembly, validation, owner replacement
             |
             v
ClientProjectionScene
  owner contributions, spatial render buckets, retained buffers
```

| Object | Authority/owner | Thread | Lifetime |
| --- | --- | --- | --- |
| `ProjectionWorldScene` | server level cache | server mutation; worker read lease | level/cache lifetime |
| `ProjectionSectionPage` | world scene | immutable after publication | section revision |
| `ProjectionSceneLease` | one update epoch | worker-readable immutable value | epoch/job lifetime |
| `SourceProjectionState` | one source output direction | server-owned committed pointer | source lifetime |
| `DepthProjectionNode` | source geometry snapshot | immutable worker output | geometry generation |
| `ProjectionUpdateEpoch` | level scheduler | server-owned state machine | one scheduling wave |
| `ProjectionOwnerResult` | worker output | immutable | until commit/discard |
| `OwnerProjectionSnapshot` | server tracker | immutable committed value | owner generation |
| `ClientProjectionScene` | client level | client/render thread | client level lifetime |
| `ClientOwnerSnapshot` | client scene | immutable owner value | snapshot token |
| `ClientRenderBucket` | client scene | render thread | bucket changes/level lifetime |

## 5. Server world scene

### 5.1 ProjectionWorldScene

`ProjectionWorldScene` replaces per-source world capture as the primary server
abstraction. It is a server-thread-owned manager of immutable pages:

```text
ProjectionWorldScene:
  sectionKey -> latest ProjectionSectionPage
  sectionKey -> sectionRevision
  bounded retained-page policy
  optional coverage-plan cache
  optional section change journal
```

The existing `ProjectionSectionSnapshotCache` is the migration seed for this
object. The important change is that source jobs acquire shared page leases from
one update epoch instead of independently asking the cache to traverse and count
the same coverage.

### 5.2 ProjectionSectionPage

A page contains immutable projection facts for one `16^3` section:

```text
ProjectionSectionPage:
  sectionKey
  revision
  ProjectionBlockFacts[4096]
  resolvedMask
  optional projectableMask
```

Facts remain Minecraft-free:

- loaded/air-like state,
- immutable optical cuboids,
- immutable receiving material profile,
- bounded diagnostic identity.

The first implementation may retain sparse, partially resolved pages. A later
implementation may eagerly populate active projection sections if measurements
show that this reduces main-thread work without excessive memory use.

### 5.3 Batch union capture

An update epoch first builds source coverage plans without reading live block
state:

```text
for each source plan:
  compute exact conservative coverage masks by section
  store sourceCoverage[source][section]
  union into epochCoverage[section]
```

The server thread then resolves each bit in `epochCoverage` at most once for that
epoch. Every source receives a `ProjectionSceneLease` containing:

- shared immutable page references,
- the page revisions captured by the epoch,
- that source's exact coverage masks,
- aggregate scene/coverage diagnostics.

The source-specific mask remains important. A page may contain facts captured for
another source, but a job must still fail with a snapshot miss if it leaves its
declared conservative coverage. Extra facts must not hide an under-captured cone.

The desired work ratio is:

```text
sum(source covered blocks) / unique(epoch covered blocks)
```

This ratio should be logged. The current 3x3 test deliberately creates a high
reuse ratio and is the primary validation scene for this layer.

### 5.4 Coverage-plan cache

Coverage planning is pure geometry. A bounded cache may retain relative coverage
plans keyed by:

```text
travel direction
beam envelope signature
maximum projection depth
snapshot halo rule
source alignment within a section
```

This cache may accelerate mask construction, but it is not world authority. Its
result must be identical to fresh conservative coverage generation.

### 5.5 Scene freshness

The initial correctness rule remains conservative:

```text
captured section revision != current section revision
=> result is stale
```

A later `SectionChangeJournal` may allow a result to commit when a section
revision changed only at positions outside the job coverage. That optimization is
optional and must retain the conservative rule as its reference verifier.

## 6. Per-source retained projection state

### 6.1 Separate geometry and appearance generations

One source output has two independent desired-state generations:

```text
geometryGeneration:
  source position/origin
  travel direction
  beam envelope/aperture
  projection quality
  relevant world geometry/material facts

appearanceGeneration:
  solved power
  coherent power
  frequency/color
  visual distance/debug mode
```

A power/color change must not invalidate an in-flight geometry result. When a
geometry result finishes, the system may apply the newest compatible appearance
state before publishing.

World material changes that alter projection geometry or receiving appearance
advance the appropriate generation through the normal section/dependency dirty
path. Geometry freshness never comes from appearance freshness alone.

### 6.2 SourceProjectionState

```text
SourceProjectionState:
  source identity and owner id
  desired geometry/appearance generations
  committed geometry/appearance generations
  committed ProjectionGeometrySnapshot
  committed OwnerProjectionSnapshot
  latest desired source plan
  pending/preparing/running/ready work handle
```

At most one desired state is retained per source. Queue entries are references to
this state, not independent requests that must all execute.

### 6.3 ProjectionGeometrySnapshot

The current `SpotProjectionResult` already contains the first retained geometry
pieces. V2 should make the separation explicit:

```text
ProjectionGeometrySnapshot:
  ordered geometry templates
  AppearancePlan
  DepthProjectionTree
  immutable dependency snapshot
  geometry signature
  source coverage summary
```

Final power/color `SpotRecord` values are an appearance-applied owner export, not
the geometry cache authority.

### 6.4 DepthProjectionTree

The first V2 tree remains a deterministic depth spine:

```text
DepthProjectionNode:
  depth
  incoming remaining certificate
  outgoing remaining certificate
  candidate/blocker fingerprint
  geometry template range
  dependency delta
  optional depth work summary
```

This formalizes the current suffix cache without changing its mathematics.

Initial invalidation remains:

```text
earliest dirty depth = k
reuse nodes < k
restore outgoing certificate of k - 1
rebuild k..end
```

A later texture-region delta tree may propagate only the changed part of
`remaining`. That is not part of the first V2 cutover because a local change can
alter downstream ordering and dependencies. Region-delta authority requires a
separate proof and reference comparison.

### 6.5 Dependency updates

Each depth node owns its dependency delta. A suffix rebuild can therefore produce:

```text
removedDependencies = old suffix dependencies - new suffix dependencies
addedDependencies   = new suffix dependencies - old suffix dependencies
```

The reverse dependency index should gain a diff-based batch API. It must not
remove and reinsert an unchanged prefix or a set-equal complete snapshot.

## 7. Moving-source desired-state policy

Exact projection remains event-driven, not a per-frame simulation.

For every source output:

```text
desired generation advances
-> replace the stored desired plan
-> coalesce any not-yet-started older request
-> mark any older running/ready result as non-committable
```

The scheduler guarantees:

- at most one queued desired state per source,
- at most one preparing/running result per source,
- only the newest compatible generation may commit,
- the old committed owner remains visible until its replacement commits,
- source retirement still sends an atomic clear.

Cooperative cancellation may be added after the retained state is stable. It is
advisory only: a worker may check interruption/cancellation between depth
transactions and stop producing a result. Freshness validation remains the
authority, so failure to cancel early cannot commit stale output.

The visual update cadence for continuously moving sources is a separate design
decision. Architecture V2 supports both:

- exact latest-state projection whenever capacity permits,
- a bounded visual refresh cadence with intermediate states coalesced.

The cadence must be chosen explicitly before movable-light gameplay is finalized.

## 8. ProjectionUpdateEpoch

### 8.1 State machine

```text
COLLECTING
  gather dirty source states and newest desired generations

PLANNING
  build per-source coverage and epoch union coverage

RESOLVING_SCENE
  resolve live world facts once for union coverage

DISPATCHING
  submit pure source-output geometry work

ASSEMBLING
  assemble complete owner results off the server thread

READY_TO_COMMIT
  immutable owner results wait for server validation/budget

COMMITTED / DISCARDED
```

An epoch is a scheduling and sharing boundary, not a global visual authority.
Owners from one epoch may commit in different ticks while each owner remains
atomic.

### 8.2 Source jobs

`ProjectionSourceJob` contains:

- source identity and node ordinal,
- captured geometry/appearance generations,
- immutable scene lease,
- immutable source geometry input,
- compatible cached geometry snapshot,
- earliest invalidated depth,
- no live cache or `Level` reference.

It computes one source output using the existing `VoxelSpotProjector` authority.

### 8.3 Worker-side owner assembly

The current `PreparedSpotBatch.commit()` still performs record-scale work on the
server thread. V2 replaces it with a pure worker assembly stage:

```text
ProjectionOwnerAssemblyJob:
  consume source-node results in original ordinal order
  apply deterministic primary-before-side reduction
  perform exact geometry-key deduplication
  apply owner quota
  assemble immutable dependencies/allocations
  compute signatures and bucket metadata
  produce OwnerProjectionResult
```

For a one-node owner this stage may reuse the node result directly. For a
multi-node owner it performs the same deterministic order as the current commit
path.

The worker may construct cache replacement values, but it may not install them.

### 8.4 Main-thread commit

The server-thread commit must be bounded by owner count, not spot count wherever
possible:

```text
validate cache instance
validate source geometry generation
validate scene lease revisions
validate optical trace/readout authority
install immutable geometry cache entry
apply dependency diff
swap committed source/owner snapshot references
enqueue network publication
```

Sorting, geometry-key map construction, record packing, signature construction,
and complete dependency aggregation should already be finished.

Network payload encoding may remain asynchronous, but the ordered immutable
snapshot selected for publication is decided on the server thread.

### 8.5 Backpressure and fairness

The worker pool remains bounded. Architecture V2 adds per-source fairness:

- one source cannot occupy multiple queued generations,
- ready owner results use a bounded queue,
- sources waiting longest receive dispatch priority within the same relevance
  class,
- a repeatedly moving source cannot starve stable sources,
- stale/cancelled work does not consume commit budget.

Increasing worker count is not an architectural solution. Worker count remains a
hardware/configuration choice after memory-bandwidth and allocation profiling.

## 9. Network migration

### 9.1 First stage: keep full owner snapshots

The current chunked `SpotOverlayPayload` already preserves per-owner atomicity.
The first client architecture migration should keep this protocol unchanged.

Completed owner snapshots are assembled as today, but the client queues completed
owner replacements and applies all replacements once per client tick/render
update. This removes repeated global rebuilding without introducing protocol
risk.

### 9.2 Client application boundary

```text
network payload chunks
-> PendingOwnerSnapshot
-> complete immutable ClientOwnerSnapshot
-> queued owner replacement
-> client/render-thread batch apply
```

The old owner remains installed until the final step.

### 9.3 Optional delta protocol

Only after retained owner/bucket storage is stable may the protocol add:

```text
owner base token
new snapshot token
removed patch identities
added patch records
appearance-only patch updates
```

Requirements:

- patch identity includes complete render geometry and texture mapping,
- hash collisions cannot silently merge patches,
- full owner snapshots remain available for initial sync and recovery,
- a delta whose base token is missing is rejected and requests/falls back to a
  full snapshot,
- quota and publication order remain server-defined.

Delta transport is an optimization, not an initial V2 dependency.

## 10. ClientProjectionScene

### 10.1 Replace the flat global cache

The current client cache rebuilds and sorts every active owner after each owner
replacement. V2 stores owners and render buckets independently:

```text
ClientProjectionScene:
  ownerId -> ClientOwnerSnapshot
  bucketKey -> ClientRenderBucket
  queued owner replacements
  dirty render buckets
```

### 10.2 Bucket key

The initial bucket key should be:

```text
dimension/level identity
receiving SectionPos
render layer: core / halo / ring / debug
```

Spots are contained on receiving block faces, so section ownership is stable.
Face may be added to the key if measurements show that it improves culling or
buffer locality.

Global distance order is not required because production spots use additive
blending. Deterministic ordering is still retained inside diagnostics and
reference comparisons.

### 10.3 Owner replacement algorithm

Each `ClientOwnerSnapshot` stores the bucket membership of its contributions.

```text
replaceOwner(ownerId, replacement):
  remove the old owner's handles only from its recorded buckets
  insert replacement handles into replacement buckets
  mark the union of old/new buckets dirty
  swap the owner snapshot atomically
```

The first implementation may remove/reinsert the complete changed owner. Its cost
is `O(owner spots + touched buckets)`, not `O(all active spots)`.

An owner-local geometry diff can be added later.

### 10.4 Debug analysis

Partial-geometry grouping and multicolor diagnostics must run only when their
debug mode is enabled or when a targeted validation requests them. Normal owner
replacement must not analyze every active owner's geometry.

### 10.5 Retained render buffers

The client render path is migrated in two steps.

Step A retains CPU-side bucket lists but renders only visible buckets. This
validates owner replacement, culling, and contribution preservation.

Step B gives each dirty bucket retained render buffers:

```text
ClientRenderBucket:
  immutable/logical contributions
  core buffer
  halo buffer
  ring buffer
  bounds
  dirty generation
```

Unchanged buckets do not rewrite vertices each frame. Appearance-only owner
updates may update instance/vertex attributes without rebuilding world geometry.

The exact Minecraft buffer implementation is deliberately left behind a
`SpotRenderBuffer` abstraction so the owner/bucket architecture does not depend
on one renderer API.

### 10.6 Render culling and budgets

Protocol safety and render budgeting must use separate limits:

```text
MAX_SPOTS_PER_OWNER:
  server/protocol safety and deterministic quota

ClientSpotRenderBudget:
  view-dependent visible bucket/quad budget
```

The client first rejects buckets outside render distance/frustum, then considers
individual contributions only inside visible buckets. Exceeding a render budget
must not cause a full global sort every frame.

A later relevance policy may prioritize by distance, projected area, and visible
alpha. It must be deterministic and owner-neutral. Server projection output is
not discarded because of a client frame budget.

## 11. Freshness and failure behavior

| Event | Required behavior |
| --- | --- |
| source geometry changes while queued | replace desired plan; do not enqueue another generation |
| source geometry changes while running | result becomes non-committable; optionally cancel |
| appearance changes while geometry runs | retain geometry work; apply newest compatible appearance |
| covered section changes | stale/discard under the conservative revision rule |
| unrelated owner changes | do not invalidate this owner |
| source retires | cancel pending state and publish atomic clear |
| receiving block is removed | immediately prune impossible local patches; schedule exact rebuild |
| worker fails | keep old committed owner; log and requeue according to bounded retry policy |
| client loses/incompletely receives chunks | retain old owner; never expose partial replacement |
| delta base token is absent | reject delta and use full snapshot recovery |
| render buffer upload fails | retain logical owner scene; rebuild dirty buffer later |

Retries must be bounded and generation-aware. A repeatedly failing old generation
must not displace a newer desired state.

## 12. Diagnostics

### 12.1 World scene and epoch

`subsystem=spot_projection event=scene_epoch`

- epoch id,
- requested/coalesced source count,
- unique sections,
- sum source coverage blocks,
- unique epoch coverage blocks,
- coverage reuse ratio,
- resolved/reused facts,
- coverage planning, world resolve, and lease build time,
- scene cache page count and eviction count.

### 12.2 Source and worker

`event=source_job`

- source/owner id and generations,
- cache mode and earliest invalidated depth,
- queue wait and worker time,
- cancellation/stale reason,
- geometry templates, dependencies, and depth reuse.

`event=owner_assembly`

- node result count,
- assembly time,
- generated/unique/exported/dropped records,
- dependency added/removed counts,
- output signatures.

### 12.3 Main-thread commit

`event=owner_commit`

- validation time,
- geometry cache install time,
- dependency diff time,
- state swap time,
- publication enqueue time,
- main-thread records iterated,
- budget deferral count.

The architectural target is that `main_thread_records_iterated` becomes zero for
ordinary complete results.

### 12.4 Client scene update

`subsystem=client_spot_scene event=owner_batch_applied`

- owner replacements,
- old/new/added/removed contribution counts,
- buckets touched/created/removed,
- logical apply time,
- buffer rebuild/upload time,
- global rebuild count, which must remain zero in production V2.

### 12.5 Client render

Extend `client_spot_render/profile` with:

- total and visible buckets,
- dirty buffers rebuilt,
- logical contributions,
- rendered core/halo/ring quads,
- per-frame Java allocations if measurable,
- culling/buffer submission/end-batch time,
- budgeted/cut contributions.

## 13. Validation gates

### 13.1 Projection correctness

All existing gates remain mandatory:

- partial-geometry structural validation,
- boundary missing faces = 0,
- same-depth and remaining-region mismatches = 0,
- direction matrix input/coverage equality,
- edge-connected cuboid continuity,
- output coverage and fragmentation signatures,
- full-versus-suffix equivalence,
- appearance-only geometry reuse.

### 13.2 Shared scene correctness

- batch-union leases and independent V1 snapshots produce identical block facts,
- every V2 source job remains inside its declared coverage,
- scene page revisions reject stale work,
- facts captured for another source do not change this source's dependency set,
- source order inside an epoch does not change output fingerprints.

### 13.3 Worker assembly correctness

- worker assembly equals the current deterministic main-thread assembly,
- primary-before-side quota behavior is unchanged,
- multi-node ordinal order is unchanged,
- dependencies, allocations, signatures, and chunk contents are identical,
- comparison is validation-only and never replaces production output.

### 13.4 Client retained-scene correctness

- after every owner replacement, the retained scene's complete contribution
  multiset equals the flat V1 reference multiset,
- shuffled/interleaved chunk delivery never exposes a partial owner,
- replacing one owner does not modify another owner's contributions,
- cross-owner identical geometry remains additive,
- section bucket culling does not omit any contribution inside render distance,
- CPU bucket rendering produces the same vertex/UV/color signature as the flat
  renderer before retained GPU buffers become authoritative.

### 13.5 Moving-source correctness

Add a deterministic motion suite:

```text
static world
source states A -> B -> C -> ... with configurable update interval
multiple overlapping moving owners
world edits during movement
```

It must verify:

- only the latest eligible generation commits,
- old owner snapshots remain until replacement,
- retired sources clear,
- no owner resurrects after a late worker completion,
- queue depth per source remains bounded,
- final output equals an isolated full rebuild of the final state.

## 14. Migration plan

### Phase 0: contracts and measurements

- land this design and required diagnostics,
- keep current production authority,
- add no permanent dual computation.

### Phase 1: client retained logical scene

- keep `SpotOverlayPayload`,
- queue completed owner replacements,
- replace global `activeSpots` rebuild with owner-local section buckets,
- keep the existing immediate renderer over visible bucket contents,
- compare the retained contribution multiset with the flat reference only in
  targeted validation.

This phase changes no server projection mathematics or protocol.

### Phase 2: retained render buffers

- introduce `SpotRenderBuffer`,
- rebuild only dirty buckets,
- retain core/halo/ring contribution separation,
- remove the per-frame global over-budget sort,
- keep CPU vertex-signature validation until buffer output is confirmed.

### Phase 3: ProjectionUpdateEpoch and union scene leases

- group ready source states into one epoch,
- build per-source and union coverage,
- resolve shared facts once,
- keep the existing `VoxelSpotProjector` worker calculation,
- retain conservative section revision validation.

### Phase 4: worker-side owner assembly

- move `PreparedSpotBatch.commit()` record-scale work into a pure worker stage,
- add immutable cache replacements and dependency diffs to the owner result,
- reduce server commit to validation, reference installation, and publication.

### Phase 5: explicit retained projection tree

- replace the implicit depth-cache record with `DepthProjectionTree`,
- retain exact suffix rebuild behavior,
- use dependency diffs for suffix replacement,
- add region-delta experiments only behind formal/reference validation.

### Phase 6: optional network deltas

- introduce stable patch identities and full-snapshot recovery,
- add appearance-only deltas,
- retain owner atomicity and deterministic server quota.

Each phase must produce an independently shippable improvement. Later phases must
not be required to keep earlier phases correct.

## 15. Performance acceptance criteria

Performance claims require the same workload and correctness gates as
`SPOT_PROJECTION_PERFORMANCE.md`.

Initial V2 targets on the current test machine:

1. Nine overlapping warm sources resolve shared world facts once per epoch; the
   scene log must show the unique-versus-summed coverage ratio.
2. Warm epoch planning plus scene lease construction stays inside the existing
   4 ms projection main-thread window at P95 for the 3x3 test.
3. Main-thread owner commit performs no ordinary per-spot map construction,
   sorting, packing, or signature traversal.
4. Client owner replacement is proportional to the replaced owner's spots and
   touched buckets; global rebuild count is zero.
5. Static 14k-spot stress rendering no longer performs Java-side vertex emission
   proportional to all spots every frame.
6. Moving-source backlog is bounded to one desired state and at most one
   preparing/running state per source.
7. Submission rejection, diagnostic loss/failure, structural mismatch, missing
   face, and final fingerprint mismatch remain zero.

Absolute millisecond targets for retained GPU rendering and moving-source visual
cadence must be set after the first retained-scene prototype. They depend on the
chosen renderer API and the still-open gameplay/readout latency policy.

## 16. Decisions required before implementation

The following choices are intentionally not hidden inside this design:

1. Maximum desired visual latency for a continuously moving source.
2. Whether moving sources target exact every-tick desired states or a bounded
   projection refresh cadence.
3. Whether distant/off-screen moving sources may use a lower visual refresh
   priority.
4. Whether the first retained GPU implementation uses section meshes or
   per-contribution instancing.
5. Whether network deltas are worth their protocol complexity after owner-local
   client replacement removes the global rebuild.

The recommended default is:

- latest-state-wins coalescing,
- no promise of exact intermediate states,
- per-owner atomic replacement,
- section-based retained client buckets,
- existing full owner snapshots until measurements justify deltas.
