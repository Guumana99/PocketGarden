# リリースの準備（Play 事前登録）

| もの | 場所 |
|---|---|
| データセーフティの一覧 | `data_safety.md` |
| ストア画像 | `store/feature_graphic.png`、`store/screenshot_01〜06.png`、`store/icon_512.png`（アプリのアイコン、`tools/prepare_app_icon.py` が `docs/store/` に書き出すものの写し）（作り方は `tools/store_art/README.md`） |
| AAB | `app/build/outputs/bundle/release/app-release.aab`（`./gradlew bundleRelease`、コミットしない） |

## 署名（アップロード鍵）

- 鍵：`C:/Users/noda-yo/keys/pocketgarden/pocketgarden-upload.jks`（PKCS12、別名 `upload`、RSA 4096、有効期限 2054 年）。2026-11-16 作成。
- パスワード：プロジェクト直下の `keystore.properties`（Git に入れない。`.gitignore`）と、同じ内容の控え `C:/Users/noda-yo/keys/pocketgarden/keystore.properties.txt`。
- `app/build.gradle.kts` は `keystore.properties` があればリリース版をこの鍵で署名する。無ければ署名なし。
- **バックアップはまだ無い**（PC の中だけ）。jks とパスワードの控えを、PC の外（USB メモリ・パスワード管理アプリなど）に保管すること。
- Play アプリ署名を使う（初回アップロードで既定）ので、この鍵は「アップロード鍵」。なくしても Play Console から再設定を申請できるが、時間がかかる。

## 広告（AdMob）

- ID は `app/build.gradle.kts` の先頭（debug・promo＝Google のテスト用、release＝本番）。release は本番 ID が空・テスト用だとビルドが止まる。
- 雨乞い・コインの 2 枠（`AdSlot`）。AdMob から返る報酬の数は使わず、量はアプリ側の決まりのまま。
- 自分の端末をテスト端末にする：AdMob 管理画面の「設定 → テストデバイス」に、logcat に出る `Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("…"))` の ID を登録する（アプリのコードは変えなくてよい）。
- app-ads.txt：https://guumana99.github.io/app-ads.txt（Guumana99.github.io の直下。公開済み）。

## ネイティブのデバッグシンボル

release の `ndk { debugSymbolLevel = "SYMBOL_TABLE" }` で AAB に入る（Play Console の警告対策）。

## ライセンス

設定の「情報」→「ライセンス」は、自動で作った一覧を読む自前のダイアログ（FB1122 から。標準の画面は AppCompat のテーマが無く落ちたため）。

## まだのこと

- 効果音の出所の確定（`docs/ASSETS.md` の「音」）
