# PeriScan

[日本語](README.ja.md)

A Fabric mod that highlights the blocks you need to clear out before running a world eater or trencher. Give it a perimeter's area and it shows the problem blocks, even through walls.

- Highlights blocks that would stop a trencher or world eater (obsidian, chests, spawners, sculk sensors, lava, waterlogged blocks, long runs of sand and gravel, …)
- Separate block lists and colors for the trenches, the area around them and the inside of the perimeter
- Highlights update by themselves as blocks are removed
- Areas are saved per world as named profiles
- With Litematica, places all of your perimeter schematics at once

See **[the usage guide](docs/usage.md)** for how to use it.

## Requirements

- Minecraft 1.21.5 to 1.21.8
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) 3.8.2 or newer
- Optional: [Litematica](https://modrinth.com/mod/litematica), for placing schematics

PeriScan only needs to be installed on your own game. It also works on servers that do not have it.

## Installing

Put the PeriScan jar, together with Fabric API and YACL, into the `mods` folder of your Fabric installation.

## Building from source

You need JDK 21 or newer.

```sh
git clone -b mc/1.21.5-1.21.8 https://github.com/panyaaa256/periscan.git
cd periscan
./gradlew buildAndCollect
```

On Windows, use `gradlew.bat buildAndCollect` instead of `./gradlew buildAndCollect`.

This builds every Minecraft version from 1.21.5 to 1.21.8.

The mods are built to `build/libs/<version>/periscan-<version>+<Minecraft version>.jar` (the file ending in `-sources.jar` is not the mod).

## License

[MIT](LICENSE)
