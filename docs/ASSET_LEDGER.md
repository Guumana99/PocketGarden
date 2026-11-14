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
| ⬜ 仮 | 絵が無く、コードで描いている（または絵文字）。素材が入ればそちらに替わる。メモに「未作成」とある物はショップに出していない（`GardenItem.inShop = false`） |
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
画像の枠の assetId は `plant_<作物>_stage<N>` と `plant_<作物>_withered`。アイコンは持ち物・ショップ・おくりもので出る絵（`UiLabels.itemIconRes`、無ければ絵文字）。

| 作物（id） | 解放 | 段階 1 | 段階 2 | 段階 3 | 段階 4 | 枯れ | アイコン | 出所・メモ |
|---|---|---|---|---|---|---|---|---|
| チューリップ（`plant_tulip`） | 最初から | ✅ | ✅ | ✅ | ➖ | ➖ | ✅ | Recraft（#58）。段階 3 は 6 色（赤・ピンク・黄・白・紫・青）すべて ✅。枯れない作物なので枯れた絵は不要。アイコンは #72（Recraft、C2PA を grep で確認）。赤・ピンク・黄の 3 本の束 |
| ひまわり（`plant_sunflower`） | Lv3 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#58）。茎の青い影は 2026-11-02 に切り抜きから除いた（`without_cast_shadow`）。アイコンは #72（Recraft、C2PA を grep で確認） |
| バラ（`plant_rose`） | Lv8 | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 | ✅ | Recraft（#6 ごろの手描き風）。4 段階。アイコンは #72（Recraft、C2PA を grep で確認）。株は旧画風のまま、アイコンだけ 3D ミニチュア調 |
| トマト（`plant_tomato`） | 最初から | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58） |
| イチゴ（`plant_strawberry`） | 最初から | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58） |
| ニンジン（`plant_carrot`） | Lv2 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58、段階 2 は #74。C2PA を grep で確認）。段階 2 は #73 まで段階 3 の葉を切り出した代用品で、#74 で届いた絵（根元にオレンジの輪が少し見える）に。段階 3 は葉と根の肩だけに切り出し（#60。2026-11-02 に根の 60% を見せる形に）。#77 で根の切り口を水平な直線から、手前ほど深い曲線（ゆらぎ付き）にし、すぐ上を土の色に染めた（`prepare_drawable_art.carrot_stages`、`CARROT_SOIL_*`） |
| ナス（`plant_eggplant`） | Lv6 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58）。段階 1 は 4 本の苗から手前の 1 本を切り出し。アイコンの下に残っていた灰色の影を #73 で取り除いた |
| 小麦（`plant_wheat`） | Lv7 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58）。描くときに 0.75 倍（畝に収まるように、11/11） |
| トウモロコシ（`plant_corn`） | Lv13 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#57、枯れは #58）。段階 1 は 16 本の苗から 3 本、段階 2 は 2 本に左右反転の 1 本を足して 3 本に並べ直した |
| パプリカ（`plant_paprika`） | Lv11 | 🟡 | 🟡 | 🟡 | ➖ | 🟡 | ✅ | Recraft（#39）。アイコンは #72（Recraft、C2PA を grep で確認）。株は旧画風のまま |
| 大根（`plant_daikon`） | Lv4 | 🟡 | 🟡 | 🟡 | ➖ | 🟡 | ✅ | Recraft（#39）。段階 3 は `crop_daikon_stage3.png`（白い根の上の部分が見える新しい絵）に差し替え、描くときに幅 0.76 マスまで縮め、さらに 0.8 倍（11/11）。アイコンは #72（Recraft、C2PA を grep で確認）。白い根が欠けないよう、アイコンは真っ白だけを背景として抜いた |
| コスモス（`plant_cosmos`） | 最初から | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#63。C2PA を grep で確認）。#63 で 4 段階から 3 段階に。葉が細く、白との混ざりを絵全体で戻して切り抜いた |
| スノードロップ（`plant_snowdrop`） | 最初から | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#64。C2PA を grep で確認）。#64 で 4 段階から 3 段階に。段階 1 はひげ根を切り落とし、白い花びらは花のまわりだけ弱く切り抜いた。アイコンは #64 で届いた絵が枯れた絵の複製だったため、#66 で置き直したもの（Recraft） |
| ミント（`plant_mint`） | 最初から | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#62。C2PA を grep で確認）。#62 で 4 段階から 3 段階に |
| スイカ（`plant_watermelon`） | Lv9 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#65。C2PA を grep で確認）。#65 で 4 段階から 3 段階に。2・3 段階目と枯れは横に広い葉の山で、幅で大きさを決めた（3 段階目と枯れは 0.85 マス）。2 段階目の葉は黒に近い灰緑だったので、#66 で 3 段階目と同じ明るさまで上げた（色味は同じ）。アイコンの縁の白い粒を #73 で取り除いた |
| ラベンダー（`plant_lavender`） | Lv5 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#67。C2PA を grep で確認）。#67 で 4 段階から 3 段階に。細かい葉の白いもやを絵全体で戻して切り抜いた。アイコンの麻ひもは消えないよう白だけを抜いた |
| しいたけ（`plant_shiitake`） | Lv14 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#68。C2PA を grep で確認）。#68 でカボチャと入れ替えた作物。丸太の長さが全段階で同じになるよう大きさを決めた。枯れの白っぽい樹皮は白だけを抜いて切り抜いた |
| ポインセチア（`plant_poinsettia`） | Lv15 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#69。C2PA を grep で確認）。#69 で 4 段階から 3 段階に。段階 2 の苞はピンク、段階 3 は赤（届いた絵のまま） |
| 白菜（`plant_hakusai`） | Lv10 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#87、#91 で段階 3 と枯れを外葉の広がった株に差し替え。横幅 0.76 マス、結球の足元に立つ）。段階 2 のひげ根を切り落とす。葉が白っぽいので真っ白だけを抜く（`white_parts`）。段階 3 は縦長で高さ 0.8 マス |
| 菜の花（`plant_nanohana`） | Lv12 | ✅ | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#87、#93 で段階 3 と枯れを穂の付いた株に差し替え。横幅 0.8 マス。#94 で段階 2 を緑のつぼみの房の株に差し替え。横幅 0.55 マス）。切り抜きはコスモスと同じ（`throughout`）。段階 2 から幅で大きさを決める（0.55・0.72 マス） |
| かぶ（`plant_kabu`） | Lv7 | 🔁 | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#book60。段階 2・3・枯れが届いた）。段階 1 は段階 2 から白い玉を消した葉だけの芽（`kabu_sprout`）。白い玉が欠けないよう真っ白だけを抜く（`white_parts`）。FB1114：畑の上に白い玉を置いたように見えたので、段階 2・3・枯れとも玉の下を切って土に埋めた見え方に（`buried_turnip`。玉のいちばん太い所の少し下を、手前ほど低い曲線で切り、切り口のすぐ上を土の色に染め、まわりに縁をぼかした土の影をコードで描く。FB1114b で盛り土と土くれ（皿や鉢に見えた）から影に変え、玉を少し多く見せた。色は畑の土 `SoilPalette`。接地点は影の手前の足元）。葉の間に閉じ込められた背景の白い点は、まわりの葉の色で埋めた（`without_specks`）。枯れは灰色の玉を少し明るく黄みを足した（`KABU_ROOT_*`）。アイコンは FB1114 で届いた `icon_kabu.png`（収穫物の大写し。大根と同じく真っ白だけを抜く） |
| さつまいも（`plant_satsumaimo`） | Lv9 | 🔁 | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#book60）。原画の明るい土の山は抜き、芋と葉だけにした（大根と同じ見え方。土を暗く低く残す案と畑の上で比べた。`without_earth`）。山の右の薄い影も抜いた。葉の色を 1.25 倍鮮やかに。段階 1 は段階 2 を縮めたもの。横幅 0.56・0.7 マス。FB1114：土を抜くときに、葉の明るい照りと芋の明るい所まで土として抜けて穴になっていたので、土は大きなかたまりだけを抜くようにし、閉じ込められた小さな穴は原画の色で埋めた（`without_earth`・`without_specks`）。描くときに 0.85 倍（葉が隣のマスにかかった）。アイコンは FB1114 で届いた `icon_satsumaimo.png`（芋の下の薄い影はナスと同じく抜く） |
| 水仙（`plant_suisen`） | Lv12 | 🔁 | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#book60、段階 3 は黄の `crop_suisen_yellow_stage3.png`）。段階 3 は 3 色：黄（原画のまま）・白い花びらに黄の中心（`_white`）・黄の花びらにオレンジの中心（`_orange`、めずらしい）。花びらと中心（副花冠）の範囲を原画の上で決め（`SUISEN_CUPS`）、色をコードで変えた（`suisen_colour`）。真っ白は作らずクリーム色で陰影を残す。段階 1 は段階 2 を縮めたもの。高さ 0.3・0.48・0.6 マス。FB1114：根元の白っぽい茎の束（下 3 割）を葉の緑に 6 割まで寄せた（`greener_foot`。アイコン・3 色とも）。描くときに 0.85 倍 |
| 葉ぼたん（`plant_habotan`） | Lv16 | 🔁 | ✅ | ✅ | ➖ | ✅ | ✅ | Recraft（#book60）。長い茎はほぼ切り、葉の塊の下が接地点（`without_stem`）。背景の薄い灰色は外から抜き、中心の白い葉は抜かない（`pocket` 60）。段階 1 は段階 2 を縮めたもの。横幅 0.46・0.6 マス。描くときに 0.8 倍（FB1114。白菜より大きく見えた）。アイコンは段階 3 の株 |

