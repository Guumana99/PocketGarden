# アセット（画像・音）

> 絵と音の**いまの状態**（採用・旧画風・仮・未作成）は、1 枠ずつ `ASSET_LEDGER.md` で管理している。
> このファイルは、素材の置き場所・加工の手順・絵の決まりごとをまとめたもの。
> 生成の設定とプロンプトは未記入の欄がある（[追記してほしいこと](#追記してほしいこと)）。

## いま同梱されているもの

| 置き場所 | 中身 | 作り方 |
|---|---|---|
| `app/src/main/assets/atlas/{crops,world}_<n>.png` + `assets_manifest.json` | スプライト 87 枚を 1 辺 2048px 以内のページに詰めたもの（#73）。作物（`plant_*`）は `crops_0`〜`2`（2035×2045・1952×2023・1925×467）、それ以外（建物・地面・柵）は `world_0`（1820×966）。合計 約 13.8MB、メモリ上 約 43MB、1 タイル 512px | `tools/asset_packer.py` |
| `app/src/main/assets/atlas/creatures_0.png` + `creatures_manifest.json` | 生き物 6 種の絵 16 枚（#71。1009×459、約 480KB、1 タイル 960px）。作物のアトラスとは別（形は同じ。#73 で `creatures.png` から改名） | `tools/prepare_creature_art.py`（中で `asset_packer.py` を呼ぶ） |
| `app/src/main/res/drawable-nodpi/{water_surface,water_ripple_a,water_ripple_b}.png` | 水面（繰り返せる 1 枚）と、その細かい波 2 枚（白＋透明）。川と池で共通。原画 `water_surface.png` | `tools/prepare_water_art.py` |
| `app/src/main/res/drawable-nodpi/{river_bank,river_bank_concave,river_bank_convex}.png` | 川の岸。まっすぐな帯（1 マスの辺に 1 枚）と、帯を曲げて作った角 2 種。原画 `river_bank.png` | `tools/prepare_water_art.py` |
| `app/src/main/res/drawable-nodpi/{floor_cobblestone,floor_wood,ground_pebble,ground_flowers}.png` | 石畳・ウッドデッキ・小石・野の花（#74）。真上から見た 1 マス分の模様（512×512）で、並べると隣のマスへ続く。庭は 1 マスに 1 枚敷く（`drawGroundTexture`） | `tools/prepare_drawable_art.py`（`GROUND`）が板の原画の上面から作る |
| `app/src/main/res/drawable-nodpi/icon_{struct_hut,struct_well,deco_rock,deco_bench,deco_lantern,deco_sprinkler,ground_pebble,ground_flowers,floor_wood,floor_cobblestone,ground_grass,struct_cottage,floor_greenhouse,struct_fence,deco_pond,struct_windmill,tile_river}.png` | 建物・飾り・地面の品物のアイコン（#74、残りの 7 つは #77。256×256） | `tools/prepare_drawable_art.py`（`ITEM_ICONS` は原画から、`CUT_ICONS` は切り抜き済みの絵から、風車は `windmill_icon`、小川は `river_icon`）。地面は斜め上から見た板の形のまま |
| `app/src/main/res/drawable/soil_wasteland.png` | 荒れ地（何も敷いていないマス）。ひび割れた明るい土の板（#57 で差し替え） | 受け取ったまま（1024×1024、白背景・透過なし）。上面の位置は `GroundSlab.WASTELAND` |
| `app/src/main/res/drawable/soil_tilled.png` | 耕した土（畝 3 本）の板（#57）。上面を畑の 1 マスに敷く | 受け取ったまま（1024×1024、白背景）。`GroundSlab.TILLED` |
| `app/src/main/res/drawable/soil_revived.png` | 耕した土（#78。畝の無い、ほぐれた土）の板（#57。#61 から荒れ地と畑の**あいだの段階**。#57〜#60 はたい肥入りの畑に使っていた） | 受け取ったまま（1024×1024、白背景）。`GroundSlab.REVIVED`、`TerrainTextures.broken` |
| `app/src/main/res/drawable-nodpi/icon_<作物>.png`（作物 20 種すべて） | 持ち物・ショップのアイコン（#57、ミントは #62、コスモスは #63、スイカは #65、スノードロップは #66、ラベンダーは #67、しいたけは #68、ポインセチアは #69、チューリップ・ひまわり・バラ・パプリカ・大根は #72） | `tools/prepare_drawable_art.py` が原画から切り抜き、256×256 に |
| `app/src/main/res/drawable/pond.png` | 池（`deco_pond`） | `tools/source_art/pond.png` をそのままコピー（1536×768）。上面の位置は `GroundSlab.POND` |
| `app/src/main/res/raw/` | BGM 5 曲（タイトル・春・夏・秋・冬）、効果音 8 個（mp3） | 外部で用意されたもの |

### スプライトの内訳

- バラ（5 枚）：`plant_rose_stage{1..4}.png` と `_withered.png`（手描き風のまま）
- チューリップ（#58、8 枚）：`plant_tulip_stage{1..3}.png` と、3 段階目の色違い `plant_tulip_stage3_{pink,yellow,white,purple,blue}.png`（`plant_tulip_stage3` が赤）。
  **枯れた絵は無い**：チューリップは枯れない（`PlantSpecies.canWither = false`）。株ごとの色は咲いたときに土の質で決まる（`BloomColours`、#79）。
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
- ラベンダー（#67、4 枚）：`plant_lavender_stage{1..3}.png` と `_withered.png`（3 段階。#67 で 4 段階から 3 段階にし、コード描画をやめた）。
  原画は `crop_lavender_stage{1..3}.png` と `crop_lavender_withered.png`（Recraft、白背景）。大きさ（`STAGES`）は丸くこんもりした株として 0.3 / 0.5 / 0.66、枯れ 0.66（目安のままで幅・高さとも 1→2→3 の順に大きい）。
  葉が細かく、コスモスと同じく白いもやが残ったので、絵全体で白との混ざりを戻す（`CUT`、`throughout`・`solid=100`。紫の花穂は桃色と同じく不透明のまま）。
  アイコンは花穂の束に白っぽい麻ひものリボン。ふつうの切り抜きではひもが途切れたので、アイコンだけ絵全体で真っ白だけを背景として抜く（`ICON_CUT`、`white_parts=1.0`。#64 の `white_petals` を、効かせる範囲を指定できる `white_parts` にした。スノードロップは 0.8 で結果は同じ）。
  `crop_soil.SOILLESS` に `lavender` を追加。
- しいたけ（#68、4 枚）：`plant_shiitake_stage{1..3}.png` と `_withered.png`（3 段階。#68 でカボチャと入れ替えた新しい作物）。原画は `crop_shiitake_stage{1..3}.png` と `crop_shiitake_withered.png`（Recraft、白背景）。
  丸太は育たず、キノコだけが増えるので、どの絵も**丸太の長さ**で大きさを決める（`STAGES` の `("log", 0.5)`、`log_width`：絵の下 4 分の 1 でいちばん広い行）。丸太は全段階・枯れとも 0.5 マス（はじめ 0.85 にしたが、同じ列に並べると前の原木が後ろの原木を半分ほど隠したので 0.6 に、#77 で 0.5 に縮めた）。
  接地点は丸太の下端の真ん中（`log_anchor`）。`CropScale.LOGS` に入れ、苗の大きさの決まりや次の段階の高さで縮めない（縮めると丸太が伸び縮みする）。
  枯れの丸太は樹皮が白っぽい灰色で、ふつうの切り抜きでは樹皮の上の縁が背景とみなされて欠けたので、`white_parts=0.8`（スノードロップと同じ）で切り抜く。`crop_soil.SOILLESS` に `shiitake` を追加。仮の絵（`CropArt.kt`）は描かない。
- ポインセチア（#69、4 枚）：`plant_poinsettia_stage{1..3}.png` と `_withered.png`（3 段階。#69 で 4 段階から 3 段階にし、コード描画をやめた。これで作物の絵はすべてそろい、作物の仮の絵の仕組み `ui/render/CropArt.kt` は削除した）。
  原画は `crop_poinsettia_stage{1..3}.png` と `crop_poinsettia_withered.png`（Recraft、白背景）。大きさ（`STAGES`）は丸い株として 0.3 / 0.55 / 0.7、枯れ 0.7（目安のままで幅・高さとも 1→2→3 の順に大きい）。
  段階 2 の苞はピンク、段階 3 は鮮やかな赤で、色は届いた絵のまま。切り抜きはふつうの設定のまま。`crop_soil.SOILLESS` に `poinsettia` を追加。
- 柵 3 種と、取り下げ中の縁石（#85）：`<id>.png`（1 本のとき）、`<id>_post.png`（杭）、`<id>_rail.png`（杭から次の杭まで）。`<id>` は `struct_fence` `struct_fence_white` `struct_fence_bamboo`。縁石 `struct_edging_brick` に杭は無い。作り方は「柵と縁石の絵を入れるとき」

原画は `tools/raw_assets/`。ファイル名（拡張子なし）がそのまま assetId になる。

### アセットの状態

作物の段階・枯れ・アイコン、建物、装飾、生き物、音の状態は `ASSET_LEDGER.md` に移した（#61 時点の表を 1 枠ずつに細かくしたもの）。

### ショップの取扱一覧（プログレッション改定時点）

`domain/model/GardenItem.kt` の全 46 品（うち縁石 1 品は取り下げ中で、ショップに出していない）。並びはショップの棚（種・建物・飾り）ごと。
「確定アセット」は庭に置いたときの絵が受け取った画像素材のもの、「コード描画（仮置き）」は絵が無くプログラムで描いているもの。
アイコンは、ショップに出している品すべてが画像（`res/drawable-nodpi/icon_*.png`、`itemIconRes`。作物は #72、建物・飾り・地面は #74 と #77）。絵文字（`itemEmoji`）は絵が無いときの予備で、いまはショップに出していない白菜・菜の花・風見鶏だけ。

| 棚 | 品名（id） | 値段 | 解放 | 庭での絵 | 素材 |
|---|---|---|---|---|---|
| 種 | チューリップの種（`seed_tulip`） | 10 | 最初から | 確定アセット | `plant_tulip_*`（#58、6 色）、アイコン画像あり（#72） |
| 種 | ひまわりの種（`seed_sunflower`） | 20 | Lv3 | 確定アセット | `plant_sunflower_*`（#58）、アイコン画像あり（#72） |
| 種 | バラの苗（`seed_rose`） | 40 | Lv8 | 確定アセット（旧画風） | `plant_rose_*`（4 段階）、アイコン画像あり（#72） |
| 種 | コスモスの種（`seed_cosmos`） | 10 | 最初から | 確定アセット | `plant_cosmos_*`（#63、3 段階）、アイコン画像あり |
| 種 | スノードロップの球根（`seed_snowdrop`） | 10 | 最初から | 確定アセット | `plant_snowdrop_*`（#64、3 段階）、アイコン画像あり（#66） |
| 種 | ミントの苗（`seed_mint`） | 5 | 最初から | 確定アセット | `plant_mint_*`（#62、3 段階）、アイコン画像あり |
| 種 | トマトの苗（`seed_tomato`） | 10 | 最初から | 確定アセット | `plant_tomato_*`（#57）、アイコン画像あり |
| 種 | イチゴの苗（`seed_strawberry`） | 5 | 最初から | 確定アセット | `plant_strawberry_*`（#57）、アイコン画像あり |
| 種 | スイカの種（`seed_watermelon`） | 40 | Lv9 | 確定アセット | `plant_watermelon_*`（#65、3 段階）、アイコン画像あり |
| 種 | ラベンダーの苗（`seed_lavender`） | 20 | Lv5 | 確定アセット | `plant_lavender_*`（#67、3 段階）、アイコン画像あり |
| 種 | しいたけの原木（`seed_shiitake`） | 45 | Lv14 | 確定アセット | `plant_shiitake_*`（#68、3 段階。カボチャと入れ替え）、アイコン画像あり |
| 種 | ポインセチアの苗（`seed_poinsettia`） | 70 | Lv15 | 確定アセット | `plant_poinsettia_*`（#69、3 段階）、アイコン画像あり |
| 種 | パプリカの苗（`seed_paprika`） | 40 | Lv11 | 確定アセット（旧画風） | `plant_paprika_*`（#39）、アイコン画像あり（#72） |
| 種 | 大根の種（`seed_daikon`） | 20 | Lv4 | 確定アセット（旧画風） | `plant_daikon_*`（#39）、アイコン画像あり（#72） |
| 種 | ニンジンの種（`seed_carrot`） | 20 | Lv2 | 確定アセット | `plant_carrot_*`（#57、2 段階目は 3 段階目から作成）、アイコン画像あり |
| 種 | ナスの苗（`seed_eggplant`） | 15 | Lv6 | 確定アセット | `plant_eggplant_*`（#57）、アイコン画像あり |
| 種 | 小麦の種（`seed_wheat`） | 35 | Lv7 | 確定アセット | `plant_wheat_*`（#57）、アイコン画像あり |
| 種 | 白菜の種（`seed_hakusai`） | 40 | Lv10 | 確定アセット | `plant_hakusai_*`（#87）、アイコン画像あり |
| 種 | 菜の花の種（`seed_nanohana`） | 45 | Lv12 | 確定アセット | `plant_nanohana_*`（#87）、アイコン画像あり |
| 種 | トウモロコシの種（`seed_corn`） | 65 | Lv13 | 確定アセット | `plant_corn_*`（#57）、アイコン画像あり |
| 建物 | 木の柵（`struct_fence`） | 15 | 最初から | 確定アセット | `struct_fence` `_post` `_rail`（#85）、アイコン画像あり |
| 建物 | 白い柵（`struct_fence_white`） | 25 | Lv5 | 確定アセット | `struct_fence_white` `_post` `_rail`（#85）、アイコン画像あり。ショップでは「柵」の一覧から |
| 建物 | 竹垣（`struct_fence_bamboo`） | 35 | Lv8 | 確定アセット | `struct_fence_bamboo` `_post` `_rail`（#85）、アイコン画像あり。ショップでは「柵」の一覧から |
| 建物 | レンガの縁石（`struct_edging_brick`） | 60 | Lv16 | 確定アセット | **取り下げ（#86）**。ショップに出していない（`inShop = false`）。絵とコードは残してある |
| 建物 | 井戸（`struct_well`） | 400 | Lv5 | 確定アセット | `struct_well`（#74、1×1）、アイコン画像あり |
| 建物 | 風車（`struct_windmill`） | 1200 | Lv9 | 確定アセット | `struct_windmill` `struct_windmill_blades` |
| 建物 | 小屋（`struct_hut`） | 2000 | Lv11 | 確定アセット | `struct_hut`（#74）、アイコン画像あり |
| 建物 | コテージ（`struct_cottage`） | 800 | Lv3 | 確定アセット | `struct_cottage` |
| 建物 | 太鼓橋（`struct_bridge_taiko`） | 3500 | Lv17 | 確定アセット | `struct_bridge_taiko`（原画 `bridge_taiko.png`）、アイコン画像あり |
| 建物 | ガラスの温室（`floor_greenhouse`） | 3000 | Lv18 | 確定アセット | `floor_greenhouse`、床は `floor_tilled` |
| 飾り | 芝生（`ground_grass`） | 10 | 最初から | 確定アセット＋コード | 育ちきると `tile_grass`。育つ途中の苗床と葉の切り抜きはコード（#61） |
| 飾り | 小石（`ground_pebble`） | 10 | 最初から | 確定アセット | `ground_pebble.png`（#74）、アイコン画像あり |
| 飾り | 石畳（`floor_cobblestone`） | 10 | 最初から | 確定アセット | `floor_cobblestone.png`（#74 で差し替え）、アイコン画像あり |
| 飾り | 小川のかけら（`tile_river`） | 10 | 最初から | 生成アセット | `tile_river_autotile.png`（スクリプトで生成）。庭・図鑑での名前は「せせらぎ」 |
| 飾り | 野の花（`ground_flowers`） | 15 | 最初から | 確定アセット | `ground_flowers.png`（#74）、アイコン画像あり |
| 飾り | ウッドデッキ（`floor_wood`） | 12 | 図鑑 2 ページ | 確定アセット | `floor_wood.png`（#74）、アイコン画像あり |
| 飾り | 丸い岩（`deco_rock`） | 60 | Lv2 | 確定アセット | `deco_rock`（#74）、アイコン画像あり。ショップでは「石」の 1 つにまとめて、押すと 5 種の一覧が出る |
| 飾り | 平たい石（`deco_rock_flat`） | 80 | Lv4 | 確定アセット | `deco_rock_flat`（原画 `rock_flat.png`）、アイコン画像あり |
| 飾り | 苔むした岩（`deco_rock_mossy`） | 150 | Lv7 | 確定アセット | `deco_rock_mossy`（原画 `rock_mossy.png`）、アイコン画像あり |
| 飾り | 立て石（`deco_rock_standing`） | 250 | Lv10 | 確定アセット | `deco_rock_standing`（原画 `rock_standing.png`）、アイコン画像あり |
| 飾り | 小石の山（`deco_rock_pebbles`） | 300 | Lv13 | 確定アセット | `deco_rock_pebbles`（原画 `rock_pebbles.png`）、アイコン画像あり |
| 飾り | ベンチ（`deco_bench`） | 100 | Lv3 | 確定アセット | `deco_bench`（#74）、アイコン画像あり |
| 飾り | 石灯籠（`deco_lantern`） | 60 | 図鑑 4 ページ | 確定アセット | `deco_lantern`（#74）、アイコン画像あり |
| 飾り | スプリンクラー（`deco_sprinkler`） | 450 | Lv7 | 確定アセット＋コード | `deco_sprinkler`（#74）、アイコン画像あり。水しぶきは `SprinklerSpray` のコードで、絵の腕の軸から出る |
| 飾り | 風見鶏（`deco_weathervane`） | 500 | Lv6 | 確定アセット | `deco_weathervane`（#87）、アイコン画像あり |
| 飾り | 池（`deco_pond`） | 120 | Lv4 | 確定アセット | `pond.png`、つなぎ目と真ん中は `pond_center.png` |

集計：確定アセット 34（うちコード併用 3、旧画風 3）、生成アセット 1、コード描画（仮置き） 0、未作成（ショップに出していない） 3。
ショップに出ないもの：畝のある土（鍬で作る。`soil_tilled.png`）、耕した土（鍬で作る。`soil_revived.png`）、おくりものの箱（`deco_starter_crate`、#74 から絵）。
品を足したり絵を入れたりしたら、この表と `GardenItem.kt` 冒頭の一覧と `ASSET_LEDGER.md` を直すこと。

### 画像が無く、プログラムで描いているもの

#74 で庭に置く物はすべて絵になった。残りは**演出としてコードで描く**もの（絵に替える予定は無い。`ASSET_LEDGER.md` の「演出としてコードで描くもの」）：
工事現場（シート・足場）、作物を抜いたときに崩れる演出（#77）、芝生の育つ途中の苗床と葉、フェンスの横木と杭、スプリンクラーの水しぶき、木枠、背景（空・丘・茂み）、影、風の筋、天気。
すべて `ui/components/GardenCanvas.kt` と `ui/effects/GardenBackdrop.kt` にある。
庭に置く物は、絵があればそれを描く（同じ assetId でアトラスに入れるだけで差し替わる）。絵が読めないとき、コテージだけはコードの図形で、ほかの建物と飾りは何も描かない（#74 で図形を削除）。

作物と生き物の仮の絵（コード描画）について:

- 作物：#69 からすべての作物に絵がある。作物の仮の絵（`CropArt.kt`）は削除したので、新しい作物は絵（`<objectId>_stage1`〜`_stage<N>` と `<objectId>_withered`）を全部そろえて入れる。一部でも欠けると `CodeArtTest` が落ちる。
- 名前の決まりは `TileSetDefinition`（`spriteIdsFor` `assetIdFor` `expectedSpriteIds`）。アトラスに入れた名前が一覧に無いと `CodeArtTest` が落ちる
- 生き物：#71 から 6 種とも Recraft の絵（`creatures_0.png`）。仮の絵（コード描画）は削除した。絵の決まりは次の「生き物の絵の決まり」。

### 生き物の絵の決まり（#70、#71 で絵が入った）

生き物の動きはすべて、絵を**動かす**だけで作る（左右反転・伸縮・傾き・上下の移動・羽の開閉と回転・2 枚の絵の切り替え）。コマごとの描き分けは無い。

- **別の画像にまとめる**：作物・建物のアトラスには入れない（描く縮尺 `tileWidth` が 960px と別のため）。
  `app/src/main/assets/atlas/creatures_0.png` と `creatures_manifest.json`（形は `assets_manifest.json` と同じ）。
  読み込みは `AssetRepository.readAtlas(CREATURE_MANIFEST_FILE)` → `SpriteAtlas.creatures`。名前の一覧は `TileSetDefinition.expectedCreatureSpriteIds`（アトラスと食い違うと `CodeArtTest` が落ちる）。
- **作り方**：`python tools/prepare_creature_art.py --preview <フォルダ>`。原画（`tools/source_art/creature_*.png`）を切り抜いて `tools/raw_creatures/` に書き、
  接地点を `tools/creature_overrides.json` に書き、`asset_packer.py`（`--page-size 1024 --sheet creatures --manifest-name creatures_manifest.json`）で詰める。
  `--preview` は濃い背景に全部並べた確認用の 1 枚（赤い丸が接地点）。
- **解像度**：1 タイル 960px（`TILE`）。いちばん寄せたとき（`GardenCamera.MAX_SCALE` 2.5、420dpi で 1 タイル約 840px）に 1:1 より細かい。
- **向き**：地上の生き物と飛ぶことりは**左向き・斜め前**。ちょうちょ・とんぼは**真上から見て頭が上**。右へ進むときはコードが左右反転する。
- **接地点（anchor）**：地上の生き物は足元（てんとうむしは体の中ほどの下）。走るりすは体の下の中央、飛ぶことりは胴の中心、ちょうちょ・とんぼは胸（羽の付け根の高さの体の中心）。
  飛ぶことりは胴の中心が接地点なので、コードは立つことりの胴の高さ（`BIRD_BODY_HEIGHT` 0.08 マス）だけ持ち上げて描き、降り立つときに体の位置が跳ねないようにしている。
- **2 枚の絵**：りすは座る `creature_squirrel` と走る `creature_squirrel_run`（移動中）、ことりは立つ `creature_bird` と飛ぶ `creature_bird_fly_*`（庭の外から飛んで来て降りるまでと、飛び去るとき）。
  切り替えは一瞬で替えず、りすは 0.14 秒、ことりは地面から 0.025 マスの高さで重ねて入れ替える（約 0.25 秒）。
  走る姿・飛ぶ姿は、頭の大きさがそろうよう、座る姿・立つ姿と同じ倍率に合わせた（原画の頭の長さ：りす 190 と 123、ことり 228 と 135）。
- **羽の部品**：ちょうちょ・とんぼ・飛ぶことりは、胴（`*_body`）と左右の羽（`*_wing_left` `*_wing_right`）に切り分けてある。
  羽の **anchor は付け根**、**attachX/attachY は胴の接地点の位置**（羽の絵の外にはみ出してよい）。コードは付け根を胴に合わせて吊り、付け根を軸に動かす。
  ちょうちょ・とんぼは胴の中心線で切り、羽は胴の下の部分も持つ（羽をたたむと胴の下に寄り、すき間が開かない）。横方向に縮めて開閉する（`VisitorLook.wingOpen`）。
  飛ぶことりは胴の輪郭を手で決めて（`BIRD_BODY`）翼を切り出し、付け根を軸に下へ回して羽ばたく。羽は胴の後ろに描く。図鑑のちょうちょ・とんぼは切り分ける前の 1 枚を出す。
- **大きさ**：りす（座る姿の高さ）・ことり・かえる・ちょうちょ・とんぼ（幅）0.2 マス、てんとうむし（幅）0.075 マス（`SIZES`）。
- **切り抜きで気をつけたところ**：りすの毛先は輪郭から 14px まで白と分けた（白いもやを残さない）。走るりすのお腹の白っぽい毛は背景の白と見分けられず、ふつうの抜き方では白いギザギザが残ったので、お腹の下だけ、オレンジの毛からの距離で 6px かけて消している（`soft_underside`。輪郭はオレンジの毛の縁に沿う）。ことりは白い尾・頬・お腹を残すため、真っ白（244 以上）だけを背景にし、足のあいだの白は別に抜いた。
  とんぼの羽の膜は青みのある白（228〜251）で、背景（253 以上）と分けて不透明度 0.3 以上の半透明で残した。かえるの淡い指先とてんとうむしの細い脚はふつうの抜き方で残る。

## 原画（`tools/source_art/`）

ユーザーから受け取った元画像。アプリには入らない。

| ファイル | 状態 |
|---|---|
| `crop_hakusai_*` `crop_nanohana_*` `icon_hakusai.png` `icon_nanohana.png` `deco_weathervane.png` | 白菜・菜の花・風見鶏（#87、Recraft、白背景 1024×1024） |
| `app_icon_frame.png` `app_icon_bg_sky.png` | アプリのアイコンの箱庭と空（#87、Recraft） |
| `crop_cosmos_stage1.png` | コスモス段階 1（#87 で差し替え）。淡いので `deepened` で段階 2 の葉の色に寄せ、半透明の筋を濃くする |
| `item_fence_wood.png` | **#85 から未使用（保管）**。正面向きのパーツ集。#84 まで柵・支柱・横木を切り出していた |
| `struct_fence_wood.png` `struct_fence_white.png` `struct_fence_bamboo.png` `struct_edging_brick.png` | 柵 3 種と縁石（#85、Recraft、白背景 1024×1024）。1 区画を正面から見た絵。`tools/prepare_fence_art.py` で切り出す |
| `creature_{squirrel,squirrel_run,bird,bird_fly,frog,ladybug,butterfly,dragonfly}.png` | 生き物 6 種（#71、Recraft、白背景 1024×1024）。`tools/prepare_creature_art.py` で切り抜いて `creatures_0.png` に |
| `tile_path_cobblestone.png` | **#74 から未使用（保管）**。イラスト調の石畳（#40）。#73 まで `build_terrain_assets.py`（#85 で削除）が 4 分の 1 を切り出して石畳の床にしていた。#74 で `tile_cobblestone_iso.png` から作った `floor_cobblestone.png` に差し替え |
| `building_cottage.png` | コテージ（#40）。透過はきれいだった。切り抜いて 3 マス分の幅で描く（描画倍率 約 2.5）。土台が 2:1 より平たい。#41 では土台の左右の角を 3×3 の左右の角にそろえていた（anchorY 0.7975）が、前の列が空いて奥に寄って見えたので、#55 から他の建物と同じく左右の角と前の角のずれを半分ずつにする（`ground_centre_y`、anchorY 0.7481、約 0.2 マス手前へ） |
| `building_greenhouse.png` | ガラスの温室（#40）。ガラスは半透明。土台が 1 マスになるよう縮小 |
| `building_windmill_body.jpg` | 風車の本体（#40）。**JPEG で透過が無く、灰色の市松模様（48px、2 トーン）が背景として描き込まれていた**。マスごとの色で抜いた |
| `building_windmill_blades.jpg` | 風車の羽根（#40）。同じく白と灰色の市松模様（約 12px）と、白いにじみが描き込まれていた。色で抜いた |
| `tile_grass_iso.png` | 芝生（#57、受け取ったときは `res/drawable/tile_grass.png`）。白背景のアイソメの芝の板（1024×1024）。`prepare_drawable_art.flat_lawn` が上面を真上から見た正方形（720 px）に戻し、`flat_to_tile` で中央の正方形の半分（`share=0.5`）を 2:1 のひし形へ貼り、ぼかし 1.2px、厚み 55px の側面を足して `tile_grass` に。細かい葉のままだとスマホで約 7 分の 1 に縮んだとき砂嵐のようにちらつくため、葉を大きく柔らかくしている。#53 の `tile_lawn_flat.png` はこれに置き換えて削除 |
| `crop_*.png`（63 枚） | 作物の原画（#57・#58・#62〜#69）。トウモロコシ・ナス・小麦・ひまわり・ミント・コスモス・スノードロップ・スイカ・ラベンダー・しいたけ・ポインセチアは `_stage1`〜`3` と `_withered`、トマト・イチゴは `_1`〜`3` と `_withered`、ニンジンは `_stage1`〜`3` と `_withered`（#74 で `_1`・`_3` を改名、`_stage2` は #74 で届いた）、チューリップは `_stage1`・`_stage2` と 6 色の `_<色>_stage3` | `prepare_drawable_art.py` が切り抜いて `raw_assets/plant_*` に |
| `icon_*.png`（18 枚） | アイコンの原画（#57、ミントは #62、コスモスは #63、スイカは #65、ラベンダーは #67、しいたけは #68、ポインセチアは #69、チューリップ・ひまわり・バラ・パプリカ・大根は #72）。バラ・パプリカ・大根は株の絵をこのスクリプトで作っていないので、アイコンだけ作る（`ICON_ONLY`）。大根は白い根が欠けたため、ラベンダーと同じく `ICON_CUT` で真っ白だけを背景として抜く。`icon_snowdrop.png` は #64 では枯れた絵の複製だったが、#66 で正しい絵に置き直して使っている | `prepare_drawable_art.py` が切り抜いて `res/drawable-nodpi/` に |
| `tile_soil_flat.png` | 耕した土（#53）。同じく真上からの写真（受け取ったときは `floor_tilled.png`）。正方形全体をひし形へ、厚み 33px で `floor_tilled` に（温室の床でだけ使う） |
| `pond.png` | 池（#53）。透過ありのアイソメの絵（丸太の枠と石）。#54 から `res/drawable/pond.png` にコピーして池（`deco_pond`）に使用。`raw_assets/` に置くと詰められて `CodeArtTest` が落ちるので、原画はここに置いてある |
| `tile_grass_default.png` | **#53 から未使用**。芝生のタイル（#40）。縁に白いにじみ。1 マス幅に縮め、上面が 2:1 のひし形になるよう縦に伸ばした（元は約 2.35:1） |
| `tile_dirt_plowed.png` | **#53 から未使用**。耕した土のタイル（#40）。縁に白いにじみと、右上に青い点（ゴミ）。同様に加工（元の上面は約 2.7:1） |
| `tile_river_atlas.png` | **未使用**。4×4 に並んでいるが、16 種のつながり方に対応した並びではなく、隣と継ぎ目が合わず、背景も不透明だった。色だけ参考にして、川は生成している |

## 加工の手順

Python 3.12（Pillow、numpy）。パスは `AI_WORKFLOW.md` を参照。

```bash
# スプライトをアトラスに詰め直す（この引数の組み合わせを変えないこと）
python tools/asset_packer.py tools/raw_assets app/src/main/assets/atlas --tile-width 512 --source-tile-width 720 --alpha-threshold 8 --overrides tools/atlas_overrides.json --strip-crop-soil
```

- 原画は **1 タイル 720px** の縮尺で描かれている。アトラスは 512px に縮めて軽くしている。
- **ページとシート**（#73）：1 枚のアトラスが 4094×2749 で 4096px の上限に迫ったので、1 辺 2048px 以内のページに分けた（`--page-size`、既定 2048。`BuildingArtTest` が 2048 以内を見ている）。
  どのページに入るかは**名前で決まる**：`plant_*` は作物のシート（`crops_<n>.png`）、それ以外はすべて `world_<n>.png`（`asset_packer.SHEETS`）。絵を足す人が選ぶことは無い。
  シートごとに背の高い順に棚に並べ、空きのある最初のページに入れる（背の低い絵がページの下の余りを埋める）。ページがいっぱいになれば次のページ（`crops_3.png` など）が自動でできる。
  前のマニフェストにあって今回書かないページのファイルは消される。アプリは何ページでも `assetId` で絵を引く（`AssetManifest.page`、`SpriteAtlas.pageOf`、描画は `AtlasPages`）。
  ページは読み込み時に並べて同時にデコードする。#73 の時点の空き：`crops_2` に約 330 万画素、`world_0` に約 240 万画素（建物 1 枚が 30 万画素前後）。
- `atlas_overrides.json` の追加の鍵（#40）：`scale`（描画倍率。アトラスでは拡大せず、描くときに大きくする）と
  `attachX`/`attachY`（別のスプライトを取り付ける点。風車の羽根の軸）。マニフェストには `drawScale`・`attachX`・`attachY` として出る（使うときだけ）。

### #57 の絵（白背景の作物・アイコン・芝生）を入れるとき

```bash
python tools/prepare_drawable_art.py --preview <確認用フォルダ>
python tools/prepare_building_art.py
python tools/asset_packer.py tools/raw_assets app/src/main/assets/atlas --tile-width 512 --source-tile-width 720 --alpha-threshold 8 --overrides tools/atlas_overrides.json --strip-crop-soil
```

- 白の切り抜き（`cut_out`）：絵の外周につながる白と薄い灰色（影）を消し、葉に囲まれた大きな真っ白のすき間も消す。輪郭の画素は白との混ざりを戻して白いふちを残さない。
  トウモロコシの苗の一覧だけは影が濃いので、灰色を深めに消す。どの絵からも、本体から離れた点は消す。
  アイコンだけ切り抜き方を変えるものは `ICON_CUT`：ナス（#73）は下に落ちた灰色の影を、絵の上から 7 割より下で、背景につながるほぼ無彩色（3 色の差 7 以内）ならどれだけ濃くても消す（`shadow`。ナスの陰は差が 8 以上で残る）。スイカ（#73）は明るい側の縁が背景とみなされて欠け、白い粒が残っていたので、真っ白だけを抜く（`white_parts`）。
- `--preview` は作物ごとに段階を並べた絵（実際の大きさの比）と、アイコンの一覧、真上から見た芝生を出す。
- 地面の板 4 枚（荒れ地・耕した土・崩しかけた地面・水面）の上面の位置を表示する。`GroundSlab` の値はこれ。絵を差し替えたらこの値も直し、荒れ地は `FlatGround.FIELD_VERSION` を上げる（作った地面のキャッシュを捨てるため）。
- 1 回 1 分ほど。

### 柵と縁石の絵を入れるとき（#85）

```bash
python tools/prepare_fence_art.py --preview <確認用フォルダ>
```

- 原画は 1 区画を正面から見た 1 枚だけ。そこから `<id>`（1 本のとき）、`<id>_post`（杭）、`<id>_rail`（杭の間）を作り、`atlas_overrides.json` を書き換える。そのあと `asset_packer.py` を回す。アイコンも作る。
- 大きさ：杭の高さは 3 種とも 210px（1 マス 720px。今までの杭 167・板 247 の間）。原画は細長いので、木と白は幅を高さより 1.3 倍広く縮める（`stout`）。縁石は高さ 76px（杭の約 36%）。
- `_rail` は杭の中心から次の杭の中心まで（横に 360px）から杭の幅を引き、両端を杭の下へ 3px ずつもぐらせた幅。引き伸ばさず、中央を繰り返して杭の間に合わせる（#86）。木は 3 回（継ぎ目は原画の 60 列で溶かす）、白と竹は 2 回で、竹は原画の密度のまま。ゲームは `_rail` を立てたまま斜めのマスに沿ってせん断して描く（`drawRunPiece`）ので、絵は 1 枚で両方の斜めに使える。地面の高さは `attachY`（絵の下端より下にあるので 1 を超える）。
- 切り抜き：白い柵は真っ白（255）だけを背景にする（`cut_plain_white`。塗りは 205 ほどの灰色）。ほかは床の影ごと抜く（`cut_shadowed`）。どちらも縁の色から白の混ざりを戻す。
- 縁石（#86 で取り下げ、作り方は残してある）：絵のレンガの隙間に沿って斜めに切った 1 歯（幅 224px）を 1 マス 7 本並べる（`brick_row`）。両端が同じ斜めなので、1 マスずらして重ねると継ぎ目が消える。最後の歯は次のレンガの黒い縁が角でとげになるので 30px 落とす。絵のレンガは右に傾いているので、右下がりの辺では反転して描き、四辺を同じ見え方にする（`GardenCanvas` の `mirrored`）。

### アプリのアイコン（#87）

```bash
python tools/prepare_app_icon.py --preview <確認用.png>
```

- 原画 `app_icon_frame.png` を切り抜いて前景、`app_icon_bg_sky.png` の下寄り 68% を背景にする（丘が見える範囲に入るように）。箱の横幅は 108dp のキャンバスの 72dp、箱の中心は真ん中から 1.5% 上。下に濃さ 22% のぼかした影。
- 切り抜き：外周につながる白に加え、葉の間に閉じた真っ白（24 画素以上）も抜き、縁は白の混ざりを戻す。
- モノクロ（テーマアイコン、Android 13 以上）は箱庭全体の輪郭だけの形。濃淡で分けると細部がつぶれた。
- `mipmap-anydpi-v26` がアダプティブ。minSdk は 26 なので旧形式の PNG は普通は使われないが、対応しないランチャーのために置く。`--preview` は円・角丸・しずく形で切った一覧を出す。

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

### 建物の土台・色変えのマスク・石・橋（prepare_building_art.py）

- 土台（`fit_plinth`）：コテージと小屋の絵は石組みがマス目より平たい。土台の下の帯（`PLINTH_BAND`）だけを、列ごとに下へ伸ばし、手前の角と左右の角をマスの角に合わせる。壁と屋根は動かさない。アンカーは左右の角の高さ、中央。
- 色変えのマスク：`mask_struct_cottage` `mask_struct_hut`（赤＝屋根、緑＝壁）。アルファを絵と同じにしてあるので、アトラスで絵と同じ大きさに切り抜かれる。コテージは赤い瓦と淡い漆喰を色で、小屋は輪郭の多角形で取る。絵を描き替えたら `cottage_mask` `hut_mask` の多角形も直す。
- 大根の段階 3（`prepare_daikon`）：土の山を取り、根を上から 62% のところで曲線で切る。パッカーはこれ以上削らない（`crop_soil.SOILLESS`）。
- 石（`cut_stone`）：外周からつながる淡い灰色だけを抜く（ぬれた小石の光を残すため）。

### 水面と岸（prepare_water_art.py）

```bash
python tools/prepare_water_art.py --preview <確認用フォルダ>
```

- 岸の帯は水に 0.20 マス入る（`BANK_DEPTH - CELL_OVERHANG`）。1 マス幅の川の水面はマスの 6 割。白いふちは `strip_white` が消す。
- 小川のかけらと池のアイコンもここで作る。
- `--pond` は `res/drawable/pond.png` の水の色を合わせ直す。ファイルをその場で書き換えるので、池の絵を差し替えたときだけ使う。

### 新しいスプライトを入れるとき

1. 背景が透明な PNG を `tools/raw_assets/<assetId>.png` として置く（作物は `plant_`、建物は `struct_`、飾りは `deco_` で始める。入るページは名前で決まる）。
2. 必要なら `atlas_overrides.json` にアンカーを足す。
3. `asset_packer.py` を上の引数で実行する。
4. `BundledArtTest` と `AssetPipelineTest`（ページの上限は `BuildingArtTest`）を回す。
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
