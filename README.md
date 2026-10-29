# PeriScan

[日本語](README.ja.md)

PeriScan is a Fabric mod that highlights the blocks you need to clear out before running a World Eater or trencher. Give it the area of your perimeter, and it shows the problem blocks, even through walls.

## What it does

- Highlights the blocks that would stop a trencher or World Eater.
- Lets you set the highlighted blocks and colors separately for the trenches, the area around them and the inside of the perimeter.
- Saves areas per world as named profiles.
- Places the schematics you use often in bulk, with Litematica.

The [usage guide](docs/usage.md) explains how to use it in detail.

## Installing

### Requirements

- Minecraft 1.20 to 26.3 (except 1.20.2; see the version table below)
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) for your Minecraft version
- Optional: [Litematica](https://modrinth.com/mod/litematica), needed for placing schematics in bulk
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu)

YACL is only needed to open the settings screens. Without it, PeriScan runs with the default settings.

### Installing on the client

PeriScan works with only the client having it. Put the PeriScan jar for your Minecraft version into the `mods` folder, together with Fabric API and YACL. The version table below shows which jar to use.

### Installing on the server too (Minecraft 1.21.9 and newer)

On Minecraft 1.21.9 and newer, PeriScan can also be installed on the server. When the server has it, the server is the one that scans the area.

The server scans the whole area wherever the players are, including chunks no player has loaded. Block changes anywhere in the area show up in the highlights, even far away from you. Chunks that are not generated yet are not scanned, and the chat says how many there are.

The server needs two things: PeriScan and Fabric API. It does not need YACL. In singleplayer, the game's own server has PeriScan too, so this works as is.

### Version table

The jar to use for each Minecraft version, and the branch its source is on:

| Minecraft | Jar | Branch |
|---|---|---|
| 26.3 | `periscan-<version>+26.3.jar` | `main` |
| 26.2 | `periscan-<version>+26.2.jar` | `main` |
| 26.1 – 26.1.2 | `periscan-<version>+26.1.2.jar` | `main` |
| 1.21.11 | `periscan-<version>+1.21.11.jar` | `main` |
| 1.21.9 – 1.21.10 | `periscan-<version>+1.21.10.jar` | `main` |
| 1.21.6 – 1.21.8 | `periscan-<version>+1.21.8.jar` | [`mc/1.21.5-1.21.8`](https://github.com/panyaaa256/periscan/tree/mc/1.21.5-1.21.8) |
| 1.21.5 | `periscan-<version>+1.21.5.jar` | [`mc/1.21.5-1.21.8`](https://github.com/panyaaa256/periscan/tree/mc/1.21.5-1.21.8) |
| 1.21.4 | `periscan-<version>+1.21.4.jar` | [`mc/1.20.5-1.21.4`](https://github.com/panyaaa256/periscan/tree/mc/1.20.5-1.21.4) |
| 1.21.2 – 1.21.3 | `periscan-<version>+1.21.3.jar` | [`mc/1.20.5-1.21.4`](https://github.com/panyaaa256/periscan/tree/mc/1.20.5-1.21.4) |
| 1.21 – 1.21.1 | `periscan-<version>+1.21.1.jar` | [`mc/1.20.5-1.21.4`](https://github.com/panyaaa256/periscan/tree/mc/1.20.5-1.21.4) |
| 1.20.5 – 1.20.6 | `periscan-<version>+1.20.6.jar` | [`mc/1.20.5-1.21.4`](https://github.com/panyaaa256/periscan/tree/mc/1.20.5-1.21.4) |
| 1.20.3 – 1.20.4 | `periscan-<version>+1.20.4.jar` | [`mc/1.20-1.20.4`](https://github.com/panyaaa256/periscan/tree/mc/1.20-1.20.4) |
| 1.20 – 1.20.1 | `periscan-<version>+1.20.1.jar` | [`mc/1.20-1.20.4`](https://github.com/panyaaa256/periscan/tree/mc/1.20-1.20.4) |

1.20.2 is not supported, as YACL only has beta builds for it.

## Building from source

You need JDK 25.

```sh
git clone https://github.com/panyaaa256/periscan.git
cd periscan
./gradlew buildAndCollect
```

On Windows, run `gradlew.bat buildAndCollect` instead of the last command.

The default branch builds the jars for Minecraft 1.21.9 to 26.3. For older versions, clone the matching `mc/<range>` branch from the version table and build it. The jars are put in `build/libs/<version>/`.

## License

[MIT](LICENSE)
