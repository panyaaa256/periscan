# Using PeriScan

[日本語](usage.ja.md)

PeriScan highlights the blocks you need to **clear out before running a world eater or trencher**. You only give it the area. Highlights show through walls, so you can find buried chests and obsidian as well.

With Litematica installed, it can also place all of your perimeter schematics at once.

- [Basic workflow](#basic-workflow)
- [Commands](#commands)
- [What gets highlighted](#what-gets-highlighted)
- [Settings](#settings)
- [Placing schematics](#placing-schematics)
- [Troubleshooting](#troubleshooting)

## Basic workflow

1. Register the perimeter's area as a **profile**.
   ```
   /peri add <x1> <z1> <x2> <z2> <name>
   ```
2. Start scanning.
   ```
   /peri scan start <name>
   ```
3. Remove the highlighted blocks. A highlight disappears by itself once its block is gone.
4. When you are done, clear the highlights.
   ```
   /peri scan clear
   ```

Profiles are saved per world (or server). After logging out and back in, run `/peri scan reload` to resume scanning the profile you were scanning before.

## Commands

| Command | What it does |
|---|---|
| `/peri add <x1> <z1> <x2> <z2> <name>` | Registers an area as a profile |
| `/peri remove <name>` | Deletes a profile, together with the schematics placed for it |
| `/peri list` | Lists your profiles |
| `/peri scan start <name>` | Starts scanning a profile's area |
| `/peri scan clear` | Removes the highlights and stops scanning (the profile is kept) |
| `/peri scan reload` | Rescans the profile you scanned last from scratch |
| `/peri schematic edit <schematic profile>` | Opens a schematic profile's settings, creating it if needed (see [below](#placing-schematics)) |
| `/peri schematic place <name> <schematic profile>` | Places a schematic profile's schematics for a profile |
| `/peri schematic clear <name>` | Removes the schematics placed for a profile (both profiles are kept) |
| `/peri schematic list` | Lists your schematic profiles |
| `/peri schematic copy <from> <to>` | Copies a schematic profile, together with its schematics, under a new name |
| `/peri schematic remove <schematic profile>` | Deletes a schematic profile, together with its copies of the schematics |
| `/peri config` | Opens the settings screen |

### Giving the area

- Coordinates are **chunk coordinates**: the block coordinate divided by 16, rounded down (for example X = 100 → 6, X = -1 → -1).
- Press Tab while typing a coordinate to get the chunk you are standing in as a suggestion.
- The rectangle with the two chunks as its corners is the **outermost edge of the perimeter**. The corners can be given in either order.
- The smallest possible area is one chunk (16 × 16 blocks).
- The largest possible area is 512 chunks (8192 blocks) per side.

### Names

Profile names can contain letters, digits and `_` `-` `.` `+`, but no spaces.

### Dimensions

- A profile belongs to the dimension where you ran `/peri add`. Scanning and placing schematics only work while you are in that dimension.
- PeriScan cannot be used in the End.

## What gets highlighted

The area is scanned as four **zones**. Each zone has its own block list and color in the settings.

```
                 North (-Z)
      +----------------------------+
      |TTTTTTTTTTTTTTTTTTTTTTTTTTTT|
      |TTTTTTTTTTTTTTTTTTTTTTTTTTTT|
      |TT                        TT|
West  |TT        E E E E         TT|    East
(-X)  |TT                        TT|    (+X)
      |TTTTTTTTTTTTTTTTTTTTTTTTTTTT|
      |TTTTTTTTTTTTTTTTTTTTTTTTTTTT|
      +----------------------------+
                 South (+Z)

  T = trench (12 wide at north and south, 3 wide at east and west; adjustable)
  E = eater area
```

| Zone | Where | Highlighted by default |
|---|---|---|
| One outside the trench | One line on each side of every trench (the line just outside the area, and the line just inside the trench) | Sculk sensors, calibrated sculk sensors, lava |
| Inside the trench | The trenches (except their bottom two layers) | Blocks pistons cannot push (below), fences and the like in certain lanes (below), runs of falling blocks (below) |
| Trench bottom | The bottom two layers of the trenches | Everything except air and liquids |
| Eater area | The inside, trenches excluded | Obsidian, ender chests, vaults, trial spawners, reinforced deepslate, kelp, waterlogged blocks |

### Height range

Scanning covers everything from just above the bedrock floor up to the "Maximum scanned Y" setting (default 128).

- Overworld: from Y = -59
- Nether: from Y = 5

The "Trench bottom" zone is the lowest two of those layers (Y = -59 and -58 in the Overworld).

### What "Inside the trench" looks for

- **Blocks pistons cannot push**: obsidian, crying obsidian, respawn anchors, reinforced deepslate, unbreakable blocks, extended pistons, as well as chests, furnaces, spawners, sculk sensors and similar blocks. Bedrock is left out because it is part of the terrain.
- **Blocks that change shape or state, in certain lanes**: walls, fences, glass panes and iron bars; blocks that change with a redstone signal such as doors, trapdoors, fence gates, pistons, dispensers, hoppers and observers; and redstone lamps. These are only highlighted in the 2nd, 5th, 8th, 11th … lane counted from the edge of the trench, because anywhere else they do not get in the trencher's way. They are never highlighted in a trench that is 3 wide.
- **Runs of falling blocks**: places where sand, gravel, concrete powder and similar blocks line up 10 or more times (adjustable) along the length of the trench. Air, liquids and blocks that pistons break are not counted, but they do not end a run either. Any other block ends it. These use the "Inside the trench" color.

### Narrow areas

If the area is too narrow for two trenches in one direction (for example a trench 12 wide in an area one chunk wide), only one trench is used in that direction. It goes on the side of the area that is farther from the world center (X = 0, Z = 0).

### Chunks not scanned yet

Chunks are scanned once they are loaded. While some are not loaded yet:

- A message at the bottom of the screen shows how many chunks are still not loaded.
- Those chunks are shown as large orange boxes (this can be turned off).

Once you get close and a chunk loads, it is scanned automatically.

### When highlights disappear

When a highlighted block is replaced by something else, its highlight disappears, including when another player breaks it. Changes made while you were too far away to see them are picked up when you come back and the chunk loads again.

## Settings

Open the settings with `/peri config`. Saved changes apply right away; if you are scanning, the area is rescanned with the new settings.

### General

| Setting | What it does | Default |
|---|---|---|
| North-south trench width | Width of the trenches at the north and south ends (lanes, 3–32) | 12 |
| East-west trench width | Width of the trenches at the east and west ends (lanes, 3–32) | 3 |
| Maximum scanned Y | Scanning goes up to this height | 128 |
| Highlight unscanned chunks | Shows chunks that have not been scanned yet as boxes | On |
| Unscanned chunk color | Color of those boxes | Orange |
| Exclude push-destroyed blocks from waterlogged check | Blocks that pistons break (leaves, coral, dripstone, …) are not highlighted even when waterlogged | On |
| Waterlogged blacklist | Blocks never highlighted for being waterlogged | Empty |

### Zone settings

Each zone ("One outside the trench", "Inside the trench", "Trench bottom", "Eater area") has:

| Setting | What it does |
|---|---|
| Scan this zone | Turn off to skip the zone. Scanning cannot start with every zone off |
| Highlight color | The zone's highlight color (white by default) |
| Include waterlogged blocks | Also highlight waterlogged blocks, such as stairs with water in them. Water itself is never highlighted (not in "Trench bottom") |
| Highlighted blocks | The blocks to highlight (not in "Trench bottom") |

Extra settings:

- **Inside the trench**
  - Falling block run threshold (2–64, default 10)
  - State-changing blocks (specific lanes only): blocks highlighted only in the 2nd, 5th, 8th, 11th … lane
- **Eater area**
  - Include trench area: when on, the eater area is the whole area including the trenches (off by default)

### Writing block lists

Put one of these on each line:

- A block ID: `minecraft:obsidian`
- A block tag, starting with `#`: `#minecraft:walls`
- One of PeriScan's special entries:

| Entry | Matches |
|---|---|
| `#periscan:immovable` | Every block pistons cannot push (except bedrock) |
| `#periscan:connecting` | Walls, fences, glass panes, iron bars and other blocks that connect to their neighbors |
| `#periscan:redstone_reactive` | Doors, trapdoors, fence gates, copper bulbs, pistons, redstone dust/torches/lamps, dispensers, hoppers and other blocks whose state changes when they receive a redstone signal (signal sources such as buttons and levers are not included) |

Mistyped entries, such as IDs that do not exist, are ignored. When you start scanning, the chat shows "ignored invalid config entry" for each of them.

## Placing schematics

Every perimeter uses the same schematics in the same positions. A **schematic profile** remembers which schematics those are and where each one goes, and `/peri schematic place` places all of them in Litematica, lined up with a profile's area.

**This needs Litematica.** Everything else works without it.

Schematic profiles are shared by all worlds and servers, unlike the profiles made with `/peri add`.

### Making a schematic profile

1. Open the profile's settings. Any name works; a profile that does not exist yet is created when you save.
   ```
   /peri schematic edit <schematic profile>
   ```
2. Under "Import schematics", add the files to use. The suggestions are the `.litematic` files in Litematica's schematics folder (usually `.minecraft/schematics`). Type to narrow them down, then click one.
3. Press "Save Changes". Each imported schematic now has its own section.
4. In each section, choose the corners to place the schematic on and its origin height, then save again.

- PeriScan **copies** the files into `config/periscan/schematics/<schematic profile>/`. You can move, rename or delete the originals afterwards.
- A changed original is not picked up by itself. Import the file again: a file with the same name replaces the copy and keeps the section's settings.
- Files with the same name cannot be imported in one save, even from different folders; none of them is imported and a message lists them. Rename one, or import them one save at a time (the later one then replaces the earlier).
- "Default origin Y" at the top is the origin height newly imported schematics start with. Set it before importing, for example to 5 for a Nether profile.
- To take a schematic out of the profile, turn on "Remove from profile" in its section and save. Its copy is deleted too.
- On Minecraft 1.19.4 there are no suggestions; type the path relative to Litematica's schematics folder.

To make a variant, for example a Nether version of an Overworld profile, copy it with `/peri schematic copy <from> <to>` and change the heights and corners in the copy.

### Saving your schematics

Save every file like this:

- Put the origin on the perimeter's **north-west corner block**
- The contents extend east (+X) and south (+Z) from the origin

For parts that repeat on several sides, save only one of them and place it on more than one corner.

### How schematics are placed

The corners are named by their X and Z side: `+-` is the +X / -Z corner.

| Corner | Where it goes |
|---|---|
| `--` | At the north-west corner, as saved |
| `+-` | At the north-east corner, flipped east-west |
| `-+` | At the south-west corner, flipped north-south |
| `++` | At the south-east corner, rotated 180° |

- The origin height is set per schematic. New schematics start with the profile's "Default origin Y" (-59 unless changed, just above the Overworld's bedrock floor; use 5 for the Nether).
- A schematic with no corner selected is not placed.
- Flipping also swaps left and right in circuits. A circuit that is not symmetric may stop working when flipped.
- Rotating 180° reverses the direction a machine faces. Where that matters (for example trenchers that must start in a fixed direction), use `+-` or `-+` instead of `++`.

### After placing

- The schematics appear in Litematica's placement list as `peri/<profile name>/…`. Their position is locked so they are not moved by accident.
- Running the command again for the same profile replaces what it placed before.
- If any file cannot be loaded, nothing is placed.
- `/peri schematic clear <name>` removes a profile's placements. Deleting the profile with `/peri remove` removes them too.
- Placements keep pointing at the schematic profile's copies. After removing a schematic or deleting a schematic profile, placements made from it in other worlds can no longer be loaded.

## Troubleshooting

| Message or problem | What to do |
|---|---|
| "all zones are disabled" | In `/peri config`, turn on "Scan this zone" for at least one zone |
| "profile '…' belongs to …" | Go to the dimension where you created the profile and try again |
| "… chunks not loaded yet" | Go near the orange boxes; those chunks are scanned once they load |
| "a saved profile is available" | Run `/peri scan reload` in that dimension to resume scanning |
| "litematica is not installed (or is an incompatible version)" | Install Litematica, or update it to the version for your Minecraft version |
| "no schematic profile named …" | Create it with `/peri schematic edit <schematic profile>`, or check the name with `/peri schematic list` |
| "… has nothing to place" | Open `/peri schematic edit <schematic profile>`, import schematics and select at least one corner |
| "import failed" | Check that the file exists in Litematica's schematics folder and ends in `.litematic` |
| "failed to load …" | Check that the file is not damaged and that Litematica can open it |
| Highlights are invisible with shaders on | Update Iris to its latest version |
