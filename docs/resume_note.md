# 再開メモ（2026-11-28 中断）

ブランチ：`feature/fb1126-plaque`（push 済み・main には未マージ）

## 依頼（ユーザー）

1. 建物の「完成」「撤去」の札を、収穫の「+n」と同じ扱いにする（建物の上に出す・カメラに付いていく・画面端やパネルの裏なら見える範囲に寄せる）。
2. 終わったら versionCode を 11 にして release の AAB を作り、main にマージ・push。報告は短く。
3. rclone の共用 client_id 終了の件：ユーザーが自分で設定するための手順を docs/ に md で書く（手作業 20 分以内。設定はユーザーがやる）。

## 済んだこと

- 札の実装：`GardenEffect.source`、`FinishedWork.site`（撤去・梱包は消える前の姿を持つ）、`GardenScreen.overSource`。
- `versionCode = 11`。
- 手順書 `docs/RCLONE_CLIENT_ID.md`（画面は未確認。drive.file のため今の Drive フォルダが引き継がれない点も書いた）。
- `FeedbackBatchTest` の期待文字列を 1 件更新。
- 本体とテストのコンパイルは成功。

## 残り

1. 関係するテストを回す（この変更後はまだ 1 件も実行していない）。
   `ConstructionTest FeedbackBatchTest FeedbackVisualTest Fb1126Test PolishTest StartupAndDialogsTest SystemRefactorTest WordingAndToastTest CameraModeTest HarvestTypeTest SoilPondAndFirstPlayTest AssetLedgerTest`
   （`--tests` は `testDebugUnitTest` の直後に付ける。`assembleDebug` は別のコマンドで。中断前の BUILD FAILED は、この並べ方の誤りが原因とみられる。）
2. エミュレータ（`emulator-5554` のみ）で、完成・撤去の札が建物の上に出るのを確認。画像は `docs/review/fb1126/`。
3. `bundleRelease` で AAB を作り、versionCode 11 を確かめる（`app/build/outputs/bundle/release/app-release.aab`。versionCode 10 の AAB は前回作成済み）。
4. `git merge --no-ff` で main へ、push、ブランチを消す。`tools/sync_docs_to_drive.ps1` で docs を Drive へ。この `resume_note.md` は消す。
5. 短く報告する。同時に完成した撤去（柵など隣り合うもの）の札が少し重なりうる点も触れる。

## 再開のとき最初にやること

`git checkout feature/fb1126-plaque` → `docs/CLAUDE.md` を読む → 上の「残り 1」。
`gradle.properties` の変更はユーザーの手元設定（触らない・コミットしない）。報告はすべて日本語。
