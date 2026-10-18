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
| 通知 | WorkManager 2.7.0（広告 SDK が既に使っている版をそのまま明示）、NotificationCompat |
| テスト | JUnit4 の JVM ユニットテストのみ（523 件）。UI テストなし |

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
- 建物の大きさは `StructureData`（小屋 2×2、井戸 1×2、コテージ 3×3）。それ以外は 1×1。
  `StructureData` は高さ（`heightSteps`、裏が隠れるかの判定用）と、シンボル建物かどうか（`isSymbol`）も持つ。
- 庭の大きさは固定ではない（下の「庭の拡張」）。`GridObject.GRID_SIZE` は**始まりの大きさ** 9 のこと。
- **種・苗は耕した土の上にしか置けない**（`PlaceItemUseCase`）。耕すのは `HoeUseCase`。
- 空きフィールドの流用が 1 つある：耕した土の `growthProgress > 0` は「たい肥入り」。

## ショップのマスター

`domain/model/GardenItem.kt` の enum が唯一のマスター。価格、解放条件、設置 XP、建築秒数を持つ。
価格の考え方はそのファイルの KDoc の表にある。

- 解放条件 `UnlockCondition`: `None` / `PlayerLevel(n)` / `CollectionPages(n)`（図鑑の登録済みページ数）。
- 棚の並びは `GardenItem.shelf(category)`。
- アイテムを足すときに触る場所：`GardenItem`、`CollectionEntry`（図鑑ページ）、`TileSetDefinition.objectIds`、
  `UiLabels`（名前・絵文字）、`CollectionDialog.flavorText`、`strings.xml`、描画（`GardenCanvas`）。

## ショップ（店のステージ、#44）

ポップアップではなく、庭から歩いて入る**店のステージ**。`GardenScreen` の中で庭の上に全画面で重なり（横に少し滑ってフェード、0.32 秒）、
庭はその下で止まって待つ（`GardenCanvas(paused)`）。戻ると庭はそのまま。画面遷移の仕組み（Navigation）は使っていない。

- 店は 3 軒（`ui/shop/ShopStage.kt` の `ShopStage`）。品ぞろえは `GardenItem` の棚（`ItemCategory`）と 1 対 1。

| 店 | `ShopStage` | 棚 | 壁の色 |
|---|---|---|---|
| 🌱 種と苗の店（入口。ショップボタンで最初に入る） | `SEEDS` | 作物（旬のものだけ） | 若草 |
| 🔨 大工の工房 | `WORKSHOP` | 建物 | 木の色 |
| 🪴 庭の雑貨店 | `SUNDRIES` | 装飾・地表・床・川 | 藤色 |

- 店の行き来は上の 3 枚の看板。部屋（壁・腰板・床）はコードで描き、店ごとに色が違う（`ShopRoom`）。
- 棚の中身は純関数 `ShopShelves.stock(stage, season, stats, coins, ownsSymbol)` → `ShelfItem(item, ShelfTag)`。
  `ShelfTag` は `FOR_SALE` / `SHORT_OF_COINS` / `LOCKED` / `OWNED`（持っているコテージ）。`ShopShelves.shelves` で 1 段 3 つに並べる。
- 棚の物を押すと手に取り（棚には薄い跡が残る）、下から**カード**がせり上がる：絵・名前・**所持数**・図鑑の説明文（`CollectionDialog.flavorText`）・
  効果（井戸・小屋・風車、コテージは庭が広がること）・図鑑で開く物はその条件（「図鑑にNページ登録すると…」）・「棚に戻す」と「N で買う」（または解放条件／所持済み）。
  #48 で作物の旬の行はやめた（棚には旬の物しか並ばないため）。
- 買った結果などの一言は、**高さを固定した 1 行の枠**（`MessageLine`、24dp、1 行で省略）に出す。出ても消えてもカードやボタンの位置は動かない。
- **所持数（#48）**：持ち物にある数（全スロットの合計、`ShopShelves.heldIn`）を、棚の値札の下と、カードの名前の下に「所持数: N個」で出す。庭に置いてある数は数えない。
- 図鑑で開く 2 つ（ウッドデッキ＝2 ページ、石灯籠＝4 ページ）の棚の札は「🔒 図鑑で解放」。ページ数はカードに書く。
- 戻るキー：カードが出ていれば棚に戻す、出ていなければ庭へ。
- 状態は `ShopViewModel`：`stage`（いる店）・`inspecting`（手に取った物）・`shelves`（派生）。`goTo` `inspect` `putBack` `leave`（入口の店・手ぶらに戻す）。
  画面は `ShopStageScreen`（view model をつなぐ）と、状態だけで描く `ShopStageContent`（Robolectric のテスト `ShopStageContentTest`）。
- 臨時ボーナス（リワード広告）は店の下のカウンターに置いた。コイン不足のダイアログと広告の確認は今までどおり。
- 旧 `ShopDialog` は削除。

## シンボル建物

庭の顔になる特別な建物。今はコテージ（`struct_cottage`、3×3、Lv.6、300 コイン、建築 2 時間）だけ。

- **庭にひとつだけ**。数えるのは「庭に立っている（工事中も含む）」と「持ち物に入っている」の両方。
  - 買う：`PurchaseItemUseCase` が、どちらかにあれば `ONLY_ONE_SYMBOL` で断る。
  - ショップ（#41）：持っている間は棚に残したまま、値段のボタンの代わりに「✔ 所持済み」を出す（`ShopViewModel.ownsSymbol`、判定は純関数 `ownsSymbolBuilding(objects, inventory)`）。急に消さないため、棚からは外さない。
  - 置く：`PlaceItemUseCase` が、庭にあれば断る（持ち物に 2 個ある状態は通常は起きないが、それでも 2 棟目は建たない）。
- 移動・持ち物に戻す・解体は、ほかの建物と同じ仕組みでできる。
- パッシブ効果は持たない。役目は庭の拡張。拡張のボタンはコテージのパネルにある。

## 庭の拡張

`GardenExpansion`。9×9 から外周に 1 周ずつ芝生を足す：9 → 11 → 13。

| 広さ | 条件 |
|---|---|
| 11×11 | 完成したシンボル建物が庭にある |
| 13×13 | それに加えて Lv.8 |

- 広さは `garden_progress.gardenSize`（`GardenProgress.gardenSize`）。
- 広げるのは `ExpandGardenUseCase`。庭の中心を保つため、**すべての物を x・y とも +1 ずらす**（`GardenExpansion.shifted`）。1 トランザクション。
- 規則側は地図を `GardenMapSource` から受け取る（`PlaceItemUseCase` `HoeUseCase` `MoveObjectUseCase`）。
  本番は `StoredGardenMap`（保存された広さの芝生）、テストは `GardenMap` そのもの（`GardenMap` は自分自身を返す `GardenMapSource`）。
- 画面側は `GardenViewModel.gardenMap`（`StateFlow`）。`GardenCanvas` は地図の大きさで描き、広がったら全体が入る倍率に引き直す。
- 自動では広がらない。縮める手段は無い。レベルが上がるだけでも広がらない（Lv.8 は 13×13 の条件の 1 つ）。
- **導線（#45）**：広げられる状態（`check == READY`）になったことを 3 か所で伝える。
  - コテージの上に**緑の吹き出し**（外向きの矢印）。`GardenExpansion.growableFrom` が返す建物に、`GardenCanvas(growableId)` で描く。
    押すとコテージが選ばれる（`TapTargets.bubbleCentre(growable = true)`）→ パネルの「庭を広げる（N×N）」。カメラモードでは出さない。
  - **お便り** `LetterKind.GARDEN_CAN_GROW`（id `grow:11` / `grow:13`、サイズごとに 1 通。`PostOffice.gardenCanGrow`、`PostLettersUseCase.gardenCanGrowIfDue`）。
    送るのはお披露目の直後・レベルアップ時・30 秒ごとの同期。送った瞬間に一度だけトースト「庭を広げられるようになりました…」。
  - 広げる前から：ショップのコテージのカードに「完成すると、庭を一回り広げられます」、建築中・お披露目待ちのコテージのパネルに「完成してお披露目すると、ここから庭を広げられます」。
  - 11×11 にしたあと Lv.8 未満なら、パネルに「Lv.8 で 13×13 に広げられます」（以前から）。

## 編集モード（#42）

- 状態は `GardenViewModel.editMode`（アプリを閉じると OFF に戻る。保存しない）。右の列の ✏️（`SideButton`、ON の間は明るい木の色）で切り替える。
  #49 から下部の「編集モード：…　おわる」の札は出さない（明るい ✏️ と格子線が ON の印で、✏️ で終わる）。
