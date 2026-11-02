# Host chip visual prototype

Open `art/host_chip.bbmodel` in Blockbench to edit the model and preview the
animated emissive texture. It has 49 cubes, a stepped circular outline, gold
rims, an obsidian face, a raised crown, and rainbow inlays and edge panels.

The exported Java model is `assets/pokercraft/models/item/host_chip.json`.
The texture is `assets/pokercraft/textures/item/host_chip.png`, a 64×1536 strip
containing 24 square frames. Its `.mcmeta` plays each frame for two ticks with
interpolation, producing a 2.4-second rainbow cycle at 20 ticks per second.

The Host Chip is available in the PokerCraft creative tab, or with:

```mcfunction
/give @s pokercraft:host_chip
```

The client renders this item at full light in inventory, hand, item frames, and
as a dropped item. This is emissive appearance, not a world-light source or a
bloom shader. It uses the same visible-hand pose as the ordinary chips.

Right-click an unclaimed table while holding the chip to claim host ownership.
Once claimed, the owner can open host controls with **P** near the table or
the seated hotbar's crown action without holding the chip. Ownership is saved
with the table and checked by the server. Another chip cannot take over a
claimed table. See `poker-gameplay.md` for setup and release requirements.
