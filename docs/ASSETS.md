# アセット（画像・音）

> 絵と音の**いまの状態**（採用・旧画風・仮・未作成）は、1 枠ずつ `ASSET_LEDGER.md` で管理している。
> このファイルは、素材の置き場所・加工の手順・絵の決まりごとをまとめたもの。
> 生成の設定とプロンプトは未記入の欄がある（[追記してほしいこと](#追記してほしいこと)）。

## いま同梱されているもの

| 置き場所 | 中身 | 作り方 |
|---|---|---|
| `app/src/main/assets/atlas/atlas.png` + `assets_manifest.json` | スプライト 75 枚を 1 枚に詰めたもの（4062×2470、約 12.2MB、メモリ上 約 40MB、1 タイル 512px） | `tools/asset_packer.py` |
| `app/src/main/res/drawable-nodpi/tile_river_autotile.png` | 川。16 種のつながり方 × 4 コマ（512×2048） | `tools/build_terrain_assets.py` が**生成** |
| `app/src/main/res/drawable-nodpi/tile_path_cobblestone.png` | 石畳の床 | 同スクリプトが原画から切り出し |
| `app/src/main/res/drawable/soil_wasteland.png` | 荒れ地（何も敷いていないマス）。ひび割れた明るい土の板（#57 で差し替え） | 受け取ったまま（1024×1024、白背景・透過なし）。上面の位置は `GroundSlab.WASTELAND` |
| `app/src/main/res/drawable/soil_tilled.png` | 耕した土（畝 3 本）の板（#57）。上面を畑の 1 マスに敷く | 受け取ったまま（1024×1024、白背景）。`GroundSlab.TILLED` |
| `app/src/main/res/drawable/soil_revived.png` | 鍬で崩しかけた地面（畝の無い、ほぐれた土）の板（#57。#61 から荒れ地と畑の**あいだの段階**。#57〜#60 はたい肥入りの畑に使っていた） | 受け取ったまま（1024×1024、白背景）。`GroundSlab.REVIVED`、`TerrainTextures.broken` |
| `app/src/main/res/drawable/pond_center.png` | 水面だけの 1 マス（#57）。池どうしのつなぎ目の水と、#61 から四方を池に囲まれた池（池の真ん中）の水面 | 受け取ったまま（1024×1024、白背景）。`GroundSlab.POND_CENTER`、`TerrainTextures.pondWater`。色は池の絵の水にそろえて使う |
| `app/src/main/res/drawable-nodpi/icon_{tomato,strawberry,carrot,eggplant,wheat,corn,mint,cosmos,watermelon,snowdrop}.png` | 持ち物・ショップのアイコン（#57、ミントは #62、コスモスは #63、スイカは #65、スノードロップは #66） | `tools/prepare_drawable_art.py` が原画から切り抜き、256×256 に |
| `app/src/main/res/drawable/pond.png` | 池（`deco_pond`） | `tools/source_art/pond.png` をそのままコピー（1536×768）。上面の位置は `GroundSlab.POND` |
| `app/src/main/res/raw/` | BGM 5 曲（タイトル・春・夏・秋・冬）、効果音 8 個（mp3） | 外部で用意されたもの |

### スプライトの内訳

- バラ（5 枚）：`plant_rose_stage{1..4}.png` と `_withered.png`（手描き風のまま）
- チューリップ（#58、8 枚）：`plant_tulip_stage{1..3}.png` と、3 段階目の色違い `plant_tulip_stage3_{pink,yellow,white,purple,blue}.png`（`plant_tulip_stage3` が赤）。
  **枯れた絵は無い**：チューリップは枯れない（`PlantSpecies.canWither = false`）。株ごとの色は `TileSetDefinition.bloomColourOf`（インスタンス id から 6 色のどれか、ずっと同じ）。
  1・2 段階目は数本の芽・つぼみ、3 段階目は 3 輪。原画は `crop_tulip_stage{1,2}.png` と `crop_tulip_<色>_stage3.png`。
- ひまわり（#58、4 枚）：`plant_sunflower_stage{1..3}.png` と `_withered.png`。1 段階目は 3 本の双葉で、アンカーは 3 本の真ん中（`atlas_overrides.json`）。
- #58 で消した旧版：`plant_tulip_stage4`・`plant_tulip_withered`・`plant_sunflower_stage4`（4 段階の手描き風）。旧 1〜3 段階は同じ名前で上書き。
- 地面と建物（#40、`tools/prepare_building_art.py` で加工）：
  - `tile_grass`・`tile_grass_dark`（芝生。#57 から `tile_grass_iso.png` の上面から作る。#47 から明るさの傾きを取り除き、1 色で並べる。`tile_grass_dark` は残っているが使っていない）、`floor_tilled`（耕した土。#47 から畑はコード描画になり、ガラスの温室の床でだけ使う）
  - `struct_cottage`（コテージ）、`floor_greenhouse`（ガラスの温室）、`struct_windmill`（風車の本体）、`struct_windmill_blades`（風車の羽根）
  - スプリンクラー（`deco_sprinkler`、水しぶき `deco_sprinkler_spray_0`〜`_7`）は**アトラスに入っていない**（#61 で確認）。本体も水しぶきもコード描画。絵を入れればその名前で差し替わる
- 作物 4 種 × 4 枚（#57）：`plant_{corn,eggplant,carrot,wheat}_stage{1..3}.png` と `_withered.png`（3 段階）。トマト・イチゴの `_stage{1..3}` も #57 の絵に差し替え、`_withered` は #58 で届いた絵に差し替え。
  - 受け取ったのは `res/drawable/crop_*.png`（1024×1024、白背景・透過なし）。原画は `tools/source_art/` に移し、`tools/prepare_drawable_art.py` で切り抜いて `raw_assets/` に書く（下の「#57 の絵を入れるとき」）。
  - 段階どうしの株数をそろえた：トウモロコシの 1 段階目は 16 本の苗の一覧だったので 3 本を 3 段階目の 3 本と同じ三角に並べ、2 段階目（2 本）には右の株を左右反転して手前に足して 3 本に。
    ナスの 1 段階目は 4 本の苗だったので手前の 1 本に（2・3 段階目は 1 株）。小麦は 3 段階とも畑一面。
  - 大きさ（タイル幅に対する高さ、`STAGES`）：トウモロコシ 0.36 / 0.7 / 0.9、ナス 0.3 / 0.58 / 0.78、ニンジン 0.38 / 0.48 / 0.62（#60 で 0.75 から）、トマト 0.34 / 0.74 / 0.86、イチゴ 0.3 / 0.48 / 0.58。小麦は 3 段階とも幅 0.92。
  - ニンジンの 2 段階目は届いていない（`crop_carrot_1` と `_3` だけ）。3 段階目の絵から根（橙色）より上を切り出し、「根がまだ土の中」の姿にした。
  - ニンジンの 3 段階目（#60）：根がまるごと土の上に出て、地面に転がっているように見えたので、**葉と根の肩（根の長さの 22%、`CARROT_SHOULDER`）だけ**を残し、下の 10 行を透明へぼかした。
  - ナスの 2・3 段階目と枯れた絵（#60）：茎が見えず、下に実が垂れているため、絵のいちばん下に立てると畑から浮いて見えた。株が地面に着く高さに手でアンカーを置く（`ANCHORS`：2 段階目 0.85、3 段階目 0.78、枯れ 0.83）。
  - 枯れた絵は #57 では最後の緑の段階を茶色くした代用品だったが、#58 で届いた `crop_<作物>_withered.png` に差し替えた（大きさは `WITHERED`）。
- 作物 4 種 × 4 枚（#39）：`plant_{tomato,strawberry,paprika,daikon}_stage{1..3}.png` と `_withered.png`（3 段階）。トマト・イチゴの `_stage{1..3}` は #57 で差し替え済み
  - 受け取ったときは `res/drawable/crop_*.png` に置かれていた。アトラスに詰めるため `tools/raw_assets/` へ移し、`plant_<作物>_…` に改名した。
    大根は受け取ったファイル名が `crop_radish_*`（`radish`）だったので `plant_daikon_*` に、枯れの 1 枚は `rop_radish_withered.png`（先頭の c が欠けていた）だったので直した。
  - どれも 1536×768、背景透明。トマトの絵には切り抜きの名残の白い筋（根のような線）が土の下に残っている。消してはいない。
  - 大根の 3 段階目は葉が横に大きく広がっていて（切り抜き後 1496px＝約 2 マス分）、となりのマスに重なって見える。
- ミント（#62、4 枚）：`plant_mint_stage{1..3}.png` と `_withered.png`（3 段階。#62 で 4 段階から 3 段階にし、コード描画をやめた）。
  原画は `crop_mint_stage{1..3}.png` と `crop_mint_withered.png`（Recraft、白背景）。大きさ（`STAGES`）は低い草として 0.32 / 0.42 / 0.62、枯れ 0.62。
  2 段階目は 0.48 だと 3 段階目より横に広くなり、育って小さく見えたので 0.42 に。3 段階目の色は手を加えていない（明るさはほかの作物と同じくらいで、白っぽい葉で彩度が低いだけ）。
  白背景の絵なので土の山は無い（`crop_soil.SOILLESS` に `mint` を追加。入れないとアトラスを詰めるとき葉が土とみなされて消える）。
- コスモス（#63、4 枚）：`plant_cosmos_stage{1..3}.png` と `_withered.png`（3 段階。#63 で 4 段階から 3 段階にし、コード描画をやめた）。
  原画は `crop_cosmos_stage{1..3}.png` と `crop_cosmos_withered.png`（Recraft、白背景）。大きさ（`STAGES`）は背の高い花として 0.30 / 0.64 / 0.85、枯れ 0.85。
  目安の 0.34 / 0.6 では 1 段階目が 2 段階目より横に広かったので、幅も高さも 1→2→3 の順に大きくなるよう直した（`BundledArtTest` で確認）。
  葉が髪の毛のように細く、白背景が葉の間から透けて、切り抜きの縁だけを戻す方法では白いもやが残った。コスモスだけ絵全体で白との混ざりを戻す（`CUT`、`cut_out` の `throughout` と `solid=100`。桃色の花びらとその周り 2px は不透明のまま）。
  `crop_soil.SOILLESS` に `cosmos` を追加。葉の黄緑は畑の上でも枯れかけには見えなかったので、色は変えていない。
- スノードロップ（#64、4 枚）：`plant_snowdrop_stage{1..3}.png` と `_withered.png`（3 段階。#64 で 4 段階から 3 段階にし、コード描画をやめた）。
  原画は `crop_snowdrop_stage{1..3}.png` と `crop_snowdrop_withered.png`（Recraft、白背景）。大きさ（`STAGES`）は背の低い花として 0.3 / 0.48 / 0.62、枯れ 0.62（枯れは葉が外へ垂れるので段階 3 より横に広い）。
  1 段階目は根元に白いひげ根が出ていたので、葉の束の下端（下から 5 分の 1 のうち、束より 15% 以上広がる最初の行）から下を切り落とした（`without_roots`）。接地点は葉の根元。
  花びらが白く、ふつうの白の切り抜きでは花びらの灰色の陰が背景とみなされ欠けた。花のまわり（絵の上 5 分の 4）だけ、真っ白（246 以上）だけを背景として抜く（`CUT`、`cut_out` の `white_petals`）。茎の間に閉じ込められた真っ白も、広い所だけ抜く。
  アイコン `icon_snowdrop.png` は #64 では枯れた絵の複製が届いたので使わず、#66 で正しい絵に置き直して作った（白い花なので、切り抜きは株と同じ `white_parts`）。
  `crop_soil.SOILLESS` に `snowdrop` を追加。
- スイカ（#65、4 枚）：`plant_watermelon_stage{1..3}.png` と `_withered.png`（3 段階。#65 で 4 段階から 3 段階にし、コード描画をやめた）。
  原画は `crop_watermelon_stage{1..3}.png` と `crop_watermelon_withered.png`（Recraft、白背景）。2・3 段階目と枯れは茎の根元の無い、低く横に広い葉の山（3 段階目は手前下に縞の実）なので、
  大きさは高さでなく横幅で決めた：1 段階目は高さ 0.3（苗の決まり `CropScale` でさらに縮む）、2 段階目は幅 0.7、3 段階目と枯れは幅 0.85（1 マスを超えない。`BundledArtTest` で確認）。
  接地点は `asset_packer.py` が絵の下の方に自動で置く（2 段階目 0.98、3 段階目 0.94、枯れ 1.0）。2 段階目の葉は届いた絵では黒に近い灰緑で、畑の上で焦げたように見えたため、#66 で 3 段階目の葉と同じ明るさまで上げた（`LEAF_GAIN`、全チャンネル同じ倍率 1.3 なので色味は同じ。黄色い花は上げない）。
  `crop_soil.SOILLESS` に `watermelon` を追加。
- フェンス 3 枚：`struct_fence.png`（単体の柵）、`struct_fence_post.png`（支柱）、`struct_fence_rail.png`（現在は未使用）

原画は `tools/raw_assets/`。ファイル名（拡張子なし）がそのまま assetId になる。

### アセットの状態

作物の段階・枯れ・アイコン、建物、装飾、生き物、音の状態は `ASSET_LEDGER.md` に移した（#61 時点の表を 1 枠ずつに細かくしたもの）。

### ショップの取扱一覧（#61 時点）

`domain/model/GardenItem.kt` の全 35 品。並びはショップの棚（種・建物・飾り）ごと。
「確定アセット」は庭に置いたときの絵が受け取った画像素材のもの、「コード描画（仮置き）」は絵が無くプログラムで描いているもの。
アイコンは、作物 10 種（トマト・イチゴ・ニンジン・ナス・小麦・トウモロコシ・ミント・コスモス・スイカ・スノードロップ）だけが画像（`res/drawable-nodpi/icon_*.png`、`itemIconRes`）、ほかは絵文字（`itemEmoji`）。

| 棚 | 品名（id） | 値段 | 解放 | 庭での絵 | 素材 |
|---|---|---|---|---|---|
| 種 | チューリップの種（`seed_tulip`） | 20 | 最初から | 確定アセット | `plant_tulip_*`（#58、6 色） |
| 種 | ひまわりの種（`seed_sunflower`） | 35 | Lv2 | 確定アセット | `plant_sunflower_*`（#58） |
| 種 | バラの苗（`seed_rose`） | 60 | Lv4 | 確定アセット（旧画風） | `plant_rose_*`（4 段階） |
| 種 | コスモスの種（`seed_cosmos`） | 20 | 最初から | 確定アセット | `plant_cosmos_*`（#63、3 段階）、アイコン画像あり |
| 種 | スノードロップの球根（`seed_snowdrop`） | 20 | 最初から | 確定アセット | `plant_snowdrop_*`（#64、3 段階）、アイコン画像あり（#66） |
| 種 | ミントの苗（`seed_mint`） | 15 | 最初から | 確定アセット | `plant_mint_*`（#62、3 段階）、アイコン画像あり |
| 種 | トマトの苗（`seed_tomato`） | 20 | 最初から | 確定アセット | `plant_tomato_*`（#57）、アイコン画像あり |
| 種 | イチゴの苗（`seed_strawberry`） | 20 | 最初から | 確定アセット | `plant_strawberry_*`（#57）、アイコン画像あり |
| 種 | スイカの種（`seed_watermelon`） | 45 | Lv3 | 確定アセット | `plant_watermelon_*`（#65、3 段階）、アイコン画像あり |
| 種 | ラベンダーの苗（`seed_lavender`） | 40 | Lv3 | コード描画（仮置き） | `CropArt.kt` |
| 種 | カボチャの種（`seed_pumpkin`） | 70 | Lv5 | コード描画（仮置き） | `CropArt.kt` |
| 種 | ポインセチアの苗（`seed_poinsettia`） | 70 | Lv5 | コード描画（仮置き） | `CropArt.kt` |
| 種 | パプリカの苗（`seed_paprika`） | 55 | Lv4 | 確定アセット（旧画風） | `plant_paprika_*`（#39） |
| 種 | 大根の種（`seed_daikon`） | 30 | Lv2 | 確定アセット（旧画風） | `plant_daikon_*`（#39） |
| 種 | ニンジンの種（`seed_carrot`） | 25 | Lv2 | 確定アセット | `plant_carrot_*`（#57、2 段階目は 3 段階目から作成）、アイコン画像あり |
| 種 | ナスの苗（`seed_eggplant`） | 40 | Lv3 | 確定アセット | `plant_eggplant_*`（#57）、アイコン画像あり |
| 種 | 小麦の種（`seed_wheat`） | 45 | Lv4 | 確定アセット | `plant_wheat_*`（#57）、アイコン画像あり |
| 種 | トウモロコシの種（`seed_corn`） | 60 | Lv5 | 確定アセット | `plant_corn_*`（#57）、アイコン画像あり |
| 建物 | ウッドフェンス（`struct_fence`） | 15 | 最初から | 確定アセット＋コード | `struct_fence` `struct_fence_post`。斜めにつなぐ横木はコード |
| 建物 | 井戸（`struct_well`） | 80 | Lv3 | コード描画（仮置き） | `drawStructure` |
| 建物 | 風車（`struct_windmill`） | 120 | Lv4 | 確定アセット | `struct_windmill` `struct_windmill_blades` |
| 建物 | 小屋（`struct_hut`） | 150 | Lv5 | コード描画（仮置き） | `drawStructure` |
| 建物 | コテージ（`struct_cottage`） | 300 | Lv6 | 確定アセット | `struct_cottage` |
| 建物 | ガラスの温室（`floor_greenhouse`） | 300 | Lv18 | 確定アセット | `floor_greenhouse`、床は `floor_tilled` |
| 飾り | 芝生（`ground_grass`） | 10 | 最初から | 確定アセット＋コード | 育ちきると `tile_grass`。育つ途中の苗床と葉の切り抜きはコード（#61） |
| 飾り | 小石（`ground_pebble`） | 10 | 最初から | コード描画（仮置き） | `drawPebbles` |
| 飾り | 石畳（`floor_cobblestone`） | 10 | 最初から | 確定アセット | `tile_path_cobblestone.png` |
| 飾り | 小川のかけら（`tile_river`） | 10 | 最初から | 生成アセット | `tile_river_autotile.png`（スクリプトで生成）。庭・図鑑での名前は「せせらぎ」 |
| 飾り | 野の花（`ground_flowers`） | 15 | 最初から | コード描画（仮置き） | `GardenCanvas.kt` |
| 飾り | ウッドデッキ（`floor_wood`） | 12 | 図鑑 2 ページ | コード描画（仮置き） | `drawWoodDeck` |
| 飾り | 岩（`deco_rock`） | 30 | Lv2 | コード描画（仮置き） | `drawDecoration` |
| 飾り | ベンチ（`deco_bench`） | 40 | Lv2 | コード描画（仮置き） | `drawBench` |
| 飾り | 石灯籠（`deco_lantern`） | 60 | 図鑑 4 ページ | コード描画（仮置き） | `drawLantern` |
| 飾り | スプリンクラー（`deco_sprinkler`） | 70 | Lv3 | コード描画（仮置き） | `drawSprinkler`、水しぶきも `SprinklerSpray` のコード |
| 飾り | 池（`deco_pond`） | 75 | Lv4 | 確定アセット | `pond.png`、つなぎ目と真ん中は `pond_center.png` |

集計：確定アセット 22（うちコード併用 2、旧画風 3）、生成アセット 1、コード描画（仮置き） 12。
ショップに出ないもの：耕した土（鍬で作る。`soil_tilled.png`）、崩しかけた地面（`soil_revived.png`）、最初の木箱（コード描画）。
品を足したり絵を入れたりしたら、この表と `GardenItem.kt` 冒頭の一覧と `ASSET_LEDGER.md` を直すこと。

### 画像が無く、プログラムで描いているもの

岩、小石、野の花、ウッドデッキ、ベンチ、石どうろう、井戸、小屋、スプリンクラー（本体と水しぶき）、
芝生の育つ途中の苗床、たい肥の粒、フェンスの横木と杭、工事現場（シート・足場）、木枠、背景（空・丘・茂み）、影、風の筋。
すべて `ui/components/GardenCanvas.kt` と `ui/effects/GardenBackdrop.kt` にある。
絵を用意できれば、同じ assetId でアトラスに入れるだけで差し替わる（スプライトがあればそちらを優先して描く）。

絵が無く、仮の絵を描いている作物と生き物の assetId:

- 作物（ラベンダー・カボチャ・ポインセチア）：`<objectId>_stage1`〜`_stage<N>` と `<objectId>_withered`
  （N は `PlantSpecies.growthStages`、既定 4）。**全部そろえて入れる**こと。一部だけだと `CodeArtTest` が落ちる。仮の絵は `ui/render/CropArt.kt`（絵が入ったら、その作物の `CROP_DRAWINGS` の行と描画関数を消してよい）
- 名前の決まりは `TileSetDefinition`（`spriteIdsFor` `assetIdFor` `expectedSpriteIds`）。アトラスに入れた名前が一覧に無いと `CodeArtTest` が落ちる
- 生き物：`creature_<id>`（`creature_butterfly` `creature_ladybug` `creature_bird` `creature_squirrel` `creature_frog` `creature_dragonfly`）。接地点は足元。
  飛ぶものも足元を接地点にする（浮かせる高さはコードの `hoverHeight`）。仮の絵は `ui/render/CreatureSprites.kt`

## 原画（`tools/source_art/`）

ユーザーから受け取った元画像。アプリには入らない。

| ファイル | 状態 |
|---|---|
| `item_fence_wood.png` | 正面向きのパーツ集。柵・支柱・横木を切り出して使用 |
| `tile_path_cobblestone.png` | 石畳。そのまま使用 |
| `building_cottage.png` | コテージ（#40）。透過はきれいだった。切り抜いて 3 マス分の幅で描く（描画倍率 約 2.5）。土台が 2:1 より平たい。#41 では土台の左右の角を 3×3 の左右の角にそろえていた（anchorY 0.7975）が、前の列が空いて奥に寄って見えたので、#55 から他の建物と同じく左右の角と前の角のずれを半分ずつにする（`ground_centre_y`、anchorY 0.7481、約 0.2 マス手前へ） |
| `building_greenhouse.png` | ガラスの温室（#40）。ガラスは半透明。土台が 1 マスになるよう縮小 |
| `building_windmill_body.jpg` | 風車の本体（#40）。**JPEG で透過が無く、灰色の市松模様（48px、2 トーン）が背景として描き込まれていた**。マスごとの色で抜いた |
| `building_windmill_blades.jpg` | 風車の羽根（#40）。同じく白と灰色の市松模様（約 12px）と、白いにじみが描き込まれていた。色で抜いた |
| `tile_grass_iso.png` | 芝生（#57、受け取ったときは `res/drawable/tile_grass.png`）。白背景のアイソメの芝の板（1024×1024）。`prepare_drawable_art.flat_lawn` が上面を真上から見た正方形（720 px）に戻し、`flat_to_tile` で中央の正方形の半分（`share=0.5`）を 2:1 のひし形へ貼り、ぼかし 1.2px、厚み 55px の側面を足して `tile_grass` に。細かい葉のままだとスマホで約 7 分の 1 に縮んだとき砂嵐のようにちらつくため、葉を大きく柔らかくしている。#53 の `tile_lawn_flat.png` はこれに置き換えて削除 |
| `crop_*.png`（51 枚） | 作物の原画（#57・#58・#62〜#65）。トウモロコシ・ナス・小麦・ひまわり・ミント・コスモス・スノードロップ・スイカは `_stage1`〜`3` と `_withered`、トマト・イチゴは `_1`〜`3` と `_withered`、ニンジンは `_1`・`_3`・`_withered`、チューリップは `_stage1`・`_stage2` と 6 色の `_<色>_stage3` | `prepare_drawable_art.py` が切り抜いて `raw_assets/plant_*` に |
| `icon_*.png`（10 枚） | アイコンの原画（#57、ミントは #62、コスモスは #63、スイカは #65）。`icon_snowdrop.png` は #64 では枯れた絵の複製だったが、#66 で正しい絵に置き直して使っている | `prepare_drawable_art.py` が切り抜いて `res/drawable-nodpi/` に |
| `tile_soil_flat.png` | 耕した土（#53）。同じく真上からの写真（受け取ったときは `floor_tilled.png`）。正方形全体をひし形へ、厚み 33px で `floor_tilled` に（温室の床でだけ使う） |
| `pond.png` | 池（#53）。透過ありのアイソメの絵（丸太の枠と石）。#54 から `res/drawable/pond.png` にコピーして池（`deco_pond`）に使用。`raw_assets/` に置くと詰められて `CodeArtTest` が落ちるので、原画はここに置いてある |
| `tile_grass_default.png` | **#53 から未使用**。芝生のタイル（#40）。縁に白いにじみ。1 マス幅に縮め、上面が 2:1 のひし形になるよう縦に伸ばした（元は約 2.35:1） |
| `tile_dirt_plowed.png` | **#53 から未使用**。耕した土のタイル（#40）。縁に白いにじみと、右上に青い点（ゴミ）。同様に加工（元の上面は約 2.7:1） |
| `tile_river_atlas.png` | **未使用**。4×4 に並んでいるが、16 種のつながり方に対応した並びではなく、隣と継ぎ目が合わず、背景も不透明だった。色だけ参考にして、川は生成している |

## 加工の手順

Python 3.12（Pillow、numpy）。パスは `AI_WORKFLOW.md` を参照。

```bash
# 川・石畳・フェンスの部品を作り直す
python tools/build_terrain_assets.py
```

```bash
# スプライトをアトラスに詰め直す（この引数の組み合わせを変えないこと）
python tools/asset_packer.py tools/raw_assets app/src/main/assets/atlas --tile-width 512 --source-tile-width 720 --alpha-threshold 8 --overrides tools/atlas_overrides.json --max-width 4096 --strip-crop-soil
```

- 原画は **1 タイル 720px** の縮尺で描かれている。アトラスは 512px に縮めて軽くしている。
- `--max-width 4096`（#40 から）：アトラスの縦横どちらも 4096px 以内に収めるため。幅 2048 のままだと縦が 4733px になり、
  4096px までしか扱えない端末で描けなくなるおそれがあった（`BuildingArtTest` が 4096 以内を見ている）。
- `atlas_overrides.json` の追加の鍵（#40）：`scale`（描画倍率。アトラスでは拡大せず、描くときに大きくする）と
  `attachX`/`attachY`（別のスプライトを取り付ける点。風車の羽根の軸）。マニフェストには `drawScale`・`attachX`・`attachY` として出る（使うときだけ）。

### #57 の絵（白背景の作物・アイコン・芝生）を入れるとき

```bash
python tools/prepare_drawable_art.py --preview <確認用フォルダ>
python tools/prepare_building_art.py
python tools/asset_packer.py tools/raw_assets app/src/main/assets/atlas --tile-width 512 --source-tile-width 720 --alpha-threshold 8 --overrides tools/atlas_overrides.json --max-width 4096 --strip-crop-soil
```

- 白の切り抜き（`cut_out`）：絵の外周につながる白と薄い灰色（影）を消し、葉に囲まれた大きな真っ白のすき間も消す。輪郭の画素は白との混ざりを戻して白いふちを残さない。
  トウモロコシの苗の一覧だけは影が濃いので、灰色を深めに消す。どの絵からも、本体から離れた点は消す。
- `--preview` は作物ごとに段階を並べた絵（実際の大きさの比）と、アイコンの一覧、真上から見た芝生を出す。
- 地面の板 4 枚（荒れ地・耕した土・崩しかけた地面・水面）の上面の位置を表示する。`GroundSlab` の値はこれ。絵を差し替えたらこの値も直し、荒れ地は `FlatGround.FIELD_VERSION` を上げる（作った地面のキャッシュを捨てるため）。
- 1 回 1 分ほど。

### 受け取った地面・建物の絵を入れるとき（#40）

```bash
python tools/prepare_building_art.py --preview <確認用フォルダ>
```

- `tools/source_art/` の原画から `tools/raw_assets/` の 7 枚を作り、`atlas_overrides.json` のアンカー・`scale`・`attachX/Y` を書き換える。そのあと上の `asset_packer.py` を回す。
- していること：JPEG の描き込み市松模様の除去（灰色で、マスの色に一致し、外側か大きな隙間につながる部分）、
  縁の白いにじみの除去、本体から離れた小さな点の除去、タイルを 1 マス幅・上面 2:1 に、建物を土台が足場（1・2・3 マス）の幅になるよう縮小または描画倍率で拡大。
- 接地点：タイルは上面の中心（左右の角の中点）。建物は土台の左右の角（いちばん外側の点の下端）の中点。
- 風車の羽根の軸は、羽根の中央の暗い木の輪の中心。本体側の取り付け点は原画の (855, 252)（破風の小さな輪の中心）を手で読んだもの。
- 羽根は本体の土台と同じくらいの幅に縮めている（大きさは好みで調整可。`prepare_windmill` の `body_width / span`）。
- `--preview` は各スプライトを派手な縞の上に置き、接地点（赤）と取り付け点（青）を打った絵を出す。透過の抜け残りはこれで見る。
- `--alpha-threshold 8`：ごく薄い縁を余白として切り落とす。
- `tools/atlas_overrides.json`：スプライトごとの**接地点**（アンカー）。既定は下端中央。
  作物（`plant_*`）は #47 から、`--strip-crop-soil` で**絵の下の土の山を取り除き**、アンカーを茎の根元（絵のいちばん下）に自動で置く。
  `atlas_overrides.json` には作物の項目を書かない（書くと自動のアンカーを上書きしてしまう）。例外は #57 の小麦 4 枚（畑の中心に立てる）とトマトの 3 段階目（主茎）で、`prepare_drawable_art.py` が書く。取り除き方は `tools/crop_soil.py` の説明。
  #57 の白背景の絵には土の山が無いので取り除かない（`crop_soil.SOILLESS`）。
  確かめるときは `python tools/crop_soil.py tools/raw_assets preview.png plant_tulip_stage4 ...` で前後を並べた画像が出る。
  枯れた株は株自体が土色なので、暗い所だけを土の山とみなす（薄い砂色の山は横幅で判断）。枯れたバラ・ひまわりは茎のいちばん下が少し削れる。
  #55 から、下から数えた帯で取りきれない土の山（イチゴの苗のように、下の葉が山より下に垂れている絵）も取る（`_strip_clumps`）：絵の上から 35% より下で、
  赤≧緑・平均して青より赤い（茶色い）土色のかたまりが、絵の画素の 1% 以上・横幅の 15% 以上あれば消し、それにつながるほぼ黒い土も消す。灰緑の葉（バラなど）は赤≧緑の条件で残る。枯れた株には使わない。
- 作物のアンカーの目安は `python tools/estimate_crop_anchors.py ...` で出せた（#39。土の山を取り除いた #47 からは使わない）。
  土の山の底辺を、横幅が縦の 2 倍の楕円とみなし、いちばん手前（いちばん下の不透明な点）から横幅の 1/4 上を接地点にする。白っぽい点（切り抜きの名残）は数えない。
  手で合わせた既存の 15 枚とは ±0.03 前後で一致した。#39 の 16 枚はこの値をそのまま使っている（エミュレータでの見た目は未確認）。

### 新しいスプライトを入れるとき

1. 背景が透明な PNG を `tools/raw_assets/<assetId>.png` として置く。
2. 必要なら `atlas_overrides.json` にアンカーを足す。
3. `asset_packer.py` を上の引数で実行する。
4. `BundledArtTest` と `AssetPipelineTest` を回す。
5. エミュレータで接地と大きさを見る。

## 絵の決まりごと（実装から分かっている範囲）

- **アイソメトリック**。タイルは横 2：縦 1 のひし形。
- スプライトは**正面向きで直立**。斜めに傾けた絵や、変形して使う前提の絵は合わない。
- 作物は成長 4 段階（#39 からの作物は 3 段階）＋枯れ 1 枚。段階ごとに大きさが変わってよい（アンカーで合わせる）。
- 1 マスに何株も描いた作物は、全段階で株数をそろえる（#57。苗だけ何十本もあると、育つと減って見える）。
- 川の画像は「マスクの値 `N=1, E=2, S=4, W=8` → `mask % 4` 列・`mask / 4` 行」の 4×4 並び、
  コマは縦に積む。手描きに差し替える場合もこの並びが必要。さらに現在は**マスごとに 1 コマずらして再生**
  しているので、流れの継ぎ目を合わせるには `ARCHITECTURE.md` の「川」の条件を満たす必要がある。
- フェンスを斜め方向に繋ぐための絵は無い。横木はプログラム描画で代用している。

## 音

- `bgm_title.mp3`・`bgm_summer.mp3`（#36 で追加）、`bgm_autumn.mp3`・`bgm_winter.mp3`（#37 で追加）、`bgm_spring.mp3`（#35 で `bgm_garden.mp3` から改名）、`se_{water,fertilize,plant,harvest,coin,level_up,error,tap}.mp3`。
- BGM の名前は `bgm_title` `bgm_spring` `bgm_summer` `bgm_autumn` `bgm_winter` の 5 つ（`BgmTrack`）。5 曲ともそろっている（#37）。どれかが欠けたときは春の曲で鳴る。
  ループ再生なので、曲の頭と終わりが継ぎ目なくつながるように書き出すこと。音量は春の曲に揃える（`tools/measure_audio.py`）。
  実測（#37、`tools/measure_audio.py`。すべて 48kHz ステレオの mp3）：

  | 曲 | 長さ | ピーク | 全体の RMS | いちばん大きい 400ms |
  |---|---|---|---|---|
  | タイトル `bgm_title` | 3:08 | -0.9 | -17.1 | -13.2 |
  | 春 `bgm_spring` | 3:05 | -0.1 | -17.9 | -14.6 |
  | 夏 `bgm_summer` | 3:07 | -0.2 | -16.8 | -13.5 |
  | 秋 `bgm_autumn` | 2:40 | -0.1 | -16.9 | -13.6 |
  | 冬 `bgm_winter` | 2:45 | -0.4 | -16.9 | -13.3 |

  （単位は dBFS。）全体の RMS の差は最大 1.1 dB（春がいちばん静か）で、曲ごとの音量補正はしていない。秋・冬はほかより 20〜30 秒短いが、ループ再生なので問題ない。
- 名前で実行時に探す（`AudioManager`）。無くても落ちない。`res/raw/keep.xml` で削除を防いでいる。
- 効果音は実測の音量差をもとに個別のゲインを掛けている（`tools/measure_audio.py`）。
- `se_tap` はマスのタップ音としては使っていない（うるさいため外した）。

## 追記してほしいこと

以下は記録が無いため空欄です。分かる方が埋めてください。

### 生成ツール

| ツール | 用途 | 設定・モデル・出力サイズ |
|---|---|---|
| Recraft AI | 受け取った画像はすべて Recraft から書き出したもの（2026-10-24 に全画像の C2PA の来歴情報で確認。`ASSET_LEDGER.md` の「出所」） | 未記入 |
| Google の生成 AI | 風車の本体と羽根（`building_windmill_*.jpg`）だけ、C2PA に Google の生成記録（SynthID）もある。Google で作り Recraft で書き出したもの | 未記入 |
| その他 | 音（BGM・効果音）の出所は記録が無い | 未記入 |

### プロンプトとデザイン方針

- 作物（チューリップ・ひまわり・バラ）のプロンプト：未記入
- タイル（川・石畳・フェンス）のプロンプト：未記入
- 共通の画風指定（線、塗り、パレット、視点）：未記入
- 背景透過や余白の取り方などの後処理：未記入
