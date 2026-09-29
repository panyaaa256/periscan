# PeriScan

[English](README.md)

ワールドイーターやトレンチャーを動かす前に取り除くべきブロックを、ハイライトする Fabric mod です。ペリメータの範囲を指定すると、邪魔になるブロックが壁越しにも見えるようになります。

- トレンチャーやワールドイーターを止めてしまうブロック（黒曜石、チェスト、スポナー、スカルクセンサー、溶岩、水没したブロック、長く連なった砂や砂利など）をハイライト
- トレンチ、その周り、ペリメータの内側で、ハイライトするブロックと色を別々に設定可能
- ブロックを取り除くと、ハイライトも自動で消える
- 範囲は名前付きのプロファイルとしてワールドごとに保存
- Litematica があれば、ペリメータ用のスケマティックをまとめて配置

使い方は **[使い方ガイド](docs/usage.ja.md)** を見てください。

## 必要なもの

- Minecraft 1.20.5〜1.21.4
- [Fabric Loader](https://fabricmc.net/use/) 0.19.3 以降
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) 3.6.6 以降（1.21 以降は 3.8.2 以降）
- 任意: [Litematica](https://modrinth.com/mod/litematica)（スケマティックの配置に使います）

PeriScan は自分のゲームに入れるだけで使えます。PeriScan が入っていないサーバーでも動きます。

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

mod は `build/libs/<バージョン>/periscan-<バージョン>+<Minecraftのバージョン>.jar` に作られます（`-sources.jar` で終わるファイルは mod ではありません）。

## ライセンス

[MIT](LICENSE)
