# リリースの手順

## 変更点の書き方

| ファイル | 言語 | 中身 |
|---|---|---|
| `CHANGELOG.md` | 英語 | **リリースノートの本文そのもの**。直近（または次）のリリース分だけを書く |
| `CHANGELOG.ja.md` | 日本語 | 全バージョン分を積み上げる |

- `CHANGELOG.md` はリリース後もそのまま残し、**次のリリースの最初の変更を書くときに丸ごと書き換える**。
- 日本語は `CHANGELOG.ja.md` の「未リリース」欄に書き足し、リリース時にその見出しをバージョン番号と日付に変える。
- 変更点は main にだけ書く。

### 形式

```markdown
<何が変わったかの要約1文。更新前に知るべきこと（設定が戻るなど）があればここに書く>

### Added
- Add …

### Changed
- …

### Removed
- Remove …

### Fixed
- Fix …
```

- 見出しは `Added` → `Changed` → `Removed` → `Fixed` の順で、**項目があるものだけ**書く。変更が1〜2個なら見出しなしの箇条書きだけでよい。
- 各項目は命令形で始める（`Fix …`、`Add …`、`Port to 26.3`）。
- 遊ぶ人に見える変化だけを書く。
- 関係する issue や PR があればリンクを付ける。

## リリースの流れ

1. バージョンを上げる: main と各 `mc/*` ブランチの `stonecutter.properties.toml` の `mod.version` を変え、それぞれ push する。
2. 変更点を確定する: `CHANGELOG.md` を見直し、`CHANGELOG.ja.md` の「未リリース」をバージョン見出しに変える。
3. ビルドと確認: 各ブランチで `./gradlew build` を実行し、テストが通ることと、ゲーム内の動作を確かめる。
4. タグ: main のリリース用コミットに `v<バージョン>` のタグを付けて push する。

タグを push すると `.github/workflows/release.yml` が動き、次を行う。

- 全ブランチで `mod.version` がタグと一致するか確かめる（一致しなければ止まる）。
- 全ブランチの全 Minecraft バージョンをビルドする。main はタグのコミット、`mc/*` はブランチの先頭を使う。
- ビルドしたコミットに `v<バージョン>+<範囲>` のタグを付ける（例: `v0.4.0+1.20.5-1.21.4`）。
- `v<バージョン>` の GitHub Release を**1つだけ**作り、全 Minecraft バージョンの jar を添付する。タイトルはタグと同じ `v<バージョン>`、本文は `CHANGELOG.md` をそのまま使う。
- GitHub Release を作ったあと、その jar を 1 つずつ Modrinth に上げる（`modrinth` ジョブ）。jar ごとに Modrinth のバージョンが 1 つでき、名前は `PeriScan <バージョン>+<MC>`、変更点は `CHANGELOG.md` になる。対応する Minecraft のバージョンは、jar の `fabric.mod.json` の範囲から決まる。
- Modrinth のバージョン一覧は上げた時刻の順に並ぶ。新しい Minecraft が上に来るよう、古い Minecraft の jar から順に上げる。
- Modrinth のプロジェクトページを、タグのコミットの内容に合わせる（`modrinth-page` ジョブ）。対象は名前、概要、説明文、タグ、ライセンス、リンク。

`modrinth` ジョブのどれかが失敗すると、残りの jar は上げずに止まる。Actions の画面で「Re-run failed jobs」を押すと、失敗した jar から順に続きを上げる。

リリースを Modrinth に上げ直すときは、Actions の「modrinth upload」を手で実行する。`tag` にタグ（例: `v1.0.0`）を入れ、`replace` にチェックを入れると、そのリリースのバージョンを Modrinth から消してから上げ直す。

## Modrinth の準備

`modrinth` ジョブ（`modrinth-upload.yml`）は[mc-publish](https://github.com/Kira-NT/mc-publish)で jar を上げる。次の 2 つを GitHub のリポジトリ設定（Settings → Secrets and variables → Actions）に登録する。

| 種類 | 名前 | 中身 |
|---|---|---|
| Variables | `MODRINTH_PROJECT_ID` | Modrinth のプロジェクト ID |
| Secrets | `MODRINTH_TOKEN` | Modrinth の API トークン（権限は Create versions、Read versions、Write versions、Read projects、Write projects。上げ直しで消すときは Delete versions も） |

- `MODRINTH_PROJECT_ID` が空のあいだは `modrinth` と `modrinth-page` のジョブを飛ばし、GitHub Release だけを作る。
- 依存 Mod は `modrinth-upload.yml` に書いてある。必須は Fabric API と YACL、任意は Litematica と Mod Menu。依存を変えたら `modrinth-upload.yml` も直す。
- プロジェクトページの内容はリポジトリに置いてある。説明文は `docs/modrinth.md`、それ以外（名前、概要、タグ、ライセンス、リンク、アイコン、ギャラリー）は `.github/modrinth/project.json`。
- 動作環境（クライアント専用）はページの設定ではなく、バージョンごとに決まる。jar を上げるときに、`fabric.mod.json` の `environment` から自動で入る。
- 説明文の画像は `docs/assets/screenshots/` に置き、GitHub の絶対 URL で参照する。
- ページの内容はリリースのたびに反映される。リリースを待たずに反映するときは、Actions の「modrinth page」を手で実行する。アイコンとギャラリーは、このワークフローで `icon` や `gallery` にチェックを入れたときだけ上げる。
- AI 利用の開示（Contains AI-generated content）と審査への提出は、Modrinth の画面で行う。

## ブランチ

| ブランチ | Minecraft |
|---|---|
| `main` | 1.21.9〜26.3 |
| `mc/1.21.5-1.21.8` | 1.21.5〜1.21.8 |
| `mc/1.20.5-1.21.4` | 1.20.5〜1.21.4 |
| `mc/1.20-1.20.4` | 1.20〜1.20.4（1.20.2 を除く） |

- 修正と機能は main で作り、各 `mc/*` ブランチへ `git cherry-pick -x` で持ち込む。
- 持ち込んだあとは `./gradlew "Refresh active project"` を実行する。main のソースは 26.3 の名前で書かれているため、そのブランチの vcsVersion の名前に直す必要がある。変わったファイルがあればコミットしてからビルドする。
- `CHANGELOG*` と `docs/releasing.md` は main にだけ置く。

## Stonecutter での開発

1つのブランチで複数の Minecraft バージョンをビルドする（main は 1.21.10 / 1.21.11 / 26.1.2 / 26.2 / 26.3）。

- ソースは最新版（`stonecutter.gradle.kts` の `stonecutter active` と `settings.gradle.kts` の `vcsVersion`）に合わせて書く。
- バージョン間の名前の違いは `stonecutter.gradle.kts` の replacements に書き、ソースには `//? if` を増やさない。呼び出し方が違うものは `compat/VersionCompat.java` にまとめる。
- 別のバージョンで補完やビルドを確かめたいときは `./gradlew "Set active project to <バージョン>"` で切り替える。
- **コミットの前に `./gradlew "Reset active project"` で最新版の状態に戻す。**
- 全バージョンのビルドとテストは `./gradlew build`、jar を `build/libs/<バージョン>/` に集めるのは `./gradlew buildAndCollect`。
