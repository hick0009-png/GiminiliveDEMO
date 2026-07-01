param(
    [string]$Id = "",
    [string]$Project = "",
    [switch]$Json,
    [switch]$Full
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

$checkpoints = @()
foreach ($file in $files) {
    try {
        $wp = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($Id -and $wp.id -ne $Id) { continue }
        if ($Project -and $wp.title -notmatch $Project) { continue }
        $checkpoints += $wp
    } catch {
        Write-Warning "Skipped invalid checkpoint: $($file.Name) - $_"
    }
}

$checkpoints = $checkpoints | Sort-Object sequence

if ($checkpoints.Count -eq 0) {
    Write-Output "No matching checkpoints found."
    exit 0
}

# Build replay data
$replayEntries = @()
$stateLog = @()

foreach ($wp in $checkpoints) {
    $entry = @{
        id = $wp.id
        seq = if ($wp.sequence) { $wp.sequence } else { 0 }
        time = $wp.createdAt
        title = $wp.title
        status = $wp.status
        progress = $wp.progress
        objective = $wp.objective
        context = $wp.context_summary
        decisions = @()
        completed = @()
        pending = @()
        knowledge = @()
        memory = @()
        errors = @()
    }

    if ($wp.decisions) {
        foreach ($d in $wp.decisions) {
            $entry.decisions += [PSCustomObject]@{
                id = if ($d.id) { $d.id } else { "DEC-?" }
                decision = $d.decision
                rationale = $d.rationale
                decidedBy = $d.decidedBy
                timestamp = if ($d.timestamp) { $d.timestamp } else { $wp.createdAt }
            }
        }
    }

    if ($wp.completed) { $entry.completed = $wp.completed }
    if ($wp.pending) { $entry.pending = $wp.pending }
    if ($wp.knowledge) { $entry.knowledge = $wp.knowledge }
    if ($wp.memory_snapshot) { $entry.memory = $wp.memory_snapshot }

    $stateLog += [PSCustomObject]@{
        seq = $entry.seq
        id = $entry.id
        status = $entry.status
        progress = $entry.progress
        completedCount = $entry.completed.Count
        pendingCount = $entry.pending.Count
        decisionCount = $entry.decisions.Count
    }

    $replayEntries += $entry
}

if ($Json) {
    $replayResult = @{
        totalCheckpoints = $checkpoints.Count
        entries = $replayEntries
        stateLog = $stateLog
    }
    Write-Output ($replayResult | ConvertTo-Json -Depth 10)
    exit 0
}

# Human-readable replay
Write-Output "=========================================="
Write-Output "REPLAY"
if ($Id) { Write-Output "Checkpoint: $Id" }
if ($Project) { Write-Output "Project: $Project" }
Write-Output "Entries: $($replayEntries.Count)"
Write-Output "=========================================="
Write-Output ""

foreach ($entry in $replayEntries) {
    Write-Output "=========================================="
    Write-Output "Checkpoint #$($entry.seq): $($entry.title)"
    Write-Output "Time: $($entry.time)"
    Write-Output "Status: $($entry.status) ($($entry.progress)%)"
    if ($entry.objective) { Write-Output "Objective: $($entry.objective)" }
    if ($entry.context) { Write-Output "Context: $($entry.context)" }
    Write-Output ""

    if ($entry.completed.Count -gt 0) {
        Write-Output "  [Completed] $($entry.completed.Count) items:"
        foreach ($c in $entry.completed) { Write-Output "    + $c" }
        Write-Output ""
    }

    if ($entry.pending.Count -gt 0) {
        Write-Output "  [Pending] $($entry.pending.Count) items:"
        foreach ($p in $entry.pending) { Write-Output "    - $p" }
        Write-Output ""
    }

    if ($entry.decisions.Count -gt 0) {
        Write-Output "  [Decisions]"
        foreach ($d in $entry.decisions) {
            Write-Output "    [$($d.id)] $($d.decision)"
            Write-Output "      reason: $($d.rationale)"
            Write-Output "      by: $($d.decidedBy)"
            Write-Output ""
        }
    }

    if ($entry.knowledge.Count -gt 0) {
        Write-Output "  [Knowledge]"
        foreach ($k in $entry.knowledge) { Write-Output "    * $k" }
        Write-Output ""
    }

    if ($Full -and $entry.memory.Count -gt 0) {
        Write-Output "  [Memory Snapshot]"
        foreach ($m in $entry.memory) {
            Write-Output "    $($m.key) = $($m.value)"
        }
        Write-Output ""
    }
}

Write-Output "=========================================="
Write-Output "STATE TRANSITION LOG"
Write-Output "------------------------------------------"
$prevProgress = 0
foreach ($s in ($stateLog | Sort-Object seq)) {
    $delta = if ($s.progress -ge $prevProgress) { "+$($s.progress - $prevProgress)%" } else { "$($s.progress - $prevProgress)%" }
    Write-Output "  #$($s.seq) $($s.id)"
    Write-Output "    $($s.status) | $($s.progress)% ($delta) | +$($s.completedCount) done, $($s.pendingCount) pending, $($s.decisionCount) decisions"
    $prevProgress = $s.progress
}
Write-Output "=========================================="
Write-Output "To export: /replay --json or .\replay.ps1 -Json"
Write-Output "Full memory: /replay --full or .\replay.ps1 -Full"
Write-Output "=========================================="
