param(
    [string]$Title = "",
    [string]$Objective = "",
    [string]$Completed = "[]",
    [string]$Pending = "[]",
    [string]$Decisions = "[]",
    [string]$Knowledge = "[]",
    [string]$Risks = "[]",
    [string]$NextSteps = "[]",
    [string]$ContextSummary = "",
    [string]$Status = "in_progress",
    [int]$Progress = 0,
    [switch]$Auto
)

$projectRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$checkpointsDir = Join-Path $projectRoot "checkpoints"
if (-not (Test-Path $checkpointsDir)) { New-Item -ItemType Directory -Path $checkpointsDir -Force | Out-Null }

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$isoNow = Get-Date -Format "yyyy-MM-ddTHH:mm:ssK"

$slug = if ($Title) { $Title.ToLower() -replace '[^a-z0-9]+', '-' -replace '^-|-$', '' } else { "checkpoint" }

# Auto sequence: find last checkpoint with same slug and increment
$seq = 1
$existingFiles = Get-ChildItem -Path $checkpointsDir -Filter "wp-$timestamp-*" -ErrorAction SilentlyContinue
$slugPrefix = "wp-$timestamp-$slug"
if ((Get-ChildItem -Path $checkpointsDir -Filter "wp-*$slug*.json" -ErrorAction SilentlyContinue).Count -gt 0) {
    $lastFile = Get-ChildItem -Path $checkpointsDir -Filter "wp-*$slug*.json" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($lastFile.Name)
    if ($baseName -match '-(\d+)$') {
        $seq = [int]$Matches[1] + 1
    }
}

$id = "wp-$timestamp-$slug"
$filename = "$id.json"
if ($Auto) {
    $filename = "wp-$timestamp-$slug-$( '{0:D3}' -f $seq ).json"
    $id = "wp-$timestamp-$slug-$seq"
}

$filePath = Join-Path $checkpointsDir $filename

$wp = @{
    schema = "hermes-work-package-v1"
    id = $id
    sequence = $seq
    createdAt = $isoNow
    updatedAt = $isoNow
    title = $Title
    status = $Status
    progress = $Progress
    objective = $Objective
    scope = ""
    completed = if ($Completed -eq "[]") { @() } else { $Completed | ConvertFrom-Json }
    pending = if ($Pending -eq "[]") { @() } else { $Pending | ConvertFrom-Json }
    artifacts = @()
    agents = @()
    decisions = if ($Decisions -eq "[]") { @() } else { $Decisions | ConvertFrom-Json }
    knowledge = if ($Knowledge -eq "[]") { @() } else { $Knowledge | ConvertFrom-Json }
    assumptions = @()
    risks = if ($Risks -eq "[]") { @() } else { $Risks | ConvertFrom-Json }
    next_steps = if ($NextSteps -eq "[]") { @() } else { $NextSteps | ConvertFrom-Json }
    dependencies = @()
    open_questions = @()
    memory_snapshot = @()
    context_summary = $ContextSummary
    tags = @()
}

$wpJson = $wp | ConvertTo-Json -Depth 10
Set-Content -Path $filePath -Value $wpJson -Encoding UTF8

if ($Auto) {
    Write-Output "[auto-checkpoint] $id"
} else {
    Write-Output "Checkpoint saved:"
    Write-Output (Resolve-Path $filePath -Relative).TrimStart('.\')
}
