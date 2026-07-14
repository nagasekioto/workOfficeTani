# 自己完結型パッケージの作り方(exe化 + PostgreSQL同梱)

このフォルダの仕組みを使うと、「PostgreSQLを別途インストールしなくても、
フォルダをコピーしてexeをダブルクリックするだけで動く」配布物を作れます。

**注意**: 既存の `scripts/start-system.vbs`(java -jarで動く従来方式)は
変更していません。今動いている本番環境に影響はありません。
この新方式は `dist/` フォルダに独立して出力されるので、
動作確認ができてから、スタートアップフォルダのショートカットを
新しい方(`dist/WorkOfficeTani/start-app.vbs`)に差し替えてください。

## 事前準備(最初の1回だけ)

1. JDK17がインストールされていること(`jpackage -version` で確認)
2. `packaging/pgsql-portable/README.md` の手順に従って、
   PostgreSQLポータブル版を `packaging/pgsql-portable/` に配置する

## ビルド方法

```powershell
cd C:\workOfficeTani
.\packaging\build-package.ps1
```

成功すると `dist\WorkOfficeTani\` フォルダが作られます。

```
dist/WorkOfficeTani/
  WorkOfficeTani.exe   ← アプリ本体(JRE同梱、Java不要)
  app/                 ← jpackageが生成するアプリ資材
  runtime/             ← 同梱されたJRE
  pgsql/               ← PostgreSQL本体(ポータブル版)
  pgdata/              ← DBのデータ(初回起動時に自動生成)
  scripts/             ← 初回起動時に流し込むスキーマファイル
  start-app.vbs        ← 起動用スクリプト
  stop-app.bat         ← 停止用スクリプト
```

## 配布・利用方法

1. `dist\WorkOfficeTani` フォルダ**ごと**、配布したいPCにコピーする
   (USBメモリ、共有フォルダ、zip圧縮して送るなど、何でもOK)
2. コピー先で `start-app.vbs` をダブルクリックすると起動します
   - 初回のみ、内蔵PostgreSQLの初期化(データベース作成・
     テーブル作成)が自動的に行われるため、少し時間がかかります
   - 2回目以降は起動が速くなります
3. しばらく待つとブラウザが自動で開き、ログイン画面が表示されます
4. 自動起動させたい場合は、`start-app.vbs` のショートカットを
   Windowsの「スタートアップ」フォルダ(`shell:startup`)に入れてください
5. 停止するときは `stop-app.bat` をダブルクリックしてください

## 動作の仕組み(簡単に)

- 内蔵PostgreSQLはポート `5433` で動作します
  (通常インストール版のPostgreSQLがポート`5432`で別に動いていても
  衝突しません)
- アプリは環境変数 `DB_PORT=5433` で起動されるため、
  `application.yml` の設定を書き換えなくても内蔵DBに接続します
- 初回起動時だけ `initdb` → `pg_ctl start` → `createdb` →
  スキーマ投入、という流れが自動実行され、完了すると
  `.db-initialized` という目印ファイルが作られます
  (このファイルがあれば2回目以降は初期化をスキップします)

## プログラムを修正した場合の更新方法

修正後、もう一度 `.\packaging\build-package.ps1` を実行すれば
`dist\WorkOfficeTani\app` の中身だけが新しくなり、`pgdata`(データ)は
そのまま引き継がれます(`dist`フォルダ自体は毎回作り直されるため、
既存の配布先フォルダで直接ビルドしている場合は `pgdata` フォルダを
退避してから上書きするなど運用に注意してください)。
