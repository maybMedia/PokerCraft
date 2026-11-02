# Hanging poker table light

The **Hanging Poker Table Light** is a three-block-wide black pendant inspired
by the supplied pool-table reference. It has a rounded rectangular housing,
an opal-white luminous diffuser, four tension wires, and a ceiling canopy.

Find it in the **PokerCraft** creative tab, or use:

```mcfunction
/give @s pokercraft:poker_table_light
```

Right-click the underside of a solid ceiling block above the table's centre.
The clicked block supports the central canopy; the fixture reserves a clear
**3 × 1 area, two blocks high below the ceiling**. Placement rotates with the
player, using the same facing convention as the poker table. For an existing
PokerCraft table, a ceiling five blocks above its base leaves the necessary
space above the table's reserved seating area.

The three lower cells emit block light level **15**, and the diffuser renders
bright even in darkness. The metal housing keeps normal lighting. The fixture
is always on. Removing its ceiling support or breaking any part removes the
entire light and drops one item. The hanging wires are selectable; the housing
and canopy provide solid collision.

Craft one light with this pattern:

```text
I . I
C G C
P P P
```

`I` = iron ingot, `C` = chain, `G` = glowstone, `P` = glass pane.

The editable source is **`art/poker_table_light.bbmodel`**. Its exported geometry
is `src/main/resources/assets/pokercraft/geo/poker_table_light.geo.json`, with
the material atlas in `textures/block/poker_table_light.png`. Keep the `diffuser`
bone name to retain its luminous rendering. Collision and selection bounds are
derived from the exported cubes, including their rotations.
