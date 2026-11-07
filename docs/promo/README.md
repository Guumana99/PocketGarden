# 宣伝用の画像と動画（X・ストアのスクリーンショット用）

このフォルダの画像と動画は、エミュレーター（`emulator-5554`）でデバッグビルドを動かして撮ったもの。
ビルドには入らない（`ASSET_LEDGER.md` にも載せない）。作り直しは `tools/promo_capture.py` 1 本でできる。

## 中身

| ファイル | 内容 |
|---|---|
| `garden_day_autumn.png` | 秋・昼・晴れ。庭の全体 |
| `garden_evening_autumn.png` | 秋・夕方・晴れ |
| `garden_night.png` | 秋・夜・晴れ（家の窓に明かり） |
| `garden_rain.png` | 秋・昼・雨 |
| `garden_close_crops.png` | 作物の寄り（ひまわり・トマト・コスモス） |
| `*_16x9.png` | 上の 5 枚の横 16:9 版（1600×900） |
| `butterfly.mp4` | ちょうちょが手前から飛んで来て、コスモスのまわりを漂い、去るまで（15 秒、1600×900） |
| `rain.mp4` | 雨の庭（7 秒、1600×900） |
| `contact_sheet.jpg` | 静止画の一覧（縮小して並べたもの。確認用） |

- 縦の画像は端末そのまま（1080×2424）。横は、画面を一時的に 2400×1350 にして撮り、1600×900 に縮めたもの（切り抜きではないので、横は横の構図で撮れている）。
- 動画は H.264（High / yuv420p / 30fps / 音なし / faststart）。X にそのまま載せられる形。
  エミュレーターの描画は 1 秒 12〜14 コマほどなので、30fps にしたぶんは同じコマの繰り返し。
- 画面上部の数字（コイン・Lv・XP）・ボタン・システムバーは写さない。生き物はちょうちょだけ（他は来ないようにしている）。

## 作り直す

```bash
export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
timeout 560 ./gradlew assembleDebug --console=plain
python tools/promo_capture.py --sheet docs/promo/contact_sheet.jpg
```

- エミュレーター `emulator-5554` が立ち上がっていること（ウィンドウ付き・`-gpu host` の方が動画が滑らか）。USB の実機には触らない。
- 動画の切り出しには ffmpeg を使う。PATH に無ければ `pip install imageio-ffmpeg` で入る（同梱の ffmpeg を使う）。
- 一部だけ撮り直す：`python tools/promo_capture.py --no-install --only garden_night.png rain.mp4`
- **保存されている庭は見せる用の庭に置き換わる**（エミュレーター専用）。
- 撮り終えると画面サイズを元に戻し、固定した見た目も解除する。

構図（倍率・寄る位置）、時間帯・天気、動画の長さと切り出す位置は、スクリプトの `STILLS` と `CLIPS` で決めている。

## 仕組み（デバッグビルドだけ）

- **見せる用の庭**：`app/src/debug/.../debug/ShowcaseGarden.kt`。11×11、Lv.5 までの作物 10 種（段階はばらばら、枯れは無し）と、
  コテージ・井戸・池・ベンチ・岩・柵・石畳・野の花・小石。空いたマスと柵の下は芝。Lv.6 以降の物は置かない（コテージだけは例外。依頼の「家」）。
- **adb からの操作**：`app/src/debug/.../debug/ShowcaseReceiver.kt`（`app/src/debug/AndroidManifest.xml` で登録）。

  ```bash
  # 見せる用の庭を読み込む（アプリは止めておく。そのあと起動して「つづきから」）
  adb -s emulator-5554 shell am broadcast -n com.studioguu.miniaturegarden/.debug.ShowcaseReceiver -f 0x20 --ez load true
  # 見た目を固定する（送るたびに全部を設定し直す。省いたものは庭のまま）
  adb -s emulator-5554 shell am broadcast -n com.studioguu.miniaturegarden/.debug.ShowcaseReceiver \
      --es season AUTUMN --es time EVENING --es weather SUNNY --ez hide_ui true --es creatures none \
      --ef scale 0.3 --ef pan_x 0 --ef pan_y 0
  # ちょうちょを今すぐ呼ぶ（庭の外から来て、約 20 秒で去る）
  ... --es creatures none --ez butterfly true
  # 解除
  adb -s emulator-5554 shell am broadcast -n com.studioguu.miniaturegarden/.debug.ShowcaseReceiver --ez clear true
  ```

  `time` は MORNING / DAY / EVENING / NIGHT、`weather` は SUNNY / CLOUDY / RAINY / SNOWY、
  `creatures` は none / all / `butterfly,bird` のような並び。
- **固定のしくみ**：`app/src/main/.../ui/debug/Showcase.kt` の `ShowcaseControls`。見た目（季節・時間帯・天気・生き物・視点・UI の表示）だけを差し替え、
  保存された季節・時計・天気の日記は変えない。`set` はデバッグビルド以外では何もしないので、リリースビルドでは常に空。
  リリース APK に入るのはこの空の受け皿だけで、見せる用の庭と受信口は入らない（`app/src/debug` にあるため）。

## 11 月の投稿用（`2026-11/`）

ファイル名の頭は投稿の ID。画像は横 16:9（1600×900）、動画は横 16:9（1600×900）と縦 9:16（1080×1920）の H.264。
確認用に `contact_sheet.jpg`（静止画の一覧）と `clips_sheet.jpg`（動画の最初・真ん中・最後のコマ）。

