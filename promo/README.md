# TikTok 撮影モード（promo ビルド）

台本（`promo/scenarios/*.yaml`）どおりにアプリの庭を動かして、1 コマずつ画像に書き出し、
縦動画（1080×1920、30 コマ）に仕上げるしくみ。実時間で録画しないので、エミュレーターが遅くてもカクつかない。

## しくみ

| 部分 | 場所 | 内容 |
|---|---|---|
| 撮影用のビルド | `app/build.gradle.kts` の `promo`、コードは `app/src/promo/` | `applicationId` は `com.studioguu.miniaturegarden.promo`。ふつうの版と同じ端末に入り、保存先も別。リリース版（AAB）には `src/promo/` は入らない |
| 時計の差し替え口 | `app/src/main/.../ui/components/GardenClock.kt` | 庭の描画（`GardenCanvas`）が使う時刻。ふだんは端末の時計。撮影中は 1 コマずつ進める時計 |
| 生き物の行き先 | `CreatureRoutes.plan(..., direction)`（`CreatureDirection`） | 止まるマス・入ってくる方向・止まる秒数。ゲームでは渡さない（動きは前と同じ） |
| 撮影 | `tools/promo_shoot.py` | 台本を JSON にして送る → 撮影 → 画像を `build/promo/<id>/` に取ってくる |
| 編集 | `tools/promo_render.py` | 画像＋効果音＋BGM＋字幕＋締めの画面 → `docs/promo/tiktok/<id>_<name>.mp4` と切り抜き一覧 |

撮影中は、庭の Compose を専用の時計（`BroadcastFrameClock`）で 1 コマずつ動かし、`GraphicsLayer.toImageBitmap()` で 1080×1920 の PNG にする。
風・川・生き物・耕したときの土・消える物のアニメーションは全部この時計で進む。
ゲームの画面（タイトル・庭・ショップ）は開かないので、広告・通知・チュートリアル・レビューのお願いは出ず、セーブデータも読み書きしない。
UI（ボタン）は映らない（`ui: shown` は未対応）。雨粒は描かない（天気の色と、雨のあとのきらっだけ）。

## 必要なもの（Windows）

- Android SDK（`adb`）とエミュレーター `emulator-5554`。撮影はエミュレーターだけで行う（実機には触らない）。
- Python 3.12 と `pip install pyyaml pillow imageio-ffmpeg`
  （ffmpeg は `imageio-ffmpeg` が持っている物を使う。`winget install Gyan.FFmpeg` で入れて PATH にあれば、そちらを先に使う）
- フォント：`tools/fonts/NotoSansJP.ttf`（Noto Sans JP、SIL Open Font License、`tools/fonts/OFL.txt`）。アプリには入れない。

## 使い方

```bash
export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
timeout 560 ./gradlew assemblePromo --console=plain
python tools/promo_shoot.py T-001 --install   # 撮影（2 回目からは --install なしでよい）
python tools/promo_render.py T-001            # 編集
```

台本は JSON にして base64 でインテントに入れて渡す（`promo_shoot.py` がやっている）。手で起動するなら

```bash
adb -s emulator-5554 shell am start -n com.studioguu.miniaturegarden.promo/com.studioguu.miniaturegarden.promo.PromoActivity --es scenario_b64 <台本の JSON を base64 にしたもの>
```

adb でアプリのフォルダーにファイルを置く方法は使わない（そのファイルとフォルダーはシェルの持ち物になり、アプリが読めない・書けないことがある）。
書き出し先は `/sdcard/Android/data/com.studioguu.miniaturegarden.promo/files/promo/out/<id>/`（`frame_00000.png` …、`cues.json`、最後に `done`）。失敗したときは `files/promo/error.txt`。
PC に取り込んだら、エミュレーター側の `out/<id>/` は消す（エミュレーターの空きは少なく、20 秒の撮影で約 0.7GB。残っていると次の撮影の途中で `ENOSPC` で落ちる）。
Git Bash から adb に `/sdcard/...` を渡すときは `MSYS_NO_PATHCONV=1` を付ける（付けないと Windows のパスに書き換わる）。

## 台本の書き方（`promo/scenarios/<id>.yaml`）

