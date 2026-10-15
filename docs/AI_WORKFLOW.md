# AI 駆動開発の進め方（Claude Code）

このプロジェクトは、仕様を 1 メッセージで渡し、AI が実装・テスト・エミュレータ確認・マージまで
行う形で進めてきた。そこで分かったことをまとめる。

## 1 回の依頼の流れ

1. `feature/<内容>` ブランチを切る。
2. 関係するコードを読む（全部は読まない。`grep` で場所を絞ってから読む）。
3. ドメイン → データ → UI の順に実装し、こまめにビルドする。
4. 関連するテストクラスだけ実行する。落ちたら「コードが悪いのか、期待値が古いのか」を必ず切り分ける。
5. エミュレータで見る。スクリーンショットは切り出して拡大してから確認する。
6. コミット → `git merge --no-ff` で `main` へ → ブランチ削除。
7. 報告する。**未確認の点と、仕様として決めた点を先に書く。**

## 依頼の書き方で効いたこと

- 番号付きの要件、守ること（「既存テストを壊さない」「画像は足さない」）、完了条件（「main にマージ」）を書く。
- テスト方針を書く（「全件は回さない。関連するものだけ」）。書かれていなければ全件を回すことになる。
- 曖昧な指示（「オープンするたびに報酬」など）は、両方の解釈を満たす形で実装し、報告で明示してきた。
- 同じ依頼文を貼り直すと、AI は「すでに実装済み」と判断して点検だけ行う。追加したい点があれば書くこと。

## コンテキストの管理

- 会話が長くなると前半は要約に置き換わる。**決まりごとは会話ではなくファイルに書く**
  （この 6 ファイルと、コード中の KDoc）。
- ドキュメントはすべて `docs/` にある。Claude Code が自動で読むのはリポジトリ直下の `CLAUDE.md` だけなので、
  `docs/CLAUDE.md` は**自動では読まれない**。新しいスレッドでは最初に「`docs/CLAUDE.md` を読んで」と伝える。
  ほかのファイルは必要なときに読ませる。
- 大きいファイル（`GardenCanvas.kt` は 1500 行超）は全体を読まず、`grep -n` で行番号を取って前後だけ読む。
- 永続メモ（`~/.claude/projects/.../memory/`）には「ブランチ運用の決まり」だけを入れてある。
  コードから分かることはメモに入れない。

## どのテストを回すか

| 触った場所 | 回すクラス |
|---|---|
| 作物の成長・天候・枯れ | `CalculateOfflineProgressUseCaseTest` `WitherAndWeatherTest` `RiverAndWeatherTest` |
| 配置・水やり・収穫・購入 | `GardenActionUseCasesTest` `HoeTest` `FloorAndFenceTest` |
| ショップ・価格・解放・建物効果 | `ShopAndEffectsTest` `ProgressionTest` |
| 図鑑 | `CollectionTest` |
| 建築・解体・移動 | `ConstructionTest` `StoreObjectTest` |
| シンボル建物・庭の拡張 | `SymbolBuildingTest`（＋数を見る `ProgressionTest` `CollectionTest` `StoreObjectTest` `ShopAndEffectsTest`） |
| 建物の裏の判定・半透明・タップ位置 | `OcclusionTest` `IsometricEngineTest` |
| 通知の予約・設定の連動・権限の判定 | `NotificationsTest`（＋設定の保存 `SettingsRepositoryTest`） |
| 生き物の来訪・組み合わせ表 | `CreatureVisitTest` |
| 自然メモの記録・DB（v8 の SQL とスキーマの一致） | `NatureNotesTest` |
| 生き物の季節の条件・自然メモの「あたらしい」・DB v11 | `NatureNotesEngineTest`（＋表の網羅 `CreatureVisitTest`） |
| リワード・1 日の回数 | `RewardTest` |
| 川・フェンスの連結、川の位相 | `FloorAndFenceTest` `FenceAutotileTest` `FlowAndSoilTest` |
| 風・影・耕した土 | `AtmosphereTest` `FlowAndSoilTest` |
| カメラ・座標変換 | `IsometricEngineTest` `CameraModeTest` |
| 画像・アトラス・`tools/` | `BundledArtTest` `AssetPipelineTest` |
| 文言・レイアウト・トースト | `PolishTest`（＋文言を見る `StoreObjectTest` `BundledArtTest`） |
| 設定・音・改ざん検出 | `SettingsRepositoryTest` `AudioManagerTest` `TimeTamperDetectionTest` |
| タイトル・スターターガーデン・チュートリアル・機能の段階解放・季節 | `OnboardingTest` |
| 庭の倍率の保存 | `SettingsRepositoryTest` `IsometricEngineTest` |
| もちものの並び替え・タブ・入手の記録（DB v9） | `InventorySortTest`（＋設定の保存 `SettingsRepositoryTest`） |
| 四季・お便り・旬のショップ・植え付け制限・温室（DB v10） | `SeasonsTest`（＋棚や数を見る `ShopAndEffectsTest` `ProgressionTest` `CollectionTest` `HoeTest`） |

アイテムや図鑑ページを**増減**したら、数を固定値で見ているテストが落ちる
（`ProgressionTest` `CollectionTest` `StoreObjectTest` `ShopAndEffectsTest`）。これは期待値の更新でよい。

### テストの癖

- 枠を `InventorySlot(...)` と丸ごと比べるテストは、入手の通し番号を外して比べる（`slot.contents()`、`GardenActionUseCasesTest.kt` の末尾）。

- テスト用の偽リポジトリ `FakeGardenRepository` は `GardenActionUseCasesTest.kt` の末尾にある（`till()` `discover()` `progress` など）。
  `GardenRepository` にメソッドを足したら、ここにも足す。
