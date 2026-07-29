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

### 2-1. 対策1: 最小権限の原則（DBロール分離）— **未着手**

**現状のリスク**: アプリがPostgreSQLの**スーパーユーザー `postgres`** で接続している。
SQLインジェクションが1つ通る、あるいはアプリが乗っ取られた瞬間に
`DROP DATABASE` も他DBの閲覧も自由にできる。権限の壁がゼロ。

**検討済みの実装方針**（未実装）

- DBロールを4分割: `kaseihu_owner`（DDL専用）/ `kaseihu_app`（通常業務）/
  `kaseihu_ro`（参照とpg_dump）/ `kaseihu_admin`（削除可）。`postgres` は緊急用に温存
- アプリ側を2 DataSource化。既定は `kaseihu_app`、
  `DatabaseMigrationRunner` と `PermanentDeleteController` のみ `kaseihu_admin`
- 環境変数未設定なら従来どおり単一接続にフォールバック（既存の起動方法を壊さない）
- 緊急アカウント手順書 `docs/EMERGENCY_ACCESS.md` を新規作成

> ⚠️ **最大の落とし穴**: `DatabaseMigrationRunner` が起動のたびに `ALTER TABLE` を実行する。
> アプリの接続ロールを非所有者に降格すると、**次回起動時に権限エラーで落ちて
> システムが起動しなくなる**（`run()` が例外を再スローするため）。
> ここを踏まない設計が対策1の肝。

**この項目は本システムで最も起動不能リスクが高い。** 段階を分けて進めること。

### 2-2. CSRF対策・認証チェックの集約 — **未対応**

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

### 2-3. ネットワーク制限の**適用** — 手順のみ

`docs/NETWORK_RESTRICTION.md` に設計と手順はありますが、**設定は適用されていません**。

診断結果（2026-07-29 時点）:

| 項目 | 現在の値 | 評価 |
|---|---|---|
| `listen_addresses` | `'*'` | ⚠️ 全ネットワークで待ち受け |
| `pg_hba.conf` | `127.0.0.1/32` と `::1/128` のみ | ✅ 良好 |
| ポート5432の許可ルール | 未検出 | ✅ 既定でブロック中（明示的な拒否ルールはない） |
| `log_connections` | 未設定 | ⚠️ 接続の試行が記録されない |

> PostgreSQL設定とファイアウォールの変更は**システム設定の変更**にあたるため、
> 手順の提供のみとし、適用は行っていません。

### 2-4. 未監査の観点

- **ZIP一括出力のエントリ名生成**（`IntroductionController.exportPdfZip`、
  `ReceiptMenuController.issuedListExportPdf`）のパストラバーサル観点
- `RegisterRecordRepository.findByYear` の `LIKE` はワイルドカード未エスケープ。
  **現状どこからも呼ばれていない**ため実害なし。将来使う場合は要対処

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

---

## 5. 推奨する次の一手

1. **`TOTP_ENCRYPTION_KEY` を設定する**（これが無いとログインできない）
2. ~~コミットをpushする~~ → **完了**（0-3）。次は**GitHub上でトークンを失効させる**
3. **CSRF対策・認証チェックの集約**（2-2）— 影響範囲は広いが、リスクは対策1より大きい
4. **対策1 DBロール分離**（2-1）— 起動不能リスクが高いので段階を分けて

---

## 改訂履歴

| 日付 | 内容 |
|------|------|
| 2026-07-29 | 初版作成 |
| 2026-07-29 | push完了に伴い0-3を更新。リモート専用コミット`325c74c`との衝突解消の記録と、削除済み機能に関わるマージの注意点を追記 |
