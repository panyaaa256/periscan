Replaces the schematic folders with schematic profiles that you edit in game. Schematics in `schematics/peri/` are no longer read: import them into a schematic profile with `/peri schematic edit <schematic profile>`.

### Added
- Add schematic profiles, shared by all worlds, that keep their own copies of the schematics, so the original files can be moved or deleted
- Choose for each schematic the corners it is placed on (`--`, `+-`, `-+`, `++`) and its origin height
- Set per schematic profile the origin height that newly imported schematics start with
- Add `/peri schematic edit`, `/peri schematic list`, `/peri schematic copy` and `/peri schematic remove`
- Add `/peri schematic clear <name>` to remove a profile's placed schematics without deleting the profile

### Changed
- Place schematics with `/peri schematic place <name> <schematic profile>` instead of `/peri schematic <name> [set]`
- Allow placing schematics in any dimension except the End, as the origin height is now set per schematic

### Removed
- Remove the `all` / `edge` / `edge/mx` / `edge/mz` folder layout
- Remove the "Peri schematics folder" and "Edge placement corners" settings

### Fixed
- Fill in the suggestion you click in the block lists; clicking one used to leave the typed text unchanged
