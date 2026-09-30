Adds Minecraft 1.19.4 to 1.21.11 and 26.1 to 26.3, and keeps each world's profiles apart. The first time you open a world, it gets a copy of the profiles saved for it by 0.3.0; worlds whose names used to collide may get another world's profiles too, which `/peri remove` deletes.

### Added
- Support Minecraft 1.19.4 to 1.21.11 (except 1.20.2) and 26.1 to 26.3, with one jar per Minecraft version
- Warn when a configured tag does not exist in the world (for example `#minecraft:wall` instead of `#minecraft:walls`), as such a tag matches nothing
- Link the source code and issue tracker in mod lists such as Mod Menu

### Fixed
- Keep the profiles of different worlds apart when their names differ only in characters other than letters and digits (for example Japanese names of the same length) or are the same, and keep them when a world is renamed
- Reject perimeters longer than 512 chunks per side instead of freezing the game
- Keep your profiles when the game crashes while saving them
- Reset out-of-range or missing values in a hand-edited config file to the nearest allowed ones instead of misbehaving
- Reduce the stutter while the chunks of a large perimeter load
