<#
.SYNOPSIS
  Uploads the Markdown files in docs/ (and the promo videos in docs/promo/tiktok/) to the Google Drive folder PocketGarden_Docs.

  The TikTok files go to two folders under PocketGarden_Docs/promo/tiktok/:
    完成/    the mp4s to post (docs/promo/tiktok/*.mp4, the latest of each)
    確認用/  the pictures to look over (docs/promo/tiktok/*_sheet.jpg and docs/promo/tiktok/frames/*.jpg), all in one folder
  tools/promo_render.py copies each new video the same way.

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
  Also deletes .md files on Drive that are no longer in docs/, and files in 完成/ and 確認用/ that
  are no longer in docs/promo/tiktok/ (old versions: Drive keeps them in its bin).

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

# Folder names in Japanese, spelt by code point (Windows PowerShell 5.1 reads a script without a BOM as ANSI).
$finished = -join [char[]](0x5B8C, 0x6210)          # 完成
$review = -join [char[]](0x78BA, 0x8A8D, 0x7528)    # 確認用
$tiktok = Join-Path $docs "promo/tiktok"

$verb = if ($Mirror) { "sync" } else { "copy" }
$common = @("--checksum", "--stats-one-line", "-v")
if ($DryRun) { $common += "--dry-run" }

function Invoke-Rclone([string[]]$Arguments) {
    & $rclone @Arguments
    if ($LASTEXITCODE -ne 0) { throw "rclone $($Arguments[0]) failed with exit code $LASTEXITCODE" }
}

Write-Host "Uploading $docs\*.md to ${Remote}:$Folder ($verb)"
Invoke-Rclone (@($verb, "$docs", "${Remote}:$Folder", "--include", "*.md") + $common)

Write-Host "`nUploading the TikTok mp4s to ${Remote}:$Folder/promo/tiktok/$finished ($verb)"
Invoke-Rclone (@($verb, "$tiktok", "${Remote}:$Folder/promo/tiktok/$finished", "--include", "/*.mp4") + $common)

# The sheets and the still frames side by side in one folder: copied one by one (a sync of two
# local folders into one would delete each other's files).
Write-Host "`nUploading the sheets and frames to ${Remote}:$Folder/promo/tiktok/$review"
Invoke-Rclone (@("copy", "$tiktok", "${Remote}:$Folder/promo/tiktok/$review", "--include", "/*_sheet.jpg") + $common)
Invoke-Rclone (@("copy", (Join-Path $tiktok "frames"), "${Remote}:$Folder/promo/tiktok/$review", "--include", "/*.jpg") + $common)
if ($Mirror) {
    $wanted = @(Get-ChildItem $tiktok -Filter *_sheet.jpg) + @(Get-ChildItem (Join-Path $tiktok "frames") -Filter *.jpg) | ForEach-Object Name
    $there = & $rclone lsf "${Remote}:$Folder/promo/tiktok/$review" --files-only
    foreach ($name in $there) {
        if ($wanted -notcontains $name) {
            Write-Host "  to the bin: $review/$name"
            if (-not $DryRun) { Invoke-Rclone @("deletefile", "${Remote}:$Folder/promo/tiktok/$review/$name") }
        }
    }
}

Write-Host "`nNow on Drive:"
& $rclone lsl "${Remote}:$Folder" --include "*.md" --include "/promo/tiktok/**"