- 仮（⬜）の作物に絵を入れるときは、**その作物の段階と枯れを全部そろえて**入れる（一部だけだと `CodeArtTest` が落ちる）。段階の数を 3 に変えるなら `PlantSpecies.growthStages` も直す。
- アイコンの絵は `res/drawable-nodpi/icon_<作物>.png`（256×256、`tools/prepare_drawable_art.py` が原画から作る）を置き、`UiLabels.itemIconRes` に 1 行足す。

**集計（作物の絵の枠 96、アイコン 24）**

| | ✅ 採用 | 🟡 旧画風 | 🔁 代用 | ⬜ 仮 |
|---|---|---|---|---|
| 段階・枯れの枠（96） | 79 | 13 | 4 | 0 |
| アイコン（24） | 24 | — | — | 0 |

## 建物

| 物（id） | 状態 | 出所 | 素材・メモ |
|---|---|---|---|
| 木の柵（`struct_fence`） | ✅ | Recraft | `struct_fence`（1 本のとき）`struct_fence_post` `struct_fence_rail`（つながるとき。#85、原画 `struct_fence_wood.png` から切り出し、横木の中央を 3 回繰り返す（#86））。コードの横木と杭はやめた。11/1 に取り込みの色を調整（`Grade`、絵は同じ） |
| 白い柵（`struct_fence_white`） | ✅ | Recraft | `struct_fence_white` `struct_fence_white_post` `struct_fence_white_rail`（#85、原画 `struct_fence_white.png`。真っ白だけを抜く。杭の間は 2 回並べる）。11/1 に取り込みの色を調整（`Grade`、絵は同じ） |
| 竹垣（`struct_fence_bamboo`） | ✅ | Recraft | `struct_fence_bamboo` `struct_fence_bamboo_post` `struct_fence_bamboo_rail`（#85、原画 `struct_fence_bamboo.png`。つなぎは原画の密度のまま 2 回並べる（#86））。11/1 に取り込みの色を調整（`Grade`、絵は同じ） |
| レンガの縁石（`struct_edging_brick`） | 📦 | Recraft | **#86 で取り下げ（ショップ・図鑑から外した）**。 `struct_edging_brick` `struct_edging_brick_rail`（#85、原画 `struct_edging_brick.png`。歯を 1 マス 7 本に並べ直す。右下がりの辺は反転して描く。杭は無い） |
| 井戸（`struct_well`） | ✅ | Recraft | `struct_well`（原画 `building_well.png`、Recraft（#74。C2PA を grep で確認））。#74 で 1×2 から 1×1 に。丸い石の井戸に屋根と桶。屋根の色は `mask_struct_well`（#92、`tools/prepare_building_art.py`） |
| 風車（`struct_windmill`） | ✅ | Google→Recraft | 本体 `struct_windmill`・羽根 `struct_windmill_blades`（原画は JPEG。描き込まれた市松模様を抜いた） |
| 小屋（`struct_hut`） | ✅ | Recraft | `struct_hut`（原画 `building_hut.png`を差し替え：片流れのトタン屋根・板壁・観音開きの戸）。色変えのマスクは `mask_struct_hut`。土台の石が明るいクリーム色なので、真っ白だけを抜いて切り抜いた |
| コテージ（`struct_cottage`） | ✅ | Recraft | `struct_cottage`（原画 `building_cottage.png`）。土台の下の帯をマスの角まで伸ばしている。色変えのマスクは `mask_struct_cottage` |
| 温室（`floor_greenhouse`） | ✅ | Recraft | 連結式（#90）：`struct_greenhouse_wall` `struct_greenhouse_door` `struct_greenhouse_post`（原画 `struct_greenhouse_wall.png` `struct_greenhouse_door.png`、`tools/prepare_greenhouse_art.py`）。ガラス・屋根の枠はコード描画（`GreenhousePainter`）、床は耕した土のコード描画。1 枚絵の `floor_greenhouse` は使わなくなったがアトラスに残してある |
| 工事現場（シート・足場） | ⬜ | — | 演出としてコードで描く（建てる途中の進み具合で足場とシートが変わる）。建物ごとの絵は無い |

