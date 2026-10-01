# Chest Item Finder – Minecraft 26.3 / Fabric

A client mod for Minecraft **Java Edition 26.3** that remembers the contents of opened chests locally. Find items using a searchable item grid, then locate matching chests by their bright white-and-gold outlines, visible through walls.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for **Minecraft 26.3**, version **0.19.5** or later.
2. Place `mc-item-finder-1.3.0.jar` and [Fabric API 0.161.0+26.3](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.161.0%2B26.3/fabric-api-0.161.0%2B26.3.jar) in your Minecraft installation's `mods` folder. Replace the previous Item Finder JAR.
3. Launch the Fabric profile. Minecraft 26.3 requires **Java 25**; the official launcher normally supplies the appropriate Java runtime.

The built mod is at `build/libs/mc-item-finder-1.3.0.jar`. The `-sources.jar` contains source code and should not go in the `mods` folder. Install the mod on your client; no server installation is required.

To access settings through the Mods screen, also install [Mod Menu 21.0.0 for Minecraft 26.3](https://www.curseforge.com/minecraft/mc-mods/modmenu). Mod Menu is optional.

## Usage

- **O** toggles chest memory. It is **OFF** by default, and the setting persists across restarts.
- With chest memory enabled, open the chests you want to search later. Normal chests, trapped chests, and double chests are supported.
- **I** opens the search screen. Search by item name in your game language, an ID such as `minecraft:diamond`, or an item tag such as `#minecraft:logs`.
- **Hide zero-count items** hides items whose remembered quantity in the current world and dimension is 0. It also applies to name, ID, and tag searches. The setting defaults to OFF and persists across restarts. Toggling it keeps your search text and resets the results to the first page.
- Click an item to search for it alone. This replaces the previous selection and closes the search screen. The numbers below the icons show the total remembered quantity in the current dimension.
- Hold **Ctrl (Control)** and click multiple items to search for them together. **Ctrl + click** adds or removes an item without closing the search screen. Selected icons have a gold border. Your selection persists when changing search terms, pages, and the zero-count filter.
- Finish selecting with **Done** or **Escape**. Every remembered chest containing **at least one** selected item receives a glowing outline. A chest containing several selected items is highlighted and counted once.
- Reopen the search screen and click **Clear highlight** to clear the selection and stop highlighting. You can also toggle **Remember chests** directly in the search screen.

With Mod Menu, open **Mods → Chest Item Finder → Configure**. Toggle **Remember chests** and **Hide zero-count items**, or use **Change key bindings…** to adjust the search and memory keys. Settings are available from the main menu. Changes take effect and are saved immediately; Done and Escape return to Mod Menu.

You can also change the keys under **Options → Controls → Key Binds → Chest Item Finder**. Searching remains available when chest memory is disabled, using previously saved chests. The search screen works in Survival mode and does not pause the game.

The mod's labels and messages are in English, regardless of the selected game language. Item names and Minecraft's own screens follow your game language.

## Stored data and limitations

The mod stores chest positions, dimensions, item IDs, quantities, and the time of the last content change. It waits for the first complete inventory update from the server. Your player inventory and the item held by the cursor are excluded. Changes while a chest is open and its visible contents when closing are recorded. Double chests count as one chest.

Files are stored under `config/itemfinder/` in the game directory: `config.json` contains settings, and `worlds/*.json` contains chest memories. Singleplayer worlds are separated by their save paths, servers by their addresses, and dimensions are also kept separate. Corrupt memory files are backed up as `.corrupt-…` files. The mod does not send saved data to other players.

Searches use the **last observed contents**. If hoppers or other players change a closed chest, reopen it with chest memory enabled to update the saved contents. Unopened chests are never searched. Ender chests, barrels, shulker boxes, and server-only menus are excluded. Highlights use a custom glowing chest outline inspired by the spectral arrow effect. Loaded positions without a chest are not highlighted.

## Development and verification

Requires JDK 25. The Gradle wrapper uses Gradle 9.6.0. Dependencies are pinned to Fabric Loom 1.17.21, Loader 0.19.5, Fabric API 0.161.0+26.3, and Mod Menu 21.0.0. Mod Menu is available in the development environment but is not bundled in the mod JAR. Minecraft 26.3 is no longer obfuscated, so the standard `jar` task is used.

```powershell
.\gradlew.bat build
```

This workspace also contains a portable JDK 25 toolchain under `.tools/jdk25/`. `build.ps1` automatically uses it when present and keeps the Gradle cache inside the project.

```powershell
.\build.ps1
```

Automated tests cover persistence round trips, world and dimension isolation, double chests, empty chests, corrupt files, settings, multiple-item searches without duplicate matches, and tracking opened and synchronized containers. The build and tests have passed; graphics and interactions have not yet been checked in a running game.

Manual gameplay checks:

1. Disable chest memory, open a chest containing diamonds, and search for diamonds: no highlight.
2. Enable chest memory, open and close the same chest, then select diamonds: its outline is visible through a wall.
3. Open a second chest and a double chest containing diamonds: all three locations are highlighted, with the double chest counted once.
4. Remove all diamonds from a chest and close it: that chest is no longer highlighted. Diamonds held only in your player inventory do not produce a match.
5. Disable chest memory and open more chests: no new matches are recorded; previously remembered chests remain searchable.
6. Restart the game and switch worlds and dimensions: memories persist, and matches stay associated with their original context.
7. Sneak while holding a block and place it against a chest: no new chest entry is recorded.
8. Open settings through Mod Menu from the main menu. Toggle chest memory and change key bindings; both persist after restarting. Done and Escape return to the previous screen. Without Mod Menu, search and both keyboard shortcuts remain available.
9. Enable Hide zero-count items in the search screen: only items with a positive remembered quantity appear, including in name, ID, and tag searches. Disable it: all registry items are visible again. With no remembered stock, the enabled filter displays a message. Also change the filter through Mod Menu and check it after restarting; existing configurations retain their chest memory setting.
10. Open one chest containing diamonds, one containing iron, and one containing both. Ctrl-click diamonds and iron: the search stays open and both icons are selected. After Done or Escape, all three chests glow; the chest containing both is counted once. Ctrl-click iron to deselect it: only diamond chests remain highlighted. Click iron normally: only iron is selected. Clear highlight removes the entire selection. Also check multiple selection across search terms and pages, and with the zero-count filter enabled.
11. Select German as the game language: mod labels, messages, and Done buttons remain English, while item names follow the game language.

License: MIT.
