# アーキテクチャ

## 技術スタック

| 項目 | 内容 |
|---|---|
| 言語・UI | Kotlin 2.2 / Jetpack Compose（Material 3） |
| DI | Hilt 2.58（`di/` に 5 モジュール） |
| 保存 | Room 2.8 + SQLCipher（庭・プロフィール・図鑑）、Preferences DataStore（設定、1 日の回数制限） |
| ビルド | Gradle 8.11 / AGP 8.10 / KSP、JDK 21 |
| SDK | minSdk 26、target / compile 35 |
| 広告 | Google Mobile Ads 23.6（リワード。**テスト ID**） |
| テスト | JUnit4 の JVM ユニットテストのみ（約 400 件）。UI テストなし |

## 層

```
domain/   純粋な Kotlin。モデル、リポジトリの interface、ユースケース
data/     Room、DataStore、暗号、アセット読み込み、音、広告
ui/       Compose。画面、ViewModel、描画
di/       Hilt の束ね
```

- ルールは `domain` に置く。`ui` は呼ぶだけ。
- ユースケースは `gardenRepository.runInTransaction { }` の中で読み→判定→書きを行う。
- ViewModel は `actionMutex` で同期処理とプレイヤー操作を直列化している。

## グリッドと物の持ち方

- 庭は 9×9。すべての物は `grid_objects` テーブルの 1 行＝ `GridObject`。
- 種類は **objectId の接頭辞**で決まる。

| 接頭辞 | 意味 | セルの層 |
|---|---|---|
| `plant_` | 作物 | 占有物（1 セル 1 つ） |
| `struct_` `deco_` `ground_` | 建物・装飾・地表 | 占有物 |
| `floor_` | 床（石畳、ウッドデッキ、**耕した土** `floor_tilled`） | 床（占有物の下） |
| `terrain_` | 地形（川） | セル全体。床も占有物も不可 |

- `GardenGrid` が「床」と「占有物」の 2 層を持ち、`checkPlacement(footprint, x, y, kind)` で可否を返す。
- 建物の大きさは `StructureData`（小屋 2×2、井戸 1×2）。それ以外は 1×1。
- **種・苗は耕した土の上にしか置けない**（`PlaceItemUseCase`）。耕すのは `HoeUseCase`。
- 空きフィールドの流用が 1 つある：耕した土の `growthProgress > 0` は「たい肥入り」。

## ショップのマスター

`domain/model/GardenItem.kt` の enum が唯一のマスター。価格、解放条件、設置 XP、建築秒数を持つ。
価格の考え方はそのファイルの KDoc の表にある。

- 解放条件 `UnlockCondition`: `None` / `PlayerLevel(n)` / `CollectionPages(n)`（図鑑の登録済みページ数）。
- 棚の並びは `GardenItem.shelf(category)`。
- アイテムを足すときに触る場所：`GardenItem`、`CollectionEntry`（図鑑ページ）、`TileSetDefinition.objectIds`、
  `UiLabels`（名前・絵文字）、`CollectionDialog.flavorText`、`strings.xml`、描画（`GardenCanvas`）。

## 時間の計算（作物）

`CalculateOfflineProgressUseCase`。1 分＝1 tick、最大 7 日ぶん。ループせず O(1) の閉形式。

- 健康な間は成長。水分 20% 以下で「のどがかわいた」、栄養 20% 以下で「栄養不足」となりストレスが増える。
- **満開のまま 72 時間**でもストレスが満ち、枯れる。枯れても消えない。
- ストレス 100% で `WITHERED`。復活は 30 コイン（`CareForPlantUseCase.revive`）。
  枯れた作物を撤去すると土がたい肥入りになり、次の種は成長 20% から始まる。
- **天候**は `WeatherType`（晴れ・くもり・雨）。UTC の日付から決定的に決まる（`forTime`）。
  雨は水分を満たし、ストレスの増加をすべて止める。
- `WeatherForecast` を渡すと、不在期間を**日ごとの天気で区切って**計算する（`WeatherForecast.Daily`）。
  `WeatherType` 自体も「ずっとその天気」という予報として渡せる（テストはこれを使う）。
- **建物のパッシブ効果**（`StructureEffect` / `PlantSurroundings`）：
  井戸は周囲 1 マスの乾きを 0.5 倍、小屋は周囲 1 マスの成長を 1.1 倍。同種は重ならない。完成した建物だけ有効。
  単体計算のときは `garden` 引数に庭全体を渡すこと（渡さないと「野ざらし」扱いになる）。

## 建築・解体（時間のかかる作業）

`GridObject` に `workKind` `workStartedAt` `workEndsAt` がある（DB v5）。

```
NONE ──置く──▶ BUILDING ──時間──▶ READY ──プレイヤーのタップ──▶ NONE（完成）
NONE ──破棄──▶ DEMOLISHING ──時間──▶ 消える
NONE ──持ち物へ──▶ PACKING ──時間──▶ 持ち物に入る
```

