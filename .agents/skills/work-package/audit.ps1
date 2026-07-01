param(
    [string]$Project = "",
    [string]$Title = "",
    [switch]$Json
)

# Ensure UTF-8 output encoding for Unicode/Thai support
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

$allDecisions = @()
$allKnowledge = @()
$allQuestions = @()
$timeline = @()
$statusMap = @{}
$totalProgress = 0
$errorCount = 0
$currentOpenQuestions = @()

foreach ($file in $files) {
    try {
        $wp = Get-Content $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
        
        if ($Project -and $wp.title -notmatch $Project) { continue }
        if ($Title -and $wp.title -notmatch $Title) { continue }
        
        $timeline += [PSCustomObject]@{
            id = $wp.id
            time = $wp.createdAt
            title = $wp.title
            progress = $wp.progress
            status = $wp.status
            seq = if ($wp.sequence) { $wp.sequence } else { 0 }
        }
        
        $totalProgress += $wp.progress
        
        if ($wp.decisions) {
            foreach ($d in $wp.decisions) {
                $allDecisions += [PSCustomObject]@{
                    checkpointId = $wp.id
                    timestamp = if ($d.timestamp) { $d.timestamp } else { $wp.createdAt }
                    decision = $d.decision
                    rationale = $d.rationale
                    decidedBy = $d.decidedBy
                    alternatives = if ($d.alternatives) { ($d.alternatives -join ", ") } else { "" }
                }
            }
        }
        
        if ($wp.knowledge) {
            foreach ($k in $wp.knowledge) {
                $allKnowledge += "$k (from $($wp.title))"
            }
        }
        
        # Open questions: later checkpoint replaces previous state
        if ($wp.open_questions -and $wp.open_questions.Count -gt 0) {
            $currentOpenQuestions = @()
            foreach ($q in $wp.open_questions) {
                $currentOpenQuestions += [PSCustomObject]@{
                    question = $q.question
                    assignedTo = $q.assignedTo
                    fromCheckpoint = $wp.id
                }
            }
        } else {
            $currentOpenQuestions = @()
        }

        if ($wp.status -eq "error" -or $wp.status -eq "failed") {
            $errorCount++
        }

        if (-not $statusMap.ContainsKey($wp.status)) { $statusMap[$wp.status] = 0 }; $statusMap[$wp.status]++
    } catch {
        Write-Warning "Skipped invalid checkpoint: $($file.Name) - $_"
    }
}

if ($Json) {
    $auditResult = @{
        totalCheckpoints = $timeline.Count
        project = if ($Project) { $Project } else { "all" }
        decisions = $allDecisions
        timeline = $timeline
        knowledge = $allKnowledge
        openQuestions = $currentOpenQuestions
        errorCount = $errorCount
        statusBreakdown = $statusMap
        avgProgress = if ($timeline.Count -gt 0) { [math]::Round($totalProgress / $timeline.Count, 1) } else { 0 }
    }
    Write-Output ($auditResult | ConvertTo-Json -Depth 10)
    exit 0
}

# Human-readable output
Write-Output "=========================================="
Write-Output "AUDIT REPORT"
$projectLabel = if ($Project) { " ($Project)" } else { "" }
Write-Output "Project:$projectLabel"
Write-Output "Checkpoints: $($timeline.Count) | Errors: $errorCount"
if ($timeline.Count -gt 0) { Write-Output "Avg Progress: $([math]::Round($totalProgress / $timeline.Count, 1))%" }
Write-Output "=========================================="
Write-Output ""

# Timeline
Write-Output "--- Timeline ---"
$sorted = $timeline | Sort-Object time
foreach ($t in $sorted) {
    $marker = switch ($t.status) {
        "error" { "[ERROR]" }
        "failed" { "[FAIL]" }
        "completed" { "[DONE]" }
        default { "       " }
    }
    Write-Output "  $marker $($t.time) | $($t.title) ($($t.progress)%)"
}
Write-Output ""

# Decisions
if ($allDecisions.Count -gt 0) {
    Write-Output "--- Decisions ($($allDecisions.Count)) ---"
    $sortedDec = $allDecisions | Sort-Object timestamp
    foreach ($d in $sortedDec) {
        Write-Output "  [$($d.checkpointId)] $($d.decision)"
        Write-Output "    reason: $($d.rationale)"
        Write-Output "    by: $($d.decidedBy)"
        if ($d.alternatives) { Write-Output "    alternatives: $($d.alternatives)" }
        Write-Output ""
    }
}

# Knowledge
if ($allKnowledge.Count -gt 0) {
    Write-Output "--- Knowledge ($($allKnowledge.Count)) ---"
    $seen = @{}
    foreach ($k in $allKnowledge) {
        if (-not $seen.ContainsKey($k)) {
            $seen[$k] = $true
            Write-Output "  * $k"
        }
    }
    Write-Output ""
}

# Open Questions (latest state from newest checkpoint)
if ($currentOpenQuestions.Count -gt 0) {
    Write-Output "--- Open Questions ($($currentOpenQuestions.Count)) ---"
    $seenQ = @{}
    foreach ($q in $currentOpenQuestions) {
        $key = $q.question
        if (-not $seenQ.ContainsKey($key)) {
            $seenQ[$key] = $true
            $assignee = if ($q.assignedTo) { " (assigned: $($q.assignedTo))" } else { "" }
            Write-Output "  ? $($q.question)$assignee"
        }
    }
    Write-Output ""
}

# Status Breakdown
Write-Output "--- Status Breakdown ---"
foreach ($entry in $statusMap.GetEnumerator()) {
    Write-Output "  $($entry.Key): $($entry.Value)"
}

Write-Output ""
Write-Output "=========================================="
Write-Output "To export as JSON: /audit --json or .\audit.ps1 -Json"
Write-Output "=========================================="
