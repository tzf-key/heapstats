@echo off
set DIR=%~dp0
"%DIR%\java" --module-path "%DIR%..\mods" -m heapstats.cli/jp.co.ntt.oss.heapstats.cli.CliMain %*