- 撤去（片づけ）は #49 から**すべて編集モードのみ**：枯れた作物・育っている作物・耕した土も。ふだんのパネルは撤去ボタンを出さず、
  枯れた作物と空いた耕した土には「片づけ（撤去）は編集モードで行えます」と書く。復活（30 コイン）はふだんの画面でもできる。
- **グリッド線は編集モードのときだけ**（`GardenCanvas(showGrid = editMode && !cameraMode)`）。カメラモードに入ると編集モードは OFF。
- 規則は `domain/usecase/GardenLayout.kt`（純関数）。

| 物 | 移動 | 持ち物に戻す | 破棄 |
|---|---|---|---|
| 作物・耕した土（地面のもの） | 不可（`IN_THE_GROUND`） | 不可 | 編集モードのみ（#49 から。それまではいつでも） |
| 芝生・小石・野の花・石畳・ウッドデッキ・フェンス・川・岩・ベンチ・石どうろう・建物（模様替えの物） | 編集モードのみ | 編集モードのみ | 編集モードのみ |
| ガラスの温室 | 不可（`FIXED`。土の床なので） | 編集モードのみ（作物が育っている間は `IN_USE`） | 編集モードのみ |
| スターターの木箱 | 不可 | 不可 | 不可 |

- 持ち物から置くのは**ふだんの画面だけ**（#50。#42〜#49 は模様替えの物を置くと編集モードに入った）。
  編集モードでは `GardenLayout.toolAllowedInEditMode` がショップだけを許し、ツールバーの他は灰色。`GardenViewModel` も `lockedByEditMode()` で
  水やり・肥料・収穫・復活・クワ・持ち物から置くを断る（「編集モード中は、並べ替えと片づけだけができます」）。パネルもそのボタンを出さない。
  編集モードに入るとクワと手に持った物を置く。抜けると移動中の物を手放す。
- 道具を置くのはその道具のボタン：クワはクワのボタン、持ち物から出した物は（明るくなった）持ち物のボタン（`GardenViewModel.onBagTapped`）。
  クワと置くときの札には「やめる」を出さない。移動中の札だけは「やめる」が残る（ツールバーにボタンが無いため）。
- 確認する場所は 2 つ：画面（`ObjectStatusDialog(editing)` が「移動」「撤去」を出すかどうか、出さないときは「編集モードにすると…」の一文）と、
  `GardenViewModel` の `startMovingSelected` `storeSelected` `removeSelected`（`refused` で「庭の模様替えは編集モードで行えます」）。
  ユースケース（`MoveObjectUseCase` `StartRemovalUseCase` など）は編集モードを知らない。物の種類の規則（作物は動かない等）だけを持つ。`MoveObjectUseCase.canMove` は `GardenLayout.isMovable`。
- 建築中の建物の取りやめ（撤去）も模様替えなので編集モードのみ。「向きを変える」と「完成のお披露目」はふだんの画面でもできる。
  向きを変えられるのは**完成した建物だけ**（#50。工事中は `FlipObjectUseCase` が `BUSY`、パネルにボタンなし）。

## 建物の裏のマス

- **タップは地面で判定する**（`TilePicker`）。屋根や壁を押しても、その点の真下の地面のマス、つまり**建物の裏のマス**が選ばれる。
  この仕組みは以前からで、#25 でテスト（`OcclusionTest`）を足した。
- #41 から、何も手に持っていないときのタップは `TapTargets.resolve` で読む（`TapTargetsTest`）。順番は：
  1. **吹き出し**（完成待ちの「✨！」、水・肥料の印）の近く（タイル幅の 0.3 倍、ただし最低 22dp）→ その持ち主。いちばん近いもの。
  2. 指の下の地面に何かある → その地面（建物の裏の作物は屋根越しに選べる）。
  3. 建物の壁・屋根の上、またはそのすぐ外（`BUILDING_MARGIN` 0.35、半タイル高さ単位）→ その建物。重なれば手前のもの。輪郭は `Occlusion.covers`（裏を隠す判定と同じ箱。工事中は足場の高さ）。
  4. それ以外 → 地面。空きマスか、庭の外なら null（選択を外す）。
  配置・移動・クワのあいだは 4 だけ（`GardenCanvas` の `editing`）。吹き出しの位置は描画と同じ関数（`TapTargets.readyBubbleCentre` `statusBubbleCentre` `workSiteHeight`）から出す。
  選んでいる物をもう一度押すと選択が外れる。複数マスの建物は、どのマス・屋根を押しても同じ物として扱う（`GardenViewModel.onTileTapped`）。
- 見えないと困るので、**建物を半透明にする**（`Occlusion`、`GardenCanvas` の `fadedObjectIds`）。
  - 配置・移動・クワのあいだは、すべての建物。
  - それ以外は、選んだマスを画面上で隠している建物だけ。選んだ建物そのものは薄くしない。
  - 判定は建物を「占有マスの上に `heightSteps` の高さの箱」とみなし、マスの中心がその輪郭に入るか。純関数。
  - 風車はそれに加えて、**羽根が回って描く円**の下のマスも隠すとみなす。軸は占有マスの中央から右へ `WindmillSails.HUB_RIGHT`（0.67 マス、反転していれば左）、上へ `HUB_UP`（1.57 マス）、羽根の届く半径 `REACH`（1.0 マス）。
    羽根は占有マスの右の角より外へ出るので、箱だけでは隠れていたマスが薄くならなかった。値は絵から測ったもので、`BuildingArtTest` がアトラスと照らし合わせる（羽根の先の高さ 20.6 段 ≒ `heightSteps` 20）。
  - 透明度 0.32 まで 0.26 秒でふわっと薄くなり、戻るときも同じ。影は薄くしない。

## 生き物の来訪と自然メモ

### 条件表（`domain/model/CreatureTable.kt`）

**組み合わせはすべて `VisitRule`（enum）の 1 行**。条件・来る生き物・寄り添う物・置き方・確率を持つ。
調整や追加はこの表だけを触ればよい。文面（うわさ・組み合わせ名）は `UiLabels.kt` の `when` が網羅を強制するので、足し忘れはコンパイルエラーになる。

| 組み合わせ | 生き物 | 条件 | どこに | 確率 |
|---|---|---|---|---|
| `BUTTERFLY_BLOOMS` | ちょうちょ | 満開の作物 2 つ以上、雨でない | 満開の作物の上を舞う | 60% |
| `LADYBUG_TULIPS` | てんとうむし | チューリップ 3 つ以上（大きさ問わず）と芝生 | チューリップの足もと | 50% |
| `BIRD_BENCH` | ことり | ベンチと柵 3 本以上 | ベンチの座面 | 50% |
| `BIRD_COTTAGE` | ことり | 完成したコテージ | 屋根の上 | 50% |
| `SQUIRREL_SUNFLOWER` | りす | 満開のひまわりと岩 | 岩のとなりの空いた芝生 | 50% |
| `FROG_RAIN` | かえる | 川タイル 3 枚以上、雨の日 | 川のとなりの空いた芝生 | 70% |
| `FROG_SUMMER`（#32） | かえる | 川タイル 3 枚以上、夏 | 川のとなりの空いた芝生 | 40% |
| `SQUIRREL_AUTUMN`（#32） | りす | 岩、秋 | 岩のとなりの空いた芝生 | 40% |
| `DRAGONFLY_AUTUMN`（#32） | とんぼ | 川タイル 2 枚以上、秋、雨でない | 川の上を飛ぶ | 50% |
| `BUTTERFLY_LAVENDER` | ちょうちょ | ラベンダー 2 つ以上（大きさ問わず。満開なら `BUTTERFLY_BLOOMS` が先に当たるため）、雨でない | ラベンダーの上を舞う | 50% |
| `LADYBUG_STRAWBERRY` | てんとうむし | イチゴ 2 つ以上（大きさ問わず）、春 | イチゴの足もと | 40% |
| `BIRD_HUT` | ことり | 完成した小屋 | 屋根の上 | 40% |
| `BIRD_WINTER_WELL` | ことり | 井戸、冬 | 井戸のとなりの空いた芝生 | 40% |
| `SQUIRREL_PUMPKIN` | りす | 満開（実った）カボチャ | カボチャのとなりの空いた芝生 | 40% |
| `FROG_WELL` | かえる | 井戸、雨の日（川が無くてよい） | 井戸のとなりの空いた芝生 | 40% |

- 条件の部品は `VisitCondition`：`Crops`（数・種類・成長度）、`Things`（物の id と数）、`WeatherIs` / `WeatherNot`、`SeasonIs`（#32、庭の季節）。
  `GardenSnapshot` は季節も持つ（省略すると春）。
