# ストア画像（tools/store_art）

Play ストアのフィーチャーグラフィック（1024×500）とスマホのスクリーンショット（1080×1920）を作る。
背景（空のグラデーション・紙の質感・ぼけた光の粒・端の葉と花びら・木のテーブル）はすべてコードで描く。
文言・色・使うゲーム画面は `config.yaml` で変えられる（季節やイベントの差し替え用）。

## 手順

```bash
export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
timeout 560 ./gradlew assemblePromo assembleDebug --console=plain
# 1. 庭の切り抜き（フィーチャーグラフィック）：撮影モードで背景なし（start.backdrop: none）
python tools/promo_shoot.py S-001 --install
# 2. UI 付きのゲーム画面（スクショ）：デバッグ版の見本の庭（--es garden spring）を 1080×1920 で
python tools/store_art/capture.py
# 3. 合成
python tools/store_art/store_art.py feature --variant B --out feature_graphic.png   # 採用：木のテーブル
python tools/store_art/store_art.py shots --frame A --final                          # 採用：黒い縁の枠。screenshot_01〜06.png
# 案の比較用：feature（A・B の両方）、shots --frame A B（screenshot_NN_A / _B）
```

出力は `docs/release/store/`（PNG・24 ビット RGB・透明なし）。

## 決まりごと

- エミュレーター（`emulator-5554`）だけを使う。アプリの言語は `cmd locale set-app-locales ... ja-JP` で日本語にする（端末が英語のままだと漢字が中国語の字形になる）。
- 見本の庭（デバッグ版）では、右の列の工具のボタンとズームの表示を出さない（リリース版と同じ並び：雲・図鑑・編集・カメラ・ポスト・設定）。
- エミュレーターのスクリーンショットは数秒かかり、画面より遅れる。すぐ消えるもの（収穫の「+16」、止まった生き物）は画面を録画して 0.1 秒ごとのコマにし、`record.pick`（秒）のコマを使う。コマは `build/store_art/raw/<名前>_frames/` に残るので、選び直すときは `pick` を変える。
- フィーチャーグラフィックのことり・ちょうちょは庭の中にいるが、画像の中では 5 ピクセルほどで見分けられない。そのため一言は生き物に触れない（「花を育てて、のんびり庭づくり」）。
- ステータスバーはデモモード（9:00・電池満タン・Wi-Fi のみ・通知なし）で撮る。
- 字体は `tools/fonts/NotoSansJP.ttf`（アプリが使う Android の日本語字体と同じ Noto Sans CJK 系）。文字色はアプリの `GardenColors.Ink`（こげ茶）、後ろにクリーム色のにじみ。
- 撮影モードの背景なしの書き出しは、時間帯の光（朝は暖色、不透明度 約 10%）が画面全体に薄く乗る。`cut_out_garden` が庭の外の分だけ取り除く。
- 写さないもの：小川・太鼓橋・温室・白菜・菜の花・風見鶏・竹垣・15×15 以降の広さ。宣伝文句・星・バッジ・他社ロゴ・実在の機種とわかる端末。
- フィーチャーグラフィックの庭とロゴは、上 15%・左右 20%・下 20% の外に出さない。真ん中に細かい文字を置かない。
- スクショの 6 枚は 1 本の横長の背景（`panorama`）を切り分けて作るので、並べると背景がつながる。下の色は春→冬へ少しずつ移る（`screenshots.tints`）。
