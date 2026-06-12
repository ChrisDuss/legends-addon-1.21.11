@echo off
powershell -ExecutionPolicy Bypass -File "%~dp0verify-dev-server.ps1" %*
