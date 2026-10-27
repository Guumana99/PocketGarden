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