- **新しい行は表の最後に足す**。抽選の乱数は行の順番（`ordinal`）から作るので、途中に入れると既存の組み合わせの来る時刻が変わる（`NatureNotesEngineTest` が先頭 6 行の順を見ている）。
  `Creature` も同じく最後に足す（とんぼは 6 番目）。`CreatureVisitTest` は先頭 9 行と、ラベンダー以降の 6 行が最後にあることを見ている。
  うわさ（`hint_<行名>`）と組み合わせ名（`combo_<行名>`）には数字を書かない（PHILOSOPHY 12「数や割合で追い立てない」。`CreatureVisitTest` が確認）。
  工事中の物と枯れた作物は数えない（`GardenSnapshot.settled`）。
- 置き方は `VisitPlacement`：`ON_ANCHOR`（物の上）か `BESIDE_ANCHOR`（となりの空いた芝生。無ければ来ない）。

### 来訪（`CreatureVisits`）

- 時間を **5 分の区切り**に分け、区切りごとに条件を満たす組み合わせが確率で抽選する。乱数は「区切り番号と組み合わせ」から決まるハッシュ。
  **同じ時刻なら必ず同じ結果**になるので、来訪は保存しない。画面を回しても、戻ってきても変わらない。
- 区切りの始まりから 20〜70 秒後に現れ、終わりの 20〜70 秒前に去る。続けて来るときも、いったん去ってまた来るように見える。
  1.5 秒かけてふわっと現れ、ふわっと消える（`CreatureVisit.opacityAt`）。
- 一度に最大 2 匹、同じ種類は 1 匹。表の上の行ほど優先。
- 評価は `GardenViewModel.watchVisitors()`：区切りが変わったとき、庭・天気・広さ・季節が変わったときに計算し直す。アプリを開いているあいだだけ。
- 描画は `GardenCanvas` の `visitors`。足もとの位置は `visitorBase`（ベンチは座面、建物は屋根の高さ）。絵は `ui/render/CreatureSprites.kt`（仮）。
  生き物は物と同じ奥行きの順で描き、タップは受けない。

### 自然メモ（図鑑の「いきもの」の章）

- 生き物が**見え始めた瞬間**に `RecordNatureNoteUseCase` が書く：生き物（`creature:<id>`）と組み合わせ（`combination:<rule>`）の 2 行。
  同じ区切りの同じ訪問は 1 回だけ（ViewModel が覚えている）。
- 初めての生き物は「🐦 ことりが遊びにきました。自然メモに書きとめました」、知っている生き物の新しい組み合わせは「📝 自然メモに新しい出会いを書きとめました」。それ以外は何も言わない。
- **あたらしい**（#32）：初めての生き物の行は `NatureNote.opened = false` で書かれ、そのページを開くまで「新しい」扱い（`NatureNotes.isNew` / `unopened`）。
  - 図鑑ボタンの赤丸（`GardenViewModel.collectionReady`）は「登録待ちのページ数＋まだ開いていない生き物の数」。
  - 本の「いきもの」のしおりに赤い点。ほかの章に登録待ちが無ければ、本は「いきもの」の章から開く。
  - 章は新しい生き物のページから開き、ページが表示されたら `OpenNatureNoteUseCase` で既読にする。「あたらしい」の札は本を閉じるまで残す。
  - 組み合わせが増えただけでは「新しい」にしない。
- 本の最後のタブ「いきもの」（`NatureNotesChapter`）：最初のページが**もくじ**（#32）で、全種を 3 列で並べる。出会った生き物は色つきの絵と名前、まだのものは**影絵**（同じ絵を薄い一色で塗りつぶしたもの）と「？？？」。タップでその生き物のページへ。
  そのあとは 1 ページ 1 種。出会った生き物は絵・名前・ひとこと・**はじめて あった ひ**（#32）・「みつけた くみあわせ」。
  まだのものは影絵と「？？？」と、**うわさ**（「〜の ちかくを このむようだ」のような控えめなヒント）だけ。数や割合は出さない。
- コインやレベルの報酬は無い。図鑑の進み具合（表紙の %）にも数えない。

## ローカル通知

アプリから離れているあいだに、庭のようすをそっと知らせる。

| 種類（`GardenNotificationKind`） | いつ | 文面の例 |
|---|---|---|
| `HARVEST_READY` | 作物が満開になる | お庭のチューリップが実ったようです 🌷 |
| `NEEDS_WATER` | 作物の水分が 20% 以下になる | チューリップがお水をほしがっているようです 💧 |
| `BUILDING_READY` | 建築が終わり、お披露目待ちになる | ウッドフェンスができあがったようです。お披露目を待っています ✨ |

解体・片づけ・枯れは知らせない（作業や叱る話はしない）。

- **予約するのはアプリから離れたとき**（`GardenViewModel.onBackground`）。**戻ったときは全部取り消す**（`onForeground`）。遊んでいる間は鳴らない。
- 予測は `PlanGardenNotificationsUseCase`。オフライン計算（`CalculateOfflineProgressUseCase`）で作物を未来へ進めて、その瞬間を探す。
  天気は日付で決まる（`WeatherForecast.Daily`）ので、予測は実際と一致する。30 分刻みで進め、見つかったら 1 分単位まで二分探索。先は 7 日まで。
  - 種類ごとに**いちばん早い 1 通だけ**。その後 1 時間以内に同じ種類が続くものはまとめて「〜など、N つの作物が…」。
  - **時間帯に関わらず、起きた瞬間に出す**（#28 で 21〜8 時の繰り延べを廃止。チャンネルが無音なので、夜は通知欄に溜まるだけ）。
- 予約と発行は `WorkManagerNotificationScheduler`（`NotificationScheduler` の実装）と `GardenNotificationWorker`。
  種類ごとに 1 つのユニークジョブ（`REPLACE`）。WorkManager なので再起動をまたいでも残る。
  発行の直前に、設定のスイッチと端末の許可をもう一度確かめる。どちらかが無ければ何もしない。
- チャンネルは `garden`「お庭のようす」。重要度 DEFAULT、**音とバイブなし**（ステータスバーと通知欄に出るだけ）。アプリ起動時に作る。
- 押すと `MainActivity` が開く（タイトル画面から）。

### 設定と権限

- スイッチは `AppSettings.notificationsEnabled`（既定 ON）。OFF にすると `NotificationScheduler.cancelAll()` で予約も表示中の通知も消す。
- 画面のスイッチは**本当に届くときだけ ON に見える**（`NotificationAccess.of`）：ゲームの設定が ON、Android 13 以上なら `POST_NOTIFICATIONS` が許可済み、端末の通知設定でアプリが止められていない。
  **端末が許可していなければ、ゲームのスイッチは ON にならない**。
- スイッチを ON にしたときの流れは `NotificationAccess.whenTurnedOn`（#33）。どの場合も、端末が許可するまでゲームの設定は変えない。
  - Android 13 以上で `POST_NOTIFICATIONS` が無い（`EXPLAIN_PERMISSION`）：木目調の案内ダイアログ（`NotificationPermissionDialog`）で
    「お便りや季節のお知らせを受け取るには、通知の許可が必要です」と伝え、「OK」でシステムの権限ダイアログを出す。「やめておく」ならスイッチは OFF のまま。
    答えのあとは `NotificationAccess.afterPermissionAnswer`：許可→ON（端末のチャンネル設定がまだ止めていれば端末の設定へ）、拒否→OFF のまま。
    ただし以前「今後表示しない」で拒否されていてシステムが何も聞かずに断ったとき（問う前も後も `shouldShowRequestPermissionRationale` が false）は、端末の設定を開く。
  - 権限はあるが端末の通知設定でアプリが止められている（`OPEN_DEVICE_SETTINGS`）：**端末のアプリ通知設定を開く**。
    戻ってきたとき端末が許可していれば（`NotificationAccess.deviceReady`）、そこで初めてゲームの設定を ON にする（`NotificationSettingRow` の `awaitingDevice`）。
  端末が許可していない間は「端末で、通知が許可されていません。スイッチを入れると、許可の手続きにすすみます。」と「端末の設定を開く」を出す。ゲームはそのまま遊べる。
- **一度だけの招待**：チュートリアルと機能の解放がすべて終わったあと（`TutorialStep.DONE`）、Android 13 以上で権限が無ければ、木目調のダイアログで「お知らせを受け取る／いまはいい」を聞く。
  どちらを選んでも二度と出ない（`AppSettings.notificationInviteShown`）。既存の庭（チュートリアル済み）では、更新後に最初に庭へ入ったときに出る。
  - 「はじめる」直後に一瞬出て消える不具合を #28 で修正：新しい庭の `garden_progress` が作られる前は段階が読めず、以前は「DONE」とみなしていたため。
    今は段階が読めるまで `GardenViewModel.tutorialStep` は null、招待は初期化（`initJob`）が済んでから判定する。

## 時間の計算（作物）

`CalculateOfflineProgressUseCase`。1 分＝1 tick、最大 7 日ぶん。ループせず O(1) の閉形式。

