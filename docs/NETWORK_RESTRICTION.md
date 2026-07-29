# ネットワークアクセス制限 設計・設定手順

本システムのデータベース（PostgreSQL）とアプリ画面（ポート8080）に、
**どこからアクセスできるか**を必要最小限に絞るための設計と手順です。

対象読者：本システムを運用する担当者
前提OS：Windows

---

## 0. 現状の診断結果（2026-07-29 時点・開発機）

| 項目 | 現在の値 | 評価 |
|------|---------|------|
| `listen_addresses` | `'*'` | ⚠️ **要修正**。全ネットワークインターフェースで待ち受けている |
| `port` | `5432` | 標準 |
| `pg_hba.conf` の許可範囲 | `127.0.0.1/32` と `::1/128` のみ | ✅ 良好 |
| 認証方式 | `scram-sha-256` | ✅ 良好 |
| ポート5432 のインバウンド許可ルール | 未検出 | ✅ 既定でブロック中。ただし明示的な拒否ルールはない |
| `log_connections` / `log_disconnections` | 未設定（無効） | ⚠️ **要修正**。接続の試行が記録されない |
| アプリのバインドアドレス | 未指定（`0.0.0.0` = 全インターフェース） | ⚠️ 要検討 |

### この状態が意味すること

**扉は二重にあり、内側の扉（`pg_hba.conf`）は閉まっていますが、外側の扉（`listen_addresses`）が開いています。**

社内LANにつながっている他のPCから、`telnet （このPCのIP） 5432` で**接続そのものは成立します**。
認証は `pg_hba.conf` によって拒否されるためデータは取れませんが、以下のリスクが残ります。

1. PostgreSQLが動いていること・バージョンが外部から分かる（攻撃の下調べに使われる）
2. 認証前の処理を狙った脆弱性が将来見つかった場合、直撃する
3. 大量の接続要求でサービスを止められる（DoS）
4. `log_connections` が無効なため、上記の試行が**一切記録に残らない**

---

## 1. 設計方針

### 1-1. 三層で守る（多層防御）

1つの設定に頼らず、独立した3つの層で制限します。どれか1つが破られても残りが効きます。

```
  ┌─ 第1層：Windowsファイアウォール ──────────────┐
  │  ポート5432・8080への外部からの通信を明示的に拒否   │
  │                                              │
  │  ┌─ 第2層：listen_addresses ──────────────┐  │
  │  │  PostgreSQLがlocalhostでしか待ち受けない   │  │
  │  │                                        │  │
  │  │  ┌─ 第3層：pg_hba.conf ──────────────┐ │  │
  │  │  │  127.0.0.1 以外からの認証を拒否     │ │  │
  │  │  │                                  │ │  │
  │  │  │        【データベース】            │ │  │
  │  │  └──────────────────────────────────┘ │  │
  │  └────────────────────────────────────────┘  │
  └──────────────────────────────────────────────┘
```

### 1-2. なぜ「特定IPアドレスの許可」ではなく「localhostのみ」なのか

`listen_addresses` に社内の固定IP（例：`192.168.1.10`）を書く方式は、
**DHCPでIPアドレスが変わった瞬間にPostgreSQLが起動しなくなります**。
運用担当者自身が締め出される（ロックアウトする）典型的なパターンです。

`localhost` は、ネットワーク構成が変わってもPCの再設置をしても**絶対に変わりません**。
本システムはアプリとDBが同じPCで動くため、`localhost` のみで業務要件を満たせます。

> 📌 **将来、社内の別PCからも画面を使いたくなった場合**
> DBの `listen_addresses` は `localhost` のままにしてください。
> 開放するのは**アプリのポート8080だけ**です（→ 第4章）。
> アプリを経由させれば、監査ログ（対策2）とログイン認証（対策4）が必ず通ります。
> DBポートを直接開けると、その両方を素通りされます。

### 1-3. ロックアウトを防ぐ「緊急アクセス用の例外経路」

制限を強めると、設定を間違えたときに**自分も入れなくなります**。
そのため、以下の3段階の脱出経路を必ず確保します。

| 段階 | 状況 | 脱出方法 |
|------|------|---------|
| 1 | ネットワーク設定を間違えた | **本体PCのコンソール**から `psql -h localhost` で接続（`listen_addresses='localhost'` である限り常に可能） |
| 2 | `pg_hba.conf` を壊してPostgreSQLが起動しない | バックアップした `pg_hba.conf.bak` を書き戻して再起動（→ 3-2） |
| 3 | それでも起動しない／パスワードも分からない | **シングルユーザーモード**で起動（→ 第6章）。認証を経由せずDBを直接操作できる最終手段 |

