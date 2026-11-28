# rclone に自分の client_id を設定する（所要 15〜20 分）

Drive 反映に使っている rclone の共用 client_id は「2026 年中に使えなくなる」と案内されている（期日の明記はない）。
自分の client_id を作って、この PC の remote `gdrive` に入れる。

- 参照：<https://rclone.org/drive/#making-your-own-client-id>（2026/11/28 に確認）
- この手順は実際に通して試してはいない。Google の画面の言葉は変わることがある。
- **クライアント シークレットは人に見せない。** git にも、チャットにも貼らない。

## 0. 先に知っておくこと：今の Drive のフォルダは引き継がれない（1 分）

今の remote は `scope = drive.file`（rclone が自分で作ったファイルだけ見える）。
client_id を変えると別のアプリ扱いになり、**今までのフォルダ `PocketGarden_Docs` は新しい設定から見えなくなる**。
そのまま反映すると、同じ名前のフォルダがもう 1 つできる。

おすすめ（権限は今と同じまま）：

1. ブラウザで <https://drive.google.com> を開く。
2. `PocketGarden_Docs` を右クリック →「名前を変更」→ `PocketGarden_Docs_old` にする。
3. 設定後の反映で `PocketGarden_Docs` が新しく作られる。確認できたら `_old` は消してよい。

フォルダを人と共有していたり、リンクを渡していたりする場合は、新しいフォルダで共有し直す。

## 1. プロジェクトを作り、Drive API を有効にする（3 分）

1. <https://console.cloud.google.com/> を、Drive に使っている Google アカウントで開く（利用規約の画面が出たら同意する）。
2. 画面上部のプロジェクト選択 →「新しいプロジェクト」→ 名前 `rclone-drive` →「作成」。作ったプロジェクトを選ぶ。
3. 左上の ≡ →「API とサービス」→「ライブラリ」→ `Google Drive API` を検索 → 開いて「有効にする」。

## 2. 同意画面を作る（5 分）

1. ≡ →「Google Auth Platform」（または「API とサービス」→「OAuth 同意画面」）→「開始」。
2. アプリ名 `rclone`、ユーザーサポートメールに自分のアドレス →「次へ」。
3. 対象は「**外部**」→「次へ」。
4. 連絡先に自分のアドレス →「次へ」→ 同意にチェック →「作成」。
5. 左の「データアクセス」→「スコープを追加または削除」→ 下の「スコープの手動追加」に次を貼る →「テーブルに追加」→「更新」→「保存」。

   ```
   https://www.googleapis.com/auth/drive.file
   ```

## 3. 公開する（1 分・忘れない）

1. 左の「対象」→「**アプリを公開**」→ 確認。
2. 公開ステータスが「本番環境」になれば完了。

「テスト」のままだと、**認可が 1 週間で切れる**。審査の申請は不要。
「アプリを公開」が押せない場合は、「ブランディング」にホームページとプライバシーポリシーの URL を求められている
（GitHub Pages などの自分のページでよい）。

## 4. クライアント ID を作る（2 分）

1. 左の「クライアント」→「クライアントを作成」。
2. アプリケーションの種類「**デスクトップ アプリ**」→ 名前はそのまま →「作成」。
3. 表示される「クライアント ID」と「クライアント シークレット」を控える（この画面を閉じずに次へ進むのが楽）。

## 5. rclone に入れる（5 分）

PowerShell で次を実行する。

```powershell
rclone config
```

聞かれた順に、次のように答える。

| 表示 | 入力 |
|---|---|
| `e/n/d/r/c/s/q>` | `e`（既存の remote を編集） |
| remote の選択 | `gdrive` |
| `client_id>` | クライアント ID を貼る |
| `client_secret>` | クライアント シークレットを貼る |
| `scope>` | 今のまま（`drive.file`）。空欄で Enter |
| `service_account_file>` など | 空欄で Enter |
| `Edit advanced config?` | `n` |
| `Token already configured - replace it?`（または `refresh?`） | **`y`** |
| `Use web browser to automatically authenticate?`（`auto config?`） | `y` |

ブラウザが開くので、同じ Google アカウントを選ぶ。
「このアプリは Google で確認されていません」と出たら、「詳細」→「rclone（安全ではないページ）に移動」→「続行」（自分で作ったアプリなので問題ない）。

戻ったら：

| 表示 | 入力 |
|---|---|
| `Configure this as a Shared Drive?` | `n` |
| `Keep this "gdrive" remote?` | `y` |
| `e/n/d/r/c/s/q>` | `q` |

## 6. 確かめる（2 分）

プロジェクトのフォルダで実行する。

```powershell
powershell -ExecutionPolicy Bypass -File tools\sync_docs_to_drive.ps1
```

- Drive に新しい `PocketGarden_Docs` ができれば成功。
- 動画と確認用の画像は `PocketGarden_Docs/promo/tiktok/` に入る。
- 「shared client_id」の警告が出なくなっていることも見る。

うまくいかないとき：

- `403 access_denied` → 手順 3 の公開が済んでいない。
- `403 ... Drive API has not been used` → 手順 1-3 の有効化が済んでいない（有効化した直後は数分待つ）。
- 認可をやり直す → `rclone config reconnect gdrive:`
