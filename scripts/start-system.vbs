' ============================================================
' start-system.vbs
' 家政婦紹介事務所 人物管理システム 自動起動スクリプト
'
' 【使い方】
' このファイルを Windows の「スタートアップ」フォルダに置くと、
' パソコンにログインするたびに、黒い画面（コマンドプロンプト）を
' 一切表示せずにシステムが裏側で起動し、しばらくしてから自動で
' ブラウザにログイン画面が開きます。
'
' スタートアップフォルダの開き方：
'   キーボードで「Windowsキー + R」を押し、
'   「shell:startup」と入力してEnter。
'   開いたフォルダに、このファイル（start-system.vbs）をコピーする。
'
' 【前提】
' 事前に一度だけ、実行可能な jar ファイルを作っておく必要があります
' （SETUP_NEW_PC.md の「9. 自動起動の設定」を参照）。
'   cd C:\workOfficeTani
'   .\mvnw clean package -DskipTests
'
' 【うまく起動しない場合】
' C:\workOfficeTani\system-log.txt に起動時のログが記録されるので、
' エラーが出ていないか確認してください。
' ============================================================

Dim objShell, appDir, jarName, logFile

appDir  = "C:\workOfficeTani"
jarName = "target\person-management-0.0.1-SNAPSHOT.jar"
logFile = appDir & "\system-log.txt"

Set objShell = CreateObject("WScript.Shell")
objShell.CurrentDirectory = appDir

' PostgreSQL（Windowsサービスとして自動起動）が準備できるまで少し待つ
WScript.Sleep 5000

' ウィンドウを一切表示せず（第2引数=0）バックグラウンドでjavaを起動。
' 起動ログは system-log.txt に記録する（トラブル時の確認用）。
objShell.Run "cmd /c java -jar """ & jarName & """ > """ & logFile & """ 2>&1", 0, False

' アプリの起動が完了するまで待ってから、ログイン画面をブラウザで自動的に開く
WScript.Sleep 15000
objShell.Run "http://localhost:8080/login", 1, False
