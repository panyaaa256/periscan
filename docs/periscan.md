[前提]
ワールドイーターを動かす際に、trencherを動かすときやイーターを動かしている最中に取り除く必要のあるブロック、液体がある
これらをチャンク座標の指定とconfigのみからハイライトするmodを作りたい

[方針(決定)]
- 単体modとして作る(malilib / tweakerooには依存しない)
- 依存: Fabric API + YACL のみ(MC 26.2)
- 実装の参考(依存はしない):
	- clientcommandsのRenderQueue → コマンド起点のハイライト描画の構造
	- litematicaのSchematic Verifier → ブロック面オーバーレイ描画
	- ※どちらもLGPL-3.0。コードをコピーする場合は自modのライセンスをLGPL互換にする必要あり(設計を読んで自分で書く分には問題なし)

[config]
yaclを使う
- quarry-like trencherを使うかどうか (bool)
	- ON: トレンチを含む3ゾーン(トレンチの一つ外側・トレンチの内側・イーター領域)すべてを走査・ハイライトする
	- OFF: イーター範囲の1ゾーンのみ走査・ハイライトする
- north-southとeast-westの二つ数字を入力する箇所を作る、デフォは12, 3 (3 ~ 32のint)
	- 意味: 南北両端のトレンチのNS方向の幅(列数)と、東西両端のトレンチのEW方向の幅(列数)
	- config値=列数(決定)。例: 12なら トレンチ本体はちょうど12列(ブロック 0~11)
- トレンチの一つ外側、トレンチの内側、イーターを走らせる部分の三か所についてそれぞれハイライトするブロックのリスト
	- トレンチの一つ外側
			- スカルクセンサー
			- 溶岩
	- トレンチの内側
			- チェスト
			- トラップチェスト
			- エンダーチェスト (2026-07-06追加)
			- 黒曜石
			- 泣く黒曜石
			- 金庫
			- スポナー
			- 試練のスポナー
			- スカルクセンサー
			- スカルクカタリスト
			- スカルクシュリーカー
			- 強化深層岩
			- 適当な塀、フェンス → 別リスト化(下記レーン規則参照)
	- イーターを走らせる部分
			- 黒曜石
			- エンダーチェスト (2026-07-06追加)
			- water loggedのブロック
			- 金庫
			- 試練のスポナー
			- 強化深層岩
		以上をデフォルトとして、追加削除可能なリストに
