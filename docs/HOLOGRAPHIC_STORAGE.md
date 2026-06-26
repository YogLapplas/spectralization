# Holographic Storage

This document records the current design target for the holographic storage line.
The Chinese project name is 《折光纪》, and this system should feel like matter is
stored as addressable optical information rather than as ordinary drawers.

## Blocks

### Holographic Storage Shell

The holographic storage shell is the basic storage cell. A single shell stores up
to 4096 items of one item type. It can work alone like a one-type drawer, and it
can also be recognized by a holographic storage core.

The shell is optically transparent for normal network solving. Its TRA response
should not change when it stores an item, because making storage contents alter
ordinary optical routing would make the system hard to reason about and hard to
use.

### Stable Holographic Storage Shell

The stable shell is a storage-shell variant. Items inside it do not undergo
ordinary phase-changing or smelting reactions driven by pass-through light. Stable
shells are reserved for workstation-style recipes, storage-matrix recording, and
future interactions where the stored item acts as a stable holographic reference.

For storage capacity and core recognition, stable shells follow the same one-cell
4096-item rule as ordinary shells.

## Core Integration

When a shell is connected to a holographic storage core, the core should treat it
as a physical storage cell instead of folding it into an anonymous bulk capacity.

Insertion priority:

1. Non-empty shell that already stores the same item.
2. General matrix storage.
3. Empty shell.

Extraction priority:

1. General matrix storage.
2. Non-empty shell.

The reason is practical: existing filled shells should continue compacting the
same item, while general storage remains the first source for extraction so the
core behaves predictably.

## Photoinduced Reaction

The new crafting family is called photoinduced reaction.

A photoinduced reaction is triggered by sustained laser exposure through a shell
that contains an item. It is not part of optical network compilation. Instead, it
checks the world over time after the network has become stable.

Basic rules:

- A laser must pass through a shell containing the source item for a short
  continuous duration.
- Each success converts at most one item.
- The source shell itself is never a valid output shell.
- Output is selected from adjacent dark shells that are not currently crossed by
  light.
- Output may merge into a shell that already legally stores the same product.
- Output may also enter an empty shell.
- If no legal output shell exists, the reaction does not run.
- The reaction does not consume explicit optical power; that cost is represented
  by the optical losses and by the requirement for stable illumination.

In ordinary photoinduced reactions, the output shell will often end up sideways
relative to the beam because the front and back shells tend to be lit too. This is
not hard-coded as a rule. It should emerge from the dark-output-shell condition.

## Stable Workstation Reactions

If a stable shell contains a workstation item, and a suitable laser passes
through it, the reaction may inspect the neighboring 3x3x3 shell volume for
required ingredients. Products choose a legal dark output shell in that same
3x3x3 area.

This system is intended for recipes where items appear to be pushed through a
continuous holographic shell array by timed light. It is allowed to support
special recipes that cannot be expressed well as normal machine processing.

## Recording Metamaterials

A stable shell containing an unrecorded metamaterial template can connect to a
formed storage matrix as a recording station. When conditions are valid it glows
green. A suitable laser passing through the core path records the template over
about 2 seconds, after which the shell glows red and pushes the recorded item to a
nearby legal container.

This keeps automation simple: a hopper and chest should be enough to collect
recorded products without leaving the station blocked.
