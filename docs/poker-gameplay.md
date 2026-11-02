# Poker gameplay

PokerCraft tables play **no-limit Texas Hold'em** for two to six players. Each
hand uses a fresh shuffled 52-card deck; jokers and loose inventory cards are not
used in live hands. The server owns dealing, turns, betting, hand evaluation, and
chip balances. Players receive only their own hole cards until showdown.

## Host setup

1. Hold the existing **Host Chip** and right-click any part of a poker table.
   The first player to do this claims its host access and opens the host menu.
   Ownership persists: afterwards press **P** near the table or use the seated
   **Host controls** action. The chip is only required to claim an unowned table.
2. Choose **Cash game** or **Last man standing**.
3. Set white, red, blue, and green chip values, initial small/big blinds, and
   the turn timer. Cash games also use minimum/maximum buy-in limits; last man
   standing uses the configured starting stack.
4. In last man standing, choose static blinds, increases every N completed
   hands, or increases every N seconds. The blind factor multiplies both blinds
   at each level. Timed intervals use server game time, not time while the server
   is stopped. New blind levels apply **between hands**, never during a hand.
   Cash-game blinds are always static.
5. Save settings. Once at least two seated players have joined and are funded,
   press **Start game**. Put the Host Chip away when clicking a stool to sit.
   Irrelevant settings are greyed out: cash chip values and buy-in limits are
   cash-only, starting stacks are tournament-only, and blind intervals/factors
   require increasing tournament blinds. Settings lock while a game is running
   or cash balances remain banked.

Chip colours can have values in any order. Every value must be a positive whole
number and a multiple of the smallest configured denomination. Cash blinds and
bets use that smallest value as their unit, so every cash-out remains exact.
For example, white 50, red 10, blue 5, green 20 is valid. Set the small blind to
**0** for a single-blind game; the big blind stays positive and determines the
minimum opening bet.
The starting stack/buy-in minimum must cover the initial big blind.

## Playing

- Right-click a stool to sit. Your hotbar becomes a palette of poker action
  items while your actual inventory and selected inventory slot stay intact.
  Select with **1–9** or the mouse wheel and **right-click** to use the action.
  The slots are **Fold**, **Check/Call**, **Raise**, **Lower raise**, **Increase
  raise**, **All-in**, **Join/Buy in**, **Host controls/Overview**, and **Leave**.
  Unavailable actions are dimmed. First use **Join** in slot 7.
  Closing an overview or host menu returns to the world and does not fold your hand;
  leaving the stool, disconnecting, or dying folds a non-all-in hand.
- In cash games, **Buy in** deposits chip value from your inventory (including
  your offhand) up to the maximum buy-in, accounting for any existing stack.
  Excess chips stay with you. Large chips can be broken into exact change;
  overflow change drops as owned items. Deposits and top-ups are accepted
  between hands and must still reach the minimum buy-in.
- Last-man-standing players receive their configured virtual starting stacks
  when the host starts the tournament. There are no physical-chip deposits or
  cash payouts in this mode, and late joins are closed once it starts.
- On your turn, choose **Fold**, **Check/Call**, **All-in**, or adjust a total
  street bet with slots **4/5**, then confirm **Raise** with slot **3**.
  Raise adjustments use one chip unit; hold the sprint key (default **Ctrl**)
  to adjust by one big blind. For example, if you have already bet 20,
  raising to 60 contributes 40 more. The server enforces turn order, minimum
  raises, short all-ins, and when raising action has reopened.
- The board and your hole cards appear in the game screen and on the tabletop.
  Your tabletop cards tilt toward your stool for readability. Opponents only
  receive card backs until an eligible showdown. Cards animate from the deck
  in clockwise, one-card-per-pass order, and community cards slide into their
  painted slots. Winners receive a short colored confetti/sparkle celebration
  around their stool, including shared and side-pot winners.
- Each seat's tabletop chips represent its current unbet stack, changing with
  buy-ins, blinds, bets and payouts. Empty and zero-stack seats have no chips.
  Cash piles use the configured denominations in descending value order,
  independent of colour. Tournament piles use virtual 1/5/25/100 units so that
  odd chips from split pots can always be represented exactly.
  Each colour draws up to 20 physical chips; larger piles show an exact
  `count x denomination` label above the bank instead of rendering thousands
  of individual chips.
- Press **V** while seated to toggle a camera directly above the table. Press
  it again to return to your previous viewing direction; standing also exits
  the overhead view. The camera moves smoothly into and out of a closer overhead
  position, one block lower than before. This changes only your camera, not your player.
- The top of the screen shows your hand and community cards, sliding in as they
  are dealt. Public actions and game events appear above them. A quiet local
  chime plays when your turn begins; other players do not hear it.
- The default timer is 45 seconds. A timeout checks when no call is owed,
  otherwise folds. All-in hands remain eligible even if their player leaves.
- Completed hands pause for eight seconds for results, then deal automatically
  when players are ready. The host may also press **Next hand**. The button
  rotates, including the special heads-up blind/action order.
- Side pots are limited to eligible contributions. Equal hands split the pot;
  odd smallest-denomination chips go clockwise from the dealer button.
- Last man standing ends when one player has chips left. Eliminated players
  remain at zero until the tournament ends or the host stops it.

## Leaving, stopping, and recovery

**Leave** between cash-game hands cashes out your balance as physical chips.
Dismounting cashes you out after the current hand finishes. An offline player's
refund is saved in the table and delivered on reconnect while the table is loaded.
Refunds for dead players wait until they are alive again, so death cannot discard
a payout from the old inventory. If everyone leaves the same tick, pending
wagers are refunded rather than choosing a winner by seat order.
Inventory overflow drops owned chip items for that player rather than deleting
them, including in creative mode.

**Stop / refund** cancels an unfinished hand and returns each player's pending
bets to their own balance; it does not award an unfinished pot. Cash players can
then cash out. The host cannot change chip values or game configuration while
cash balances remain banked. **Release host access** requires an empty, stopped
table and lets a new host claim it.

Breaking a table cancels the hand and refunds banked cash exactly once. Offline
refunds are dropped at the table as owner-assigned chip items. Host ownership,
configuration, active hands, deck order, tournament progress, balances, and
offline refunds are saved with the block entity. Private state is excluded from
public chunk/block-entity synchronization.
After a reload, missing seats or players follow the same departure policy;
an abandoned hand refunds its pending bets instead of choosing a winner.

## Verification

Run the server rule/integration suite with `gradlew runGametest`.
Run the separate development client with `gradlew runClientSmoke`; it checks
all 54 card models and the game/admin menus at multiple GUI sizes, captures
screenshots under `build/clientSmoke/screenshots`, then exits. Its test mod is
excluded from the released JAR. The report is `build/clientSmoke/client-smoke-result.txt`.

The editable table project is `art/six_seat_poker_table.bbmodel`. Its chip banks
contain individually controlled `stack_<seat>_<colour>_<level>` bones, with
levels 0–19. Keep these names when editing and export Bedrock geometry to
`src/main/resources/assets/pokercraft/geo/six_seat_poker_table.geo.json`.
The existing `chips_1` through `chips_6` parents retain the placement animation.