```yaml
id: T-001
name: flower_garden      # 出来上がりのファイル名 T-001_flower_garden.mp4
title: 花の庭（荒れ地から完成まで）
fps: 30
seed: 42                 # 同じ seed なら毎回同じ動き（生き物の飛び方など）
hook_from_end: 0         # 最後の何秒を頭にも付けるか（初期値 0 ＝付けない）
start:
  garden: new_game       # 新規ゲームの庭（11×11 の荒れ地）。今はこれだけ
  season: spring_early   # spring / summer / autumn / winter（_early などは付けてよいが、絵は季節だけで決まる）
  time_of_day: morning   # morning 7.5時 / day 12時 / afternoon 15時 / evening 17.8時 / night 21.5時 / 数字（時）
  weather: sunny         # sunny / cloudy / rainy / snowy
  ui: hidden             # hidden のみ
  tap_marker: true       # タップ位置の丸
camera: { zoom: fit }    # zoom は「庭全体が入る倍率」の何倍か。fit = 1
timeline:                # at は録画の秒（頭に付ける完成形は含まない）
  - { at: 0.5, till: { area: [3,3,7,7], over: 3.0 } }
```

### 録画の前に済ませておく（`setup:`）

録画の 0 秒より前に済ませておく動きの一覧。書き方は `timeline` と同じで、`at` は要らない（書いても無視）。
上から順に、全部いっぺんに行う（`over` は無視）。音・タップの丸・アニメーションは出ず、0 秒の時点で出来上がった姿から録画が始まる。
例：家が建っていて花が咲いている庭から始める（T-003）。

```yaml
setup:
  - { place: { item: struct_cottage, at: [4,2] } }
  - { plant: { crop: tulip, area: [2,6,8,8], colours: [red, pink, yellow] } }
  - { grow: { area: [2,6,8,8], to_stage: 3 } }
```

使えるのは 耕す・植える・育てる・収穫・置く・取り除く・天気・季節・時間帯・カメラ・「◯日目」・タップの丸。
生き物・きらっ・効果音だけ・終わり は見えている間にしか意味がないので、setup では行わない（警告を出して飛ばす）。

マスの指定はどの動きも同じ：`cells: [[x,y], ...]` か `area: [x1,y1,x2,y2]`（両端を含む。1 行ずつ、行ごとに向きを変えて順に）。
`over` はその秒数をかけて 1 マスずつ順に行う（省くと同時。植えるだけは 1 マス 0.08 秒、最大 1 秒）。

| 動き | 書き方 | 備考 |
|---|---|---|
| 耕す | `till: { area, over, ridged: false }` | 打ったマスの土が跳ね、0.25 秒後に耕した土（`ridged: true` で畝）。耕した土にもう一度 `ridged: true` で畝になる |
| 植える | `plant: { crop: cosmos, cells/area, over, colour / colours: [...], dry: true }` または `plant: { crops: [cosmos, tulip, lavender, sunflower], area, colours: { tulip: [red, pink, yellow] } }` | 土が無ければ作る（花は耕した土、ほかは畝）。`crops` は混ぜて植える：マス (x, y) は crops[(x + 2y) % 種類数]（隣どうしが別の花になる）。色は順に使う。`dry: true` で乾いた株にする（ゲームで乾いた株に出る青いしずくの印が出て、水やりで消える。ゲームでは植えた直後の株は水が満タンなので、印は出ない） |
| 水やり | `water: { area, over }` | タップの丸と音。乾いた株（しずくの印）は印が消える（ゲームと同じ。土の色は変わらない） |
| 育てる | `grow: { area, to_stage: 2, over }` | 段階は 1 から。最後の段階が咲いた・実った姿。咲いても音は鳴らない（`sound: se_xxx` で付けられる） |
| 収穫 | `harvest: { cells/area, over }` | ゲームと同じ：1 回だけの作物は株が浮いて消える。くり返し実る作物（いちご・トマトなど）は、旬のあいだは株が残って段階 2 に戻る（旬を過ぎていれば消える）。画面の真ん中に「+16 🪙」（その収穫のコイン）が浮かぶ（2.6 秒、次の収穫で入れ替わる） |
| 置く | `place: { item: deco_bench, at: [x,y], flip: false }` | 物の id は `GardenItem` のもの |
| 取り除く | `remove: { cells/area, over }` | 土以外 |
| 生き物 | `creature: { kind: butterfly, count: 2, from: outside_left, land_on: [[4,3],[6,4]], stay: 3.0 }` | kind：butterfly / ladybug / bird / squirrel / frog / dragonfly / rabbit / snail（#book60）。うさぎは `coat: white` で白い冬毛。from：outside_left / outside_right / outside_top / outside_bottom / 角度。land_on の n 番目のマスに n 匹目が止まる（0.5 秒ずつ遅れて来る）。**at は降り立つ秒**：飛ぶもの（ちょうちょ・とんぼ・ことり）は庭の外の空からその分前に飛び始める（ちょうちょは約 7.4 秒、ことり約 3.9 秒、とんぼ約 3 秒前。画面に入るのは 1〜2 秒前）。`from_distance: 6`（ちょうちょ・とんぼのみ）で、何マス先から飛び始めるかを短くできる（速さはそのまま。6 マスでちょうちょ約 3.8 秒）。引きの画面では遠くからの飛来が空に小さく長く見えるので、寄った画面に画面の外から入らせたいときに使う。stay の後は庭の外へ飛び去る（ゲームと同じ距離・速さ）。ことり（bird）の land_on のマスにコテージか小屋が置いてあれば、その屋根の棟に止まる（ゲームでその建物目当てに来たときと同じ。コテージの棟は高さ 1.75 なので、寄るときは center の 3 つ目を 1.75 に）。屋根の上では、棟の 2 点のあいだを横にちょんちょんと動いて向きを変える（ゲームと同じ）。飛び方・羽ばたき・振り向きはゲームのまま |
| 天気 | `weather: rainy` | |
| 時間帯 | `time_of_day: evening` または `time_of_day: { to: evening, over: 2.0 }` | |
| 季節 | `season: autumn` | |
| カメラ | `camera: { zoom: 1.6, center: [5,5], over: 1.0 }` | zoom は fit（庭全体）・倍率・`max`（ゲームで寄れる最大＝`GardenCamera.MAX_SCALE`、1080×1920 では fit の約 10 倍）。center を省くと庭の真ん中。center の 3 つ目は地面からの高さ（タイル幅。花に止まったちょうちょは約 0.6） |
| 「◯日目」 | `day_label: "3日目"`（`day_label: ""` で消す）。`"ポケ庭日記 1日目
春のはじめ・晴れ"` のように `
` で 2 行目を書ける | 左上（上の端は避けている）に 0.25 秒で出る。アプリの札と同じ羊皮紙色（`GardenColors.Parchment`）の角の丸い板に、こげ茶の文字（`Ink`・2 行目は `InkSoft`）、少し影。字幕とは見た目を分けている。1 行目は大きく（36sp）、2 行目は小さく（22sp）。1 行目が同じで 2 行目だけ変わるときは、板はそのままで 2 行目が 0.6 秒でふわっと切り替わる。出てから 3 秒はこの大きさ、そのあと 0.5 秒で 7 割の大きさ・不透明度 0.75 の控えめな札に縮んで左上の角へ寄る。2 行目が変わるときは 0.3 秒で元の大きさに戻って 2 秒見せ、また縮む |
| タップの丸 | `tap_marker: false` | |
| 札を薄く | `label_opacity: { to: 0.3, over: 0.3 }`（`to: 1.0` で戻す） | 生き物が札の裏を通るあいだなど。札の不透明度にさらに掛ける |
| きらっ | `sparkle: { over: 1.2 }` | 雨のあとのきらめき（全部の作物） |
| 効果音だけ | `sound: se_coin` | |
| 終わり | `end: true` | ここで録画を終える（無ければ最後の動きの 1 秒後） |
| UI | `ui: shown` | 未対応（警告を出して無視） |