## 地面・装飾

| 物（id） | 状態 | 出所 | 素材・メモ |
|---|---|---|---|
| 荒れ地（何も敷いていないマス） | ✅ | Recraft | `res/drawable/soil_wasteland.png` |
| 耕した土（`floor_loose`、畝の無い平らな土） | ✅ | Recraft | `res/drawable/soil_revived.png`（#78 からこの用途。#61〜#77 は崩しかけた地面）。クワ 1 回目の「まだらに重なる」のもこの絵 |
| 畝のある土（`floor_tilled`） | ✅ | Recraft | `res/drawable/soil_tilled.png` |
| 土の質の色（よい土・ふかふかの土） | ⬜ | — | 土の絵に色を掛ける（`soilTint`、乗算。#78）。たい肥は物として残さず、抜いたときの崩れる演出（`drawCrumbling`、コード）だけ |
| 芝生（`ground_grass`） | ✅＋⬜ | Recraft | 育ちきると `tile_grass`（原画 `tile_grass_iso.png`）。育つ途中の苗床と葉の切り抜きは演出としてコードで描く（#61）。`tile_grass_dark` は 📦 |
| 苔（`ground_moss`） | ✅ | Recraft | `res/drawable-nodpi/ground_moss.png`（原画 `tile_moss_top.png`、#book60。真上から見た模様のまま届いた）。大きな斜めの明暗の波を消して平らにし（`MOSS_EVEN_OUT`）、ごく弱いむらを戻し（`MOSS_MOTTLE`）、端をつないで 1 マスに 1 枚敷く（`moss_texture`）。芝生と同じく地面を覆い、上に置ける物も芝生と同じ。FB1114：芝生と同じ時間（48 時間）で育つ。育つ途中は同じ絵をまだらな薄い斑点で見せ、斑点が広がってつながり一面になる（コード描画 `MossPatches`）。旧セーブの苔は育ち切った状態 |
| 小石（`ground_pebble`） | ✅ | Recraft | `res/drawable-nodpi/ground_pebble.png`（原画 `tile_pebble_iso.png`、Recraft（#74。C2PA を grep で確認））。板の上面を真上から見た模様にし、マスどうしが続くようにして 1 マスに 1 枚敷く |
| 野の花（`ground_flowers`） | ✅ | Recraft | `res/drawable-nodpi/ground_flowers.png`（原画 `tile_flowers_iso.png`、Recraft（#74。C2PA を grep で確認））。小石と同じ作り方 |
| 石畳（`floor_cobblestone`） | ✅ | Recraft | `res/drawable-nodpi/floor_cobblestone.png`（原画 `tile_cobblestone_iso.png`、Recraft（#74。C2PA を grep で確認））。小石と同じ作り方で、継ぎ目では石が重ならず目地で区切れる。#73 まではイラスト調の `tile_path_cobblestone.png`（📦、`tools/source_art/`） |
| ウッドデッキ（`floor_wood`） | ✅ | Recraft | `res/drawable-nodpi/floor_wood.png`（原画 `tile_wood_deck_iso.png`、Recraft（#74。C2PA を grep で確認））。板の幅の整数倍で切って、隣のマスへ板が続く。板の長さ方向はマスの縁が板の継ぎ目 |
| 小川／せせらぎ（`tile_river`） | ✅ | Recraft | 水面 `water_surface.png`・波 `water_ripple_a.png` `water_ripple_b.png`・岸 `river_bank.png` `river_bank_concave.png` `river_bank_convex.png`（`tools/prepare_water_art.py`。原画 `water_surface.png` `river_bank.png`）。角 2 種は帯を曲げて作った |
| 池（`deco_pond`） | ✅ | Recraft | `res/drawable/pond.png`。つなぎ目と真ん中の水面は川と同じ `water_surface.png`、水の色も合わせた |
| 丸い岩（`deco_rock`） | ✅ | Recraft | `deco_rock`（原画 `deco_rock.png`、Recraft（#74。C2PA を grep で確認））。幅 0.46 マス |
| 平たい石（`deco_rock_flat`） | ✅ | Recraft | `deco_rock_flat`（原画 `rock_flat.png`）。幅 0.5 マス |
| 苔むした岩（`deco_rock_mossy`） | ✅ | Recraft | `deco_rock_mossy`（原画 `rock_mossy.png`）。幅 0.5 マス |
| 立て石（`deco_rock_standing`） | ✅ | Recraft | `deco_rock_standing`（原画 `rock_standing.png`）。高さ 0.68 マス、根元が接地点 |
| 小石の山（`deco_rock_pebbles`） | ✅ | Recraft | `deco_rock_pebbles`（原画 `rock_pebbles.png`）。幅 0.52 マス |
| 太鼓橋（`struct_bridge_taiko`） | ✅ | Recraft | `struct_bridge_taiko`（1×3）、`struct_bridge_taiko_long`（1×4。長さ方向だけ伸ばしたもの）。原画 `bridge_taiko.png` |
| ベンチ（`deco_bench`） | ✅ | Recraft | `deco_bench`（原画 `deco_bench.png`、Recraft（#74。C2PA を grep で確認））。幅 0.56 マス、4 本の脚の真ん中に立つ。裏側 `deco_bench_back`（原画 `bench_back.png`）と反転で 4 方向 |
| 石灯籠（`deco_lantern`） | ✅ | Recraft | `deco_lantern`（原画 `deco_lantern.png`、Recraft（#74。C2PA を grep で確認））。高さ 0.44 マス |
| スプリンクラー（`deco_sprinkler`） | ✅＋⬜ | Recraft | 本体 `deco_sprinkler`（原画 `deco_sprinkler.png`、Recraft（#74。C2PA を grep で確認）。高さ 0.34 マス）。水しぶき（`SprinklerSpray`）は演出としてコードで描き、本体の絵の腕の軸（取り付け点）から出る。しぶきの絵を入れるなら `deco_sprinkler_spray_0`〜`_7` |
| 風見鶏（`deco_weathervane`） | ✅ | Recraft | `deco_weathervane`（原画 `deco_weathervane.png`、#87）。高さ 0.8 マス、柱の土台の中心がマスの中心 |
| かかし（`deco_scarecrow`） | ✅ | Recraft | `deco_scarecrow`（原画 `deco_scarecrow.png`、#book60）。高さ 0.92 マス（腕は 1 マスに収まる 0.62 マス幅）、杭の下端が接地点（`ON_A_POST`）。向きを変えると左右反転。雪の日は帽子と袖の上にうっすら雪（`PictureSnow`） |
| バードバス（`deco_birdbath`） | ✅ | Recraft | `deco_birdbath`（原画 `deco_birdbath.png`、#book60）。高さ 0.5 マス。向きを変えると左右反転。雪の日は鉢の縁にうっすら雪、水面は雪を載せず淡い氷の色（`PictureSnow.FROZEN`） |
| おくりものの箱（`deco_starter_crate`。#79 で名前だけ変更、絵は同じ） | ✅ | Recraft | `deco_starter_crate`（原画 `deco_starter_crate.png`、Recraft（#74。C2PA を grep で確認））。幅 0.42 マス、ふたが開いて苗と種袋が見える |

