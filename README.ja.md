# PeriScan

[English](README.md)

World Eater や trencher を動かす前に取り除くべきブロックを、ハイライトする Fabric mod です。露天掘りの範囲を指定すると、邪魔になるブロックが壁越しにも見えるようになります。

- trencher や World Eater を止めてしまうブロックをハイライト
- トレンチ、その周り、露天掘りの範囲で、ハイライトするブロックと色を別々に設定可能
- 範囲は名前付きのプロファイルとしてワールドごとに保存可能
- Litematica を使用した設計図の一括配置

使い方は[使い方ガイド](docs/usage.ja.md)を確認してください。

## 対応済みの Minecraft のバージョン

| バージョン | jar | ブランチ |
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

## 必要な環境

- Minecraft 1.20〜26.3（上の表を参照）
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 以降
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl)（Minecraft のバージョンに合うもの）
- 任意: [Litematica](https://modrinth.com/mod/litematica)（設計図の一括配置で必要）
- 任意: [Mod Menu](https://modrinth.com/mod/modmenu)

PeriScan はクライアント側に導入するだけで使えます。サーバー側に入れる必要はありません。

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

デフォルトブランチにおいては Minecraft 1.21.9〜26.3 に対応するバージョンがビルドされます。それより古いバージョンは、対応する `mc/<範囲>` ブランチを clone してください。

mod は `build/libs/<バージョン>/` に作られます。

## ライセンス

[MIT](LICENSE)