> ⚠️ 段階3のシングルユーザーモードは「PCの物理的なコンソールを操作できる人」なら誰でも実行できます。
> これは緊急時の命綱であると同時に、**PCそのものへの物理アクセスを許すと全てが無意味になる**ことを意味します。
> PCの施錠管理・Windowsのサインインパスワードは、この手順書と同じくらい重要です。

---

## 2. 設定変更の前にやること（必須）

**設定を1文字でも変える前に、以下を必ず実行してください。**

### 2-1. データのバックアップ

```powershell
& "C:\workOfficeTani\backup\backup.ps1"
```

「Backup OK: （ファイルパス）」と表示されることを確認します。

### 2-2. 設定ファイルのバックアップ

PostgreSQLのデータディレクトリを確認します（開発機は `C:\workofficetani\data`、
本番機では異なる可能性があります）。

```powershell
$env:PGPASSWORD = "（postgresのパスワード）"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d postgres -c "SHOW data_directory;"
```

表示されたパスを `$PGDATA` として、設定ファイルを複製します。

```powershell
$PGDATA = "（上で表示されたパス）"
Copy-Item "$PGDATA\postgresql.conf" "$PGDATA\postgresql.conf.bak" -Force
Copy-Item "$PGDATA\pg_hba.conf"     "$PGDATA\pg_hba.conf.bak"     -Force
Get-ChildItem "$PGDATA\*.bak"
```

> 📌 `.bak` ファイルは**同じフォルダに置いています**。これは「設定を間違えた」場合の即時復旧用です。
> 「PCごと失う」場合には役に立たないため、`docs/INCIDENT_RESPONSE.md` 第0章のとおり、
> 別媒体にも複製しておいてください。

### 2-3. 現在の設定を記録する

戻せるように、変更前の値を紙またはUSBメモリに控えます。

```powershell
Select-String -Path "$PGDATA\postgresql.conf" -Pattern '^\s*(listen_addresses|port|log_connections|log_disconnections)'
Get-Content "$PGDATA\pg_hba.conf" | Where-Object { $_ -match '^\s*(host|local|hostssl|hostnossl)' }
```

---

## 3. PostgreSQL の設定変更

### 3-1. `listen_addresses` を localhost に限定する

`$PGDATA\postgresql.conf` をメモ帳等で開き、以下の行を探します。

```
listen_addresses = '*'
```

これを次のように書き換えて保存します。

```
listen_addresses = 'localhost'
```

> ⚠️ `listen_addresses` は**再起動が必要な設定**です（`reload` では反映されません）。

```powershell
Restart-Service postgresql-x64-17
```

サービス名が分からない場合は以下で確認します。

```powershell
Get-Service | Where-Object { $_.Name -like 'postgresql*' }
```

**反映確認**：

```powershell
$env:PGPASSWORD = "（postgresのパスワード）"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d postgres -c "SHOW listen_addresses;"
```

`localhost` と表示されればOKです。あわせて、実際に外部から見えなくなったことを確認します。

```powershell
netstat -ano | Select-String ":5432"
```

`0.0.0.0:5432` や `[::]:5432` が消え、`127.0.0.1:5432` だけになっていれば成功です。

### 3-2. 起動しなくなった場合の戻し方

```powershell
Copy-Item "$PGDATA\postgresql.conf.bak" "$PGDATA\postgresql.conf" -Force
Restart-Service postgresql-x64-17
```

起動しない原因は、PostgreSQLのログで確認できます。

```powershell
Get-ChildItem "$PGDATA\log" | Sort-Object LastWriteTime -Descending | Select-Object -First 1 | Get-Content -Tail 40
```

### 3-3. 接続の記録を有効にする（対策2・監査ログと連動）

`$PGDATA\postgresql.conf` に以下を追記（または既存行のコメントを外して変更）します。

```
log_connections = on
log_disconnections = on
log_line_prefix = '%m [%p] %u@%d from %h '
```

これにより「**いつ・誰が・どのIPから**DBに接続したか」がPostgreSQL側のログに残ります。
アプリ側の監査ログ（対策2）が消された場合でも、こちらが残る可能性があります。

この3つは `reload` で反映できます（再起動不要）。

```powershell
& "C:\Program Files\PostgreSQL\17\bin\pg_ctl.exe" reload -D "$PGDATA"
```