- 健康な間は成長。水分 20% 以下で「のどがかわいた」、栄養 20% 以下で「栄養不足」となりストレスが増える。
- **満開のまま 72 時間**でもストレスが満ち、枯れる。枯れても消えない。
- **水やり**（`CareForPlantUseCase.water`）：水分 80% 未満のときだけでき、1 回で **+35 ポイント**（`WATER_AMOUNT`、上限 100%。`wateredMoisture`）、5 XP。
  #37 までは 1 回で 100% に戻っていた（#38 で変更）。2% からなら 37%、50% から 85%、65% 以上からは 100%。
  80% 未満なら続けて水やりできるので、カラカラの土を満たすには 2〜3 回かかる（そのぶん XP も 1 回ごとに入る）。
  復活（`revive`）と雨は従来どおり 100% にする。
- ストレス 100% で `WITHERED`。復活は 30 コイン（`CareForPlantUseCase.revive`）。
  枯れた作物を撤去すると土がたい肥入りになり、次の種は成長 20% から始まる。
- **天候**は `WeatherType`（晴れ・くもり・雨）。UTC の日付から決定的に決まる（`forTime`）。
  雨は水分を満たし、ストレスの増加をすべて止める。
- `WeatherForecast` を渡すと、不在期間を**日ごとの天気で区切って**計算する（`WeatherForecast.Daily`）。
  `WeatherType` 自体も「ずっとその天気」という予報として渡せる（テストはこれを使う）。
- **建物のパッシブ効果**（`StructureEffect` / `PlantSurroundings`）：
  井戸は周囲 1 マスの乾きを 0.5 倍、小屋は周囲 1 マスの成長を 1.1 倍、風車（#40）は周囲 2 マスの乾きを 0.75 倍。同種は重ならない（乾きはいちばん効くものだけ）。完成した建物だけ有効。
  単体計算のときは `garden` 引数に庭全体を渡すこと（渡さないと「野ざらし」扱いになる）。

## 建築・解体（時間のかかる作業）

`GridObject` に `workKind` `workStartedAt` `workEndsAt` がある（DB v5）。

- **経験値と図鑑は、お披露目のとき**（#50）：建築に時間のかかる物（`buildSeconds > 0`、柵も含む）は、置いたときではなく
  `UnveilBuildingUseCase` で `placeXp` を得て、初めてなら図鑑のページが登録待ちになる（`RecordCollectionUseCase`）。すぐ置ける物は従来どおり置いたとき。
  工事中・お披露目前に取りやめた物は経験値を得ていないので、しまっても減らない（`StoreObjectUseCase`）。
- 完成（READY）になったときの音はお祝いの `LEVEL_UP`（#50。それまではコインの音）。
- 工事中の物が庭から消えるとき（しまう・取りやめ）は、**工事現場のまま**ふっと消える（#50。完成した姿が一瞬見えていた）。

```
NONE ──置く──▶ BUILDING ──時間──▶ READY ──プレイヤーのタップ──▶ NONE（完成）
NONE ──破棄──▶ DEMOLISHING ──時間──▶ 消える
NONE ──持ち物へ──▶ PACKING ──時間──▶ 持ち物に入る
```

- 建築秒数は `GardenItem.buildSeconds`（#43 から：フェンス 20 秒、井戸 30 分、風車 45 分、小屋 1 時間、コテージ 2 時間。それまでは 20 秒〜10 分）。
  解体・片づけは半分、ただし最長 5 分（`MAX_DEMOLISH_SECONDS`）。考え方は `GardenItem.kt` の KDoc。
- 残り時間の表示（`durationText`）は 1 時間未満なら「分・秒」、1 時間からは「時間・分」（秒は切り上げて短く見せない。`SpokenDuration`）。
- 時間切れの処理は `CompleteWorkUseCase`。ViewModel の `watchWork()` が次の期限まで待って同期する（ポーリングなし）。
- **完成は自動ではない**。`READY` で止まり、タップで `UnveilBuildingUseCase`。
- 時刻改ざんを検出した同期では、作業は最初からやり直しになる（`SyncGardenUseCase`）。
- 移動は `MoveObjectUseCase`（即時）。作物・耕した土・作業中の物は動かせない。#42 から編集モードのときだけ（「編集モード（#42）」）。
- 即時の取り外しは `StoreObjectUseCase` / `RemoveObjectUseCase`、時間のかかる入口が `StartRemovalUseCase`。

## DB

`pocket_garden.db`、現在 **v13**（v12→v13 は `grid_objects.harvestCount`、既存の作物は 0＝まだ収穫していない）。スキーマは `app/schemas/` に出力される。

| 版 | 変更 |
|---|---|
| 2 | `user_profile.currentXp` |
| 3 | `collection_entries`（図鑑） |
| 4 | `collection_entries.revealed`（手動登録。既存行は登録済みに） |
| 5 | `grid_objects.workKind / workStartedAt / workEndsAt`（既存はすべて完成扱い） |
| 6 | `garden_progress`（季節とチュートリアルの段階。1 行のみ）。行はマイグレーションではなく `ensureInitialized` が作る |
| 7 | `garden_progress.gardenSize`（既存の庭は 9） |
| 8 | `nature_notes`（自然メモ。`noteId` が主キー、`firstSeenAt`、`count`）。作成 SQL は `AppDatabase.CREATE_NATURE_NOTES`（テストで `schemas/8.json` と照合） |
| 9 | `inventory_slots.acquiredOrder`（入手の通し番号。既存の枠は 0＝いちばん古い扱い） |
| 10 | `letters`（ポストのお便り。作成 SQL は `AppDatabase.CREATE_LETTERS`、テストで `schemas/10.json` と照合）、`garden_progress.seasonStartedAt`（季節の始まり。既存は 0＝次の同期で時計を始める） |
| 11 | `nature_notes.opened`（既存の行は既読＝1） |
| 12 | `letters.topic`（#34、`MIGRATION_11_12`。ヒントの種類・入荷のレベル。既存の手紙は空文字） |
| 13 | `grid_objects.harvestCount`（#39、`MIGRATION_12_13`。連続収穫の作物が何回収穫されたか。既存は 0） |

- マイグレーションは `AppDatabase.MIGRATION_x_y`、登録は `di/DatabaseModule.kt`。
- DB は SQLCipher。鍵は Android Keystore で包む。プロフィールは HMAC 署名付き（`SecurityManager`）。
- 繋がり方（川・フェンス）、風、影などの見た目は**保存しない**。位置から毎回求める。

## 描画エンジン

すべて `ui/components/GardenCanvas.kt` の Canvas 1 枚。

- `IsometricTransform`: タイル 128×64dp、高さ 1 段 16dp。`worldToScreen(x, y, z)`。
- `GardenCamera`: ピンチとドラッグ。ピンチアウトの限界は全体が入る倍率（`FIT_MARGIN = 0.86`、木枠の分を空けている）。
  既定の倍率 `GardenCamera.starting` は**画面だけで決まる**（#51）：`max(START_SCALE = 0.375, 13×13 が入る倍率)`。庭の大きさに左右されないので、新しい庭も広がった直後の庭も同じ倍率・中央で開く。
- `LayerPipeline`: 描画命令を層（地面 → グリッド → 影 → 地表物 → 物 → 上のエフェクト → HUD）と奥行きで並べる。
- 物の描画は `submitObjects()`。図鑑の絵 `ObjectPreview` も同じ関数を使う（絵がずれない）。
- スプライトは `assets/atlas/atlas.png` + `assets_manifest.json`。無い物はプログラム描画。
- 地面の絵（川・石畳）は `res/drawable-nodpi/` から `R.drawable` で読む。
- **川の岸**（#51）：絵は真上から見た平らなもの。`drawRiverTile` が絵と同じ空間で、奥の岸（北・西、`RiverAutotile.shadedSides`）の内側の水に影、手前の岸（南・東、`litSides`）に細い光を `SRC_ATOP` で重ねる（絵の上だけ。芝には出ない）。水は芝より一段低く見える。
- **庭の木枠の内縁**（`drawFrameLip`）：地面の層の最後（`Float.MAX_VALUE`）に描く。奥の 2 枚の板の影と、木と土の境の線が、川・石畳・畑の上にかかり、地面が枠の下に収まって見える。

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

