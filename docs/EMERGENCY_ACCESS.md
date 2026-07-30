# 緊急アクセス手順（DBロール分離後）

対策1（最小権限の原則）でDBロールを分離したあと、
**権限が原因でシステムが動かなくなったとき**に読む手順書です。

最終更新: 2026-07-30

> 📌 **この文書は印刷して、パソコンとは別の場所に保管してください。**
> パソコンが使えない状況で必要になる手順です。

---

## 1. ロール構成

| ロール | 権限 | 誰が使うか | 失うと何が起きるか |
|---|---|---|---|
| `postgres` | スーパーユーザー（**緊急用に温存**） | 人（緊急時のみ） | 復旧手段が無くなる |
| `kaseihu_owner` | テーブルの所有者。DDL | `DatabaseMigrationRunner`（起動時のみ） | **起動時のマイグレーションが失敗し、システムが起動しない** |
| `kaseihu_app` | SELECT/INSERT/UPDATE/DELETE | アプリの通常動作 | **システムが起動しない** |
| `kaseihu_ro` | SELECT のみ | バックアップ（pg_dump）・調査 | 自動バックアップが失敗する |

`postgres` を残しているのは、他のロールのパスワードを失ったときに
それを直せる手段が必要だからです。**`postgres` のパスワードは必ず別に保管してください。**

---

## 2. 症状から探す

| 症状 | 原因 | 対処 |
|---|---|---|
| 起動時に「**権限不足**」と出て起動しない | 制限ロールでDDLを実行しようとした | → 3-1 |
| 起動時に `password authentication failed` | ロールのパスワードが違う | → 3-2 |
| 特定の画面だけ「permission denied」で落ちる | **新しいテーブルに権限が付いていない** | → 3-3 |
| とにかく今すぐ動かしたい | — | → 3-4（ロール分離を取り消す） |

---

## 3. 対処

### 3-1. 「権限不足」で起動しない

`DatabaseMigrationRunner` は起動のたびに `ALTER TABLE` / `CREATE TABLE` を実行します。
これには**テーブルの所有者権限**が必要です。

アプリの通常接続（`kaseihu_app`）にはDDL権限が無いため、
**マイグレーション専用の接続情報を設定していないと必ずここで失敗します。**

```powershell
[Environment]::SetEnvironmentVariable("DB_MIGRATION_USER", "kaseihu_owner", "User")
[Environment]::SetEnvironmentVariable("DB_MIGRATION_PASSWORD", "（所有者のパスワード）", "User")
```

設定後、**PowerShellを開き直してから**起動し直してください。

### 3-2. パスワードが違う

`postgres` で入り直して、ロールのパスワードを付け替えます。

```powershell
$env:PGPASSWORD = "（postgresのパスワード）"
& "C:\workofficetani\bin\psql.exe" -U postgres -h localhost -d kaseihu -c "ALTER ROLE kaseihu_app PASSWORD '（新しいパスワード）';"
```

そのうえで環境変数 `DB_PASSWORD` を同じ値に更新し、PowerShellを開き直します。

### 3-3. 特定の画面だけ権限エラーになる

**新しいテーブルを追加したあとに起こります。** 最も気付きにくい不具合です。

`create-db-roles.sql` では
`ALTER DEFAULT PRIVILEGES FOR ROLE kaseihu_owner` を設定しているため、
**`kaseihu_owner` が作ったテーブルには自動で権限が付きます**。

しかし `postgres` や別のロールでテーブルを作った場合、この既定権限は効きません。
その場合は手で権限を付けます。

```powershell
$env:PGPASSWORD = "（postgresのパスワード）"
& "C:\workofficetani\bin\psql.exe" -U postgres -h localhost -d kaseihu -c "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO kaseihu_app; GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO kaseihu_app; GRANT SELECT ON ALL TABLES IN SCHEMA public TO kaseihu_ro;"
```

> **再発防止**: 新しいテーブルは必ず `DatabaseMigrationRunner`（＝`kaseihu_owner`）で
> 作ってください。手でテーブルを作らないこと。

### 3-4. ロール分離を取り消して、とにかく動かす

**業務データは一切消えません。** 所有者と権限だけを元に戻します。

```powershell
$env:PGPASSWORD = "（postgresのパスワード）"
& "C:\workofficetani\bin\psql.exe" -U postgres -h localhost -d kaseihu -f "C:\Users\hyudo\workspace\workOfficeTani\scripts\rollback-db-roles.sql"
```

そのうえで環境変数を元に戻します。

```powershell
[Environment]::SetEnvironmentVariable("DB_USER", $null, "User")
[Environment]::SetEnvironmentVariable("DB_MIGRATION_USER", $null, "User")
[Environment]::SetEnvironmentVariable("DB_MIGRATION_PASSWORD", $null, "User")
[Environment]::SetEnvironmentVariable("DB_PASSWORD", "（postgresのパスワード）", "User")
```

PowerShellを開き直してから起動し直してください。

---

## 4. 保管しておくべき値

`docs/SECURITY_CHECKLIST.md` 第3章の一覧に加えて、以下も**紙で**保管してください。

| 値 | 失うとどうなるか |
|---|---|
| `postgres` のパスワード | **すべての復旧手段が無くなる。最重要** |
| `kaseihu_owner` のパスワード | 起動時のマイグレーションができない（→ 3-2で付け替え可能） |
| `kaseihu_app` のパスワード | システムが起動しない（→ 3-2で付け替え可能） |
| `kaseihu_ro` のパスワード | 自動バックアップが失敗する（→ 3-2で付け替え可能） |

**`postgres` のパスワード以外は、失っても `postgres` で付け替えられます。**
逆に言えば、`postgres` のパスワードだけは絶対に失わないでください。

---

## 5. 権限が正しいか点検する

定期点検（`docs/SECURITY_CHECKLIST.md`）に合わせて実行してください。

```powershell
$env:PGPASSWORD = "（postgresのパスワード）"
& "C:\workofficetani\bin\psql.exe" -U postgres -h localhost -d kaseihu -c "SELECT rolname, rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname LIKE 'kaseihu%';"
```

**`rolsuper` / `rolcreatedb` / `rolcreaterole` がすべて `f` であること。**
どれかが `t` になっていたら分離した意味が無くなっています。

```powershell
& "C:\workofficetani\bin\psql.exe" -U postgres -h localhost -d kaseihu -c "SELECT tableowner, COUNT(*) FROM pg_tables WHERE schemaname='public' GROUP BY tableowner;"
```

**すべて `kaseihu_owner` 所有であること。** `postgres` 所有のテーブルが混じっていると、
そのテーブルは `kaseihu_app` から見えず、3-3の症状が出ます。
