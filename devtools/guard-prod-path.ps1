<#
.SYNOPSIS
    Decide whether a path is a legitimate production Minecraft drop point.

.DESCRIPTION
    Read-only path check. Writes no files. Intended as a gate before any
    workflow that copies a jar into the production Minecraft installation.

    Rules:
      1. Only D:\.minecraft\versions\1.21\mods is accepted as the production
         drop point (exact match required).
      2. D:\.minecraft\mods is explicitly rejected: it is an empty directory,
         placing a jar there silently does nothing. This is the most common
         mistake.
      3. The .minecraft root and every other path are rejected.
      4. If the accepted directory already holds development-only artifacts
         (-dev.jar / -sources.jar), that is reported as a warning.

    NOTE: this file is deliberately ASCII-only. Windows PowerShell 5.1 reads
    a BOM-less .ps1 as ANSI, which corrupts non-ASCII comments and can swallow
    line breaks, breaking the parser. Keep this file ASCII to stay portable.
    Chinese documentation lives in devtools\README.md.

.OUTPUTS
    Exit code 0 = allowed, 1 = blocked, 2 = bad arguments.
    The final stdout line is always "VERDICT=ALLOW" or "VERDICT=BLOCK".

.PARAMETER Path
    Directory path to judge.

.PARAMETER Quiet
    Suppress the human-readable output; only the verdict line and exit code.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Path,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Constants must exist before use: StrictMode errors on undefined variables.
$ALLOWED_PROD_MODS = 'D:\.minecraft\versions\1.21\mods'
$TRAP_PATHS = @('D:\.minecraft\mods', 'D:\.minecraft')

function Say { param([string]$T) if (-not $Quiet) { Write-Output $T } }

function Normalize-Path {
    param([string]$P)
    $full = [System.IO.Path]::GetFullPath($P)
    return $full.TrimEnd('\', '/')
}

function Verdict {
    param([string]$V)
    Write-Output ("VERDICT=" + $V)
}

$target = Normalize-Path $Path
$allowed = Normalize-Path $ALLOWED_PROD_MODS

Say ("Target  : " + $target)
Say ("Allowed : " + $allowed)

# Rule 2/3: trap paths first, so the reason is precise.
foreach ($trap in $TRAP_PATHS) {
    if ($target -eq (Normalize-Path $trap)) {
        if ($trap -eq 'D:\.minecraft\mods') {
            Say ""
            Say "BLOCKED: this is D:\.minecraft\mods, an empty directory."
            Say "         Production mods live in D:\.minecraft\versions\1.21\mods\"
        } else {
            Say ""
            Say "BLOCKED: this is the .minecraft root, not a mods directory."
        }
        Verdict 'BLOCK'
        exit 1
    }
}

# Rule 1: whitelist.
if ($target -eq $allowed) {
    $suspects = @()
    if (Test-Path -LiteralPath $target) {
        $suspects = @(Get-ChildItem -LiteralPath $target -Force -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like '*-dev.jar' -or $_.Name -like '*-sources.jar' })
    }
    if ($suspects.Count -gt 0) {
        Say ""
        Say "ALLOWED, but development artifacts are present (they should not be):"
        $suspects | ForEach-Object { Say ("  ! " + $_.Name) }
        Say "  Use build\libs output, not build\devlibs\-dev.jar, for production."
    } else {
        Say ""
        Say "ALLOWED: legitimate production drop point."
    }
    Verdict 'ALLOW'
    exit 0
}

# Rule 3: everything else.
Say ""
if ($target -like 'D:\.minecraft*') {
    Say "BLOCKED: under .minecraft, but not the valid production mods directory."
} else {
    Say "BLOCKED: not on the production whitelist (this gate only judges prod drop points)."
}
Verdict 'BLOCK'
exit 1
