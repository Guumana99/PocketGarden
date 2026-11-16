# リリースの準備（Play 事前登録）

| もの | 場所 |
|---|---|
| データセーフティの一覧 | `data_safety.md` |
| ストア画像 | `store/feature_graphic.png`、`store/screenshot_01〜06.png`（作り方は `tools/store_art/README.md`） |
| AAB | `app/build/outputs/bundle/release/app-release.aab`（`./gradlew bundleRelease`、コミットしない） |

## 署名（アップロード鍵）

- 鍵：`C:/Users/noda-yo/keys/pocketgarden/pocketgarden-upload.jks`（PKCS12、別名 `upload`、RSA 4096、有効期限 2054 年）。2026-11-16 作成。
- パスワード：プロジェクト直下の `keystore.properties`（Git に入れない。`.gitignore`）と、同じ内容の控え `C:/Users/noda-yo/keys/pocketgarden/keystore.properties.txt`。
- `app/build.gradle.kts` は `keystore.properties` があればリリース版をこの鍵で署名する。無ければ署名なし。
- **バックアップはまだ無い**（PC の中だけ）。jks とパスワードの控えを、PC の外（USB メモリ・パスワード管理アプリなど）に保管すること。
- Play アプリ署名を使う（初回アップロードで既定）ので、この鍵は「アップロード鍵」。なくしても Play Console から再設定を申請できるが、時間がかかる。

## まだのこと

- AdMob の本番 ID（`app/build.gradle.kts` の 2 つの定数。今はテスト用）
- `versionCode 1` / `versionName 0.1.0`
