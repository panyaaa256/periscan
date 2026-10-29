# Using PeriScan

[日本語](usage.ja.md)

PeriScan finds the blocks within a given area that keep a World Eater or trencher from working, and highlights them. Highlights are drawn through blocks, so you can remove buried chests and obsidian in advance. With Litematica, it can also place the schematics you use often in bulk, lined up with the area.

- [Getting started](#getting-started)
- [Registering an area](#registering-an-area)
- [Scanning](#scanning)
- [What gets highlighted](#what-gets-highlighted)
- [Showing and hiding highlights](#showing-and-hiding-highlights)
- [Settings](#settings)
- [Placing schematics](#placing-schematics)
- [Commands](#commands)
- [Troubleshooting](#troubleshooting)

## Getting started

1. Register the perimeter's area as a profile, with a name.
   ```
   /peri add <x1> <z1> <x2> <z2> <name>
   ```
2. Scan the profile's area.
   ```
   /peri scan start <name>
   ```
3. Remove the highlighted blocks. A highlight disappears by itself once its block is gone.

## Registering an area

Register an area with `/peri add <x1> <z1> <x2> <z2> <name>`. A registered area is called a profile.

### Coordinates

The coordinates are chunk coordinates. You can find the chunk you are in on the debug screen (F3). Press Tab while typing a coordinate to get the chunk you are standing in as a suggestion.

Give the two chunks at opposite corners of the perimeter's area, in either order. The smallest area is one chunk (16 × 16 blocks), and the largest is a square of 512 × 512 chunks (8192 × 8192 blocks).

### Names

Profile names can contain letters, digits and `_` `-` `.` `+`. They cannot contain spaces.

### Dimensions

A profile belongs to the dimension where you ran `/peri add`. You can only scan it and place schematics for it while you are in that dimension. PeriScan cannot be used in the End.

### Where profiles are saved

Profiles are saved per world (per server in multiplayer). `/peri list` lists the profiles you registered.

Deleting a profile with `/peri remove <name>` also removes the schematics placed for it.

## Scanning

### Starting, stopping and scanning again

`/peri scan start <name>` starts scanning a profile's area. `/peri scan clear` removes the highlights and stops scanning. The profile is kept.

`/peri scan reload` rescans the profile you scanned last from scratch. It also resumes scanning the same profile after you have logged out.

### How the scan progresses

How chunks get scanned depends on whether the server has PeriScan. In singleplayer, the game's own server has PeriScan too, so the scan always progresses as when the server has it.

#### When the server has PeriScan (Minecraft 1.21.9 and newer)

The server scans every chunk of the area, including chunks no player has loaded. Until the scan is done, the bottom of the screen shows "the server is scanning - … chunks left", and a large orange box marks each chunk not scanned yet. To hide the boxes, turn off "Highlight unscanned chunks" in the settings.

Runs of falling blocks are highlighted once the whole area has been scanned.

Chunks that are not generated yet are not scanned. When the scan is done, the chat says how many there are. Generate them, by going near them for example, then run `/peri scan reload`.

#### When the server does not have PeriScan

A chunk is scanned when it loads in your game. While some chunks are not loaded yet, the bottom of the screen shows "… chunks not loaded yet", and a large orange box marks each of them. The boxes are hidden the same way as above.

A chunk is scanned once you get close enough for it to load.

### When blocks change

Highlights follow block changes right away, including changes made by other players.

- When a highlighted block is replaced by something else, its highlight disappears.
- When a block that should be highlighted appears, such as obsidian formed by lava and water, it is highlighted right away.
- Runs of falling blocks are updated when one of their blocks is broken, or when falling sand and gravel land.

When the server has PeriScan, changes anywhere in the area show up, including changes in chunks you have not loaded. Your own changes take a moment longer to show, as they go through the server.

When the server does not have PeriScan, changes made while you were too far away to see them show up when you come back and the chunk loads again.

## What gets highlighted

### The four zones

The registered area is scanned in the four zones below. Each zone has its own block list and color in the settings.

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
| One outside the trench | One line on each side of every trench: the line just outside the area, and the line just inside the trench | Sculk sensors, calibrated sculk sensors, lava |
| Inside the trench | The trenches, except their bottom two layers while "Trench bottom" is on | Blocks pistons cannot push, fences and the like in certain lanes, runs of falling blocks (see the section below) |
| Trench bottom | The bottom two layers of the trenches | When turned on, everything except air and liquids |
| Eater area | The inside, trenches excluded | Obsidian, ender chests, vaults, trial spawners, reinforced deepslate, kelp, waterlogged blocks |

### Scanned height

The scan covers the layers from just above the bedrock floor up to the "Maximum scanned Y" setting (default 128). Where the bottom is depends on the dimension: Y = -59 in the Overworld and Y = 5 in the Nether.

The "Trench bottom" zone is the lowest two of those layers: Y = -59 and -58 in the Overworld.

### What "Inside the trench" looks for

"Inside the trench" highlights these three kinds of blocks.

#### Blocks pistons cannot push

Obsidian, crying obsidian, respawn anchors, reinforced deepslate, unbreakable blocks and extended pistons, as well as chests, furnaces, spawners, sculk sensors and similar blocks. Bedrock is left out, as it is part of the terrain.

#### Blocks that change shape or state, in certain lanes

Walls, fences, glass panes and iron bars, and the blocks that change when they receive a redstone signal, such as doors, trapdoors, fence gates, pistons and redstone lamps. They are only highlighted in the 2nd, 5th, 8th, 11th … lane counted from the edge of the trench. In a trench that is 3 wide, they are never highlighted.

#### Runs of falling blocks

Places where sand, gravel, concrete powder and similar blocks line up 10 or more times along the length of the trench. The number can be changed in the settings. Air, liquids and blocks that pistons break are not counted, but they do not end a run either.

## Showing and hiding highlights

Highlights can be hidden without stopping the scan, all of them at once or zone by zone. The scan goes on while they are hidden, so the highlights are up to date when you show them again.

Use "Show highlights" and "Show this zone" in the settings, or the keys below. The keys are in the "PeriScan" category of Options > Controls > Key Binds. None has a key assigned at first; assign the ones you want to use.

| Key | What it does |
|---|---|
| Open Settings | Opens the settings, like `/peri config` |
| Show/Hide Highlights | Hides or shows everything PeriScan draws. Same as "Show highlights" in the settings |
| Rescan | Same as `/peri scan reload` |
| Clear Scan | Same as `/peri scan clear` |
| Show/Hide One Outside the Trench | Hides or shows the highlights of that zone. Same as "Show this zone" in the settings |
| Show/Hide Inside the Trench | Same as above. Runs of falling blocks are hidden with it |
| Show/Hide Trench Bottom | Same as above |
| Show/Hide Eater Area | Same as above |

What you hide stays hidden after restarting the game, until you show it again.

A zone with "Scan this zone" turned off has nothing to highlight. Its key only tells you that the zone is not enabled.

## Settings

The settings screen opens with `/peri config`. With Mod Menu installed, the settings button of PeriScan in Mod Menu opens it too. Changes apply when you save them. If you are scanning, the area is rescanned with the new settings.

The settings screens need YACL. On Minecraft 1.21.9 and newer, PeriScan also runs without YACL, with the default settings.

### General

| Setting | What it does | Default |
|---|---|---|
| North-south trench width | Width of the trenches at the north and south ends, in lanes (3–32) | 12 |
| East-west trench width | Width of the trenches at the east and west ends, in lanes (3–32) | 3 |
| Maximum scanned Y | The highest Y coordinate that is scanned | 128 |
| Show highlights | Turn off to hide everything PeriScan draws, both the highlights and the unscanned chunk boxes. Scanning continues | On |
| Highlight unscanned chunks | Shows chunks that have not been scanned yet as boxes | On |
| Unscanned chunk color | Color of the boxes above | Orange |
| Exclude push-destroyed blocks from waterlogged check | Blocks that pistons break, such as leaves, coral and dripstone, are not highlighted even when waterlogged | On |
| Waterlogged blacklist | Blocks never highlighted for being waterlogged | Empty |

### Zone settings

Each of the four zones has these settings.

| Setting | What it does |
|---|---|
| Scan this zone | Turn off to skip the zone. With every zone off, nothing is scanned |
| Show this zone | Turn off to hide the zone's highlights. The zone is still scanned |
| Highlight color | The zone's highlight color. White for every zone by default |
| Include waterlogged blocks | Also highlights waterlogged blocks, such as stairs with water in them |
| Highlighted blocks | The list of blocks to highlight. "Trench bottom" does not have it |

These settings are only in the zones listed.

| Zone | Setting | What it does |
|---|---|---|
| Inside the trench | Falling block run threshold | How many falling blocks in a row get highlighted (2–64, default 10) |
| Inside the trench | State-changing blocks (specific lanes only) | Blocks highlighted only in the 2nd, 5th, 8th, 11th … lane |
| Eater area | Include trench area | When on, the eater area is the whole area, trenches included (off by default) |

### Writing block lists

Put one of these on each line of a block list:

- A block ID, such as `minecraft:obsidian`
- A block tag, starting with `#`, such as `#minecraft:walls`
- One of PeriScan's special tags (see the table below)

| Special tag | Matches |
|---|---|
| `#periscan:immovable` | Every block pistons cannot push. Bedrock is not included |
| `#periscan:connecting` | Walls, fences, glass panes, iron bars and other blocks whose shape changes as they connect to their neighbors |
| `#periscan:redstone_reactive` | Doors, trapdoors, fence gates, copper bulbs, pistons, redstone dust/torches/lamps, dispensers, hoppers and other blocks whose state changes when they receive a redstone signal. Blocks that only send a signal, such as buttons and levers, are not included |

The lists suggest block IDs and tags as you type. The world's tags are only suggested when you open the settings while in a world. You can still type anything.

Mistyped entries, such as IDs that do not exist, are ignored. They are listed in the top right corner of the screen. When you start scanning, the chat also warns "ignored invalid config entry" for each of them.

## Placing schematics

Every perimeter uses the same schematics in the same positions. Register in a schematic profile which schematics go where, and `/peri schematic place` places all of them at once. Litematica places them, lined up with a profile's area.

This feature needs Litematica. Everything else works without it.

Schematic profiles are shared by all worlds and servers. This differs from the profiles made with `/peri add`, which are saved per world.

### Saving your schematics

Save every schematic file like this:

- Put the origin on the perimeter's north-west corner block.
- Make the contents extend east (+X) and south (+Z) from the origin.

For parts that repeat on several sides, save only one of them and place it on more than one corner.

### Making a schematic profile

1. Make a schematic profile and open its settings. Any name works. Use `create` to make a new one, and `edit` to open one you already made.
   ```
   /peri schematic create <schematic profile>
   /peri schematic edit <schematic profile>
   ```
2. Under "Import schematics", add the files to use. The suggestions are the `.litematic` files in Litematica's schematics folder (usually `.minecraft/schematics`). Type to narrow them down, then click one.
3. Press "Save Changes". Each imported schematic now has its own section.
4. In each section, choose the corners to place the schematic on and its origin height. Then save again.

PeriScan copies the imported files into `config/periscan/schematics/<schematic profile>/`. After importing, you can move, rename or delete the original files.

A changed original is not picked up by the copy. Import the file again. A file with the same name replaces only the copy, and the section's settings are kept.

Files with the same name cannot be imported in one save, even from different folders. None of them is imported, and a message lists them. Rename one, or import them one save at a time. The one imported later then replaces the earlier one.

"Default origin Y" at the top of the settings is the origin height that newly imported schematics start with. Set it before importing, for example to 5 for a Nether profile.

To take a schematic out of the profile, turn on "Remove from profile" in its section and save. The copy in the profile is deleted too.

Sometimes a small change is all you need, as when making a Nether version of an Overworld profile. Then copy the profile with `/peri schematic copy <from> <to>`, and change the heights and corners in the copy.

### How each corner is placed

A corner is named by its X side and its Z side. For example, `+-` is the corner on the +X side and the -Z side.

| Corner | Where and how it goes |
|---|---|
| `--` | At the north-west corner, as saved |
| `+-` | At the north-east corner, flipped east-west |
| `-+` | At the south-west corner, flipped north-south |
| `++` | At the south-east corner, rotated 180° |

Flipping also swaps left and right in circuits. A circuit that is not symmetric may stop working when flipped. Rotating 180° reverses the direction a machine faces. When "Keep the saved orientation" is on in a schematic's section, it is placed without being flipped or rotated.

The origin height can be set per schematic. A newly imported schematic's origin height is the profile's "Default origin Y", which is -59 by default.

A schematic with no corner selected is not placed.

### After placing

The placed schematics appear in Litematica's placement list as `peri/<profile name>/…`. Their position is locked so they are not moved by accident.

Placing again for the same profile replaces what was placed before. If any file cannot be loaded, nothing is placed.

`/peri schematic clear <name>` removes a profile's placements. Deleting the profile with `/peri remove` removes them too.

Placements keep pointing at the copies in the schematic profile. So after removing a schematic or deleting a schematic profile, placements made from it can no longer be loaded in other worlds.

## Commands

| Command | What it does |
|---|---|
| `/peri add <x1> <z1> <x2> <z2> <name>` | Registers an area as a profile |
| `/peri remove <name>` | Deletes a profile, together with the schematics placed for it |
| `/peri list` | Lists your profiles |
| `/peri scan start <name>` | Starts scanning a profile's area |
| `/peri scan clear` | Removes the highlights and stops scanning. The profile is kept |
| `/peri scan reload` | Rescans the profile you scanned last from scratch |
| `/peri schematic create <schematic profile>` | Creates a new schematic profile |
| `/peri schematic edit <schematic profile>` | Opens a schematic profile's settings |
| `/peri schematic place <name> <schematic profile>` | Places a schematic profile's schematics for a profile's area |
| `/peri schematic clear <name>` | Removes the schematics placed for a profile. The profile and the schematic profile are kept |
| `/peri schematic list` | Lists your schematic profiles |
| `/peri schematic copy <from> <to>` | Copies a schematic profile, together with its schematics, under a new name |
| `/peri schematic remove <schematic profile>` | Deletes a schematic profile, together with its copies of the schematics |
| `/peri config` | Opens the settings screen |

## Troubleshooting

| Message or problem | What to do |
|---|---|
| "all zones are disabled" | In `/peri config`, turn on "Scan this zone" for at least one zone |
| "profile '…' belongs to …" | Go to the dimension where you created the profile and try again |
| "a saved profile is available" | Run `/peri scan reload` in that dimension to resume scanning |
| "the server is scanning - … chunks left" | Wait. The server scans the rest of the area on its own |
| "… chunks of the region are not generated yet" | Generate those chunks, by going near them for example, then run `/peri scan reload` |
| "… chunks could not be read from the server's save" | The server log says which chunks and why. Their save data may be corrupted |
| "… chunks not loaded yet" | Go near the orange boxes, and those chunks are scanned once they load. This is shown when the server does not have PeriScan |
| No highlights although a scan is running | Check "Show highlights" and "Show this zone" for each zone in the settings. They may still be hidden with a key |
| "… is not enabled" when pressing a zone's key | Turn on "Scan this zone" for that zone in the settings |
| "YetAnotherConfigLib (YACL) is not installed" | Install YACL to open the settings screens. Until then, the default settings are used |
| "litematica is not installed (or is an incompatible version)" | Install Litematica, or update it to the version for your Minecraft version |
| "no schematic profile named …" | Create it with `/peri schematic create <schematic profile>`, or check the name with `/peri schematic list` |
| "… has nothing to place" | Import schematics with `/peri schematic edit` or `create`, and select at least one corner |
| "import failed" | Check that the file is in Litematica's schematics folder and that its name ends in `.litematic` |
| "failed to load …" | Check that the file is not corrupted and that Litematica can open it |
| Highlights are invisible with shaders on | Update Iris to its latest version |
