# FB1122b ＋ 1.0.0 結果（2026-11-22）

feature/release-1.0.0 を main へマージ。versionCode 7 / versionName 1.0.0。
AAB: `app/build/outputs/bundle/release/app-release.aab`（52,527,200 バイト、アップロード鍵で署名、git には入れない）。
切り抜き: `docs/fb1122b/`。リリースノート案: `docs/release/release_notes_1.0.0.md`。

## 確認できていないこと
- 実機（Pixel 10a）での確認なし。エミュレーターのみ。
- ショップの「文字 1.3 倍」：ショップは端末の文字サイズ設定を使わず、自前の倍率（`ShopText.SCALE`＝1.1）で固定。端末を 1.3 にしても見た目は変わらず、そのまま確認した（窓の下端は画面の測定で決まるので、文字の大きさによらない）。
- 風車の羽根の「前後の切り抜き」：×0.5・×1.13・×1.3、左右反転の向きで、羽根が雪の手前にあるのを目で確認。動いている最中は未確認。
- release のはじめて起動：広告は、本番 ID に触れないよう、エミュレーターの Wi-Fi とデータを切って確認した（広告は出ていない）。終わったあと戻した。
- 全件テスト：6 件が落ちる（下）。すべて今回より前（main の c854339）から落ちていて、直していない。
- release の中に、デバッグ用の文言の「リソース」が少し残っている（`resources.arsc` に `debug_menu_title` など。コードは無く、画面には出ない）。

## 勝手に決めたこと
- 「茶色の壁」＝いちばん下の茶色の床と解釈。紫の帯（羽目板）を消し、品の窓の下端を床の上端（高さの 82%）にした。壁の縞は床まで伸ばした。
- 風車の屋根の雪は、屋根の多角形（絵の輪郭の外側は広めに取り、絵の透明部分が輪郭を決める。壁との境の軒の下の線だけ、絵から測った）で作り直した。軒の壁に接する縁は雪を 7 割に薄くした（風車だけ）。
- 風車の絵の穴は、屋根の欠けと壁の楔形の 2 か所だけ、周りの模様をコピーして埋めた。歩廊の端のぎざぎざ（絵の外形）は穴ではないので触っていない。反転した風車では、歩廊の端が雪の日に白っぽく見える所が残る。
- 穴を埋める処理は `tools/prepare_building_art.py`（`fill_holes`、`WINDMILL_HOLES`）に入れ、取り込みを流し直しても消えない。原画（`tools/raw_assets/struct_windmill.png`）と、取り込み後（アトラス `world_1`・アイコン）の両方を作り直し、`ASSET_LEDGER.md` を更新した。JPEG の原画（`tools/source_art`）は触っていない。

## release の確認（エミュレーター、release APK）
- versionCode 7 / versionName 1.0.0、`debuggable` なし。
- 無い：デバッグのアイコン（🛠）・開発者メニュー・×倍率の表示・時間送り（+1 時間／+1 日）・撮影モード（promo）の部品・テスト用の広告 ID。dex に `DebugMenuDialog` `DebugZoomReadout` `ShowcaseReceiver` `PromoActivity` `FeedbackGarden` `GroundGarden` `TextAudit` は無く、マニフェストにも無い。画面でも 🛠 と倍率は出なかった。
- 本番の広告 ID：アプリ ID・雨乞い枠・コイン枠が入っている（テスト用 `3940256099942544` は dex にもマニフェストにも無い）。release は本番 ID が空・テスト用だとビルドが止まる仕組みのまま。
- データを消した状態（アンインストール後に入れ直し）から、はじめる → 「やってみる」→ クワで畝 → ミントの苗 → 植える → 水 → 肥料 → 時間を進める → 収穫 → 「いよいよ本番！」→ お庭へ、まで通った。落ちなし（logcat に FATAL なし）。

## 全件テスト
1377 件、1371 件が通り、6 件が落ちた。落ちた 6 件は、今回の変更の前（main の c854339）でも同じ 6 件が落ちる（別の作業ツリーで確かめた）。
- `SoilTest`「vegetables go on ridges…」：作物が増えた（かぶ・さつまいも・水仙・葉牡丹）のに、期待値の一覧が古い。
- `ConnectedGroundTest`「every crop picture stands on the base…」：かぶの絵が増えたのに期待の一覧が古い。
- `WordingFb1110Test`「the book counts … 51 in all」：図鑑の数が増えている（52 以上）のに古い数。
- `SeasonsTest`「a greenhouse with something growing in it stays…」：温室の片づけ（連結式になった）の期待が古いと思われる（原因は詳しく見ていない）。
- `WordingAndToastTest`「each word is written one way」：文言のどれかが禁じた書き方を含む（どれかは見ていない）。
- `LevelDialogTest`「the bubble shows the level…」：Robolectric の UI で「表示されていない」（原因は見ていない）。
- 直したテストは無い。

## 画像
`docs/fb1122b/`：
- ショップ：`before_shop_sundries.png`（前、紫の帯あり）、`after_shop_{seeds,workshop,sundries}_end.png`（一番下まで）、`after_shop_sundries_top.png`
- 風車の雪：`before_snow_windmill.png`（前）、`after_snow_x113_front.png` `after_snow_x113_flipped.png`（反転）`after_snow_x05.png` `after_snow_x13_front.png`
- 雪の日以外の壁：`after_day_x113_front.png` `after_day_x113_flipped.png`
