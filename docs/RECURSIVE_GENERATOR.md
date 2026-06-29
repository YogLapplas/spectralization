# Recursive Generator

This document records the design intent and implementation lessons for the
recursive generator.

## Design Goal

The recursive generator is a generator whose fuel is another recursive
generator. It should feel recursive without requiring a recursive runtime data
structure. The player performs a simple action:

```text
empty recursive generator block + recursive generator item
=> locked recursive generator with one deeper state
```

The machine has one input slot. That slot accepts exactly one recursive
generator item. Once accepted, the slot becomes locked and the item is treated as
the internal generator for the running state.

The player-facing fantasy is not "put fuel in a furnace". It is "place a
generator inside a generator, then race the remaining time window".

## State Model

The runtime state is fixed width:

```text
remaining[0..15]
energy
locked input item
```

There is no dynamic stack object. The array is the stack. Each nonzero
`remaining[i]` is one active recursive layer.

Layer output is exponential:

```text
depth 1: 250 FE/t
depth 2: 500 FE/t
...
depth 16: 8,192,000 FE/t
```

The energy buffer is 1 GFE. The generator stops consuming remaining time while
the buffer is full.

## Timing Rule

Base layer time is 51 seconds.

Every recursion step ages all layers by 3 seconds, including the newly inserted
layer. With perfect zero-tick handling, the layer times after 16 recursive
insertions are:

```text
3s, 6s, 9s, ..., 48s
```

This makes 15-layer and 16-layer fuel conversion comparable in the ideal case,
while any real player or automation delay makes 16-layer operation less
efficient. The reward is that 16-layer output is twice the 15-layer output.

This creates the intended choice:

```text
15 layers: easier to preserve time, lower peak output
16 layers: very high peak output, only a short top-power window
```

At the cap, further recursion behaves like a fixed-width stack window rather
than an unbounded list.

## UI Rule

The time chart is a stack visualization.

- Columns are left aligned.
- Newer/outer layers appear on the left.
- Older/inner layers appear toward the right.
- The shortest active column is usually the rightmost one.
- When a rightmost layer reaches zero, the remaining chart does not shift.

The locked item slot is a real slot, not decoration. Its item tooltip must
reflect the machine's current state, so the locked item stack is kept in sync
with the block entity state while the machine runs.

## Implementation Lessons

### Partial Shift-Click Must Stop

A machine slot with capacity 1 can expose a subtle quick-move bug. If shift-click
inserts only one item from a stack, the remaining stack must stay in the original
player inventory slot. The menu must not then fall through into the ordinary
inventory-to-hotbar quick-move behavior.

The recursive generator solves this by using a terminal quick-move branch for
recursive generator items:

```text
if recursive generator item:
    try to move exactly one item into the machine slot
    return ItemStack.EMPTY
```

Returning empty is intentional. It stops the vanilla shift-click loop from
calling the same source slot again and triggering normal inventory movement for
the leftover items.

### Saved State Must Include The Locked Item

The block item carries both the runtime recursive state and the locked internal
generator item. Breaking and replacing the block must preserve the locked item.

The generic machine-content drop path must not clear the locked input slot while
the generator is active, because the drop item is responsible for carrying that
state.

### Depth Is Not Active Count

The promotion algorithm must not use active layer count as the destination index
for a new layer. Once earlier layers time out, holes appear in the array. Using
the active count would write the new layer back into the middle of the array and
truncate deeper layers.

Promotion uses the highest remaining layer instead:

```text
newLayer = highestRemainingLayer + 1
```

At the 16-layer cap, it shifts the fixed-width window forward.

### Full Buffer Means Pause

The generator should not waste remaining time when its internal energy buffer is
full. It may continue to push energy outward, but generation and remaining-time
decrement pause until there is buffer space again.

This is important because the high-depth generator produces enormous burst
power, and the player should be able to reason about output bottlenecks.
