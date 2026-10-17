<#
.SYNOPSIS
  Uploads the Markdown files in docs/ to the Google Drive folder PocketGarden_Docs.

.DESCRIPTION
  Uses rclone and its "gdrive" remote. A file that changed locally replaces the one on Drive
  (compared by checksum); unchanged files are skipped. Files on Drive that are no longer in
  docs/ are left alone unless -Mirror is given.

  One-time setup (already done on this PC):
    winget install --id Rclone.Rclone -e
    rclone config create gdrive drive scope=drive.file   # opens the browser to sign in

  The drive.file scope only lets rclone see what it has created itself, so the folder is
  made by the first run and is not shared with any folder of the same name made by hand.

.PARAMETER Mirror
  Also deletes .md files on Drive that are no longer in docs/.

.PARAMETER DryRun
  Shows what would be uploaded or deleted without changing anything.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools/sync_docs_to_drive.ps1
#>
param(
    [switch]$Mirror,
    [switch]$DryRun,
    [string]$Remote = "gdrive",
    [string]$Folder = "PocketGarden_Docs"
)

$ErrorActionPreference = "Stop"
$docs = Join-Path $PSScriptRoot "..\docs" | Resolve-Path

# rclone from PATH, or where winget unpacked it (PATH only changes for new shells).
$rclone = (Get-Command rclone -ErrorAction SilentlyContinue).Source
if (-not $rclone) {
    $rclone = Get-ChildItem "$env:LOCALAPPDATA\Microsoft\WinGet\Packages" -Recurse -Filter rclone.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $rclone) { throw "rclone not found. Install it with: winget install --id Rclone.Rclone -e" }

$remotes = & $rclone listremotes
if ($remotes -notcontains "${Remote}:") {
    throw "rclone has no '$Remote' remote. Set it up with: rclone config create $Remote drive scope=drive.file"
}

$verb = if ($Mirror) { "sync" } else { "copy" }
$arguments = @($verb, "$docs", "${Remote}:$Folder", "--include", "*.md", "--checksum", "--stats-one-line", "-v")
if ($DryRun) { $arguments += "--dry-run" }

Write-Host "Uploading $docs\*.md to ${Remote}:$Folder ($verb)"
& $rclone @arguments
if ($LASTEXITCODE -ne 0) { throw "rclone $verb failed with exit code $LASTEXITCODE" }

Write-Host "`nNow on Drive:"
& $rclone lsl "${Remote}:$Folder" --include "*.md"
