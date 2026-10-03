Adds keys, and lets you hide highlights without stopping the scan: all of them at once, or zone by zone. `/peri schematic edit` no longer creates a missing schematic profile; create new ones with `/peri schematic create` first.

### Added
- Add a "PeriScan" category to the key binds (Options > Controls > Key Binds). No key is assigned by default
  - Open Settings
  - Show/Hide Highlights (everything)
  - Show/Hide for each zone
  - Rescan (same as `/peri scan reload`)
  - Clear Scan (same as `/peri scan clear`)
- Add "Show highlights" (General) and "Show this zone" (each zone) to the settings. They are the same switches as the keys, and are kept when the game restarts
- Add `/peri schematic create <schematic profile>`, which creates an empty schematic profile and opens its settings
- Add "Keep the saved orientation" to each schematic of a schematic profile. When it is on, the schematic is placed on every corner without being flipped or rotated, and is moved to fit the corner instead

### Changed
- `/peri schematic edit` only opens schematic profiles that exist. A mistyped name is now reported, instead of opening an empty profile
- Replace the placeholder mod icon with the PeriScan icon

### Fixed
- Limit the chunk coordinates of `/peri add` to the world (-1875000 to 1875000). Larger values overflowed the block coordinates
