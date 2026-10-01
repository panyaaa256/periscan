# PeriScan

[English](README.md)

ワールドイーターやトレンチャーを動かす前に取り除くべきブロックを、ハイライトする Fabric mod です。ペリメータの範囲を指定すると、邪魔になるブロックが壁越しにも見えるようになります。

- トレンチャーやワールドイーターを止めてしまうブロック（黒曜石、チェスト、スポナー、スカルクセンサー、溶岩、水没したブロック、長く連なった砂や砂利など）をハイライト
- トレンチ、その周り、ペリメータの内側で、ハイライトするブロックと色を別々に設定可能
- ブロックを取り除くと、ハイライトも自動で消える
- 範囲は名前付きのプロファイルとしてワールドごとに保存
- Litematica があれば、ペリメータ用のスケマティックをまとめて配置

使い方は **[使い方ガイド](docs/usage.ja.md)** を見てください。

## 対応する Minecraft のバージョン

| Minecraft | jar | ブランチ |
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

1.20.2 は、YACL に beta 版しかないため対象外です。

## 必要なもの

- Minecraft 1.20〜26.3（上の表を参照）
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 以降
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl)（Minecraft のバージョンに合うもの）
- 任意: [Litematica](https://modrinth.com/mod/litematica)（スケマティックの配置に使います）
- 任意: [Mod Menu](https://modrinth.com/mod/modmenu)（mod 一覧から設定画面を開けます）

PeriScan は自分のゲームに入れるだけで使えます。PeriScan が入っていないサーバーでも動きます。

## 導入

Minecraft のバージョンに合う PeriScan の jar（上の表を参照）を、Fabric API・YACL と一緒に Fabric 環境の `mods` フォルダに入れてください。

## ソースからのビルド

JDK 25 が必要です。

```sh
git clone https://github.com/panyaaa256/periscan.git
cd periscan
./gradlew buildAndCollect
```

Windows では `./gradlew buildAndCollect` の代わりに `gradlew.bat buildAndCollect` を使ってください。

これで Minecraft 1.21.9〜26.3 の全バージョンがビルドされます。それより古いバージョンは、対応する `mc/<範囲>` ブランチを clone してください。

mod は `build/libs/<バージョン>/periscan-<バージョン>+<Minecraftのバージョン>.jar` に作られます（`-sources.jar` で終わるファイルは mod ではありません）。

## ライセンス

[MIT](LICENSE)