## ショップのアイコン

ショップ・持ち物・おくりもので品物に出す小さな絵（`UiLabels.itemIconRes`、256×256、`tools/prepare_drawable_art.py` が原画から作る）。作物は上の作物の表のアイコンの列（#72 から 18 種すべて ✅）。
`itemEmoji` は、絵が無いときの予備として残してある。

| 品物（id） | 状態 | 素材・メモ |
|---|---|---|
| 小屋・井戸（`struct_hut` `struct_well`） | ✅ | `icon_struct_hut.png` `icon_struct_well.png`（#74、建物の原画から） |
| 岩・ベンチ・石灯籠・スプリンクラー（`deco_rock` `deco_bench` `deco_lantern` `deco_sprinkler`） | ✅ | `icon_deco_*.png`（#74、飾りの原画から） |
| 小石・野の花・ウッドデッキ・石畳（`ground_pebble` `ground_flowers` `floor_wood` `floor_cobblestone`） | ✅ | `icon_ground_*.png` `icon_floor_*.png`（#74。斜め上から見た板の形のまま） |
| コテージ・温室（`struct_cottage` `floor_greenhouse`） | ✅ | `icon_struct_cottage.png`（#93 でしみを取った絵から作り直し） `icon_floor_greenhouse.png`（温室は #90 から連結式の 2×2 に作物を入れた姿、`tools/prepare_greenhouse_art.py`、作物入りの絵はアトラスから取るので `asset_packer.py` の後に流す。#77、庭で使っている切り抜き済みの絵 `raw_assets/` から） |
| 柵 3 種・縁石（`struct_fence` `struct_fence_white` `struct_fence_bamboo` `struct_edging_brick`） | ✅ | `icon_struct_fence.png` ほか 3 枚（#85、1 本で立つときの絵から。`tools/prepare_fence_art.py`）。縁石は取り下げ中で店には出ない |
| 風車（`struct_windmill`） | ✅ | `icon_struct_windmill.png`（#77、本体と羽根の切り抜きを、庭と同じ取り付け点で重ねた） |
| 池・芝生（`deco_pond` `ground_grass`） | ✅ | `icon_deco_pond.png`（#77、`pond.png`）・`icon_ground_grass.png`（#77、`tile_grass_iso.png` を板の形のまま） |
| 小川（`tile_river`） | ⚙️ | `icon_tile_river.png`（#77、スクリプトで作った `tile_river_autotile.png` のまっすぐなひと続きを、庭のマスと同じひし形に） |
| 風見鶏（`deco_weathervane`） | ✅ | `icon_deco_weathervane.png`（#87、原画 `deco_weathervane.png` から） |
| 苔・かかし・バードバス（`ground_moss` `deco_scarecrow` `deco_birdbath`） | ✅ | `icon_ground_moss.png`（#book60、苔の模様を芝生のアイコンと同じ板の形に）・`icon_deco_scarecrow.png` `icon_deco_birdbath.png`（原画から、#74 と同じ作り） |

