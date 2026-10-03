PeriScan is a client-side Fabric mod for building perimeters. Give it the area, and it highlights every block that would stop a World Eater or trencher. You can then clear them out before the machines run. Highlights are drawn through blocks, so buried chests and obsidian show up too.

![Vaults and trial spawners in a trial chamber, highlighted in red through the walls](https://raw.githubusercontent.com/panyaaa256/periscan/main/docs/assets/screenshots/trial-chamber.png)

## Features

- Finds the blocks that get in the machines' way
  - Blocks pistons cannot push, such as obsidian, chests and spawners
  - Waterlogged blocks, sculk sensors, lava and more
- Scans the area in four zones, each with its own block list and color
  - The trenches, the lines next to them, the trench bottom and the eater area
- Finds long runs of falling blocks, such as sand and gravel, inside the trenches
- Follows block changes right away: a highlight disappears once its block is gone
- Keeps scanned highlights visible from far away, so you can check the whole perimeter at once
- Saves areas per world or server as named profiles
- Adds keys to show or hide all highlights or a single zone
- Places your schematics on the corners of the perimeter in bulk, if you have Litematica

![The whole perimeter from above, with the trench zones along the edges and the eater area inside](https://raw.githubusercontent.com/panyaaa256/periscan/main/docs/assets/screenshots/perimeter-overview.png)

## How to use

1. Register the perimeter's area as a profile with `/peri add <x1> <z1> <x2> <z2> <name>`. The coordinates are the chunk coordinates of two opposite corners. Press Tab while typing to fill in the chunk you are standing in.
2. Start scanning with `/peri scan start <name>`.
3. Remove the highlighted blocks.

Block lists, colors and trench widths can be changed with `/peri config`, or from Mod Menu. The [usage guide](https://github.com/panyaaa256/periscan/blob/main/docs/usage.md) explains every command, zone and setting. 日本語の使い方ガイドは[こちら](https://github.com/panyaaa256/periscan/blob/main/docs/usage.ja.md)です。

## Requirements

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl)
- Optional: [Litematica](https://modrinth.com/mod/litematica), for placing schematics in bulk
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu), for opening the settings from the mod list

PeriScan only needs to be installed on the client. It does not need to be installed on the server.

## Compatibility

- Minecraft 1.20 to 26.3, except 1.20.2 (YACL only has beta builds for it)
- Highlights stay visible while an [Iris](https://modrinth.com/mod/iris) shader pack is active
- PeriScan cannot be used in the End

## Issues and feedback

To report a bug or suggest an idea, please open an issue on [GitHub](https://github.com/panyaaa256/periscan/issues). English and Japanese are both welcome.
