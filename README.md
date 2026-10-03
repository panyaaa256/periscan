# PeriScan

[日本語](README.ja.md)

A Fabric mod that highlights the blocks you need to clear out before running a World Eater or trencher. Give it the area of your perimeter and it shows the problem blocks, even through walls.

- Highlights blocks that would stop a trencher or World Eater
- Separate block lists and colors for the trenches, the area around them and the perimeter's area
- Areas can be saved per world as named profiles
- Places schematics in bulk with Litematica

See [the usage guide](docs/usage.md) for how to use it.

## Requirements

- Minecraft 1.20.5 to 1.21.4
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) 3.6.6 or newer (3.8.2 or newer on 1.21 and later)
- Optional: [Litematica](https://modrinth.com/mod/litematica), needed for placing schematics in bulk

PeriScan only needs to be installed on the client. It does not need to be installed on the server.

## Installing

Put the PeriScan jar, together with Fabric API and YACL, into the `mods` folder of your Fabric installation.

## Building from source

You need JDK 21 or newer.

```sh
git clone -b mc/1.20.5-1.21.4 https://github.com/panyaaa256/periscan.git
cd periscan
./gradlew buildAndCollect
```

On Windows, use `gradlew.bat buildAndCollect` instead of `./gradlew buildAndCollect`.

This builds every Minecraft version from 1.20.5 to 1.21.4.

The mods are built to `build/libs/<version>/`.

## License

[MIT](LICENSE)