- **つながった畑（#47）**：`SoilJoins.at(x, y)` が上下左右のマスも耕した土かを見る。つながっている辺は
  マスの縁にまっすぐ置き（揺れなし・内側への寄せなし）、縁取り・こぼれた土・畝の切れ目を描かない。隣どうしの輪郭がぴったり重なり、1 枚の畑になる。
  - 畝は全マス共通の行（`furrowRows`）なので、横につながると長い畝になる。
  - 土の色は `fieldDampness`（2 マス間隔の格子のなめらかなノイズ）で、マスごとに色が跳ばない。湿った・乾いた斑（`patches`）は周り 8 マスの分も描いて自分の形で切り抜くので、マスの境をまたぐ。
  - 開いている辺だけに濃い縁取り・草地へこぼれた土・土くれと草。手前（東・南）の開いた辺には低い土の側面（畑が芝生より少し高い）。
  - 耕した土は `floor_tilled` の絵を使わず、すべてこの描画（1 マスの絵は四方に縁のある塊で、つなげられないため）。ガラスの温室の床は従来どおり絵。

### 工事現場

`drawWorkSite`：工事シート＋足場＋土ぼこり＋進捗バー。**中身は一切描かない。**
`READY` は進捗バーの代わりに「✨！」の吹き出し。完成時は `drawCompletionBurst` と `popScale`。

## 起動と導入（初回）

- `MainActivity` は `TitleScreen` → `GardenScreen` の 2 画面を `Crossfade` で切り替えるだけ（ナビゲーションライブラリは不使用）。
  庭に入ったら戻らない。戻るキーはアプリを閉じる。
- タイトル（`ui/title/`）：背景は `GardenBackdrop`、木の看板、`GardenCanvas` で描く**見本の庭**（`TitleSampleGarden`、保存しない。
  花が 1.2 秒ごとに 1 段ずつ育ち、満開でしばらく止まってから最初に戻る）。見本の上に透明な層を重ね、触っても動かない。
  - 「はじめる」：セーブが無ければそのまま庭へ。あれば確認のうえ `deleteSavedGarden()` で全部消してから庭へ。
  - 「つづきから」：セーブがあるときだけ押せる。セーブの有無は `hasSavedGarden()`（プロフィールの行があるか）。
- **BGM は 5 つ**（#35、`BgmTrack`）：`TITLE`（`bgm_title`）と、季節ごとの `SPRING` `SUMMER` `AUTUMN` `WINTER`（`bgm_spring` ほか）。
  - **5 曲すべてのファイルがそろっている**（#37）：`bgm_title.mp3` `bgm_spring.mp3`（以前の `bgm_garden.mp3` を改名）`bgm_summer.mp3`（#36）`bgm_autumn.mp3` `bgm_winter.mp3`（#37）。
    どの曲も自分のファイルで鳴り、曲が変わるたびにクロスフェードする（タイトル→庭、季節の変わり目）。春の曲への代替は、ファイルが欠けたときの保険として残してある。
    **自分のファイルが無い曲は春の曲で鳴る**（`BgmTrack.FALLBACK_RESOURCE`、`AudioManager.resourceFor`）。
    新しい曲は `res/raw` に同じ名前で置くだけでよい（コードの変更は不要）。
  - 同じファイルに行き着く曲どうしの切り替え（例：曲の無いタイトル→春の庭）は、`playBgm` が「もう鳴っている」として途切れずに続ける。
    違うファイルなら `AudioEngine.startBgm` が**クロスフェード**（1.2 秒、12 段）。鳴っていた曲は下がりながら止まり、新しい曲は 0 から設定の音量まで上がる。
    一時停止中・ミュート中に曲が変わったら、戻ったときに新しい曲から始まる。
  - タイトルは `TitleViewModel.onForeground` で `TITLE`。庭は `GardenViewModel.watchSeasonForMusic` が庭の季節を見て `BgmTrack.forSeason`。
    遊んでいる最中に季節が変わるとその場でクロスフェード。庭が読めるまでは何も選ばず、タイトルの曲が鳴り続ける（仮の季節で一瞬鳴らさない）。
  - ライフサイクルは従来どおり RESUME/PAUSE で `resumeBgm` / `pauseBgm`。
- **設定との連動**：BGM の音量スライダーとオン・オフは `SettingsViewModel.change` が**保存より先に** `AudioManager.apply` へ渡す（指の動きにそのまま追従）。
  保存は DataStore（`bgm_enabled` `bgm_volume`）。起動時は `PocketGardenApplication` が保存値を同期で読んでから鳴らし、以後は保存値の変化も `apply` に流す。
  ミュートは一時停止（再開すると同じ位置から）、音量を変えるとダッキングやフェードの途中でもその値が勝つ。
- 見本の庭（`TitleSampleGarden`）は **11×11**（`TitleSampleGarden.MAP`）。コテージ・小屋・井戸、バラ・チューリップ・ひまわりの花壇、石畳、柵、川、ウッドデッキ、
  ベンチ・石どうろう・岩・野の花などを並べ、生き物（屋根とベンチのことり、花壇のちょうちょ）を常にいさせる（`visitors`、来訪の時刻は 0〜`Long.MAX_VALUE`）。
  花は 8 拍で育ち 12 拍満開のまま（多くがいつも咲いている）。`GardenCanvas(initialZoom = 1.22)` で全体が収まる倍率より少し寄せ、左右の角が少しはみ出すくらいに大きく見せる。
- 看板（`TitleSign`）は 2 本のロープで上から吊るした木の板。木目・縁の溝・釘をコードで描き、名前は影付きの大きな文字、下に「POCKET GARDEN」と左右の小枝（`Sprig`）。絵文字は使わない。
- 新しい庭は `GardenRepositoryImpl.ensureInitialized` が作る：プロフィール（100 コイン）、空の持ち物、
  `StarterGarden.objects()`（**更地に木箱 1 つだけ**。#28 で柵と耕した土をなくした）、`GardenProgress.NEW_GARDEN`（春・木箱から）。
  **最初の種は持ち物に入っていない**（以前は 3 個。今は木箱の 1 個だけ）。
  セーブ済みで `garden_progress` が無い庭（v5 以前）には `EXISTING_GARDEN`（春・チュートリアル済み）を入れる。

### チュートリアル（`TutorialStep`）

```
OPEN_CRATE ──木箱を開ける──▶ TILL_SOIL ──1 か所耕す──▶ PLANT_SEED ──作物が庭にある──▶ WATER_SEEDLING ──水やり──▶ FINISHED
FINISHED ──タップ──▶ REVEAL_EDIT ──タップ──▶ REVEAL_RAIN ──タップ──▶ REVEAL_COLLECTION ──タップ──▶ REVEAL_CAMERA ──タップ──▶ REVEAL_POST ──タップ──▶ DONE
```

- 段階は名前で保存する（`garden_progress.tutorialStep`）ので、段階を足しても DB の版は上げない。知らない名前は `DONE`。
- `OPEN_CRATE`〜`WATER_SEEDLING` が導き（`TutorialStep.isGuiding`）：札とまたたく星が出る（#46 から耕す段階は星なし。どこを耕してもよい）。
- **導きの間の道具の制限（#46）**：`TutorialStep.allows(TutorialTool)`。木箱の段階は何も、耕す段階はクワだけ、種まきは持ち物だけ、水やりは水やりだけ。
  ツールバー（`QuickActionBar(allows)`）のほかのボタンは灰色で押せない。`FINISHED` 以降は全部使える。
  - クワは `HoeUseCase` も守る：導きの間で `allows(HOE)` でなければ `NOT_THIS_STEP`。`TILL_SOIL` で 1 か所耕すと、同じトランザクションで `PLANT_SEED` へ進む。
    画面側もクワを自動で置く（2 か所目は耕せない）。
  - 種まき・水やりの段階のパネルは、作物の状態と「水やり」だけ（収穫・撤去・下の注意書きを出さない。`ObjectStatusDialog(guiding)`）。
  - 木箱を開けるときは編集モードを切る（格子線は出ない）。種を受け取った音はコインではなく軽いタップ音（`SoundEffect.TAP`）。
  - 木箱のダイアログの「後で」「受け取る」は同じ幅の枠つきボタン（`CarvedButton` と `WoodButton`）。
- 編集モード（✏️）も段階解放の 1 つ（`GardenFeature.EDIT_MODE`、最初に渡す）。それまで ✏️ は出さない。
- **機能の段階解放**（`GardenFeature`）：「編集モード」「慈雨」「図鑑」「カメラ」「ポスト」は導きの間と `FINISHED` では画面に出さない。
  `TutorialStep.reveals(feature)` が true になった順に、ボタンがぽんと現れる（`GardenScreen` の `RevealedFeature`）。
  紹介中（`TutorialStep.introducing`）のボタンには金色の光る枠（`revealGlow`）と「NEW」札、画面の中央に木の札（`FeatureRevealCard`、名前・ひとこと・場所・「わかった」）。
  札はダイアログにせず庭に重ねるので、光るボタンが同時に見える。「わかった」で `FollowTutorialUseCase.acknowledge` が次へ進める（今の段階と違う札のタップは無視）。
  ゲームを閉じても段階は残るので、次に開いたときに続きから紹介される。チュートリアル済みの既存の庭（`DONE`）は全部最初から出ている。
  段階が読めるまで（起動直後）はどのボタンも出さず、読めたときに最初からある物はアニメーションなしで出す。

