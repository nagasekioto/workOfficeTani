# 新しいパソコンでのセットアップ手順書

このシステム（家政婦紹介事務所 人物管理システム）を、**まだ何も入っていない新しいパソコン（本番機）**で
動かすための手順です。パソコンの初期状態から順番に進めれば、そのまま使えるようになります。

対象OS：Windows（PowerShellを使用）

---

## 全体の流れ

1. 必要なソフトウェアをインストールする（VSCode／Git／Java／PostgreSQL＋pgAdmin）
2. システムのプログラム一式を取得する（GitHubからダウンロード）
3. データベースを準備する
4. 設定ファイルを確認する
5. システムを起動する
6. 動作確認・ログイン
7. バックアップを設定する
8. うまくいかないときのチェックリスト

---

## 1. 必要なソフトウェアをインストールする

### 1-1. Visual Studio Code（VSCode）
プログラムの中身を見たり、簡単な修正をするためのエディタです。

- https://code.visualstudio.com/ を開き、「Download for Windows」からダウンロードしてインストール
- インストール時の設定はすべて標準（デフォルト）のままでOK

### 1-2. Git（バージョン管理ソフト）
GitHubからプログラム一式を取得するために必要です。

- https://git-scm.com/download/win からダウンロードしてインストール
- インストール時の設定はすべて標準のままでOK

インストール後、確認します（PowerShellで実行）。
```powershell
git --version
```
バージョン番号が表示されればOKです。

### 1-3. Java（JDK 17）
このシステムはJavaで作られているため、Java本体（JDK）が必要です。

- https://adoptium.net/ を開き、「Latest LTS Release」から **バージョン17（またはそれ以降）** の
  Windows用インストーラー（.msi）をダウンロードしてインストール
- インストール時に「Set JAVA_HOME」「Add to PATH」のチェックボックスが出てきたら、**両方ともチェックを入れる**

インストール後、確認します。
```powershell
java -version
```
`17`または`21`など、17以降のバージョンが表示されればOKです。

### 1-4. PostgreSQL（データベース）＋ pgAdmin
このシステムが使うデータベース本体です。pgAdminは中身を確認するための管理画面です。

- https://www.postgresql.org/download/windows/ を開き、インストーラーをダウンロード
- インストール中に聞かれること：
  - **パスワード**：PostgreSQLの管理者（postgres）用パスワードを設定します。**必ずメモしてください**
    （後で`application.yml`という設定ファイルに、このパスワードを書く必要があります）
  - **ポート番号**：初期値の`5432`のままでOK
  - **Stack Builder** を起動するか聞かれたら、不要なので閉じてOK
  - インストーラーには**pgAdminも一緒に含まれています**（追加インストール不要）

インストール後、スタートメニューから「pgAdmin 4」を開いて起動できることを確認してください
（初回はマスターパスワードの設定を求められることがあります）。

---

## 2. システムのプログラム一式を取得する

作業用のフォルダ（例：`C:\workofficetani`）に、GitHubからプログラムをダウンロードします。

```powershell
cd C:\
git clone https://github.com/nagasekioto/workOfficeTani.git
cd workOfficeTani
```

このリポジトリが非公開（プライベート）の場合は、GitHubのアクセストークンが必要になることがあります。
その場合は以下のようにトークン付きのURLでcloneしてください（`<トークン>`は実際のトークンに置き換え）。

```powershell
git clone https://<トークン>@github.com/nagasekioto/workOfficeTani.git
```

---

## 3. データベースを準備する

### 3-1. データベースを新規作成する

PowerShellで、PostgreSQLに付属する`psql`コマンドを使います（パスは環境によって異なる場合があるので、
見つからない場合は下記「8. チェックリスト」の方法で探してください）。

```powershell
$env:PGPASSWORD = "（1-4で設定したPostgreSQLのパスワード）"
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -c "CREATE DATABASE kaseihu;"
```

### 3-2. テーブルを作成する

まず、土台となるテーブル（persons・customers・sales・sales_details・schedules）を作成する
`schema-bootstrap.sql` を流し込みます。**これを飛ばすと、後続の手順でテーブルが正しく作成されません。**

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d kaseihu -f "C:\workofficetani\src\main\resources\schema-bootstrap.sql"
```

続けて、このシステムのフォルダに入っている統合スキーマファイル`schema-all.sql`を流し込みます
（追加のカラムやその他のテーブルが反映されます）。

```powershell
& "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -d kaseihu -f "C:\workofficetani\src\main\resources\schema-all.sql"
```

エラーが出ずに完了すれば、必要なテーブルがすべて作成されます。

---

## 4. 設定ファイルを確認する

`C:\workofficetani\src\main\resources\application.yml` をVSCodeで開き、
PostgreSQLのパスワードが実際の環境と合っているか確認します。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/kaseihu
    username: postgres
    password: （1-4で設定した実際のパスワード）
    driver-class-name: org.postgresql.Driver
```

パスワードが違う場合は書き換えて保存してください。

---

## 5. システムを起動する

```powershell
cd C:\workofficetani
.\mvnw clean spring-boot:run
```

**初回はMaven本体のダウンロードが走るため、数分かかります。** インターネット接続が必要です。
（会社のファイアウォールやセキュリティソフトによっては、この時だけ通信がブロックされることがあります。
その場合はネットワーク管理者にご確認ください）

画面に以下のような表示が出れば起動成功です。

```
Started PersonManagementApplication in X.XXX seconds
```

---

## 6. 動作確認・ログイン

ブラウザで以下を開きます。

```
http://localhost:8080/login
```

