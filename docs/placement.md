[前提]
マインクラフトにおいてperimeterを作るときは毎回使うschematicsとその座標は同じである
これを、初心者の人でも簡単に設計図を置くことができるようにschematicsを一括でplacement化する機能をPeriScanに追加する
(当初は別mod案1/案2を比較したが、periscanへの統合+litematica soft dependに決定 2026-07-07)

[方針(決定) 2026-07-07]
- 別modではなくPeriScanの一機能として実装する
- litematicaはsoft depend
	- fabric.mod.json: depends には入れず suggests: { "litematica": "*" } 程度
	- gradle: modCompileOnly(litematica + malilib) + modLocalRuntime(開発時動作確認用)
- 実行時判定はバージョン文字列比較ではなく、reflectionによる能力チェック(probe)
	- isModLoaded("litematica") && 実際に使うクラス/メソッドの存在確認、を初回に1度だけ
	- probe失敗時はクラッシュさせず機能無効化+警告ログ(scan機能は影響を受けずに動く)
- litematicaを直接触るコードは com.panyaaa256.periscan.integration.litematica パッケージに完全隔離
	- LitematicaIntegration … 常時ロードOKの入口。litematicaの型をimportしない
	- LitematicaPlacer … litematicaを直接触る本体。isAvailable() 通過後にのみクラス参照
	- ロジック側(座標計算・テーブル)にlitematicaの型を漏らさない
- 対象litematicaビルド: sakura-ryoko fork litematica-fabric-1.21.11-0.26.11 (決定 2026-07-07)
	- Modrinth mavenから取得: maven.modrinth:litematica:0.26.11 (バージョンID R9maucI8) + maven.modrinth:malilib:0.27.16
	- probe方式なのでmasa本家でも動く想定だが、コンパイル・検証はsakura fork基準

[peri profile (決定) 2026-07-07]
複数のperiを名前付きで管理する概念。scanとplacementの両機能の土台になる。

■ コマンド(ルートを /peri に統一)
- /peri add [x1] [z1] [x2] [z2] [name] … profileを作成
	- 座標はチャンク座標(既存 /periscan と同じ意味 = perimeter最外周)
	- 2点は保存時に min角/max角 に正規化する(入力順序に依存しない)
	- 実行時のdimensionをprofileに記録する
- /peri remove [name] … profile削除。紐づくplacement(下記命名規則で判別)も削除する
- /peri list … profile一覧(名前・範囲・dimension)を表示
- /peri scan start [name] … 既存のスキャンをprofile指定で実行
- /peri schematic [name] <dir> … placement一括作成(下記)
	- dir省略時はprofileのdimensionから自動選択(overworld→ow, nether→nether)
	- dirはschematics/peri/配下の実在ディレクトリをタブ補完でサジェスト
- 既存 /periscan start/clear/reload は /peri scan start / clear / reload に移行
	- configはscan固有ではなくmod全体の設定なので /peri config (トップレベル)とする(2026-07-07変更)
	- 旧 /periscan は廃止する(決定 2026-07-07。エイリアスも残さない)

■ profileの内容と永続化
- name / 正規化済みチャンク範囲(min/max) / dimension / 作成日
- ワールド・サーバーごとにファイル保存(既存の「アクティブな領域」保存はprofileに統合し、
  「最後にscanしたprofile名」を保存する形に置き換える)
- scan / schematic 実行時、現在のdimensionがprofileのdimensionと違う場合はエラー

[origin計算 (決定 2026-07-07 / 2026-07-08更新)]
■ ディレクトリ構造とschematic作成規約 (2026-07-08決定)
schematics /
	peri (config「periスキマティックのフォルダ名」で変更可、デフォ "peri") /
		ow (セット。コマンドのdir引数。省略時はdimensionから自動選択: overworld→ow, nether→nether) /
			all  / … 1回だけ置く部品(イーター本体・整地用duperなど)。originを x min, z min の角に合わせて保存
			edge / … 点対称部品(周辺部)。半周分を、originをperiの角に合わせて保存
		nether / … netherセットのedgeはmx/mzサブフォルダで振り分け(2026-07-08決定、下記)
			all  /
			edge /
				mx / … X反転ミラーで対にする部品
				mz / … Z反転ミラーで対にする部品
- 配置タイプはファイル名のテーブルではなく all / edge サブフォルダで決まる(ファイル名ハードコード表は不要になった)
- originのy規約値(決定 2026-07-08): ow = -59, nether = 5。endは非対応(そもそもendでperiしないため全面エラー)
- periは長方形リング=中心点対称なので、edgeの半周分を180°回転すれば残り半周が埋まる(正方形でなくても成立)

