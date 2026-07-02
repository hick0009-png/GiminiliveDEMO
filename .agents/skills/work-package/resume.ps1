param(
    [string]$Path = ""
)

$projectRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$checkpointsDir = Join-Path $projectRoot "checkpoints"

if (-not $Path) {
    $latestJsonPath = Join-Path $checkpointsDir "latest.json"
    if (Test-Path $latestJsonPath) {
        try {
            $latestObj = Get-Content $latestJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
            if ($latestObj.latest_checkpoint_path) {
                $resolvedPath = Join-Path $projectRoot $latestObj.latest_checkpoint_path
                if (Test-Path $resolvedPath) {
                    $Path = $resolvedPath
                }
            }
        } catch {}
    }
    
    if (-not $Path) {
        # Fallback to the newest wp-*.json file in checkpoints folder
        if (Test-Path $checkpointsDir) {
            $newestFile = Get-ChildItem -Path $checkpointsDir -Filter "wp-*.json" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($newestFile) {
                $Path = $newestFile.FullName
            }
        }
    }
} else {
    # Resolve relative paths relative to project root
    if (-not (Test-Path $Path)) {
        $resolvedPath = Join-Path $projectRoot $Path
        if (Test-Path $resolvedPath) {
            $Path = $resolvedPath
        }
    }
}

if (-not $Path -or -not (Test-Path $Path)) {
    Write-Error "No checkpoint file found to resume from."
    exit 1
}

try {
    $wp = Get-Content $Path -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    Write-Error "Invalid checkpoint file: $_"
    exit 1
}

Write-Output "=========================================="
Write-Output "Work Package Resume"
Write-Output "=========================================="
Write-Output "Title:    $($wp.title)"
Write-Output "Status:   $($wp.status)"
Write-Output "Progress: $($wp.progress)%"
Write-Output "Created:  $($wp.createdAt)"
Write-Output "Updated:  $($wp.updatedAt)"
Write-Output ""

if ($wp.objective) {
    Write-Output "Objective:"
    Write-Output "  $($wp.objective)"
    Write-Output ""
}

if ($wp.completed.Count -gt 0) {
    Write-Output "Completed ($($wp.completed.Count)):"
    foreach ($item in $wp.completed) { Write-Output "  [DONE] $item" }
    Write-Output ""
}

if ($wp.pending.Count -gt 0) {
    Write-Output "Pending ($($wp.pending.Count)):"
    foreach ($item in $wp.pending) { Write-Output "  [TODO] $item" }
    Write-Output ""
}

if ($wp.next_steps.Count -gt 0) {
    Write-Output "Next Steps ($($wp.next_steps.Count)):"
    $i = 1
    foreach ($step in $wp.next_steps) { Write-Output "  $i. $step"; $i++ }
    Write-Output ""
}

if ($wp.knowledge.Count -gt 0) {
    Write-Output "Knowledge ($($wp.knowledge.Count)):"
    foreach ($k in $wp.knowledge) { Write-Output "  * $k" }
    Write-Output ""
}

if ($wp.decisions.Count -gt 0) {
    Write-Output "Decisions ($($wp.decisions.Count)):"
    foreach ($d in $wp.decisions) { Write-Output "  * $($d.decision) ($($d.decidedBy))" }
    Write-Output ""
}

if ($wp.open_questions.Count -gt 0) {
    Write-Output "Open Questions:"
    foreach ($q in $wp.open_questions) { Write-Output "  ? $($q.question)" }
    Write-Output ""
}

if ($wp.context_summary) {
    Write-Output "Context Summary:"
    Write-Output "  $($wp.context_summary)"
    Write-Output ""
}

Write-Output "=========================================="
Write-Output "Agent: resume from state above"
Write-Output "=========================================="
