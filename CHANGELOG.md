# Changelog

[日本語](CHANGELOG.ja.md)

## 0.3.0

### Before updating

- **Your settings (`/peri config`) go back to their defaults** after updating, because the way they are saved has changed. Set them again after updating. Your profiles are kept.

### Changes to what gets highlighted

- `#periscan:immovable` (blocks pistons cannot push) now follows the game's own rules more closely:
  - Now included: obsidian, crying obsidian, respawn anchors, reinforced deepslate, and unbreakable blocks such as end portal frames. These were missed before.
  - No longer included: retracted pistons, and blocks that break when pushed even though they hold data (bells, comparators, heads, decorated pots, suspicious sand and gravel).
- `#periscan:redstone_reactive` now matches blocks that change when they **receive** a redstone signal:
  - Now included: redstone dust, redstone torches, redstone lamps, repeaters, comparators, crafters, bells, heads, shelves, big dripleaf, rails and TNT.
  - No longer included: blocks that only send a signal (buttons, levers, pressure plates, tripwires, observers, lecterns) and barrels.
- The default "State-changing blocks" list no longer lists the redstone lamp separately, since `#periscan:redstone_reactive` now covers it.

### Fixes

- A damaged profile save file no longer wipes all of your profiles. The damaged file is kept next to it with `.broken` added to its name.
- A profile that was edited incorrectly by hand is now skipped instead of causing errors in commands such as `/peri list`.
- The description of "Maximum scanned Y" now says correctly that scanning starts just above the bedrock floor.

### Other

- Added a [usage guide](docs/usage.md).
- The file name of the mod now includes the Minecraft version, for example `periscan-0.3.0+1.21.11.jar`.
