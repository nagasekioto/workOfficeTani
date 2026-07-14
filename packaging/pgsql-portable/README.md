# PostgreSQL ポータブル版をここに配置してください

このフォルダは空です(バイナリはサイズが大きいためGit管理していません)。
`build-package.ps1` を実行する前に、以下の手順でPostgreSQLの
「インストーラー不要のzip版(ポータブル版)」をここに展開してください。

## 入手方法

1. https://www.enterprisedb.com/download-postgresql-binaries を開く
2. Windows x86-64 の該当バージョン(例: 17.x)の **zip archive** をダウンロード
   (インストーラー版 `.exe` ではなく、"Binaries" と書かれている zip の方)
3. ダウンロードしたzipを展開し、中身をこのフォルダに直接配置する

## 配置後のフォルダ構成(このようになっていればOK)

```
packaging/pgsql-portable/
  bin/
    initdb.exe
    pg_ctl.exe
    postgres.exe
    psql.exe
    createdb.exe
    ...
  lib/
  share/
  ...
```

`packaging/pgsql-portable/bin/initdb.exe` が存在していれば、
`build-package.ps1` が正しく認識してパッケージに同梱します。

## 補足

- ここに置いたファイルは `.gitignore` で除外されているため、
  GitHubにはアップロードされません(バイナリが大きく、GitHub上で
  管理する必要もないためです)。パッケージを作る担当者が、都度
  このフォルダに配置してからビルドしてください。
- ライセンス上、PostgreSQL本体の再配布は問題ありません(PostgreSQL
  Licenseは緩やかなBSD系ライセンスです)が、社内での利用に留める
  想定であれば特に気にする必要はありません。
