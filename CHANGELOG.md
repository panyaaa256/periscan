Resets your `/peri config` settings to their defaults (profiles are kept) and makes block matching follow the game's own rules.

### Added
- Add a [usage guide](https://github.com/panyaaa256/periscan/blob/main/docs/usage.md)

### Changed
- Match obsidian, crying obsidian, respawn anchors, reinforced deepslate and unbreakable blocks such as end portal frames in `#periscan:immovable`
- Stop matching retracted pistons and blocks that break when pushed (bells, comparators, heads, decorated pots, suspicious sand and gravel) in `#periscan:immovable`
- Match blocks that change when they receive a redstone signal in `#periscan:redstone_reactive`: redstone dust, redstone torches, redstone lamps, repeaters, comparators, crafters, bells, heads, shelves, big dripleaf, rails and TNT
- Stop matching blocks that only send a signal (buttons, levers, pressure plates, tripwires, observers, lecterns) and barrels in `#periscan:redstone_reactive`
- Remove the redstone lamp from the default "State-changing blocks" list, as `#periscan:redstone_reactive` now covers it
- Change how settings are saved, which resets them to their defaults
- Add the Minecraft version to the file name, for example `periscan-0.3.0+26.2.jar`

### Fixed
- Keep all profiles when a profile save file is damaged; the damaged file is kept with `.broken` added to its name
- Skip profiles that were edited incorrectly by hand instead of failing in commands such as `/peri list`
- Correct the "Maximum scanned Y" description: scanning starts just above the bedrock floor
