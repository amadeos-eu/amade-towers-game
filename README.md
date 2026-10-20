# amade-towers-game

**amade-towers-game** is the tower defence game of the [amadeOS](https://github.com/datazuul/amadeos) desktop: orcs, dire wolves and trolls march from their lairs on your castle, and there is no road. They take the shortest way over the open ground, and every archer, cannon or frost tower you build makes that way longer. Three battlefields larger than the screen, a meadow, a ford and two caves, made in the [Tiled](https://www.mapeditor.org/) editor; the enemies, the towers and the waves are text files. Stars are saved. Built on [amade-game-engine](https://github.com/datazuul/amade-game-engine); runs on other operating systems as well.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

## Playing

- A click, **Space** or **Enter** leads from the title to the choice of a battlefield. Click one, or choose with **left** and **right** and play with **Space** or **Enter**. A battlefield opens when the one before it is won.
- **Click a free tile** of grass to open the build menu: archer, cannon or frost tower, with their prices. Click one, or press **1**, **2** or **3**. A tower may stand anywhere on grass as long as one way from every lair to the castle stays open; the game refuses a tower that would close the last one, and says why.
- **Click a tower** to see its range, upgrade it twice (the brass bands show the stage) or sell it for 70 % of what it cost.
- **Archers** shoot fast and far. **Cannons** fire a ball that bursts where its target will be and hurts all around. **Frost** towers slow every enemy near them.
- Enemies that reach the castle cost lives; when none are left, the castle falls. Three stars for keeping 90 % of the lives, two for half.
- **N** or the button in the bar calls the next wave early, for as much gold as seconds were left. **F** or **x2** doubles the speed. **F3** shows the flow field: an arrow on every tile, the way an enemy standing there will walk.
- **Arrow keys** or **WASD**, dragging with the right or middle mouse button, or the mouse at the edge of the screen move the view.
- **P** pauses, and so does leaving the window; **Esc** closes a menu, pauses, and in the pause leaves the battle.
- **F11** switches to full screen and back. *Game → Graphics* chooses how the battlefield reaches the screen: **Direct** (at the screen's full size) or **Pixels** (at 640 × 400, enlarged by whole factors). **F12** shows how long each frame takes.
- Sound effects and music can be switched off under *Game → Sound*.

The keys can be changed in `~/.config/amadeos/towers/towers.properties`, with the names Java gives them, several separated by spaces:

```properties
keys.left=LEFT A
keys.right=RIGHT D
keys.up=UP W
keys.down=DOWN S
keys.next=N
keys.fast=F
keys.flow=F3
keys.pause=P
```

Texts in English, Deutsch, Français, Español and Italiano.

## Files

| What | Where |
|---|---|
| Settings (graphics, sound, keys) | `~/.config/amadeos/towers/towers.properties` |
| Progress (open battlefields, stars) | `~/.local/share/amadeos/games/towers/progress.save` |

The progress is written to a new file that then replaces the old one, with the old one kept as `progress.save.bak` and a checksum in the last line, so a crash while saving or a damaged file loses nothing.

## Making battlefields

The battlefields are `src/main/resources/eu/amadeos/games/towers/maps/map1.tmx` to `map3.tmx`, made for Tiled 1.10 or later, with the tileset `tiles.tsx` and its picture `tiles.png`. Open them in Tiled, change them, save; the game reads them as they are.

- A map is orthogonal, not infinite, with tiles of 20 × 20 pixels.
- The tile layer **ground** says what each tile is; the tileset gives each tile a class: `tree` or `pine` (blocks building and walking, drawn over the enemies), `rock` and `water` (block both). A tile without a class is grass. The game draws the ground itself, so `tiles.png` only shows Tiled which tile is which.
- The object layer **things** holds a rectangle of the class `castle` (one; every tile it covers by more than half is the castle) and one or more of the class `lair`, the tile where enemies come out. A lair on the edge of the map looks best.
- A map that names an unknown class, has no castle or no lair, or a lair outside the map is rejected with its file and the object.

What the battlefields send and give is in `src/main/resources/eu/amadeos/games/towers/data/`, explained at the top of each file:

- `units.txt`: each enemy's health, speed, armour, bounty, harm and size.
- `towers.txt`: each stage of each tower, its cost, range, reload, damage and shot; `like` starts a stage with everything of the one before.
- `waves.txt`: each level's gold and lives, how much tougher each wave is, and its waves as groups of a unit, how many and how far apart.

Change a number, start the game again, play. A mistake is reported with its file and line. `LevelTest` plays every battlefield with a simple builder, so a level that cannot be won, or a map whose lairs cannot reach the castle, fails the build.

## Building

```bash
./gradlew test          # the window test needs a display (xvfb-run on servers)
./gradlew run
./gradlew deb           # build/distributions/amade-towers-game_<version>_all.deb
./gradlew installToAmadeOs   # copies the package to ../amadeos/config/packages.chroot/
```

All pictures are drawn by the game from vector shapes (`Art`) when the window opens, at the size of the screen. `./gradlew art` draws `maps/tiles.png` and the icon from the same shapes, `./gradlew screenshots -Pshots=<folder>` the handbook's screenshots, and `./gradlew benchmark` measures the frame times of every battlefield.

With `../amade-game-engine` (and `../amade-commons`) checked out next to this repository, Gradle builds those checkouts instead of the JitPack versions.

## Structure

- `domain`: the rules without user interface or time of their own. `Game` (a battle: building, upgrading and selling, the flow field the enemies follow, waves, shots, cold, lives, gold and stars), `Level` (reads a Tiled map: its ground, lairs and castle), `Rules` (reads `units.txt`, `towers.txt` and `waves.txt`), `UnitType`, `TowerType`, `Wave`, `Creep`, `Tower`, `Shot`, `Trail` (where the enemies have walked, for the path on the ground), `Ground`, `Event`, `Field` (sizes and numbers).
- `repository`: `TowersSettings` (graphics, sound, keys), `Progress` (open battlefields and stars, in a save file).
- `ui.game`: the running game on the loop thread. `TowersGame` (loop, mouse and keys, menus, scenes: title, choice, battle, pause, result; the camera), `TowersPainter` (the battlefield as the camera sees it), `Terrain` (the ground in chunks drawn once), `Art` (all drawings), `Pictures` (the sprites made of them), `Hud` (the bar, the cards and texts), `Effects` (events become bursts, scorch marks, sounds and music), `SoundBank`, `Music` (the tunes, in MML), `Autobuild` (the builder of the title screen and the tests), `Arena` (what the presenter may ask of the game).
- `ui.main`: Model-View-Presenter for everything around the battle: `TowersPresenter`, `TowersView`, `TowersWindow`.

From the engine: `FlowField` (one search from the castle gives every tile its way), `Catalog` (the data files), `OverlayMenu`, `Mouse`, `TiledMap`, `Camera`, `Rotations` and `Sprite`, `Particles`, `SaveFile`, `GameLoop`, `GameCanvas`, `Sound`, `Tune` and `Mixer`, `Keyboard` and `Controls`, `SceneStack`, `FullScreen`.

Comments of the form `// --8<-- [start:name]` mark the excerpts the [Game Developer's Handbook](https://datazuul.github.io/amade-game-engine/) quotes; chapter 30 is about this game.

## Licence

[EUPL-1.2](LICENSE)
