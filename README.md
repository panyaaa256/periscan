# PeriScan

[日本語](README.ja.md)

A Fabric mod that highlights the blocks you need to clear out before running a world eater or trencher. Give it a perimeter's area and it shows the problem blocks, even through walls.

- Highlights blocks that would stop a trencher or world eater (obsidian, chests, spawners, sculk sensors, lava, waterlogged blocks, long runs of sand and gravel, …)
- Separate block lists and colors for the trenches, the area around them and the inside of the perimeter
- Highlights update by themselves as blocks are removed
- Areas are saved per world as named profiles
- With Litematica, places all of your perimeter schematics at once

See **[the usage guide](docs/usage.md)** for how to use it.

## Minecraft versions

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
| 1.20.3 – 1.20.4 | `periscan-<version>+1.20.4.jar` | [`mc/1.19.4-1.20.4`](https://github.com/panyaaa256/periscan/tree/mc/1.19.4-1.20.4) |
| 1.20 – 1.20.1 | `periscan-<version>+1.20.1.jar` | [`mc/1.19.4-1.20.4`](https://github.com/panyaaa256/periscan/tree/mc/1.19.4-1.20.4) |
| 1.19.4 | `periscan-<version>+1.19.4.jar` | [`mc/1.19.4-1.20.4`](https://github.com/panyaaa256/periscan/tree/mc/1.19.4-1.20.4) |

1.20.2 is not supported, as YACL only has beta builds for it.

## Requirements

- Minecraft 1.19.4 to 26.3 (see the table above)
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) for your Minecraft version
- Optional: [Litematica](https://modrinth.com/mod/litematica), for placing schematics
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu), for opening the settings from the mod list

PeriScan only needs to be installed on your own game. It also works on servers that do not have it.

## Installing

Put the PeriScan jar for your Minecraft version (see the table above), together with Fabric API and YACL, into the `mods` folder of your Fabric installation.

## Building from source

You need JDK 25.

```sh
git clone https://github.com/panyaaa256/periscan.git
cd periscan
./gradlew buildAndCollect
```

On Windows, use `gradlew.bat buildAndCollect` instead of `./gradlew buildAndCollect`.

This builds every Minecraft version from 1.21.9 to 26.3. For older Minecraft versions, clone the matching `mc/<range>` branch instead.

The mods are built to `build/libs/<version>/periscan-<version>+<Minecraft version>.jar` (the file ending in `-sources.jar` is not the mod).

## License

[MIT](LICENSE)