どの動きにも `sound: se_xxx`（または `none`）を書くと、その動きの効果音を変えられる。

### 効果音の合図（`cues.json` の `sounds`）

アプリの `res/raw` の音を使う。同じ音が近すぎるときは最初の 1 つだけ（耕す 0.12 秒・植える 0.15 秒・水 0.4 秒・収穫 0.15 秒・きらっ 0.3 秒）。

書き出し（`promo_render.py`）では、さらに間引く：同じ種類の音は 1 秒に 1 回まで。耕す・植える・水やり（`se_tap` `se_plant` `se_water`）は、それぞれ最初の 2 回だけはっきり鳴らし、あとは 6dB 小さく（`SAME_SOUND_GAP` `CLEAR_REPEATS` `REPEAT_QUIET_DB`）。T-001 では 34 個のうち 7 個が残る。

| 動き | 音 |
|---|---|
| 耕す | `se_tap` |
| 植える・置く | `se_plant` |
| 水やり・雨になる | `se_water` |
| 収穫 | `se_harvest` |
| 咲く（最後の段階になる）・きらっ | なし（アプリの音に合うものが無い） |

アプリの効果音（`app/src/main/res/raw`、ゲームで鳴る場面）：

| ファイル | ゲームで鳴る場面 |
|---|---|
| `se_tap` | クワで固い地面を打つ・スプリンクラーを止める・おくりものの箱を開ける |
| `se_water` | 水やり・スプリンクラーを動かす・枯れた株を生き返らせる・恵みの雨 |
| `se_fertilize` | 肥料 |
| `se_plant` | 種をまく・物を置く・動かす・向きを変える・持ち物に戻す |
| `se_harvest` | 収穫 |
| `se_coin` | ショップで買う・ごほうびのコイン・設定の音量の試し聞き |
| `se_level_up` | レベルアップ・季節が変わる・建物のお披露目・庭が広がる・チュートリアルの区切り |
| `se_error` | できない操作 |

## 字幕（`promo/captions/<id>.yaml`）

