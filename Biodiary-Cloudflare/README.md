# Biodiary アカウントAPI（Cloudflare Workers + D1）

ver0.1.2向けです。アカウント、ログイン状態、コミュニティ参加状態をCloudflare D1に保存します。コミュニティに共有した写真・記録とアイコン画像は、引き続きRaspberry PiのAPIに保存します。未共有の観察記録は従来どおり端末内に保存します。Piには共有記録の所有者を識別するための最小限のアカウント情報も残ります。

写真の通信では、Cloudflareが60秒間有効な署名付き接続情報を発行し、ブラウザ・Android端末がPiのTailscale Funnelへ直接接続します。Piは署名とコミュニティ参加状態を検証します。Cloudflare WorkerからFunnelへ接続すると525が発生する環境でも、端末からFunnelへ接続できれば共有できます。Piの `BIODIARY_WEB_ORIGINS` にはブラウザ版の公開元URLを設定します。

## 導入状況（2026年10月7日）

- Cloudflare D1 `biodiary-accounts` に既存の1アカウントを移行済みです。名前とIDを維持し、初回ログイン時にパスワードも移行しました。
- Workerの公開URLは `https://biodiary-api.ytmurakawa0325.workers.dev` です。
- Raspberry Pi APIを更新し、Cloudflareからの署名を受け入れる設定にしました。従来のログインは既存アカウント移行の間だけ有効です。
- ブラウザ版は `https://biodiary-album.pages.dev` に公開済みです。Android版のソースには新URLを設定済みで、端末へ反映するにはAndroid Studioから再実行が必要です。
- 既存ユーザーはCloudflare側でログイン済みです。Workerの旧Piログイン移行機能は停止済みで、Piの旧トークン経路はラズパイへの接続が安定してから停止します。

## 再構築時の手順

1. Raspberry PiのSQLiteファイルをバックアップする。現在のサービスで使う `BIODIARY_DATA_DIR` の `biodiary.sqlite3` を、別名でコピーする。
2. 更新した `biodiary_api.py` と `export_cloudflare_accounts.py` をPiの `~/biodiary-api/` に配置する。まだ `BIODIARY_CLOUDFLARE_AUTH_ONLY=1` は設定しない。
3. Piで既存アカウントの移行SQLを作る。環境変数のディレクトリは実際の保存先に合わせる。

   ```sh
   BIODIARY_DATA_DIR=/home/NASfiles/.biodiary python3 ~/biodiary-api/export_cloudflare_accounts.py > ~/existing-accounts.sql
   ```

   このSQLには名前・ID・一言が入ります。パスワードは含まれません。取り扱いに注意し、移行後に不要なコピーを削除してください。

4. CloudflareにログインできるPCで、このフォルダを開き、Node.jsを使って以下を実行する。

   ```sh
   npm install
   npx wrangler login
   npx wrangler d1 create biodiary-accounts
   ```

5. 表示された `database_id` を `wrangler.toml` に設定し、次を実行する。

   ```sh
   npx wrangler d1 migrations apply biodiary-accounts --remote
   npx wrangler d1 execute biodiary-accounts --remote --file=existing-accounts.sql
   ```

   2行目の前に、Piで作った `existing-accounts.sql` をこのフォルダへ安全にコピーする。既存のログイン名を先に予約するため、**公開登録より前**に行う。

6. 共有の秘密文字列を1つ生成する。Piなら次で生成できる。

   ```sh
   python3 -c "import secrets; print(secrets.token_urlsafe(48))"
   ```

   同じ値をCloudflareの `PI_SHARED_SECRET` と、Piの `BIODIARY_CLOUDFLARE_IDENTITY_SECRET` に設定する。Cloudflare側は `npx wrangler secret put PI_SHARED_SECRET` を使う。値をソースコードやチャットに貼らない。

