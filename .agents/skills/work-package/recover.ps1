param(
    [string]$Id = "",
    [string]$Status = "error",
    [switch]$Json,
    [switch]$DryRun
)

[Console]::OutputEncoding = [Text.Encoding]::UTF8
$PSDefaultParameterValues['*:Encoding'] = 'utf8'

$checkpointsDir = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))) "checkpoints"

if (-not (Test-Path $checkpointsDir)) {
    Write-Error "No checkpoints directory found."
    exit 1
}

$files = Get-ChildItem -Path $checkpointsDir -Filter "wp-*.json" | Sort-Object Name

if ($files.Count -eq 0) {
    Write-Output "No checkpoints found."
    exit 0
}

# Load all checkpoints
$allCheckpoints = @()
foreach ($file in $files) {
    try {
        $wp = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        $allCheckpoints += $wp
    } catch {
        Write-Warning "Skipped invalid checkpoint: $($file.Name) - $_"
    }
}

$allCheckpoints = $allCheckpoints | Sort-Object sequence

# Find the error checkpoint
$errorCp = $null
if ($Id) {
    $errorCp = $allCheckpoints | Where-Object { $_.id -eq $Id -and ($_.status -eq "error" -or $_.status -eq "failed" -or $_.status -eq "cancelled") }
    if (-not $errorCp) {
        $errorCp = $allCheckpoints | Where-Object { $_.id -eq $Id }
        if (-not $errorCp) { Write-Error "Checkpoint not found: $Id"; exit 1 }
    }
} else {
    $errorCp = $allCheckpoints | Where-Object { $_.status -eq "error" -or $_.status -eq "failed" -or $_.status -eq "cancelled" } | Select-Object -Last 1
}

if (-not $errorCp) {
    Write-Output "No error/failed checkpoints found. Everything looks clean."
    exit 0
}

# Find the last good checkpoint (before the error)
$goodCp = $null
if ($errorCp.sequence -gt 1) {
    $goodSeq = $errorCp.sequence - 1
    $goodCp = $allCheckpoints | Where-Object { $_.sequence -eq $goodSeq -and $_.status -eq "completed" } | Select-Object -Last 1
}
if (-not $goodCp) {
    $goodCp = $allCheckpoints | Where-Object { $_.sequence -lt $errorCp.sequence -and $_.status -eq "completed" } | Select-Object -Last 1
}
if (-not $goodCp -and $allCheckpoints.Count -gt 0) {
    $goodCp = $allCheckpoints | Where-Object { $_.id -ne $errorCp.id -and $_.status -eq "completed" } | Select-Object -Last 1
}

# Build recovery data
$recovery = @{
    errorCheckpoint = @{
        id = $errorCp.id
        sequence = $errorCp.sequence
        title = $errorCp.title
        status = $errorCp.status
        createdAt = $errorCp.createdAt
        objective = $errorCp.objective
        context = $errorCp.context_summary
        errorInfo = if ($errorCp.error_info) { $errorCp.error_info } else { $null }
        pendingItems = if ($errorCp.pending) { @($errorCp.pending) } else { @() }
        lastMemory = if ($errorCp.memory_snapshot) { @($errorCp.memory_snapshot) } else { @() }
    }
    goodCheckpoint = if ($goodCp) {
        @{
            id = $goodCp.id
            sequence = $goodCp.sequence
            title = $goodCp.title
            status = $goodCp.status
            progress = $goodCp.progress
            createdAt = $goodCp.createdAt
            context = $goodCp.context_summary
            completedItems = if ($goodCp.completed) { @($goodCp.completed) } else { @() }
            pendingItems = if ($goodCp.pending) { @($goodCp.pending) } else { @() }
            decisions = if ($goodCp.decisions) { @($goodCp.decisions) } else { @() }
            memorySnapshot = if ($goodCp.memory_snapshot) { @($goodCp.memory_snapshot) } else { @() }
            knowledge = if ($goodCp.knowledge) { @($goodCp.knowledge) } else { @() }
        }
    } else { $null }
    recoveryPlan = @(
        "Rollback to checkpoint: $($goodCp.id)",
        "Restore context: '$($goodCp.context_summary)'",
        "Redo pending items from good checkpoint + any new work"
    )
}

if ($DryRun) {
    $recovery.dryRun = $true
    $recovery.recoveryPlan += "[DRY RUN] No actual recovery performed"
}