■ 配置規則 (確定 / 2026-07-08: 角ペアをconfigで選択可に)
- min角ブロック座標 = (minChunkX*16, y規約値, minChunkZ*16)
- max角ブロック座標 = (maxChunkX*16+15, y規約値, maxChunkZ*16+15)
- edge/ : 1ファイル → 2 placement。角ペアはconfig「edgeを設置する角」で選択:
	- ++ / -- (デフォルト): 無変換 @ -x/-z角、CLOCKWISE_180 @ +x/+z角
	- +- / -+ : Mirror FRONT_BACK(X反転) @ +x/-z角、Mirror LEFT_RIGHT(Z反転) @ -x/+z角
	  (2026-07-08変更: 当初90°/270°回転で実装したが、回転はX/Z軸が入れ替わり
	   NS=12幅/EW=3幅のトレンチの走る方角が崩れるためミラーに変更。
	   ミラーなら各トレンチが自分の軸に留まる。無変換の内容は+x/+z方向に展開するので、
	   X反転後は-x/+z方向に展開→originは+x/-z角、Z反転はその逆)
- all/ : 1ファイル → 1 placement (無変換 @ min角。角ペア設定の影響を受けない)
- nether専用ルール (2026-07-08決定): netherのtrencherは始動方角が固定(NSトレンチはWから等)
  のため、反対側コピーに180°回転は使えない(両軸の向きが反転する)。ミラーなら反転しない
  軸の向きが保たれるが、どの軸を反転すべきかはファイル内容に依存するため、mx/mzサブフォルダで事前指定する:
	- edge/mx/ : 無変換 @ -x/-z角 + Mirror FRONT_BACK(X反転) @ +x/-z角
	- edge/mz/ : 無変換 @ -x/-z角 + Mirror LEFT_RIGHT(Z反転) @ -x/+z角
	- profileのdimensionがnetherのとき常にこの方式(configの「edgeを設置する角」はow等でのみ有効)
	- netherセットのedge直下に.litematicファイルが直接あるとエラー(mx/mzへの振り分け漏れ防止)
- 90°/270°回転は不使用。mirrorは+-/-+モードとnetherのmx/mzで使用
- 注意: ミラーはキラリティ(左右の向き)を反転するため、鏡映対称でない回路を含む
  schematicは+-/-+モードでは成立しない可能性がある(要ゲーム内確認)
- 180°回転のピボット挙動はゲーム内検証済み(2026-07-07): schematic側でoriginを適切に
  設定すれば min角@NONE + max角@180° でピッタリ合う。追加のオフセット調整は不要
- コードに残る定数は ORIGIN_Y(ow/netherのy)と DEFAULT_SET_DIR(dimension→デフォルトセット名)のみ

[placement作成仕様 (決定) 2026-07-07]
- アトミック: 1ファイルでも失敗(ファイル欠落・読込失敗・テーブル未登録・サイズ不一致)したら
  全体中止し、placementを1つも作らない。「全部成功 or 何も起きない」
- 冪等性: placement名は peri/<profile名>/<ファイル名> とし、実行時に同プレフィックスの
  既存placementを削除してから作成する(座標間違いの打ち直しが自然に動く)
- 初期状態: 全placement enabled + render on + origin locked(誤操作で動かされない)
- schematicsベースパス: 固定パスではなくlitematicaのconfig(DataManager経由)から実パスを解決する
- 成功時チャット出力: 何を・どこに・何placement作ったかの要約を表示

[probe対象 (確定 2026-07-07 / sakura fork 0.26.11のremap済みjarをjavapで確認)]
ここが「このmodが依存するlitematica内部APIの全リスト」。MCバージョン更新時はこの表だけ確認すればよい。
クラスはすべて fi.dy.masa.litematica 配下、MinecraftクラスはmojmapNames。
| クラス | メンバ | 用途 |
|---|---|---|
| data.DataManager | static SchematicPlacementManager getSchematicPlacementManager() | placement管理への入口 |
| data.DataManager | static java.nio.file.Path getSchematicsBaseDirectory() | ベースパス解決 |
| schematic.LitematicaSchematic | static LitematicaSchematic createFromFile(Path dir, String fileName) | ファイル読込(失敗時null想定・要確認) |
| schematic.placement.SchematicPlacement | static SchematicPlacement createFor(LitematicaSchematic, BlockPos origin, String name, boolean enabled, boolean enableRender) | placement作成 |
| schematic.placement.SchematicPlacement | setRotation(net.minecraft.world.level.block.Rotation, fi.dy.masa.malilib.gui.interfaces.IMessageConsumer) | 180°回転 |
| schematic.placement.SchematicPlacement | setMirror(net.minecraft.world.level.block.Mirror, fi.dy.masa.malilib.gui.interfaces.IMessageConsumer) | +-/-+モードのX/Z反転 |
| schematic.placement.SchematicPlacement | isLocked() / toggleLocked() | lock(直接setterなし。!isLocked()ならtoggle) |
| schematic.placement.SchematicPlacement | getName() | 冪等性のためのプレフィックス判定 |
| schematic.placement.SchematicPlacementManager | addSchematicPlacement(SchematicPlacement, boolean printMessage) | 追加 |
| schematic.placement.SchematicPlacementManager | getAllSchematicsPlacements() / removeSchematicPlacement(SchematicPlacement) | 冪等性のための列挙・削除 |
※setRotation/setMirrorのIMessageConsumerはmalilibのインターフェース(no-op実装を渡す)

