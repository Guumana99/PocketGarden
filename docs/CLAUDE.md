# ぽけっと庭園 — 作業ガイド

9×9 マス（シンボル建物を建てると 13×13 まで広がる）の箱庭で作物を育て、建物や装飾を置いて眺める Android ゲーム。
Kotlin / Jetpack Compose / Hilt / Room(SQLCipher)。パッケージは `com.studioguu.miniaturegarden`。

このファイルは `docs/` にあるため、新しいスレッドで自動的には読み込まれない。作業の最初に読ませること。

くわしい背景は、同じ `docs/` フォルダに用途別に分けてある。必要なものだけ読むこと。

| 知りたいこと | ファイル |
|---|---|
| 構成・描画・計算ロジック・DB | `ARCHITECTURE.md` |
| 世界観と UX の判断基準 | `PHILOSOPHY.md` |
| 画像・音の素材と加工手順 | `ASSETS.md` |
| AI での進め方、環境の落とし穴 | `AI_WORKFLOW.md` |
| 済んだこと、これからの候補 | `ROADMAP.md` |

## コマンド

Git Bash で実行する。PATH の `java` は 1.8 なので、必ず先に `JAVA_HOME` を設定する。

```bash
export JAVA_HOME="/c/Users/noda-yo/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2"
```

```bash
# ビルド（デバッグ APK）
timeout 560 ./gradlew assembleDebug --console=plain
```

```bash
# 影響範囲だけテスト（基本はこちら）
timeout 560 ./gradlew testDebugUnitTest --tests "*ConstructionTest" --tests "*PolishTest" --console=plain
```

```bash
# 全件テスト（約 490 件。指示があったとき、または広く触ったときだけ）
timeout 560 ./gradlew testDebugUnitTest --console=plain
```

- `timeout` を付ける。Gradle デーモンが固まったことがある（`./gradlew --stop` で復帰）。
- 結果は `app/build/test-results/testDebugUnitTest/*.xml`。件数はここから数える。
- Gradle は入力が変わらないテストを再実行しない。`strings.xml` やソースを**ファイルとして読む**テスト
  （`PolishTest` `BundledArtTest` `CameraModeTest` `StoreObjectTest` の一部）は、
  それだけ変えても「up-to-date」で飛ばされる。確実に回すなら `--rerun-tasks`。

## テストの方針

- **触った範囲に関係するテストクラスだけ**を `--tests` で回す。全件は時間がかかるので既定では回さない。
- どのクラスが何を見ているかは `AI_WORKFLOW.md` の対応表を使う。
- 仕様変更で既存テストの期待値が変わるときは、テストを直してよい。ただし**何件を、なぜ変えたか**を報告に書く。
- 「全件は未実行」「この後の変更は再テストしていない」は、隠さず報告に書く。

## Git の決まり（厳守）

- `main` に直接コミットしない。`feature/<内容>` ブランチで作業する。
- ビルドと関連テスト、可能ならエミュレータ確認まで済んだら `git merge --no-ff` で `main` へ。ブランチは消す。
- 作業ツリーはクリーンに保つ。例外は `gradle.properties` の未コミット変更
  （`org.gradle.tooling.parallel=true`。ユーザーの手元設定なので**触らない・コミットしない**）。
- `git add` はパスを指定する（`git add app tools`）。`git add -A` は使わない。
- コミットメッセージは英語、末尾に `Co-Authored-By:` 行。

## 基本ルール

- 返答と報告は日本語。コード、コメント、コミットメッセージは英語。
- 報告は「何をしたか」より先に、**確認できていないこと・仕様として勝手に決めたこと**を書く。
- 画像アセットを勝手に足さない。足りない絵はプログラム描画で補い、その旨を伝える。
- UI 文言に「動画」という語を使わない（広告まわり）。
- 接続中の実機（USB）には触らない。確認はエミュレータ `emulator-5554` のみ（`adb -s emulator-5554`）。
- AdMob はテスト用 ID のまま。本番 ID への差し替えは `app/build.gradle.kts` の 2 つの定数。

## よく触る場所

```
app/src/main/java/com/studioguu/miniaturegarden/
  domain/model/     GardenItem(ショップのマスター) GridObject GardenGrid Collection StructureData StructureEffect GardenExpansion WeatherType
                    CreatureTable(生き物の組み合わせ表 VisitRule) CreatureVisits(来訪の判定・自然メモ)
  domain/usecase/   GardenActionUseCases ConstructionUseCases CalculateOfflineProgressUseCase OnboardingUseCases ほか
  data/             Room(local) DataStore(settings, reward) security asset audio ads notifications(WorkManager)
  ui/GardenScreen.kt GardenViewModel.kt   画面と状態
  ui/title/         タイトル画面と見本の庭      ui/onboarding/  木箱のポップアップ、チュートリアルの札、完了ダイアログ、機能の紹介
  ui/notifications/ 通知の権限・設定の行・招待ダイアログ
  ui/components/GardenCanvas.kt           庭の描画すべて（大きいファイル）
  ui/render/        IsometricTransform RiverAutotile FenceAutotile Atmosphere(風・光) SoilTexture Occlusion(建物の裏) CreatureSprites(生き物の仮の絵)
tools/              アセット加工スクリプト（Python）
```