| ファイル | 内容 | 撮り方 |
|---|---|---|
| `X-010_tilling_*.mp4` | 荒れ地の 3 マスをクワで 2 回ずつたたいて耕した土、さらに 2 回ずつで畝（15 秒） | `garden till`（大根の列を空けた庭）＋ `hoe`、マスをタップ |
| `X-012_bird_before_16x9.png` | 直す前：ことりがいちごの株の上に乗って見える | 9fb5fc1 の 1 つ前を別の作業フォルダでビルド（下の「古いビルド」）、`promo_burst.py` で選ぶ |
| `X-012_bird_after_16x9.png` | 直した後：同じ庭・同じ構図で、ことりがいちごの奥に半分隠れる | `garden birdcrops` ＋ `bird 5,4`、`promo_burst.py` で選ぶ |
| `X-012_bird_roof_16x9.png` | ことりがコテージの屋根の棟に止まる | 見せる用の庭 ＋ `bird struct_cottage`、`promo_burst.py` で選ぶ |
| `X-014_tulips_16x9.png` | チューリップの花壇の寄り（赤・ピンク・黄だけ、全部咲いた段階） | `garden tulips` |
| `X-017_{morning,noon,evening,night}_16x9.png` | 同じ構図で朝・昼・夕方・夜の全体 | `time` だけを変える |
| `X-019_soil_16x9.png` | 何も植えていない畝を左から ふつうの土・よい土・ふかふかの土 | `garden soil` |
| `X-021_cottage_{1..4}_16x9.png` | コテージの色違い：赤/白（元の色）・青/クリーム・緑/水色・こげ茶/淡いピンク | `roof` / `wall`（屋根 4 色×壁 4 色＝16 通り） |
| `X-024_raincloud_*.mp4` | 右のアイコン列の雨雲をタップ → 確認 → 雨 → 作物がきらっと光る（15 秒） | `garden thirsty` ＋ `side_only` ＋ `skip_ad`、雲と「雨を降らせる」を探してタップ |
| `X-026_rain_button_{1,2,3}_16x9.png` | 雨乞いのボタンの 3 段階：茶色の文字ボタン／空の雨雲／右の列の雲 | 1 は 979e699 の 1 つ前、2 は 2be8292 の 1 つ前を別の作業フォルダでビルド、3 は今の `side_only` |
| `X-028_snow_16x9.png` | 冬・雪の日の一角（作物の上と地面の雪。屋根には積もらない作り） | `season WINTER` ＋ `weather SNOWY` |
| `X-005_butterfly_9x16.mp4` / `X-007_rain_9x16.mp4` | 前回の `butterfly.mp4`・`rain.mp4` の縦版（寄った構図） | |

```bash
# 11 月の分を全部（一部だけなら --only ファイル名 ...）
python tools/promo_capture.py --set 2026-11
```

構図・庭・動画の長さは `promo_capture.py` の `NOVEMBER` にまとめてある（X-012 と X-026 の 1・2 を除く）。

### 横の画面について

アプリは縦に固定してあるので、そのまま画面を 2400×1350 にすると左右に黒い帯が出る（API 37 のエミュレーター）。
見せる用に UI を隠している間（`hide_ui` / `side_only`）だけ、縦の固定を外して横いっぱいに描く（`GardenScreen` の `hideBars`）。
向きが変わると画面が作り直され、クワが手から離れるので、スクリプトは先に UI を隠す見た目だけを送って向きを変えさせ、
落ち着いてから本番の見た目（クワなど）を送る。

### 足した受信口の指定（`ShowcaseReceiver`）

| 指定 | 内容 |
|---|---|
| `--es garden till` / `soil` / `tulips` / `thirsty` / `birdcrops` | 見せる用の庭の変種（読み込み時）。`ShowcaseGarden` を参照 |
| `--es roof blue --es wall cream` | コテージの色（読み込み時、`BuildingPaint` の id） |
| `--ez hoe true` | クワを持つ（UI を隠したままタップで耕せる） |
| `--es bird struct_well` / `struct_cottage` / `5,4` | ことりを今すぐ呼ぶ（建物へ、または地面のマスのまわりへ） |
| `--ez side_only true` | 操作のうち右のアイコン列だけを出す（上の数字・下のツールバー・デバッグのボタン・お知らせの招待やレベルアップは出さない） |
| `--ez skip_ad true` | 雨雲からの雨を広告なしで降らせ、その日の回数にも数えない（デバッグビルドのみ） |

### 一瞬を選ぶ（ことり）

ことりの位置は来るたびに変わるので、続けて撮った中から選ぶ。

```bash
python tools/promo_burst.py --garden birdcrops --view 2.6 5 4.5 0 --count 30 --gap 0.5 --out app/build/promo/bird -- --es creatures bird --es bird 5,4
python tools/promo_burst.py --view 1.4 1 1 520 --count 18 --gap 1 --out app/build/promo/roof -- --es creatures bird --es bird struct_cottage
```

`sheet.jpg` から選んだ `bNN.png`（2400×1350）を 1600×900 に縮める。

### 古いビルド（直す前の絵）

```bash
git worktree add --detach ../PocketGarden-promo-old 9fb5fc1^       # X-026 の 1 は 979e699^、2 は 2be8292^
cp local.properties ../PocketGarden-promo-old/
python tools/promo_old_build.py ../PocketGarden-promo-old --bird   # X-026 は --bird なし
(cd ../PocketGarden-promo-old && ./gradlew assembleDebug)
adb -s emulator-5554 install -r -d ../PocketGarden-promo-old/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm clear com.studioguu.miniaturegarden   # DB が古いので（エミュレーターだけ）
```

`promo_old_build.py` は撮影のためだけの手直し（コミットしない）：見せる用の庭を今のものに差し替え、横いっぱいに描く。
`--bird` なしでは `hide_ui` で上の数字・下のツールバー・デバッグのボタンだけを隠す（雨乞いのボタンを写すため）。
撮り終えたら今のビルドを入れ直し、同じく `pm clear` する。
