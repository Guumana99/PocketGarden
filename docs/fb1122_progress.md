# FB1122 進み具合（2026-11-22 中断時点）

ブランチ: feature/fb-1122（main へは未マージ・未 push）。versionCode 6 / 0.1.5 は未変更。

## 済んだこと（コードは入れた・エミュレーターで確認）
1. ライセンス画面の落ち：原因は `OssLicensesMenuActivity` が AppCompat テーマを要求するのに、アプリのテーマが `android:Theme.Material`
   （`IllegalStateException: You need to use a Theme.AppCompat theme`）。標準画面をやめ、自動生成の
   `third_party_licenses` / `third_party_license_metadata` を読む自前のダイアログ（`OssLicenses.kt` + `LicensesDialog`）に替えた。
   `play-services-oss-licenses` 依存は外した。debug は開ける（debug は生成一覧が空で「Debug License Info」のみ）。
2. 効果音クレジット行を削除（文字列・テストも）。
3. 影：小屋・コテージ・風車の影は足元の短い影（`drawStructureShadow` に maxReach）、岩（deco_rock*）は真下に小さい接地影のみ。
4. ショップ：品の窓を、羽目板の上端（`ShopRoomShape.PANELLING_TOP`）までに切った（実測）。
5. はみ出し：`GardenClip` の `standing`（建物・置き物の切り抜き）を廃止。地面だけ枠で切る。
6. 風車の雪：屋根の穴を雪で埋める（`fillHoles`）、屋根の端（軸側の板）の面を足した。

## 未確認・途中
- 単体テストは未実行（直前に中断）。直したテスト：SettingsContentTest（3 件入れ替え）、FeedbackVisualTest、GreenhouseLayoutTest。
  回す候補：SettingsContentTest FeedbackVisualTest GreenhouseLayoutTest SnowCoverTest EdgeSnowTest ShopShelvesTest PolishTest ほか影響範囲。
- ショップを 0.7（羽目板の上端）で切り直した後は、実機相当の再確認をしていない（直前は 0.82 で確認、雑貨店・文字 1.3 倍とも OK）。
- release でライセンス一覧が出ること、release の AAB は未確認・未作成。
- 影・風車の「前後の切り抜き」は、前の状態の画像を撮っていない（docs/fb1122 に after_* のみ）。必要なら main をビルドして撮る。
- 風車の壁の白いかすれ：絵の透明な穴。雪の日に目立つので、雪の層を壁にかけない案を報告で提案する（直すかはユーザー判断）。
- デバッグ用の庭 `b60_fb22_shadow` / `b60_fb22_edges` を GroundGarden.kt に足した（debug のみ）。edges では風車 2 つが出なかった（原因未調査）。

## 次にやること
1. 単体テストを回す（影響範囲のみ）。
2. 羽目板上端での最終確認（3 つの店）。
3. versionCode 6 / versionName 0.1.5、`bundleRelease` で AAB、release でライセンス一覧を確認。
4. 報告（確認できていないこと・勝手に決めたことを先に）。main へマージ・push、docs の Drive 同期。
