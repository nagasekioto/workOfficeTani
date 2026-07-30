# 実装状況（引き継ぎ用）

家政婦紹介事務所 人物管理システムのセキュリティ対応について、
**何が実装済みで、何が未実装か**を新しい作業者向けにまとめたものです。

最終更新: 2026-07-29

---

## 0. 最初に知っておくこと

### 0-1. リポジトリの場所

| 項目 | 値 |
|---|---|
| **作業対象** | `C:\Users\hyudo\workspace\workOfficeTani` |
| GitHub | https://github.com/nagasekioto/workOfficeTani |
| ブランチ | `main` |

> ⚠️ `C:\workofficetani` は**PostgreSQLのインストール先**であり、作業対象ではありません。
> 中に空に近いSpringスケルトンが入っていますが、これは実物ではありません。間違えないこと。

### 0-2. ビルドと実行

```powershell
cd C:\Users\hyudo\workspace\workOfficeTani
$env:DB_PASSWORD = "（実際のDBパスワード）"
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

> ⚠️ **`DB_PASSWORD` が未設定だとテストも起動も失敗します。**
> DBに接続する結合テスト（`PersonManagementApplicationTests`）があるためです。
> `TOTP_ENCRYPTION_KEY` も未設定だとログインできません（起動はします）。

- 技術構成: Java 17 / Spring Boot 3.5.10 / **Spring Data JDBC（JPAではない）** / Thymeleaf / PostgreSQL / iTextPDF / Apache POI / zxing
- DB: `kaseihu`（PostgreSQL 18, localhost:5432）
- テスト件数: **90件**（全て成功）

### 0-3. push は完了しました（2026-07-29）

remote URLに埋め込まれていたGitHubアクセストークンを除去し、`main` を同期済みです。
Windows資格情報マネージャーに保存済みの `github.com` / `nagasekioto` の認証情報で通ります。

```powershell
git remote set-url origin https://github.com/nagasekioto/workOfficeTani.git
```

> ⚠️ **トークン自体はまだ失効していません。** remote URLから消えただけで、
> GitHub側では今も有効です。失効は利用者側の未実施事項として残っています（第3章 #4）。

**push時に判明した注意点（同じ状況になったら踏む落とし穴）**

リモートに手元に無いコミット `325c74c`（2026-07-14）が1件ありました。
内容は**削除済みの共通パスワード方式**（`app.login.password` / 環境変数 `LOGIN_PASSWORD`）に
起動時ログを足すもので、対象コードが `f71d76d` で既に存在しないため衝突しました。

解消方法（マージコミット `cc85629`）:

- `LoginController.java` は手元版を採用。リモート側の `@PostConstruct logLoginPasswordSource()` は
  `correctPassword` / `SKIP_PASSWORD_CHECK` / `@Value` を参照しており、削除済みでコンパイルが通らない
- **自動マージで未使用importが3件（`PostConstruct` / `Logger` / `LoggerFactory`）黙って混入した。**
  import節は衝突マーカーの外側で自動マージされるため、目視では気付けない
- `SETUP_NEW_PC.md` はパスワード関連の記述を不採用とし、
  パスワードと無関係で現在も有効な「古いプロセスがポートを握ったままになる」行のみ救出

> **削除した機能に関わるマージでは、衝突箇所を直すだけでは足りません。**
> `git diff --cached HEAD -- src` でソース差分がゼロであることを必ず確認してください。

---

## 1. 実装済み

### 1-1. 対策2: 監査ログ（誰が・いつ・何を見たか）

| 項目 | 内容 |
|---|---|
| 画面 | **1-7-7 監査ログ** `/audit-log` |
| 記録先1 | DBテーブル `access_logs` |
| 記録先2 | ファイル `logs/audit/audit-YYYY-MM-DD.log`（追記のみ） |
| 保持期間 | 365日（`app.audit.retention-days`）。1日1回自動削除 |

**主なファイル**

- `service/AuditLogService.java` — 記録の本体
- `config/AuditLogInterceptor.java` — 全リクエストを1箇所で捕捉
- `config/WebMvcConfig.java` — Interceptorの登録
- `controller/AuditLogController.java` — 1-7-7の画面
- `templates/audit-log.html`

**設計上、崩してはいけない点**

- 記録先を**2箇所に分けている**。DBだけだと侵入時に `DELETE FROM access_logs` の
  一行で証跡を消される。片方だけの実装にしてはいけない
- `AuditLogService` / `AuditLogInterceptor` は**絶対に例外を外へ投げない**。
  監査ログの不具合で業務画面が全滅するのを防ぐため
- クエリ文字列は**許可リスト方式**。許可リスト外の値は `***` に伏せる
  （稼働管理簿の `search` に氏名が入りうるため、ログ自体が漏えい源にならないように）
- セッションIDはSHA-256ハッシュの先頭16文字のみ保存。生IDは保存しない
- URIの `;jsessionid=...` を記録前に除去する

イベント種別: `ACCESS` / `LOGIN_SUCCESS` / `LOGIN_FAILURE` / `LOGIN_LOCKED` /
`LOGOUT` / `LOGIN_BACKUP_CODE` / `AUTH_CHANGE`

### 1-2. 対策3: SQLインジェクション対策（監査完了）

SQL発行箇所75件（`@Query` 17件 / `jdbcTemplate` 16件 / `Statement` 42件）を全件監査。
**危険箇所は0件。** 全てプレースホルダ（`:name` または `?`）を使用。

監査の過程で見つけた**ヘッダーインジェクション1件を修正**
（`RegisterController.feeLedgerPdf` が `month` を検証せず `Content-Disposition` に連結）。

- 報告書: `docs/SQL_INJECTION_AUDIT.md`（全件一覧と再発防止項目）
- 未監査で残っている観点: **ZIP一括出力のエントリ名生成**（パストラバーサル）

### 1-3. 対策4: MFA（Google Authenticator / TOTP）

| 項目 | 内容 |
|---|---|
| 画面 | ログイン `/login`、初回セットアップ `/auth/setup`、**1-7-8 ログイン利用者管理** `/auth/users` |
| 方式 | 利用者名 ＋ 6桁コード。バックアップコードも同じ欄で使える |
| テーブル | `auth_users` / `auth_backup_codes` |

**主なファイル**

- `service/TotpService.java` — RFC 6238の自前実装（外部ライブラリ不使用）
- `service/SecretCipher.java` — AES-256-GCM暗号化
- `service/AuthUserService.java` — 登録・認証・バックアップコード
- `service/QrCodeService.java` — QRをSVGのデータURIで生成（zxing coreのみ）
- `controller/AuthSetupController.java` / `controller/LoginController.java`
- `templates/login.html` / `auth-setup.html` / `auth-enroll.html` /
  `auth-backup-codes.html` / `auth-users.html`

**設計上、崩してはいけない点**

- TOTPシークレットは **AES-GCMで暗号化し、鍵は環境変数 `TOTP_ENCRYPTION_KEY`（DBの外）**。
  DBを丸ごと盗まれても第2要素を奪えないようにするため。
  **平文で保存するフォールバックは意図的に作っていない**（設定漏れに気付けるように）
- バックアップコードは **SHA-256ハッシュのみ保存**。平文は発行直後の1度だけ画面表示
- `last_totp_step` により**同じ6桁コードの使い回し（再生攻撃）を拒否**する
- `completeEnrollment` は**登録済みの利用者に対して実行してはいけない**。
  実行できると、6桁コードを一度覗き見た相手がバックアップコード10個を奪える
- 登録対象は**セッションに記録した手続きからのみ決定**する。
  フォームの `userId` を信用してはいけない
- 有効な利用者が0人のときのみ、かつ**localhostからのみ**初回セットアップ画面を開く
- **最後の1人は無効化できない**（全員が締め出されるのを防ぐため）

正しさは **RFC 6238 Appendix B の公式テストベクタ6件**で検証済み（`TotpServiceTest`）。

### 1-4. パスワード設計の見直し

- 共通パスワード方式（`POST /login`）を**削除**
- メール認証方式（`EmailAuthService` とエンドポイント2本）を**削除**
  → 環境変数1つで二要素認証を迂回できる経路だったため
- `application.yml` の既定値から秘密の値（`7136` / `tani`）を**削除**
- `DB_PASSWORD` 未設定時は、Spring起動前に日本語で理由を表示して停止
  （`PersonManagementApplication.checkRequiredEnv`）
- 方針文書: `docs/PASSWORD_POLICY.md`

### 1-5. その他（実装済みの細かい対策）

| 内容 | 場所 |
|---|---|
| セッションIDをURLに載せない | `application.yml` `server.servlet.session.tracking-modes: cookie` |
| ログイン成功時のセッションID再生成（セッション固定化対策） | `LoginController` `request.changeSessionId()` |
| IPごとのログイン試行回数制限（5回で5分ロック） | `LoginController` |
| 利用者名の存在を応答から推測させない | `LoginController` |
| `logs/` と `.env` をGit管理外に | `.gitignore` |
| **Cookieに `SameSite=Strict`**（CSRFの緩和） | `application.yml` `server.servlet.session.cookie.same-site` |
| **セッションタイムアウト30分** | `application.yml` `session.timeout`（`SESSION_TIMEOUT`で変更可） |
| **ZIPエントリ名のサニタイズ**（パストラバーサル対策） | `ValidationUtils.sanitizeFileNamePart` |
| **アプリの待ち受けを127.0.0.1に限定** | `application.yml` `server.address`（`SERVER_ADDRESS`で変更可） |

#### 1-5-1. セッションCookieの強化（2026-07-29）

`SameSite=Strict` により、**他サイトから当システムへのリクエストにCookieが付かなくなります**。
CSRFトークンが無い現状（2-2）で `/permanent-delete` などのPOSTを外部から発火させられるのを
緩和するための措置です。**ただしCSRF対策の代替にはなりません。** 2-2は引き続き必要です。

> ⚠️ `secure` は **`false` のままにすること**。既定値を `COOKIE_SECURE` で切り替えられるように
> してありますが、現在はHTTP（localhost）で動いているため `true` にすると
> Cookieが一切送られなくなり、**ログインできなくなります**。HTTPS化と同時に切り替えること。

実機で `Set-Cookie: JSESSIONID=...; Path=/; HttpOnly; SameSite=Strict` が
出力されることを確認済み（設定のバインドを見るテストだけでは、Tomcatが実際に
ヘッダを出すかまでは確認できないため）。

#### 1-5-2. ZIP出力のパストラバーサル対策（2026-07-29）

対象は `IntroductionController.exportPdfZip` と `ReceiptMenuController.issuedListExportPdf`。

修正前は `replaceAll("[/:*?<>|]", "_")` で、**`\`（円記号）が抜けていました**。
ZIPの仕様上の区切りは `/` だけですが、**Windowsの展開ソフトの多くは `\` も区切りとして
解釈する**ため、氏名（1-1-1から入力される値）に `..\..\` を仕込まれると
意図しない場所へ書き出させられる恐れがありました。

`ValidationUtils.sanitizeFileNamePart` に集約し、`\`・制御文字（NULバイト）・
連続ドット・Windows予約名（CON/PRN/COM1等）・長さ（80文字）をまとめて処理します。
サニタイズを通していなかった `refNo` / `receiptNo` / `title` も通すようにしました。

### 1-6. 手順・ドキュメント（整備済み）

| ファイル | 内容 |
|---|---|
| `docs/SECURITY_CHECKLIST.md` | 対策の実施状況、**「唯一の命綱」チェックリスト**、保管場所の記録シート、定期点検 |
| `docs/INCIDENT_RESPONSE.md` | 乗っ取り発覚時の初動、認証情報の無効化、鍵のローテーション、復旧、報告義務 |
| `docs/NETWORK_RESTRICTION.md` | DBへのアクセス制限の設計と設定手順（**適用は未実施**） |
| `docs/SQL_INJECTION_AUDIT.md` | 監査結果 |
| `docs/PASSWORD_POLICY.md` | 秘密の値の扱い |
| `SETUP_NEW_PC.md` | 新PCでのセットアップ手順（TOTP対応済み） |
| `templates/backup-guide.html` | 1-7-5 バックアップ手順（監査ログのバックアップ、命綱の置き場所を追加済み） |

---

## 2. 未実装

### 2-1. 対策1: 最小権限の原則（DBロール分離）— **DB側は適用済み。切り替えは未実施**

#### 状況（2026-07-30）

| 段階 | 内容 | 状態 |
|---|---|---|
| Stage 1 | DBロールの作成と所有権の移管 | ✅ **適用済み** |
| Stage 2 | アプリ側のマイグレーション専用接続の実装 | ✅ **実装済み**（既定は従来動作） |
| Stage 3 | 環境変数を設定して実際に制限ロールへ切り替える | ⬜ **未実施**（利用者の操作） |

**現在アプリは従来どおり `postgres` で接続しています。** 環境変数を設定するまで動作は変わりません。

#### ロール構成（3つ。当初案の4分割から減らした）

| ロール | 権限 | 誰が使うか |
|---|---|---|
| `kaseihu_owner` | テーブル所有者。DDL | `DatabaseMigrationRunner`（起動時のみ） |
| `kaseihu_app` | SELECT/INSERT/UPDATE/DELETE | アプリの通常動作 |
| `kaseihu_ro` | SELECT のみ | バックアップ（pg_dump）・調査 |
| `postgres` | スーパーユーザー | **緊急用に温存**（`docs/EMERGENCY_ACCESS.md`） |

**当初案の `kaseihu_admin`（削除専用）は作りませんでした。**
アプリは領収書削除・売上明細削除など各画面で正常に `DELETE` するため、
DELETEだけを別ロールに分けると業務が動かなくなります。
致命的な被害はスーパーユーザー権限にあるので、そこを外すことを優先しました。

実測で確認した、スーパーユーザーを外すことの効果:

| `kaseihu_app` での操作 | 結果 |
|---|---|
| `SELECT` / `INSERT` / `DELETE` | ✅ 通る（業務は動く） |
| `ALTER TABLE persons ADD COLUMN` | ❌ 所有者である必要があります |
| `DROP TABLE access_logs` | ❌ 所有者である必要があります |
| `CREATE TABLE` | ❌ スキーマ public へのアクセスが拒否されました |
| **`COPY (SELECT 1) TO PROGRAM 'cmd'`** | ❌ 拒否（**スーパーユーザー専用の任意コマンド実行。ここが最大の収穫**） |

拒否後にスキーマ・データへ副作用が無いことも確認済み（18テーブル・件数すべて不変）。

#### 落とし穴への対処（2つ）

> ⚠️ **落とし穴1**: `DatabaseMigrationRunner` が起動のたびに `ALTER TABLE` を実行する。
> 接続ロールを非所有者に降格すると、**次回起動時に権限エラーで落ちて起動しなくなる**。

対処: `DatabaseMigrationRunner` に**マイグレーション専用の接続**を追加しました。
`DB_MIGRATION_USER` が設定されていればそれで接続し、**未設定なら従来どおり通常の接続を使います**。
さらに権限エラーを検出したときは、直し方（`DB_MIGRATION_USER` の設定とロールバック手順）を
日本語で表示します。**この落とし穴を実際に踏んで、案内が出ることを確認済み。**

> ⚠️ **落とし穴2**: 今後テーブルを追加したとき、`kaseihu_app` に権限が付かず
> **その画面だけが実行時に権限エラーで落ちる**。しかも `postgres` でテストしていると再現しない。

対処: `ALTER DEFAULT PRIVILEGES **FOR ROLE kaseihu_owner**` を設定済み。
`FOR ROLE` を付けるのが要点で、これが無いと「スクリプト実行者(`postgres`)が作った
テーブル」にしか効かず、実際に作るのは `kaseihu_owner` なので無意味になります。
**新しいテーブルは必ず `DatabaseMigrationRunner` 経由で作ること**（手で作らない）。

#### Stage 3: 切り替え方（利用者の操作）

パスワードは `C:\Users\hyudo\pg-config-backup\db-role-passwords.txt` にあります。
**紙に控えたら、このファイルは削除するか鍵のかかる場所へ移してください。**

```powershell
[Environment]::SetEnvironmentVariable("DB_USER", "kaseihu_app", "User")
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "（kaseihu_app のパスワード）", "User")
[Environment]::SetEnvironmentVariable("DB_MIGRATION_USER", "kaseihu_owner", "User")
[Environment]::SetEnvironmentVariable("DB_MIGRATION_PASSWORD", "（kaseihu_owner のパスワード）", "User")
```

**この4つはセットで設定すること。** `DB_USER` だけ変えると落とし穴1を踏みます。

**実機で検証済み**: この設定で起動し、`kaseihu_app` で10接続、
マイグレーションは `kaseihu_owner` で実行、画面表示と監査ログのINSERTが動作、
18テーブルすべて読み取り可能であることを確認しました。

#### 元に戻す

```powershell
psql -U postgres -h localhost -d kaseihu -f scripts\rollback-db-roles.sql
```

業務データは一切消えません。所有者と権限だけを戻します。
詳細と症状別の対処は **`docs/EMERGENCY_ACCESS.md`**（印刷して保管すること）。

### 2-2. CSRF対策・認証チェックの集約 — **認証集約はPhase A完了、CSRFは未対応**

#### Phase A（2026-07-29 完了）: 認証Interceptorで穴を塞いだ

`config/AuthenticationInterceptor.java` を新設し、`WebMvcConfig` に登録しました。
**「既定で全部を守り、通してよい経路だけを明示的に除外する」方式**です。

このとき**新たな穴を1件検出しました**。`templates/index.html` が Spring Boot の
ウェルカムページとして `GET /` で配信されており、**コントローラを経由しないため
認証チェックが一切効いていませんでした**。手書き方式では、そもそもコントローラが
無い経路を守れないという構造的な問題です。Interceptor化で塞がりました。

**崩してはいけない点:**

- **`WebMvcConfig` の登録順**。`AuditLogInterceptor` を**先**に登録すること。
  Interceptorは登録順に `preHandle` が走り、先に `true` を返したものの
  `afterCompletion` は必ず呼ばれる。順序を逆にすると
  **未認証の不正アクセス試行が監査ログに一切残らなくなる**
- 除外リストに **`/auth/users` を含めないこと**（1-7-8は認証必須）。
  `/auth/setup` と `/auth/enroll/**` だけを通す
- `/login` `/login/**` `/logout` を除外から外さないこと（外すと誰もログインできない）

未認証時の返し方は、`Accept` に `text/html` を含む場合（ブラウザの画面遷移）は
ログイン画面へリダイレクト、それ以外（fetch/XHR）は401です。
**MockMvcは既定でAcceptを送らない**ため、リダイレクトを検証するテストでは
`.header("Accept", "text/html")` を明示する必要があります。

実機で全経路を確認済み（`/` `/menu` `/person/list` `/audit-log` `/auth/users`
`/permanent-delete` → 302 `/login`、`/login` → 200、JSON要求 → 401、
弾かれたリクエストが `authenticated=false` で監査ログに記録されること）。

#### Phase B（2026-07-30 完了）: 冗長になった手書きチェックを削除

Interceptorで守られていることを確認したうえで、各コントローラの手書きチェックを削除しました。
16ファイルから **-306行 / +109行**。

`session.getAttribute("authenticated")` の残存は以下の**3箇所のみ**です。
ここを認証チェックと勘違いして消さないこと。

| 場所 | 何をしているか |
|---|---|
| `AuditLogInterceptor:66` | 監査ログに「認証済みだったか」を記録する。認証チェックではない |
| `LoginController:114` | ログイン成功時に印を立てる処理そのもの |
| `AuthenticationInterceptor` のJavadoc | 説明文（コード上の判定は定数 `SESSION_AUTHENTICATED` 経由） |

`AuthSetupController.canAccessInitialSetup`（利用者0人＋localhost判定）と
`SESSION_PENDING_ENROLLMENT` の確認処理は、認証チェックとは別物なので残しています。

**実機で全104エンドポイント（GET 64 / POST 40）を網羅確認済み。**
未認証アクセスが全件 `302 /login` または `401` になり、素通りするものは0件でした。
ログイン経路5件（`/login` `/login/totp` `/logout` `/auth/setup` `/auth/enroll/confirm`）のみ通過します。

#### CSRF対策（2026-07-30 完了）

`config/CsrfInterceptor.java` を追加。**送信元のOriginヘッダを検証する方式**です。

**なぜトークン方式にしなかったか（重要）**

教科書的な対策はフォームに使い捨てトークンを埋める方式ですが、
本システムのThymeleafテンプレートには**共通フラグメントが1つも無く**
（`th:fragment` / `th:replace` の使用箇所ゼロを確認）、
**40個のフォームが28ファイルに散在**しています。全部に手で埋め込む必要があり、
**1つ埋め忘れるとその画面が使えなくなり、しかも画面を開くまで気付けません。**
業務が止まるリスクに見合わないと判断しました。

代わりにOriginヘッダで送信元を検証します。ブラウザのOriginはJavaScriptから
書き換えられないため偽装できず、**テンプレートには一切手を入れないので
埋め忘れによる画面破壊が起こりえません。**
Cookieの `SameSite=Strict`（1-5-1）と合わせて二重の防御になります。

**仕様**

- GET / HEAD / OPTIONS は検証しない（**データを変えるGETを作らないことが前提**）
- POST等は `Origin` を検証。無ければ `Referer` から送信元を取り出す
- **どちらも無い場合は拒否**する。「判定できないものは通す」にすると、
  ヘッダを送らない経路がそのまま抜け道になるため
- 期待するoriginは設定の固定値ではなく**リクエストから組み立てる**ので、
  ポート変更やHTTPS化をしても直す必要がない
- `/login/totp` も検証対象。認証チェックの除外対象だが、POSTである以上
  外部から発火させられるため

**限界**: Origin も Referer も送らない古いブラウザからは操作できなくなります。
1台のPCで最新ブラウザから使う運用のため許容しています。
社内の他PCや古い環境へ広げる場合はトークン方式の追加を検討してください。

**テスト時の注意**: MockMvcは既定でOriginを送りません。POSTのテストには
`.header("Origin", "http://localhost")` が必要です（既存14箇所に付与済み）。

**実機確認**: 実ブラウザでログインフォームを送信し、CSRF検証を通過して
ログイン処理まで到達すること（403ではなく302）を確認済み。
外部オリジン・外部Referer・ヘッダ無しはいずれも403になることも確認済み。

ご依頼の6項目には含まれていませんが、調査中に見つかった重大な未対応事項です。

**このアプリには Spring Security が入っていません**
（`pom.xml` に `spring-boot-starter-security` がなく、
`thymeleaf-extras-springsecurity6` と `spring-security-test` だけが宙に浮いている）。

認証は各コントローラが `session.getAttribute("authenticated")` を手書きでチェックする方式で、
**14ファイル52箇所に散在**しています。

- **CSRF対策が一切ない。** ログイン中の利用者に細工したページを踏ませるだけで、
  `/permanent-delete` などのPOSTを外部から発火させられる
- チェックの書き漏れがあればその画面は**認証なしで個人情報が見える**
  （実際、コミット `e349c58` が「認証チェック漏れの回帰対応」＝前科がある）

### 2-3. ネットワーク制限の**適用** — アプリ側は適用済み、PostgreSQL側は未適用

**アプリ側は完了しました**（2026-07-29）。`server.address` の既定値を `0.0.0.0` から
`127.0.0.1` に変更し、`netstat` で `127.0.0.1:8080` のみの待ち受けになることを実機確認済みです。

> 社内の他PCからも画面を使う運用の場合は `SERVER_ADDRESS=0.0.0.0` を設定してください。
> 「他のパソコンから画面が開けない」場合の対処は `SETUP_NEW_PC.md` 第8章に記載しています。

**PostgreSQL側も適用しました（2026-07-30）。** ただし `listen_addresses` の反映には
PostgreSQLサービスの再起動が必要で、**再起動には管理者権限が必要なため未実施**です。

状況（2026-07-30 時点）:

| 項目 | 変更前 | 現在 | 状態 |
|---|---|---|---|
| `listen_addresses` | `'*'` | `'localhost'` | ⏳ **設定済み。次回のPostgreSQL再起動で反映** |
| `log_connections` | 未設定 | `'all'` | ✅ **適用済み**（リロードのみで反映。再起動不要） |
| `pg_hba.conf` | `127.0.0.1/32` と `::1/128` のみ | 変更なし | ✅ 良好 |
| ポート5432の許可ルール | 未検出 | 変更なし | ✅ 既定でブロック中 |

**適用方法**: `postgresql.conf` を直接編集せず **`ALTER SYSTEM SET`** を使いました。
`ALTER SYSTEM` は**値を実行時に検証して不正なら弾く**ため、
「起動時に初めて設定ミスが分かって起動しない」という事故を防げます。
設定は `postgresql.auto.conf` に書き込まれます。

**反映させるコマンド**（管理者権限のPowerShell）:

```powershell
Restart-Service postgresql-x64-18
```

PostgreSQLのサービスは自動起動なので、**このコマンドを実行しなくても
次回のPC再起動時に自動的に反映されます。**

**再起動でシステムが止まらないことの根拠**（実機で確認済み）:

- `localhost` は `127.0.0.1` と `::1` の両方に解決される
- `pg_hba.conf` が許可しているのはまさにその2つ（`127.0.0.1/32` / `::1/128`）
- アプリの接続先は `jdbc:postgresql://localhost:5432/kaseihu`
- `pg_file_settings` で設定ファイルに解析エラーが無いことを確認済み
  （`listen_addresses` の `setting could not be applied` は構文エラーではなく
  「再起動が必要」を意味する通知）

**復旧手段**: `C:\Users\hyudo\pg-config-backup\` に以下を保管しています。

| ファイル | 内容 |
|---|---|
| `ROLLBACK-元に戻す手順.md` | 元に戻す手順（**PostgreSQLが起動しない場合の手順も記載**） |
| `kaseihu-*.dump` | 変更前の業務データ（求職者12・求人者12・売上明細23・紹介状16） |
| `postgresql.conf.*.bak` 他 | 変更前の設定ファイル3種 |

> データ領域 `C:\workofficetani\data` は**管理者権限なしで書き込めることを確認済み**です。
> そのため万一PostgreSQLが起動しなくなっても、`postgresql.auto.conf` を
> バックアップから戻すことで確実に復旧できます。

**ファイアウォール規則（`NETWORK_RESTRICTION.md` 第4章）は未適用です。**
`listen_addresses = 'localhost'` が反映されればPostgreSQLはLAN側で待ち受けなくなるため、
5432に対する規則の必要性は下がります。

### 2-4. 未監査の観点

- ~~ZIP一括出力のエントリ名生成のパストラバーサル観点~~
  → **監査・修正完了**（1-5-2）。`\` が抜けていた問題を検出し対処済み
- ~~`RegisterRecordRepository.findByYear` の `LIKE` はワイルドカード未エスケープ~~
  → **対処済み（2026-07-30）。** `LIKE` をやめ `substring(work_month from 1 for 4) = :year` に変更。
  以前は呼び出し側が `"2026%"` とワイルドカードを付けて渡す約束で、
  その値に利用者入力が混ざると `%` `_` が効いてしまう形だった
  （プレースホルダを使ってもLIKEのワイルドカードは防げない）。
  ワイルドカードという概念ごと無くしたので、エスケープ漏れが起こりえない
- ~~**HTTPS化**が未対応~~ → **対応済み（2026-07-30）。ただし既定はOFF。**

  **なぜ既定OFFにしたか**: `server.address` を `127.0.0.1` に絞ったため通信が
  このPCの外に出ず、盗聴の危険が無い。一方で既定ONにすると**自己署名証明書の警告が
  毎回出て、起動スクリプトも壊れる**（`http://localhost:8080` を開く作りのため）。
  効果より運用の手間・事故リスクが大きいと判断した。

  有効化に必要なもの:

  | 環境変数 | 値 |
  |---|---|
  | `HTTPS_ENABLED` | `true` |
  | `HTTPS_KEYSTORE` | キーストアのフルパス |
  | `HTTPS_KEYSTORE_PASSWORD` | キーストアのパスワード |
  | `COOKIE_SECURE` | `true` |
  | `SERVER_PORT` | `8443`（推奨） |

  > ⚠️ **`HTTPS_ENABLED` と `COOKIE_SECURE` は必ずセットで切り替えること。**
  > HTTPなのに `COOKIE_SECURE=true` だと、ブラウザがCookieを送らず**ログインできません**。
  > `StartupSecurityCheck` がこの不整合を起動時に検出して警告します。

  証明書は `scripts\create-https-certificate.ps1` で作成します（JDK付属の `keytool` を使用）。
  **`-ext "SAN=dns:localhost,ip:127.0.0.1"` が必須**で、これが無いと最近のブラウザは
  ホスト名を確認できず接続を拒否します。有効期間は825日
  （これより長いとブラウザが証明書を信頼しない仕様があるため）。

  > ⚠️ **キーストアには秘密鍵が入っています。`.gitignore` で `*.p12` `*.jks` `*.keystore`
  > を除外済み。ここを崩してコミットすると秘密鍵がGitHubに公開され、暗号化が無意味になります。**

  自己署名証明書のためブラウザに警告が出ます。消すにはWindowsの
  「信頼されたルート証明機関」への登録が必要ですが、**システム設定の変更**にあたるため
  スクリプトでは行わず案内のみにしています。

---

## 3. 利用者側の操作が必要な事項（コードでは解決できない）

| # | 内容 | 未実施だとどうなるか |
|---|------|---------------------|
| 1 | **`TOTP_ENCRYPTION_KEY` の設定** | **ログインできません**。最優先 |
| 2 | **`DB_PASSWORD` の設定** | **起動しません** |
| 3 | **`postgres` のパスワード変更** | `7136` は今も有効で、コミット履歴に残っているため誰でも知りうる |
| 4 | **GitHubトークンの失効** | `.git\config` からは除去済み（0-3）だが、**GitHub側では今も有効**。漏れていれば誰でもリポジトリを書き換えられる |
| 5 | ネットワーク制限の適用 | 2-3のとおり |
| 6 | 手順書の印刷・保管 | パソコンが使えない時に手順が読めない |
| 7 | バックアップスクリプトの作り直し | 旧版はDBパスワードが直書き。`/backup-guide` ①手順4を再実施 |

詳細と手順は `docs/SECURITY_CHECKLIST.md` 第5章、`docs/PASSWORD_POLICY.md` 第6章。

---

## 4. 作業する上での注意（このプロジェクト固有）

- **Spring Data JDBC であって JPA ではない。** `@Entity` や `EntityManager` は使わない
- コントローラの依存は `ObjectProvider` で受ける箇所がある。
  `@WebMvcTest` でBeanが無くても生成に失敗しないようにするため。この形を崩さないこと
- Thymeleafテンプレートで **`th:utext` を使わない**（全46テンプレートで不使用を維持）。
  生HTMLを埋め込みたい場合はデータURIなど別の手を使う（QRコードがその例）
- 新しい秘密の値を追加したら、`docs/PASSWORD_POLICY.md` 第2章と
  `docs/SECURITY_CHECKLIST.md` 第3章に**必ず行を追加する**
- テストを無効化して完了扱いにしない（`CLAUDE.md` の Development Rules 参照）
- **実機で動かして確認すること。** テストが通っても画面が壊れていた事例が実際にあった
  （TOTP実装時、`/login/totp` へ直接POSTして検証したため、
  ログイン画面自体がTOTP非対応のまま残っていたのを見落とした）

### スクリプトの文字コード（ハマりどころ）

`scripts/` のファイルは、種類ごとに文字コードの制約が違う。**間違えると黙って動かなくなる。**

| 種類 | 文字コード | 理由 |
|---|---|---|
| `.ps1` | **UTF-8（BOM付き）** | PowerShell 5.1 はBOMが無いUTF-8をANSI(cp932)として読むため、日本語が文字化けする |
| `.vbs` | **ASCIIのみ** | Windows Script Host は ANSI コードページで読み、**UTF-8のBOMを受け付けない**。<br>BOM無しUTF-8の日本語コメントは文字化けして「閉じていない文字列型の定数です」の構文エラーになり、<br>BOMを付けると今度は「(1,1) 無効な文字です」になる |
| `.bat` | **ASCIIのみ** | コンソールのコードページで読まれるため、環境によって文字化けする |

日本語のメッセージは**すべて `.ps1` 側に置くこと**。`.vbs` と `.bat` は
`.ps1` を呼び出すだけの薄い入り口にしてある。

---

## 5. 推奨する次の一手

1. **`TOTP_ENCRYPTION_KEY` を設定する**（これが無いとログインできない）
2. ~~コミットをpushする~~ → **完了**（0-3）。次は**GitHub上でトークンを失効させる**
3. ~~Cookie強化・ZIP対策・待ち受け制限~~ → **完了**（1-5-1 / 1-5-2 / 2-3）
4. ~~認証チェックの集約 Phase A~~ → **完了**（2-2）。`GET /` の穴も塞いだ
5. **認証チェックの集約 Phase B**（2-2）— 冗長になった52箇所を削除する
6. **CSRF対策**（2-2）— Phase Aの `AuthenticationInterceptor` に載せる。
   `SameSite=Strict`（1-5-1）は緩和策であって代替ではない
7. **対策1 DBロール分離**（2-1）— 起動不能リスクが高いので段階を分けて

---

## 改訂履歴

| 日付 | 内容 |
|------|------|
| 2026-07-29 | 初版作成 |
| 2026-07-29 | push完了に伴い0-3を更新。リモート専用コミット`325c74c`との衝突解消の記録と、削除済み機能に関わるマージの注意点を追記 |
| 2026-07-29 | セッションCookie強化(1-5-1)・ZIPパストラバーサル対策(1-5-2)・待ち受けアドレス制限(2-3)を実施。テスト90→102件。第5章の順序を「認証集約→CSRF」に整理 |
| 2026-07-29 | 認証チェック集約のPhase A(2-2)を実施。`GET /`(ウェルカムページ)が無認証だった穴を検出し閉塞。テスト102→109件 |
| 2026-07-30 | 認証チェック集約のPhase B(2-2)を実施。手書きチェックを16ファイルから削除(-306行)。全104エンドポイントを実機で網羅確認。`findByYear`のLIKE(2-4)も対処 |
| 2026-07-30 | CSRF対策(2-2)をOriginヘッダ検証で実装。テスト109→116件 |
| 2026-07-30 | HTTPS対応(2-4)を既定OFFの追加設定として実装。証明書作成スクリプトを追加。テスト116→119件 |
| 2026-07-30 | ネットワーク制限のPostgreSQL側(2-3)を適用。`log_connections='all'`は反映済み、`listen_addresses='localhost'`は再起動待ち。復旧材料を `C:\Users\hyudo\pg-config-backup\` に保管 |
| 2026-07-30 | 対策1 DBロール分離(2-1)のStage1(DB側)とStage2(アプリ側)を実施。3ロール作成・所有権移管・マイグレーション専用接続を追加。切り替え(Stage3)は利用者操作。`docs/EMERGENCY_ACCESS.md` を新規作成 |
