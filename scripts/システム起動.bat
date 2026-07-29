@echo off
rem ============================================================
rem  Person Management System - Launcher
rem
rem  Double-click this file to start the system.
rem  All on-screen messages are produced by launch.ps1.
rem
rem  This file is kept ASCII-only on purpose: a .bat file is read
rem  using the console code page, so non-ASCII text here can turn
rem  into garbage on a machine with a different locale.
rem ============================================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch.ps1"