7. Piのサービス設定で秘密文字列を読み込ませ、`sudo systemctl restart biodiary-api` を実行する。現行APIが動作していることを確認してから `npx wrangler deploy` でWorkerを公開する。公開後に表示された `https://...workers.dev` が、アプリとブラウザに入力するCloudflare API URLになる。
8. 既存ユーザーは同じログイン名・パスワードでWorkerに一度ログインする。初回ログイン時にパスワードの照合をPiで行い、その後のログインに使う情報をD1へ登録する。既存の写真と共有記録の所有者IDは維持する。
9. 全員の移行を確認したら `wrangler.toml` の `LEGACY_LOGIN_ENABLED` を `false` にして再公開する。Pi側で `BIODIARY_CLOUDFLARE_AUTH_ONLY=1` を設定して再起動すると、旧式のPiトークンによる写真APIへの直接アクセスを停止できる。

## コミュニティ招待

アカウント作成に招待コードは不要です。コミュニティ参加にだけ使用します。

```sh
node scripts/new-invite.mjs 5
```

発行数は1〜60を指定でき、省略すると1件です。表示された1つのSQLをCloudflare D1 Consoleに貼り付けるか、`npx wrangler d1 execute biodiary-accounts --remote --file=招待コード用.sql` で登録します。その後、招待コードを1人に1つずつ伝えます。コードは14日間有効で1回だけ使えます。コミュニティの上限（コミュニティごとの人数）は `wrangler.toml` の `MAX_COMMUNITY_MEMBERS` で変更できます。アカウントの上限は別の `MAX_ACCOUNTS` です。

### 別のコミュニティを作る（面接・見学用など）

いつものコミュニティ（ID `default`）とは別に、投稿・メンバー・いいね・コメント・通知が混ざらないコミュニティを作れます。招待コードを発行するときにコミュニティIDと名前を指定します。

```sh
node scripts/new-invite.mjs 3 --community interview --name "面接用コミュニティ"
```

- 表示されたSQL（コミュニティの作成と招待コードの登録）をCloudflare D1 Consoleでそのまま実行します。同じIDで2回目以降に発行しても、コミュニティは作り直されません。
- このコードで参加した人は、そのコミュニティのメンバー・共有記録だけを見られます。いつものコミュニティの人からも、面接用の投稿は見えません。
- 最初に参加した人がそのコミュニティの管理者になり、アカウント画面からコミュニティ名を変更できます。
- 1つのアカウントが参加できるコミュニティは1つだけです。自分も面接用コミュニティの中を見せたい場合は、面接用に別のアカウントを作って参加します。
- コミュニティの一覧は D1 Console で `SELECT * FROM communities;`、メンバーは `SELECT login_name, community_id FROM accounts WHERE community_joined=1;` で確認できます。

### 複数コミュニティ対応を本番に反映する手順（ver0.1.3 → 複数コミュニティ）

順番が大切です。**Pi を先に更新**してください（古いPiのまま新しいWorkerを公開すると、面接用コミュニティの人にもいつもの共有記録が見えてしまいます）。

1. Pi の `biodiary.sqlite3` をバックアップし、新しい `biodiary_api.py` を配置して `sudo systemctl restart biodiary-api` で再起動する。起動時に `community_id` 列が自動で追加され、既存のアカウントはすべて `default` になります。
2. このフォルダで `npx wrangler d1 migrations apply biodiary-accounts --remote` を実行する（`0004_multiple_communities.sql` が適用され、参加済みの既存アカウントが `default` に入ります）。
3. `npx wrangler deploy` でWorkerを公開する。
4. 上の `new-invite.mjs --community ...` で招待コードを作って登録する。

Android版・ブラウザ版のアプリは変更不要です（コミュニティ名・管理者表示は既存の画面がそのまま使えます）。

## 無料枠と注意点

2026年10月時点の公式資料では、D1 Freeはデータベース1個500 MB、アカウント合計5 GB、1日あたり500万行読み取り・10万行書き込みです。アカウント数が少ない用途には十分な余裕があります。ただし上限を超えると無料プランではD1のクエリが失敗します。写真はD1に保存しません。

- [Cloudflare D1の制限](https://developers.cloudflare.com/d1/platform/limits/)
- [Cloudflare D1の料金と無料枠](https://developers.cloudflare.com/d1/platform/pricing/)
- [D1マイグレーション](https://developers.cloudflare.com/d1/reference/migrations/)

Worker公開後、Android版の再ビルドとブラウザ版の再配布が必要です。公開済みのアプリには、このフォルダの変更だけでは反映されません。