```yaml
captions:
  - { from: 0.3, to: 3.4, text: "荒れ地を耕して" }
  - { from: 12.2, to: 16.8, text: "ちょうちょが\n遊びに来た！" }   # \n で改行
```

秒は本編の秒（頭に完成形を付けるときは、その分を編集でずらす）。1 行 15 字・2 行まで（超えると警告）。
字の大きさは 74px。1 行が安全な幅（約 820px）に収まらないときは自動で小さくする。
白い文字に濃い茶の縁取り。画面の上の 12%・下の 20%・右の 190px には置かない。

## BGM

台本（`promo/scenarios/<id>.yaml`）か字幕ファイル（`promo/captions/<id>.yaml`）の一番上に書く（両方にあれば字幕ファイルが勝つ）。
撮影には関係しないので、字幕ファイルに書くのがよい。

```yaml
bgm: パステルハウス.mp3   # tools/bgm/ のファイル名
bgm_start: 4.0            # 曲の何秒から使うか
```

- `bgm_start` は、曲の頭に無音や静かな前奏があれば飛ばして、メロディの始まる所にする（動画の最初の 1 秒から曲が聞こえるように）。
- 動画の頭で 0.2 秒のフェードイン、最後の 1.5 秒（締めの画面）でフェードアウト。
- BGM が主役。音量は自動で合わせる：はっきり鳴らす効果音が鳴っている間の平均（50ms ごとの RMS、いちばん大きい所から 30dB 以内）が、曲全体の平均より 5dB 小さくなるようにする。
  効果音が鳴る瞬間に曲を下げる処理（ダッキング）はしない。書き出しのときに `bgm: ... gain -4.8 dB` のように数値を出す（値は `promo_render.py` の `SE_UNDER_BGM_DB`）。
- 最後に音全体（効果音＋BGM）を 1 つの倍率で上げ下げして、ラウドネス -14 LUFS にそろえる（効果音と BGM の比率は変わらない）。ピークはリミッターで -2dB に抑え、mp4 でトゥルーピーク -1dB 以下を確かめる（超えたら警告。値は `promo_render.py` の `TARGET_LUFS` `PEAK_CEILING_DB` `LIMIT_DB`）。BGM が無いときも同じ。
- 曲は切り出し・音量・フェード以外の加工をしない（規約でアレンジ禁止。速さ・音程も変えない）。44.1kHz への変換だけは書き出しの都合で行う。
- 曲のファイル（`tools/bgm/`）は Git に入れない（配布にあたるため。作業する PC にだけ置く）。アプリにも入れない。

### 使っている曲

どちらも OpenTracks（https://opentracks.com/ ）の曲。

| 曲名 | 作曲者 | URL | ファイル | bgm_start | 使う動画 |
|---|---|---|---|---|---|
| パステルハウス | かずち | https://opentracks.com/bgm/detail/1021 | `パステルハウス.mp3` | 4.0 秒（0〜4.0 秒は静かな前奏。4.02 秒から音が大きくなる） | 早送りシリーズ（T-001） |
| Cat life | GT-K | https://opentracks.com/bgm/detail/2551 | `Cat_life.mp3` | 0.3 秒（頭の 0.3 秒は無音。すぐ曲が始まる） | 今日来た子・日記シリーズ |

## 締めの画面（`promo/outro.yaml`）

1.5 秒（`seconds`）。最後の場面をぼかして暗くした上に、大きく「ポケット庭園」（画面の幅に収まる大きさ）、下に小さく「（ポケ庭）」「Androidで年内配信予定」。文言・秒数・ぼかしはこのファイルで変える。
本編から 0.4 秒かけて切り替わる。

動画ごとに変えるときは、字幕ファイル（`promo/captions/<id>.yaml`）に `outro:` を書く。書いた項目だけが `promo/outro.yaml` の代わりになる。

```yaml
outro:
  title: つづく
  lines: [次回 ポケ庭日記 2日目, フォローで続きが届きます, ポケット庭園（ポケ庭）]
```

## 出来上がり

`docs/promo/tiktok/<id>_<name>.mp4`（1080×1920、30fps、H.264＋AAC 128kbps。BGM は指定したときだけ）と、
1 秒ごとの切り抜き一覧 `docs/promo/tiktok/<id>_sheet.jpg`。15MB を超えたら `--crf` を大きくする。
書き出した画像（`build/promo/`）は Git に入れない。
書き出すたびに、mp4 と切り抜き一覧を Google ドライブの `PocketGarden_Docs/promo/tiktok/` にもコピーする（rclone の `gdrive`。`tools/sync_docs_to_drive.ps1` と同じ設定。`--no-sync` で止める）。`sync_docs_to_drive.ps1` も、このフォルダの mp4・jpg を一緒に送る。