if ($Json) {
    Write-Output ($recovery | ConvertTo-Json -Depth 10)
    exit 0
}

# Display error info
Write-Output "=========================================="
Write-Output "RECOVERY PLAN"
Write-Output "=========================================="
Write-Output ""
Write-Output "[ERROR CHECKPOINT]"
Write-Output "  ID:       $($errorCp.id)"
Write-Output "  Title:    $($errorCp.title)"
Write-Output "  Status:   $($errorCp.status)"
Write-Output "  Time:     $($errorCp.createdAt)"
if ($errorCp.objective) { Write-Output "  Objective: $($errorCp.objective)" }

if ($errorCp.error_info) {
    $ei = $errorCp.error_info
    Write-Output ""
    Write-Output "  Error Details:"
    if ($ei.error_type) { Write-Output "    Type:    $($ei.error_type)" }
    if ($ei.error_message) { Write-Output "    Message: $($ei.error_message)" }
    if ($ei.error_timestamp) { Write-Output "    Time:    $($ei.error_timestamp)" }
    if ($ei.tool_used) { Write-Output "    Tool:    $($ei.tool_used)" }
    if ($ei.actions_before_error) {
        Write-Output "    Actions before error:"
        foreach ($a in $ei.actions_before_error) { Write-Output "      - $a" }
    }
    if ($ei.recovery_instruction) { Write-Output "    Recovery hint: $($ei.recovery_instruction)" }
} else {
    Write-Output ""
    Write-Output "  (No structured error info. Status: $($errorCp.status))"
}

if ($errorCp.pending.Count -gt 0) {
    Write-Output ""
    Write-Output "  Pending items lost:"
    foreach ($p in $errorCp.pending) { Write-Output "    - $p" }
}

Write-Output ""
Write-Output "------------------------------------------"

if ($goodCp) {
    Write-Output ""
    Write-Output "[RESTORE POINT]"
    Write-Output "  ID:       $($goodCp.id)"
    Write-Output "  Title:    $($goodCp.title)"
    Write-Output "  Status:   $($goodCp.status) ($($goodCp.progress)%)"
    Write-Output "  Time:     $($goodCp.createdAt)"
    if ($goodCp.context) { Write-Output "  Context:  $($goodCp.context)" }
    Write-Output ""
    Write-Output "  Completed so far ($($goodCp.completed.Count)):"
    foreach ($c in $goodCp.completed) { Write-Output "    + $c" }
    Write-Output ""
    Write-Output "  Pending to redo ($($goodCp.pending.Count)):"
    foreach ($p in $goodCp.pending) { Write-Output "    - $p" }
    Write-Output ""
    Write-Output "  Decisions to preserve:"
    foreach ($d in $goodCp.decisions) {
        Write-Output "    [$($d.id)] $($d.decision) ($($d.decidedBy))"
    }
    Write-Output ""
    Write-Output "  Knowledge to reload:"
    foreach ($k in $goodCp.knowledge) { Write-Output "    * $k" }
    Write-Output ""
    Write-Output "  Memory Snapshot to restore:"
    foreach ($m in $goodCp.memory_snapshot) {
        Write-Output "    $($m.key) = $($m.value)"
    }
} else {
    Write-Output ""
    Write-Output "[RESTORE POINT] No previous good checkpoint found."
    Write-Output "  Recovering from scratch may be needed."
}

Write-Output ""
Write-Output "=========================================="
Write-Output "RECOVERY INSTRUCTIONS"
Write-Output "------------------------------------------"
if ($goodCp) {
    Write-Output "1. Load context from checkpoint: $($goodCp.id)"
    Write-Output "2. Restore memory_snapshot values"
    Write-Output "3. Continue with pending items"
    Write-Output "4. Avoid repeating: $($errorCp.status) condition by checking preconditions"
} else {
    Write-Output "1. No viable restore point found"
    Write-Output "2. Start fresh or locate a manual backup"
}
if ($errorCp.error_info -and $errorCp.error_info.recovery_instruction) {
    Write-Output ""
    Write-Output "Recovery hint from checkpoint:"
    Write-Output "  $($errorCp.error_info.recovery_instruction)"
}
Write-Output "=========================================="
Write-Output "/recover --id <checkpoint-id> for specific checkpoint"
Write-Output "/recover --json for machine-readable output"
Write-Output "/recover --dryrun to simulate recovery"
Write-Output "=========================================="