**反映確認**：

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d postgres -c "SHOW log_connections;"
```

> 📌 ログの出力先は `$PGDATA\log\` です。**DBのデータと同じフォルダにあります。**
> ランサムウェアや攻撃者にとっては同じ場所＝同時に消せる場所です。
> 対策2で、このログを別媒体へ定期コピーする仕組みを整備します。

### 3-4. `pg_hba.conf` は現状維持でよい

現在の設定は既に適切です。**変更しないでください。**

```
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256
```

> ⚠️ **絶対にやってはいけない設定**
> - `host all all 0.0.0.0/0 trust` … 全世界からパスワードなしで接続可能になります
> - `host all all 0.0.0.0/0 scram-sha-256` … パスワード総当たりの標的になります
> - `local all all trust` … Windowsでは `local` 行は使われませんが、書かないこと

> 📌 Windows版PostgreSQLはUNIXドメインソケットを使いません。
> そのため `local` で始まる行は無視されます。緊急時も `-h localhost` での接続が基本経路です。

---

## 4. Windowsファイアウォールの設定

現在、ポート5432・8080への**明示的な許可ルールは存在せず**、Windowsの既定でブロックされています。
しかしこれは「たまたま許可されていない」状態です。
将来、何かのソフトのインストール時に「このアプリの通信を許可しますか？」で誤って許可すると、
その瞬間に開いてしまいます。

そこで、**明示的な拒否ルール**を作ります。Windowsファイアウォールでは、
**拒否（Block）ルールは許可（Allow）ルールより優先される**ため、後から誤って許可しても効き続けます。

> ⚠️ 以下は管理者権限のPowerShellで実行してください。

### 4-1. DBポート（5432）を外部から遮断する

```powershell
New-NetFirewallRule -DisplayName "kaseihu-BLOCK-PostgreSQL-5432-from-LAN" -Direction Inbound -Protocol TCP -LocalPort 5432 -RemoteAddress Any -Action Block -Profile Any
```

localhost 内部の通信（アプリ→DB）はファイアウォールを通過しないため、この規則の影響を受けません。
**アプリは今までどおり動作します。**

### 4-2. アプリのポート（8080）の扱いを決める

運用形態によって選択してください。

| 運用形態 | 設定 |
|---------|------|
| **A. このPCだけで使う**（現行の想定） | 8080も遮断する → 4-2-A |
| **B. 社内の他PCからも画面を使う** | 社内サブネットのみ許可 → 4-2-B |

#### 4-2-A. このPCだけで使う場合

```powershell
New-NetFirewallRule -DisplayName "kaseihu-BLOCK-App-8080-from-LAN" -Direction Inbound -Protocol TCP -LocalPort 8080 -RemoteAddress Any -Action Block -Profile Any
```

さらにアプリ自体をlocalhostにバインドすると、より確実です（→ 第5章）。

#### 4-2-B. 社内の他PCからも使う場合

まず自分のPCのIPアドレスとサブネットを確認します。

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' } | Select-Object IPAddress, PrefixLength, InterfaceAlias
```

社内サブネット（例：`192.168.1.0/24`）**のみ**を許可し、それ以外は拒否します。

```powershell
New-NetFirewallRule -DisplayName "kaseihu-ALLOW-App-8080-from-office" -Direction Inbound -Protocol TCP -LocalPort 8080 -RemoteAddress 192.168.1.0/24 -Action Allow -Profile Private
New-NetFirewallRule -DisplayName "kaseihu-BLOCK-App-8080-others"      -Direction Inbound -Protocol TCP -LocalPort 8080 -RemoteAddress Any            -Action Block -Profile Public
```

> ⚠️ この場合でも、**DBポート5432は開けないでください**（4-1の拒否ルールは維持）。
> 他PCからはアプリ画面を経由させ、ログイン認証と監査ログを必ず通す設計にします。

### 4-3. 設定の確認

```powershell
Get-NetFirewallRule -DisplayName "kaseihu-*" | Select-Object DisplayName, Direction, Action, Enabled
```

### 4-4. ロックアウトからの復帰（ルールの削除）

制限が原因で業務が止まった場合、以下で個別に削除できます。

```powershell
Remove-NetFirewallRule -DisplayName "kaseihu-BLOCK-App-8080-from-LAN"
```

作成したルールを全部消したい場合：

```powershell
Get-NetFirewallRule -DisplayName "kaseihu-*" | Remove-NetFirewallRule
```

> 📌 ファイアウォールのルールは**このPC上にしか存在しません**。
> どのルールを作ったかを `docs/INCIDENT_RESPONSE.md` 第4章の棚卸し表と同様に、
> 紙にも控えておいてください。PCが初期化された時に再現できなくなります。

