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
| `/peri schematic <name> [set]` | Places schematics for a profile (see [below](#placing-schematics)) |
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

### When blocks change

Highlights follow block changes right away, including changes made by other players. When a highlighted block is replaced by something else, its highlight disappears; when a block that should be highlighted appears, such as obsidian formed by lava and water, it is highlighted at once. Runs of falling blocks are updated the same way when one of their blocks is broken or when falling sand and gravel land. Changes made while you were too far away to see them are picked up when you come back and the chunk loads again.

## Settings

Open the settings with `/peri config`, or with the settings button of PeriScan in Mod Menu if you have it installed. Saved changes apply right away; if you are scanning, the area is rescanned with the new settings.

### General

| Setting | What it does | Default |
|---|---|---|
| North-south trench width | Width of the trenches at the north and south ends (lanes, 3–32) | 12 |
| East-west trench width | Width of the trenches at the east and west ends (lanes, 3–32) | 3 |
| Maximum scanned Y | Scanning goes up to this height | 128 |
| Peri schematics folder | Folder used for [placing schematics](#placing-schematics) | peri |
| Edge placement corners | How edge schematics are placed ([see below](#how-schematics-are-placed)) | ++ / -- |
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

On Minecraft 1.20 and newer, the lists suggest block IDs and tags as you type. The world's tags are only suggested when you open the settings while in a world. You can still type anything.

Mistyped entries, such as IDs that do not exist, are ignored. When you save the settings, a message in the top right corner lists them, and when you start scanning, the chat shows "ignored invalid config entry" for each of them.

## Placing schematics

Every perimeter uses the same schematics in the same positions. `/peri schematic` places all of them in Litematica, lined up with a profile's area.

**This needs Litematica.** Everything else works without it.

### Folder layout

Put your files in Litematica's schematics folder (usually `.minecraft/schematics`) like this:

```
schematics/
  peri/              <- the "Peri schematics folder" setting
    ow/              <- a set (for the Overworld)
      all/           <- placed once
      edge/          <- placed twice, on opposite sides
    nether/          <- a set (for the Nether)
      all/
      edge/
        mx/          <- paired by flipping east-west
        mz/          <- paired by flipping north-south
```

- Choose the set with `/peri schematic <name> <set>`. Without it, `ow` is used in the Overworld and `nether` in the Nether. Press Tab to see the sets you have.
- Every `.litematic` file in these folders is placed.

### Saving your schematics

Save every file like this:

- Put the origin on the perimeter's **north-west corner block**
- The origin's height is **Y = -59** in the Overworld and **Y = 5** in the Nether
- The contents extend east (+X) and south (+Z) from the origin

In `edge`, save only the half of the perimeter that can be reused for the opposite side.

### How schematics are placed

| Folder | Where it goes |
|---|---|
| `all/` | Once at the north-west corner, as saved |
| `edge/` ("Edge placement corners" is `++ / --`) | As saved at the north-west corner + rotated 180° at the south-east corner |
| `edge/` ("Edge placement corners" is `+- / -+`) | Flipped east-west at the north-east corner + flipped north-south at the south-west corner |
| `edge/mx/` (Nether) | As saved at the north-west corner + flipped east-west at the north-east corner |
| `edge/mz/` (Nether) | As saved at the north-west corner + flipped north-south at the south-west corner |

- In the Nether, files cannot go directly in `edge/`; sort them into `mx/` or `mz/`. Nether trenchers always start in a fixed direction, so the opposite copy is flipped instead of rotated 180°. Which way to flip depends on the contents, so the folder tells PeriScan.
- The "Edge placement corners" setting is not used in the Nether.
- Flipping also swaps left and right in circuits. A circuit that is not symmetric may stop working when flipped.

### After placing

- The schematics appear in Litematica's placement list as `peri/<profile name>/…`. Their position is locked so they are not moved by accident.
- Running the command again for the same profile replaces what it placed before.
- If any file cannot be loaded, nothing is placed.
- Deleting the profile with `/peri remove` also removes its placements.

## Troubleshooting

| Message or problem | What to do |
|---|---|
| "all zones are disabled" | In `/peri config`, turn on "Scan this zone" for at least one zone |
| "profile '…' belongs to …" | Go to the dimension where you created the profile and try again |
| "… chunks not loaded yet" | Go near the orange boxes; those chunks are scanned once they load |
| "a saved profile is available" | Run `/peri scan reload` in that dimension to resume scanning |
| "litematica is not installed (or is an incompatible version)" | Install Litematica, or update it to the version for your Minecraft version |
| "… has .litematic files directly in edge" | In a Nether set, move the files in `edge/` into `edge/mx/` or `edge/mz/` |
| "failed to load …" | Check that the file is not damaged and that Litematica can open it |
| Highlights are invisible with shaders on | Update Iris to its latest version |