- 木箱 `deco_starter_crate` はアイテムではない（買えない・動かせない・しまえない）。タップで選択の代わりに木目のポップアップが開き、
  「受け取る」で `OpenStarterCrateUseCase`：木箱を消し、チューリップの種 1 個を持ち物へ（同じ種に重ねる／空き枠へ。満杯なら何もしない）。
- 段階を進めるのは `FollowTutorialUseCase`。**プレイヤーの操作ではなく庭の状態を見て**進める（起動時・同期ごと・置いた後・耕した後に呼ぶ）。
  途中でアプリを閉じても、別の手順で達成しても、次に見たときに追いつく。いくつかの段階が一度に済んでいれば、まとめて進める。
  - 耕す段階は、どこかに耕した土があれば済み。星は木箱のあった中央の芝生（空いている中で中央にいちばん近いマス）に出る。
  - 種まきの段階で作物が現れたら、その作物の水分を 50%（`SOWN_SEED_MOISTURE`）にする。新しい作物は水分 100% で、
    水やり（80% 未満のときだけできる）がすぐにはできないため。
  - 水やりの段階は `wateringDone()` で終わる。雨などで「まだ湿っている」になった水やりも 1 回と数える。
  - 水やりの前に作物が無くなったら（収穫・撤去）、`FINISHED` へ（機能の解放はそのまま続く）。
- 導きは控えめに：対象のマス（`TutorialGuide.hintCells`）に小さな星 3 つとほのかな光（`drawHintTwinkle`）、
  ツールバーの上に木の札の一文（`TutorialHint`）。札は他のパネルが出ているあいだは引っ込む。操作は何も制限しない。
- 完了時は**木目調のダイアログ**（`TutorialDoneDialog`）。「準備はおしまい」と、自由な庭づくりへ送り出す文（改行は文言側で手で入れてある）、「庭づくりをはじめる」。
  タップするまで消えない。トーストは出さない。

### 四季（#30）

季節は庭ごとの時計で進む（`domain/model/Seasons.kt` の `SeasonClock`）。新しい庭は作った日に春から始まる（チュートリアルのチューリップがいつでも植えられるように）。

| 項目 | 内容 |
|---|---|
| めぐり | 春→夏→秋→冬→春。**1 季節 ＝ 実時間 `SeasonClock.DAYS_PER_SEASON`（10）日**。この 1 つの定数で速さを変えられる。不在中も進む |
| 保存 | `garden_progress.season` と `seasonStartedAt`（今の季節が始まった時刻）。`seasonStartedAt == 0` は「まだ時計が動いていない」で、次の同期で今から始める |
| 進める | `AdvanceSeasonUseCase`（`GardenViewModel.sync()` から毎回）。何季節分たっても「今の季節」だけを、本当に始まった時刻で保存する。時計が戻っても季節は戻さない |
| お便り | 季節が来たら「〇〇がやってきました」（`LetterKind.SEASON_ARRIVED`）、終わりの 2 日前（`NOTICE_MILLIS`）から「もうすぐ〇〇」（`SEASON_COMING`、まとめ買いと温室の案内）。`letterId` に季節と時刻を入れて、同じ知らせは 2 通届かない。長く留守にしていたときは今の季節の 1 通だけ |
| 遊んでいる最中に変わったら | 効果音と「〇〇がやってきました。ポストにお便りが届いています」のトースト |
| 旬 | `GardenItem.seasons`（空＝通年）と `isInSeason`。作物以外はすべて通年 |
| ショップ | 作物の棚は旬の物と通年の物だけ（`GardenItem.shelf(category, season)`、棚の上に「いまは〇の種が並んでいます」）。`PurchaseItemUseCase` も季節外れは `OUT_OF_SEASON` で断る。**まとめ買いは自由**、持ち物の種は季節が変わっても残る |
| 植え付け | `PlaceItemUseCase`：季節外れの種・苗は `OUT_OF_SEASON`（「🌱 いまは育てるのに適さない季節です…」）。種は持ち物に残る。**ガラスの温室の上なら季節を問わない** |
| すでに植わっている作物 | 季節が変わってもそのまま育つ（枯れたり止まったりしない。罰しない方針） |
| デバッグ | 開発者メニューの「次の季節へ」（`AdvanceSeasonUseCase.skipSeason`）。「+1 時間／+1 日」の時間送りも季節の時計を進める |

#### 作物の旬

| 作物 | 旬 | 解放 | 種の値段 |
|---|---|---|---|
| チューリップ | 春 | 最初から | 20 |
| ひまわり | 夏 | Lv.2 | 35 |
| バラ | 春・秋 | Lv.4 | 60 |
| コスモス（#30 で追加） | 秋 | 最初から | 20 |
| スノードロップ（#30 で追加） | 冬 | 最初から | 20 |
| ミント（#30 で追加） | 通年 | 最初から | 15 |
| トマト（#31 で追加） | 夏 | 最初から | 20 |
| イチゴ（#31 で追加） | 春 | 最初から | 20 |
| スイカ（#31 で追加） | 夏 | Lv.3 | 45 |
| ラベンダー（#31 で追加） | 通年 | Lv.3 | 40 |
| カボチャ（#31 で追加） | 秋 | Lv.5 | 70 |
| ポインセチア（#31 で追加） | 冬 | Lv.5 | 70 |
| 大根（#39 で追加） | 秋・冬 | Lv.2 | 30 |
| パプリカ（#39 で追加） | 夏 | Lv.4 | 55 |

どの季節にも、最初から植えられる旬の作物がある（夏はトマト。#31 まではミントだけだった）。
収益の規則（`ProgressionTest`）：解放レベルが高い作物ほど、1 時間あたりのコイン（収穫−種の値段）と XP が、それより低いレベルのすべての作物を上回る。
Lv.1 の作物は Lv.2 のひまわり未満、Lv.3 はひまわりとバラ（Lv.4）のあいだ、Lv.5 はバラより上に収めてある。
また、育つのに時間がかかる作物ほど収穫のコインと XP が多い（`GardenActionUseCasesTest`）。乾く時間（`dryOutHours`）は 16 時間以上にする（半日で水切れになると、12 時間の成長を見るテストが落ちる）。
もちもののタブでは、ラベンダーはミントと同じ「ハーブ・観葉」（`InventoryGroup.GREENERY`）、ほかは「作物」。
アトラスの絵があるのはチューリップ・ひまわり・バラ（4 段階＋枯れ）と、トマト・イチゴ・パプリカ・大根（#39、**3 段階**＋枯れ、`growthStages = 3`）。
ほかの 7 種は絵が無いので、コードで描いている（`ui/render/CropArt.kt`。種類ごとの絵は `CROP_DRAWINGS` の 1 行と private な関数 1 つで、`GardenCanvas.drawPlant` は `drawCropInCode` を呼ぶだけ）。
スプライト名の決まり（`<objectId>_stage<N>`・`_withered`・`creature_<id>`・風車の羽根）は `TileSetDefinition` にまとめてあり、`expectedSpriteIds` がゲームの引くスプライト名の一覧。アトラスにあってこの一覧に無い名前（綴り違い）は `CodeArtTest` が落とす。
アトラスに絵があればそちらが優先されるので、トマト・イチゴの `drawTomato` `drawStrawberry` は今は使われない（絵が欠けたときの代わりとして残してある）。

#### 収穫のしかた（#39、`HarvestType`）

| 種類 | 作物 | 収穫すると |
|---|---|---|
| `SINGLE_HARVEST`（単発） | 大根・チューリップ・ひまわり・バラほか、下の 3 種以外すべて | 作物は消え、下の耕した土が空く（従来どおり） |
| `REPEATABLE_HARVEST`（連続） | トマト・イチゴ・パプリカ | 株は残り、成長が **2 段階目の始まり**（`PlantSpecies.regrowFrom`。3 段階なら 50%）に戻る。時間と世話でまた 3 段階目（満開）へ |

- 処理は `HarvestPlantUseCase`（残すかどうかは `HarvestPlantUseCase.afterHarvest`）。残る株は水分・栄養・これまでのストレスをそのまま持ち越し、`harvestCount` が 1 増える。
- **2 回目からの収穫は少なめ**（`PlantSpecies.harvestReward(harvestCount)`）：1 回目は `harvestCoins`/`harvestXp`、2 回目からは `regrowCoins`/`regrowXp`。
  もう一度実るまでの時間（`regrowHours`）は育つ時間の半分。2 回目からの 1 時間あたりの稼ぎが 1 回目（種代込み）を超えないように決めてある
  （トマト 17 コイン＋26 XP／24 時間、イチゴ 14＋22／21 時間、パプリカ 42＋59／50 時間）。種を買い直さず植え直さなくてよいことだけが得で、
  上のレベルの作物を追い越さない（`ProgressionTest`）。