- UI は Compose の実機テストが無い。代わりに、**計算を純関数に切り出してテスト**する
  （`Wind` `Daylight` `SoilTexture` `CameraFrame` `popScale` `RiverAutotile.frameFor`）。
  描画関数そのものは、ソースを文字列として読んで「〜を呼んでいない」を確かめるテストがある（`PolishTest` `CameraModeTest`）。
  リファクタリングで関数名や並びを変えると、これらが落ちる。
- JVM テストには `javax.imageio` がコンパイル時に見えない。画像を読むテストはリフレクション経由（`BundledArtTest` の `Picture`）。
- `SettingsRepositoryTest` の「observers are told about each change」は、まれにタイミングで落ちる。再実行で通る。

## 環境の落とし穴（Windows 11 / Git Bash）

### シェル

- 既定の `java` は 1.8。`JAVA_HOME` を JDK 21 に向けてから Gradle を呼ぶ（`CLAUDE.md` 参照）。
- **ヒアドキュメントの中にアポストロフィ（`'`）があると失敗することがある**（`<<'EOF'` と引用しても
  「unexpected EOF while looking for matching」で止まった）。Kotlin のコメントや英文に `'` が
  あるなら、スクリプトはヒアドキュメントではなく**ファイルとして書いてから実行**する。
- `python -c "..."` の中で Windows パスにバックスラッシュを書かない。`cygpath -w` で渡し、スクリプト側は `sys.argv` で受ける。
- 待つときは `ping -n <秒> 127.0.0.1 >/dev/null`。`sleep` は使えない。
- Bash ツールは呼び出しごとに作業ディレクトリが戻ることがある。毎回 `cd` を絶対パスで書く。
- Python は `/c/Users/noda-yo/AppData/Local/Programs/Python/Python312/python.exe`（Pillow、numpy 入り）。

### ファイルの編集

- 既存ファイルの書き換えは、**完全一致を確かめてから置換する Python スクリプト**が確実だった
  （`assert s.count(old) == 1`）。一致しなければ何も書かずに止まるので、半端な状態にならない。
- ただし 1 つのスクリプトで複数ファイルを順に書くと、途中の失敗で**前半だけ適用**される。
  失敗したら `git status` で適用済みを確かめ、残りだけを流し直す。
- 改行コードは CRLF と LF が混在しうる。読むときに `\r\n` → `\n` へ正規化し、`newline="\n"` で書く。
- Write ツールは「読んだ後に変更されたファイル」への上書きを拒む。その場合は置換スクリプトか Edit を使う。

### エミュレータ

- 起動：`emulator.exe -avd Pixel_10 -no-window -no-audio -no-boot-anim -no-snapshot-save`（バックグラウンド）。
- `adb` は必ず `-s emulator-5554`。USB の実機が繋がっていることがある。
- 起動直後はアプリが重く、**最初の数回のタップは取りこぼす**。起動後 50〜60 秒待ち、
  結果をスクリーンショットで確かめてから次へ進む。
- 起動するとまずタイトル画面。「はじめる」は y≈2034、「つづきから」は y≈2195（初回は灰色）。
  新しい庭は更地で、中央 (4,4) に木箱だけ（#28 から）。チュートリアルは 木箱→クワで (4,4) を耕す→種まき→水やり→完了ダイアログ→機能の紹介 3 枚。
- コテージ（Lv.6）など後半の物を試すときは、開発者メニュー（🛠）の「+500 コイン・Lv+1」を押す。ガラスの温室（Lv.18）は 17 回。季節は「次の季節へ」で進める。
  建築を待たずに完成させるには「+1 時間」（建築の残り時間も進む）。
- 画面は 1080×2424。9×9 のときのマスの中心は `x = 540 + (gx - gy) * 51.1`、`y = 1204 + (gx + gy - 8) * 25.6`。
  下部ツールバーは y≈2270（クワ 190、持ち物 718、ショップ 885）。
- エミュレータのデータは起動ごとに初期状態に戻っていた。そのため**旧データからの移行はエミュレータでは確かめられていない**。実機への上書きインストールで確認する。
- 1 秒未満の演出（フェード、弾ける演出、風）は、スクリーンショットでは確認できない。
  連続撮影の差分で「動いている」ことまでは言えるが、見た目の良し悪しは実機で見てもらう。
- 動画解析の道具（ffmpeg、OpenCV）は入っていない。
- 生き物の確認：スターターガーデンに柵は無くなった（#28）。「ことり」の条件（ベンチと柵 3 本）は自分で柵を置いてそろえる。
  来るかどうかは 5 分ごとの抽選（50%）なので、長いと 10 分以上待つ。来たかどうかは図鑑の「いきもの」タブで確かめるのが確実（トーストは数秒で消える）。
- 通知の確認：柵（建築 20 秒）を置いてホームに戻り、30 秒ほど待って
  `adb -s emulator-5554 shell dumpsys notification --noredact | grep -A3 miniaturegarden`。通知欄は `cmd statusbar expand-notifications`。
  Git Bash では `MSYS_NO_PATHCONV=1` を付けないと `/sdcard/...` などのパスが書き換えられる。

## 報告の書き方

- 最初にマージ先のコミットとテスト件数。全件を回していないなら、そう書く。
- 次に、**確認できていないこと**と**依頼と違う点・自分で決めた点**。
- そのあと、項目ごとの内容。ファイル名の羅列ではなく、動作を書く。
- 既存テストを書き換えたら、件数と理由を書く。
- 依頼文の前提が事実と違うとき（存在しないファイル名、すでに実装済みの機能、使っていない画像）は、
  合わせずに指摘する。