---

## 5. アプリ側のバインドアドレス制限（任意・4-2-Aの場合）

`application.yml` に以下を追加すると、アプリが待ち受けるアドレスを環境変数で制御できます。

```yaml
server:
  # 待ち受けアドレス。未設定なら従来どおり全インターフェース(0.0.0.0)で待ち受ける。
  # このPCだけで使う運用なら SERVER_ADDRESS=127.0.0.1 を設定する。
  address: ${SERVER_ADDRESS:0.0.0.0}
```

**環境変数を設定しない限り動作は今までと完全に同じ**です。
このPCだけで使う運用に切り替える場合のみ、環境変数を設定してください。

```powershell
# 一時的に試す場合
$env:SERVER_ADDRESS = "127.0.0.1"
```

**確認方法**：設定後にアプリを起動し、以下で `127.0.0.1:8080` だけが表示されればOKです。

```powershell
netstat -ano | Select-String ":8080"
```

> ⚠️ 4-2-B（他PCからも使う）運用の場合は、この設定を**してはいけません**。
> 設定すると他PCから画面が開けなくなります。

---

## 6. 緊急アクセス経路：シングルユーザーモード

`pg_hba.conf` を壊した・パスワードが全て分からなくなった場合の**最終手段**です。
認証を一切経由せずにデータベースを操作できます。

> ⚠️ この操作中、PostgreSQLサービスは停止しており、アプリからは接続できません。
> 必ず業務時間外に、第2章のバックアップを取得済みの状態で行ってください。

### 手順

1. PostgreSQLサービスを停止する

```powershell
Stop-Service postgresql-x64-17
```

2. シングルユーザーモードで起動する

```powershell
& "C:\Program Files\PostgreSQL\17\bin\postgres.exe" --single -D "$PGDATA" postgres
```

3. プロンプト（`backend>`）が出たら、必要な操作を1行ずつ入力する
   例：パスワードのリセット

```
ALTER ROLE postgres WITH PASSWORD 'new-strong-password';
```

4. `Ctrl + D` で終了し、サービスを再開する

```powershell
Start-Service postgresql-x64-17
```

5. **作業後、必ず通常の方法で接続できることを確認する**

```powershell
$env:PGPASSWORD = "new-strong-password"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d kaseihu -c "SELECT count(*) FROM persons;"
```

---

## 7. 設定完了後の確認チェックリスト

すべてに ☑ が付くことを確認してください。

- ☐ `SHOW listen_addresses;` が `localhost` を返す
- ☐ `netstat -ano | Select-String ":5432"` に `127.0.0.1:5432` のみが表示される（`0.0.0.0` がない）
- ☐ `SHOW log_connections;` が `on` を返す
- ☐ `Get-NetFirewallRule -DisplayName "kaseihu-*"` に拒否ルールが表示される
- ☐ **アプリが起動し、ログインでき、求職者一覧が表示される**（最重要）
- ☐ バックアップスクリプトが正常に完走する（`& "C:\workOfficeTani\backup\backup.ps1"`）
- ☐ `postgresql.conf.bak` / `pg_hba.conf.bak` を別媒体にも複製した
- ☐ 作成したファイアウォールルール名を紙に控えた

**「アプリが起動し、ログインでき、一覧が表示される」の確認を飛ばさないでください。**
ネットワーク制限の失敗は、翌朝の業務開始時に発覚するのが最悪のパターンです。

---

## 8. 「唯一の命綱」チェック（対策5分）

| 守るもの | 命綱 | ❌ 危険な置き方 | ✅ 正しい置き方 |
|---------|------|----------------|----------------|
| DBへのネットワーク経路 | `pg_hba.conf` / `postgresql.conf` | 変更前バックアップを同じフォルダにだけ置く | 同フォルダ＋別媒体（USB・別クラウド） |
| 制限からの脱出手段 | シングルユーザーモードの手順 | この文書（リポジトリ内）だけ | 紙に印刷して鍵付き引き出しへ |
| ファイアウォール設定 | ルール一覧 | PC内のFW設定にのみ存在 | ルール名と内容を紙にも控える |
| 接続の記録 | PostgreSQLログ | `$PGDATA\log\`（DBと同じ場所） | 別媒体へ定期コピー（対策2で自動化） |

---

## 改訂履歴

| 日付 | 内容 |
|------|------|
| 2026-07-29 | 初版作成（対策5）。開発機の診断結果を反映 |