- 塀・フェンス類のレーン規則 (2026-07-06追加):
	- トレンチ内の塀・フェンス類(#walls, #fences, #fence_gates、専用リストで編集可)は、
	  トレンチの端から1始まりで数えて「3で割って2余る列」(2, 5, 8, 11...)にある場合のみハイライトする
	- それ以外の列はtrencherの妨げにならないため無視。四隅は両方向どちらかで該当すればハイライト
	- 幅が3のトレンチは真ん中に塀等があっても壊れないため、塀・フェンス類を一切ハイライトしない (2026-07-06追加)
- ハイライト色は**ゾーンごとに3色**をRGBで設定可能に、デフォは白(決定: ブロックごとではなくゾーンごと)

[仕様]
client sideとする

■ コマンド
/periscan [x1] [z1] [x2] [z2] というように指定し、実行する
- 座標はチャンク座標
- **指定範囲はペリメータの最外周**を表す(決定)
- サブコマンド(提案):
	- /periscan clear … ハイライト全消去
	- /periscan reload … 現在の領域を再スキャン

■ ゾーンの定義(決定)
軸の対応: north-south方向 = ゲーム内Z軸、east-west方向 = X軸
(GeoGebra図ではNS=x軸として作図。以下の例は指定範囲がブロック 0~527 の528x528、NS=12, EW=3 の場合)

- トレンチの内側(=トレンチ本体、図の青い帯):
	- NS両端: NS方向の幅12列 x EW方向全長 の帯が2本(例: 0~11 と 516~527)
	- EW両端: EW方向の幅3列 x NS方向全長 の帯が2本(例: 0~2 と 525~527)
	- 四隅の重なりは和集合として扱う
- トレンチの一つ外側:
	- 各トレンチ帯の両脇1列(ペリメータの外側1列 + 内側1列)、帯の全長にわたるライン
	- 例(NS側): -1, 12, 515, 528 の各列
	- 外側1列は指定範囲の**外**にあるため、スキャン範囲は指定範囲+外周1ブロックになる点に注意
- イーターを走らせる部分:
	- トレンチを除いた内側の矩形 = (528-12*2) x (528-3*2) = 504 x 522(例: NS 12~515, EW 3~524)
	- 「トレンチの一つ外側」の内側1列はイーター領域の縁と重なるが、ゾーンの重なりは許容し、
	  各ゾーンのブロックリストで独立に判定する
- quarry-like trencher が OFF のときはイーター領域のみ走査する(トレンチ2ゾーンはスキップ)
	- OFF時のイーター領域は**指定範囲全体**(例: 528x528)とする(決定)。NS/EW値はこの場合使われない

■ スキャン
- Y範囲: ワールド最下端から config の「スキャンする最大Y」まで(デフォ128)(2026-07-06変更、generalに設定項目)
- 必要であればチャンクを読み込む必要があることをguiで表示させる
- 読み込みが完了したチャンクから順にhighlightしていく(ClientChunkEvents.CHUNK_LOAD駆動)
- water loggedの判定はBlockStateのwaterloggedプロパティのみを対象にする(水源そのものはハイライトしない)(2026-07-06変更)
- 水没判定の除外(全ゾーン共通):
	- ピストンで押されると壊れるブロック(葉・サンゴ・鍾乳石・レール等)を自動除外(ゲーム内部のPushReaction=DESTROYで判定、ON/OFF可・デフォON)
	- 追加の手動ブラックリスト(ID/#タグ、デフォ空)も併用可能

■ 描画
- **壁越しに常時表示**(決定): 深度テストを無視して枠線+半透明の面を描画する
- 色はゾーンごとのconfig色を使用
- ハイライト対象が数千個を超える規模になったら、チャンクセクション単位で頂点をキャッシュする方式に切り替える(最初は毎フレーム即時描画でよい)
- Iris対応: カスタムRenderPipelineはIrisの対応表にないため、シェーダーパック有効時は何も描画されない。IrisApi.assignPipeline(pipeline, IrisProgram.BASIC)で登録して解決(ソフト依存、integration/iris) (2026-07-10追加)

■ ハイライトの解除
- highlightされたブロックがhighlightすべきブロック以外に置き換わったときにhighlightを解除する
- ほかの人が壊した場合も、クライアントに届くブロック更新パケットとして観測できるので同じ仕組みで解除される(視界外・未ロードチャンクで起きた変更は、そのチャンクの再ロード時の再スキャンで反映)

■ 永続化
- ワールド/サーバーごとに「アクティブな領域(チャンク座標)」をファイルに保存する
- 再ログイン時は領域を復元するが**自動では再走査しない**(2026-07-06変更)。チャットで案内を出し、
  /periscan reload を打ったときにのみ走査・ハイライトを開始する
- ブロックごとのハイライト状態そのものは保存しない(再スキャンで復元できるため不要)

■ コマンドUX (2026-07-06追加)
- 座標引数には現在プレイヤーがいるチャンクのX/Z(軸に対応した値)をサジェストする
- 引数が4つ未満の場合はbrigadier標準の「Unknown or incomplete command」エラーで失敗する

[実装メモ (2026-07-06)]
- mod id: periscan / 表示名: PeriScan / パッケージ: com.panyaaa256.periscan / ライセンス: MIT(全コード自作)
- ブロックリストはID(minecraft:obsidian)と#タグ(#minecraft:walls)の両対応
- water logged判定はゾーンごとのbool(デフォはイーター領域のみON)
- デフォルトリストに 校正スカルクセンサー と #minecraft:fence_gates を追加してある(不要ならconfigで削除)
- 1.21.11のmojmapでは ResourceLocation→Identifier などの改名あり
- 未ロードチャンクの通知はアクションバー表示(残チャンク数)で実装

[todo]
- イーター範囲の昆布のハイライト ✅ 2026-07-07実装
- トレンチ内側の範囲に進む方向に10ブロック(configurable)以上落下ブロックが続いている箇所をハイライト（押したら壊れるブロックは無視して10ブロック） ✅ 2026-07-07実装
- 3か所それぞれにenalble / disableをつける、このときイーターの内側に関してはトレンチ範囲を含めた全体を範囲に取るかトレンチ以外の範囲をとるかのboolを作る（これによりuse quarry-like trencherのboolを削除できる） ✅ 2026-07-07実装
- 全部disableの時に走査を開始したら警告を出して走査は走らせない ✅ 2026-07-07実装

[実装メモ (2026-07-07)]
- 昆布: minecraft:kelp と minecraft:kelp_plant をイーター領域のデフォルトリストに追加。※既存の periscan.json には自動反映されないので、既存configの場合は手動でリストに追加が必要
- 落下ブロック連続の解釈(未確認なら要レビュー):
	- 「進む方向」= トレンチ帯の長手方向(Z両端のトレンチはX方向、X両端はZ方向)。各Y・各列ごとの水平ラインで判定
	- 落下ブロック = Fallable実装ブロック(砂・砂利・コンクリートパウダー・金床など)。ただし押すと壊れるもの(鍾乳石・怪しい砂など)は落下ブロックとして数えない
	- 空気・液体・押すと壊れるブロックは数に入れないが連続を切らない(2026-07-07決定: 空気も途切れさせない)。それ以外のブロックでのみ連続が切れる
	- 連続する落下ブロック数が config のしきい値(デフォ10、2~64)以上なら、その連続内の落下ブロック全部をハイライト(トレンチ内側の色を使用)
	- 連続はチャンク境界をまたぐ。未ロードチャンクで連続は打ち切り、ロードされ次第ライン全体を再計算
- ゾーンごとの enable/disable は各ゾーンのconfigカテゴリ先頭に配置。イーターの「トレンチ範囲を含める」bool(デフォOFF=トレンチ除外)を追加し、useQuarryLikeTrencher は削除(旧ON=全ゾーン有効+トレンチ除外、旧OFF=イーターのみ有効+トレンチ含める、で再現可能)
- bottom trench(トレンチの底)ゾーンを追加(2026-07-07指示):
	- 範囲: トレンチ帯と同じXZ範囲 × スキャン下端から2マス(オーバーワールド Y=-59,-58 / ネザー Y=5,6)
	- 空気と液体ブロック以外のすべてをハイライト(水没した固体ブロックは対象、リスト設定なし)
	- この2マスは「トレンチの内側」の判定(ブロックリスト・塀レーン・落下ブロック連続)から除外
	- config: enable/disable と色のみ(デフォ有効・白)。全ゾーン無効判定にも含まれる
- 全ゾーン無効時: /periscan start / reload はエラーで走査しない。config保存時に全ゾーン無効になった場合は走査を止めて領域は保持(reloadで再開可能)

[26.2移植メモ (2026-08-22)]
1.21.11ブランチからの移植。MCの新バージョン体系(年.リリース)で 1.21.11 → 26.1 → 26.2。
- **難読化の廃止**: 26.1以降、Mojangはversion manifestにclient_mappings/server_mappingsを出さなくなり、
  jar自体がmojmap名で配布される。fabricのintermediaryも 26.2 では "0.0.0"(=不要のマーカー)、yarnは0件。
  → build.gradleから `mappings loom.officialMojangMappings()` を削除。
  → loomプラグインを `net.fabricmc.fabric-loom-remap` から `net.fabricmc.fabric-loom` に変更。
  → 依存も `modImplementation`/`modCompileOnly`/`modLocalRuntime` → `implementation`/`compileOnly`/`localRuntime`。
  → remapJarタスクが無くなり `jar` の出力がそのまま配布物になる。
- **Java 25必須** (version jsonのjavaVersion.majorVersion=25)。release=25 / fabric.mod.json の java を >=25 に。
- 依存バージョン: fabric-api 0.158.0+26.2 / YACL 3.9.6+26.2-fabric / loader 0.19.3 / loom 1.17.19 /
  litematica 0.28.5 / malilib 0.29.4 / iris 1.11.2+26.2-fabric

■ API変更(コンパイルエラーになった箇所すべて)
| 1.21.11 | 26.2 |
|---|---|
| `ChunkPos` の public field `x` / `z` | recordになり `x()` / `z()` |
| `ChunkPos#toLong()` | `pack()` (逆は `ChunkPos.unpack(long)`) |
| `Minecraft#screen` / `setScreen(Screen)` | `Minecraft.gui.screen()` / `gui.setScreen(Screen)` |
| `Player#displayClientMessage(c, false/true)` | `sendSystemMessage(c)` / `sendOverlayMessage(c)` |
| `GameRenderer#getMainCamera()` | `mainCamera()` |
| `ClientCommandManager.literal/argument` | `ClientCommands.literal/argument` |
| `FabricClientCommandSource#getWorld()` | `getLevel()` |
| `fabric...rendering.v1.world` パッケージ | `fabric...rendering.v1.level` |
| `WorldRenderEvents.AFTER_ENTITIES` + `WorldRenderContext` | `LevelRenderEvents.COLLECT_SUBMITS` + `LevelRenderContext` |
| `MultiBufferSource` / `consumers().getBuffer(type)` | 廃止。`context.submitNodeCollector().submitCustomGeometry(poseStack, renderType, (pose, buffer) -> ...)` |
| `context.matrices()` | `context.poseStack()` (カメラ相対座標を渡す規約は同じ) |
| `RenderPipeline.Builder#withBlend(BlendFunction)` | `withColorTargetState(new ColorTargetState(BlendFunction))` |
| `withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)` + `withDepthWrite(false)` | `withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))` |
| `withVertexFormat(fmt, VertexFormat.Mode.X)` | `withVertexBinding(0, fmt)` + `withPrimitiveTopology(PrimitiveTopology.X)` |
| `RenderSetup.builder(p).bufferSize(n)` | `bufferSize` 廃止(サイズは `RenderType` 側が持つ) |
- `com.mojang.blaze3d.platform.DepthTestFunction` → `CompareOp`、`VertexFormat.Mode` → `com.mojang.blaze3d.PrimitiveTopology`(トップレベル化)
- 描画は「即時にVertexConsumerへ書く」方式から「submit nodeを積んで後でまとめて描く」方式に変わった。
  fill/lineそれぞれ1回ずつ `submitCustomGeometry` を呼び、コールバック内で全ボックスを流し込む形にした
  (RenderTypeごとにバッチされる点は旧BufferSourceと同じ)。
- カメラ位置は `Minecraft.getInstance().gameRenderer.mainCamera().position()` ではなく
  フレームの抽出済み状態 `context.levelState().cameraRenderState.pos` を使う。
- `MATRICES_FOG_SNIPPET` は GLOBALS + MATRICES_PROJECTION + FOG のBindGroupLayoutを含むので、
  1.21.11と同じくこれをベースにシェーダとステートを足すだけでよい(vanillaの DEBUG_FILLED_SNIPPET と同じ組み方)。
  `core/position_color` シェーダは 26.2 にも存在する。
- iris(`IrisApi#assignPipeline`)とlitematica/malilibのprobe対象は 26.2 版jarをjavapで再確認済み、変更なし。