[実装メモ (2026-07-07) peri profile基盤]
- PeriCommand(/periルート) / persist.PeriProfile / persist.ProfileStore を追加、
  旧 PeriScanCommand / persist.RegionStore は削除(旧 /periscan コマンド廃止)
- 保存形式: config/periscan/worlds/<world>.json に { "profiles": { name: {minX,minZ,maxX,maxZ,dimension,createdAt} }, "lastScanned": name }
	- 旧形式(dimensionごとの領域 / 最古の単一領域)は読み込み時に一度だけprofileへ自動変換して書き戻す
	  (profile名はdimension pathから: overworld, the_nether)
- 範囲は保存時にmin/max角へ正規化。dimensionは作成時のものを文字列(identifier)で記録
- /peri scan start・reload は現在のdimensionがprofileのdimensionと違えばエラー
- reloadは「lastScanned」profileを再スキャン。clearはハイライト停止+lastScanned解除(profileは残る)
- /peri remove はスキャン中のprofileならハイライトも停止。litematica統合後はここで
  peri/<profile>/ プレフィックスのplacement削除も行う(TODOコメント済み)
- profile名はbrigadierのword()(空白なし英数と_-.+)。placement名に埋め込む前提の制約
- /peri list は名前・チャンク範囲・ブロックサイズ・dimensionを表示、lastScannedに「直近にスキャン」マーク

[実装メモ (2026-07-08) litematica統合]
- integration.litematica パッケージ:
	- LitematicaIntegration … 入口。litematica型を一切importしない。probe(isModLoaded+probe表の
	  全メンバをClass.forName/getMethodで確認)を初回に1度実行、失敗時はwarnログ+機能無効
	- LitematicaPlacer … litematicaを直接触る唯一のクラス(package-private)。probe通過後のみロードされる
- /peri schematic [name] <dir>:
	- 全ファイルを先に読み込み → 旧placement削除 → 作成、の順でアトミック(読込失敗で全体中止)
	- placement名: peri/<profile>/all/<ファイル名> / peri/<profile>/edge/<ファイル名>(180°側は @180 付き)
	- enabled + render on で作成。追加 → 回転 → lock の順(lock済みplacementは変更を拒否するためlockは最後)
	- endガード: add / scan start / scan reload / schematic に適用
	  (list / remove / config は場所非依存のため許可)
- /peri remove はprofile削除時に peri/<profile>/ プレフィックスのplacementも削除
- config「periスキマティックのフォルダ名」(schematicsFolder、デフォ "peri")をgeneralカテゴリに追加
- config「edgeを設置する角」(edgeCorners: PP_MM / PM_MP、デフォ PP_MM)をgeneralカテゴリに追加 (2026-07-08)
	- placement名のサフィックスは PP_MM: なし/@180、PM_MP: @mx/@mz(X反転/Z反転)
- createFromFileは拡張子付きファイル名をそのまま受け付ける(fileFromDirAndNameは.litematicを
  重複付与しないことをbytecodeで確認済み)。対象は *.litematic のみ、ファイル名順にソートして処理

[todo]
- 対象litematicaビルドの確認とgradle依存の追加 ✅ 2026-07-07 (sakura fork 0.26.11 / malilib 0.27.16、Modrinth maven)
- probe対象表のシグネチャ確定 ✅ 2026-07-07 (remap済みjarをjavapで確認、上表に反映)
- 180°回転のピボット挙動・off-by-oneのゲーム内検証 ✅ 2026-07-07 (originの設定で解決、調整不要)
- 旧 /periscan コマンドの扱い ✅ 2026-07-07 (廃止に決定)
- 配置タイプの確定 ✅ 2026-07-08 (ファイル名テーブルではなく all / edge サブフォルダ方式に変更)
- origin y規約値の確定 ✅ 2026-07-08 (ow = -59, nether = 5)
- /peri schematic のゲーム内E2E確認(all/edgeの実配置、edge@180の位置、+-/-+モードの@mx/@mzの位置と鏡映回路の成立、netherのmx/mzペアの位置、再実行時の置き換え)
- netherのedgeスキマティックをmx/mz(X反転で対にするか/Z反転で対にするか)に振り分けて保存(ユーザー側作業)
- schematicsファイル(セット)の整備(ユーザー側作業)
- 期待periサイズ検証(セットとprofileの範囲サイズ不一致の検知)は未実装。必要になったら追加
