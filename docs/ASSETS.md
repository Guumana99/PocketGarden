# アセット（画像・音）

> **このファイルには未記入の欄があります。**
> 画像の生成に使ったツールの設定、プロンプト、キャラクター「たこパイセン」のデザイン方針は、
> これまでの開発スレッドでは共有されておらず、リポジトリにも記録がありません。
> 推測で書くと将来の作業を誤らせるため、該当箇所は「未記入」としています。
> 生成を担当した方が追記してください（[追記してほしいこと](#追記してほしいこと)）。

## いま同梱されているもの

| 置き場所 | 中身 | 作り方 |
|---|---|---|
| `app/src/main/assets/atlas/atlas.png` + `assets_manifest.json` | スプライト 41 枚を 1 枚に詰めたもの（4019×2408、約 11MB、メモリ上 約 39MB、1 タイル 512px） | `tools/asset_packer.py` |
| `app/src/main/res/drawable-nodpi/tile_river_autotile.png` | 川。16 種のつながり方 × 4 コマ（512×2048） | `tools/build_terrain_assets.py` が**生成** |
| `app/src/main/res/drawable-nodpi/tile_path_cobblestone.png` | 石畳の床 | 同スクリプトが原画から切り出し |
| `app/src/main/res/raw/` | BGM 5 曲（タイトル・春・夏・秋・冬）、効果音 8 個（mp3） | 外部で用意されたもの |

### スプライトの内訳

- 作物 3 種 × 5 枚：`plant_{tulip,sunflower,rose}_stage{1..4}.png` と `_withered.png`
- 地面と建物（#40、`tools/prepare_building_art.py` で加工）：
  - `tile_grass`・`tile_grass_dark`（芝生。市松の 2 色。暗い方は同じ絵を 0.9 倍の明るさにしたもの）、`floor_tilled`（耕した土）
  - `struct_cottage`（コテージ）、`floor_greenhouse`（ガラスの温室）、`struct_windmill`（風車の本体）、`struct_windmill_blades`（風車の羽根）
- 作物 4 種 × 4 枚（#39）：`plant_{tomato,strawberry,paprika,daikon}_stage{1..3}.png` と `_withered.png`（3 段階）
  - 受け取ったときは `res/drawable/crop_*.png` に置かれていた。アトラスに詰めるため `tools/raw_assets/` へ移し、`plant_<作物>_…` に改名した。
    大根は受け取ったファイル名が `crop_radish_*`（`radish`）だったので `plant_daikon_*` に、枯れの 1 枚は `rop_radish_withered.png`（先頭の c が欠けていた）だったので直した。
  - どれも 1536×768、背景透明。トマトの絵には切り抜きの名残の白い筋（根のような線）が土の下に残っている。消してはいない。
  - 大根の 3 段階目は葉が横に大きく広がっていて（切り抜き後 1496px＝約 2 マス分）、となりのマスに重なって見える。
- フェンス 3 枚：`struct_fence.png`（単体の柵）、`struct_fence_post.png`（支柱）、`struct_fence_rail.png`（現在は未使用）

原画は `tools/raw_assets/`。ファイル名（拡張子なし）がそのまま assetId になる。

### 画像が無く、プログラムで描いているもの

岩、芝生（地面に置く草の飾り）、小石、野の花、ウッドデッキ、ベンチ、石どうろう、井戸、小屋、
フェンスの横木と杭、工事現場（シート・足場）、木枠、背景（空・丘・茂み）、影、風の筋。
すべて `ui/components/GardenCanvas.kt` と `ui/effects/GardenBackdrop.kt` にある。
絵を用意できれば、同じ assetId でアトラスに入れるだけで差し替わる（スプライトがあればそちらを優先して描く）。

絵が無く、仮の絵を描いている作物と生き物の assetId:

- 作物（コスモス・スノードロップ・ミント・スイカ・ラベンダー・カボチャ・ポインセチア）：`<objectId>_stage1`〜`_stage<N>` と `<objectId>_withered`
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
| `building_cottage.png` | コテージ（#40）。透過はきれいだった。切り抜いて 3 マス分の幅で描く（描画倍率 約 2.5）。土台が 2:1 より平たいので、#41 から土台の左右の角を 3×3 の左右の角にそろえて中央に置く（`prepare_building(..., centred=True)`、anchorY 0.7975）。前の角は区画の前の角より少し手前で止まり、芝生がのぞく |
| `building_greenhouse.png` | ガラスの温室（#40）。ガラスは半透明。土台が 1 マスになるよう縮小 |
| `building_windmill_body.jpg` | 風車の本体（#40）。**JPEG で透過が無く、灰色の市松模様（48px、2 トーン）が背景として描き込まれていた**。マスごとの色で抜いた |
| `building_windmill_blades.jpg` | 風車の羽根（#40）。同じく白と灰色の市松模様（約 12px）と、白いにじみが描き込まれていた。色で抜いた |
| `tile_grass_default.png` | 芝生のタイル（#40）。縁に白いにじみ。1 マス幅に縮め、上面が 2:1 のひし形になるよう縦に伸ばした（元は約 2.35:1） |
| `tile_dirt_plowed.png` | 耕した土のタイル（#40）。縁に白いにじみと、右上に青い点（ゴミ）。同様に加工（元の上面は約 2.7:1） |
| `tile_river_atlas.png` | **未使用**。4×4 に並んでいるが、16 種のつながり方に対応した並びではなく、隣と継ぎ目が合わず、背景も不透明だった。色だけ参考にして、川は生成している |

## 加工の手順

Python 3.12（Pillow、numpy）。パスは `AI_WORKFLOW.md` を参照。

```bash
# 川・石畳・フェンスの部品を作り直す
python tools/build_terrain_assets.py
```

```bash
# スプライトをアトラスに詰め直す（この引数の組み合わせを変えないこと）
python tools/asset_packer.py tools/raw_assets app/src/main/assets/atlas --tile-width 512 --source-tile-width 720 --alpha-threshold 8 --overrides tools/atlas_overrides.json --max-width 4096
```

- 原画は **1 タイル 720px** の縮尺で描かれている。アトラスは 512px に縮めて軽くしている。
- `--max-width 4096`（#40 から）：アトラスの縦横どちらも 4096px 以内に収めるため。幅 2048 のままだと縦が 4733px になり、
  4096px までしか扱えない端末で描けなくなるおそれがあった（`BuildingArtTest` が 4096 以内を見ている）。
- `atlas_overrides.json` の追加の鍵（#40）：`scale`（描画倍率。アトラスでは拡大せず、描くときに大きくする）と
  `attachX`/`attachY`（別のスプライトを取り付ける点。風車の羽根の軸）。マニフェストには `drawScale`・`attachX`・`attachY` として出る（使うときだけ）。

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
  作物は土の盛り上がりが絵に含まれるので、アンカーを 0.77〜0.93 あたりに上げてある。
  新しい絵を入れたら、まずここを調整する。浮いて見える原因はたいていアンカー。
- 作物のアンカーの目安は `python tools/estimate_crop_anchors.py tools/raw_assets tools/atlas_overrides.json <assetId>...` で出せる（#39）。
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
- 作物は成長 4 段階＋枯れ 1 枚。段階ごとに大きさが変わってよい（アンカーで合わせる）。
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
| Recraft AI | 未記入（`asset_packer.py` のコメントに「Recraft.ai から書き出した PNG」とだけある） | 未記入 |
| Nano Banana | 未記入 | 未記入 |
| その他 | 未記入 | 未記入 |

### プロンプトとデザイン方針

- 作物（チューリップ・ひまわり・バラ）のプロンプト：未記入
- タイル（川・石畳・フェンス）のプロンプト：未記入
- 共通の画風指定（線、塗り、パレット、視点）：未記入
- 背景透過や余白の取り方などの後処理：未記入

### キャラクター「たこパイセン」

このキャラクターは、アプリのコードにも素材にも、これまでの開発スレッドにも登場していません。

- 設定・役割（ゲーム内のどこに出るのか）：未記入
- デザイン方針とプロンプト：未記入
- 素材の置き場所：未記入