**集計（作物以外の品物のアイコン 24。すべて絵）**：✅ 23、⚙️ 1。

## 演出としてコードで描くもの

絵に替える予定は無く、コードで描き続けるもの（#74 で決めた）。動き・進み具合・天気で形や色が変わるため。

- 工事現場（シート・足場）：建てる途中の進み具合で変わる
- 作物を抜いたときに株が崩れて土に混ざる演出（#77、`drawCrumbling`）
- 芝生が育つ途中（苗床と葉の切り抜き）
- フェンスの横木（隣のフェンスへ斜めにつなぐ部分）と杭
- スプリンクラーの水しぶき（`SprinklerSpray`）
- 肥料が土に染みて栄養が満ちる演出（`ui/render/FeedingEffect.kt`：落ちる粒・土の光・上がる泡・マスの上の小さなゲージ・満ちたときのきらめき。11/6）
- 庭の木枠・背景・影・風・天気（雨・雨乞いの雨・雪。雪の日の地面と作物の雪、舞う雪は #75。屋根と柵の縁の雪は 11/7 夜、`EdgeSnow`。屋根・ベンチ・岩の雪は FB1113 から絵の画素から計算、`SnowCover` `PictureSnow`）
- 時間帯の光（朝・夕方・夜の色、`SceneTint`）、夜の明かり（石灯籠の火と小屋・コテージの窓、`drawLamps`）、夜空の月と星（#75）