パスワード欄に `tani` と入力してログインできれば成功です
（このパスワードは `src/main/java/.../controller/LoginController.java` 内の `CORRECT_PASSWORD` で
設定されています。変更したい場合はこの値を書き換えてください）。

ログイン後、「初期画面」が表示され、1-1〜1-7の各メニューが操作できることを確認してください。

---

## 7. バックアップを設定する

データが壊れたときのために、自動バックアップの設定を必ず行ってください。
詳しい手順は、システム内の **「その他（1-7）」→「1-7-5 バックアップ手順」** にまとめてあります。
ログイン後、ブラウザでそのままご覧いただけます。

---

## 8. うまくいかないときのチェックリスト

| 症状 | 対応 |
|---|---|
| `java -version`が認識されない | パソコンを再起動してから再度試す（PATHの反映に再起動が必要な場合がある） |
| PostgreSQLの実行ファイルの場所が分からない | `Get-ChildItem -Path C:\ -Filter "psql.exe" -Recurse -ErrorAction SilentlyContinue -Force` でパソコン全体を検索する |
| `.\mvnw`実行時に「スクリプトの実行が無効」と出る | `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser -Force` を実行してから再度試す |
| 起動時に「Port 8080 was already in use」と出る | 別のアプリや前回の起動が残っている。`netstat -ano \| findstr :8080` でプロセスIDを確認し、`Stop-Process -Id （番号） -Force` で終了してから再度起動する |
| ログイン画面でパスワードが通らない | `LoginController.java`内の`CORRECT_PASSWORD`の値と、実際に入力しているパスワードを確認する |
| PowerShellにコマンドを貼り付けると文字化け・引用符エラーが出る | メモ帳等を経由せず、PowerShell上で直接コマンドを実行する（「1-7-5 バックアップ手順」内の注意点も参照） |

---

## 9. 自動起動の設定（パソコンにログインしたら自動で立ち上がるようにする・オプション）

パソコンの操作に慣れていない方が使う場合、毎回VSCode・ターミナルを開いて
`mvnw spring-boot:run` を打つのは大変です。以下の設定をしておくと、
**パソコンにログインするだけで、裏側で自動的にシステムが起動し、
少し待つとブラウザにログイン画面が自動で開く**ようになります。

### 9-1. 実行可能な jar ファイルを作る（最初の1回だけ）

`mvnw spring-boot:run` はソースコードを毎回コンパイルするため起動が遅いので、
自動起動用には、あらかじめビルド済みの1つのファイル（jar）を作っておきます。

```powershell
cd C:\workOfficeTani
.\mvnw clean package -DskipTests
```

成功すると `C:\workOfficeTani\target\person-management-0.0.1-SNAPSHOT.jar`
というファイルができます。

**注意**：今後プログラムを修正した場合は、このjarファイルは自動更新されないため、
修正のたびに `.\mvnw clean package -DskipTests` を再実行してjarを作り直してください。

### 9-2. 自動起動スクリプトをスタートアップフォルダに置く

1. `C:\workOfficeTani\scripts\start-system.vbs` を右クリック→コピー
2. キーボードで **Windowsキー + R** を押し、`shell:startup` と入力してEnter
   （Windowsの「スタートアップ」フォルダが開きます）
3. コピーした `start-system.vbs` をこのフォルダに貼り付け

### 9-3. 動作確認

パソコンを再起動（またはサインアウトしてサインインし直す）してください。
20秒ほど待つと、自動的にブラウザが開いて `http://localhost:8080/login` の
ログイン画面が表示されれば成功です。

裏側でシステムが正しく起動しているかは、以下のログファイルで確認できます。

```
C:\workOfficeTani\system-log.txt
```

### 9-4. 手動で今すぐ起動・停止したいとき

`start-system.vbs` はダブルクリックでもいつでも手動実行できます
（デスクトップにこのファイルのショートカットを作っておくと便利です）。

システムを停止したい場合は、PowerShellで以下を実行してください
（`(PID)`の部分は前段のコマンドで表示されたプロセスIDに置き換え）。

```powershell
netstat -ano | findstr :8080
Stop-Process -Id (PID) -Force
```

### 9-5. プログラムが更新されたときの反映方法

`git pull` で最新版を取り込んでも、9-1で作った jar ファイルは
自動更新されません。以下のいずれかの方法で反映してください。

**おすすめ：更新スクリプトを使う（1クリック）**

`C:\workOfficeTani\scripts\update-system.bat` をダブルクリックすると、
以下を自動で行います。

1. 動いているシステムを安全に停止
2. `git pull`（最新版の取得）
3. jar ファイルの作り直し（ビルド）
4. システムの再起動

黒い画面に進行状況が表示され、「更新が完了しました」と出れば成功です。
エラーが出た場合は画面を閉じずに、表示された内容を確認してください。

**手動で行う場合**

```powershell
# 1. 実行中のシステムを停止
Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*person-management*.jar*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

# 2. 最新版を取得してビルドし直す
cd C:\workOfficeTani
git pull
.\mvnw clean package -DskipTests

# 3. 再起動（ダブルクリックでも可）
.\scripts\start-system.vbs
```

※ jar 使用中に `mvnw clean` を実行すると
「別のプロセスが使用中です」というエラーになります。
必ず手順1でシステムを停止してから行ってください。

ログイン後、「その他（1-7）」から以下も参照できます。

- **1-7-1 システム説明書**：各画面の使い方
- **1-7-2 Q&A表**：よくある質問
- **1-7-3 システム診断**：データの不整合チェック
- **1-7-4 データフロー・計算式ガイド**：金額の計算式の詳細
- **1-7-5 バックアップ手順**：自動バックアップ・復元方法
