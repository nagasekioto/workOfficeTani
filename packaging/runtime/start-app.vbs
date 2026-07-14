' ============================================================
' start-app.vbs
' 家政婦紹介事務所 人物管理システム - 自己完結型 起動スクリプト
'
' このフォルダの中に PostgreSQL(pgsql) と アプリ本体(exe) が
' 両方とも同梱されているため、別途 PostgreSQL をインストール
' しなくてもこのフォルダをコピーするだけで動作します。
'
' 【使い方】
'   このファイルをダブルクリックすると起動します。
'   Windows の「スタートアップ」フォルダにこのファイルの
'   ショートカットを置けば、パソコン起動時に自動で立ち上がります。
'
' 【うまく起動しない場合】
'   同じフォルダ内の system-log.txt / pg-log.txt にログが
'   記録されるので、エラーが出ていないか確認してください。
' ============================================================

Dim fso, shell
Set fso = CreateObject("Scripting.FileSystemObject")
Set shell = CreateObject("WScript.Shell")

Dim appDir
appDir = fso.GetParentFolderName(WScript.ScriptFullName)

Dim pgBin, pgData, pgPort, appExe, logFile, pgLogFile, markerFile
pgBin      = appDir & "\pgsql\bin"
pgData     = appDir & "\pgdata"
pgPort     = "5433"
appExe     = appDir & "\WorkOfficeTani.exe"
logFile    = appDir & "\system-log.txt"
pgLogFile  = appDir & "\pg-log.txt"
markerFile = appDir & "\.db-initialized"

shell.CurrentDirectory = appDir

Sub WriteLog(msg)
    Dim f
    Set f = fso.OpenTextFile(logFile, 8, True)
    f.WriteLine Now & " - " & msg
    f.Close
End Sub

' --- 1. 初回だけ: データベースの初期化・スキーマ投入 ---
If Not fso.FileExists(markerFile) Then
    WriteLog "初回起動を検出しました。データベースを初期化します。"

    Dim rc
    rc = shell.Run("""" & pgBin & "\initdb.exe"" -D """ & pgData & """ -U postgres -E UTF8 --locale=C -A trust", 0, True)
    If rc <> 0 Then
        WriteLog "initdb に失敗しました(終了コード: " & rc & ")"
        MsgBox "データベースの初期化に失敗しました。system-log.txt を確認してください。", vbCritical, "起動エラー"
        WScript.Quit 1
    End If

    rc = shell.Run("""" & pgBin & "\pg_ctl.exe"" start -D """ & pgData & """ -o ""-p " & pgPort & """ -l """ & pgLogFile & """ -w", 0, True)
    If rc <> 0 Then
        WriteLog "PostgreSQL の初回起動に失敗しました(終了コード: " & rc & ")"
        MsgBox "データベースの起動に失敗しました。pg-log.txt を確認してください。", vbCritical, "起動エラー"
        WScript.Quit 1
    End If

    rc = shell.Run("""" & pgBin & "\createdb.exe"" -U postgres -p " & pgPort & " kaseihu", 0, True)
    If rc <> 0 Then
        WriteLog "createdb に失敗しました(終了コード: " & rc & ")。既にデータベースが存在する可能性があります。"
    End If

    rc = shell.Run("""" & pgBin & "\psql.exe"" -U postgres -p " & pgPort & " -d kaseihu -f """ & appDir & "\scripts\schema-bootstrap.sql""", 0, True)
    If rc <> 0 Then
        WriteLog "schema-bootstrap.sql の実行でエラーが発生しました(終了コード: " & rc & ")"
    End If

    rc = shell.Run("""" & pgBin & "\psql.exe"" -U postgres -p " & pgPort & " -d kaseihu -f """ & appDir & "\scripts\schema-all.sql""", 0, True)
    If rc <> 0 Then
        WriteLog "schema-all.sql の実行でエラーが発生しました(終了コード: " & rc & ")"
    End If

    Dim markerF
    Set markerF = fso.CreateTextFile(markerFile, True)
    markerF.WriteLine Now & " - initialized"
    markerF.Close

    WriteLog "データベースの初期化が完了しました。"
Else
    ' --- 2. 2回目以降: PostgreSQL を起動するだけ ---
    WriteLog "PostgreSQL を起動しています..."
    Dim rc2
    rc2 = shell.Run("""" & pgBin & "\pg_ctl.exe"" start -D """ & pgData & """ -o ""-p " & pgPort & """ -l """ & pgLogFile & """ -w", 0, True)
    If rc2 <> 0 Then
        WriteLog "PostgreSQL の起動でエラー(終了コード: " & rc2 & ")。既に起動中の場合は無視して問題ありません。"
    End If
End If

' --- 3. アプリ本体(exe)を起動(DB_PORTを環境変数で渡す) ---
WriteLog "アプリケーションを起動しています..."
shell.Environment("PROCESS")("DB_PORT") = pgPort
shell.Run "cmd /c """ & appExe & """ > """ & logFile & """ 2>&1", 0, False

' --- 4. アプリの起動を待ってからブラウザを開く ---
WScript.Sleep 15000

Dim chromePaths, chromePath, i
chromePaths = Array( _
    shell.ExpandEnvironmentStrings("%ProgramFiles%\Google\Chrome\Application\chrome.exe"), _
    shell.ExpandEnvironmentStrings("%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"), _
    shell.ExpandEnvironmentStrings("%LocalAppData%\Google\Chrome\Application\chrome.exe") _
)
chromePath = ""
For i = 0 To UBound(chromePaths)
    If fso.FileExists(chromePaths(i)) Then
        chromePath = chromePaths(i)
        Exit For
    End If
Next

If chromePath <> "" Then
    shell.Run """" & chromePath & """ --new-window ""http://localhost:8080/login""", 1, False
Else
    shell.Run "http://localhost:8080/login", 1, False
End If
