<#
.SYNOPSIS
    The only sanctioned channel for copying a build artifact into production.

.DESCRIPTION
    Use this when you intentionally want a build to reach the production
    Minecraft installation. Dry-run by default: nothing is written unless
    -Apply is given.

    Steps:
      1. Pass the guard-prod-path.ps1 whitelist gate.
      2. Verify the source exists and is not a devlibs / -dev.jar artifact.
      3. Warn if the production client is currently running (a locked jar
         produces a failed or truncated copy).
      4. Back up the existing jar under devtools\backup\<timestamp>\.
      5. Copy and verify by SHA256.
      6. Print the rollback command.

    NOTE: this file is deliberately ASCII-only. Windows PowerShell 5.1 reads a
    BOM-less .ps1 as ANSI, which corrupts non-ASCII comments and can swallow
    line breaks, breaking the parser. Chinese documentation lives in
    devtools\README.md.

.OUTPUTS
    Exit code 0 = success or clean no-op, 1 = blocked, 2 = bad input.

.PARAMETER Source
    Source jar. Defaults to build\libs\materialstock-1.0.0.jar relative to the
    project root (the parent of this script's directory).

.PARAMETER Target
    Destination mods directory. Defaults to D:\.minecraft\versions\1.21\mods.

.PARAMETER Apply
    Actually write. Without it the script only reports what it would do.

.PARAMETER Force
    Continue even if the production client appears to be running.
#>
[CmdletBinding()]
param(
    [string]$Source = 'build\libs\materialstock-1.0.0.jar',
    [string]$Target = 'D:\.minecraft\versions\1.21\mods',
    [switch]$Apply,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$guard = Join-Path $scriptDir 'guard-prod-path.ps1'

$mode = if ($Apply) { 'APPLY (real write)' } else { 'DRY-RUN' }
Write-Output ("=== Pre-flight [" + $mode + "] ===")

# --- 1. Path gate -----------------------------------------------------------
if (-not (Test-Path -LiteralPath $guard)) {
    Write-Output ("ERROR: gate script not found: " + $guard)
    exit 2
}
& $guard -Path $Target
$guardCode = $LASTEXITCODE
if ($guardCode -ne 0) {
    Write-Output ""
    Write-Output "Blocked by path gate. Nothing was written."
    exit 1
}

# --- 2. Source --------------------------------------------------------------
if (-not [System.IO.Path]::IsPathRooted($Source)) {
    $projectRoot = Split-Path -Parent $scriptDir
    $Source = Join-Path $projectRoot $Source
}
$Source = [System.IO.Path]::GetFullPath($Source)

if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) {
    Write-Output ("ERROR: source not found: " + $Source)
    Write-Output "Run 'gradle build' first."
    exit 2
}

$srcName = Split-Path -Leaf $Source
if ($srcName -like '*-dev.jar' -or $Source -like '*\devlibs\*') {
    Write-Output ""
    Write-Output "BLOCKED: source is a development artifact (devlibs / -dev.jar)."
    Write-Output "         Production takes the build\libs output only."
    exit 1
}
if ($srcName -like '*-sources.jar') {
    Write-Output ""
    Write-Output "BLOCKED: source is a sources jar, not a loadable mod."
    exit 1
}

$srcHash = (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash
$srcSize = (Get-Item -LiteralPath $Source).Length
Write-Output ("Source  : " + $Source)
Write-Output ("          " + $srcSize + " bytes  SHA256 " + $srcHash.Substring(0, 16) + "...")

# --- 3. Is the production client running? -----------------------------------
$running = @()
try {
    $running = @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction Stop |
        Where-Object { $_.CommandLine -and $_.CommandLine -like '*versions\1.21*' })
} catch {
    $running = @()
}
if ($running.Count -gt 0) {
    Write-Output ""
    Write-Output ("WARNING: production Minecraft appears to be running (" + $running.Count + " process(es)).")
    if ($Apply -and -not $Force) {
        Write-Output "         Fully exit the game first, or pass -Force to override."
        exit 1
    }
    Write-Output "         Continuing (dry-run, or -Force given)."
} else {
    Write-Output "Client  : no running production client detected"
}

$dest = Join-Path $Target $srcName
Write-Output ("Dest    : " + $dest)

# --- 4. Backup --------------------------------------------------------------
$rollback = ''
if (Test-Path -LiteralPath $dest -PathType Leaf) {
    $oldHash = (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash
    $oldSize = (Get-Item -LiteralPath $dest).Length
    Write-Output ("Existing: " + $oldSize + " bytes  SHA256 " + $oldHash.Substring(0, 16) + "...")
    if ($oldHash -eq $srcHash) {
        Write-Output ""
        Write-Output "Identical content - nothing to deploy."
        exit 0
    }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $backupDir = Join-Path $scriptDir ("backup\" + $stamp)
    $backupFile = Join-Path $backupDir $srcName
    Write-Output ("Backup  : " + $backupDir)
    $rollback = "Copy-Item -LiteralPath '" + $backupFile + "' -Destination '" + $dest + "' -Force"
    if ($Apply) {
        New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
        Copy-Item -LiteralPath $dest -Destination $backupFile -Force
        Write-Output ("Rollback: " + $rollback)
    }
} else {
    Write-Output "Existing: none (first deployment)"
}

# --- 5. Copy and verify -----------------------------------------------------
if (-not $Apply) {
    Write-Output ""
    Write-Output "Dry run complete. Nothing written. Re-run with -Apply to execute."
    exit 0
}

Copy-Item -LiteralPath $Source -Destination $dest -Force
$newHash = (Get-FileHash -LiteralPath $dest -Algorithm SHA256).Hash
if ($newHash -ne $srcHash) {
    Write-Output ""
    Write-Output "VERIFY FAILED: hash mismatch after copy."
    Write-Output ("  expected " + $srcHash)
    Write-Output ("  actual   " + $newHash)
    if ($rollback -ne '') { Write-Output ("  rollback: " + $rollback) }
    exit 1
}

Write-Output ""
Write-Output ("Deployed and verified: " + $dest)
if ($rollback -ne '') { Write-Output ("Rollback: " + $rollback) }
Write-Output "Fully restart the game to load the new jar (Fabric does not hot-reload mods)."
exit 0