- 収穫ボタンの金額と、浮かぶ「+コイン」も同じ値を見る。作物の詳細には「🔁 収穫しても株は残り、また実ります」の一文。
- 満開でなくなるので、収穫した株に「満開のまま 72 時間で枯れる」時計は動かない。放っておけば水切れなどでほかの作物と同じように枯れる。
- **肥料**（#50）：栄養が 50% 未満（`FERTILIZING_THRESHOLD`）のときだけ。満タンにして 2 時間ぶん育つ。栄養は 3 日で尽きるので、あげられるのは 1 日半に 1 回ほど
  （それまでは何度でもあげられ、連打で一気に実った）。足りているときは `ALREADY_FED`「まだ栄養は足りています」。
- **枯れた作物**：絵は `<objectId>_withered`（`TileSetDefinition.assetIdFor`）。収穫・水やり・肥料はできず（`WITHERED`）、勝手には消えない。
  **空き地に戻すのは撤去だけ**（`RemoveObjectUseCase`。下の耕した土はたい肥入りになる）。30 コインの復活（`revive`）は従来どおり残してある（株がよみがえるだけで、空き地にはならない）。
ショップ・もちもの・図鑑のアイコンは絵文字（`UiLabels.itemEmoji`）。

#### ガラスの温室（`floor_greenhouse`）

- Lv.18・300 コイン（コテージと同じ、店でいちばん高い物）・設置 XP 40。棚は「構造物」、持ち物のタブは「かぐ」。図鑑の「たてもの」の章に 1 ページ。
- **1 マスの特殊な床**：耕した土の代わりになる床（`GridObject.isGreenhouse`、`GardenGrid.isTilled` は温室も含む、`isUnderGlass`）。芝生に置く。クワは要らない。
- 描画は耕した土＋その上にかぶせるガラスの家（`drawGreenhouseGlass`、作物より手前に描く半透明の板と白い枠）。
- 移動はできない。持ち物に戻す・壊すは、中で作物が育っている間は `IN_USE` で断る。

#### 季節の見た目

- 芝生のマスごとに季節の層を重ねる（`drawSeasonGround`、`SeasonGround`）：春は花びら、夏は濃い緑、秋は黄金色と落ち葉、冬は雪と小さなきらめき。位置はマス座標から決まる。
- 背景（`GardenBackdrop(season = …)`）：丘と草原の色を季節の色へ寄せ、枝と茂みの葉の色を変える（秋は橙、冬は雪をかぶる）。空は天気の色のまま。
- 作物・建物・川そのものの見た目は季節で変えていない。

#### ポスト（お便り）

- DB v10 の `letters` 表（`LetterEntity`、`LetterDao.insertIfAbsent`）、v12 で `topic` 列（#34）。リポジトリは `observeLetters` `postLetter`（同じ id は無視）`markLetterRead(letterId)`。
- **アーカイブ**（#34）：手紙は消さない（消えるのは「はじめる」で庭ごとやり直したときだけ）。ポストは新しい順の一覧（`LazyColumn`）で、いつでもさかのぼって読める。
- **既読は 1 通ずつ**（#34）：一覧をタップすると手紙のページが開き、そのとき初めてその 1 通が既読になる（`PostDialog` の `LaunchedEffect` → `ReadLettersUseCase.markRead`）。ポストを開いただけでは既読にしない（#30〜#33 は開いた時点で全部既読だった）。
  未読は封をした封筒 ✉️・太字・赤い「NEW」のスタンプ（少し傾けた赤い札）、既読は 📄 と一段濃い紙。一覧の上に「まだ読んでいないお便り：N 通」。戻る／「一覧へもどる」で一覧へ。
- 画面右の 📮（チュートリアル後の機能解放の 4 番目、`GardenFeature.POST`）に未読数の赤丸（読んだ分だけ減る）。
- 文面は種類・季節・`topic` から画面側で組み立てる（`ui/post/PostDialog.kt`、`letter_*` の文字列）。保存するのは種類・季節・時刻・既読・topic だけ。

| 種類（`LetterKind`） | いつ | 中身 |
|---|---|---|
| `SEASON_ARRIVED` | 季節が来たとき | 庭のようす＋「ショップには〇の種」＋「この季節が旬の作物：…」（#34 で追加。`GardenItem.seasons` から） |
| `SEASON_COMING` | 季節の終わりの 2 日前 | まとめ買いと温室の案内 |
| `TIP`（#34） | 庭の最初の手紙から `PostOffice.TIP_INTERVAL_MILLIS`（2 日）後、以後は前のヒントから 2 日ごと | `LetterTip` の順に 1 通ずつ、各 1 回：お水→雨→たい肥→模様がえ（持ち物に戻す）→生き物→ガラスの温室（「温室を使うと、冬でも作物が育ちます」） |
| `NEW_STOCK`（#34） | レベルアップでショップに新しい物が並んだとき（`GardenViewModel.watchForLevelUps`） | 新しく並んだ物の一覧（作物には旬）。`topic` は「元のレベル-新しいレベル」、id は `stock:<新しいレベル>` |

- ヒントは同期のたび（`GardenViewModel.sync` → `PostLettersUseCase.tipIfDue`）に判定する。何を送ったかは手紙そのもの（`kind = TIP` の `topic`）から分かるので、別の記録は持たない。長い留守のあとでも 1 回の同期で届くのは 1 通だけ。
- ヒントの文面は、ゲームが本当にそうなっていることだけを書く。新しいヒントは `LetterTip` の最後に足す（途中に入れると、すでに届いた庭で順番がずれる）。

## 地面と建物の絵、向き、風車（#40）

- **絵の倍率**：`AssetManifest.drawScale`。足場より小さい絵（コテージ 約 2.5、風車 約 3.3）は、アトラスで拡大せず描くときに大きくする。
  寸法はすべて `drawnWidth(scale)`/`drawnHeight(scale)` を通す（`drawSprite`、影、足元の草）。地面のタイルと温室は 1 のまま（原画の段階で縮めてある）。
- **芝生**：`tile_grass` を全マスに 1 色で並べる（#47 で市松をやめた。`tile_grass_dark` は使わない）。季節の重ね（花びら・雪など）は絵の上にも描く。
  タイルには少し厚み（側面）があり、x→y の順に描くので手前のタイルが奥の側面を隠す。
  - 絵の明るさの傾き（奥が明るく手前が暗い）は加工で取り除いてある（`prepare_building_art.py` の `flatten_light`）。上面の奥の 2 辺は端をぼかしてある。
  - #51 から：絵の**上面のひし形だけ**を切り出し、縁に向けてぼかした絵（`LawnShade.feather`、`FEATHER_START = 0.74` から滑らかに 0 へ。`FeatheredLawn` がアトラスから一度だけ作る）を
    **1.32 倍**（`FEATHER_OVERLAP`）で描く。自分のマスの中はほぼ不透明、ぼかしは隣のマスに重なるので、縁の暗い線も側面も見えずマスの区切りが消える。
    下に絵の平均色（`LawnShade.meanColour`）を庭の形で敷き、芝生は庭の形で切り抜く。川（`TileType.RIVER`）のマスより先に描く。絵が読めないときは従来どおり。
  - （従来）各マスを 1.04 倍で描いて隣と重ねる（`LawnShade.CELL_OVERLAP`）。半分ほどのマスを左右反転（`LawnShade.mirrored`、マス座標のハッシュ）して、同じ模様が並ばないようにする。
  - その上に、マスをまたぐ大きく淡い明暗の斑（`LawnShade.patches`、1〜2 マス幅）を庭の形で切り抜いて重ねる。
- **耕した土**：コード描画の「つながった畑」（上の「耕した土」）。地面の層の中で**マスの奥から手前の順**（`1f + depthOf(x, y)`）に描く。
  たい肥入りは葉やわらの粒を足す。
- **作物の絵**：受け取った絵には株の下に土の山が付いていたが、`asset_packer.py --strip-crop-soil`（`tools/crop_soil.py`）で取り除いてある（#47）。
  アンカーは**茎の根元**（`crop_soil.stem_base`：横は絵の下 1/5 の中央値、縦はその近くのいちばん下。元気な株は絵のいちばん下、枯れて葉が垂れた株は少し上）。株はマスの中心＝畑の表面に立ち、風で傾くのは株だけ（傾きはアンカーを支点にするので、地面は動かない）。
