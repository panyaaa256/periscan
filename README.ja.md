# PeriScan

[English](README.md)

PeriScanは、World Eaterやtrencherを動かす前に取り除くべきブロックをハイライトするFabric modです。露天掘りの範囲を指定すると、邪魔になるブロックが壁越しに見えるようになります。

## できること

- trencherやWorld Eaterの動作を妨げるブロックをハイライトします。
- トレンチ、その周り、露天掘りの内側のそれぞれで、ハイライトするブロックと色を設定できます。
- 範囲に名前を付けて、プロファイルとしてワールドごとに保存できます。
- Litematicaを使って、よく使う設計図をまとめて配置できます。

使い方の詳細は[使い方ガイド](docs/usage.ja.md)にあります。

## 導入

### 必要なもの

- Minecraft 1.20〜26.3（1.20.2を除く。詳しくは下の対応表）
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3以降
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl)（Minecraftのバージョンに合うもの）
- 任意: [Litematica](https://modrinth.com/mod/litematica)（設計図をまとめて配置するときに必要）
- 任意: [Mod Menu](https://modrinth.com/mod/modmenu)

Minecraft 1.21.9以降では、YACLが必要なのは設定画面を開くときだけです。入っていなければ、PeriScanは初期設定のまま動きます。

### クライアントへの導入

PeriScanは、クライアントに入れるだけで使えます。Minecraftのバージョンに合うPeriScanのjarを、Fabric APIとYACLと一緒に`mods`フォルダへ入れてください。jarの選び方は下の対応表にあります。

### サーバーにも導入する（Minecraft 1.21.9以降）

Minecraft 1.21.9以降では、PeriScanをサーバーにも入れられます。サーバーに入っている場合、範囲をスキャンするのはサーバーです。

サーバーは、プレイヤーがどこにいても範囲全体をスキャンします。どのプレイヤーも読み込んでいないチャンクも対象です。範囲内のブロックの変化も、遠く離れた場所のものまでハイライトに反映されます。まだ生成されていないチャンクはスキャンせず、その数をチャットで知らせます。

サーバーに入れるのはPeriScanとFabric APIの2つです。サーバーにYACLは要りません。シングルプレイでは、ゲーム内部のサーバーにもPeriScanが入ります。そのため、何もしなくてもこの動き方になります。

### 対応表

Minecraftのバージョンごとに、使うjarと、そのソースがあるブランチは次のとおりです。

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

1.20.2には対応していません。YACLにbeta版しかないためです。

## ソースからビルドする

ビルドにはJDK 25が必要です。

```sh
git clone https://github.com/panyaaa256/periscan.git
cd periscan
./gradlew buildAndCollect
```

Windowsでは、最後のコマンドの代わりに`gradlew.bat buildAndCollect`を実行してください。

デフォルトブランチでビルドされるのは、Minecraft 1.21.9〜26.3向けのjarです。それより古いバージョン向けは、対応表にある`mc/<範囲>`ブランチをcloneしてビルドしてください。できたjarは`build/libs/<バージョン>/`に入ります。

## ライセンス

[MIT](LICENSE)
