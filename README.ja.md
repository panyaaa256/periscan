# PeriScan

[English](README.md)

World Eater や trencher を動かす前に取り除くべきブロックを、ハイライトする Fabric mod です。露天掘りの範囲を指定すると、邪魔になるブロックが壁越しにも見えるようになります。

- trencher や World Eater を止めてしまうブロックをハイライト
- トレンチ、その周り、露天掘りの範囲で、ハイライトするブロックと色を別々に設定可能
- 範囲は名前付きのプロファイルとしてワールドごとに保存可能
- Litematica を使用した設計図の一括配置

使い方は[使い方ガイド](docs/usage.ja.md)を確認してください。

## 必要な環境

- Minecraft 1.20.5〜1.21.4
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 以降
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) 3.6.6 以降（1.21 以降は 3.8.2 以降）
- 任意: [Litematica](https://modrinth.com/mod/litematica)（設計図の一括配置で必要）

PeriScan はクライアント側に導入するだけで使えます。サーバー側に入れる必要はありません。

## 導入

PeriScan の jar を、Fabric API・YACL と一緒に Fabric 環境の `mods` フォルダに入れてください。

## ソースからのビルド

JDK 21 以降が必要です。

```sh
git clone -b mc/1.20.5-1.21.4 https://github.com/panyaaa256/periscan.git
cd periscan
./gradlew buildAndCollect
```

Windows では `./gradlew buildAndCollect` の代わりに `gradlew.bat buildAndCollect` を使ってください。

これで Minecraft 1.20.5〜1.21.4 の全バージョンがビルドされます。

mod は `build/libs/<バージョン>/` に作られます。

## ライセンス

[MIT](LICENSE)