- **ガラスの温室**：`floor_greenhouse` の絵を、作物より手前（`depthOf + 0.5`）に描く。中で作物が育っている間は **55% の不透明度**にして作物が透けて見えるようにしている。
- **向き（左右反転）**：`GridObject.isFlipped`（DB には以前からある列。移行なし）。向きを変えられるのはコテージ・温室・風車だけ（`GridObject.FLIPPABLE_OBJECT_IDS`、どれも足場が正方形なのでマスは変わらない）。
  - 置くとき：配置中の札に「↔ 向き」ボタン（`GardenViewModel.togglePlacingFlip` → `PlaceItemUseCase(flipped = …)`）。持ち物から取り出すたびに「ふつう」に戻る。向きを変えられない物では無視。
  - 置いたあと：建物の詳細に「↔ 向きを変える」（`FlipObjectUseCase`。工事中でもできる）。ほかの物は `NOT_FLIPPABLE`。
  - 描画：`drawSwayingSprite(flipped = obj.isFlipped)`。地面のタイルは光の向きが逆になるので反転させない（マニフェストの `isFlippedAllowed = false`）。
- **風車**（`StructureData.WINDMILL`、2×2、Lv.4・120 コイン・設置 45 XP・建築 45 分）：本体 `struct_windmill` の上に、羽根 `struct_windmill_blades` を
  本体の取り付け点（`attachX/attachY` → `AssetManifest.attachmentAt`）を軸にして回して描く（`drawTurningSprite`）。
  - 角度は `WindmillSails.angleAt(風の時計, 天気, x, y)`：晴れで 9 秒に 1 回転、くもり 1.25 倍、雨 1.6 倍。マスで少しずらして、並べても揃って回らない。
    風の時計（`windMillis`）を使うので、シートが出ていて庭が止まっている間は羽根も止まり、図鑑の絵（時計なし）では止まっている。
  - 反転した風車は、羽根の取り付け点も左右反転し、回る向きも逆になる（反対側から見た回転）。
  - 工事中は足場だけ（羽根は描かない）。絵がなければ小屋と同じ箱と屋根で描く。
- 建物の高さ（`StructureData.heightSteps`、建物の裏が隠れるかの判定）は絵に合わせた：コテージ 8 → **16**、風車 **20**（羽根の先まで）。
- タイトルの見本の庭にも風車（川の手前、(8, 8)）。そのため岩を (10, 8)、野の花の 1 つを (4, 8) へずらした。

## 画面

- 遊ぶ画面は `GardenScreen` だけ（その前にタイトル）。開発者メニュー（デバッグビルドのみ）に時間送り・天気・「+500 コイン・Lv+1」・「次の季節へ」。時間送りは建築・解体の残り時間も進める（#25 から）。ダイアログ（ショップ、図鑑、設定、持ち物）を上に出す。
- 下部ツールバーは固定。選択パネルと各モードのバナーはその上に**重ねて**フェードで切り替える。
- **庭の倍率と位置は保存する**（#28）：`AppSettings.gardenView`（`GardenView`：scale と pan。DataStore の `garden_view_*`）。
  `GardenCanvas` はプレイヤーが動かした（または庭が広がった）あと 0.6 秒止まったら `onViewChanged` で返し、`GardenViewModel.saveView` が書く。
  開くときは `savedView` が読めた時点で 1 回だけ `GardenCamera.restored` で戻す（画面に合う範囲に収め直す）。最初の既定の倍率は保存しない。
  - 「はじめる」（`TitleViewModel.startOver`、保存がなくても通す）で `gardenView = null` にする。庭が広がったときは `GardenCamera.starting` に戻す（#51）。
  - デバッグビルドは `GardenCanvas(onZoomChanged)` で倍率を受け取り、🛠 の下に `DebugZoomReadout`（`×0.38` 形式、`zoomText`）。開発者メニューにも標準倍率と並べて出す。
- **もちもの**（`InventoryBottomSheet`）は中身を `GardenViewModel.inventorySlots`（常に購読）から受け取り、`skipPartiallyExpanded` で最初から全高で開く。
  以前は開いてから中身を読み込んでいたため、せり上がる途中でシートが伸びてカクついていた。シートが出ている間は庭の風と川を止める（`GardenCanvas(paused)`）。
- **もちものの並び替え**（#29、`domain/model/InventorySort.kt`）：純関数 `InventoryOrder.arrange(slots, sort, filter, season)`。
  タブ（`InventoryFilter`：すべて／たね／かぐ／そのた）で絞ってから、`InventorySort` で並べ、空き枠は後ろ。ほかのタブの物の分は無地の枠で埋めて、どのタブでもマスの数を変えない。
  - 入手順：`InventorySlot.acquiredOrder`（DB v9）の大きい順。時刻ではなく**通し番号**（`List<InventorySlot>.nextAcquisition()`＝今の最大＋1）なので、時計に左右されない。
    買う・持ち物に戻す・木箱を開けるで更新し、同じ物の山に足したときも前へ出る。取り出し（置く）では変えない。
  - 種別：`InventoryGroup`（作物／家具・建物／ハーブ・観葉＝芝生と野の花／土・タイル＝小石・石畳・ウッドデッキ・川）の順。タブの「そのた」はハーブ・観葉と土・タイル。
  - 所持数順：多い順。季節順：旬（`GardenItem.seasons` に今の季節）→通年→旬が早く来る順（`InventoryOrder.seasonRank`）。
  - レベル順：`GardenItem.openingRank`（最初から→Lv の低い順→図鑑で開く物）。ショップの棚の並びも同じ値を使う。
  - 同じ順位は「新しく入った順→枠の番号」で決まる（並びが揺れない）。
  - 並び順は `AppSettings.inventorySort`（DataStore の `inventory_sort`、名前で保存）、タブは `GardenScreen` の `rememberSaveable`。
  - ヘッダー右の木の札（「⇅ 入手順 ▾」）でメニューを開く。タブや並びを変えても枠は滑らず、その場でぱっと入れ替わる（#33 で `animateItem` を外した。滑る動きがカクつきに見えたため）。
- **メニューの部品**（`ui/components/Cozy.kt`）：木と羊皮紙の色だけで作り、ボタンが板から浮かないよう低く（へこませて）揃える。
  - `ParchmentDialog`：淡い羊皮紙（`GardenColors.ParchmentPale` #F4EEDC）に木の枠と柔らかい影。縁は古い紙のように少し濃い。
    題・中身・右下のボタン。画面が低いときは**中身だけが縮む**（中身の中のスクロールが短くなる）ので、閉じるボタンは必ず届く。
  - `WoodPanel`：浅い木の板（木目の線、上の縁に影・下の縁に光＝へこみ）。設定のまとまりごと、ショップの棚に使う。
  - `CarvedButton`：板に彫ったようなへこんだボタン。閉じる・初期値に戻す・ライセンスなど脇役の操作。主役の 1 つ（買う・受け取る）は今までの `WoodButton`（盛り上がった板）。
  - `WoodToggle`（オン＝オリーブ `GardenColors.Olive`、木の枠）、`WoodSlider`（オリーブの帯と木のつまみ）、`WoodTabs`（選んだタブに木の下線）。
  - 設定（`SettingsDialog`）は ViewModel から値を受けて `SettingsContent`（状態を持たない本体）を描く。操作は `SettingsActions`（`SettingsViewModel` が実装）。
    タブ「音量」は BGM と効果音を 1 枚ずつの板に、「情報」は版と読み物、「その他」は通知の行。ショップも羊皮紙のダイアログ・木のタブ・棚の板にした。
- 図鑑の赤丸（`CountBadge`）は、フォントの上余白を切って数字を円の中央に置く。
- 図鑑のしおり（タブ）は「さくもつ／ちけい／たてもの／いきもの」（#33 で「ちけい・みち」「たてもの・さく」を短くした。4 つ並ぶと見切れていたため）。
  ラベル（`TabLabel`）は 1 行で、幅に入らなければ 1 割ずつ縮め（下限 8sp）、収まってから描く。
- **レベルアップ**（#33）は浮かぶ文字やトーストではなく、画面中央の木目調のボード（`LevelUpDialog`、「🎉 レベルが上がりました！」）。
  「OK」を押すまで閉じない（外側タップ・戻るでは閉じない）。新しく並んだ物があるときだけ「新しいアイテムが解放されました」と、その名前（最大 6 つ）を出す。
  状態は `GardenViewModel.levelUp`（`LevelUpNotice`）。ボードが出ている間にさらに上がったら 1 枚にまとめる（`LevelUpNotice.after`：元のレベルはそのまま、行き先を更新）。
  木箱・チュートリアル完了のボード・カメラモードの間は待たせ、通知の招待はボードが出ている間は出さない。
- 通知は自前の `GardenToastHost`（レイアウトを動かさない）。
- カメラモードは HUD 全体と排他の分岐。写真は庭の `graphicsLayer` を 4:5 の枠で切り抜く（`CameraFrame`）。