- 建築秒数は `GardenItem.buildSeconds`（フェンス 20、井戸 120、小屋 300）。解体は半分。
- 時間切れの処理は `CompleteWorkUseCase`。ViewModel の `watchWork()` が次の期限まで待って同期する（ポーリングなし）。
- **完成は自動ではない**。`READY` で止まり、タップで `UnveilBuildingUseCase`。
- 時刻改ざんを検出した同期では、作業は最初からやり直しになる（`SyncGardenUseCase`）。
- 移動は `MoveObjectUseCase`（即時）。作物・耕した土・作業中の物は動かせない。
- 即時の取り外しは `StoreObjectUseCase` / `RemoveObjectUseCase`、時間のかかる入口が `StartRemovalUseCase`。

## DB

`pocket_garden.db`、現在 **v5**。スキーマは `app/schemas/` に出力される。

| 版 | 変更 |
|---|---|
| 2 | `user_profile.currentXp` |
| 3 | `collection_entries`（図鑑） |
| 4 | `collection_entries.revealed`（手動登録。既存行は登録済みに） |
| 5 | `grid_objects.workKind / workStartedAt / workEndsAt`（既存はすべて完成扱い） |

- マイグレーションは `AppDatabase.MIGRATION_x_y`、登録は `di/DatabaseModule.kt`。
- DB は SQLCipher。鍵は Android Keystore で包む。プロフィールは HMAC 署名付き（`SecurityManager`）。
- 繋がり方（川・フェンス）、風、影などの見た目は**保存しない**。位置から毎回求める。

## 描画エンジン

すべて `ui/components/GardenCanvas.kt` の Canvas 1 枚。

- `IsometricTransform`: タイル 128×64dp、高さ 1 段 16dp。`worldToScreen(x, y, z)`。
- `GardenCamera`: ピンチとドラッグ。初期は全体が入る倍率（`FIT_MARGIN = 0.86`、木枠の分を空けている）。
- `LayerPipeline`: 描画命令を層（地面 → グリッド → 影 → 地表物 → 物 → 上のエフェクト → HUD）と奥行きで並べる。
- 物の描画は `submitObjects()`。図鑑の絵 `ObjectPreview` も同じ関数を使う（絵がずれない）。
- スプライトは `assets/atlas/atlas.png` + `assets_manifest.json`。無い物はプログラム描画。
- 地面の絵（川・石畳）は `res/drawable-nodpi/` から `R.drawable` で読む。

### 川（`RiverAutotile`）

- 上下左右の隣が川かどうかで 4 ビットのマスク（N=1, E=2, S=4, W=8）。アトラスの `mask % 4` 列・`mask / 4` 行。
- アトラスは 4×4 ブロックを縦に 4 コマ積んだ形。
- **位相ずらし**：`frameFor(tick, x, y) = tick - (x + y)`。各マスは上流（西・北）より 1 コマ遅れる。
- アトラス側は波を「東・南へ流れる」1 本の場として描いてあり、1 マスあたりの波数を
  `整数 + 1/コマ数`（1.25）にしてある。これでコマをずらしたときにだけ継ぎ目が合う。
  生成は `tools/build_terrain_assets.py`。**コマ数や波数を片方だけ変えると継ぎ目が壊れる。**

### フェンス（`FenceAutotile`）

- 同じ 4 ビットのマスク。隣が無ければ柵の画像 1 枚、あれば支柱の画像＋隣へ伸びる横木と杭（プログラム描画）。
- 横木はマスの辺の中央で終わるので隣と必ず繋がる。奥（北・西）の腕は支柱より先に描く。

### 風と光（`Atmosphere.kt`）

- `Wind.sway(time, x, y, weather)` は時刻と位置だけの純関数。
  平常は弱いそよ風（最大の 18%）。10 秒の枠ごとに 1 回、ずらした時刻に突風が庭を横切る。
  前線が通ると作物が風下へしなり、約 3 秒で凪に戻る。`gustAt()` は描画用の前線位置。
- `Daylight.at(hour, weather)` は影の向き・長さ・濃さ。朝は左へ長く、正午に短く、夕方は右へ。
- 風の時計は約 50ms ごとに進み、Canvas だけが描き直される（再コンポーズなし）。

### 耕した土（`SoilTexture`）

輪郭の揺れ、こぼれた土、土粒の位置をマス座標から決定的に生成する純関数。描画は `drawTilledSoil`。

### 工事現場

`drawWorkSite`：工事シート＋足場＋土ぼこり＋進捗バー。**中身は一切描かない。**
`READY` は進捗バーの代わりに「✨！」の吹き出し。完成時は `drawCompletionBurst` と `popScale`。

## 画面

- `GardenScreen` が唯一の画面。ダイアログ（ショップ、図鑑、設定、持ち物）を上に出す。
- 下部ツールバーは固定。選択パネルと各モードのバナーはその上に**重ねて**フェードで切り替える。
- 通知は自前の `GardenToastHost`（レイアウトを動かさない）。
- カメラモードは HUD 全体と排他の分岐。写真は庭の `graphicsLayer` を 4:5 の枠で切り抜く（`CameraFrame`）。
