# アセット（画像・音）

> **このファイルには未記入の欄があります。**
> 画像の生成に使ったツールの設定、プロンプト、キャラクター「たこパイセン」のデザイン方針は、
> これまでの開発スレッドでは共有されておらず、リポジトリにも記録がありません。
> 推測で書くと将来の作業を誤らせるため、該当箇所は「未記入」としています。
> 生成を担当した方が追記してください（[追記してほしいこと](#追記してほしいこと)）。

## いま同梱されているもの

| 置き場所 | 中身 | 作り方 |
|---|---|---|
| `app/src/main/assets/atlas/atlas.png` + `assets_manifest.json` | スプライト 18 枚を 1 枚に詰めたもの（2043×1686、1 タイル 512px） | `tools/asset_packer.py` |
| `app/src/main/res/drawable-nodpi/tile_river_autotile.png` | 川。16 種のつながり方 × 4 コマ（512×2048） | `tools/build_terrain_assets.py` が**生成** |
| `app/src/main/res/drawable-nodpi/tile_path_cobblestone.png` | 石畳の床 | 同スクリプトが原画から切り出し |
| `app/src/main/res/raw/` | BGM 1 曲、効果音 8 個（mp3） | 外部で用意されたもの |

### スプライトの内訳

- 作物 3 種 × 5 枚：`plant_{tulip,sunflower,rose}_stage{1..4}.png` と `_withered.png`
- フェンス 3 枚：`struct_fence.png`（単体の柵）、`struct_fence_post.png`（支柱）、`struct_fence_rail.png`（現在は未使用）

原画は `tools/raw_assets/`。ファイル名（拡張子なし）がそのまま assetId になる。

### 画像が無く、プログラムで描いているもの

岩、芝生、小石、野の花、ウッドデッキ、ベンチ、石どうろう、井戸、小屋、耕した土、
フェンスの横木と杭、工事現場（シート・足場）、木枠、背景（空・丘・茂み）、影、風の筋。
すべて `ui/components/GardenCanvas.kt` と `ui/effects/GardenBackdrop.kt` にある。
絵を用意できれば、同じ assetId でアトラスに入れるだけで差し替わる（スプライトがあればそちらを優先して描く）。

## 原画（`tools/source_art/`）

ユーザーから受け取った元画像。アプリには入らない。

| ファイル | 状態 |
|---|---|
| `item_fence_wood.png` | 正面向きのパーツ集。柵・支柱・横木を切り出して使用 |
| `tile_path_cobblestone.png` | 石畳。そのまま使用 |
| `tile_river_atlas.png` | **未使用**。4×4 に並んでいるが、16 種のつながり方に対応した並びではなく、隣と継ぎ目が合わず、背景も不透明だった。色だけ参考にして、川は生成している |

## 加工の手順

Python 3.12（Pillow、numpy）。パスは `AI_WORKFLOW.md` を参照。

```bash
# 川・石畳・フェンスの部品を作り直す
python tools/build_terrain_assets.py
```

```bash
# スプライトをアトラスに詰め直す（この引数の組み合わせを変えないこと）
python tools/asset_packer.py tools/raw_assets app/src/main/assets/atlas --tile-width 512 --source-tile-width 720 --alpha-threshold 8 --overrides tools/atlas_overrides.json
```

- 原画は **1 タイル 720px** の縮尺で描かれている。アトラスは 512px に縮めて軽くしている。
- `--alpha-threshold 8`：ごく薄い縁を余白として切り落とす。
- `tools/atlas_overrides.json`：スプライトごとの**接地点**（アンカー）。既定は下端中央。
  作物は土の盛り上がりが絵に含まれるので、アンカーを 0.77〜0.93 あたりに上げてある。
  新しい絵を入れたら、まずここを調整する。浮いて見える原因はたいていアンカー。

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

- `bgm_garden.mp3`、`se_{water,fertilize,plant,harvest,coin,level_up,error,tap}.mp3`。
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