## 生き物

#71 から 6 種とも ✅（Recraft の絵。原画は `tools/source_art/creature_*.png`、白背景 1024×1024、11 枚すべて C2PA に Recraft AI の記録あり、Google の記録なし）。#book60 でうさぎとかたつむりを足して 8 種。
作物のアトラスではなく、生き物用の別の画像 `creatures_0.png` `creatures_1.png` / `creatures_manifest.json`（#book60 で 2 ページ、998×1021 と 545×154、1 タイル 960px）に入っている。✅ はその画像に入っているもの。
作り方は `tools/prepare_creature_art.py`、絵の決まり（羽の部品・接地点・大きさ）は `ASSETS.md` の「生き物の絵の決まり」。コード描画の仮の絵は #71 で削除した。

| 生き物 | assetId | 出所・メモ | 状態 |
|---|---|---|---|
| ちょうちょ | `creature_butterfly` | Recraft（#71）。真上から見た姿。胴（`_body`）と左右の羽（`_wing_left` `_wing_right`）に切り分けて羽ばたかせる | ✅ |
| てんとうむし | `creature_ladybug` | Recraft（#71）。0.075 マス。2026-11-08 から、葉の緑の上でくっきり見えるよう取り込むときに赤を少し鮮やかに（彩度 1.3 倍・明るさ 1.08 倍）し、外周 1px を濃く（`clearer_ladybug`）。元の絵は `tools/source_art` のまま | ✅ |
| ことり | `creature_bird` | Recraft（#71）。立つ姿と飛ぶ姿（`creature_bird_fly.png` を胴 `_fly_body` と左右の翼 `_fly_wing_left` `_fly_wing_right` に切り分け）。白い尾・頬・お腹を残すため、白の抜き方を弱くした | ✅ |
| りす | `creature_squirrel` | Recraft（#71）。座る姿と走る姿（`creature_squirrel_run`）。しっぽの毛先は白いもやが残らないよう深めに白と分けた。走る姿のお腹の白っぽい毛は、白いギザギザが残らないよう、お腹の下だけ柔らかく消した | ✅ |
| かえる | `creature_frog` | Recraft（#71） | ✅ |
| ことり（ついばむ） | `creature_bird_peck` | Recraft で作成・採用（2026-11-02）。頭を下げた立ち姿。つつく間だけ立ち姿から切り替える | ✅ |
| ことり（正面） | `creature_bird_front` | Recraft で作成・採用（2026-11-07）。振り向きの途中に約 0.09 秒だけ出す。横向きと頭から足までの高さをそろえ、両足の間を接地点にした。淡い足の指は切り抜きで残っている | ✅ |
| りす（正面） | `creature_squirrel_front` | Recraft で作成・採用（2026-11-07）。振り向きの途中に約 0.09 秒だけ出す。横向きと高さをそろえ、両足の間を接地点にした。しっぽの毛先は横向きと同じく深めに白と分けた | ✅ |
| りす（縮んだ走り） | `creature_squirrel_run2` | Recraft で作成・採用（2026-11-02）。接地点は足元。移動中、跳ねの地面側で `creature_squirrel_run` と入れ替わる | ✅ |
| かえる（跳ぶ） | `creature_frog_jump` | Recraft で作成・採用（2026-11-02）。原画のかえるが小さいので 1.8 倍にして目をそろえた。跳ぶ間だけ出す | ✅ |
| とんぼ | `creature_dragonfly` | Recraft（#71）。真上から見た姿。2026-11-08 から 0.26 マス（前は 0.2 マス、1.3 倍）。透明な羽は半透明（不透明度 0.3〜）で残し、脈と縁は不透明。胴と左右の羽に切り分け | ✅ |
| うさぎ | `creature_rabbit` | Recraft（#book60）。座る姿・跳ぶ姿（`creature_rabbit_run`、同じ倍率）・正面（`creature_rabbit_front`、座る姿と頭から足までの高さをそろえ、両足の間が接地点）。高さ 0.24 マス。お腹と尾の白が欠けないよう真っ白だけを抜く。白い冬毛の 3 枚（`creature_rabbit_white` `creature_rabbit_white_run` `creature_rabbit_white_front`）は茶色の 3 枚の毛をコードで白く塗った（`winter_coat`。耳先の黒・目・陰影は残す）。FB1114：茶色の判定から外れた灰色がかった暗い毛が背中の筋・正面の右わきの斑として残っていたので、目（原画上の位置で指定）と耳の黒以外の見えている所はすべて毛として塗り、明るさは周りとならして暗い毛の点を消した | ✅ |
| かたつむり | `creature_snail` | Recraft（#book60）。左向き。幅 0.11 マス（最大ズームで殻の渦が見える）。淡い目の柄が消えないよう真っ白だけを抜く。這うときは殻を残して体だけ伸び縮みさせる（`SNAIL_SHELL_FROM`） | ✅ |

