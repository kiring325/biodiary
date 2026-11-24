# Biodiary API（Raspberry Pi）

Androidアプリやブラウザ版から観察記録・写真を共有するための小規模APIです。記録はログインした本人だけが初期状態で見られます。共有を有効にした記録だけ、ほかの登録アカウントにも表示します。登録はPi上で発行した招待コードが必要で、上限は初期値10アカウントです。

## Piへの配置

WindowsのPowerShellでAndroidプロジェクトのルートへ移動し、次を実行します。`raspberrypi.local` が解決できない場合は、ルーターに表示されるPiのIPアドレスに置き換えてください。

```powershell
scp -r .\server yuto@raspberrypi.local:/home/yuto/biodiary-api
```

このサーバーはPiにある既存のPythonパッケージ `Flask` と `Waitress` を使います。APIコードは `/home/yuto/biodiary-api`、写真とSQLiteデータベースは `/home/NASfiles/.biodiary` に置きます。この保存先は `yuto` 所有でアクセス権を700にします。`/home/NASfiles` をSMB共有している場合、SMBで `yuto` としてアクセスできる利用者はこの隠しフォルダにもアクセスできる可能性があります。共有利用者を制限してください。

既に稼働中のPiを更新する場合は、WindowsのPowerShellでAndroidプロジェクトのルートから以下を実行します。サーバーのデータベースと写真はコピーしません。

```powershell
scp .\server\biodiary_api.py yuto@raspberrypi.local:/home/yuto/biodiary-api/biodiary_api.py
ssh -t yuto@raspberrypi.local 'sudo systemctl restart biodiary-api && systemctl is-active biodiary-api'
```

## Pi上でサービスを登録

PiのSSHで以下を実行します。

```bash
sudo cp /home/yuto/biodiary-api/biodiary-api.service /etc/systemd/system/biodiary-api.service
sudo systemctl daemon-reload
sudo systemctl enable --now biodiary-api
```

サービスは `127.0.0.1:8080` だけで待ち受けます。現在の公開URLにはTailscale Funnelを利用できます。招待コードの管理コマンドもPi上で実行します。

```bash
cd /home/yuto/biodiary-api
BIODIARY_DATA_DIR=/home/NASfiles/.biodiary python3 manage.py invite --count 1 --expires-days 30
```

表示された招待コードは一度だけ使えます。登録者に個別に渡し、第三者に公開しないでください。複数人分を発行する場合は `--count 3` のように指定します。アカウント上限を超えて登録できません。

## APIの概要

- `POST /api/v1/auth/register`：招待コードで登録。`inviteCode`, `loginName`, `password`, `displayName` をJSONで送信。
- `POST /api/v1/auth/login` / `POST /api/v1/auth/logout`：ログインとログアウト。
- `GET` / `PATCH /api/v1/account/me`：自分のプロフィールを取得・更新。
- `PUT /api/v1/account/me/avatar`：自分のアイコンを登録・更新。JPEG・PNG・WebP形式、最大1MB。
- `GET /api/v1/community/members`：登録済みメンバーのログイン名、アカウントID、ひとこと、アイコンのURLを取得。
- `GET /api/v1/community/members/{accountId}/avatar`：ログイン済みメンバーへアイコンを返す。
- `GET` / `PUT /api/v1/tag-groups`：自分のタググループを取得・保存。
- `GET` / `POST /api/v1/observations`：自分の記録と共有記録を取得し、写真付き記録を登録。登録時に `shared: true` を明示した場合だけ、最初から共有状態になります。

差分同期では `GET /api/v1/observations?since=<Unix秒>&limit=200&offset=0` を使います。応答の `updatedAt` は各記録の更新時刻（ミリ秒）、`serverTime` は次回同期に使うサーバー時刻（秒）、最初のページの `visibleIds` は現在閲覧できる記録IDです。`since` を省略した旧版には従来の新しい順で返します。写真の取得に失敗した場合、端末側は同期時刻を進めず次回再試行します。
- `GET` / `PUT` / `DELETE /api/v1/observations/{id}`：閲覧・編集・削除。編集と削除は所有者のみ。
- `GET /api/v1/observations/{id}/photo`：権限を確認したうえで写真を返す。
- `GET /api/v1/status`：サービス名とAPI版だけを返す状態確認用エンドポイント。

ログイン後のAPIには `Authorization: Bearer <token>` を付けます。新しい観察記録は非公開で作られ、所有者が `shared: true` に変更した場合だけメンバー全員の一覧に現れます。写真は最大8MBのJPEG・PNG・WebPに制限しています。

## ブラウザ版の接続

ブラウザから接続する場合は、Cloudflare Pagesの公開URLを確定してから、サービス設定に `BIODIARY_WEB_ORIGINS=https://公開URL` を追加します。設定がない間、APIはブラウザ向けCORS接続を許可しません。現在のブラウザ版はログイン後にTailscale Funnel経由でAPIを利用します。

## 現時点の範囲

Androidアプリとブラウザ版には招待コード登録・ログイン・個別の観察記録共有を追加しています。既存の端末内記録は自動で送信されず、共有操作をした記録だけがサーバーへ送られます。「コミュニティ」には登録済みアカウントを表示し、オンライン中かどうかは表示しません。

Pi 3 Model A+のメモリを抑えるため、Waitressは2スレッドで起動します。SQLiteのDBファイルと写真が置かれるSSDは別媒体へ定期バックアップしてください。

## 写真4枚対応

1件の記録に1〜4枚を保存できます。1枚目は従来の photoUrl、全写真は photoUrls の配列で返します。写真番号は1始まりで、2枚目以降は /api/v1/observations/{id}/photo/2 などから取得します。全写真に同じ閲覧権限を適用します。

登録・編集のmultipartフォームでは photo、photo2、photo3、photo4 を順番に送信し、metadata.photoCount に枚数を指定します。編集で写真を送る場合は変更後の全写真を送ってください。各写真は8MB以下、合計4枚までです。場所の詳細を非公開にした場合は全写真のメタデータを除去します。

更新すると extra_photos_json 列を自動で追加します。既存データは消去しません。古いクライアントによる1枚目の差し替えで追加写真が削除されないようにしています。GET /api/v1/status の maxPhotosPerObservation: 4 で対応を確認できます。

検証は tests/test_multi_photos.py で行えます。FlaskとPillowが必要で、データはテスト用の一時フォルダにのみ作成します。
