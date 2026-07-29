' ============================================================
'  Person Management System - silent auto-start
'
'  Purpose:
'    Start the system in the background when Windows logs in,
'    without showing a console window.
'    To start it by hand, double-click the launcher .bat instead
'    (the one whose name means "start the system").
'
'  How to register for auto-start:
'    1. Right-click this file -> "Create shortcut"
'    2. Press Windows + R, type "shell:startup", press Enter
'    3. Move the shortcut into the folder that opens
'
'    Do NOT copy this file itself into the startup folder.
'    It locates the system from its own location, so it stops
'    working once moved out of the "scripts" folder.
'    A shortcut keeps the real file in place, so it is fine.
'
'  IMPORTANT - this file must stay ASCII-only.
'    Windows Script Host reads .vbs using the ANSI code page and
'    rejects a UTF-8 BOM outright. Japanese text saved as UTF-8
'    here turns into mojibake and breaks parsing with an
'    "unterminated string constant" error. All Japanese messages
'    live in launch.ps1, which is UTF-8 with BOM and read by
'    PowerShell (which handles that correctly).
'
'  All start-up logic (settings check, rebuilding the jar,
'  waiting for start-up to finish) lives in launch.ps1 so that
'  the same behaviour is shared with the .bat launcher.
'
'  If it does not start:
'    This script shows no console, so failures are invisible.
'    Double-click the launcher .bat to run the same logic with a
'    window, and the reason is shown in Japanese.
'    A log is also written to launcher-log.txt.
' ============================================================

Dim objShell, fso, scriptDir, ps1Path

Set objShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

' Work out where this script lives (the "scripts" folder).
' Hard-coding a path would break as soon as the folder is moved.
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
ps1Path = fso.BuildPath(scriptDir, "launch.ps1")

If Not fso.FileExists(ps1Path) Then
    MsgBox "launch.ps1 not found." & vbCrLf & vbCrLf & _
           "Looked in: " & ps1Path & vbCrLf & vbCrLf & _
           "Keep this file inside the 'scripts' folder and put a " & _
           "shortcut in the startup folder instead.", _
           vbCritical, "Person Management System"
    WScript.Quit 1
End If

' Give PostgreSQL (a Windows service) a moment to come up first.
WScript.Sleep 5000

' Second argument 0 = hidden window. -Hidden tells launch.ps1 not to
' wait for a key press: with no visible console that would hang
' forever where nobody can see it. It reports failures in a dialog.
objShell.Run "powershell -NoProfile -ExecutionPolicy Bypass -File """ & ps1Path & """ -Hidden", 0, False
