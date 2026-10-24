# アセット台帳

ゲームに出る絵と音の**いまの状態**を、1 枠ずつ管理する表。作物は段階・枯れ・アイコンごと、建物・装飾・生き物は 1 つずつ。
加工の手順や絵の決まりごとは `ASSETS.md`、ショップの値段や解放条件は `ASSETS.md` の「ショップの取扱一覧」にある。

**絵や音を足す・差し替える・コード描画を絵にする・使わなくする**ときは、同じコミットでこの表を直すこと。
表と実物（アトラス、`res/` の画像と音、`PlantSpecies`、`GardenItem`、生き物、アイコン）が食い違うと `AssetLedgerTest` が落ちる。印・列の並び・集計の書き方を変えるときはテストも直す。

## 凡例

### 状態

| 印 | 意味 |
|---|---|
| ✅ 採用 | 受け取った素材をゲームで使っている（切り抜き・縮小・アンカー調整などの加工は含む） |
| 🟡 採用・旧画風 | 使っているが、#57 以降の 3D ミニチュア調とは画風が違う。作り直しの候補 |
| 🔁 代用 | その枠の絵は届いておらず、同じ作物の別の段階の絵から作って埋めている |
| ⚙️ 生成 | スクリプトが作った絵（受け取った絵ではない） |
| ⬜ 仮 | 絵が無く、コードで描いている（または絵文字）。素材が入ればそちらに替わる |
| ➖ 不要 | 仕様上その枠が無い（例：枯れない作物の枯れた絵） |
| 📦 保管 | リポジトリにあるが使っていない |

### 出所

画像に埋め込まれた来歴情報（C2PA）で確かめたもの。2026-10-24 にリポジトリの全画像を調べた結果。

- **Recraft**：C2PA に「Recraft AI」の生成記録がある（受け取った画像はすべてこれ）。
- **Google→Recraft**：C2PA に Google の生成記録（SynthID）と Recraft の記録の両方がある。Google の生成 AI で作った絵を Recraft で書き出したもの。風車の 2 枚だけ。
- アトラス用に加工した `tools/raw_assets/` の絵は加工で C2PA が消えている。出所は原画（`tools/source_art/`）の記録で判断した。#39 の絵（バラ・パプリカ・大根）は原画が無く、`raw_assets/` の絵そのものに Recraft の記録が残っている。
- 確かめ方：`grep -c "Recraft AI" <ファイル>`（1 以上なら Recraft の記録あり）、Google は `SynthID` で同様に。

## 作物

1 行が 1 つの作物。段階の数は `PlantSpecies.growthStages`（既定 4、#39 以降の作物は 3）。
画像の枠の assetId は `plant_<作物>_stage<N>` と `plant_<作物>_withered`。アイコンは持ち物・ショップ・木箱で出る絵（`UiLabels.itemIconRes`、無ければ絵文字）。

| 作物（id） | 解放 | 段階 1 | 段階 2 | 段階 3 | 段階 4 | 枯れ | アイコン | 出所・メモ |
|---|---|---|---|---|---|---|---|---|
| チューリップ（`plant_tulip`） | 最初から | ✅ | ✅ | ✅ | ➖ | ➖ | ⬜ 絵文字 | Recraft（#58）。段階 3 は 6 色（赤・ピンク・黄・白・紫・青）すべて ✅。枯れない作物なので枯れた絵は不要 |
| ひまわり（`plant_sunflower`） | Lv2 | ✅ | ✅ | ✅ | ➖ | ✅ | ⬜ 絵文字 | Recraft（#58） |
| バラ（`plant_rose`） | Lv4 | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | ⬜ 絵文字 | Recraft（#6 ごろの手描き風）。4 段階 |
| トマト（`plant_tomato`） | 最初から | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58） |
| イチゴ（`plant_strawberry`） | 最初から | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58） |
| ニンジン（`plant_carrot`） | Lv2 | ✅ | 🔁 | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58）。段階 2 は届いておらず、段階 3 の葉だけを切り出して作った。段階 3 は葉と根の肩だけに切り出し（#60） |
| ナス（`plant_eggplant`） | Lv3 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58）。段階 1 は 4 本の苗から手前の 1 本を切り出し |
| 小麦（`plant_wheat`） | Lv4 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58） |
| トウモロコシ（`plant_corn`） | Lv5 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58）。段階 1 は 16 本の苗から 3 本、段階 2 は 2 本に左右反転の 1 本を足して 3 本に並べ直した |
| パプリカ（`plant_paprika`） | Lv4 | 🟡 | 🟡 | 🟡 | ➖ | 🟡 | ⬜ 絵文字 | Recraft（#39） |
| 大根（`plant_daikon`） | Lv2 | 🟡 | 🟡 | 🟡 | ➖ | 🟡 | ⬜ 絵文字 | Recraft（#39）。段階 3 は描くときに幅 0.85 マスまで縮める（#60） |
| コスモス（`plant_cosmos`） | 最初から | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ 絵文字 | `ui/render/CropArt.kt` |
| スノードロップ（`plant_snowdrop`） | 最初から | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ 絵文字 | 同上 |
| ミント（`plant_mint`） | 最初から | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ 絵文字 | 同上 |
| スイカ（`plant_watermelon`） | Lv3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ 絵文字 | 同上 |
| ラベンダー（`plant_lavender`） | Lv3 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ 絵文字 | 同上 |
| カボチャ（`plant_pumpkin`） | Lv5 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ 絵文字 | 同上 |
| ポインセチア（`plant_poinsettia`） | Lv5 | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ | ⬜ 絵文字 | 同上 |

