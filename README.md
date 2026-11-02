# PokerCraft

Multiplayer no-limit Texas Hold'em for Minecraft Java Edition. Play with two to
six players at a physical poker table, using hotbar actions while seated.

## Features

- Cash games with physical chips, configurable denominations, buy-ins, and cash-outs.
- Last-man-standing tournaments with virtual starting stacks and static, timed,
  or hand-based blind increases.
- Server-managed dealing, betting, side pots, split pots, and private hole cards.
- Animated dealing, tabletop chip stacks that follow player balances, and winner effects.
- A card HUD, public action announcements, a private turn chime, and a smooth overhead camera.
- Loose playing cards, a 54-card deck including jokers, and a ceiling-mounted table light.
- Editable Blockbench models in [art](art).

## Installation

| Requirement            | Version                                   |
|------------------------|-------------------------------------------|
| Minecraft Java Edition | 1.21.1                                    |
| Java                   | 21 or newer                               |
| Fabric Loader          | 0.16.14 or newer                          |
| Fabric API             | Required; developed with `0.116.6+1.21.1` |
| GeckoLib               | `4.7.6` or newer within 4.x               |

Use dependency builds intended for Minecraft 1.21.1. Install PokerCraft, Fabric API,
and GeckoLib in the `mods` folder on **both the server and every player's client**.
The same PokerCraft JAR works on clients and dedicated Fabric servers.

## Start a game

1. Get a **Poker Table**, **Host Chip**, and poker chips from the PokerCraft
   creative tab. Commands are also available:

   ```mcfunction
   /give @s pokercraft:poker_table
   /give @s pokercraft:host_chip
   /give @s pokercraft:white_chip 64
   ```

2. Place the table on solid ground with a clear **7 x 5 area, three blocks high**.
   Its footprint rotates with placement.
3. Hold the Host Chip and right-click the table to claim it. Choose the game
   mode, configure settings, and save. After claiming, press **P** near the table
   to reopen host controls without holding the chip.
4. Put the Host Chip away and right-click a stool to sit. Select hotbar slot **7**
   and right-click to join. In a cash game, use slot 7 again to buy in from your
   inventory. Excess chip value stays in your inventory when you reach the
   maximum buy-in. Tournament stacks are assigned when the host starts the game.
5. Once at least two seated players have joined and are ready, the host can
   select **Start game**. Subsequent hands deal automatically after the results pause.

Chip colours can have values in any order. Every denomination must be a positive
whole number and a multiple of the smallest denomination; for example, white 50,
red 10, blue 5, and green 20. Set the small blind to **0** for a single-blind game.
Cash-game blinds stay static; tournament blind increases apply between hands.

## Controls

While seated, poker action icons replace the displayed hotbar. Your real
inventory and selected inventory slot remain intact. Select an action with **1-9** or the mouse wheel, then
**right-click** to use it.

| Slot / key                | Action                                         |
|---------------------------|------------------------------------------------|
| 1                         | Fold                                           |
| 2                         | Check / call                                   |
| 3                         | Confirm raise to the selected total street bet |
| 4 / 5                     | Lower / increase the raise amount              |
| 6                         | All-in                                         |
| 7                         | Join / buy in                                  |
| 8                         | Host controls / table overview                 |
| 9                         | Leave / cash out between hands                 |
| Sprint key (default Ctrl) | Hold while adjusting a raise for larger steps  |
| P                         | Open host controls near a table you own        |
| V                         | Toggle the overhead table camera while seated  |
| Sneak (default Shift)     | Stand up                                       |

The P and V bindings can be changed in Minecraft's controls menu under PokerCraft.
Leaving your stool, disconnecting, or dying folds a hand that is not all-in.
On timeout, the server checks if no call is owed and otherwise folds.
See the [gameplay guide](docs/poker-gameplay.md) for cash-outs, refunds, side pots,
host settings, and recovery after a reload.

## Cards and table lighting

Right-click with a deck to draw a card, or sneak and right-click to gather loose
cards from your inventory. Return cards through inventory right-click interactions.
Loose cards and jokers are separate from the shuffled 52-card deck used in live hands.

Place a **Hanging Poker Table Light** against the underside of a solid ceiling.
It needs a clear **3 x 1 area, two blocks high below the ceiling**, and emits light
level 15. See the [light guide](docs/table-light.md) for crafting and placement.

## Build and develop

Import the repository as a Gradle project in IntelliJ IDEA and select **JDK 21**
for the project and Gradle JVM. Run commands from the repository root.

On Windows PowerShell:

```powershell
.\gradlew.bat build
```

On macOS or Linux:

```sh
./gradlew build
```

The release JAR is `build/libs/pokercraft-<version>.jar`. Set `mod_version` in
[gradle.properties](gradle.properties) before building a release. Upload the
regular JAR; the `-sources.jar` contains source code. Fabric API and GeckoLib
are separate runtime dependencies. Development test mods are excluded from the release JAR.

Useful development tasks:

| Task             | Purpose                                                                             |
|------------------|-------------------------------------------------------------------------------------|
| `runClient`      | Launch a development Minecraft client                                               |
| `runServer`      | Launch a development dedicated server                                               |
| `runGametest`    | Run server rules, item, placement, and integration tests                            |
| `runClientSmoke` | Check client resources, menus, card models, and previews; save screenshots and exit |

For example, run `.\gradlew.bat runGametest` on Windows or `./gradlew runGametest`
on macOS/Linux. The server test report is `build/reports/gametest.xml`.
Client smoke-test results are in `build/clientSmoke/client-smoke-result.txt`,
with screenshots in `build/clientSmoke/screenshots/`.

## More documentation

- [Gameplay and host setup](docs/poker-gameplay.md)
- [Hanging table light](docs/table-light.md)
- [Host Chip model and texture](docs/host-chip.md)
- [Card texture generation](docs/card-textures.md)

Common game and server code lives in `src/main`; client rendering and input live
in `src/client`. Server game tests live in `src/gametest`, and the separate client
smoke-test mod lives in `src/clientSmoke`.
