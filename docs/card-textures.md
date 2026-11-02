# Playing card textures

The card set contains all 52 standard cards and red and black jokers. Every
atlas is **256×256**, with **96×144** pixels for the face: four times the linear
resolution of the previous 64×64 atlas and 24×36 face. All faces are redrawn at
the higher resolution, including the ace of spades, with larger corner indices,
smooth suit silhouettes, distinct court portraits, and higher-contrast ink.
Hearts have rounded lobes, diamonds have four sharp points, clubs have three
round lobes, and spades have a pointed crown and stem. Dense pip layouts keep
individual symbols separate.

The normalized model UV layout is unchanged. The original patterned back, edge
strips, and unused pixels keep their design through exact nearest-neighbor
scaling. The display-only hidden-card model continues to use the back artwork
on both sides.

- Ace of spades: `textures/item/playing_card.png`
- Additional textures: `textures/item/cards/<rank>_of_<suit>.png`
- Jokers: `textures/item/cards/red_joker.png` and `black_joker.png`
- Matching model variants: `models/item/cards/<card-name>.json`

All paths are relative to `src/main/resources/assets/pokercraft/`.
Ranks are `ace`, `2` through `10`, `jack`, `queen`, and `king`.
Suits are `spades`, `hearts`, `diamonds`, and `clubs`.

The model variants inherit geometry and display transforms from
`pokercraft:item/playing_card_base`, overriding texture slot `0` and the particle
texture. For example, `pokercraft:item/cards/queen_of_hearts` uses
`pokercraft:item/cards/queen_of_hearts.png`.

Playing cards receive a random face on their first server inventory tick (including
the offhand). Loose cards retain their face when thrown, returned, or collected.
Cards with matching data can stack to 64; different faces can be stored together
in the Deck of Cards item. Hovering a card shows its rank and suit beneath its name.
The saved `minecraft:custom_model_data` component selects one of 54 model overrides,
and the thrown card keeps its identity through embedding, collection, and reloads.
Existing cards without a face receive one when they enter a player's inventory.

A new Deck of Cards contains one of each of the 54 faces. Right-click to draw a
random card into the inventory, without replacement. Sneak + right-click gathers
loose playing cards from the inventory and offhand, up to the 54-card capacity.
In the inventory, right-click cards onto a deck to return them, or hold the deck
on the cursor and right-click loose cards. Right-click with an empty cursor on a
deck, or right-click an empty slot while holding the deck, to draw a card.
The deck tooltip shows the remaining count. Empty decks stay empty until refilled;
partial decks and all stored card data are preserved when saved. Returned cards
can include duplicates from other decks and retain their faces and custom names.

Face IDs 1 through 52 use suit order `spades`, `hearts`, `diamonds`, `clubs`,
with rank order ace, 2 through 10, jack, queen, king. IDs 53 and 54 are the red
and black jokers. For example, `/give @s pokercraft:playing_card[minecraft:custom_model_data=25]`
gives a queen of hearts. The item selector is `models/item/playing_card.json`;
the shared geometry and display transforms live in `models/item/playing_card_base.json`.

To regenerate the assets and contact sheet, install Pillow 10.1 or later and run:

```powershell
python scripts/generate_card_textures.py
```

The stable 64×64 source template is `art/playing_card_template.png`; the face
artwork is drawn procedurally by the generator, using Pillow's bundled font.
This makes regeneration repeatable without relying on the generated textures
or operating-system fonts. The full-deck preview is `build/previews/cards.png`.
The generator checks unique artwork, pip counts, 256×256 atlas dimensions, and
preservation of all scaled pixels outside the face. The client smoke check also
verifies that all 54 loaded models use 256×256 sprites.