図鑑の「いきもの」の章（影絵・ページの絵）は、各種の 1 枚目（`creature_<id>`）を静止して出す。

## 画面・UI

| 物 | 状態 | メモ |
|---|---|---|
| 背景（空・丘・草原・隅の茂み） | ⬜ | `ui/effects/GardenBackdrop.kt`。演出としてコードで描く |
| 庭の木枠、影、風の筋、天気（雨・雨乞いの雨・雪）、時間帯の光と夜の明かり | ⬜ | 演出としてコードで描く（上の「演出としてコードで描くもの」。雪・時間帯・明かりは #75） |
| 木のボタン・トースト・図鑑の本・看板 | ⬜ | Compose のコード描画（`ui/components/` ほか） |
| 店の内装（棚・カウンター） | ⬜ | コード。商品はすべてアイコンの絵（#77。上の「ショップのアイコン」） |
| アプリのアイコン（ランチャー） | ✅ | 出所 Recraft。原画 `app_icon_frame.png`（箱庭）・`app_icon_bg_sky.png`（空と丘）。`res/mipmap-*/ic_launcher_{foreground,background,monochrome}.png`、旧形式 `ic_launcher.png` `ic_launcher_round.png`（#87、`tools/prepare_app_icon.py`）。モノクロは箱庭の輪郭の形。11/1 に上下の中央・直径 66dp の円の内側へ（モノクロも同じ） |
| 通知の小さなアイコン | ✅ | `res/drawable/ic_stat_garden.xml`（芽の形の単色ベクター。ステータスバー用で単色が必須） |

