Draws large groups of highlights much faster, and turns the "Trench bottom" zone off for new installs. Your saved settings are kept: if you already have a config file, "Trench bottom" stays as you set it.

### Changed
- Turn the "Trench bottom" zone off by default, as it highlights the whole floor of a trench that is not dug yet
- Check the bottom two layers of the trench with "Inside the trench" while "Trench bottom" is off, so obsidian, chests and the like there are still highlighted
- Draw highlighted blocks that touch each other as one shape, without the faces and lines between them, which makes large groups of highlights much cheaper to draw
- Reduce stutter when many chunks of a region with long trenches load at once

### Fixed
- Highlight blocks that appear in the line just outside the area (lava, sculk sensors) right away; they used to wait until the chunk was loaded again
- Fix the description of `#periscan:redstone_reactive` in the settings screen and the usage guide: observers and other blocks that only emit a signal are not matched