- 仮（⬜）の作物に絵を入れるときは、**その作物の段階と枯れを全部そろえて**入れる（一部だけだと `CodeArtTest` が落ちる）。段階の数を 3 に変えるなら `PlantSpecies.growthStages` も直す。
- アイコンの絵は `res/drawable-nodpi/icon_<作物>.png`（256×256、`tools/prepare_drawable_art.py` が原画から作る）を置き、`UiLabels.itemIconRes` に 1 行足す。

**集計（作物の絵の枠 79、アイコン 18）**

| | ✅ 採用 | 🟡 旧画風 | 🔁 代用 | ⬜ 仮 |
|---|---|---|---|---|
| 段階・枯れの枠（79） | 30 | 13 | 1 | 35 |
| アイコン（18） | 6 | — | — | 12 |

## 建物

| 物（id） | 状態 | 出所 | 素材・メモ |
|---|---|---|---|
| ウッドフェンス（`struct_fence`） | ✅＋⬜ | Recraft | `struct_fence` `struct_fence_post`（原画 `item_fence_wood.png` から切り出し）。斜めにつなぐ横木と杭はコード。`struct_fence_rail` は 📦 |
| 井戸（`struct_well`） | ⬜ | — | `drawStructure` |
| 風車（`struct_windmill`） | ✅ | Google→Recraft | 本体 `struct_windmill`・羽根 `struct_windmill_blades`（原画は JPEG。描き込まれた市松模様を抜いた） |
| 小屋（`struct_hut`） | ⬜ | — | `drawStructure` |
| コテージ（`struct_cottage`） | ✅ | Recraft | `struct_cottage`（原画 `building_cottage.png`） |
| ガラスの温室（`floor_greenhouse`） | ✅ | Recraft | `floor_greenhouse`（原画 `building_greenhouse.png`）。中の床は `floor_tilled` |
| 工事現場（シート・足場） | ⬜ | — | コード。建物ごとの絵は無い |

## 地面・装飾

| 物（id） | 状態 | 出所 | 素材・メモ |
|---|---|---|---|
| 荒れ地（何も敷いていないマス） | ✅ | Recraft | `res/drawable/soil_wasteland.png` |
| 崩しかけた地面（荒れ地と畑のあいだ） | ✅ | Recraft | `res/drawable/soil_revived.png`（#61 からこの用途） |
| 耕した土（畑） | ✅ | Recraft | `res/drawable/soil_tilled.png` |
| たい肥入りの畑 | ⬜ | — | `soil_tilled.png` を暗くして、葉とわらの粒をコードで描き足す（#61） |
| 芝生（`ground_grass`） | ✅＋⬜ | Recraft | 育ちきると `tile_grass`（原画 `tile_grass_iso.png`）。育つ途中の苗床と葉の切り抜きはコード（#61）。`tile_grass_dark` は 📦 |
| 小石（`ground_pebble`） | ⬜ | — | `drawPebbles` |
| 野の花（`ground_flowers`） | ⬜ | — | `GardenCanvas.kt` |
| 石畳（`floor_cobblestone`） | ✅ | Recraft | `res/drawable-nodpi/tile_path_cobblestone.png` |
| ウッドデッキ（`floor_wood`） | ⬜ | — | `drawWoodDeck` |
| 小川のかけら／せせらぎ（`tile_river`） | ⚙️ | スクリプト | `res/drawable-nodpi/tile_river_autotile.png`（`tools/build_terrain_assets.py`）。受け取った `tile_river_atlas.png` はつながりが合わず 📦 |
| 池（`deco_pond`） | ✅ | Recraft | `res/drawable/pond.png`。つなぎ目と真ん中の水面は `pond_center.png`（✅ Recraft） |
| 岩（`deco_rock`） | ⬜ | — | `drawDecoration` |
| ベンチ（`deco_bench`） | ⬜ | — | `drawBench` |
| 石灯籠（`deco_lantern`） | ⬜ | — | `drawLantern` |
| スプリンクラー（`deco_sprinkler`） | ⬜ | — | 本体 `drawSprinkler`、水しぶき `SprinklerSpray`。絵を入れるなら `deco_sprinkler` と `deco_sprinkler_spray_0`〜`_7` |
| 最初の木箱 | ⬜ | — | コード |