## 音

すべてゲームで使っている（✅）。BGM は Suno Pro（2026-11-10 作り直し）。効果音の**出所は記録が無い**（`ASSETS.md` では「外部で用意されたもの」）。分かれば下の表に書く。

| 種類 | ファイル | 状態 | 出所 |
|---|---|---|---|
| BGM | `bgm_title` `bgm_spring` `bgm_summer` `bgm_autumn` `bgm_winter`（5 曲のループ部分）と、イントロ `bgm_title_intro` `bgm_spring_intro` `bgm_summer_intro` `bgm_autumn_intro` `bgm_winter_intro`（ogg） | ✅ | Suno Pro（2026-11-10 作成）。イントロ＋ループに加工済み。元の WAV は `tools/source_audio/bgm/`（コミットしない） |
| 効果音 | `se_water` `se_fertilize` `se_plant` `se_harvest` `se_coin` `se_level_up` `se_error` `se_tap`（8 個。`se_tap` はマスのタップには使っていない） | ✅ | 未記録 |

## ストア掲載用

| 物 | 状態 |
|---|---|
| 高解像度アイコン（512×512） | ✅。出所 Recraft。`docs/store/icon_512.png`（#87、原画 `app_icon_frame.png` `app_icon_bg_sky.png`。背景込みの正方形、角は丸めない）。11/1 にランチャーと同じ位置・大きさへ |
| フィーチャーグラフィック（1024×500） | 未作成 |
| スクリーンショット | 未作成 |

## 保管（使っていない素材）

| ファイル | 理由 |
|---|---|
| `tools/source_art/tile_grass_default.png` | #53 から未使用（芝生は `tile_grass_iso.png` から作る） |
| `tools/source_art/tile_dirt_plowed.png` | #53 から未使用 |
| `tools/source_art/tile_river_atlas.png` | 16 種のつながり方に合わない並びで、継ぎ目も合わなかった |
| アトラスの `tile_grass_dark` | 残っているが描画に使っていない |