## ショップのアイコン

ショップ・持ち物・木箱で品物に出す小さな絵。**絵があるのは、上の作物の表でアイコンが ✅ の作物だけ**（いまはトマト・イチゴ・ニンジン・ナス・小麦・トウモロコシ、すべて Recraft）。
ほかの種と、建物・飾りはすべて ⬜ 絵文字（`UiLabels.itemEmoji`）。

## 生き物

すべて ⬜ 仮（`ui/render/CreatureSprites.kt` のコード描画）。絵を入れるときの assetId は `creature_<id>`、接地点は足元（飛ぶものも足元。浮かせる高さはコードの `hoverHeight`）。

| 生き物 | assetId | 状態 |
|---|---|---|
| ちょうちょ | `creature_butterfly` | ⬜ |
| てんとうむし | `creature_ladybug` | ⬜ |
| ことり | `creature_bird` | ⬜ |
| りす | `creature_squirrel` | ⬜ |
| かえる | `creature_frog` | ⬜ |
| とんぼ | `creature_dragonfly` | ⬜ |

図鑑の「いきもの」の章（影絵・ページの絵）も同じ描画を使う。

## 画面・UI

| 物 | 状態 | メモ |
|---|---|---|
| 背景（空・丘・草原・隅の茂み） | ⬜ | `ui/effects/GardenBackdrop.kt` |
| 庭の木枠、影、風の筋、天気（雨・慈雨） | ⬜ | コード。演出として作り込んであり、絵に替える予定は今のところ無い |
| 木のボタン・トースト・図鑑の本・看板 | ⬜ | Compose のコード描画（`ui/components/` ほか） |
| 店の内装（棚・カウンター） | ⬜ | コード。商品は絵文字（上の「ショップのアイコン」） |
| アプリのアイコン（ランチャー） | ⬜ | `res/drawable/ic_launcher_foreground.xml`（鉢植えの単純なベクター）。リリース前に作る |
| 通知の小さなアイコン | ✅ | `res/drawable/ic_stat_garden.xml`（芽の形の単色ベクター。ステータスバー用で単色が必須） |

## 音

すべてゲームで使っている（✅）。**出所は記録が無い**（`ASSETS.md` では「外部で用意されたもの」）。分かれば下の表に書く。

| 種類 | ファイル | 状態 | 出所 |
|---|---|---|---|
| BGM | `bgm_title` `bgm_spring` `bgm_summer` `bgm_autumn` `bgm_winter`（5 曲） | ✅ | 未記録 |
| 効果音 | `se_water` `se_fertilize` `se_plant` `se_harvest` `se_coin` `se_level_up` `se_error` `se_tap`（8 個。`se_tap` はマスのタップには使っていない） | ✅ | 未記録 |

## ストア掲載用

| 物 | 状態 |
|---|---|
| 高解像度アイコン（512×512） | 未作成 |
| フィーチャーグラフィック（1024×500） | 未作成 |
| スクリーンショット | 未作成 |

## 保管（使っていない素材）

| ファイル | 理由 |
|---|---|
| `tools/source_art/tile_grass_default.png` | #53 から未使用（芝生は `tile_grass_iso.png` から作る） |
| `tools/source_art/tile_dirt_plowed.png` | #53 から未使用 |
| `tools/source_art/tile_river_atlas.png` | 16 種のつながり方に合わない並びで、継ぎ目も合わなかった |
| アトラスの `tile_grass_dark` | 残っているが描画に使っていない |
| アトラスの `struct_fence_rail` | 横木はコードで描いている |
